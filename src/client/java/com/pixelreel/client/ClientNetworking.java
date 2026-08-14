package com.pixelreel.client;

import com.pixelreel.blockentities.DisplayBlockEntity;
import com.pixelreel.channels.ChannelService;
import com.pixelreel.client.gui.MediaSourceScreen;
import com.pixelreel.client.gui.emby.EmbyConfigScreen;
import com.pixelreel.client.gui.jellyfin.JellyfinConfigScreen;
import com.pixelreel.client.gui.ondemand.OnDemandBrowseScreen;
import com.pixelreel.client.gui.ondemand.OnDemandDetailScreen;
import com.pixelreel.client.gui.ondemand.OnDemandEpisodeScreen;
import com.pixelreel.client.gui.ondemand.OnDemandSeriesScreen;
import com.pixelreel.client.gui.plex.PlexConfigScreen;
import com.pixelreel.client.gui.tunarr.ChannelMenuScreen;
import com.pixelreel.client.gui.tunarr.TunarrConfigScreen;
import com.pixelreel.client.playback.ChannelPlayer;
import com.pixelreel.client.playback.PlaybackManager;
import com.pixelreel.networking.ModNetworkPayloads;
import com.pixelreel.networking.ScreenAction;
import com.pixelreel.ondemand.OnDemandProvider;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public final class ClientNetworking {
	private static OnDemandProvider pendingBrowseProvider = OnDemandProvider.JELLYFIN;
	private static ModNetworkPayloads.BrowseKind pendingBrowseKind = ModNetworkPayloads.BrowseKind.MOVIES;
	private static String pendingBrowseSearch = "";
	private static int pendingBrowsePage;

	private ClientNetworking() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(ModNetworkPayloads.ChannelList.TYPE, (payload, context) -> {
			ClientChannelCache.INSTANCE.accept(payload.entries(), payload.status());
			if (Minecraft.getInstance().gui.screen() instanceof ChannelMenuScreen menu) {
				menu.onChannelsUpdated();
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(ModNetworkPayloads.OpenMenu.TYPE, (payload, context) ->
			openMenu(payload.pos())
		);

		ClientPlayNetworking.registerGlobalReceiver(ModNetworkPayloads.ScreenNotice.TYPE, (payload, context) -> {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.player != null) {
				minecraft.player.sendOverlayMessage(Component.translatable(payload.translationKey()));
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(ModNetworkPayloads.RetryDisplay.TYPE, (payload, context) ->
			PlaybackManager.INSTANCE.retry(payload.pos())
		);

		ClientPlayNetworking.registerGlobalReceiver(ModNetworkPayloads.ShowClientStatus.TYPE, (payload, context) ->
			printClientStatus(payload.pos())
		);

		ClientPlayNetworking.registerGlobalReceiver(ModNetworkPayloads.PlaybackUrl.TYPE, (payload, context) ->
			PlaybackManager.INSTANCE.acceptPlaybackUrl(payload.pos(), payload.channelEpoch(), payload.streamUrl(), payload.subtitleUrl())
		);

		ClientPlayNetworking.registerGlobalReceiver(ModNetworkPayloads.MediaFeatures.TYPE, (payload, context) -> {
			ClientMediaCache.INSTANCE.acceptFeatures(payload);
			if (Minecraft.getInstance().gui.screen() instanceof MediaSourceScreen screen) {
				screen.onFeaturesUpdated();
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(ModNetworkPayloads.JellyfinBrowseResult.TYPE, (payload, context) -> {
			// Drop out-of-order replies so an older page cannot wipe a newer one.
			if (payload.provider() != pendingBrowseProvider
				|| payload.kind() != pendingBrowseKind
				|| payload.page() != pendingBrowsePage
				|| !payload.search().equals(pendingBrowseSearch)) {
				return;
			}
			ClientMediaCache.INSTANCE.acceptBrowse(payload);
			if (Minecraft.getInstance().gui.screen() instanceof OnDemandBrowseScreen screen) {
				screen.onBrowseUpdated();
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(ModNetworkPayloads.JellyfinChildrenResult.TYPE, (payload, context) -> {
			ClientMediaCache.INSTANCE.acceptChildren(payload);
			var screen = Minecraft.getInstance().gui.screen();
			if (screen instanceof OnDemandSeriesScreen seriesScreen) {
				seriesScreen.onChildrenUpdated();
			} else if (screen instanceof OnDemandEpisodeScreen episodeScreen) {
				episodeScreen.onChildrenUpdated();
			} else if (screen instanceof OnDemandDetailScreen detailScreen) {
				detailScreen.onItemUpdated();
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(ModNetworkPayloads.JellyfinConfigData.TYPE, (payload, context) -> {
			ClientMediaCache.INSTANCE.acceptConfig(payload);
			if (Minecraft.getInstance().gui.screen() instanceof JellyfinConfigScreen screen) {
				screen.onConfigUpdated();
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(ModNetworkPayloads.EmbyConfigData.TYPE, (payload, context) -> {
			ClientMediaCache.INSTANCE.acceptEmbyConfig(payload);
			if (Minecraft.getInstance().gui.screen() instanceof EmbyConfigScreen screen) {
				screen.onConfigUpdated();
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(ModNetworkPayloads.PlexConfigData.TYPE, (payload, context) -> {
			ClientMediaCache.INSTANCE.acceptPlexConfig(payload);
			if (Minecraft.getInstance().gui.screen() instanceof PlexConfigScreen screen) {
				screen.onConfigUpdated();
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(ModNetworkPayloads.TunarrConfigData.TYPE, (payload, context) -> {
			ClientMediaCache.INSTANCE.acceptTunarrConfig(payload);
			if (Minecraft.getInstance().gui.screen() instanceof TunarrConfigScreen screen) {
				screen.onConfigUpdated();
			}
		});
	}

	public static void requestChannels(boolean forceRefresh) {
		if (ClientPlayNetworking.canSend(ModNetworkPayloads.RequestChannels.TYPE)) {
			ClientPlayNetworking.send(new ModNetworkPayloads.RequestChannels(forceRefresh));
		}
	}

	public static void requestPlaybackUrl(BlockPos pos, int channelEpoch) {
		if (ClientPlayNetworking.canSend(ModNetworkPayloads.RequestPlaybackUrl.TYPE)) {
			ClientPlayNetworking.send(new ModNetworkPayloads.RequestPlaybackUrl(pos, channelEpoch));
		}
	}

	public static void requestMediaFeatures() {
		if (ClientPlayNetworking.canSend(ModNetworkPayloads.RequestMediaFeatures.TYPE)) {
			ClientPlayNetworking.send(new ModNetworkPayloads.RequestMediaFeatures());
		}
	}

	public static void requestJellyfinBrowse(
		OnDemandProvider provider,
		ModNetworkPayloads.BrowseKind kind,
		String search,
		int page,
		boolean force
	) {
		String normalized = search == null ? "" : search;
		pendingBrowseProvider = provider;
		pendingBrowseKind = kind;
		pendingBrowseSearch = normalized;
		pendingBrowsePage = page;
		if (ClientPlayNetworking.canSend(ModNetworkPayloads.RequestJellyfinBrowse.TYPE)) {
			ClientPlayNetworking.send(
				new ModNetworkPayloads.RequestJellyfinBrowse(provider, kind, normalized, page, force)
			);
		}
	}

	public static void requestJellyfinChildren(
		OnDemandProvider provider,
		ModNetworkPayloads.ChildrenKind kind,
		String parentId,
		boolean force
	) {
		if (ClientPlayNetworking.canSend(ModNetworkPayloads.RequestJellyfinChildren.TYPE)) {
			ClientPlayNetworking.send(new ModNetworkPayloads.RequestJellyfinChildren(provider, kind, parentId, force));
		}
	}

	public static void playJellyfin(OnDemandProvider provider, BlockPos pos, String itemId, long startPositionMs) {
		if (ClientPlayNetworking.canSend(ModNetworkPayloads.ScreenPlayJellyfin.TYPE)) {
			ClientPlayNetworking.send(new ModNetworkPayloads.ScreenPlayJellyfin(provider, pos, itemId, startPositionMs));
		}
	}

	public static void reportMediaEnded(BlockPos pos, int channelEpoch) {
		if (ClientPlayNetworking.canSend(ModNetworkPayloads.ReportMediaEnded.TYPE)) {
			ClientPlayNetworking.send(new ModNetworkPayloads.ReportMediaEnded(pos, channelEpoch));
		}
	}

	public static void requestJellyfinConfig() {
		if (ClientPlayNetworking.canSend(ModNetworkPayloads.RequestJellyfinConfig.TYPE)) {
			ClientPlayNetworking.send(new ModNetworkPayloads.RequestJellyfinConfig());
		}
	}

	public static void requestEmbyConfig() {
		if (ClientPlayNetworking.canSend(ModNetworkPayloads.RequestEmbyConfig.TYPE)) {
			ClientPlayNetworking.send(new ModNetworkPayloads.RequestEmbyConfig());
		}
	}

	public static void requestPlexConfig() {
		if (ClientPlayNetworking.canSend(ModNetworkPayloads.RequestPlexConfig.TYPE)) {
			ClientPlayNetworking.send(new ModNetworkPayloads.RequestPlexConfig());
		}
	}

	public static void requestTunarrConfig() {
		if (ClientPlayNetworking.canSend(ModNetworkPayloads.RequestTunarrConfig.TYPE)) {
			ClientPlayNetworking.send(new ModNetworkPayloads.RequestTunarrConfig());
		}
	}

	public static void updateTunarrConfig(String m3uUrl, String xmltvUrl) {
		if (ClientPlayNetworking.canSend(ModNetworkPayloads.UpdateTunarrConfig.TYPE)) {
			ClientPlayNetworking.send(new ModNetworkPayloads.UpdateTunarrConfig(m3uUrl, xmltvUrl));
		}
	}

	public static void updateJellyfinConfig(
		String url,
		String apiKey,
		String userId,
		boolean movies,
		boolean shows,
		boolean autoplay,
		List<String> libraryIds
	) {
		if (ClientPlayNetworking.canSend(ModNetworkPayloads.UpdateJellyfinConfig.TYPE)) {
			ClientPlayNetworking.send(new ModNetworkPayloads.UpdateJellyfinConfig(url, apiKey, userId, movies, shows, autoplay, libraryIds));
		}
	}

	public static void updateEmbyConfig(
		String url,
		String apiKey,
		String userId,
		boolean movies,
		boolean shows,
		List<String> libraryIds
	) {
		if (ClientPlayNetworking.canSend(ModNetworkPayloads.UpdateEmbyConfig.TYPE)) {
			ClientPlayNetworking.send(new ModNetworkPayloads.UpdateEmbyConfig(url, apiKey, userId, movies, shows, libraryIds));
		}
	}

	public static void updatePlexConfig(
		String url,
		String token,
		boolean movies,
		boolean shows,
		List<String> libraryKeys
	) {
		if (ClientPlayNetworking.canSend(ModNetworkPayloads.UpdatePlexConfig.TYPE)) {
			ClientPlayNetworking.send(new ModNetworkPayloads.UpdatePlexConfig(url, token, movies, shows, libraryKeys));
		}
	}

	public static void refreshJellyfinLibrary() {
		if (ClientPlayNetworking.canSend(ModNetworkPayloads.RefreshJellyfinLibrary.TYPE)) {
			ClientPlayNetworking.send(new ModNetworkPayloads.RefreshJellyfinLibrary());
		}
	}

	public static void unequipPixelGlasses() {
		try {
			ClientPlayNetworking.send(new ModNetworkPayloads.UnequipPixelGlasses());
		} catch (RuntimeException ignored) {
			// Not connected yet / channel not ready — local head-slot clear still dismisses the overlay.
		}
	}

	public static void sendControl(BlockPos pos, ScreenAction action, float value) {
		if (ClientPlayNetworking.canSend(ModNetworkPayloads.ScreenControl.TYPE)) {
			ClientPlayNetworking.send(new ModNetworkPayloads.ScreenControl(pos, action, value));
		}
	}

	public static void sendTune(BlockPos pos, String channelId) {
		if (!ClientPlayNetworking.canSend(ModNetworkPayloads.ScreenTune.TYPE)) {
			com.pixelreel.PixelReel.LOGGER.warn("Cannot send channel tune for {} - play channel not ready", channelId);
			return;
		}
		ClientPlayNetworking.send(new ModNetworkPayloads.ScreenTune(pos, channelId));
	}

	public static void openMenu(BlockPos pos) {
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.execute(() -> {
			if (minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof DisplayBlockEntity display) {
				minecraft.gui.setScreen(new MediaSourceScreen(display));
			}
		});
	}

	private static void printClientStatus(BlockPos pos) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null) {
			return;
		}
		if (!(minecraft.level.getBlockEntity(pos) instanceof DisplayBlockEntity display)) {
			return;
		}
		ChannelPlayer player = PlaybackManager.INSTANCE.player(display);
		if (player == null) {
			minecraft.player.sendSystemMessage(Component.translatable("chat.pixelreel.status.player_idle"));
			return;
		}
		String frame = player.videoTexture().frameWidth() > 0
			? player.videoTexture().frameWidth() + "x" + player.videoTexture().frameHeight()
			: "-";
		minecraft.player.sendSystemMessage(
			Component.translatable(
				"chat.pixelreel.status.player",
				player.status().name(),
				frame,
				"authorized",
				Math.round(player.bufferingProgress()) + "%",
				player.errorDetail().isEmpty() ? "-" : player.errorDetail()
			)
		);
	}
}
