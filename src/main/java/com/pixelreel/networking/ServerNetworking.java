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
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ServerNetworking {
	private ServerNetworking() {
	}

	public static void register() {
		ServerPlayNetworking.registerGlobalReceiver(
			ModNetworkPayloads.ScreenControl.ID, (server, player, handler, buf, sender) -> {
				ModNetworkPayloads.ScreenControl payload = ModNetworkPayloads.ScreenControl.readFromBuf(buf);
				handleControl(payload, player);
			}
		);
		ServerPlayNetworking.registerGlobalReceiver(
			ModNetworkPayloads.ScreenTune.ID, (server, player, handler, buf, sender) -> {
				ModNetworkPayloads.ScreenTune payload = ModNetworkPayloads.ScreenTune.readFromBuf(buf);
				handleTune(payload, player);
			}
		);
		ServerPlayNetworking.registerGlobalReceiver(
			ModNetworkPayloads.RequestChannels.ID, (server, player, handler, buf, sender) -> {
				ModNetworkPayloads.RequestChannels payload = ModNetworkPayloads.RequestChannels.readFromBuf(buf);
				sendChannelList(player, payload.forceRefresh());
			}
		);
		ServerPlayNetworking.registerGlobalReceiver(
			ModNetworkPayloads.RequestMediaFeatures.ID, (server, player, handler, buf, sender) -> sendMediaFeatures(player)
		);
		ServerPlayNetworking.registerGlobalReceiver(
			ModNetworkPayloads.RequestJellyfinBrowse.ID, (server, player, handler, buf, sender) -> {
				ModNetworkPayloads.RequestJellyfinBrowse payload = ModNetworkPayloads.RequestJellyfinBrowse.readFromBuf(buf);
				handleBrowse(payload, player);
			}
		);
		ServerPlayNetworking.registerGlobalReceiver(
			ModNetworkPayloads.RequestJellyfinChildren.ID, (server, player, handler, buf, sender) -> {
				ModNetworkPayloads.RequestJellyfinChildren payload = ModNetworkPayloads.RequestJellyfinChildren.readFromBuf(buf);
				handleChildren(payload, player);
			}
		);
		ServerPlayNetworking.registerGlobalReceiver(
			ModNetworkPayloads.ScreenPlayJellyfin.ID, (server, player, handler, buf, sender) -> {
				ModNetworkPayloads.ScreenPlayJellyfin payload = ModNetworkPayloads.ScreenPlayJellyfin.readFromBuf(buf);
				handlePlayJellyfin(payload, player);
			}
		);
		ServerPlayNetworking.registerGlobalReceiver(
			ModNetworkPayloads.ReportMediaEnded.ID, (server, player, handler, buf, sender) -> {
				ModNetworkPayloads.ReportMediaEnded payload = ModNetworkPayloads.ReportMediaEnded.readFromBuf(buf);
				handleMediaEnded(payload, player);
			}
		);
		ServerPlayNetworking.registerGlobalReceiver(
			ModNetworkPayloads.RequestJellyfinConfig.ID, (server, player, handler, buf, sender) -> handleRequestJellyfinConfig(player)
		);
		ServerPlayNetworking.registerGlobalReceiver(
			ModNetworkPayloads.UpdateJellyfinConfig.ID, (server, player, handler, buf, sender) -> {
				ModNetworkPayloads.UpdateJellyfinConfig payload = ModNetworkPayloads.UpdateJellyfinConfig.readFromBuf(buf);
				handleUpdateJellyfinConfig(payload, player);
			}
		);
		ServerPlayNetworking.registerGlobalReceiver(
			ModNetworkPayloads.RequestEmbyConfig.ID, (server, player, handler, buf, sender) -> handleRequestEmbyConfig(player)
		);
		ServerPlayNetworking.registerGlobalReceiver(
			ModNetworkPayloads.UpdateEmbyConfig.ID, (server, player, handler, buf, sender) -> {
				ModNetworkPayloads.UpdateEmbyConfig payload = ModNetworkPayloads.UpdateEmbyConfig.readFromBuf(buf);
				handleUpdateEmbyConfig(payload, player);
			}
		);
		ServerPlayNetworking.registerGlobalReceiver(
			ModNetworkPayloads.RequestPlexConfig.ID, (server, player, handler, buf, sender) -> handleRequestPlexConfig(player)
		);
		ServerPlayNetworking.registerGlobalReceiver(
			ModNetworkPayloads.UpdatePlexConfig.ID, (server, player, handler, buf, sender) -> {
				ModNetworkPayloads.UpdatePlexConfig payload = ModNetworkPayloads.UpdatePlexConfig.readFromBuf(buf);
				handleUpdatePlexConfig(payload, player);
			}
		);
		ServerPlayNetworking.registerGlobalReceiver(
			ModNetworkPayloads.RefreshJellyfinLibrary.ID, (server, player, handler, buf, sender) -> handleRefreshLibrary(player)
		);
		ServerPlayNetworking.registerGlobalReceiver(
			ModNetworkPayloads.RequestTunarrConfig.ID, (server, player, handler, buf, sender) -> handleRequestTunarrConfig(player)
		);
		ServerPlayNetworking.registerGlobalReceiver(
			ModNetworkPayloads.UpdateTunarrConfig.ID, (server, player, handler, buf, sender) -> {
				ModNetworkPayloads.UpdateTunarrConfig payload = ModNetworkPayloads.UpdateTunarrConfig.readFromBuf(buf);
				handleUpdateTunarrConfig(payload, player);
			}
		);
		ServerPlayNetworking.registerGlobalReceiver(
			ModNetworkPayloads.UnequipPixelGlasses.ID,
			(server, player, handler, buf, sender) -> server.execute(() ->
				com.pixelreel.items.PixelGlassesItem.tryUnequip(player)
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
			display, payload.action(), payload.value(), player.getGameProfile().getName()
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
			PixelReel.LOGGER.warn("Rejected channel change from {} at {}: no reachable display", player.getGameProfile().getName(), payload.pos().toShortString());
			return;
		}
		ScreenControllerLogic.Outcome outcome = ScreenControllerLogic.tune(display, payload.channelId());
		PixelReel.LOGGER.info(
			"Channel tune from {} at {} -> {} ({})",
			player.getGameProfile().getName(),
			payload.pos().toShortString(),
			payload.channelId(),
			outcome
		);
		notify(player, payload.pos(), outcome);
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
			FriendlyByteBuf buf = PacketByteBufs.create();
			new ModNetworkPayloads.JellyfinBrowseResult(
				provider,
				payload.kind(),
				payload.search(),
				payload.page(),
				0,
				OnDemandCatalog.lastStatus(provider),
				List.of()
			).writeToBuf(buf);
			ServerPlayNetworking.send(player, ModNetworkPayloads.JellyfinBrowseResult.ID, buf);
			return;
		}
		OnDemandCatalog.refresh(provider, payload.forceRefresh()).whenComplete((status, error) -> runOnServer(player, () -> {
			OnDemandCatalog.Page page = OnDemandCatalog.page(provider, payload.kind(), payload.search(), payload.page());
			List<JellyfinItemSummary> slim = page.items().stream().map(JellyfinItemSummary::forBrowsePacket).toList();
			FriendlyByteBuf buf = PacketByteBufs.create();
			new ModNetworkPayloads.JellyfinBrowseResult(
				provider,
				payload.kind(),
				payload.search(),
				page.page(),
				page.totalCount(),
				OnDemandCatalog.lastStatus(provider),
				slim
			).writeToBuf(buf);
			ServerPlayNetworking.send(player, ModNetworkPayloads.JellyfinBrowseResult.ID, buf);
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
					List<JellyfinItemSummary> items = item.isPresent() ? List.of(item.get()) : List.of();
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
		FriendlyByteBuf buf = PacketByteBufs.create();
		new ModNetworkPayloads.JellyfinChildrenResult(
			payload.provider(),
			payload.kind(),
			payload.parentId(),
			OnDemandCatalog.lastStatus(payload.provider()),
			items == null ? List.of() : items
		).writeToBuf(buf);
		ServerPlayNetworking.send(player, ModNetworkPayloads.JellyfinChildrenResult.ID, buf);
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
				display, provider, item.id(), payload.startPositionMs(), player.getGameProfile().getName()
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
			FriendlyByteBuf buf = PacketByteBufs.create();
			new ModNetworkPayloads.ScreenNotice(BlockPos.ZERO, key).writeToBuf(buf);
			ServerPlayNetworking.send(player, ModNetworkPayloads.ScreenNotice.ID, buf);
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
			FriendlyByteBuf buf = PacketByteBufs.create();
			new ModNetworkPayloads.ScreenNotice(BlockPos.ZERO, key).writeToBuf(buf);
			ServerPlayNetworking.send(player, ModNetworkPayloads.ScreenNotice.ID, buf);
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
			FriendlyByteBuf buf = PacketByteBufs.create();
			new ModNetworkPayloads.ScreenNotice(BlockPos.ZERO, key).writeToBuf(buf);
			ServerPlayNetworking.send(player, ModNetworkPayloads.ScreenNotice.ID, buf);
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
			FriendlyByteBuf buf = PacketByteBufs.create();
			new ModNetworkPayloads.ScreenNotice(BlockPos.ZERO, "message.pixelreel.ondemand.refresh_failed").writeToBuf(buf);
			ServerPlayNetworking.send(player, ModNetworkPayloads.ScreenNotice.ID, buf);
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
				FriendlyByteBuf buf = PacketByteBufs.create();
				new ModNetworkPayloads.ScreenNotice(BlockPos.ZERO, key).writeToBuf(buf);
				ServerPlayNetworking.send(player, ModNetworkPayloads.ScreenNotice.ID, buf);
			}));
	}

	private static void sendJellyfinConfig(ServerPlayer player, List<JellyfinLibrary> libraries) {
		if (player.hasDisconnected()) {
			return;
		}
		PixelReelConfig.JellyfinPublicConfig pub = ConfigManager.get().jellyfinPublicConfig();
		FriendlyByteBuf buf = PacketByteBufs.create();
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
		).writeToBuf(buf);
		ServerPlayNetworking.send(player, ModNetworkPayloads.JellyfinConfigData.ID, buf);
	}

	private static void sendEmbyConfig(ServerPlayer player, List<JellyfinLibrary> libraries) {
		if (player.hasDisconnected()) {
			return;
		}
		PixelReelConfig.EmbyPublicConfig pub = ConfigManager.get().embyPublicConfig();
		FriendlyByteBuf buf = PacketByteBufs.create();
		new ModNetworkPayloads.EmbyConfigData(
			pub.url(),
			pub.userId(),
			pub.moviesEnabled(),
			pub.tvShowsEnabled(),
			pub.hasApiKey(),
			pub.libraryIds(),
			libraries == null ? List.of() : libraries,
			EmbyService.INSTANCE.lastStatus()
		).writeToBuf(buf);
		ServerPlayNetworking.send(player, ModNetworkPayloads.EmbyConfigData.ID, buf);
	}

	private static void sendPlexConfig(ServerPlayer player, List<JellyfinLibrary> libraries) {
		if (player.hasDisconnected()) {
			return;
		}
		PixelReelConfig.PlexPublicConfig pub = ConfigManager.get().plexPublicConfig();
		FriendlyByteBuf buf = PacketByteBufs.create();
		new ModNetworkPayloads.PlexConfigData(
			pub.url(),
			pub.moviesEnabled(),
			pub.tvShowsEnabled(),
			pub.hasToken(),
			pub.libraryKeys(),
			libraries == null ? List.of() : libraries,
			PlexService.INSTANCE.lastStatus()
		).writeToBuf(buf);
		ServerPlayNetworking.send(player, ModNetworkPayloads.PlexConfigData.ID, buf);
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
			FriendlyByteBuf buf = PacketByteBufs.create();
			new ModNetworkPayloads.ScreenNotice(BlockPos.ZERO, "message.pixelreel.tunarr.invalid_url").writeToBuf(buf);
			ServerPlayNetworking.send(player, ModNetworkPayloads.ScreenNotice.ID, buf);
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
			FriendlyByteBuf buf = PacketByteBufs.create();
			new ModNetworkPayloads.ScreenNotice(BlockPos.ZERO, key).writeToBuf(buf);
			ServerPlayNetworking.send(player, ModNetworkPayloads.ScreenNotice.ID, buf);
		}));
	}

	private static void sendTunarrConfig(ServerPlayer player) {
		if (player.hasDisconnected()) {
			return;
		}
		PixelReelConfig.TunarrPublicConfig pub = ConfigManager.get().tunarrPublicConfig();
		FriendlyByteBuf buf = PacketByteBufs.create();
		new ModNetworkPayloads.TunarrConfigData(pub.m3uUrl(), pub.xmltvUrl(), ChannelService.INSTANCE.lastStatus()).writeToBuf(buf);
		ServerPlayNetworking.send(player, ModNetworkPayloads.TunarrConfigData.ID, buf);
	}

	public static void sendMediaFeatures(ServerPlayer player) {
		if (player.hasDisconnected()) {
			return;
		}
		PixelReelConfig.FeatureFlags flags = CinemaPermissions.featureFlags(player);
		FriendlyByteBuf buf = PacketByteBufs.create();
		ModNetworkPayloads.MediaFeatures.from(
			flags,
			JellyfinService.INSTANCE.lastStatus(),
			EmbyService.INSTANCE.lastStatus(),
			PlexService.INSTANCE.lastStatus(),
			ChannelService.INSTANCE.lastStatus()
		).writeToBuf(buf);
		ServerPlayNetworking.send(player, ModNetworkPayloads.MediaFeatures.ID, buf);
	}

	public static void sendChannelList(ServerPlayer player, boolean forceRefresh) {
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
		LiveStatus status = ChannelService.INSTANCE.lastStatus();
		List<ChannelEntry> entries = ChannelService.INSTANCE.entries();
		if (entries.size() > ModNetworkPayloads.MAX_CHANNELS) {
			entries = entries.subList(0, ModNetworkPayloads.MAX_CHANNELS);
		}
		FriendlyByteBuf buf = PacketByteBufs.create();
		new ModNetworkPayloads.ChannelList(status, entries).writeToBuf(buf);
		ServerPlayNetworking.send(player, ModNetworkPayloads.ChannelList.ID, buf);
	}

	private static void runOnServer(ServerPlayer player, Runnable action) {
		MinecraftServer server = player.level().getServer();
		if (server != null) {
			server.execute(() -> {
				try {
					action.run();
				} catch (Exception e) {
					PixelReel.LOGGER.error("Failed to deliver cinema data to {}", player.getGameProfile().getName(), e);
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
			FriendlyByteBuf buf = PacketByteBufs.create();
			new ModNetworkPayloads.ScreenNotice(pos, key).writeToBuf(buf);
			ServerPlayNetworking.send(player, ModNetworkPayloads.ScreenNotice.ID, buf);
		}
	}
}
