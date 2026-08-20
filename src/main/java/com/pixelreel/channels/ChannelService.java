package com.pixelreel.channels;

import com.pixelreel.PixelReel;
import com.pixelreel.config.ConfigManager;
import com.pixelreel.config.PixelReelConfig;
import java.io.IOException;
import java.io.StringReader;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jetbrains.annotations.Nullable;

/** server-side channel */
public final class ChannelService {
	public static final ChannelService INSTANCE = new ChannelService();

	private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
		Thread thread = new Thread(runnable, "pixelreel-http");
		thread.setDaemon(true);
		return thread;
	});
	private volatile @Nullable HttpClient httpClient;
	private volatile List<Channel> cachedChannels = List.of();
	private volatile Map<String, List<Programme>> cachedProgrammes = Map.of();
	private volatile long cacheFilledAtMillis;
	private volatile LiveStatus lastStatus = LiveStatus.notConfigured();
	private @Nullable CompletableFuture<List<Channel>> inFlight;

	private ChannelService() {
	}

	public LiveStatus lastStatus() {
		return this.lastStatus;
	}

	public List<Channel> cachedChannels() {
		return this.cachedChannels;
	}

	public boolean isCacheFresh() {
		return !this.cachedChannels.isEmpty()
			&& System.currentTimeMillis() - this.cacheFilledAtMillis < ConfigManager.get().channelCacheSeconds * 1000L;
	}

	public void invalidateCache() {
		this.cacheFilledAtMillis = 0L;
	}

	public List<ChannelEntry> entries() {
		long now = System.currentTimeMillis() / 1000L;
		Map<String, List<Programme>> programmes = this.cachedProgrammes;
		List<ChannelEntry> entries = new ArrayList<>();
		for (Channel channel : this.cachedChannels) {
			entries.add(new ChannelEntry(channel, guideFor(programmes.get(channel.id()), now)));
		}
		return entries;
	}

	public GuideInfo guideFor(String channelId) {
		return guideFor(this.cachedProgrammes.get(channelId), System.currentTimeMillis() / 1000L);
	}

	private static GuideInfo guideFor(@Nullable List<Programme> schedule, long now) {
		if (schedule == null || schedule.isEmpty()) {
			return GuideInfo.EMPTY;
		}
		Programme current = null;
		Programme next = null;
		for (Programme programme : schedule) {
			if (programme.isOnAirAt(now)) {
				current = programme;
			} else if (programme.startEpochSeconds() > now) {
				next = programme;
				break;
			}
		}
		return new GuideInfo(
			current == null ? "" : current.title(),
			current == null ? "" : current.episode(),
			current == null ? 0L : current.startEpochSeconds(),
			current == null ? 0L : current.endEpochSeconds(),
			current == null ? "" : current.iconUrl(),
			next == null ? "" : next.title(),
			next == null ? 0L : next.startEpochSeconds(),
			next == null ? 0L : next.endEpochSeconds()
		);
	}

	public Optional<Channel> findById(String channelId) {
		if (channelId == null || channelId.isEmpty()) {
			return Optional.empty();
		}
		return this.cachedChannels.stream().filter(channel -> channel.id().equals(channelId)).findFirst();
	}

	public Optional<Channel> resolve(String query) {
		if (query == null || query.isBlank()) {
			return Optional.empty();
		}
		String trimmed = query.trim();
		List<Channel> channels = this.cachedChannels;
		for (Channel channel : channels) {
			if (channel.id().equalsIgnoreCase(trimmed)) {
				return Optional.of(channel);
			}
		}
		try {
			int number = Integer.parseInt(trimmed);
			for (Channel channel : channels) {
				if (channel.number() == number) {
					return Optional.of(channel);
				}
			}
		} catch (NumberFormatException ignored) {
		}
		for (Channel channel : channels) {
			if (channel.name().equalsIgnoreCase(trimmed)) {
				return Optional.of(channel);
			}
		}
		String lower = trimmed.toLowerCase(Locale.ROOT);
		for (Channel channel : channels) {
			if (channel.name().toLowerCase(Locale.ROOT).contains(lower)) {
				return Optional.of(channel);
			}
		}
		return Optional.empty();
	}

	public synchronized CompletableFuture<List<Channel>> channels(boolean forceRefresh) {
		PixelReelConfig config = ConfigManager.get();
		if (!config.isConfigured()) {
			this.lastStatus = LiveStatus.notConfigured();
			return CompletableFuture.completedFuture(List.of());
		}
		if (!forceRefresh && this.isCacheFresh()) {
			return CompletableFuture.completedFuture(this.cachedChannels);
		}
		if (this.inFlight != null && !this.inFlight.isDone()) {
			return this.inFlight;
		}
		CompletableFuture<List<Channel>> future = CompletableFuture.<List<Channel>>supplyAsync(() -> this.fetchBlocking(config), this.executor)
			.exceptionally(throwable -> {
				Throwable cause = unwrap(throwable);
				PixelReel.LOGGER.warn("Channel playlist fetch failed ({}): {}", hostOnly(config.m3uUrl), describe(cause));
				this.lastStatus = LiveStatus.offline(describe(cause));
				return this.cachedChannels;
			});
		this.inFlight = future;
		return future;
	}

	private List<Channel> fetchBlocking(PixelReelConfig config) {
		String playlist;
		try {
			playlist = this.getString(config.m3uUrl, config);
		} catch (Exception e) {
			throw new CompletionException(e);
		}
		List<Channel> parsed = M3uParser.parse(playlist);

		XmltvParser.GuideData guide = XmltvParser.GuideData.empty();
		if (!config.xmltvUrl.isEmpty()) {
			try {
				String xml = this.getString(config.xmltvUrl, config);
				guide = XmltvParser.parse(new StringReader(xml));
			} catch (Exception e) {
				PixelReel.LOGGER.warn("Channel guide fetch failed ({}): {}", hostOnly(config.xmltvUrl), describe(unwrap(e)));
			}
		}

		List<Channel> merged = mergeGuide(parsed, guide);
		merged.sort(Comparator.comparingInt(Channel::number).thenComparing(Channel::name));

		Map<String, List<Programme>> programmes = new java.util.HashMap<>();
		for (Channel channel : merged) {
			String guideId = matchGuideChannelId(channel, guide);
			if (guideId != null && guide.programmes().containsKey(guideId)) {
				programmes.put(channel.id(), guide.programmes().get(guideId));
			}
		}

		synchronized (this) {
			this.cachedChannels = List.copyOf(merged);
			this.cachedProgrammes = Map.copyOf(programmes);
			this.cacheFilledAtMillis = System.currentTimeMillis();
		}

		if (merged.isEmpty()) {
			this.lastStatus = new LiveStatus(true, true, 0, "Connected, but the playlist contains no channels");
			PixelReel.LOGGER.warn("The channel playlist was reachable but contained no channels");
		} else {
			this.lastStatus = LiveStatus.online(merged.size());
			PixelReel.LOGGER.info("Loaded {} live channel(s); guide data for {} of them", merged.size(), programmes.size());
		}
		return this.cachedChannels;
	}

	private static List<Channel> mergeGuide(List<Channel> parsed, XmltvParser.GuideData guide) {
		List<Channel> merged = new ArrayList<>(parsed.size());
		for (Channel channel : parsed) {
			String guideId = matchGuideChannelId(channel, guide);
			String guideIcon = "";
			if (guideId != null) {
				XmltvParser.GuideChannel guideChannel = guide.channels().get(guideId);
				if (guideChannel != null) {
					guideIcon = guideChannel.iconUrl();
				}
			}
			merged.add(new Channel(channel.id(), channel.number(), channel.name(), channel.logoUrl(), guideIcon, channel.streamUrl()));
		}
		return merged;
	}

	private static @Nullable String matchGuideChannelId(Channel channel, XmltvParser.GuideData guide) {
		if (guide.channels().isEmpty()) {
			return null;
		}
		if (guide.channels().containsKey(channel.id())) {
			return channel.id();
		}
		String numberText = Integer.toString(channel.number());
		String normalizedName = channel.normalizedName();
		for (XmltvParser.GuideChannel guideChannel : guide.channels().values()) {
			for (String displayName : guideChannel.displayNames()) {
				if (displayName.equals(numberText)) {
					return guideChannel.id();
				}
			}
		}
		for (XmltvParser.GuideChannel guideChannel : guide.channels().values()) {
			for (String displayName : guideChannel.displayNames()) {
				if (!normalizedName.isEmpty() && Channel.normalize(displayName).equals(normalizedName)) {
					return guideChannel.id();
				}
			}
		}
		return null;
	}

	private String getString(String url, PixelReelConfig config) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
			.timeout(Duration.ofSeconds(config.networkTimeoutSeconds))
			.header("User-Agent", "pixelreel/1.0")
			.GET()
			.build();
		HttpResponse<String> response = this.client(config).send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() / 100 != 2) {
			throw new IOException("HTTP " + response.statusCode());
		}
		return response.body();
	}

	private HttpClient client(PixelReelConfig config) {
		HttpClient existing = this.httpClient;
		if (existing != null) {
			return existing;
		}
		synchronized (this) {
			if (this.httpClient == null) {
				this.httpClient = HttpClient.newBuilder()
					.connectTimeout(Duration.ofSeconds(config.networkTimeoutSeconds))
					.followRedirects(HttpClient.Redirect.NORMAL)
					.executor(this.executor)
					.build();
			}
			return this.httpClient;
		}
	}

	public static String hostOnly(String url) {
		try {
			URI uri = URI.create(url);
			String host = uri.getHost();
			return host == null ? "?" : host + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
		} catch (IllegalArgumentException e) {
			return "?";
		}
	}

	private static Throwable unwrap(Throwable throwable) {
		Throwable current = throwable;
		while ((current instanceof CompletionException || current instanceof ExecutionException) && current.getCause() != null) {
			current = current.getCause();
		}
		return current;
	}

	private static String describe(Throwable throwable) {
		if (throwable instanceof ConnectException) {
			return "Connection refused - is the media server running?";
		}
		if (throwable instanceof UnknownHostException) {
			return "Unknown host - check the server address";
		}
		if (throwable instanceof HttpConnectTimeoutException) {
			return "Connection timed out";
		}
		if (throwable instanceof HttpTimeoutException) {
			return "Server did not respond in time";
		}
		String message = throwable.getMessage();
		return message != null && !message.isBlank() ? message : throwable.getClass().getSimpleName();
	}
}
