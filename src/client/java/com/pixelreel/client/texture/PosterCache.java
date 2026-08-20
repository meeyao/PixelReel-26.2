package com.pixelreel.client.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.pixelreel.PixelReel;
import com.pixelreel.channels.Channel;
import com.pixelreel.channels.ChannelEntry;
import com.pixelreel.client.playback.video.NativeImageAccess;
import com.pixelreel.config.ConfigManager;
import com.pixelreel.config.PixelReelConfig;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

/** loads channel logos asynchronously */
public final class PosterCache {
	public static final PosterCache INSTANCE = new PosterCache();
	public static final ResourceLocation PLACEHOLDER = PixelReel.id("textures/gui/channel_placeholder.png");

	private static final int MAX_ENTRIES = 96;
	private static final long MAX_IMAGE_BYTES = 8 * 1024 * 1024;
	private static final int MAX_DIMENSION = 1024;
	private static final int UPLOADS_PER_TICK = 6;
	private static final long FAILURE_RETRY_MILLIS = 30_000L;
	private static final Set<String> BROKEN_HOSTS = Set.of("localhost", "127.0.0.1", "0.0.0.0", "::1", "[::1]");
	private static final String LOGO_KEY_PREFIX = "chlogo:";
	private static final String IMAGE_KEY_PREFIX = "img:";

	public enum State {
		LOADING,
		READY,
		FALLBACK
	}

	public record Poster(State state, @Nullable ResourceLocation texture, int width, int height) {
		public ResourceLocation textureOrPlaceholder() {
			return this.texture != null ? this.texture : PLACEHOLDER;
		}
	}

	private record PreparedPixels(int width, int height, int[] argb) {
	}

	private static final Poster LOADING_POSTER = new Poster(State.LOADING, null, 0, 0);
	private static final Poster FALLBACK_POSTER = new Poster(State.FALLBACK, null, 0, 0);

	private final Map<String, Entry> entries = new ConcurrentHashMap<>();
	private final Deque<String> order = new ArrayDeque<>();
	private final ExecutorService loader = Executors.newFixedThreadPool(2, runnable -> {
		Thread thread = new Thread(runnable, "pixelreel-poster");
		thread.setDaemon(true);
		return thread;
	});
	private final ConcurrentLinkedQueue<Runnable> uploadQueue = new ConcurrentLinkedQueue<>();
	private final AtomicLong generation = new AtomicLong();
	private volatile @Nullable HttpClient httpClient;

	private PosterCache() {
	}

	public Poster get(ChannelEntry entry) {
		Channel channel = entry.channel();
		String key = LOGO_KEY_PREFIX + (channel.id().isEmpty() ? "num:" + channel.number() : channel.id());
		return this.getOrLoad(key, buildCandidates(entry));
	}

	public Poster getByUrl(String key, String url) {
		if (key == null || key.isBlank() || url == null || url.isBlank()) {
			return FALLBACK_POSTER;
		}
		return this.getOrLoad(IMAGE_KEY_PREFIX + key, List.of(new Candidate.Remote(url)));
	}

	private Poster getOrLoad(String key, List<Candidate> candidates) {
		Entry cached = this.entries.get(key);
		if (cached != null) {
			if (cached.state == State.READY && cached.textureId != null) {
				return new Poster(State.READY, cached.textureId, cached.width, cached.height);
			}
			if (cached.state == State.LOADING) {
				return LOADING_POSTER;
			}
			if (System.currentTimeMillis() - cached.failedAt < FAILURE_RETRY_MILLIS) {
				return FALLBACK_POSTER;
			}
			this.entries.remove(key);
			this.order.remove(key);
		}

		Entry pending = new Entry(this.generation.get());
		this.put(key, pending);
		this.loader.execute(() -> this.loadChain(key, pending, candidates));
		return LOADING_POSTER;
	}

	private sealed interface Candidate {
		record LocalFile(Path path) implements Candidate {
		}

		record Remote(String url) implements Candidate {
		}
	}

	private static List<Candidate> buildCandidates(ChannelEntry entry) {
		Channel channel = entry.channel();
		PixelReelConfig config = ConfigManager.get();
		LinkedHashSet<String> remoteUrls = new LinkedHashSet<>();
		List<Candidate> candidates = new ArrayList<>();

		String override = firstOverride(config, channel);
		if (override != null) {
			if (override.regionMatches(true, 0, "http://", 0, 7) || override.regionMatches(true, 0, "https://", 0, 8)) {
				remoteUrls.add(override);
			} else {
				Path path = Path.of(override);
				candidates.add(new Candidate.LocalFile(path.isAbsolute() ? path : ConfigManager.posterOverrideDir().resolve(override)));
			}
		}

		Path posterDir = ConfigManager.posterOverrideDir();
		for (String base : new String[]{String.valueOf(channel.number()), sanitizeFileName(channel.id()), sanitizeFileName(channel.name())}) {
			if (base.isEmpty()) {
				continue;
			}
			for (String extension : new String[]{".png", ".jpg", ".jpeg"}) {
				candidates.add(new Candidate.LocalFile(posterDir.resolve(base + extension)));
			}
		}

		for (String url : new String[]{channel.logoUrl(), channel.guideIconUrl()}) {
			String rewritten = rewriteBrokenHost(url, config);
			if (!rewritten.isEmpty()) {
				remoteUrls.add(rewritten);
			}
		}
		for (String guessed : guessTunarrChannelIcons(channel.streamUrl(), config)) {
			remoteUrls.add(guessed);
		}

		for (String url : remoteUrls) {
			candidates.add(new Candidate.Remote(url));
		}
		return candidates;
	}

	private static List<String> guessTunarrChannelIcons(String streamUrl, PixelReelConfig config) {
		if (streamUrl == null || streamUrl.isBlank()) {
			return List.of();
		}
		try {
			URI uri = URI.create(streamUrl.trim());
			String path = uri.getPath();
			if (path == null) {
				return List.of();
			}
			int marker = path.indexOf("/stream/channels/");
			if (marker < 0) {
				return List.of();
			}
			String uuid = path.substring(marker + "/stream/channels/".length());
			int slash = uuid.indexOf('/');
			if (slash >= 0) {
				uuid = uuid.substring(0, slash);
			}
			if (uuid.isBlank()) {
				return List.of();
			}
			String host = config.mediaServerHost();
			if (host.isEmpty() && uri.getHost() != null) {
				host = uri.getHost();
			}
			if (host.isEmpty()) {
				return List.of();
			}
			int port = uri.getPort();
			String base = (uri.getScheme() == null ? "http" : uri.getScheme()) + "://" + host + (port > 0 ? ":" + port : "");
			return List.of(
				base + "/images/uploads/" + uuid + "_icon.png",
				base + "/images/uploads/" + uuid + "_icon.jpeg",
				base + "/images/uploads/" + uuid + "_icon.jpg"
			);
		} catch (IllegalArgumentException e) {
			return List.of();
		}
	}

	private static @Nullable String firstOverride(PixelReelConfig config, Channel channel) {
		for (String key : new String[]{String.valueOf(channel.number()), channel.id(), channel.name()}) {
			String value = config.posterOverrides.get(key);
			if (value != null && !value.isBlank()) {
				return value.trim();
			}
		}
		return null;
	}

	public static String sanitizeFileName(String value) {
		if (value == null) {
			return "";
		}
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._ -]", "_").strip();
	}

	public static String rewriteBrokenHost(String url, PixelReelConfig config) {
		if (url == null || url.isBlank()) {
			return "";
		}
		String trimmed = url.trim();
		if (!trimmed.regionMatches(true, 0, "http://", 0, 7) && !trimmed.regionMatches(true, 0, "https://", 0, 8)) {
			return "";
		}
		try {
			URI uri = URI.create(trimmed);
			String host = uri.getHost();
			if (host == null) {
				return "";
			}
			if (!BROKEN_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
				return trimmed;
			}
			String replacement = config.mediaServerHost();
			if (replacement.isEmpty()) {
				return trimmed;
			}
			int port = uri.getPort();
			if (port < 0) {
				try {
					port = URI.create(config.m3uUrl).getPort();
				} catch (IllegalArgumentException ignored) {
				}
			}
			return new URI(uri.getScheme(), null, replacement, port, uri.getPath(), uri.getQuery(), uri.getFragment()).toString();
		} catch (IllegalArgumentException | URISyntaxException e) {
			return "";
		}
	}

	private void loadChain(String key, Entry entry, List<Candidate> candidates) {
		try {
			for (Candidate candidate : candidates) {
				try {
					if (!this.isCurrent(key, entry)) {
						return;
					}
					byte[] bytes = switch (candidate) {
						case Candidate.LocalFile localFile -> readLocal(localFile.path());
						case Candidate.Remote remote -> this.fetchRemote(remote.url());
					};
					if (bytes == null) {
						continue;
					}
					// Decode on the worker only. NativeImage / GL upload must happen on the client tick.
					PreparedPixels prepared = preparePixels(bytes);
					if (prepared == null || !this.isCurrent(key, entry)) {
						continue;
					}
					String source = switch (candidate) {
						case Candidate.LocalFile localFile -> localFile.path().toString();
						case Candidate.Remote remote -> remote.url();
					};
					this.uploadQueue.offer(() -> this.installPrepared(key, entry, prepared, source));
					return;
				} catch (Throwable t) {
					PixelReel.LOGGER.warn("Channel thumbnail candidate failed for {} ({}): {}", key, candidate, t.toString());
				}
			}
			if (this.isCurrent(key, entry)) {
				entry.state = State.FALLBACK;
				entry.failedAt = System.currentTimeMillis();
				PixelReel.LOGGER.debug("No usable channel thumbnail for {}; using fallback card", key);
			}
		} catch (Throwable t) {
			if (this.isCurrent(key, entry)) {
				entry.state = State.FALLBACK;
				entry.failedAt = System.currentTimeMillis();
				PixelReel.LOGGER.warn("Artwork load failed for {}", key, t);
			}
		}
	}

	private boolean isCurrent(String key, Entry entry) {
		return entry.generation == this.generation.get() && this.entries.get(key) == entry;
	}

	private static @Nullable PreparedPixels preparePixels(byte[] bytes) throws IOException {
		BufferedImage awt = ImageIO.read(new ByteArrayInputStream(bytes));
		if (awt == null) {
			return null;
		}
		int width = awt.getWidth();
		int height = awt.getHeight();
		if (width <= 0 || height <= 0) {
			return null;
		}
		int targetWidth = width;
		int targetHeight = height;
		if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
			float scale = (float) MAX_DIMENSION / Math.max(width, height);
			targetWidth = Math.max(1, Math.round(width * scale));
			targetHeight = Math.max(1, Math.round(height * scale));
		}
		BufferedImage rgba = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = rgba.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.drawImage(awt, 0, 0, targetWidth, targetHeight, null);
		} finally {
			graphics.dispose();
		}
		int[] argb = new int[targetWidth * targetHeight];
		rgba.getRGB(0, 0, targetWidth, targetHeight, argb, 0, targetWidth);
		return new PreparedPixels(targetWidth, targetHeight, argb);
	}

	private static int argbToNative(int argb) {
		int a = (argb >>> 24) & 0xFF;
		int r = (argb >>> 16) & 0xFF;
		int g = (argb >>> 8) & 0xFF;
		int b = argb & 0xFF;
		return (a << 24) | (b << 16) | (g << 8) | r;
	}

	private NativeImage toNativeImage(PreparedPixels prepared) {
		NativeImage image = new NativeImage(prepared.width(), prepared.height(), false);
		int[] argb = prepared.argb();
		try {
			long base = NativeImageAccess.pointer(image);
			for (int i = 0; i < argb.length; i++) {
				MemoryUtil.memPutInt(base + (long) i * 4L, argbToNative(argb[i]));
			}
		} catch (RuntimeException reflectionFailed) {
			int width = prepared.width();
			for (int y = 0; y < prepared.height(); y++) {
				int row = y * width;
				for (int x = 0; x < width; x++) {
					image.setPixelRGBA(x, y, argbToNative(argb[row + x]));
				}
			}
		}
		return image;
	}

	private void installPrepared(String key, Entry entry, PreparedPixels prepared, String source) {
		if (!this.isCurrent(key, entry)) {
			return;
		}
		NativeImage image = null;
		try {
			image = toNativeImage(prepared);
			if (!this.isCurrent(key, entry)) {
				image.close();
				return;
			}
			int width = image.getWidth();
			int height = image.getHeight();
			DynamicTexture texture = new DynamicTexture(image);
			image = null;
			texture.setFilter(true, false);
			texture.upload();
			ResourceLocation id = Minecraft.getInstance().getTextureManager()
				.register("pixelreel_poster", texture);
			entry.textureId = id;
			entry.width = width;
			entry.height = height;
			entry.state = State.READY;
			PixelReel.LOGGER.debug("Loaded channel thumbnail for {} from {} -> {}", key, source, id);
		} catch (Exception e) {
			if (image != null) {
				image.close();
			}
			if (this.isCurrent(key, entry)) {
				entry.state = State.FALLBACK;
				entry.failedAt = System.currentTimeMillis();
				PixelReel.LOGGER.warn("Could not upload channel artwork for {} from {}", key, source, e);
			}
		}
	}

	private static byte @Nullable [] readLocal(Path path) throws IOException {
		if (!Files.isRegularFile(path)) {
			return null;
		}
		byte[] bytes = Files.readAllBytes(path);
		return looksLikeImage(bytes) ? bytes : null;
	}

	private byte @Nullable [] fetchRemote(String url) throws Exception {
		Path cacheFile = cachePathFor(url);
		PixelReelConfig config = ConfigManager.get();
		if (cacheFile != null && Files.isRegularFile(cacheFile)) {
			Instant expiry = Files.getLastModifiedTime(cacheFile).toInstant().plus(Duration.ofHours(config.artworkCacheHours));
			if (Instant.now().isBefore(expiry)) {
				byte[] cached = Files.readAllBytes(cacheFile);
				if (looksLikeImage(cached) && !looksLikeText(cached)) {
					return cached;
				}
			}
		}

		HttpResponse<byte[]> response = this.client().send(
			HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(config.networkTimeoutSeconds))
				.header("User-Agent", "pixelReel/1.0")
				.header("Accept", "image/png,image/jpeg,image/*;q=0.8,*/*;q=0.5")
				.GET()
				.build(),
			HttpResponse.BodyHandlers.ofByteArray()
		);
		if (response.statusCode() / 100 != 2) {
			return null;
		}
		byte[] body = response.body();
		if (body.length == 0 || body.length > MAX_IMAGE_BYTES) {
			return null;
		}
		String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
		if (contentType.contains("text/html") || contentType.contains("application/json") || contentType.contains("text/xml") || contentType.contains("application/xml")) {
			return null;
		}
		boolean declaredImage = contentType.startsWith("image/");
		if ((!declaredImage && !looksLikeImage(body)) || looksLikeText(body)) {
			return null;
		}
		if (cacheFile != null) {
			try {
				Files.createDirectories(cacheFile.getParent());
				Files.write(cacheFile, body);
			} catch (IOException e) {
				PixelReel.LOGGER.debug("Could not cache poster to disk: {}", e.toString());
			}
		}
		return body;
	}

	private static boolean looksLikeImage(byte[] bytes) {
		if (bytes.length < 12) {
			return false;
		}
		if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
			return true;
		}
		if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
			return true;
		}
		// GIF
		if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
			return true;
		}
		// WebP: RIFF....WEBP
		if (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
			&& bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
			return true;
		}
		return false;
	}

	private static boolean looksLikeText(byte[] bytes) {
		int check = Math.min(bytes.length, 64);
		for (int i = 0; i < check; i++) {
			byte value = bytes[i];
			if (value == 0) {
				return false;
			}
		}
		String head = new String(bytes, 0, check, StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT).stripLeading();
		return head.startsWith("<!doctype") || head.startsWith("<html") || head.startsWith("<?xml") || head.startsWith("{") || head.startsWith("[");
	}

	private static @Nullable Path cachePathFor(String url) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			String name = HexFormat.of().formatHex(digest.digest(url.getBytes(StandardCharsets.UTF_8)));
			return ConfigManager.artworkCacheDir().resolve(name + ".img");
		} catch (Exception e) {
			return null;
		}
	}

	public void clientTick() {
		int budget = UPLOADS_PER_TICK;
		Runnable task;
		while (budget-- > 0 && (task = this.uploadQueue.poll()) != null) {
			task.run();
		}
	}

	/**
	 * Drop on-demand poster textures for the previous browse page.
	 * Channel logos are kept. In-flight loads for the old page are cancelled.
	 */
	public void clearPage() {
		this.generation.incrementAndGet();
		this.uploadQueue.clear();
		Iterator<Map.Entry<String, Entry>> iterator = this.entries.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<String, Entry> mapEntry = iterator.next();
			if (!mapEntry.getKey().startsWith(IMAGE_KEY_PREFIX)) {
				continue;
			}
			iterator.remove();
			this.order.remove(mapEntry.getKey());
			releaseTexture(mapEntry.getValue());
		}
	}

	private void put(String key, Entry entry) {
		this.entries.put(key, entry);
		this.order.addLast(key);
		while (this.order.size() > MAX_ENTRIES) {
			String evicted = this.order.pollFirst();
			if (evicted == null) {
				break;
			}
			Entry removed = this.entries.remove(evicted);
			if (removed != null) {
				releaseTexture(removed);
			}
		}
	}

	private static void releaseTexture(Entry entry) {
		if (entry.textureId != null) {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft != null) {
				minecraft.getTextureManager().release(entry.textureId);
			}
			entry.textureId = null;
		}
	}

	private HttpClient client() {
		HttpClient existing = this.httpClient;
		if (existing != null) {
			return existing;
		}
		synchronized (this) {
			if (this.httpClient == null) {
				this.httpClient = HttpClient.newBuilder()
					.connectTimeout(Duration.ofSeconds(ConfigManager.get().networkTimeoutSeconds))
					.followRedirects(HttpClient.Redirect.NORMAL)
					.build();
			}
			return this.httpClient;
		}
	}

	public void clear() {
		this.generation.incrementAndGet();
		this.uploadQueue.clear();
		this.entries.values().forEach(PosterCache::releaseTexture);
		this.entries.clear();
		this.order.clear();
	}

	private static final class Entry {
		final long generation;
		volatile State state = State.LOADING;
		@Nullable ResourceLocation textureId;
		int width;
		int height;
		volatile long failedAt;

		Entry(long generation) {
			this.generation = generation;
		}
	}
}
