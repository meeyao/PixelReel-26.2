package com.pixelreel.emby;

import com.pixelreel.PixelReel;
import com.pixelreel.config.ConfigManager;
import com.pixelreel.config.PixelReelConfig;
import com.pixelreel.jellyfin.JellyfinClient;
import com.pixelreel.jellyfin.JellyfinItemSummary;
import com.pixelreel.jellyfin.JellyfinLibrary;
import com.pixelreel.jellyfin.JellyfinStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;

/** server-side Emby cache */
public final class EmbyService {
	public static final EmbyService INSTANCE = new EmbyService();
	public static final int PAGE_SIZE = 48;

	private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
		Thread thread = new Thread(runnable, "pixelreel-emby");
		thread.setDaemon(true);
		return thread;
	});

	private volatile JellyfinStatus lastStatus = JellyfinStatus.notConfigured();
	private volatile List<JellyfinLibrary> libraries = List.of();
	private volatile List<JellyfinItemSummary> movies = List.of();
	private volatile List<JellyfinItemSummary> series = List.of();
	private volatile Map<String, List<JellyfinItemSummary>> seasonsBySeries = Map.of();
	private volatile Map<String, List<JellyfinItemSummary>> episodesBySeason = Map.of();
	private volatile Map<String, JellyfinItemSummary> itemsById = Map.of();
	private volatile long cacheFilledAtMillis;
	private volatile String resolvedUserId = "";
	private @Nullable CompletableFuture<JellyfinStatus> inFlight;

	private EmbyService() {
	}

	public JellyfinStatus lastStatus() {
		return this.lastStatus;
	}

	public List<JellyfinLibrary> libraries() {
		return this.libraries;
	}

	public String resolvedUserId() {
		return this.resolvedUserId;
	}

	public boolean isCacheFresh() {
		PixelReelConfig config = ConfigManager.get();
		return this.lastStatus.authenticated()
			&& System.currentTimeMillis() - this.cacheFilledAtMillis < config.embyLibraryCacheSeconds * 1000L;
	}

	public void invalidateCache() {
		this.cacheFilledAtMillis = 0L;
		this.resolvedUserId = "";
	}

	public synchronized CompletableFuture<JellyfinStatus> refresh(boolean force) {
		PixelReelConfig config = ConfigManager.get();
		if (!config.isEmbyConfigured()) {
			this.lastStatus = JellyfinStatus.notConfigured();
			this.clearCatalogue();
			return CompletableFuture.completedFuture(this.lastStatus);
		}
		if (!JellyfinClient.isUrlValid(config.embyUrl)) {
			this.lastStatus = JellyfinStatus.offline("Invalid Emby URL");
			return CompletableFuture.completedFuture(this.lastStatus);
		}
		if (!force && this.isCacheFresh()) {
			return CompletableFuture.completedFuture(this.lastStatus);
		}
		if (this.inFlight != null && !this.inFlight.isDone()) {
			return this.inFlight;
		}
		CompletableFuture<JellyfinStatus> future = CompletableFuture.supplyAsync(() -> this.scanBlocking(config), this.executor);
		this.inFlight = future;
		return future;
	}

	public List<JellyfinItemSummary> movies(@Nullable String search) {
		return filter(this.movies, search);
	}

	public List<JellyfinItemSummary> series(@Nullable String search) {
		return filter(this.series, search);
	}

	public Page pageMovies(String search, int page) {
		return pageOf(this.movies(search), page);
	}

	public Page pageSeries(String search, int page) {
		return pageOf(this.series(search), page);
	}

	public Optional<JellyfinItemSummary> find(String itemId) {
		if (itemId == null || itemId.isBlank()) {
			return Optional.empty();
		}
		return Optional.ofNullable(this.itemsById.get(itemId));
	}

	public CompletableFuture<Optional<JellyfinItemSummary>> fetchItem(String itemId) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				PixelReelConfig config = ConfigManager.get();
				if (!config.isEmbyConfigured()) {
					return Optional.empty();
				}
				JellyfinClient client = this.client(config);
				String userId = this.ensureUserId(client);
				JellyfinItemSummary item = client.getItem(userId, itemId);
				if (item != null) {
					Map<String, JellyfinItemSummary> copy = new LinkedHashMap<>(this.itemsById);
					copy.put(item.id(), item);
					this.itemsById = Map.copyOf(copy);
				}
				return Optional.ofNullable(item);
			} catch (Exception e) {
				PixelReel.LOGGER.warn("Failed to fetch Emby item {}: {}", itemId, JellyfinClient.describeError(e));
				return Optional.empty();
			}
		}, this.executor);
	}

	public CompletableFuture<List<JellyfinItemSummary>> seasons(String seriesId, boolean force) {
		List<JellyfinItemSummary> cached = this.seasonsBySeries.getOrDefault(seriesId, List.of());
		if (!force && !cached.isEmpty()) {
			return CompletableFuture.completedFuture(cached);
		}
		return CompletableFuture.supplyAsync(() -> {
			try {
				PixelReelConfig config = ConfigManager.get();
				JellyfinClient client = this.client(config);
				String userId = this.ensureUserId(client);
				List<JellyfinItemSummary> seasons = client.listChildren(userId, seriesId, "Season");
				seasons.sort(Comparator.comparingInt(JellyfinItemSummary::indexNumber).thenComparing(JellyfinItemSummary::title));
				Map<String, List<JellyfinItemSummary>> copy = new LinkedHashMap<>(this.seasonsBySeries);
				copy.put(seriesId, List.copyOf(seasons));
				this.seasonsBySeries = Map.copyOf(copy);
				this.index(seasons);
				return seasons;
			} catch (Exception e) {
				PixelReel.LOGGER.warn("Failed to load Emby seasons for {}: {}", seriesId, JellyfinClient.describeError(e));
				return cached;
			}
		}, this.executor);
	}

	public CompletableFuture<List<JellyfinItemSummary>> episodes(String seasonId, boolean force) {
		List<JellyfinItemSummary> cached = this.episodesBySeason.getOrDefault(seasonId, List.of());
		if (!force && !cached.isEmpty()) {
			return CompletableFuture.completedFuture(cached);
		}
		return CompletableFuture.supplyAsync(() -> {
			try {
				PixelReelConfig config = ConfigManager.get();
				JellyfinClient client = this.client(config);
				String userId = this.ensureUserId(client);
				List<JellyfinItemSummary> episodes = client.listChildren(userId, seasonId, "Episode");
				episodes.sort(Comparator.comparingInt(JellyfinItemSummary::indexNumber).thenComparing(JellyfinItemSummary::title));
				Map<String, List<JellyfinItemSummary>> copy = new LinkedHashMap<>(this.episodesBySeason);
				copy.put(seasonId, List.copyOf(episodes));
				this.episodesBySeason = Map.copyOf(copy);
				this.index(episodes);
				return episodes;
			} catch (Exception e) {
				PixelReel.LOGGER.warn("Failed to load Emby episodes for {}: {}", seasonId, JellyfinClient.describeError(e));
				return cached;
			}
		}, this.executor);
	}

	public CompletableFuture<Optional<JellyfinClient.PlaybackStart>> resolvePlayback(String itemId, long startPositionMs) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				PixelReelConfig config = ConfigManager.get();
				if (!config.isEmbyConfigured()) {
					return Optional.empty();
				}
				JellyfinClient client = this.client(config);
				String userId = this.ensureUserId(client);
				long ticks = Math.max(0L, startPositionMs) * 10_000L;
				return Optional.of(client.startPlayback(userId, itemId, ticks));
			} catch (Exception e) {
				PixelReel.LOGGER.warn("Failed to resolve Emby playback for {}: {}", itemId, JellyfinClient.describeError(e));
				return Optional.empty();
			}
		}, this.executor);
	}

	public String buildStreamUrl(String itemId, String mediaSourceId, String playSessionId, int subtitleIndex) {
		PixelReelConfig config = ConfigManager.get();
		if (!config.isEmbyConfigured()) {
			return "";
		}
		return this.client(config).buildStreamUrl(itemId, mediaSourceId, playSessionId, subtitleIndex);
	}

	public String buildSubtitleUrl(String itemId, String mediaSourceId, int subtitleIndex) {
		PixelReelConfig config = ConfigManager.get();
		if (!config.isEmbyConfigured()) {
			return "";
		}
		return this.client(config).buildSubtitleUrl(itemId, mediaSourceId, subtitleIndex);
	}

	public Optional<JellyfinItemSummary> findNextEpisode(String seriesId, String seasonId, int episodeNumber) {
		List<JellyfinItemSummary> episodes = this.episodesBySeason.getOrDefault(seasonId, List.of());
		for (JellyfinItemSummary episode : episodes) {
			if (episode.indexNumber() > episodeNumber) {
				return Optional.of(episode);
			}
		}
		List<JellyfinItemSummary> seasons = this.seasonsBySeries.getOrDefault(seriesId, List.of());
		int currentSeasonIndex = -1;
		for (int i = 0; i < seasons.size(); i++) {
			if (seasons.get(i).id().equals(seasonId)) {
				currentSeasonIndex = i;
				break;
			}
		}
		if (currentSeasonIndex < 0) {
			return Optional.empty();
		}
		for (int i = currentSeasonIndex + 1; i < seasons.size(); i++) {
			JellyfinItemSummary nextSeason = seasons.get(i);
			List<JellyfinItemSummary> nextEpisodes = this.episodesBySeason.getOrDefault(nextSeason.id(), List.of());
			if (!nextEpisodes.isEmpty()) {
				return Optional.of(nextEpisodes.get(0));
			}
		}
		return Optional.empty();
	}

	public CompletableFuture<Optional<JellyfinItemSummary>> resolveNextEpisode(String seriesId, String seasonId, int episodeNumber) {
		return this.seasons(seriesId, false).thenCompose(seasons -> {
			Optional<JellyfinItemSummary> local = this.findNextEpisode(seriesId, seasonId, episodeNumber);
			if (local.isPresent()) {
				return CompletableFuture.completedFuture(local);
			}
			List<CompletableFuture<List<JellyfinItemSummary>>> loads = new ArrayList<>();
			boolean passedCurrent = false;
			for (JellyfinItemSummary season : seasons) {
				if (season.id().equals(seasonId)) {
					passedCurrent = true;
					loads.add(this.episodes(season.id(), false));
					continue;
				}
				if (passedCurrent) {
					loads.add(this.episodes(season.id(), false));
				}
			}
			if (loads.isEmpty()) {
				return CompletableFuture.completedFuture(Optional.empty());
			}
			return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new))
				.thenApply(v -> this.findNextEpisode(seriesId, seasonId, episodeNumber));
		});
	}

	public void reportPlaying(String itemId, String mediaSourceId, String playSessionId, long positionMs, boolean paused) {
		this.executor.execute(() -> {
			try {
				PixelReelConfig config = ConfigManager.get();
				if (!config.isEmbyConfigured()) {
					return;
				}
				this.client(config).reportPlaying(itemId, mediaSourceId, playSessionId, positionMs * 10_000L, paused);
			} catch (Exception e) {
				PixelReel.LOGGER.debug("Emby playing report skipped: {}", e.toString());
			}
		});
	}

	public void reportProgress(String itemId, String mediaSourceId, String playSessionId, long positionMs, boolean paused) {
		this.executor.execute(() -> {
			try {
				PixelReelConfig config = ConfigManager.get();
				if (!config.isEmbyConfigured()) {
					return;
				}
				this.client(config).reportProgress(itemId, mediaSourceId, playSessionId, positionMs * 10_000L, paused);
			} catch (Exception e) {
				PixelReel.LOGGER.debug("Emby progress report skipped: {}", e.toString());
			}
		});
	}

	public void reportStopped(String itemId, String mediaSourceId, String playSessionId, long positionMs, boolean completed) {
		this.executor.execute(() -> {
			try {
				PixelReelConfig config = ConfigManager.get();
				if (!config.isEmbyConfigured()) {
					return;
				}
				this.client(config).reportStopped(itemId, mediaSourceId, playSessionId, positionMs * 10_000L, completed);
			} catch (Exception e) {
				PixelReel.LOGGER.debug("Emby stopped report skipped: {}", e.toString());
			}
		});
	}

	public CompletableFuture<List<JellyfinLibrary>> discoverLibraries() {
		return CompletableFuture.supplyAsync(() -> {
			try {
				PixelReelConfig config = ConfigManager.get();
				if (!config.isEmbyConfigured()) {
					return List.<JellyfinLibrary>of();
				}
				JellyfinClient client = this.client(config);
				String userId = this.ensureUserId(client);
				List<JellyfinLibrary> discovered = client.listLibraries(userId);
				this.libraries = List.copyOf(discovered);
				return discovered;
			} catch (Exception e) {
				PixelReel.LOGGER.warn("Failed to list Emby libraries: {}", JellyfinClient.describeError(e));
				this.lastStatus = e instanceof JellyfinClient.JellyfinAuthException
					? JellyfinStatus.authFailed(JellyfinClient.sanitizeDetail(JellyfinClient.describeError(e)))
					: JellyfinStatus.offline(JellyfinClient.sanitizeDetail(JellyfinClient.describeError(e)));
				return List.<JellyfinLibrary>of();
			}
		}, this.executor);
	}

	private JellyfinStatus scanBlocking(PixelReelConfig config) {
		try {
			JellyfinClient client = this.client(config);
			client.getSystemInfo();
			String userId = this.ensureUserId(client);
			List<JellyfinLibrary> discovered = client.listLibraries(userId);
			this.libraries = List.copyOf(discovered);

			List<JellyfinLibrary> enabled = enabledLibraries(discovered, config);
			if (enabled.isEmpty()) {
				this.clearCatalogue();
				this.lastStatus = JellyfinStatus.online(0, 0, "No permitted libraries are available");
				this.cacheFilledAtMillis = System.currentTimeMillis();
				return this.lastStatus;
			}

			List<String> movieParents = enabled.stream().filter(JellyfinLibrary::isMovies).map(JellyfinLibrary::id).toList();
			List<String> showParents = enabled.stream().filter(JellyfinLibrary::isTvShows).map(JellyfinLibrary::id).toList();

			List<JellyfinItemSummary> movieItems = List.of();
			List<JellyfinItemSummary> seriesItems = List.of();
			if (config.embyMoviesEnabled && !movieParents.isEmpty()) {
				movieItems = client.listItems(userId, movieParents, "Movie", null, true);
				movieItems.sort(Comparator.comparing(JellyfinItemSummary::title, String.CASE_INSENSITIVE_ORDER));
			}
			if (config.embyTvShowsEnabled && !showParents.isEmpty()) {
				seriesItems = client.listItems(userId, showParents, "Series", null, true);
				seriesItems.sort(Comparator.comparing(JellyfinItemSummary::title, String.CASE_INSENSITIVE_ORDER));
			}

			this.movies = List.copyOf(movieItems);
			this.series = List.copyOf(seriesItems);
			this.seasonsBySeries = Map.of();
			this.episodesBySeason = Map.of();
			Map<String, JellyfinItemSummary> index = new LinkedHashMap<>();
			for (JellyfinItemSummary item : movieItems) {
				index.put(item.id(), item);
			}
			for (JellyfinItemSummary item : seriesItems) {
				index.put(item.id(), item);
			}
			this.itemsById = Map.copyOf(index);
			this.cacheFilledAtMillis = System.currentTimeMillis();
			this.lastStatus = JellyfinStatus.online(
				movieItems.size(),
				seriesItems.size(),
				"Libraries ready"
			);
			PixelReel.LOGGER.info(
				"Emby library ready ({}): {} movie(s), {} series from {} library(ies)",
				JellyfinClient.hostOnly(config.embyUrl),
				movieItems.size(),
				seriesItems.size(),
				enabled.size()
			);
			return this.lastStatus;
		} catch (JellyfinClient.JellyfinAuthException e) {
			this.lastStatus = JellyfinStatus.authFailed(JellyfinClient.sanitizeDetail(e.getMessage()));
			PixelReel.LOGGER.warn("Emby authentication failed: {}", JellyfinClient.sanitizeDetail(e.getMessage()));
			return this.lastStatus;
		} catch (Exception e) {
			String detail = JellyfinClient.sanitizeDetail(JellyfinClient.describeError(e));
			this.lastStatus = JellyfinStatus.offline(detail);
			PixelReel.LOGGER.warn("Emby scan failed: {}", detail);
			return this.lastStatus;
		}
	}

	private static List<JellyfinLibrary> enabledLibraries(List<JellyfinLibrary> discovered, PixelReelConfig config) {
		List<String> selected = config.embyLibraryIds == null ? List.of() : config.embyLibraryIds;
		return discovered.stream()
			.filter(library -> library.isMovies() || library.isTvShows())
			.filter(library -> {
				if (library.isMovies() && !config.embyMoviesEnabled) {
					return false;
				}
				if (library.isTvShows() && !config.embyTvShowsEnabled) {
					return false;
				}
				return selected.isEmpty() || selected.contains(library.id());
			})
			.collect(Collectors.toList());
	}

	private void clearCatalogue() {
		this.movies = List.of();
		this.series = List.of();
		this.seasonsBySeries = Map.of();
		this.episodesBySeason = Map.of();
		this.itemsById = Map.of();
	}

	private void index(List<JellyfinItemSummary> items) {
		Map<String, JellyfinItemSummary> copy = new LinkedHashMap<>(this.itemsById);
		for (JellyfinItemSummary item : items) {
			copy.put(item.id(), item);
		}
		this.itemsById = Map.copyOf(copy);
	}

	private String ensureUserId(JellyfinClient client) throws Exception {
		PixelReelConfig config = ConfigManager.get();
		if (!config.embyUserId.isBlank()) {
			this.resolvedUserId = config.embyUserId.trim();
			return this.resolvedUserId;
		}
		if (!this.resolvedUserId.isBlank()) {
			return this.resolvedUserId;
		}
		String userId = client.resolveUserId();
		this.resolvedUserId = userId;
		if (!userId.isBlank() && ConfigManager.get().embyUserId.isBlank()) {
			ConfigManager.update(updated -> updated.embyUserId = userId);
			PixelReel.LOGGER.info("Saved auto-resolved Emby user id to config");
		}
		return userId;
	}

	private JellyfinClient client(PixelReelConfig config) {
		return new JellyfinClient(JellyfinClient.createHttpClient(config, this.executor), config.embyConnection(), config.networkTimeoutSeconds);
	}

	private static List<JellyfinItemSummary> filter(List<JellyfinItemSummary> source, @Nullable String search) {
		if (search == null || search.isBlank()) {
			return source;
		}
		String q = search.trim().toLowerCase(Locale.ROOT);
		List<JellyfinItemSummary> filtered = new ArrayList<>();
		for (JellyfinItemSummary item : source) {
			if (item.title().toLowerCase(Locale.ROOT).contains(q) || item.seriesName().toLowerCase(Locale.ROOT).contains(q)) {
				filtered.add(item);
			}
		}
		return filtered;
	}

	private static Page pageOf(List<JellyfinItemSummary> items, int page) {
		int safePage = Math.max(0, page);
		int from = safePage * PAGE_SIZE;
		if (from >= items.size()) {
			return new Page(List.of(), safePage, items.size());
		}
		int to = Math.min(items.size(), from + PAGE_SIZE);
		return new Page(items.subList(from, to), safePage, items.size());
	}

	public record Page(List<JellyfinItemSummary> items, int page, int totalCount) {
		public int totalPages() {
			return this.totalCount == 0 ? 0 : (this.totalCount + PAGE_SIZE - 1) / PAGE_SIZE;
		}
	}
}
