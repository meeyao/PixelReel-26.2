package com.pixelreel.networking;

import com.pixelreel.PixelReel;
import com.pixelreel.blockentities.DisplayBlockEntity;
import com.pixelreel.channels.ChannelEntry;
import com.pixelreel.channels.ChannelService;
import com.pixelreel.channels.LiveStatus;
import com.pixelreel.config.ConfigManager;
import com.pixelreel.config.PixelReelConfig;
import com.pixelreel.emby.EmbyService;
import com.pixelreel.jellyfin.JellyfinItemKind;
import com.pixelreel.jellyfin.JellyfinItemSummary;
import com.pixelreel.jellyfin.JellyfinLibrary;
import com.pixelreel.jellyfin.JellyfinService;
import com.pixelreel.jellyfin.JellyfinStatus;
import com.pixelreel.ondemand.OnDemandCatalog;
import com.pixelreel.ondemand.OnDemandProvider;
import com.pixelreel.permissions.CinemaPermissions;
import com.pixelreel.plex.PlexService;
import com.pixelreel.server.ScreenControllerLogic;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ServerNetworking {
	private static final ServerRateLimit PLAYBACK_URL_RATE = new ServerRateLimit(30_000, 30);
	private static final ServerRateLimit POSTER_RATE = new ServerRateLimit(10_000, 120);

	private ServerNetworking() {
	}

	public static void register() {
		ServerPlayNetworking.registerGlobalReceiver(
			ModNetworkPayloads.ScreenControl.TYPE, (payload, context) -> handleControl(payload, context.player())
		);
		ServerPlayNetworking.registerGlobalReceiver(
			ModNetworkPayloads.ScreenTune.TYPE, (payload, context) -> handleTune(payload, context.player())
		);
		ServerPlayNetworking.registerGlobalReceiver(
			ModNetworkPayloads.RequestPlaybackUrl.TYPE, (payload, context) -> handleRequestPlaybackUrl(payload, context.player())
		);
		ServerPlayNetworking.registerGlobalReceiver(
			ModNetworkPayloads.RequestPoster.TYPE, (payload, context) -> handleRequestPoster(payload, context.player())
		);
		ServerPlayNetworking.registerGlobalReceiver(
			ModNetworkPayloads.RequestChannels.TYPE, (payload, context) -> sendChannelList(context.player(), payload.forceRefresh())
		);
		ServerPlayNetworking.registerGlobalReceiver(
			ModNetworkPayloads.RequestMediaFeatures.TYPE, (payload, context) -> sendMediaFeatures(context.player())
		);
		ServerPlayNetworking.registerGlobalReceiver(
			ModNetworkPayloads.RequestJellyfinBrowse.TYPE, (payload, context) -> handleBrowse(payload, context.player())
		);
		ServerPlayNetworking.registerGlobalReceiver(
			ModNetworkPayloads.RequestJellyfinChildren.TYPE, (payload, context) -> handleChildren(payload, context.player())
		);
		ServerPlayNetworking.registerGlobalReceiver(
			ModNetworkPayloads.ScreenPlayJellyfin.TYPE, (payload, context) -> handlePlayJellyfin(payload, context.player())
		);
		ServerPlayNetworking.registerGlobalReceiver(
			ModNetworkPayloads.ReportMediaEnded.TYPE, (payload, context) -> handleMediaEnded(payload, context.player())
		);
		ServerPlayNetworking.registerGlobalReceiver(
			ModNetworkPayloads.RequestJellyfinConfig.TYPE, (payload, context) -> handleRequestJellyfinConfig(context.player())
		);
		ServerPlayNetworking.registerGlobalReceiver(
			ModNetworkPayloads.UpdateJellyfinConfig.TYPE, (payload, context) -> handleUpdateJellyfinConfig(payload, context.player())
		);
		ServerPlayNetworking.registerGlobalReceiver(
			ModNetworkPayloads.RequestEmbyConfig.TYPE, (payload, context) -> handleRequestEmbyConfig(context.player())
		);
		ServerPlayNetworking.registerGlobalReceiver(
			ModNetworkPayloads.UpdateEmbyConfig.TYPE, (payload, context) -> handleUpdateEmbyConfig(payload, context.player())
		);
		ServerPlayNetworking.registerGlobalReceiver(
			ModNetworkPayloads.RequestPlexConfig.TYPE, (payload, context) -> handleRequestPlexConfig(context.player())
		);
		ServerPlayNetworking.registerGlobalReceiver(
			ModNetworkPayloads.UpdatePlexConfig.TYPE, (payload, context) -> handleUpdatePlexConfig(payload, context.player())
		);
		ServerPlayNetworking.registerGlobalReceiver(
			ModNetworkPayloads.RefreshJellyfinLibrary.TYPE, (payload, context) -> handleRefreshLibrary(context.player())
		);
		ServerPlayNetworking.registerGlobalReceiver(
			ModNetworkPayloads.RequestTunarrConfig.TYPE, (payload, context) -> handleRequestTunarrConfig(context.player())
		);
		ServerPlayNetworking.registerGlobalReceiver(
			ModNetworkPayloads.UpdateTunarrConfig.TYPE, (payload, context) -> handleUpdateTunarrConfig(payload, context.player())
		);
		ServerPlayNetworking.registerGlobalReceiver(
			ModNetworkPayloads.UnequipPixelGlasses.TYPE,
			(payload, context) -> context.server().execute(() ->
				com.pixelreel.items.PixelGlassesItem.tryUnequip(context.player())
			)
		);
	}

	private static void handleControl(ModNetworkPayloads.ScreenControl payload, ServerPlayer player) {
		DisplayBlockEntity display = ScreenControllerLogic.resolve(player, payload.pos());
		if (display == null) {
			PixelReel.LOGGER.debug("Rejected screen control from {} at {}: no reachable display", player.getName().getString(), payload.pos().toShortString());
			return;
		}
		if (!mayControl(player, payload.action())) {
			notify(player, payload.pos(), ScreenControllerLogic.Outcome.NO_PERMISSION);
			return;
		}
		ScreenControllerLogic.Outcome outcome = ScreenControllerLogic.apply(
			display, payload.action(), payload.value(), player.getGameProfile().name()
		);
		notify(player, payload.pos(), outcome);
	}

	private static boolean mayControl(ServerPlayer player, ScreenAction action) {
		return switch (action) {
			case POWER_TOGGLE, POWER_ON, POWER_OFF, VOLUME_SET, CHANNEL_NEXT, CHANNEL_PREVIOUS -> CinemaPermissions.canChangeContent(player)
				|| CinemaPermissions.canPlayTunarr(player);
			case STOP -> CinemaPermissions.canStop(player);
			case RESUME, UNPAUSE, PAUSE, PAUSE_TOGGLE -> CinemaPermissions.canPause(player);
			case SEEK, SEEK_FORWARD, SEEK_BACKWARD, RESTART -> CinemaPermissions.canSeek(player);
			case PLAY_NEXT_NOW, CANCEL_NEXT, CYCLE_SUBTITLE, SELECT_SUBTITLE -> CinemaPermissions.canChangeContent(player);
			// Any nearby viewer may fill in duration once VLC discovers it (like ReportMediaEnded).
			case REPORT_DURATION -> true;
		};
	}

	private static void handleTune(ModNetworkPayloads.ScreenTune payload, ServerPlayer player) {
		if (!CinemaPermissions.canPlayTunarr(player)) {
			notify(player, payload.pos(), ScreenControllerLogic.Outcome.NO_PERMISSION);
			return;
		}
		DisplayBlockEntity display = ScreenControllerLogic.resolve(player, payload.pos());
		if (display == null) {
			PixelReel.LOGGER.warn("Rejected channel change from {} at {}: no reachable display", player.getGameProfile().name(), payload.pos().toShortString());
			return;
		}
		ScreenControllerLogic.Outcome outcome = ScreenControllerLogic.tune(display, payload.channelId());
		PixelReel.LOGGER.info(
			"Channel tune from {} at {} -> {} ({})",
			player.getGameProfile().name(),
			payload.pos().toShortString(),
			payload.channelId(),
			outcome
		);
		notify(player, payload.pos(), outcome);
	}

	private static void handleRequestPlaybackUrl(ModNetworkPayloads.RequestPlaybackUrl payload, ServerPlayer player) {
		if (!PLAYBACK_URL_RATE.allow(player.getUUID())) {
			return;
		}
		double maxDistance = Math.max(1.0, ConfigManager.get().maximumPlaybackDistance) * 1.75;
		DisplayBlockEntity display = resolvePlaybackDisplay(player, payload.pos(), maxDistance);
		if (display == null || display.getChannelEpoch() != payload.channelEpoch() || !display.shouldPlay()) {
			return;
		}
		if (!canReceivePlaybackUrl(player, display)) {
			return;
		}
		if (player.distanceToSqr(display.screenCentre()) > maxDistance * maxDistance) {
			return;
		}
		PixelReelConfig config = ConfigManager.get();
		int proxyPort = config.proxyPort;
		String proxyHost = config.proxyHost;
		String streamUrl;
		String subtitleUrl;
		if (display.isOnDemand() && MediaProxy.INSTANCE.isActive()) {
			String displayKey = MediaProxy.displayKey(player.level().dimension().identifier().toString(), display.getBlockPos());
			streamUrl = proxyPath("/stream", MediaProxy.INSTANCE.issue(displayKey, display.getStreamUrl()));
			subtitleUrl = proxyPath("/subtitle", MediaProxy.INSTANCE.issue(displayKey, display.getSubtitleFetchUrl()));
		} else {
			streamUrl = display.getStreamUrl();
			subtitleUrl = display.getSubtitleFetchUrl();
		}
		ServerPlayNetworking.send(
			player,
			new ModNetworkPayloads.PlaybackUrl(
				display.getBlockPos(),
				display.getChannelEpoch(),
				proxyPort,
				proxyHost,
				streamUrl,
				subtitleUrl
			)
		);
	}

	private static String proxyPath(String prefix, String token) {
		return token == null || token.isBlank() ? "" : prefix + "/" + token;
	}

	private static void handleRequestPoster(ModNetworkPayloads.RequestPoster payload, ServerPlayer player) {
		if (!CinemaPermissions.canBrowse(player)) {
			return;
		}
		String url = "";
		if (POSTER_RATE.allow(player.getUUID())) {
			url = OnDemandCatalog.find(payload.provider(), payload.itemId())
				.map(JellyfinItemSummary::imageUrl)
				.orElse("");
		}
		PixelReelConfig config = ConfigManager.get();
		String deliverable = url;
		if (MediaProxy.INSTANCE.isActive() && !url.isBlank()) {
			deliverable = proxyPath("/poster", MediaProxy.INSTANCE.issuePoster(url));
		}
		ServerPlayNetworking.send(
			player,
			new ModNetworkPayloads.PosterUrl(payload.provider(), payload.itemId(), deliverable, config.proxyHost, config.proxyPort)
		);
	}

	private static DisplayBlockEntity resolvePlaybackDisplay(ServerPlayer player, BlockPos pos, double maxDistance) {
		if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) {
			return null;
		}
		if (!level.isInWorldBounds(pos) || !level.isLoaded(pos)) {
			return null;
		}
		if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > maxDistance * maxDistance) {
			return null;
		}
		return com.pixelreel.blocks.DisplayBlock.controllerAt(level, pos);
	}

	private static boolean canReceivePlaybackUrl(ServerPlayer player, DisplayBlockEntity display) {
		if (!display.isOnDemand()) {
			return CinemaPermissions.canPlayTunarr(player);
		}
		return switch (display.getJellyfinKind()) {
			case MOVIE -> CinemaPermissions.canPlayOnDemandMovies(player);
			case SERIES, SEASON, EPISODE -> CinemaPermissions.canPlayOnDemandShows(player);
			default -> CinemaPermissions.canControlPlayback(player);
		};
	}

	private static void handleBrowse(ModNetworkPayloads.RequestJellyfinBrowse payload, ServerPlayer player) {
		if (!CinemaPermissions.canBrowse(player)) {
			notify(player, BlockPos.ZERO, ScreenControllerLogic.Outcome.NO_PERMISSION);
			return;
		}
		OnDemandProvider provider = payload.provider();
		boolean movies = payload.kind() == ModNetworkPayloads.BrowseKind.MOVIES;
		if (movies && !CinemaPermissions.canPlayOnDemandMovies(player)) {
			notify(player, BlockPos.ZERO, ScreenControllerLogic.Outcome.NO_PERMISSION);
			return;
		}
		if (!movies && !CinemaPermissions.canPlayOnDemandShows(player)) {
			notify(player, BlockPos.ZERO, ScreenControllerLogic.Outcome.NO_PERMISSION);
			return;
		}
		if (!OnDemandCatalog.isConfigured(provider)) {
			ServerPlayNetworking.send(
				player,
				new ModNetworkPayloads.JellyfinBrowseResult(
					provider,
					payload.kind(),
					payload.search(),
					payload.page(),
					0,
					OnDemandCatalog.lastStatus(provider),
					List.of()
				)
			);
			return;
		}
		OnDemandCatalog.refresh(provider, payload.forceRefresh()).whenComplete((status, error) -> runOnServer(player, () -> {
			OnDemandCatalog.Page page = OnDemandCatalog.page(provider, payload.kind(), payload.search(), payload.page());
			List<JellyfinItemSummary> slim = page.items().stream().map(JellyfinItemSummary::forBrowsePacket).toList();
			ServerPlayNetworking.send(
				player,
				new ModNetworkPayloads.JellyfinBrowseResult(
					provider,
					payload.kind(),
					payload.search(),
					page.page(),
					page.totalCount(),
					OnDemandCatalog.lastStatus(provider),
					slim
				)
			);
		}));
	}

	private static void handleChildren(ModNetworkPayloads.RequestJellyfinChildren payload, ServerPlayer player) {
		if (!CinemaPermissions.canBrowse(player)) {
			notify(player, BlockPos.ZERO, ScreenControllerLogic.Outcome.NO_PERMISSION);
			return;
		}
		boolean canShows = CinemaPermissions.canPlayOnDemandShows(player);
		boolean canMovies = CinemaPermissions.canPlayOnDemandMovies(player);
		// Seasons/episodes need shows; ITEM refetch is used for movie detail descriptions too.
		if (payload.kind() == ModNetworkPayloads.ChildrenKind.ITEM) {
			if (!canShows && !canMovies) {
				notify(player, BlockPos.ZERO, ScreenControllerLogic.Outcome.NO_PERMISSION);
				return;
			}
		} else if (!canShows) {
			notify(player, BlockPos.ZERO, ScreenControllerLogic.Outcome.NO_PERMISSION);
			return;
		}
		OnDemandProvider provider = payload.provider();
		if (!OnDemandCatalog.isConfigured(provider)) {
			sendChildren(player, payload, List.of());
			return;
		}
		switch (payload.kind()) {
			case SEASONS -> OnDemandCatalog.seasons(provider, payload.parentId(), payload.forceRefresh())
				.whenComplete((items, error) -> runOnServer(player, () -> sendChildren(player, payload, items)));
			case EPISODES -> OnDemandCatalog.episodes(provider, payload.parentId(), payload.forceRefresh())
				.whenComplete((items, error) -> runOnServer(player, () -> sendChildren(player, payload, items)));
			case ITEM -> OnDemandCatalog.fetchItem(provider, payload.parentId())
				.whenComplete((item, error) -> runOnServer(player, () -> {
					List<JellyfinItemSummary> items = item.isPresent() ? List.of(item.get().forClientPacket()) : List.of();
					sendChildren(player, payload, items);
				}));
		}
	}

	private static void sendChildren(
		ServerPlayer player,
		ModNetworkPayloads.RequestJellyfinChildren payload,
		List<JellyfinItemSummary> items
	) {
		if (player.hasDisconnected()) {
			return;
		}
		ServerPlayNetworking.send(
			player,
			new ModNetworkPayloads.JellyfinChildrenResult(
				payload.provider(),
				payload.kind(),
				payload.parentId(),
				OnDemandCatalog.lastStatus(payload.provider()),
				items == null ? List.of() : items.stream().map(JellyfinItemSummary::forClientPacket).toList()
			)
		);
	}

	private static void handlePlayJellyfin(ModNetworkPayloads.ScreenPlayJellyfin payload, ServerPlayer player) {
		DisplayBlockEntity display = ScreenControllerLogic.resolve(player, payload.pos());
		if (display == null) {
			notify(player, payload.pos(), ScreenControllerLogic.Outcome.NO_SCREEN);
			return;
		}
		if (!CinemaPermissions.canChangeContent(player)) {
			notify(player, payload.pos(), ScreenControllerLogic.Outcome.NO_PERMISSION);
			return;
		}
		OnDemandProvider provider = payload.provider();
		if (!OnDemandCatalog.isConfigured(provider)) {
			notify(player, payload.pos(), ScreenControllerLogic.Outcome.JELLYFIN_UNAVAILABLE);
			return;
		}
		OnDemandCatalog.fetchItem(provider, payload.itemId()).whenComplete((itemOpt, error) -> runOnServer(player, () -> {
			if (itemOpt.isEmpty()) {
				notify(player, payload.pos(), ScreenControllerLogic.Outcome.ITEM_UNAVAILABLE);
				return;
			}
			JellyfinItemSummary item = itemOpt.get();
			if (item.kind() == JellyfinItemKind.MOVIE && !CinemaPermissions.canPlayOnDemandMovies(player)) {
				notify(player, payload.pos(), ScreenControllerLogic.Outcome.NO_PERMISSION);
				return;
			}
			if ((item.kind() == JellyfinItemKind.EPISODE || item.kind() == JellyfinItemKind.SERIES || item.kind() == JellyfinItemKind.SEASON)
				&& !CinemaPermissions.canPlayOnDemandShows(player)) {
				notify(player, payload.pos(), ScreenControllerLogic.Outcome.NO_PERMISSION);
				return;
			}
			if (item.kind() != JellyfinItemKind.MOVIE && item.kind() != JellyfinItemKind.EPISODE) {
				notify(player, payload.pos(), ScreenControllerLogic.Outcome.ITEM_UNAVAILABLE);
				return;
			}
			ScreenControllerLogic.playOnDemandItem(
				display, provider, item.id(), payload.startPositionMs(), player.getGameProfile().name()
			);
		}));
	}

	private static void handleMediaEnded(ModNetworkPayloads.ReportMediaEnded payload, ServerPlayer player) {
		DisplayBlockEntity display = ScreenControllerLogic.resolve(player, payload.pos());
		if (display == null || display.getChannelEpoch() != payload.channelEpoch()) {
			return;
		}
		ScreenControllerLogic.onMediaEnded(display);
	}

	private static void handleRequestJellyfinConfig(ServerPlayer player) {
		if (!CinemaPermissions.canConfigureJellyfin(player)) {
			notify(player, BlockPos.ZERO, ScreenControllerLogic.Outcome.NO_PERMISSION);
			return;
		}
		OnDemandCatalog.discoverLibraries(OnDemandProvider.JELLYFIN)
			.whenComplete((libraries, error) -> runOnServer(player, () -> sendJellyfinConfig(player, libraries)));
	}

	private static void handleUpdateJellyfinConfig(ModNetworkPayloads.UpdateJellyfinConfig payload, ServerPlayer player) {
		if (!CinemaPermissions.canConfigureJellyfin(player)) {
			notify(player, BlockPos.ZERO, ScreenControllerLogic.Outcome.NO_PERMISSION);
			return;
		}
		ConfigManager.update(config -> {
			config.jellyfinUrl = payload.url();
			if (payload.apiKey() != null && !payload.apiKey().isBlank()) {
				config.jellyfinApiKey = payload.apiKey();
			}
			config.jellyfinUserId = payload.userId() == null ? "" : payload.userId();
			config.jellyfinMoviesEnabled = payload.moviesEnabled();
			config.jellyfinTvShowsEnabled = payload.tvShowsEnabled();
			config.jellyfinAutoplayNextEpisode = payload.autoplayNextEpisode();
			config.onDemandAutoplayNextEpisode = payload.autoplayNextEpisode();
			config.jellyfinLibraryIds = new ArrayList<>(payload.libraryIds());
		});
		JellyfinService.INSTANCE.invalidateCache();
		JellyfinService.INSTANCE.refresh(true).whenComplete((status, error) -> runOnServer(player, () -> {
			sendMediaFeatures(player);
			OnDemandCatalog.discoverLibraries(OnDemandProvider.JELLYFIN).whenComplete((libraries, ignored) ->
				runOnServer(player, () -> sendJellyfinConfig(player, libraries))
			);
			String key = status != null && status.authenticated()
				? "message.pixelreel.jellyfin.config_saved"
				: "message.pixelreel.jellyfin.config_failed";
			ServerPlayNetworking.send(player, new ModNetworkPayloads.ScreenNotice(BlockPos.ZERO, key));
		}));
	}

	private static void handleRequestEmbyConfig(ServerPlayer player) {
		if (!CinemaPermissions.canConfigureEmby(player)) {
			notify(player, BlockPos.ZERO, ScreenControllerLogic.Outcome.NO_PERMISSION);
			return;
		}
		OnDemandCatalog.discoverLibraries(OnDemandProvider.EMBY)
			.whenComplete((libraries, error) -> runOnServer(player, () -> sendEmbyConfig(player, libraries)));
	}

	private static void handleUpdateEmbyConfig(ModNetworkPayloads.UpdateEmbyConfig payload, ServerPlayer player) {
		if (!CinemaPermissions.canConfigureEmby(player)) {
			notify(player, BlockPos.ZERO, ScreenControllerLogic.Outcome.NO_PERMISSION);
			return;
		}
		ConfigManager.update(config -> {
			config.embyUrl = payload.url();
			if (payload.apiKey() != null && !payload.apiKey().isBlank()) {
				config.embyApiKey = payload.apiKey();
			}
			config.embyUserId = payload.userId() == null ? "" : payload.userId();
			config.embyMoviesEnabled = payload.moviesEnabled();
			config.embyTvShowsEnabled = payload.tvShowsEnabled();
			config.embyLibraryIds = new ArrayList<>(payload.libraryIds());
		});
		EmbyService.INSTANCE.invalidateCache();
		EmbyService.INSTANCE.refresh(true).whenComplete((status, error) -> runOnServer(player, () -> {
			sendMediaFeatures(player);
			OnDemandCatalog.discoverLibraries(OnDemandProvider.EMBY).whenComplete((libraries, ignored) ->
				runOnServer(player, () -> sendEmbyConfig(player, libraries))
			);
			String key = status != null && status.authenticated()
				? "message.pixelreel.emby.config_saved"
				: "message.pixelreel.emby.config_failed";
			ServerPlayNetworking.send(player, new ModNetworkPayloads.ScreenNotice(BlockPos.ZERO, key));
		}));
	}

	private static void handleRequestPlexConfig(ServerPlayer player) {
		if (!CinemaPermissions.canConfigurePlex(player)) {
			notify(player, BlockPos.ZERO, ScreenControllerLogic.Outcome.NO_PERMISSION);
			return;
		}
		OnDemandCatalog.discoverLibraries(OnDemandProvider.PLEX)
			.whenComplete((libraries, error) -> runOnServer(player, () -> sendPlexConfig(player, libraries)));
	}

	private static void handleUpdatePlexConfig(ModNetworkPayloads.UpdatePlexConfig payload, ServerPlayer player) {
		if (!CinemaPermissions.canConfigurePlex(player)) {
			notify(player, BlockPos.ZERO, ScreenControllerLogic.Outcome.NO_PERMISSION);
			return;
		}
		ConfigManager.update(config -> {
			config.plexUrl = payload.url();
			if (payload.token() != null && !payload.token().isBlank()) {
				config.plexToken = payload.token();
			}
			config.plexMoviesEnabled = payload.moviesEnabled();
			config.plexTvShowsEnabled = payload.tvShowsEnabled();
			config.plexLibraryKeys = new ArrayList<>(payload.libraryKeys());
		});
		PlexService.INSTANCE.invalidateCache();
		PlexService.INSTANCE.refresh(true).whenComplete((status, error) -> runOnServer(player, () -> {
			sendMediaFeatures(player);
			OnDemandCatalog.discoverLibraries(OnDemandProvider.PLEX).whenComplete((libraries, ignored) ->
				runOnServer(player, () -> sendPlexConfig(player, libraries))
			);
			String key = status != null && status.authenticated()
				? "message.pixelreel.plex.config_saved"
				: "message.pixelreel.plex.config_failed";
			ServerPlayNetworking.send(player, new ModNetworkPayloads.ScreenNotice(BlockPos.ZERO, key));
		}));
	}

	private static void handleRefreshLibrary(ServerPlayer player) {
		if (!CinemaPermissions.canRefreshLibrary(player)) {
			notify(player, BlockPos.ZERO, ScreenControllerLogic.Outcome.NO_PERMISSION);
			return;
		}
		PixelReelConfig config = ConfigManager.get();
		List<CompletableFuture<JellyfinStatus>> futures = new ArrayList<>();
		if (config.isJellyfinConfigured()) {
			futures.add(JellyfinService.INSTANCE.refresh(true));
		}
		if (config.isEmbyConfigured()) {
			futures.add(EmbyService.INSTANCE.refresh(true));
		}
		if (config.isPlexConfigured()) {
			futures.add(PlexService.INSTANCE.refresh(true));
		}
		if (futures.isEmpty()) {
			sendMediaFeatures(player);
			ServerPlayNetworking.send(player, new ModNetworkPayloads.ScreenNotice(BlockPos.ZERO, "message.pixelreel.ondemand.refresh_failed"));
			return;
		}
		CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
			.whenComplete((ignored, error) -> runOnServer(player, () -> {
				sendMediaFeatures(player);
				boolean ok = futures.stream().map(f -> {
					try {
						return f.getNow(null);
					} catch (Exception e) {
						return null;
					}
				}).anyMatch(status -> status != null && status.authenticated());
				String key = ok
					? "message.pixelreel.ondemand.refresh_done"
					: "message.pixelreel.ondemand.refresh_failed";
				ServerPlayNetworking.send(player, new ModNetworkPayloads.ScreenNotice(BlockPos.ZERO, key));
			}));
	}

	private static void sendJellyfinConfig(ServerPlayer player, List<JellyfinLibrary> libraries) {
		if (player.hasDisconnected()) {
			return;
		}
		PixelReelConfig.JellyfinPublicConfig pub = ConfigManager.get().jellyfinPublicConfig();
		ServerPlayNetworking.send(
			player,
			new ModNetworkPayloads.JellyfinConfigData(
				pub.url(),
				pub.userId(),
				pub.moviesEnabled(),
				pub.tvShowsEnabled(),
				pub.autoplayNextEpisode(),
				pub.hasApiKey(),
				pub.libraryIds(),
				libraries == null ? List.of() : libraries,
				JellyfinService.INSTANCE.lastStatus()
			)
		);
	}

	private static void sendEmbyConfig(ServerPlayer player, List<JellyfinLibrary> libraries) {
		if (player.hasDisconnected()) {
			return;
		}
		PixelReelConfig.EmbyPublicConfig pub = ConfigManager.get().embyPublicConfig();
		ServerPlayNetworking.send(
			player,
			new ModNetworkPayloads.EmbyConfigData(
				pub.url(),
				pub.userId(),
				pub.moviesEnabled(),
				pub.tvShowsEnabled(),
				pub.hasApiKey(),
				pub.libraryIds(),
				libraries == null ? List.of() : libraries,
				EmbyService.INSTANCE.lastStatus()
			)
		);
	}

	private static void sendPlexConfig(ServerPlayer player, List<JellyfinLibrary> libraries) {
		if (player.hasDisconnected()) {
			return;
		}
		PixelReelConfig.PlexPublicConfig pub = ConfigManager.get().plexPublicConfig();
		ServerPlayNetworking.send(
			player,
			new ModNetworkPayloads.PlexConfigData(
				pub.url(),
				pub.moviesEnabled(),
				pub.tvShowsEnabled(),
				pub.hasToken(),
				pub.libraryKeys(),
				libraries == null ? List.of() : libraries,
				PlexService.INSTANCE.lastStatus()
			)
		);
	}

	private static void handleRequestTunarrConfig(ServerPlayer player) {
		if (!CinemaPermissions.canConfigureTunarr(player)) {
			notify(player, BlockPos.ZERO, ScreenControllerLogic.Outcome.NO_PERMISSION);
			return;
		}
		sendTunarrConfig(player);
	}

	private static void handleUpdateTunarrConfig(ModNetworkPayloads.UpdateTunarrConfig payload, ServerPlayer player) {
		if (!CinemaPermissions.canConfigureTunarr(player)) {
			notify(player, BlockPos.ZERO, ScreenControllerLogic.Outcome.NO_PERMISSION);
			return;
		}
		PixelReelConfig.TunarrPublicConfig expanded = PixelReelConfig.expandTunarrUrls(payload.m3uUrl(), payload.xmltvUrl());
		if (expanded.m3uUrl().isEmpty()) {
			ServerPlayNetworking.send(player, new ModNetworkPayloads.ScreenNotice(BlockPos.ZERO, "message.pixelreel.tunarr.invalid_url"));
			sendTunarrConfig(player);
			return;
		}
		ConfigManager.update(config -> {
			config.m3uUrl = expanded.m3uUrl();
			config.xmltvUrl = expanded.xmltvUrl();
		});
		ChannelService.INSTANCE.invalidateCache();
		ChannelService.INSTANCE.channels(true).whenComplete((channels, error) -> runOnServer(player, () -> {
			sendMediaFeatures(player);
			sendTunarrConfig(player);
			sendChannelListNow(player);
			LiveStatus status = ChannelService.INSTANCE.lastStatus();
			String key = status.reachable() && status.channelCount() > 0
				? "message.pixelreel.tunarr.config_saved"
				: "message.pixelreel.tunarr.config_failed";
			ServerPlayNetworking.send(player, new ModNetworkPayloads.ScreenNotice(BlockPos.ZERO, key));
		}));
	}

	private static void sendTunarrConfig(ServerPlayer player) {
		if (player.hasDisconnected()) {
			return;
		}
		PixelReelConfig.TunarrPublicConfig pub = ConfigManager.get().tunarrPublicConfig();
		ServerPlayNetworking.send(
			player,
			new ModNetworkPayloads.TunarrConfigData(pub.m3uUrl(), pub.xmltvUrl(), ChannelService.INSTANCE.lastStatus())
		);
	}

	public static void sendMediaFeatures(ServerPlayer player) {
		if (player.hasDisconnected()) {
			return;
		}
		PixelReelConfig.FeatureFlags flags = CinemaPermissions.featureFlags(player);
		ServerPlayNetworking.send(
			player,
			ModNetworkPayloads.MediaFeatures.from(
				flags,
				JellyfinService.INSTANCE.lastStatus(),
				EmbyService.INSTANCE.lastStatus(),
				PlexService.INSTANCE.lastStatus(),
				ChannelService.INSTANCE.lastStatus()
			)
		);
	}

	public static void sendChannelList(ServerPlayer player, boolean forceRefresh) {
		if (!CinemaPermissions.canPlayTunarr(player)) {
			notify(player, BlockPos.ZERO, ScreenControllerLogic.Outcome.NO_PERMISSION);
			return;
		}
		ChannelService service = ChannelService.INSTANCE;
		if (!forceRefresh && service.isCacheFresh()) {
			sendChannelListNow(player);
		} else {
			service.channels(forceRefresh).whenComplete((channels, error) -> runOnServer(player, () -> sendChannelListNow(player)));
		}
	}

	public static void sendChannelListNow(ServerPlayer player) {
		if (player.hasDisconnected()) {
			return;
		}
		if (!CinemaPermissions.canPlayTunarr(player)) {
			notify(player, BlockPos.ZERO, ScreenControllerLogic.Outcome.NO_PERMISSION);
			return;
		}
		LiveStatus status = ChannelService.INSTANCE.lastStatus();
		List<ChannelEntry> entries = ChannelService.INSTANCE.entries().stream().map(ChannelEntry::withoutClientSecrets).toList();
		if (entries.size() > ModNetworkPayloads.MAX_CHANNELS) {
			entries = entries.subList(0, ModNetworkPayloads.MAX_CHANNELS);
		}
		ServerPlayNetworking.send(player, new ModNetworkPayloads.ChannelList(status, entries));
	}

	private static void runOnServer(ServerPlayer player, Runnable action) {
		MinecraftServer server = player.level().getServer();
		if (server != null) {
			server.execute(() -> {
				try {
					action.run();
				} catch (Exception e) {
					PixelReel.LOGGER.error("Failed to deliver cinema data to {}", player.getGameProfile().name(), e);
				}
			});
		}
	}

	private static void notify(ServerPlayer player, BlockPos pos, ScreenControllerLogic.Outcome outcome) {
		String key = switch (outcome) {
			case OK -> null;
			case NO_SCREEN -> "message.pixelreel.no_screen";
			case NO_CHANNELS -> "message.pixelreel.no_channels";
			case UNKNOWN_CHANNEL -> "message.pixelreel.unknown_channel";
			case NO_PERMISSION -> "message.pixelreel.no_permission";
			case JELLYFIN_UNAVAILABLE -> "message.pixelreel.ondemand.unavailable";
			case ITEM_UNAVAILABLE -> "message.pixelreel.ondemand.item_unavailable";
			case NEXT_UNAVAILABLE -> "message.pixelreel.ondemand.next_unavailable";
			case SUBTITLE_UNAVAILABLE -> "message.pixelreel.ondemand.subtitle_unavailable";
		};
		if (key != null) {
			ServerPlayNetworking.send(player, new ModNetworkPayloads.ScreenNotice(pos, key));
		}
	}
}
