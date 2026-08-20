package com.pixelreel.server;

import com.pixelreel.PixelReel;
import com.pixelreel.blockentities.DisplayBlockEntity;
import com.pixelreel.blocks.DisplayBlock;
import com.pixelreel.channels.Channel;
import com.pixelreel.channels.ChannelService;
import com.pixelreel.config.ConfigManager;
import com.pixelreel.jellyfin.JellyfinItemKind;
import com.pixelreel.jellyfin.JellyfinItemSummary;
import com.pixelreel.media.MediaSource;
import com.pixelreel.media.SubtitleTrack;
import com.pixelreel.networking.ScreenAction;
import com.pixelreel.ondemand.OnDemandCatalog;
import com.pixelreel.ondemand.OnDemandProvider;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/** Server-side handling of every display. */
public final class ScreenControllerLogic {
	public static final double MAX_CONTROL_DISTANCE = 64.0;
	private static final double MAX_CONTROL_DISTANCE_SQR = MAX_CONTROL_DISTANCE * MAX_CONTROL_DISTANCE;
	private static final long SEEK_STEP_MS = 10_000L;

	private ScreenControllerLogic() {
	}

	public static @Nullable DisplayBlockEntity resolve(ServerPlayer player, BlockPos pos) {
		if (!(player.level() instanceof ServerLevel level)) {
			return null;
		}
		if (!level.isInWorldBounds(pos) || !level.isLoaded(pos)) {
			return null;
		}
		if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > MAX_CONTROL_DISTANCE_SQR) {
			return null;
		}
		return DisplayBlock.controllerAt(level, pos);
	}

	public static Outcome apply(DisplayBlockEntity display, ScreenAction action, float value) {
		return apply(display, action, value, "");
	}

	public static Outcome apply(DisplayBlockEntity display, ScreenAction action, float value, String actor) {
		switch (action) {
			case POWER_TOGGLE -> setPowered(display, !display.isPowered());
			case POWER_ON -> setPowered(display, true);
			case POWER_OFF -> setPowered(display, false);
			case VOLUME_SET -> display.setVolume(value);
			case STOP -> {
				if (display.isOnDemand()) {
					display.capturePosition();
					reportStopped(display, false);
				}
				display.setSuspended(true);
			}
			case RESUME -> {
				display.setSuspended(false);
				if (display.isOnDemand()) {
					display.setPlaybackPaused(false);
				}
				if (!display.isPowered()) {
					setPowered(display, true);
				}
			}
			case CHANNEL_NEXT -> {
				if (display.getMediaSource() != MediaSource.TUNARR) {
					return Outcome.OK;
				}
				return step(display, 1);
			}
			case CHANNEL_PREVIOUS -> {
				if (display.getMediaSource() != MediaSource.TUNARR) {
					return Outcome.OK;
				}
				return step(display, -1);
			}
			case PAUSE_TOGGLE -> {
				if (!display.isOnDemand()) {
					return Outcome.OK;
				}
				display.setPlaybackPaused(!display.isPlaybackPaused());
				reportProgress(display);
			}
			case PAUSE -> {
				if (display.isOnDemand()) {
					display.setPlaybackPaused(true);
					reportProgress(display);
				}
			}
			case UNPAUSE -> {
				if (display.isOnDemand()) {
					display.setSuspended(false);
					display.setPlaybackPaused(false);
					if (!display.isPowered()) {
						setPowered(display, true);
					}
					reportProgress(display);
				}
			}
			case SEEK -> {
				if (!display.isOnDemand()) {
					return Outcome.OK;
				}
				display.seekTo((long)value);
				reportProgress(display);
			}
			case SEEK_FORWARD -> {
				if (!display.isOnDemand()) {
					return Outcome.OK;
				}
				display.seekTo(display.currentPlaybackPositionMs() + SEEK_STEP_MS);
				reportProgress(display);
			}
			case SEEK_BACKWARD -> {
				if (!display.isOnDemand()) {
					return Outcome.OK;
				}
				display.seekTo(Math.max(0L, display.currentPlaybackPositionMs() - SEEK_STEP_MS));
				reportProgress(display);
			}
			case RESTART -> {
				if (!display.isOnDemand()) {
					return Outcome.OK;
				}
				display.restartPlayback();
				reportProgress(display);
			}
			case PLAY_NEXT_NOW -> {
				if (!display.hasAutoplayPending()) {
					return Outcome.NEXT_UNAVAILABLE;
				}
				String nextId = display.getNextEpisodeItemId();
				OnDemandProvider provider = display.onDemandProvider();
				display.clearAutoplay();
				playOnDemandItem(display, provider, nextId, 0L, actor);
			}
			case CANCEL_NEXT -> display.clearAutoplay();
			case CYCLE_SUBTITLE -> {
				return cycleSubtitle(display);
			}
			case SELECT_SUBTITLE -> {
				return selectSubtitle(display, Math.round(value));
			}
			case REPORT_DURATION -> {
				if (!display.isOnDemand()) {
					return Outcome.OK;
				}
				display.setDurationMs((long)value);
			}
		}
		return Outcome.OK;
	}

	public static Outcome cycleSubtitle(DisplayBlockEntity display) {
		if (!display.isOnDemand() || !display.hasSubtitleTracks()) {
			return Outcome.SUBTITLE_UNAVAILABLE;
		}
		return selectSubtitle(display, display.nextSubtitleIndex());
	}

	public static Outcome selectSubtitle(DisplayBlockEntity display, int subtitleIndex) {
		if (!display.isOnDemand()) {
			return Outcome.SUBTITLE_UNAVAILABLE;
		}
		if (subtitleIndex < 0) {
			String direct = OnDemandCatalog.rebuildStreamUrl(display, -1);
			display.applySubtitleSelection(-1, "", direct);
			PixelReel.LOGGER.info("Subtitles on display at {} -> None", display.getBlockPos().toShortString());
			return Outcome.OK;
		}
		if (!display.hasSubtitleTracks()) {
			return Outcome.SUBTITLE_UNAVAILABLE;
		}
		SubtitleTrack chosen = null;
		for (SubtitleTrack track : display.getSubtitleTracks()) {
			if (track.index() == subtitleIndex) {
				chosen = track;
				break;
			}
		}
		if (chosen == null) {
			return Outcome.SUBTITLE_UNAVAILABLE;
		}
		if (chosen.supportsTextOverlay()) {
			String subtitleUrl = OnDemandCatalog.subtitleFetchUrl(display, subtitleIndex);
			if (subtitleUrl.isBlank()) {
				return Outcome.SUBTITLE_UNAVAILABLE;
			}
			String direct = OnDemandCatalog.rebuildStreamUrl(display, -1);
			display.applySubtitleSelection(subtitleIndex, subtitleUrl, direct);
		} else {
			String burned = OnDemandCatalog.rebuildStreamUrl(display, subtitleIndex);
			if (burned.isBlank()) {
				return Outcome.SUBTITLE_UNAVAILABLE;
			}
			display.applySubtitleSelection(subtitleIndex, "", burned);
		}
		PixelReel.LOGGER.info(
			"Subtitles on display at {} -> {} ({})",
			display.getBlockPos().toShortString(),
			display.getSubtitleLabel(),
			subtitleIndex
		);
		return Outcome.OK;
	}

	public static void setPowered(DisplayBlockEntity display, boolean powered) {
		boolean wasPowered = display.isPowered();
		display.setPowered(powered);
		if (!powered && display.isOnDemand() && wasPowered) {
			reportStopped(display, false);
		}
		if (powered && !display.hasChannel()) {
			List<Channel> channels = ChannelService.INSTANCE.cachedChannels();
			if (!channels.isEmpty()) {
				display.tuneTo(channels.get(0));
			} else {
				ChannelService.INSTANCE.channels(false);
			}
		}
	}

	public static Outcome tune(DisplayBlockEntity display, String channelId) {
		Optional<Channel> channel = ChannelService.INSTANCE.findById(channelId);
		if (channel.isEmpty()) {
			channel = ChannelService.INSTANCE.resolve(channelId);
		}
		if (channel.isEmpty()) {
			if (ChannelService.INSTANCE.cachedChannels().isEmpty()) {
				ChannelService.INSTANCE.channels(false);
				return Outcome.NO_CHANNELS;
			}
			return Outcome.UNKNOWN_CHANNEL;
		}
		if (display.isOnDemand()) {
			reportStopped(display, false);
		}
		display.tuneTo(channel.get());
		display.setPowered(true);
		return Outcome.OK;
	}

	public static Outcome step(DisplayBlockEntity display, int delta) {
		List<Channel> channels = ChannelService.INSTANCE.cachedChannels();
		if (channels.isEmpty()) {
			ChannelService.INSTANCE.channels(false);
			return Outcome.NO_CHANNELS;
		}
		int current = indexOf(channels, display.getChannelId());
		int size = channels.size();
		int next = current < 0 ? (delta >= 0 ? 0 : size - 1) : Math.floorMod(current + delta, size);
		display.tuneTo(channels.get(next));
		display.setPowered(true);
		return Outcome.OK;
	}

	public static void playJellyfinItem(DisplayBlockEntity display, String itemId, long startPositionMs, String actor) {
		playOnDemandItem(display, OnDemandProvider.JELLYFIN, itemId, startPositionMs, actor);
	}

	public static void playOnDemandItem(
		DisplayBlockEntity display,
		OnDemandProvider provider,
		String itemId,
		long startPositionMs,
		String actor
	) {
		if (itemId == null || itemId.isBlank() || display.getLevel() == null) {
			return;
		}
		if (!OnDemandCatalog.isConfigured(provider)) {
			return;
		}
		MinecraftServer server = display.getLevel().getServer();
		if (server == null) {
			return;
		}
		BlockPos pos = display.getBlockPos().immutable();
		var level = display.getLevel();
		OnDemandCatalog.fetchItem(provider, itemId).thenCompose(optionalItem -> {
			if (optionalItem.isEmpty()) {
				return java.util.concurrent.CompletableFuture.completedFuture(Optional.<ResolvedPlay>empty());
			}
			JellyfinItemSummary item = optionalItem.get();
			long startMs = startPositionMs >= 0L ? startPositionMs : item.resumePositionMs();
			return OnDemandCatalog.resolvePlayback(provider, item.id(), startMs).thenApply(playback -> {
				if (playback.isEmpty()) {
					return Optional.<ResolvedPlay>empty();
				}
				return Optional.of(new ResolvedPlay(item, playback.get(), startMs));
			});
		}).whenComplete((resolvedOpt, error) -> server.execute(() -> {
			DisplayBlockEntity current = DisplayBlock.displayAt(level, pos);
			if (current == null) {
				return;
			}
			if (error != null || resolvedOpt == null || resolvedOpt.isEmpty()) {
				PixelReel.LOGGER.warn(
					"{} playback failed for {} at {}: {}",
					provider.displayName(),
					itemId,
					pos.toShortString(),
					error == null ? "item or stream unavailable" : error.toString()
				);
				return;
			}
			ResolvedPlay resolved = resolvedOpt.get();
			if (current.isOnDemand() && !current.getJellyfinItemId().isEmpty() && !current.getJellyfinItemId().equals(itemId)) {
				reportStopped(current, false);
			}
			JellyfinItemSummary item = resolved.item();
			var playback = resolved.playback();
			current.playOnDemand(
				provider,
				item,
				playback.streamUrl(),
				playback.playSessionId(),
				playback.mediaSourceId(),
				resolved.startMs(),
				actor,
				playback.hdr(),
				playback.subtitles(),
				playback.plexMediaIndex(),
				playback.plexPartIndex(),
				playback.plexPartKey()
			);
			current.setPowered(true);
			OnDemandCatalog.reportPlaying(
				provider,
				item.id(),
				playback.mediaSourceId(),
				playback.playSessionId(),
				resolved.startMs(),
				false,
				item.runtimeMs()
			);
			PixelReel.LOGGER.info(
				"{} playing {} ({}) on display at {} with {} subtitle track(s)",
				provider.displayName(),
				item.title(),
				item.kind(),
				pos.toShortString(),
				playback.subtitles() == null ? 0 : playback.subtitles().size()
			);
		}));
	}

	public static void onMediaEnded(DisplayBlockEntity display) {
		if (!display.isOnDemand()) {
			return;
		}
		OnDemandProvider provider = display.onDemandProvider();
		long endPos = display.getPlaybackDurationMs() > 0L
			? display.getPlaybackDurationMs()
			: display.currentPlaybackPositionMs();
		if (display.getJellyfinKind() != JellyfinItemKind.EPISODE) {
			OnDemandCatalog.reportStopped(
				provider,
				display.getJellyfinItemId(),
				display.getJellyfinMediaSourceId(),
				display.getPlaySessionId(),
				endPos,
				true,
				display.getPlaybackDurationMs()
			);
			display.capturePosition();
			display.setPlaybackPaused(true);
			return;
		}
		OnDemandCatalog.reportStopped(
			provider,
			display.getJellyfinItemId(),
			display.getJellyfinMediaSourceId(),
			display.getPlaySessionId(),
			endPos,
			true,
			display.getPlaybackDurationMs()
		);
		if (!ConfigManager.get().onDemandAutoplayNextEpisode) {
			display.setPlaybackPaused(true);
			return;
		}
		String seriesId = display.getJellyfinSeriesId();
		String seasonId = display.getJellyfinSeasonId();
		int episodeNumber = display.getEpisodeNumber();
		if (seriesId.isEmpty() || seasonId.isEmpty()) {
			display.setPlaybackPaused(true);
			return;
		}
		MinecraftServer server = display.getLevel() == null ? null : display.getLevel().getServer();
		if (server == null) {
			return;
		}
		BlockPos pos = display.getBlockPos().immutable();
		var level = display.getLevel();
		OnDemandCatalog.resolveNextEpisode(provider, seriesId, seasonId, episodeNumber).whenComplete((next, error) -> server.execute(() -> {
			DisplayBlockEntity current = DisplayBlock.displayAt(level, pos);
			if (current == null) {
				return;
			}
			if (next.isEmpty()) {
				current.setPlaybackPaused(true);
				return;
			}
			JellyfinItemSummary episode = next.get();
			long delay = ConfigManager.get().jellyfinNextEpisodeCountdownSeconds * 1000L;
			current.beginAutoplay(episode.id(), episode.title(), System.currentTimeMillis() + delay);
		}));
	}

	public static void reportProgress(DisplayBlockEntity display) {
		if (!display.isOnDemand() || display.getJellyfinItemId().isEmpty()) {
			return;
		}
		OnDemandCatalog.reportProgress(
			display.onDemandProvider(),
			display.getJellyfinItemId(),
			display.getJellyfinMediaSourceId(),
			display.getPlaySessionId(),
			display.currentPlaybackPositionMs(),
			display.isPlaybackPaused() || display.isSuspended(),
			display.getPlaybackDurationMs()
		);
	}

	private static void reportStopped(DisplayBlockEntity display, boolean completed) {
		OnDemandCatalog.reportStopped(
			display.onDemandProvider(),
			display.getJellyfinItemId(),
			display.getJellyfinMediaSourceId(),
			display.getPlaySessionId(),
			display.currentPlaybackPositionMs(),
			completed,
			display.getPlaybackDurationMs()
		);
	}

	private static int indexOf(List<Channel> channels, String channelId) {
		if (channelId.isEmpty()) {
			return -1;
		}
		for (int i = 0; i < channels.size(); i++) {
			if (channels.get(i).id().equals(channelId)) {
				return i;
			}
		}
		return -1;
	}

	private record ResolvedPlay(JellyfinItemSummary item, OnDemandCatalog.PlaybackStart playback, long startMs) {
	}

	public enum Outcome {
		OK,
		NO_SCREEN,
		NO_CHANNELS,
		UNKNOWN_CHANNEL,
		NO_PERMISSION,
		JELLYFIN_UNAVAILABLE,
		ITEM_UNAVAILABLE,
		NEXT_UNAVAILABLE,
		SUBTITLE_UNAVAILABLE
	}
}
