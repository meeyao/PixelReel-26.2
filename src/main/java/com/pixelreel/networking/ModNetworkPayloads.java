package com.pixelreel.networking;

import com.pixelreel.PixelReel;
import com.pixelreel.channels.ChannelEntry;
import com.pixelreel.channels.LiveStatus;
import com.pixelreel.config.PixelReelConfig;
import com.pixelreel.jellyfin.JellyfinItemSummary;
import com.pixelreel.jellyfin.JellyfinLibrary;
import com.pixelreel.jellyfin.JellyfinService;
import com.pixelreel.jellyfin.JellyfinStatus;
import com.pixelreel.ondemand.OnDemandProvider;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class ModNetworkPayloads {
	public static final int MAX_CHANNELS = 2048;
	public static final int MAX_JF_PAGE = JellyfinService.PAGE_SIZE;
	public static final int MAX_JF_CHILDREN = 256;

	private ModNetworkPayloads() {
	}

	public static void register() {
		PayloadTypeRegistry<RegistryFriendlyByteBuf> serverbound = PayloadTypeRegistry.serverboundPlay();
		serverbound.register(ScreenControl.TYPE, ScreenControl.CODEC);
		serverbound.register(ScreenTune.TYPE, ScreenTune.CODEC);
		serverbound.register(RequestPlaybackUrl.TYPE, RequestPlaybackUrl.CODEC);
		serverbound.register(RequestPoster.TYPE, RequestPoster.CODEC);
		serverbound.register(RequestChannels.TYPE, RequestChannels.CODEC);
		serverbound.register(RequestMediaFeatures.TYPE, RequestMediaFeatures.CODEC);
		serverbound.register(RequestJellyfinBrowse.TYPE, RequestJellyfinBrowse.CODEC);
		serverbound.register(RequestJellyfinChildren.TYPE, RequestJellyfinChildren.CODEC);
		serverbound.register(ScreenPlayJellyfin.TYPE, ScreenPlayJellyfin.CODEC);
		serverbound.register(ReportMediaEnded.TYPE, ReportMediaEnded.CODEC);
		serverbound.register(RequestJellyfinConfig.TYPE, RequestJellyfinConfig.CODEC);
		serverbound.register(UpdateJellyfinConfig.TYPE, UpdateJellyfinConfig.CODEC);
		serverbound.register(RequestEmbyConfig.TYPE, RequestEmbyConfig.CODEC);
		serverbound.register(UpdateEmbyConfig.TYPE, UpdateEmbyConfig.CODEC);
		serverbound.register(RequestPlexConfig.TYPE, RequestPlexConfig.CODEC);
		serverbound.register(UpdatePlexConfig.TYPE, UpdatePlexConfig.CODEC);
		serverbound.register(RefreshJellyfinLibrary.TYPE, RefreshJellyfinLibrary.CODEC);
		serverbound.register(RequestTunarrConfig.TYPE, RequestTunarrConfig.CODEC);
		serverbound.register(UpdateTunarrConfig.TYPE, UpdateTunarrConfig.CODEC);
		serverbound.register(UnequipPixelGlasses.TYPE, UnequipPixelGlasses.CODEC);

		PayloadTypeRegistry<RegistryFriendlyByteBuf> clientbound = PayloadTypeRegistry.clientboundPlay();
		clientbound.register(ChannelList.TYPE, ChannelList.CODEC);
		clientbound.register(ScreenNotice.TYPE, ScreenNotice.CODEC);
		clientbound.register(OpenMenu.TYPE, OpenMenu.CODEC);
		clientbound.register(RetryDisplay.TYPE, RetryDisplay.CODEC);
		clientbound.register(ShowClientStatus.TYPE, ShowClientStatus.CODEC);
		clientbound.register(PlaybackUrl.TYPE, PlaybackUrl.CODEC);
		clientbound.register(PosterUrl.TYPE, PosterUrl.CODEC);
		clientbound.register(MediaFeatures.TYPE, MediaFeatures.CODEC);
		clientbound.register(JellyfinBrowseResult.TYPE, JellyfinBrowseResult.CODEC);
		clientbound.register(JellyfinChildrenResult.TYPE, JellyfinChildrenResult.CODEC);
		clientbound.register(JellyfinConfigData.TYPE, JellyfinConfigData.CODEC);
		clientbound.register(EmbyConfigData.TYPE, EmbyConfigData.CODEC);
		clientbound.register(PlexConfigData.TYPE, PlexConfigData.CODEC);
		clientbound.register(TunarrConfigData.TYPE, TunarrConfigData.CODEC);
	}

	public record ChannelList(LiveStatus status, List<ChannelEntry> entries) implements CustomPacketPayload {
		public static final Type<ChannelList> TYPE = new Type<>(PixelReel.id("channel_list"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ChannelList> CODEC = StreamCodec.composite(
			LiveStatus.STREAM_CODEC,
			ChannelList::status,
			ChannelEntry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CHANNELS)),
			ChannelList::entries,
			ChannelList::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record RequestChannels(boolean forceRefresh) implements CustomPacketPayload {
		public static final Type<RequestChannels> TYPE = new Type<>(PixelReel.id("request_channels"));
		public static final StreamCodec<RegistryFriendlyByteBuf, RequestChannels> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, RequestChannels::forceRefresh, RequestChannels::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record ScreenControl(BlockPos pos, ScreenAction action, float value) implements CustomPacketPayload {
		public static final Type<ScreenControl> TYPE = new Type<>(PixelReel.id("screen_control"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ScreenControl> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC,
			ScreenControl::pos,
			ByteBufCodecs.VAR_INT.map(ScreenAction::byIndex, Enum::ordinal),
			ScreenControl::action,
			ByteBufCodecs.FLOAT,
			ScreenControl::value,
			ScreenControl::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record ScreenTune(BlockPos pos, String channelId) implements CustomPacketPayload {
		public static final Type<ScreenTune> TYPE = new Type<>(PixelReel.id("screen_tune"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ScreenTune> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC,
			ScreenTune::pos,
			ByteBufCodecs.stringUtf8(128),
			ScreenTune::channelId,
			ScreenTune::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record RequestPlaybackUrl(BlockPos pos, int channelEpoch) implements CustomPacketPayload {
		public static final Type<RequestPlaybackUrl> TYPE = new Type<>(PixelReel.id("request_playback_url"));
		public static final StreamCodec<RegistryFriendlyByteBuf, RequestPlaybackUrl> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC,
			RequestPlaybackUrl::pos,
			ByteBufCodecs.VAR_INT,
			RequestPlaybackUrl::channelEpoch,
			RequestPlaybackUrl::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record PlaybackUrl(
		BlockPos pos,
		int channelEpoch,
		int proxyPort,
		String proxyHost,
		String streamUrl,
		String subtitleUrl
	) implements CustomPacketPayload {
		public static final Type<PlaybackUrl> TYPE = new Type<>(PixelReel.id("playback_url"));
		public static final StreamCodec<RegistryFriendlyByteBuf, PlaybackUrl> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC,
			PlaybackUrl::pos,
			ByteBufCodecs.VAR_INT,
			PlaybackUrl::channelEpoch,
			ByteBufCodecs.VAR_INT,
			PlaybackUrl::proxyPort,
			ByteBufCodecs.stringUtf8(256),
			PlaybackUrl::proxyHost,
			ByteBufCodecs.stringUtf8(2048),
			PlaybackUrl::streamUrl,
			ByteBufCodecs.stringUtf8(2048),
			PlaybackUrl::subtitleUrl,
			PlaybackUrl::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record RequestPoster(OnDemandProvider provider, String itemId) implements CustomPacketPayload {
		public static final Type<RequestPoster> TYPE = new Type<>(PixelReel.id("request_poster"));
		public static final StreamCodec<RegistryFriendlyByteBuf, RequestPoster> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(OnDemandProvider::byIndex, OnDemandProvider::ordinal),
			RequestPoster::provider,
			ByteBufCodecs.stringUtf8(128),
			RequestPoster::itemId,
			RequestPoster::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record PosterUrl(OnDemandProvider provider, String itemId, String url) implements CustomPacketPayload {
		public static final Type<PosterUrl> TYPE = new Type<>(PixelReel.id("poster_url"));
		public static final StreamCodec<RegistryFriendlyByteBuf, PosterUrl> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(OnDemandProvider::byIndex, OnDemandProvider::ordinal),
			PosterUrl::provider,
			ByteBufCodecs.stringUtf8(128),
			PosterUrl::itemId,
			ByteBufCodecs.stringUtf8(2048),
			PosterUrl::url,
			PosterUrl::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record ScreenNotice(BlockPos pos, String translationKey) implements CustomPacketPayload {
		public static final Type<ScreenNotice> TYPE = new Type<>(PixelReel.id("screen_notice"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ScreenNotice> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC,
			ScreenNotice::pos,
			ByteBufCodecs.stringUtf8(128),
			ScreenNotice::translationKey,
			ScreenNotice::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record OpenMenu(BlockPos pos) implements CustomPacketPayload {
		public static final Type<OpenMenu> TYPE = new Type<>(PixelReel.id("open_menu"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenMenu> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, OpenMenu::pos, OpenMenu::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record RetryDisplay(BlockPos pos) implements CustomPacketPayload {
		public static final Type<RetryDisplay> TYPE = new Type<>(PixelReel.id("retry_display"));
		public static final StreamCodec<RegistryFriendlyByteBuf, RetryDisplay> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, RetryDisplay::pos, RetryDisplay::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record ShowClientStatus(BlockPos pos) implements CustomPacketPayload {
		public static final Type<ShowClientStatus> TYPE = new Type<>(PixelReel.id("show_client_status"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ShowClientStatus> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, ShowClientStatus::pos, ShowClientStatus::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record RequestMediaFeatures() implements CustomPacketPayload {
		public static final Type<RequestMediaFeatures> TYPE = new Type<>(PixelReel.id("request_media_features"));
		public static final StreamCodec<RegistryFriendlyByteBuf, RequestMediaFeatures> CODEC = StreamCodec.unit(new RequestMediaFeatures());

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record MediaFeatures(
		boolean canBrowse,
		boolean canPlayTunarr,
		boolean canPlayMovies,
		boolean canPlayShows,
		boolean canControlPlayback,
		boolean canConfigureTunarr,
		boolean canConfigureJellyfin,
		boolean canConfigureEmby,
		boolean canConfigurePlex,
		boolean canRefreshLibrary,
		boolean autoplayNextEpisode,
		boolean jellyfinMovies,
		boolean jellyfinShows,
		boolean embyMovies,
		boolean embyShows,
		boolean plexMovies,
		boolean plexShows,
		JellyfinStatus jellyfinStatus,
		JellyfinStatus embyStatus,
		JellyfinStatus plexStatus,
		LiveStatus tunarrStatus
	) implements CustomPacketPayload {
		public static final Type<MediaFeatures> TYPE = new Type<>(PixelReel.id("media_features"));
		public static final StreamCodec<RegistryFriendlyByteBuf, MediaFeatures> CODEC = StreamCodec.of(
			(buf, value) -> {
				buf.writeBoolean(value.canBrowse);
				buf.writeBoolean(value.canPlayTunarr);
				buf.writeBoolean(value.canPlayMovies);
				buf.writeBoolean(value.canPlayShows);
				buf.writeBoolean(value.canControlPlayback);
				buf.writeBoolean(value.canConfigureTunarr);
				buf.writeBoolean(value.canConfigureJellyfin);
				buf.writeBoolean(value.canConfigureEmby);
				buf.writeBoolean(value.canConfigurePlex);
				buf.writeBoolean(value.canRefreshLibrary);
				buf.writeBoolean(value.autoplayNextEpisode);
				buf.writeBoolean(value.jellyfinMovies);
				buf.writeBoolean(value.jellyfinShows);
				buf.writeBoolean(value.embyMovies);
				buf.writeBoolean(value.embyShows);
				buf.writeBoolean(value.plexMovies);
				buf.writeBoolean(value.plexShows);
				JellyfinStatus.STREAM_CODEC.encode(buf, value.jellyfinStatus);
				JellyfinStatus.STREAM_CODEC.encode(buf, value.embyStatus);
				JellyfinStatus.STREAM_CODEC.encode(buf, value.plexStatus);
				LiveStatus.STREAM_CODEC.encode(buf, value.tunarrStatus);
			},
			buf -> new MediaFeatures(
				buf.readBoolean(),
				buf.readBoolean(),
				buf.readBoolean(),
				buf.readBoolean(),
				buf.readBoolean(),
				buf.readBoolean(),
				buf.readBoolean(),
				buf.readBoolean(),
				buf.readBoolean(),
				buf.readBoolean(),
				buf.readBoolean(),
				buf.readBoolean(),
				buf.readBoolean(),
				buf.readBoolean(),
				buf.readBoolean(),
				buf.readBoolean(),
				buf.readBoolean(),
				JellyfinStatus.STREAM_CODEC.decode(buf),
				JellyfinStatus.STREAM_CODEC.decode(buf),
				JellyfinStatus.STREAM_CODEC.decode(buf),
				LiveStatus.STREAM_CODEC.decode(buf)
			)
		);

		public static MediaFeatures from(
			PixelReelConfig.FeatureFlags flags,
			JellyfinStatus jellyfinStatus,
			JellyfinStatus embyStatus,
			JellyfinStatus plexStatus,
			LiveStatus tunarrStatus
		) {
			return new MediaFeatures(
				flags.canBrowse(),
				flags.canPlayTunarr(),
				flags.canPlayMovies(),
				flags.canPlayShows(),
				flags.canControlPlayback(),
				flags.canConfigureTunarr(),
				flags.canConfigureJellyfin(),
				flags.canConfigureEmby(),
				flags.canConfigurePlex(),
				flags.canRefreshLibrary(),
				flags.autoplayNextEpisode(),
				flags.jellyfinMovies(),
				flags.jellyfinShows(),
				flags.embyMovies(),
				flags.embyShows(),
				flags.plexMovies(),
				flags.plexShows(),
				jellyfinStatus,
				embyStatus,
				plexStatus,
				tunarrStatus
			);
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record RequestTunarrConfig() implements CustomPacketPayload {
		public static final Type<RequestTunarrConfig> TYPE = new Type<>(PixelReel.id("request_tunarr_config"));
		public static final StreamCodec<RegistryFriendlyByteBuf, RequestTunarrConfig> CODEC = StreamCodec.unit(new RequestTunarrConfig());

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record UpdateTunarrConfig(String m3uUrl, String xmltvUrl) implements CustomPacketPayload {
		public static final Type<UpdateTunarrConfig> TYPE = new Type<>(PixelReel.id("update_tunarr_config"));
		public static final StreamCodec<RegistryFriendlyByteBuf, UpdateTunarrConfig> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(512),
			UpdateTunarrConfig::m3uUrl,
			ByteBufCodecs.stringUtf8(512),
			UpdateTunarrConfig::xmltvUrl,
			UpdateTunarrConfig::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record TunarrConfigData(String m3uUrl, String xmltvUrl, LiveStatus status) implements CustomPacketPayload {
		public static final Type<TunarrConfigData> TYPE = new Type<>(PixelReel.id("tunarr_config_data"));
		public static final StreamCodec<RegistryFriendlyByteBuf, TunarrConfigData> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(512),
			TunarrConfigData::m3uUrl,
			ByteBufCodecs.stringUtf8(512),
			TunarrConfigData::xmltvUrl,
			LiveStatus.STREAM_CODEC,
			TunarrConfigData::status,
			TunarrConfigData::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public enum BrowseKind {
		MOVIES,
		SERIES;

		private static final BrowseKind[] VALUES = values();

		public static BrowseKind byIndex(int index) {
			return index >= 0 && index < VALUES.length ? VALUES[index] : MOVIES;
		}
	}

	public record RequestJellyfinBrowse(
		OnDemandProvider provider,
		BrowseKind kind,
		String search,
		int page,
		boolean forceRefresh
	) implements CustomPacketPayload {
		public static final Type<RequestJellyfinBrowse> TYPE = new Type<>(PixelReel.id("request_jf_browse"));
		public static final StreamCodec<RegistryFriendlyByteBuf, RequestJellyfinBrowse> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(OnDemandProvider::byIndex, OnDemandProvider::ordinal),
			RequestJellyfinBrowse::provider,
			ByteBufCodecs.VAR_INT.map(BrowseKind::byIndex, Enum::ordinal),
			RequestJellyfinBrowse::kind,
			ByteBufCodecs.stringUtf8(128),
			RequestJellyfinBrowse::search,
			ByteBufCodecs.VAR_INT,
			RequestJellyfinBrowse::page,
			ByteBufCodecs.BOOL,
			RequestJellyfinBrowse::forceRefresh,
			RequestJellyfinBrowse::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record JellyfinBrowseResult(
		OnDemandProvider provider,
		BrowseKind kind,
		String search,
		int page,
		int totalCount,
		JellyfinStatus status,
		List<JellyfinItemSummary> items
	) implements CustomPacketPayload {
		public static final Type<JellyfinBrowseResult> TYPE = new Type<>(PixelReel.id("jf_browse_result"));
		public static final StreamCodec<RegistryFriendlyByteBuf, JellyfinBrowseResult> CODEC = StreamCodec.of(
			(buf, value) -> {
				buf.writeVarInt(value.provider.ordinal());
				buf.writeVarInt(value.kind.ordinal());
				buf.writeUtf(value.search, 128);
				buf.writeVarInt(value.page);
				buf.writeVarInt(value.totalCount);
				JellyfinStatus.STREAM_CODEC.encode(buf, value.status);
				JellyfinItemSummary.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_JF_PAGE)).encode(buf, value.items);
			},
			buf -> new JellyfinBrowseResult(
				OnDemandProvider.byIndex(buf.readVarInt()),
				BrowseKind.byIndex(buf.readVarInt()),
				buf.readUtf(128),
				buf.readVarInt(),
				buf.readVarInt(),
				JellyfinStatus.STREAM_CODEC.decode(buf),
				JellyfinItemSummary.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_JF_PAGE)).decode(buf)
			)
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public enum ChildrenKind {
		SEASONS,
		EPISODES,
		ITEM;

		private static final ChildrenKind[] VALUES = values();

		public static ChildrenKind byIndex(int index) {
			return index >= 0 && index < VALUES.length ? VALUES[index] : ITEM;
		}
	}

	public record RequestJellyfinChildren(
		OnDemandProvider provider,
		ChildrenKind kind,
		String parentId,
		boolean forceRefresh
	) implements CustomPacketPayload {
		public static final Type<RequestJellyfinChildren> TYPE = new Type<>(PixelReel.id("request_jf_children"));
		public static final StreamCodec<RegistryFriendlyByteBuf, RequestJellyfinChildren> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(OnDemandProvider::byIndex, OnDemandProvider::ordinal),
			RequestJellyfinChildren::provider,
			ByteBufCodecs.VAR_INT.map(ChildrenKind::byIndex, Enum::ordinal),
			RequestJellyfinChildren::kind,
			ByteBufCodecs.stringUtf8(128),
			RequestJellyfinChildren::parentId,
			ByteBufCodecs.BOOL,
			RequestJellyfinChildren::forceRefresh,
			RequestJellyfinChildren::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record JellyfinChildrenResult(
		OnDemandProvider provider,
		ChildrenKind kind,
		String parentId,
		JellyfinStatus status,
		List<JellyfinItemSummary> items
	) implements CustomPacketPayload {
		public static final Type<JellyfinChildrenResult> TYPE = new Type<>(PixelReel.id("jf_children_result"));
		public static final StreamCodec<RegistryFriendlyByteBuf, JellyfinChildrenResult> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(OnDemandProvider::byIndex, OnDemandProvider::ordinal),
			JellyfinChildrenResult::provider,
			ByteBufCodecs.VAR_INT.map(ChildrenKind::byIndex, Enum::ordinal),
			JellyfinChildrenResult::kind,
			ByteBufCodecs.stringUtf8(128),
			JellyfinChildrenResult::parentId,
			JellyfinStatus.STREAM_CODEC,
			JellyfinChildrenResult::status,
			JellyfinItemSummary.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_JF_CHILDREN)),
			JellyfinChildrenResult::items,
			JellyfinChildrenResult::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record ScreenPlayJellyfin(
		OnDemandProvider provider,
		BlockPos pos,
		String itemId,
		long startPositionMs
	) implements CustomPacketPayload {
		public static final Type<ScreenPlayJellyfin> TYPE = new Type<>(PixelReel.id("screen_play_jf"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ScreenPlayJellyfin> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(OnDemandProvider::byIndex, OnDemandProvider::ordinal),
			ScreenPlayJellyfin::provider,
			BlockPos.STREAM_CODEC,
			ScreenPlayJellyfin::pos,
			ByteBufCodecs.stringUtf8(128),
			ScreenPlayJellyfin::itemId,
			ByteBufCodecs.VAR_LONG,
			ScreenPlayJellyfin::startPositionMs,
			ScreenPlayJellyfin::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record ReportMediaEnded(BlockPos pos, int channelEpoch) implements CustomPacketPayload {
		public static final Type<ReportMediaEnded> TYPE = new Type<>(PixelReel.id("report_media_ended"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ReportMediaEnded> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC,
			ReportMediaEnded::pos,
			ByteBufCodecs.VAR_INT,
			ReportMediaEnded::channelEpoch,
			ReportMediaEnded::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record RequestJellyfinConfig() implements CustomPacketPayload {
		public static final Type<RequestJellyfinConfig> TYPE = new Type<>(PixelReel.id("request_jf_config"));
		public static final StreamCodec<RegistryFriendlyByteBuf, RequestJellyfinConfig> CODEC = StreamCodec.unit(new RequestJellyfinConfig());

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record UpdateJellyfinConfig(
		String url,
		String apiKey,
		String userId,
		boolean moviesEnabled,
		boolean tvShowsEnabled,
		boolean autoplayNextEpisode,
		List<String> libraryIds
	) implements CustomPacketPayload {
		public static final Type<UpdateJellyfinConfig> TYPE = new Type<>(PixelReel.id("update_jf_config"));
		public static final StreamCodec<RegistryFriendlyByteBuf, UpdateJellyfinConfig> CODEC = StreamCodec.of(
			(buf, value) -> {
				buf.writeUtf(value.url, 256);
				buf.writeUtf(value.apiKey, 512);
				buf.writeUtf(value.userId, 128);
				buf.writeBoolean(value.moviesEnabled);
				buf.writeBoolean(value.tvShowsEnabled);
				buf.writeBoolean(value.autoplayNextEpisode);
				buf.writeVarInt(value.libraryIds.size());
				for (String id : value.libraryIds) {
					buf.writeUtf(id, 128);
				}
			},
			buf -> {
				String url = buf.readUtf(256);
				String apiKey = buf.readUtf(512);
				String userId = buf.readUtf(128);
				boolean movies = buf.readBoolean();
				boolean shows = buf.readBoolean();
				boolean autoplay = buf.readBoolean();
				int count = buf.readVarInt();
				List<String> ids = new ArrayList<>(count);
				for (int i = 0; i < count; i++) {
					ids.add(buf.readUtf(128));
				}
				return new UpdateJellyfinConfig(url, apiKey, userId, movies, shows, autoplay, ids);
			}
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record RefreshJellyfinLibrary() implements CustomPacketPayload {
		public static final Type<RefreshJellyfinLibrary> TYPE = new Type<>(PixelReel.id("refresh_jf_library"));
		public static final StreamCodec<RegistryFriendlyByteBuf, RefreshJellyfinLibrary> CODEC = StreamCodec.unit(new RefreshJellyfinLibrary());

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record JellyfinConfigData(
		String url,
		String userId,
		boolean moviesEnabled,
		boolean tvShowsEnabled,
		boolean autoplayNextEpisode,
		boolean hasApiKey,
		List<String> selectedLibraryIds,
		List<JellyfinLibrary> availableLibraries,
		JellyfinStatus status
	) implements CustomPacketPayload {
		public static final Type<JellyfinConfigData> TYPE = new Type<>(PixelReel.id("jf_config_data"));
		public static final StreamCodec<RegistryFriendlyByteBuf, JellyfinConfigData> CODEC = StreamCodec.of(
			(buf, value) -> {
				buf.writeUtf(value.url, 256);
				buf.writeUtf(value.userId, 128);
				buf.writeBoolean(value.moviesEnabled);
				buf.writeBoolean(value.tvShowsEnabled);
				buf.writeBoolean(value.autoplayNextEpisode);
				buf.writeBoolean(value.hasApiKey);
				buf.writeVarInt(value.selectedLibraryIds.size());
				for (String id : value.selectedLibraryIds) {
					buf.writeUtf(id, 128);
				}
				JellyfinLibrary.STREAM_CODEC.apply(ByteBufCodecs.list(128)).encode(buf, value.availableLibraries);
				JellyfinStatus.STREAM_CODEC.encode(buf, value.status);
			},
			buf -> {
				String url = buf.readUtf(256);
				String userId = buf.readUtf(128);
				boolean movies = buf.readBoolean();
				boolean shows = buf.readBoolean();
				boolean autoplay = buf.readBoolean();
				boolean hasKey = buf.readBoolean();
				int count = buf.readVarInt();
				List<String> selected = new ArrayList<>(count);
				for (int i = 0; i < count; i++) {
					selected.add(buf.readUtf(128));
				}
				List<JellyfinLibrary> libraries = JellyfinLibrary.STREAM_CODEC.apply(ByteBufCodecs.list(128)).decode(buf);
				JellyfinStatus status = JellyfinStatus.STREAM_CODEC.decode(buf);
				return new JellyfinConfigData(url, userId, movies, shows, autoplay, hasKey, selected, libraries, status);
			}
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record RequestEmbyConfig() implements CustomPacketPayload {
		public static final Type<RequestEmbyConfig> TYPE = new Type<>(PixelReel.id("request_emby_config"));
		public static final StreamCodec<RegistryFriendlyByteBuf, RequestEmbyConfig> CODEC = StreamCodec.unit(new RequestEmbyConfig());

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record UpdateEmbyConfig(
		String url,
		String apiKey,
		String userId,
		boolean moviesEnabled,
		boolean tvShowsEnabled,
		List<String> libraryIds
	) implements CustomPacketPayload {
		public static final Type<UpdateEmbyConfig> TYPE = new Type<>(PixelReel.id("update_emby_config"));
		public static final StreamCodec<RegistryFriendlyByteBuf, UpdateEmbyConfig> CODEC = StreamCodec.of(
			(buf, value) -> {
				buf.writeUtf(value.url, 256);
				buf.writeUtf(value.apiKey, 512);
				buf.writeUtf(value.userId, 128);
				buf.writeBoolean(value.moviesEnabled);
				buf.writeBoolean(value.tvShowsEnabled);
				buf.writeVarInt(value.libraryIds.size());
				for (String id : value.libraryIds) {
					buf.writeUtf(id, 128);
				}
			},
			buf -> {
				String url = buf.readUtf(256);
				String apiKey = buf.readUtf(512);
				String userId = buf.readUtf(128);
				boolean movies = buf.readBoolean();
				boolean shows = buf.readBoolean();
				int count = buf.readVarInt();
				List<String> ids = new ArrayList<>(count);
				for (int i = 0; i < count; i++) {
					ids.add(buf.readUtf(128));
				}
				return new UpdateEmbyConfig(url, apiKey, userId, movies, shows, ids);
			}
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record EmbyConfigData(
		String url,
		String userId,
		boolean moviesEnabled,
		boolean tvShowsEnabled,
		boolean hasApiKey,
		List<String> selectedLibraryIds,
		List<JellyfinLibrary> availableLibraries,
		JellyfinStatus status
	) implements CustomPacketPayload {
		public static final Type<EmbyConfigData> TYPE = new Type<>(PixelReel.id("emby_config_data"));
		public static final StreamCodec<RegistryFriendlyByteBuf, EmbyConfigData> CODEC = StreamCodec.of(
			(buf, value) -> {
				buf.writeUtf(value.url, 256);
				buf.writeUtf(value.userId, 128);
				buf.writeBoolean(value.moviesEnabled);
				buf.writeBoolean(value.tvShowsEnabled);
				buf.writeBoolean(value.hasApiKey);
				buf.writeVarInt(value.selectedLibraryIds.size());
				for (String id : value.selectedLibraryIds) {
					buf.writeUtf(id, 128);
				}
				JellyfinLibrary.STREAM_CODEC.apply(ByteBufCodecs.list(128)).encode(buf, value.availableLibraries);
				JellyfinStatus.STREAM_CODEC.encode(buf, value.status);
			},
			buf -> {
				String url = buf.readUtf(256);
				String userId = buf.readUtf(128);
				boolean movies = buf.readBoolean();
				boolean shows = buf.readBoolean();
				boolean hasKey = buf.readBoolean();
				int count = buf.readVarInt();
				List<String> selected = new ArrayList<>(count);
				for (int i = 0; i < count; i++) {
					selected.add(buf.readUtf(128));
				}
				List<JellyfinLibrary> libraries = JellyfinLibrary.STREAM_CODEC.apply(ByteBufCodecs.list(128)).decode(buf);
				JellyfinStatus status = JellyfinStatus.STREAM_CODEC.decode(buf);
				return new EmbyConfigData(url, userId, movies, shows, hasKey, selected, libraries, status);
			}
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record RequestPlexConfig() implements CustomPacketPayload {
		public static final Type<RequestPlexConfig> TYPE = new Type<>(PixelReel.id("request_plex_config"));
		public static final StreamCodec<RegistryFriendlyByteBuf, RequestPlexConfig> CODEC = StreamCodec.unit(new RequestPlexConfig());

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record UpdatePlexConfig(
		String url,
		String token,
		boolean moviesEnabled,
		boolean tvShowsEnabled,
		List<String> libraryKeys
	) implements CustomPacketPayload {
		public static final Type<UpdatePlexConfig> TYPE = new Type<>(PixelReel.id("update_plex_config"));
		public static final StreamCodec<RegistryFriendlyByteBuf, UpdatePlexConfig> CODEC = StreamCodec.of(
			(buf, value) -> {
				buf.writeUtf(value.url, 256);
				buf.writeUtf(value.token, 512);
				buf.writeBoolean(value.moviesEnabled);
				buf.writeBoolean(value.tvShowsEnabled);
				buf.writeVarInt(value.libraryKeys.size());
				for (String key : value.libraryKeys) {
					buf.writeUtf(key, 128);
				}
			},
			buf -> {
				String url = buf.readUtf(256);
				String token = buf.readUtf(512);
				boolean movies = buf.readBoolean();
				boolean shows = buf.readBoolean();
				int count = buf.readVarInt();
				List<String> keys = new ArrayList<>(count);
				for (int i = 0; i < count; i++) {
					keys.add(buf.readUtf(128));
				}
				return new UpdatePlexConfig(url, token, movies, shows, keys);
			}
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record UnequipPixelGlasses() implements CustomPacketPayload {
		public static final Type<UnequipPixelGlasses> TYPE = new Type<>(PixelReel.id("unequip_pixel_glasses"));
		public static final StreamCodec<RegistryFriendlyByteBuf, UnequipPixelGlasses> CODEC =
			StreamCodec.unit(new UnequipPixelGlasses());

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record PlexConfigData(
		String url,
		boolean moviesEnabled,
		boolean tvShowsEnabled,
		boolean hasToken,
		List<String> selectedLibraryKeys,
		List<JellyfinLibrary> availableLibraries,
		JellyfinStatus status
	) implements CustomPacketPayload {
		public static final Type<PlexConfigData> TYPE = new Type<>(PixelReel.id("plex_config_data"));
		public static final StreamCodec<RegistryFriendlyByteBuf, PlexConfigData> CODEC = StreamCodec.of(
			(buf, value) -> {
				buf.writeUtf(value.url, 256);
				buf.writeBoolean(value.moviesEnabled);
				buf.writeBoolean(value.tvShowsEnabled);
				buf.writeBoolean(value.hasToken);
				buf.writeVarInt(value.selectedLibraryKeys.size());
				for (String key : value.selectedLibraryKeys) {
					buf.writeUtf(key, 128);
				}
				JellyfinLibrary.STREAM_CODEC.apply(ByteBufCodecs.list(128)).encode(buf, value.availableLibraries);
				JellyfinStatus.STREAM_CODEC.encode(buf, value.status);
			},
			buf -> {
				String url = buf.readUtf(256);
				boolean movies = buf.readBoolean();
				boolean shows = buf.readBoolean();
				boolean hasToken = buf.readBoolean();
				int count = buf.readVarInt();
				List<String> selected = new ArrayList<>(count);
				for (int i = 0; i < count; i++) {
					selected.add(buf.readUtf(128));
				}
				List<JellyfinLibrary> libraries = JellyfinLibrary.STREAM_CODEC.apply(ByteBufCodecs.list(128)).decode(buf);
				JellyfinStatus status = JellyfinStatus.STREAM_CODEC.decode(buf);
				return new PlexConfigData(url, movies, shows, hasToken, selected, libraries, status);
			}
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
