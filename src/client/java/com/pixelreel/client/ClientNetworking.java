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
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

public final class ClientNetworking {
	private static OnDemandProvider pendingBrowseProvider = OnDemandProvider.JELLYFIN;
	private static ModNetworkPayloads.BrowseKind pendingBrowseKind = ModNetworkPayloads.BrowseKind.MOVIES;
	private static String pendingBrowseSearch = "";
	private static int pendingBrowsePage;

	private ClientNetworking() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(ModNetworkPayloads.ChannelList.ID, (client, handler, buf, sender) -> {
			ModNetworkPayloads.ChannelList payload = ModNetworkPayloads.ChannelList.readFromBuf(buf);
			client.execute(() -> {
				ClientChannelCache.INSTANCE.accept(payload.entries(), payload.status());
				if (Minecraft.getInstance().screen instanceof ChannelMenuScreen menu) {
					menu.onChannelsUpdated();
				}
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(ModNetworkPayloads.ZoneList.ID, (client, handler, buf, sender) -> {
			ModNetworkPayloads.ZoneList payload = ModNetworkPayloads.ZoneList.readFromBuf(buf);
			client.execute(() -> ClientZoneCache.INSTANCE.update(payload.zones()));
		});

		ClientPlayNetworking.registerGlobalReceiver(ModNetworkPayloads.OpenMenu.ID, (client, handler, buf, sender) -> {
			ModNetworkPayloads.OpenMenu payload = ModNetworkPayloads.OpenMenu.readFromBuf(buf);
			client.execute(() -> openMenu(payload.pos()));
		});

		ClientPlayNetworking.registerGlobalReceiver(ModNetworkPayloads.ScreenNotice.ID, (client, handler, buf, sender) -> {
			ModNetworkPayloads.ScreenNotice payload = ModNetworkPayloads.ScreenNotice.readFromBuf(buf);
			client.execute(() -> {
				Minecraft minecraft = Minecraft.getInstance();
				if (minecraft.player != null) {
					minecraft.player.displayClientMessage(Component.translatable(payload.translationKey()), true);
				}
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(ModNetworkPayloads.RetryDisplay.ID, (client, handler, buf, sender) -> {
			ModNetworkPayloads.RetryDisplay payload = ModNetworkPayloads.RetryDisplay.readFromBuf(buf);
			client.execute(() -> PlaybackManager.INSTANCE.retry(payload.pos()));
		});

		ClientPlayNetworking.registerGlobalReceiver(ModNetworkPayloads.ShowClientStatus.ID, (client, handler, buf, sender) -> {
			ModNetworkPayloads.ShowClientStatus payload = ModNetworkPayloads.ShowClientStatus.readFromBuf(buf);
			client.execute(() -> printClientStatus(payload.pos()));
		});

		ClientPlayNetworking.registerGlobalReceiver(ModNetworkPayloads.MediaFeatures.ID, (client, handler, buf, sender) -> {
			ModNetworkPayloads.MediaFeatures payload = ModNetworkPayloads.MediaFeatures.readFromBuf(buf);
			client.execute(() -> {
				ClientMediaCache.INSTANCE.acceptFeatures(payload);
				if (Minecraft.getInstance().screen instanceof MediaSourceScreen screen) {
					screen.onFeaturesUpdated();
				}
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(ModNetworkPayloads.JellyfinBrowseResult.ID, (client, handler, buf, sender) -> {
			ModNetworkPayloads.JellyfinBrowseResult payload = ModNetworkPayloads.JellyfinBrowseResult.readFromBuf(buf);
			client.execute(() -> {
				if (payload.provider() != pendingBrowseProvider
					|| payload.kind() != pendingBrowseKind
					|| payload.page() != pendingBrowsePage
					|| !payload.search().equals(pendingBrowseSearch)) {
					return;
				}
				ClientMediaCache.INSTANCE.acceptBrowse(payload);
				if (Minecraft.getInstance().screen instanceof OnDemandBrowseScreen screen) {
					screen.onBrowseUpdated();
				}
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(ModNetworkPayloads.JellyfinChildrenResult.ID, (client, handler, buf, sender) -> {
			ModNetworkPayloads.JellyfinChildrenResult payload = ModNetworkPayloads.JellyfinChildrenResult.readFromBuf(buf);
			client.execute(() -> {
				ClientMediaCache.INSTANCE.acceptChildren(payload);
				var screen = Minecraft.getInstance().screen;
				if (screen instanceof OnDemandSeriesScreen seriesScreen) {
					seriesScreen.onChildrenUpdated();
				} else if (screen instanceof OnDemandEpisodeScreen episodeScreen) {
					episodeScreen.onChildrenUpdated();
				} else if (screen instanceof OnDemandDetailScreen detailScreen) {
					detailScreen.onItemUpdated();
				}
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(ModNetworkPayloads.JellyfinConfigData.ID, (client, handler, buf, sender) -> {
			ModNetworkPayloads.JellyfinConfigData payload = ModNetworkPayloads.JellyfinConfigData.readFromBuf(buf);
			client.execute(() -> {
				ClientMediaCache.INSTANCE.acceptConfig(payload);
				if (Minecraft.getInstance().screen instanceof JellyfinConfigScreen screen) {
					screen.onConfigUpdated();
				}
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(ModNetworkPayloads.EmbyConfigData.ID, (client, handler, buf, sender) -> {
			ModNetworkPayloads.EmbyConfigData payload = ModNetworkPayloads.EmbyConfigData.readFromBuf(buf);
			client.execute(() -> {
				ClientMediaCache.INSTANCE.acceptEmbyConfig(payload);
				if (Minecraft.getInstance().screen instanceof EmbyConfigScreen screen) {
					screen.onConfigUpdated();
				}
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(ModNetworkPayloads.PlexConfigData.ID, (client, handler, buf, sender) -> {
			ModNetworkPayloads.PlexConfigData payload = ModNetworkPayloads.PlexConfigData.readFromBuf(buf);
			client.execute(() -> {
				ClientMediaCache.INSTANCE.acceptPlexConfig(payload);
				if (Minecraft.getInstance().screen instanceof PlexConfigScreen screen) {
					screen.onConfigUpdated();
				}
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(ModNetworkPayloads.TunarrConfigData.ID, (client, handler, buf, sender) -> {
			ModNetworkPayloads.TunarrConfigData payload = ModNetworkPayloads.TunarrConfigData.readFromBuf(buf);
			client.execute(() -> {
				ClientMediaCache.INSTANCE.acceptTunarrConfig(payload);
				if (Minecraft.getInstance().screen instanceof TunarrConfigScreen screen) {
					screen.onConfigUpdated();
				}
			});
		});
	}

	public static void requestChannels(boolean forceRefresh) {
		if (ClientPlayNetworking.canSend(ModNetworkPayloads.RequestChannels.ID)) {
			FriendlyByteBuf buf = PacketByteBufs.create();
			new ModNetworkPayloads.RequestChannels(forceRefresh).writeToBuf(buf);
			ClientPlayNetworking.send(ModNetworkPayloads.RequestChannels.ID, buf);
		}
	}

	public static void requestMediaFeatures() {
		if (ClientPlayNetworking.canSend(ModNetworkPayloads.RequestMediaFeatures.ID)) {
			FriendlyByteBuf buf = PacketByteBufs.create();
			new ModNetworkPayloads.RequestMediaFeatures().writeToBuf(buf);
			ClientPlayNetworking.send(ModNetworkPayloads.RequestMediaFeatures.ID, buf);
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
		if (ClientPlayNetworking.canSend(ModNetworkPayloads.RequestJellyfinBrowse.ID)) {
			FriendlyByteBuf buf = PacketByteBufs.create();
			new ModNetworkPayloads.RequestJellyfinBrowse(provider, kind, normalized, page, force).writeToBuf(buf);
			ClientPlayNetworking.send(ModNetworkPayloads.RequestJellyfinBrowse.ID, buf);
		}
	}

	public static void requestJellyfinChildren(
		OnDemandProvider provider,
		ModNetworkPayloads.ChildrenKind kind,
		String parentId,
		boolean force
	) {
		if (ClientPlayNetworking.canSend(ModNetworkPayloads.RequestJellyfinChildren.ID)) {
			FriendlyByteBuf buf = PacketByteBufs.create();
			new ModNetworkPayloads.RequestJellyfinChildren(provider, kind, parentId, force).writeToBuf(buf);
			ClientPlayNetworking.send(ModNetworkPayloads.RequestJellyfinChildren.ID, buf);
		}
	}

	public static void playJellyfin(OnDemandProvider provider, BlockPos pos, String itemId, long startPositionMs) {
		if (ClientPlayNetworking.canSend(ModNetworkPayloads.ScreenPlayJellyfin.ID)) {
			FriendlyByteBuf buf = PacketByteBufs.create();
			new ModNetworkPayloads.ScreenPlayJellyfin(provider, pos, itemId, startPositionMs).writeToBuf(buf);
			ClientPlayNetworking.send(ModNetworkPayloads.ScreenPlayJellyfin.ID, buf);
		}
	}

	public static void reportMediaEnded(BlockPos pos, int channelEpoch) {
		if (ClientPlayNetworking.canSend(ModNetworkPayloads.ReportMediaEnded.ID)) {
			FriendlyByteBuf buf = PacketByteBufs.create();
			new ModNetworkPayloads.ReportMediaEnded(pos, channelEpoch).writeToBuf(buf);
			ClientPlayNetworking.send(ModNetworkPayloads.ReportMediaEnded.ID, buf);
		}
	}

	public static void requestJellyfinConfig() {
		if (ClientPlayNetworking.canSend(ModNetworkPayloads.RequestJellyfinConfig.ID)) {
			FriendlyByteBuf buf = PacketByteBufs.create();
			new ModNetworkPayloads.RequestJellyfinConfig().writeToBuf(buf);
			ClientPlayNetworking.send(ModNetworkPayloads.RequestJellyfinConfig.ID, buf);
		}
	}

	public static void requestEmbyConfig() {
		if (ClientPlayNetworking.canSend(ModNetworkPayloads.RequestEmbyConfig.ID)) {
			FriendlyByteBuf buf = PacketByteBufs.create();
			new ModNetworkPayloads.RequestEmbyConfig().writeToBuf(buf);
			ClientPlayNetworking.send(ModNetworkPayloads.RequestEmbyConfig.ID, buf);
		}
	}

	public static void requestPlexConfig() {
		if (ClientPlayNetworking.canSend(ModNetworkPayloads.RequestPlexConfig.ID)) {
			FriendlyByteBuf buf = PacketByteBufs.create();
			new ModNetworkPayloads.RequestPlexConfig().writeToBuf(buf);
			ClientPlayNetworking.send(ModNetworkPayloads.RequestPlexConfig.ID, buf);
		}
	}

	public static void requestTunarrConfig() {
		if (ClientPlayNetworking.canSend(ModNetworkPayloads.RequestTunarrConfig.ID)) {
			FriendlyByteBuf buf = PacketByteBufs.create();
			new ModNetworkPayloads.RequestTunarrConfig().writeToBuf(buf);
			ClientPlayNetworking.send(ModNetworkPayloads.RequestTunarrConfig.ID, buf);
		}
	}

	public static void updateTunarrConfig(String m3uUrl, String xmltvUrl) {
		if (ClientPlayNetworking.canSend(ModNetworkPayloads.UpdateTunarrConfig.ID)) {
			FriendlyByteBuf buf = PacketByteBufs.create();
			new ModNetworkPayloads.UpdateTunarrConfig(m3uUrl, xmltvUrl).writeToBuf(buf);
			ClientPlayNetworking.send(ModNetworkPayloads.UpdateTunarrConfig.ID, buf);
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
		if (ClientPlayNetworking.canSend(ModNetworkPayloads.UpdateJellyfinConfig.ID)) {
			FriendlyByteBuf buf = PacketByteBufs.create();
			new ModNetworkPayloads.UpdateJellyfinConfig(url, apiKey, userId, movies, shows, autoplay, libraryIds).writeToBuf(buf);
			ClientPlayNetworking.send(ModNetworkPayloads.UpdateJellyfinConfig.ID, buf);
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
		if (ClientPlayNetworking.canSend(ModNetworkPayloads.UpdateEmbyConfig.ID)) {
			FriendlyByteBuf buf = PacketByteBufs.create();
			new ModNetworkPayloads.UpdateEmbyConfig(url, apiKey, userId, movies, shows, libraryIds).writeToBuf(buf);
			ClientPlayNetworking.send(ModNetworkPayloads.UpdateEmbyConfig.ID, buf);
		}
	}

	public static void updatePlexConfig(
		String url,
		String token,
		boolean movies,
		boolean shows,
		List<String> libraryKeys
	) {
		if (ClientPlayNetworking.canSend(ModNetworkPayloads.UpdatePlexConfig.ID)) {
			FriendlyByteBuf buf = PacketByteBufs.create();
			new ModNetworkPayloads.UpdatePlexConfig(url, token, movies, shows, libraryKeys).writeToBuf(buf);
			ClientPlayNetworking.send(ModNetworkPayloads.UpdatePlexConfig.ID, buf);
		}
	}

	public static void refreshJellyfinLibrary() {
		if (ClientPlayNetworking.canSend(ModNetworkPayloads.RefreshJellyfinLibrary.ID)) {
			FriendlyByteBuf buf = PacketByteBufs.create();
			new ModNetworkPayloads.RefreshJellyfinLibrary().writeToBuf(buf);
			ClientPlayNetworking.send(ModNetworkPayloads.RefreshJellyfinLibrary.ID, buf);
		}
	}

	public static void unequipPixelGlasses() {
		try {
			FriendlyByteBuf buf = PacketByteBufs.create();
			new ModNetworkPayloads.UnequipPixelGlasses().writeToBuf(buf);
			ClientPlayNetworking.send(ModNetworkPayloads.UnequipPixelGlasses.ID, buf);
		} catch (RuntimeException ignored) {
			// Not connected yet / channel not ready — local head-slot clear still dismisses the overlay.
		}
	}

	public static void sendControl(BlockPos pos, ScreenAction action, float value) {
		if (ClientPlayNetworking.canSend(ModNetworkPayloads.ScreenControl.ID)) {
			FriendlyByteBuf buf = PacketByteBufs.create();
			new ModNetworkPayloads.ScreenControl(pos, action, value).writeToBuf(buf);
			ClientPlayNetworking.send(ModNetworkPayloads.ScreenControl.ID, buf);
		}
	}

	public static void sendTune(BlockPos pos, String channelId) {
		if (!ClientPlayNetworking.canSend(ModNetworkPayloads.ScreenTune.ID)) {
			com.pixelreel.PixelReel.LOGGER.warn("Cannot send channel tune for {} - play channel not ready", channelId);
			return;
		}
		FriendlyByteBuf buf = PacketByteBufs.create();
		new ModNetworkPayloads.ScreenTune(pos, channelId).writeToBuf(buf);
		ClientPlayNetworking.send(ModNetworkPayloads.ScreenTune.ID, buf);
	}

	public static void openMenu(BlockPos pos) {
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.execute(() -> {
			if (minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof DisplayBlockEntity display) {
				minecraft.setScreen(new MediaSourceScreen(display));
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
		ChannelPlayer player = PlaybackManager.INSTANCE.player(display.getStreamUrl());
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
				ChannelService.hostOnly(display.getStreamUrl()),
				Math.round(player.bufferingProgress()) + "%",
				player.errorDetail().isEmpty() ? "-" : player.errorDetail()
			)
		);
	}
}
