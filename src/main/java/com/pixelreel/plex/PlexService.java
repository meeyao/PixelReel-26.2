package com.pixelreel.plex;

import com.pixelreel.PixelReel;
import com.pixelreel.config.ConfigManager;
import com.pixelreel.config.PixelReelConfig;
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

/** server-side Plex cache */
public final class PlexService {
	public static final PlexService INSTANCE = new PlexService();
	public static final int PAGE_SIZE = 48;

	private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
		Thread thread = new Thread(runnable, "pixelreel-plex");
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
	private @Nullable CompletableFuture<JellyfinStatus> inFlight;

	private PlexService() {
	}

	public JellyfinStatus lastStatus() {
		return this.lastStatus;
	}

	public List<JellyfinLibrary> libraries() {
		return this.libraries;
	}

	public boolean isCacheFresh() {
		PixelReelConfig config = ConfigManager.get();
		return this.lastStatus.authenticated()
			&& System.currentTimeMillis() - this.cacheFilledAtMillis < config.plexLibraryCacheSeconds * 1000L;
	}

	public void invalidateCache() {
		this.cacheFilledAtMillis = 0L;
	}

	public synchronized CompletableFuture<JellyfinStatus> refresh(boolean force) {
		PixelReelConfig config = ConfigManager.get();
		if (!config.isPlexConfigured()) {
			this.lastStatus = JellyfinStatus.notConfigured();
			this.clearCatalogue();
			return CompletableFuture.completedFuture(this.lastStatus);
		}
		if (!isUrlValid(config.plexUrl)) {
			this.lastStatus = JellyfinStatus.offline("Invalid Plex URL");
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
				if (!config.isPlexConfigured()) {
					return Optional.empty();
				}
				JellyfinItemSummary item = this.client(config).getItem(itemId);
				if (item != null) {
					Map<String, JellyfinItemSummary> copy = new LinkedHashMap<>(this.itemsById);
					copy.put(item.id(), item);
					this.itemsById = Map.copyOf(copy);
				}
				return Optional.ofNullable(item);
			} catch (Exception e) {
				PixelReel.LOGGER.warn("Failed to fetch Plex item {}: {}", itemId, PlexClient.describeError(e));
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
				List<JellyfinItemSummary> seasons = this.client(config).listChildren(seriesId);
				seasons.sort(Comparator.comparingInt(JellyfinItemSummary::indexNumber).thenComparing(JellyfinItemSummary::title));
				Map<String, List<JellyfinItemSummary>> copy = new LinkedHashMap<>(this.seasonsBySeries);
				copy.put(seriesId, List.copyOf(seasons));
				this.seasonsBySeries = Map.copyOf(copy);
				this.index(seasons);
				return seasons;
			} catch (Exception e) {
				PixelReel.LOGGER.warn("Failed to load Plex seasons for {}: {}", seriesId, PlexClient.describeError(e));
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
				List<JellyfinItemSummary> episodes = this.client(config).listChildren(seasonId);
				episodes.sort(Comparator.comparingInt(JellyfinItemSummary::indexNumber).thenComparing(JellyfinItemSummary::title));
				Map<String, List<JellyfinItemSummary>> copy = new LinkedHashMap<>(this.episodesBySeason);
				copy.put(seasonId, List.copyOf(episodes));
				this.episodesBySeason = Map.copyOf(copy);
				this.index(episodes);
				return episodes;
			} catch (Exception e) {
				PixelReel.LOGGER.warn("Failed to load Plex episodes for {}: {}", seasonId, PlexClient.describeError(e));
				return cached;
			}
		}, this.executor);
	}

	public CompletableFuture<Optional<PlexClient.PlaybackStart>> resolvePlayback(String itemId, long startPositionMs) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				PixelReelConfig config = ConfigManager.get();
				if (!config.isPlexConfigured()) {
					return Optional.empty();
				}
				return Optional.of(this.client(config).startPlayback(itemId, startPositionMs));
			} catch (Exception e) {
				PixelReel.LOGGER.warn("Failed to resolve Plex playback for {}: {}", itemId, PlexClient.describeError(e));
				return Optional.empty();
			}
		}, this.executor);
	}

	public String buildStreamUrl(
		String ratingKey,
		int mediaIndex,
		int partIndex,
		String partKey,
		String playSessionId,
		int subtitleStreamId,
		long startPositionMs
	) {
		PixelReelConfig config = ConfigManager.get();
		if (!config.isPlexConfigured()) {
			return "";
		}
		return this.client(config).buildStreamUrl(
			ratingKey, mediaIndex, partIndex, partKey, playSessionId, subtitleStreamId, startPositionMs
		);
	}

	public String buildSubtitleUrl(int subtitleStreamId) {
		PixelReelConfig config = ConfigManager.get();
		if (!config.isPlexConfigured()) {
			return "";
		}
		return this.client(config).buildSubtitleUrl(subtitleStreamId);
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
				return Optional.of(nextEpisodes.getFirst());
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

	public void reportTimeline(String itemId, String state, long positionMs, long durationMs) {
		this.executor.execute(() -> {
			try {
				PixelReelConfig config = ConfigManager.get();
				if (!config.isPlexConfigured()) {
					return;
				}
				this.client(config).reportTimeline(itemId, state, positionMs, durationMs);
			} catch (Exception e) {
				PixelReel.LOGGER.debug("Plex timeline report skipped: {}", e.toString());
			}
		});
	}

	public CompletableFuture<List<JellyfinLibrary>> discoverLibraries() {
		return CompletableFuture.supplyAsync(() -> {
			try {
				PixelReelConfig config = ConfigManager.get();
				if (!config.isPlexConfigured()) {
					return List.<JellyfinLibrary>of();
				}
				List<JellyfinLibrary> discovered = this.client(config).listLibraries();
				this.libraries = List.copyOf(discovered);
				return discovered;
			} catch (Exception e) {
				PixelReel.LOGGER.warn("Failed to list Plex libraries: {}", PlexClient.describeError(e));
				this.lastStatus = e instanceof PlexClient.PlexAuthException
					? JellyfinStatus.authFailed(PlexClient.sanitizeDetail(PlexClient.describeError(e)))
					: JellyfinStatus.offline(PlexClient.sanitizeDetail(PlexClient.describeError(e)));
				return List.<JellyfinLibrary>of();
			}
		}, this.executor);
	}

	private JellyfinStatus scanBlocking(PixelReelConfig config) {
		try {
			PlexClient client = this.client(config);
			client.ping();
			List<JellyfinLibrary> discovered = client.listLibraries();
			this.libraries = List.copyOf(discovered);

			List<JellyfinLibrary> enabled = enabledLibraries(discovered, config);
			if (enabled.isEmpty()) {
				this.clearCatalogue();
				this.lastStatus = JellyfinStatus.online(0, 0, "No permitted libraries are available");
				this.cacheFilledAtMillis = System.currentTimeMillis();
				return this.lastStatus;
			}

			List<JellyfinItemSummary> movieItems = new ArrayList<>();
			List<JellyfinItemSummary> seriesItems = new ArrayList<>();
			for (JellyfinLibrary library : enabled) {
				if (library.isMovies() && config.plexMoviesEnabled) {
					movieItems.addAll(client.listSectionItems(library.id(), "movie"));
				}
				if (library.isTvShows() && config.plexTvShowsEnabled) {
					seriesItems.addAll(client.listSectionItems(library.id(), "show"));
				}
			}
			movieItems.sort(Comparator.comparing(JellyfinItemSummary::title, String.CASE_INSENSITIVE_ORDER));
			seriesItems.sort(Comparator.comparing(JellyfinItemSummary::title, String.CASE_INSENSITIVE_ORDER));

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
			this.lastStatus = JellyfinStatus.online(movieItems.size(), seriesItems.size(), "Libraries ready");
			PixelReel.LOGGER.info(
				"Plex library ready: {} movie(s), {} series from {} library(ies)",
				movieItems.size(),
				seriesItems.size(),
				enabled.size()
			);
			return this.lastStatus;
		} catch (PlexClient.PlexAuthException e) {
			this.lastStatus = JellyfinStatus.authFailed(PlexClient.sanitizeDetail(e.getMessage()));
			PixelReel.LOGGER.warn("Plex authentication failed: {}", PlexClient.sanitizeDetail(e.getMessage()));
			return this.lastStatus;
		} catch (Exception e) {
			String detail = PlexClient.sanitizeDetail(PlexClient.describeError(e));
			this.lastStatus = JellyfinStatus.offline(detail);
			PixelReel.LOGGER.warn("Plex scan failed: {}", detail);
			return this.lastStatus;
		}
	}

	private static List<JellyfinLibrary> enabledLibraries(List<JellyfinLibrary> discovered, PixelReelConfig config) {
		List<String> selected = config.plexLibraryKeys == null ? List.of() : config.plexLibraryKeys;
		return discovered.stream()
			.filter(library -> library.isMovies() || library.isTvShows())
			.filter(library -> {
				if (library.isMovies() && !config.plexMoviesEnabled) {
					return false;
				}
				if (library.isTvShows() && !config.plexTvShowsEnabled) {
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

	private PlexClient client(PixelReelConfig config) {
		return new PlexClient(PlexClient.createHttpClient(config, this.executor), config);
	}

	private static boolean isUrlValid(String url) {
		if (url == null || url.isBlank()) {
			return false;
		}
		String lower = url.trim().toLowerCase(Locale.ROOT);
		return lower.startsWith("http://") || lower.startsWith("https://");
	}

	private static List<JellyfinItemSummary> filter(List<JellyfinItemSummary> source, @Nullable String search) {
		if (search == null || search.isBlank()) {
			return source;
		}
		String q = search.trim().toLowerCase(Locale.ROOT);
		List<JellyfinItemSummary> filtered = new ArrayList<>();
		for (JellyfinItemSummary item : itemsOrEmpty(source)) {
			if (item.title().toLowerCase(Locale.ROOT).contains(q) || item.seriesName().toLowerCase(Locale.ROOT).contains(q)) {
				filtered.add(item);
			}
		}
		return filtered;
	}

	private static List<JellyfinItemSummary> itemsOrEmpty(List<JellyfinItemSummary> source) {
		return source == null ? List.of() : source;
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
