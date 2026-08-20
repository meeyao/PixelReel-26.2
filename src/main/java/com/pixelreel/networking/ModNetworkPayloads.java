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
import com.pixelreel.zones.Zone;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public final class ModNetworkPayloads {
	public static final int MAX_CHANNELS = 2048;
	public static final int MAX_JF_PAGE = JellyfinService.PAGE_SIZE;
	public static final int MAX_JF_CHILDREN = 256;

	private ModNetworkPayloads() {
	}

	public record ChannelList(LiveStatus status, List<ChannelEntry> entries) {
		public static final ResourceLocation ID = new ResourceLocation(PixelReel.MOD_ID, "channel_list");

		public void writeToBuf(FriendlyByteBuf buf) {
			this.status.writeToBuf(buf);
			buf.writeVarInt(this.entries.size());
			for (ChannelEntry entry : this.entries) {
				entry.writeToBuf(buf);
			}
		}

		public static ChannelList readFromBuf(FriendlyByteBuf buf) {
			LiveStatus status = LiveStatus.readFromBuf(buf);
			int count = buf.readVarInt();
			List<ChannelEntry> entries = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				entries.add(ChannelEntry.readFromBuf(buf));
			}
			return new ChannelList(status, entries);
		}
	}

	public record RequestChannels(boolean forceRefresh) {
		public static final ResourceLocation ID = new ResourceLocation(PixelReel.MOD_ID, "request_channels");

		public void writeToBuf(FriendlyByteBuf buf) {
			buf.writeBoolean(this.forceRefresh);
		}

		public static RequestChannels readFromBuf(FriendlyByteBuf buf) {
			return new RequestChannels(buf.readBoolean());
		}
	}

	public record ScreenControl(BlockPos pos, ScreenAction action, float value) {
		public static final ResourceLocation ID = new ResourceLocation(PixelReel.MOD_ID, "screen_control");

		public void writeToBuf(FriendlyByteBuf buf) {
			buf.writeBlockPos(this.pos);
			buf.writeVarInt(this.action.ordinal());
			buf.writeFloat(this.value);
		}

		public static ScreenControl readFromBuf(FriendlyByteBuf buf) {
			return new ScreenControl(
				buf.readBlockPos(),
				ScreenAction.byIndex(buf.readVarInt()),
				buf.readFloat()
			);
		}
	}

	public record ScreenTune(BlockPos pos, String channelId) {
		public static final ResourceLocation ID = new ResourceLocation(PixelReel.MOD_ID, "screen_tune");

		public void writeToBuf(FriendlyByteBuf buf) {
			buf.writeBlockPos(this.pos);
			buf.writeUtf(this.channelId, 128);
		}

		public static ScreenTune readFromBuf(FriendlyByteBuf buf) {
			return new ScreenTune(buf.readBlockPos(), buf.readUtf(128));
		}
	}

	public record ScreenNotice(BlockPos pos, String translationKey) {
		public static final ResourceLocation ID = new ResourceLocation(PixelReel.MOD_ID, "screen_notice");

		public void writeToBuf(FriendlyByteBuf buf) {
			buf.writeBlockPos(this.pos);
			buf.writeUtf(this.translationKey, 128);
		}

		public static ScreenNotice readFromBuf(FriendlyByteBuf buf) {
			return new ScreenNotice(buf.readBlockPos(), buf.readUtf(128));
		}
	}

	public record OpenMenu(BlockPos pos) {
		public static final ResourceLocation ID = new ResourceLocation(PixelReel.MOD_ID, "open_menu");

		public void writeToBuf(FriendlyByteBuf buf) {
			buf.writeBlockPos(this.pos);
		}

		public static OpenMenu readFromBuf(FriendlyByteBuf buf) {
			return new OpenMenu(buf.readBlockPos());
		}
	}

	public record RetryDisplay(BlockPos pos) {
		public static final ResourceLocation ID = new ResourceLocation(PixelReel.MOD_ID, "retry_display");

		public void writeToBuf(FriendlyByteBuf buf) {
			buf.writeBlockPos(this.pos);
		}

		public static RetryDisplay readFromBuf(FriendlyByteBuf buf) {
			return new RetryDisplay(buf.readBlockPos());
		}
	}

	public record ShowClientStatus(BlockPos pos) {
		public static final ResourceLocation ID = new ResourceLocation(PixelReel.MOD_ID, "show_client_status");

		public void writeToBuf(FriendlyByteBuf buf) {
			buf.writeBlockPos(this.pos);
		}

		public static ShowClientStatus readFromBuf(FriendlyByteBuf buf) {
			return new ShowClientStatus(buf.readBlockPos());
		}
	}

	public record RequestMediaFeatures() {
		public static final ResourceLocation ID = new ResourceLocation(PixelReel.MOD_ID, "request_media_features");

		public void writeToBuf(FriendlyByteBuf buf) {
		}

		public static RequestMediaFeatures readFromBuf(FriendlyByteBuf buf) {
			return new RequestMediaFeatures();
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
	) {
		public static final ResourceLocation ID = new ResourceLocation(PixelReel.MOD_ID, "media_features");

		public void writeToBuf(FriendlyByteBuf buf) {
			buf.writeBoolean(this.canBrowse);
			buf.writeBoolean(this.canPlayTunarr);
			buf.writeBoolean(this.canPlayMovies);
			buf.writeBoolean(this.canPlayShows);
			buf.writeBoolean(this.canControlPlayback);
			buf.writeBoolean(this.canConfigureTunarr);
			buf.writeBoolean(this.canConfigureJellyfin);
			buf.writeBoolean(this.canConfigureEmby);
			buf.writeBoolean(this.canConfigurePlex);
			buf.writeBoolean(this.canRefreshLibrary);
			buf.writeBoolean(this.autoplayNextEpisode);
			buf.writeBoolean(this.jellyfinMovies);
			buf.writeBoolean(this.jellyfinShows);
			buf.writeBoolean(this.embyMovies);
			buf.writeBoolean(this.embyShows);
			buf.writeBoolean(this.plexMovies);
			buf.writeBoolean(this.plexShows);
			this.jellyfinStatus.writeToBuf(buf);
			this.embyStatus.writeToBuf(buf);
			this.plexStatus.writeToBuf(buf);
			this.tunarrStatus.writeToBuf(buf);
		}

		public static MediaFeatures readFromBuf(FriendlyByteBuf buf) {
			return new MediaFeatures(
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
				JellyfinStatus.readFromBuf(buf),
				JellyfinStatus.readFromBuf(buf),
				JellyfinStatus.readFromBuf(buf),
				LiveStatus.readFromBuf(buf)
			);
		}

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
	}

	public record RequestTunarrConfig() {
		public static final ResourceLocation ID = new ResourceLocation(PixelReel.MOD_ID, "request_tunarr_config");

		public void writeToBuf(FriendlyByteBuf buf) {
		}

		public static RequestTunarrConfig readFromBuf(FriendlyByteBuf buf) {
			return new RequestTunarrConfig();
		}
	}

	public record UpdateTunarrConfig(String m3uUrl, String xmltvUrl) {
		public static final ResourceLocation ID = new ResourceLocation(PixelReel.MOD_ID, "update_tunarr_config");

		public void writeToBuf(FriendlyByteBuf buf) {
			buf.writeUtf(this.m3uUrl, 512);
			buf.writeUtf(this.xmltvUrl, 512);
		}

		public static UpdateTunarrConfig readFromBuf(FriendlyByteBuf buf) {
			return new UpdateTunarrConfig(buf.readUtf(512), buf.readUtf(512));
		}
	}

	public record TunarrConfigData(String m3uUrl, String xmltvUrl, LiveStatus status) {
		public static final ResourceLocation ID = new ResourceLocation(PixelReel.MOD_ID, "tunarr_config_data");

		public void writeToBuf(FriendlyByteBuf buf) {
			buf.writeUtf(this.m3uUrl, 512);
			buf.writeUtf(this.xmltvUrl, 512);
			this.status.writeToBuf(buf);
		}

		public static TunarrConfigData readFromBuf(FriendlyByteBuf buf) {
			return new TunarrConfigData(buf.readUtf(512), buf.readUtf(512), LiveStatus.readFromBuf(buf));
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
	) {
		public static final ResourceLocation ID = new ResourceLocation(PixelReel.MOD_ID, "request_jf_browse");

		public void writeToBuf(FriendlyByteBuf buf) {
			buf.writeVarInt(this.provider.ordinal());
			buf.writeVarInt(this.kind.ordinal());
			buf.writeUtf(this.search, 128);
			buf.writeVarInt(this.page);
			buf.writeBoolean(this.forceRefresh);
		}

		public static RequestJellyfinBrowse readFromBuf(FriendlyByteBuf buf) {
			return new RequestJellyfinBrowse(
				OnDemandProvider.byIndex(buf.readVarInt()),
				BrowseKind.byIndex(buf.readVarInt()),
				buf.readUtf(128),
				buf.readVarInt(),
				buf.readBoolean()
			);
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
	) {
		public static final ResourceLocation ID = new ResourceLocation(PixelReel.MOD_ID, "jf_browse_result");

		public void writeToBuf(FriendlyByteBuf buf) {
			buf.writeVarInt(this.provider.ordinal());
			buf.writeVarInt(this.kind.ordinal());
			buf.writeUtf(this.search, 128);
			buf.writeVarInt(this.page);
			buf.writeVarInt(this.totalCount);
			this.status.writeToBuf(buf);
			buf.writeVarInt(this.items.size());
			for (JellyfinItemSummary item : this.items) {
				item.writeToBuf(buf);
			}
		}

		public static JellyfinBrowseResult readFromBuf(FriendlyByteBuf buf) {
			OnDemandProvider provider = OnDemandProvider.byIndex(buf.readVarInt());
			BrowseKind kind = BrowseKind.byIndex(buf.readVarInt());
			String search = buf.readUtf(128);
			int page = buf.readVarInt();
			int totalCount = buf.readVarInt();
			JellyfinStatus status = JellyfinStatus.readFromBuf(buf);
			int count = buf.readVarInt();
			List<JellyfinItemSummary> items = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				items.add(JellyfinItemSummary.readFromBuf(buf));
			}
			return new JellyfinBrowseResult(provider, kind, search, page, totalCount, status, items);
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
	) {
		public static final ResourceLocation ID = new ResourceLocation(PixelReel.MOD_ID, "request_jf_children");

		public void writeToBuf(FriendlyByteBuf buf) {
			buf.writeVarInt(this.provider.ordinal());
			buf.writeVarInt(this.kind.ordinal());
			buf.writeUtf(this.parentId, 128);
			buf.writeBoolean(this.forceRefresh);
		}

		public static RequestJellyfinChildren readFromBuf(FriendlyByteBuf buf) {
			return new RequestJellyfinChildren(
				OnDemandProvider.byIndex(buf.readVarInt()),
				ChildrenKind.byIndex(buf.readVarInt()),
				buf.readUtf(128),
				buf.readBoolean()
			);
		}
	}

	public record JellyfinChildrenResult(
		OnDemandProvider provider,
		ChildrenKind kind,
		String parentId,
		JellyfinStatus status,
		List<JellyfinItemSummary> items
	) {
		public static final ResourceLocation ID = new ResourceLocation(PixelReel.MOD_ID, "jf_children_result");

		public void writeToBuf(FriendlyByteBuf buf) {
			buf.writeVarInt(this.provider.ordinal());
			buf.writeVarInt(this.kind.ordinal());
			buf.writeUtf(this.parentId, 128);
			this.status.writeToBuf(buf);
			buf.writeVarInt(this.items.size());
			for (JellyfinItemSummary item : this.items) {
				item.writeToBuf(buf);
			}
		}

		public static JellyfinChildrenResult readFromBuf(FriendlyByteBuf buf) {
			OnDemandProvider provider = OnDemandProvider.byIndex(buf.readVarInt());
			ChildrenKind kind = ChildrenKind.byIndex(buf.readVarInt());
			String parentId = buf.readUtf(128);
			JellyfinStatus status = JellyfinStatus.readFromBuf(buf);
			int count = buf.readVarInt();
			List<JellyfinItemSummary> items = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				items.add(JellyfinItemSummary.readFromBuf(buf));
			}
			return new JellyfinChildrenResult(provider, kind, parentId, status, items);
		}
	}

	public record ScreenPlayJellyfin(
		OnDemandProvider provider,
		BlockPos pos,
		String itemId,
		long startPositionMs
	) {
		public static final ResourceLocation ID = new ResourceLocation(PixelReel.MOD_ID, "screen_play_jf");

		public void writeToBuf(FriendlyByteBuf buf) {
			buf.writeVarInt(this.provider.ordinal());
			buf.writeBlockPos(this.pos);
			buf.writeUtf(this.itemId, 128);
			buf.writeVarLong(this.startPositionMs);
		}

		public static ScreenPlayJellyfin readFromBuf(FriendlyByteBuf buf) {
			return new ScreenPlayJellyfin(
				OnDemandProvider.byIndex(buf.readVarInt()),
				buf.readBlockPos(),
				buf.readUtf(128),
				buf.readVarLong()
			);
		}
	}

	public record ReportMediaEnded(BlockPos pos, int channelEpoch) {
		public static final ResourceLocation ID = new ResourceLocation(PixelReel.MOD_ID, "report_media_ended");

		public void writeToBuf(FriendlyByteBuf buf) {
			buf.writeBlockPos(this.pos);
			buf.writeVarInt(this.channelEpoch);
		}

		public static ReportMediaEnded readFromBuf(FriendlyByteBuf buf) {
			return new ReportMediaEnded(buf.readBlockPos(), buf.readVarInt());
		}
	}

	public record RequestJellyfinConfig() {
		public static final ResourceLocation ID = new ResourceLocation(PixelReel.MOD_ID, "request_jf_config");

		public void writeToBuf(FriendlyByteBuf buf) {
		}

		public static RequestJellyfinConfig readFromBuf(FriendlyByteBuf buf) {
			return new RequestJellyfinConfig();
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
	) {
		public static final ResourceLocation ID = new ResourceLocation(PixelReel.MOD_ID, "update_jf_config");

		public void writeToBuf(FriendlyByteBuf buf) {
			buf.writeUtf(this.url, 256);
			buf.writeUtf(this.apiKey, 512);
			buf.writeUtf(this.userId, 128);
			buf.writeBoolean(this.moviesEnabled);
			buf.writeBoolean(this.tvShowsEnabled);
			buf.writeBoolean(this.autoplayNextEpisode);
			buf.writeVarInt(this.libraryIds.size());
			for (String id : this.libraryIds) {
				buf.writeUtf(id, 128);
			}
		}

		public static UpdateJellyfinConfig readFromBuf(FriendlyByteBuf buf) {
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
	}

	public record RefreshJellyfinLibrary() {
		public static final ResourceLocation ID = new ResourceLocation(PixelReel.MOD_ID, "refresh_jf_library");

		public void writeToBuf(FriendlyByteBuf buf) {
		}

		public static RefreshJellyfinLibrary readFromBuf(FriendlyByteBuf buf) {
			return new RefreshJellyfinLibrary();
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
	) {
		public static final ResourceLocation ID = new ResourceLocation(PixelReel.MOD_ID, "jf_config_data");

		public void writeToBuf(FriendlyByteBuf buf) {
			buf.writeUtf(this.url, 256);
			buf.writeUtf(this.userId, 128);
			buf.writeBoolean(this.moviesEnabled);
			buf.writeBoolean(this.tvShowsEnabled);
			buf.writeBoolean(this.autoplayNextEpisode);
			buf.writeBoolean(this.hasApiKey);
			buf.writeVarInt(this.selectedLibraryIds.size());
			for (String id : this.selectedLibraryIds) {
				buf.writeUtf(id, 128);
			}
			buf.writeVarInt(this.availableLibraries.size());
			for (JellyfinLibrary lib : this.availableLibraries) {
				lib.writeToBuf(buf);
			}
			this.status.writeToBuf(buf);
		}

		public static JellyfinConfigData readFromBuf(FriendlyByteBuf buf) {
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
			int libCount = buf.readVarInt();
			List<JellyfinLibrary> libraries = new ArrayList<>(libCount);
			for (int i = 0; i < libCount; i++) {
				libraries.add(JellyfinLibrary.readFromBuf(buf));
			}
			JellyfinStatus status = JellyfinStatus.readFromBuf(buf);
			return new JellyfinConfigData(url, userId, movies, shows, autoplay, hasKey, selected, libraries, status);
		}
	}

	public record RequestEmbyConfig() {
		public static final ResourceLocation ID = new ResourceLocation(PixelReel.MOD_ID, "request_emby_config");

		public void writeToBuf(FriendlyByteBuf buf) {
		}

		public static RequestEmbyConfig readFromBuf(FriendlyByteBuf buf) {
			return new RequestEmbyConfig();
		}
	}

	public record UpdateEmbyConfig(
		String url,
		String apiKey,
		String userId,
		boolean moviesEnabled,
		boolean tvShowsEnabled,
		List<String> libraryIds
	) {
		public static final ResourceLocation ID = new ResourceLocation(PixelReel.MOD_ID, "update_emby_config");

		public void writeToBuf(FriendlyByteBuf buf) {
			buf.writeUtf(this.url, 256);
			buf.writeUtf(this.apiKey, 512);
			buf.writeUtf(this.userId, 128);
			buf.writeBoolean(this.moviesEnabled);
			buf.writeBoolean(this.tvShowsEnabled);
			buf.writeVarInt(this.libraryIds.size());
			for (String id : this.libraryIds) {
				buf.writeUtf(id, 128);
			}
		}

		public static UpdateEmbyConfig readFromBuf(FriendlyByteBuf buf) {
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
	) {
		public static final ResourceLocation ID = new ResourceLocation(PixelReel.MOD_ID, "emby_config_data");

		public void writeToBuf(FriendlyByteBuf buf) {
			buf.writeUtf(this.url, 256);
			buf.writeUtf(this.userId, 128);
			buf.writeBoolean(this.moviesEnabled);
			buf.writeBoolean(this.tvShowsEnabled);
			buf.writeBoolean(this.hasApiKey);
			buf.writeVarInt(this.selectedLibraryIds.size());
			for (String id : this.selectedLibraryIds) {
				buf.writeUtf(id, 128);
			}
			buf.writeVarInt(this.availableLibraries.size());
			for (JellyfinLibrary lib : this.availableLibraries) {
				lib.writeToBuf(buf);
			}
			this.status.writeToBuf(buf);
		}

		public static EmbyConfigData readFromBuf(FriendlyByteBuf buf) {
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
			int libCount = buf.readVarInt();
			List<JellyfinLibrary> libraries = new ArrayList<>(libCount);
			for (int i = 0; i < libCount; i++) {
				libraries.add(JellyfinLibrary.readFromBuf(buf));
			}
			JellyfinStatus status = JellyfinStatus.readFromBuf(buf);
			return new EmbyConfigData(url, userId, movies, shows, hasKey, selected, libraries, status);
		}
	}

	public record RequestPlexConfig() {
		public static final ResourceLocation ID = new ResourceLocation(PixelReel.MOD_ID, "request_plex_config");

		public void writeToBuf(FriendlyByteBuf buf) {
		}

		public static RequestPlexConfig readFromBuf(FriendlyByteBuf buf) {
			return new RequestPlexConfig();
		}
	}

	public record UpdatePlexConfig(
		String url,
		String token,
		boolean moviesEnabled,
		boolean tvShowsEnabled,
		List<String> libraryKeys
	) {
		public static final ResourceLocation ID = new ResourceLocation(PixelReel.MOD_ID, "update_plex_config");

		public void writeToBuf(FriendlyByteBuf buf) {
			buf.writeUtf(this.url, 256);
			buf.writeUtf(this.token, 512);
			buf.writeBoolean(this.moviesEnabled);
			buf.writeBoolean(this.tvShowsEnabled);
			buf.writeVarInt(this.libraryKeys.size());
			for (String key : this.libraryKeys) {
				buf.writeUtf(key, 128);
			}
		}

		public static UpdatePlexConfig readFromBuf(FriendlyByteBuf buf) {
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
	}

	public record UnequipPixelGlasses() {
		public static final ResourceLocation ID = new ResourceLocation(PixelReel.MOD_ID, "unequip_pixel_glasses");

		public void writeToBuf(FriendlyByteBuf buf) {
		}

		public static UnequipPixelGlasses readFromBuf(FriendlyByteBuf buf) {
			return new UnequipPixelGlasses();
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
	) {
		public static final ResourceLocation ID = new ResourceLocation(PixelReel.MOD_ID, "plex_config_data");

		public void writeToBuf(FriendlyByteBuf buf) {
			buf.writeUtf(this.url, 256);
			buf.writeBoolean(this.moviesEnabled);
			buf.writeBoolean(this.tvShowsEnabled);
			buf.writeBoolean(this.hasToken);
			buf.writeVarInt(this.selectedLibraryKeys.size());
			for (String key : this.selectedLibraryKeys) {
				buf.writeUtf(key, 128);
			}
			buf.writeVarInt(this.availableLibraries.size());
			for (JellyfinLibrary lib : this.availableLibraries) {
				lib.writeToBuf(buf);
			}
			this.status.writeToBuf(buf);
		}

		public static PlexConfigData readFromBuf(FriendlyByteBuf buf) {
			String url = buf.readUtf(256);
			boolean movies = buf.readBoolean();
			boolean shows = buf.readBoolean();
			boolean hasToken = buf.readBoolean();
			int count = buf.readVarInt();
			List<String> selected = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				selected.add(buf.readUtf(128));
			}
			int libCount = buf.readVarInt();
			List<JellyfinLibrary> libraries = new ArrayList<>(libCount);
			for (int i = 0; i < libCount; i++) {
				libraries.add(JellyfinLibrary.readFromBuf(buf));
			}
			JellyfinStatus status = JellyfinStatus.readFromBuf(buf);
			return new PlexConfigData(url, movies, shows, hasToken, selected, libraries, status);
		}
	}

	public record ZoneList(List<Zone> zones) {
		public static final ResourceLocation ID = new ResourceLocation(PixelReel.MOD_ID, "zone_list");

		public void writeToBuf(FriendlyByteBuf buf) {
			buf.writeVarInt(this.zones.size());
			for (Zone zone : this.zones) {
				buf.writeUtf(zone.name(), 64);
				buf.writeUtf(zone.dimension(), 128);
				buf.writeBlockPos(zone.min());
				buf.writeBlockPos(zone.max());
			}
		}

		public static ZoneList readFromBuf(FriendlyByteBuf buf) {
			int count = buf.readVarInt();
			List<Zone> zones = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				String name = buf.readUtf(64);
				String dimension = buf.readUtf(128);
				BlockPos min = buf.readBlockPos();
				BlockPos max = buf.readBlockPos();
				zones.add(new Zone(name, dimension, min, max));
			}
			return new ZoneList(zones);
		}
	}
}
