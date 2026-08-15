package com.pixelreel.blockentities;

import com.pixelreel.ClientBridge;
import com.pixelreel.blocks.DisplayBlock;
import com.pixelreel.blocks.DisplayType;
import com.pixelreel.channels.Channel;
import com.pixelreel.config.ConfigManager;
import com.pixelreel.jellyfin.JellyfinItemKind;
import com.pixelreel.jellyfin.JellyfinItemSummary;
import com.pixelreel.media.MediaSource;
import com.pixelreel.media.SubtitleTrack;
import com.pixelreel.networking.MediaProxy;
import com.pixelreel.registry.ModBlockEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** server-auth one display */
public class DisplayBlockEntity extends BlockEntity {
	private static final String KEY_POWERED = "Powered";
	private static final String KEY_SUSPENDED = "Suspended";
	private static final String KEY_CHANNEL_ID = "ChannelId";
	private static final String KEY_CHANNEL_NUMBER = "ChannelNumber";
	private static final String KEY_CHANNEL_NAME = "ChannelName";
	private static final String KEY_STREAM_URL = "StreamUrl";
	private static final String KEY_VOLUME = "Volume";
	private static final String KEY_EPOCH = "Epoch";
	private static final String KEY_MEDIA_SOURCE = "MediaSource";
	private static final String KEY_JF_ITEM_ID = "JfItemId";
	private static final String KEY_JF_KIND = "JfKind";
	private static final String KEY_JF_SERIES_ID = "JfSeriesId";
	private static final String KEY_JF_SEASON_ID = "JfSeasonId";
	private static final String KEY_JF_SEASON = "JfSeason";
	private static final String KEY_JF_EPISODE = "JfEpisode";
	private static final String KEY_MEDIA_TITLE = "MediaTitle";
	private static final String KEY_MEDIA_OVERVIEW = "MediaOverview";
	private static final String KEY_MEDIA_YEAR = "MediaYear";
	private static final String KEY_MEDIA_IMAGE = "MediaImage";
	private static final String KEY_POS_MS = "PlayPosMs";
	private static final String KEY_DUR_MS = "PlayDurMs";
	private static final String KEY_PAUSED = "PlayPaused";
	private static final String KEY_ANCHOR = "PlayAnchor";
	private static final String KEY_CONTROLLER = "Controller";
	private static final String KEY_PLAY_SESSION = "PlaySession";
	private static final String KEY_MEDIA_SOURCE_ID = "MediaSourceId";
	private static final String KEY_NEXT_ID = "NextItemId";
	private static final String KEY_NEXT_TITLE = "NextTitle";
	private static final String KEY_AUTOPLAY_AT = "AutoplayAt";
	private static final String KEY_START_MS = "StartPosMs";
	private static final String KEY_HDR = "HdrContent";
	private static final String KEY_SUB_INDEX = "SubIndex";
	private static final String KEY_SUB_TRACKS = "SubTracks";
	private static final String KEY_SUB_URL = "SubUrl";
	private static final String KEY_PLEX_MEDIA = "PlexMediaIdx";
	private static final String KEY_PLEX_PART = "PlexPartIdx";
	private static final String KEY_PLEX_PART_KEY = "PlexPartKey";

	public static final int MAX_STREAM_URL = 2048;
	public static final int MAX_OVERVIEW = 1024;

	private boolean powered;
	private boolean suspended;
	private String channelId = "";
	private int channelNumber;
	private String channelName = "";
	private String streamUrl = "";
	private float volume;
	private int channelEpoch;
	private int ticksAlive;
	private boolean panelsValidated;
	private @Nullable String lastPlaybackProblem;

	private MediaSource mediaSource = MediaSource.TUNARR;
	private String jellyfinItemId = "";
	private JellyfinItemKind jellyfinKind = JellyfinItemKind.UNKNOWN;
	private String jellyfinSeriesId = "";
	private String jellyfinSeasonId = "";
	private int seasonNumber;
	private int episodeNumber;
	private String mediaTitle = "";
	private String mediaOverview = "";
	private int mediaYear;
	private String mediaImageUrl = "";
	private long playbackPositionMs;
	private long playbackDurationMs;
	private boolean playbackPaused;
	private long playbackAnchorMillis;
	private String controllingPlayer = "";
	private String playSessionId = "";
	private String jellyfinMediaSourceId = "";
	private String nextEpisodeItemId = "";
	private String nextEpisodeTitle = "";
	private long autoplayAtMillis;
	private long startPositionMs;
	private int progressReportTicks;
	private boolean hdrContent;
	private List<SubtitleTrack> subtitleTracks = List.of();
	private int selectedSubtitleIndex = -1;
	private String subtitleFetchUrl = "";
	private int plexMediaIndex;
	private int plexPartIndex;
	private String plexPartKey = "";

	public DisplayBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.DISPLAY, pos, state);
		this.volume = (float)ConfigManager.get().defaultDisplayVolume;
	}

	public @Nullable DisplayBlock displayBlock() {
		return this.getBlockState().getBlock() instanceof DisplayBlock block ? block : null;
	}

	public DisplayType type() {
		DisplayBlock block = this.displayBlock();
		return block == null ? DisplayType.COMPACT_TELEVISION : block.type();
	}

	public Direction facing() {
		BlockState state = this.getBlockState();
		return state.hasProperty(DisplayBlock.FACING) ? state.getValue(DisplayBlock.FACING) : Direction.NORTH;
	}

	public boolean isPowered() {
		return this.powered;
	}

	public boolean isSuspended() {
		return this.suspended;
	}

	public String getChannelId() {
		return this.channelId;
	}

	public int getChannelNumber() {
		return this.channelNumber;
	}

	public String getChannelName() {
		return this.channelName;
	}

	public String getStreamUrl() {
		return this.streamUrl;
	}

	public float getVolume() {
		return this.volume;
	}

	public int getChannelEpoch() {
		return this.channelEpoch;
	}

	public MediaSource getMediaSource() {
		return this.mediaSource;
	}

	public boolean isJellyfin() {
		return this.mediaSource == MediaSource.JELLYFIN;
	}

	public boolean isOnDemand() {
		return this.mediaSource.isOnDemand();
	}

	public boolean isHdrContent() {
		return this.hdrContent;
	}

	public List<SubtitleTrack> getSubtitleTracks() {
		return this.subtitleTracks;
	}

	public int getSelectedSubtitleIndex() {
		return this.selectedSubtitleIndex;
	}

	public boolean hasSubtitleTracks() {
		return !this.subtitleTracks.isEmpty();
	}

	public String getSubtitleLabel() {
		if (this.selectedSubtitleIndex < 0) {
			return SubtitleTrack.OFF.displayLabel();
		}
		for (SubtitleTrack track : this.subtitleTracks) {
			if (track.index() == this.selectedSubtitleIndex) {
				return track.displayLabel();
			}
		}
		return SubtitleTrack.OFF.displayLabel();
	}

	public int getPlexMediaIndex() {
		return this.plexMediaIndex;
	}

	public int getPlexPartIndex() {
		return this.plexPartIndex;
	}

	public String getPlexPartKey() {
		return this.plexPartKey;
	}

	public String getSubtitleFetchUrl() {
		return this.subtitleFetchUrl;
	}

	public com.pixelreel.ondemand.OnDemandProvider onDemandProvider() {
		return com.pixelreel.ondemand.OnDemandProvider.fromMediaSource(this.mediaSource);
	}

	public String getJellyfinItemId() {
		return this.jellyfinItemId;
	}

	public JellyfinItemKind getJellyfinKind() {
		return this.jellyfinKind;
	}

	public String getJellyfinSeriesId() {
		return this.jellyfinSeriesId;
	}

	public String getJellyfinSeasonId() {
		return this.jellyfinSeasonId;
	}

	public int getSeasonNumber() {
		return this.seasonNumber;
	}

	public int getEpisodeNumber() {
		return this.episodeNumber;
	}

	public String getMediaTitle() {
		return this.mediaTitle.isEmpty() ? this.channelName : this.mediaTitle;
	}

	public String getMediaOverview() {
		return this.mediaOverview;
	}

	public int getMediaYear() {
		return this.mediaYear;
	}

	public String getMediaImageUrl() {
		return this.mediaImageUrl;
	}

	public boolean isPlaybackPaused() {
		return this.playbackPaused;
	}

	public long getPlaybackDurationMs() {
		return this.playbackDurationMs;
	}

	public long getStartPositionMs() {
		return this.startPositionMs;
	}

	public String getControllingPlayer() {
		return this.controllingPlayer;
	}

	public String getPlaySessionId() {
		return this.playSessionId;
	}

	public String getJellyfinMediaSourceId() {
		return this.jellyfinMediaSourceId.isEmpty() ? this.jellyfinItemId : this.jellyfinMediaSourceId;
	}

	public String getNextEpisodeItemId() {
		return this.nextEpisodeItemId;
	}

	public String getNextEpisodeTitle() {
		return this.nextEpisodeTitle;
	}

	public long getAutoplayAtMillis() {
		return this.autoplayAtMillis;
	}

	public boolean hasAutoplayPending() {
		return this.autoplayAtMillis > 0L && !this.nextEpisodeItemId.isEmpty();
	}

	public boolean hasChannel() {
		return !this.channelId.isEmpty() || !this.jellyfinItemId.isEmpty();
	}

	public boolean shouldPlay() {
		if (this.level != null && !this.level.isClientSide()) {
			return this.powered && !this.suspended && !this.streamUrl.isEmpty() && this.hasChannel();
		}
		return this.powered && !this.suspended && this.hasChannel();
	}

	public long currentPlaybackPositionMs() {
		if (!this.isOnDemand()) {
			return 0L;
		}
		if (this.playbackPaused || this.playbackAnchorMillis <= 0L) {
			return clampPos(this.playbackPositionMs);
		}
		long elapsed = Math.max(0L, System.currentTimeMillis() - this.playbackAnchorMillis);
		return clampPos(this.playbackPositionMs + elapsed);
	}

	private long clampPos(long positionMs) {
		if (this.playbackDurationMs > 0L) {
			return Math.min(this.playbackDurationMs, Math.max(0L, positionMs));
		}
		return Math.max(0L, positionMs);
	}

	public Vec3 screenCentre() {
		DisplayType type = this.type();
		Direction facing = this.facing();
		Direction axis = facing.getCounterClockWise();
		double halfWidthOffset = (type.widthBlocks() - 1) / 2.0 - type.controllerColumn();
		Vec3 base = Vec3.atCenterOf(this.worldPosition);
		return base.add(
			axis.getStepX() * halfWidthOffset,
			type.heightBlocks() / 2.0 - 0.5,
			axis.getStepZ() * halfWidthOffset
		);
	}

	public void setPowered(boolean value) {
		if (this.powered != value) {
			this.powered = value;
			if (!value) {
				this.suspended = false;
				this.clearAutoplay();
			}
			BlockState state = this.getBlockState();
			if (this.level != null && state.hasProperty(DisplayBlock.POWERED)) {
				this.level.setBlock(this.worldPosition, state.setValue(DisplayBlock.POWERED, value), 3);
			}
			this.markDirtyAndSync();
		}
	}

	public void setSuspended(boolean value) {
		if (this.suspended != value) {
			if (value && this.isOnDemand() && !this.playbackPaused) {
				this.capturePosition();
			}
			this.suspended = value;
			if (value) {
				this.clearAutoplay();
			}
			this.markDirtyAndSync();
		}
	}

	public void tuneTo(Channel channel) {
		this.mediaSource = MediaSource.TUNARR;
		this.channelId = channel.id();
		this.channelNumber = channel.number();
		this.channelName = channel.name();
		this.streamUrl = channel.streamUrl();
		this.suspended = false;
		this.clearJellyfinFields();
		this.hdrContent = false;
		this.channelEpoch++;
		this.revokePlaybackTickets();
		this.markDirtyAndSync();
	}

	public void playJellyfin(
		JellyfinItemSummary item,
		String streamUrl,
		String playSessionId,
		String mediaSourceId,
		long startPositionMs,
		String controllerName
	) {
		this.playOnDemand(
			com.pixelreel.ondemand.OnDemandProvider.JELLYFIN,
			item,
			streamUrl,
			playSessionId,
			mediaSourceId,
			startPositionMs,
			controllerName
		);
	}

	public void playOnDemand(
		com.pixelreel.ondemand.OnDemandProvider provider,
		JellyfinItemSummary item,
		String streamUrl,
		String playSessionId,
		String mediaSourceId,
		long startPositionMs,
		String controllerName
	) {
		this.playOnDemand(provider, item, streamUrl, playSessionId, mediaSourceId, startPositionMs, controllerName, false);
	}

	public void playOnDemand(
		com.pixelreel.ondemand.OnDemandProvider provider,
		JellyfinItemSummary item,
		String streamUrl,
		String playSessionId,
		String mediaSourceId,
		long startPositionMs,
		String controllerName,
		boolean hdrContent
	) {
		this.playOnDemand(
			provider, item, streamUrl, playSessionId, mediaSourceId, startPositionMs, controllerName, hdrContent, List.of(), 0, 0, ""
		);
	}

	public void playOnDemand(
		com.pixelreel.ondemand.OnDemandProvider provider,
		JellyfinItemSummary item,
		String streamUrl,
		String playSessionId,
		String mediaSourceId,
		long startPositionMs,
		String controllerName,
		boolean hdrContent,
		List<SubtitleTrack> subtitles,
		int plexMediaIndex,
		int plexPartIndex,
		String plexPartKey
	) {
		this.mediaSource = provider.toMediaSource();
		this.jellyfinItemId = item.id();
		this.jellyfinKind = item.kind();
		this.jellyfinSeriesId = item.seriesId();
		this.jellyfinSeasonId = item.seasonId();
		this.seasonNumber = item.parentIndexNumber();
		this.episodeNumber = item.indexNumber();
		this.mediaTitle = item.title();
		this.mediaOverview = clampLength(item.overview(), MAX_OVERVIEW);
		this.mediaYear = item.productionYear();
		this.mediaImageUrl = item.imageUrl();
		String prefix = switch (provider) {
			case JELLYFIN -> "jf:";
			case EMBY -> "emby:";
			case PLEX -> "plex:";
		};
		this.channelId = prefix + item.id();
		this.channelNumber = 0;
		this.channelName = item.kind() == JellyfinItemKind.EPISODE && !item.seriesName().isEmpty()
			? item.seriesName() + " - " + item.title()
			: item.title();
		this.streamUrl = clampLength(streamUrl, MAX_STREAM_URL);
		this.playSessionId = playSessionId == null ? "" : playSessionId;
		this.jellyfinMediaSourceId = mediaSourceId == null || mediaSourceId.isBlank() ? item.id() : mediaSourceId;
		this.startPositionMs = Math.max(0L, startPositionMs);
		this.playbackPositionMs = this.startPositionMs;
		this.playbackDurationMs = item.runtimeMs();
		this.playbackPaused = false;
		this.playbackAnchorMillis = System.currentTimeMillis();
		this.controllingPlayer = controllerName == null ? "" : controllerName;
		this.hdrContent = hdrContent;
		this.subtitleTracks = subtitles == null ? List.of() : List.copyOf(subtitles);
		this.selectedSubtitleIndex = -1;
		this.subtitleFetchUrl = "";
		this.plexMediaIndex = Math.max(0, plexMediaIndex);
		this.plexPartIndex = Math.max(0, plexPartIndex);
		this.plexPartKey = plexPartKey == null ? "" : clampLength(plexPartKey, MAX_STREAM_URL);
		this.suspended = false;
		this.clearAutoplay();
		this.channelEpoch++;
		this.progressReportTicks = 0;
		this.revokePlaybackTickets();
		this.markDirtyAndSync();
	}

	public void applySubtitleSelection(int subtitleIndex, String subtitleUrl) {
		this.applySubtitleSelection(subtitleIndex, subtitleUrl, null);
	}

	public void applySubtitleSelection(int subtitleIndex, String subtitleUrl, @Nullable String newStreamUrl) {
		String previousSubtitleUrl = this.subtitleFetchUrl;
		this.selectedSubtitleIndex = subtitleIndex;
		this.subtitleFetchUrl = clampLength(subtitleUrl == null ? "" : subtitleUrl, MAX_STREAM_URL);
		if (newStreamUrl != null && !newStreamUrl.isBlank()) {
			String next = clampLength(newStreamUrl, MAX_STREAM_URL);
			if (!next.equals(this.streamUrl)) {
				this.capturePosition();
				this.startPositionMs = this.playbackPositionMs;
				this.streamUrl = next;
				this.channelEpoch++;
			}
		}
		if (!this.subtitleFetchUrl.equals(previousSubtitleUrl)) {
			this.channelEpoch++;
		}
		this.revokePlaybackTickets();
		this.markDirtyAndSync();
	}

	private void revokePlaybackTickets() {
		if (this.level == null || this.level.isClientSide()) {
			return;
		}
		MediaProxy.INSTANCE.revoke(MediaProxy.displayKey(this.level.dimension().identifier().toString(), this.worldPosition));
	}

	public int nextSubtitleIndex() {
		if (this.subtitleTracks.isEmpty()) {
			return -1;
		}
		if (this.selectedSubtitleIndex < 0) {
			return this.subtitleTracks.getFirst().index();
		}
		for (int i = 0; i < this.subtitleTracks.size(); i++) {
			if (this.subtitleTracks.get(i).index() == this.selectedSubtitleIndex) {
				if (i + 1 < this.subtitleTracks.size()) {
					return this.subtitleTracks.get(i + 1).index();
				}
				return -1;
			}
		}
		return -1;
	}

	public void setPlaybackPaused(boolean paused) {
		if (!this.isOnDemand()) {
			return;
		}
		if (this.playbackPaused == paused) {
			return;
		}
		if (paused) {
			this.capturePosition();
			this.playbackPaused = true;
		} else {
			this.playbackPaused = false;
			this.playbackAnchorMillis = System.currentTimeMillis();
		}
		this.markDirtyAndSync();
	}

	public void seekTo(long positionMs) {
		if (!this.isOnDemand()) {
			return;
		}
		this.playbackPositionMs = clampPos(positionMs);
		this.startPositionMs = this.playbackPositionMs;
		this.playbackAnchorMillis = System.currentTimeMillis();
		this.channelEpoch++;
		this.markDirtyAndSync();
	}

	public void restartPlayback() {
		this.seekTo(0L);
		this.playbackPaused = false;
		this.suspended = false;
		this.clearAutoplay();
		this.markDirtyAndSync();
	}

	public void setDurationMs(long durationMs) {
		if (durationMs > 0L && this.playbackDurationMs != durationMs) {
			this.playbackDurationMs = durationMs;
			this.markDirtyAndSync();
		}
	}

	public void beginAutoplay(String nextItemId, String nextTitle, long autoplayAtMillis) {
		this.nextEpisodeItemId = nextItemId == null ? "" : nextItemId;
		this.nextEpisodeTitle = nextTitle == null ? "" : nextTitle;
		this.autoplayAtMillis = autoplayAtMillis;
		this.markDirtyAndSync();
	}

	public void clearAutoplay() {
		if (this.nextEpisodeItemId.isEmpty() && this.autoplayAtMillis == 0L) {
			return;
		}
		this.nextEpisodeItemId = "";
		this.nextEpisodeTitle = "";
		this.autoplayAtMillis = 0L;
		this.markDirtyAndSync();
	}

	public void capturePosition() {
		if (!this.isOnDemand()) {
			return;
		}
		this.playbackPositionMs = this.currentPlaybackPositionMs();
		this.playbackAnchorMillis = System.currentTimeMillis();
	}

	public void setVolume(float value) {
		float clamped = Math.clamp(value, 0.0F, 1.0F);
		if (Math.abs(this.volume - clamped) >= 1.0E-4F) {
			this.volume = clamped;
			this.markDirtyAndSync();
		}
	}

	public void setLastPlaybackProblem(@Nullable String problem) {
		this.lastPlaybackProblem = problem;
	}

	public @Nullable String lastPlaybackProblem() {
		return this.lastPlaybackProblem;
	}

	public void bumpProgressReportTicks() {
		this.progressReportTicks++;
	}

	public void resetProgressReportTicks() {
		this.progressReportTicks = 0;
	}

	public void serverTick() {
		if (this.level == null || this.level.isClientSide()) {
			return;
		}
		if (!this.panelsValidated) {
			if (++this.ticksAlive >= 20) {
				this.panelsValidated = true;
				DisplayBlock block = this.displayBlock();
				if (block != null) {
					DisplayBlock.placePanels(this.level, this.worldPosition, block.type(), this.facing(), true);
				}
			}
		}
		if (this.hasAutoplayPending() && System.currentTimeMillis() >= this.autoplayAtMillis) {
			String nextId = this.nextEpisodeItemId;
			this.clearAutoplay();
			com.pixelreel.server.ScreenControllerLogic.playOnDemandItem(this, this.onDemandProvider(), nextId, 0L, this.controllingPlayer);
		}
		if (this.shouldPlay() && this.isOnDemand() && !this.playbackPaused) {
			this.bumpProgressReportTicks();
			int interval = Math.max(5, ConfigManager.get().jellyfinProgressReportSeconds) * 20;
			if (this.progressReportTicks >= interval) {
				this.resetProgressReportTicks();
				com.pixelreel.server.ScreenControllerLogic.reportProgress(this);
			}
		}
	}

	private void clearJellyfinFields() {
		this.jellyfinItemId = "";
		this.jellyfinKind = JellyfinItemKind.UNKNOWN;
		this.jellyfinSeriesId = "";
		this.jellyfinSeasonId = "";
		this.seasonNumber = 0;
		this.episodeNumber = 0;
		this.mediaTitle = "";
		this.mediaOverview = "";
		this.mediaYear = 0;
		this.mediaImageUrl = "";
		this.playbackPositionMs = 0L;
		this.playbackDurationMs = 0L;
		this.playbackPaused = false;
		this.playbackAnchorMillis = 0L;
		this.controllingPlayer = "";
		this.playSessionId = "";
		this.jellyfinMediaSourceId = "";
		this.startPositionMs = 0L;
		this.hdrContent = false;
		this.subtitleTracks = List.of();
		this.selectedSubtitleIndex = -1;
		this.subtitleFetchUrl = "";
		this.plexMediaIndex = 0;
		this.plexPartIndex = 0;
		this.plexPartKey = "";
		this.clearAutoplay();
	}

	private void markDirtyAndSync() {
		this.setChanged();
		if (this.level != null && !this.level.isClientSide()) {
			BlockState state = this.getBlockState();
			this.level.sendBlockUpdated(this.worldPosition, state, state, 3);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putBoolean(KEY_POWERED, this.powered);
		output.putBoolean(KEY_SUSPENDED, this.suspended);
		output.putString(KEY_CHANNEL_ID, this.channelId);
		output.putInt(KEY_CHANNEL_NUMBER, this.channelNumber);
		output.putString(KEY_CHANNEL_NAME, this.channelName);
		output.putString(KEY_STREAM_URL, "");
		output.putFloat(KEY_VOLUME, this.volume);
		output.putInt(KEY_EPOCH, this.channelEpoch);
		output.putString(KEY_MEDIA_SOURCE, this.mediaSource.name());
		output.putString(KEY_JF_ITEM_ID, this.jellyfinItemId);
		output.putString(KEY_JF_KIND, this.jellyfinKind.name());
		output.putString(KEY_JF_SERIES_ID, this.jellyfinSeriesId);
		output.putString(KEY_JF_SEASON_ID, this.jellyfinSeasonId);
		output.putInt(KEY_JF_SEASON, this.seasonNumber);
		output.putInt(KEY_JF_EPISODE, this.episodeNumber);
		output.putString(KEY_MEDIA_TITLE, this.mediaTitle);
		output.putString(KEY_MEDIA_OVERVIEW, this.mediaOverview);
		output.putInt(KEY_MEDIA_YEAR, this.mediaYear);
		output.putString(KEY_MEDIA_IMAGE, "");
		output.putLong(KEY_POS_MS, this.currentPlaybackPositionMs());
		output.putLong(KEY_DUR_MS, this.playbackDurationMs);
		output.putBoolean(KEY_PAUSED, this.playbackPaused);
		output.putLong(KEY_ANCHOR, this.playbackPaused ? 0L : System.currentTimeMillis());
		output.putString(KEY_CONTROLLER, this.controllingPlayer);
		output.putString(KEY_PLAY_SESSION, this.playSessionId);
		output.putString(KEY_MEDIA_SOURCE_ID, this.jellyfinMediaSourceId);
		output.putString(KEY_NEXT_ID, this.nextEpisodeItemId);
		output.putString(KEY_NEXT_TITLE, this.nextEpisodeTitle);
		output.putLong(KEY_AUTOPLAY_AT, this.autoplayAtMillis);
		output.putLong(KEY_START_MS, this.startPositionMs);
		output.putBoolean(KEY_HDR, this.hdrContent);
		output.putInt(KEY_SUB_INDEX, this.selectedSubtitleIndex);
		SubtitleTrack.writeList(output, KEY_SUB_TRACKS, this.subtitleTracks);
		output.putString(KEY_SUB_URL, "");
		output.putInt(KEY_PLEX_MEDIA, this.plexMediaIndex);
		output.putInt(KEY_PLEX_PART, this.plexPartIndex);
		output.putString(KEY_PLEX_PART_KEY, "");
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.powered = input.getBooleanOr(KEY_POWERED, false);
		this.suspended = input.getBooleanOr(KEY_SUSPENDED, false);
		this.channelId = clampLength(input.getStringOr(KEY_CHANNEL_ID, ""), Channel.MAX_TEXT);
		this.channelNumber = Math.max(0, input.getIntOr(KEY_CHANNEL_NUMBER, 0));
		this.channelName = clampLength(input.getStringOr(KEY_CHANNEL_NAME, ""), Channel.MAX_TEXT);
		this.streamUrl = clampLength(input.getStringOr(KEY_STREAM_URL, ""), MAX_STREAM_URL);
		this.volume = Math.clamp(input.getFloatOr(KEY_VOLUME, (float)ConfigManager.get().defaultDisplayVolume), 0.0F, 1.0F);
		this.channelEpoch = input.getIntOr(KEY_EPOCH, 0);
		this.mediaSource = MediaSource.byName(input.getStringOr(KEY_MEDIA_SOURCE, MediaSource.TUNARR.name()));
		this.jellyfinItemId = clampLength(input.getStringOr(KEY_JF_ITEM_ID, ""), Channel.MAX_TEXT);
		this.jellyfinKind = parseKind(input.getStringOr(KEY_JF_KIND, ""));
		this.jellyfinSeriesId = clampLength(input.getStringOr(KEY_JF_SERIES_ID, ""), Channel.MAX_TEXT);
		this.jellyfinSeasonId = clampLength(input.getStringOr(KEY_JF_SEASON_ID, ""), Channel.MAX_TEXT);
		this.seasonNumber = Math.max(0, input.getIntOr(KEY_JF_SEASON, 0));
		this.episodeNumber = Math.max(0, input.getIntOr(KEY_JF_EPISODE, 0));
		this.mediaTitle = clampLength(input.getStringOr(KEY_MEDIA_TITLE, ""), Channel.MAX_TEXT);
		this.mediaOverview = clampLength(input.getStringOr(KEY_MEDIA_OVERVIEW, ""), MAX_OVERVIEW);
		this.mediaYear = Math.max(0, input.getIntOr(KEY_MEDIA_YEAR, 0));
		this.mediaImageUrl = clampLength(input.getStringOr(KEY_MEDIA_IMAGE, ""), MAX_STREAM_URL);
		this.playbackPositionMs = Math.max(0L, input.getLongOr(KEY_POS_MS, 0L));
		this.playbackDurationMs = Math.max(0L, input.getLongOr(KEY_DUR_MS, 0L));
		this.playbackPaused = input.getBooleanOr(KEY_PAUSED, false);
		this.playbackAnchorMillis = input.getLongOr(KEY_ANCHOR, this.playbackPaused ? 0L : System.currentTimeMillis());
		this.controllingPlayer = clampLength(input.getStringOr(KEY_CONTROLLER, ""), Channel.MAX_TEXT);
		this.playSessionId = clampLength(input.getStringOr(KEY_PLAY_SESSION, ""), Channel.MAX_TEXT);
		this.jellyfinMediaSourceId = clampLength(input.getStringOr(KEY_MEDIA_SOURCE_ID, ""), Channel.MAX_TEXT);
		this.nextEpisodeItemId = clampLength(input.getStringOr(KEY_NEXT_ID, ""), Channel.MAX_TEXT);
		this.nextEpisodeTitle = clampLength(input.getStringOr(KEY_NEXT_TITLE, ""), Channel.MAX_TEXT);
		this.autoplayAtMillis = Math.max(0L, input.getLongOr(KEY_AUTOPLAY_AT, 0L));
		this.startPositionMs = Math.max(0L, input.getLongOr(KEY_START_MS, this.playbackPositionMs));
		this.hdrContent = input.getBooleanOr(KEY_HDR, false);
		this.selectedSubtitleIndex = input.getIntOr(KEY_SUB_INDEX, -1);
		this.subtitleTracks = SubtitleTrack.readList(input, KEY_SUB_TRACKS);
		this.subtitleFetchUrl = clampLength(input.getStringOr(KEY_SUB_URL, ""), MAX_STREAM_URL);
		this.plexMediaIndex = Math.max(0, input.getIntOr(KEY_PLEX_MEDIA, 0));
		this.plexPartIndex = Math.max(0, input.getIntOr(KEY_PLEX_PART, 0));
		this.plexPartKey = clampLength(input.getStringOr(KEY_PLEX_PART_KEY, ""), MAX_STREAM_URL);
	}

	private static JellyfinItemKind parseKind(String raw) {
		if (raw == null || raw.isBlank()) {
			return JellyfinItemKind.UNKNOWN;
		}
		try {
			return JellyfinItemKind.valueOf(raw);
		} catch (IllegalArgumentException e) {
			return JellyfinItemKind.UNKNOWN;
		}
	}

	private static String clampLength(@Nullable String value, int max) {
		if (value == null) {
			return "";
		}
		return value.length() <= max ? value : value.substring(0, max);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return this.saveCustomOnly(registries);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		if (this.level != null && this.level.isClientSide()) {
			ClientBridge.releasePlayback(this.worldPosition);
		}
	}
}
