package com.pixelreel.client;

import com.pixelreel.channels.LiveStatus;
import com.pixelreel.jellyfin.JellyfinItemSummary;
import com.pixelreel.jellyfin.JellyfinStatus;
import com.pixelreel.networking.ModNetworkPayloads;
import com.pixelreel.ondemand.OnDemandProvider;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** Client cache of on-demand lists pages and permission flags. */
public final class ClientMediaCache {
	public static final ClientMediaCache INSTANCE = new ClientMediaCache();

	private Features features = Features.defaults();
	private OnDemandProvider browseProvider = OnDemandProvider.JELLYFIN;
	private ModNetworkPayloads.BrowseKind browseKind = ModNetworkPayloads.BrowseKind.MOVIES;
	private String browseSearch = "";
	private int browsePage;
	private int browseTotal;
	private List<JellyfinItemSummary> browseItems = List.of();
	private JellyfinStatus browseStatus = JellyfinStatus.notConfigured();
	private OnDemandProvider childrenProvider = OnDemandProvider.JELLYFIN;
	private ModNetworkPayloads.ChildrenKind childrenKind = ModNetworkPayloads.ChildrenKind.ITEM;
	private String childrenParentId = "";
	private List<JellyfinItemSummary> children = List.of();
	private ModNetworkPayloads.@Nullable JellyfinConfigData configData;
	private ModNetworkPayloads.@Nullable EmbyConfigData embyConfigData;
	private ModNetworkPayloads.@Nullable PlexConfigData plexConfigData;
	private ModNetworkPayloads.@Nullable TunarrConfigData tunarrConfigData;

	private ClientMediaCache() {
	}

	public Features features() {
		return this.features;
	}

	public void acceptFeatures(ModNetworkPayloads.MediaFeatures payload) {
		this.features = new Features(
			payload.canBrowse(),
			payload.canPlayTunarr(),
			payload.canPlayMovies(),
			payload.canPlayShows(),
			payload.canControlPlayback(),
			payload.canConfigureTunarr(),
			payload.canConfigureJellyfin(),
			payload.canConfigureEmby(),
			payload.canConfigurePlex(),
			payload.canRefreshLibrary(),
			payload.autoplayNextEpisode(),
			payload.jellyfinMovies(),
			payload.jellyfinShows(),
			payload.embyMovies(),
			payload.embyShows(),
			payload.plexMovies(),
			payload.plexShows(),
			payload.jellyfinStatus(),
			payload.embyStatus(),
			payload.plexStatus(),
			payload.tunarrStatus()
		);
	}

	public void beginBrowse() {
		this.browseItems = List.of();
	}

	public void acceptBrowse(ModNetworkPayloads.JellyfinBrowseResult payload) {
		this.browseProvider = payload.provider();
		this.browseKind = payload.kind();
		this.browseSearch = payload.search();
		this.browsePage = payload.page();
		this.browseTotal = payload.totalCount();
		this.browseItems = List.copyOf(payload.items());
		this.browseStatus = payload.status();
	}

	public void acceptChildren(ModNetworkPayloads.JellyfinChildrenResult payload) {
		this.childrenProvider = payload.provider();
		this.childrenKind = payload.kind();
		this.childrenParentId = payload.parentId();
		this.children = List.copyOf(payload.items());
	}

	public void acceptConfig(ModNetworkPayloads.JellyfinConfigData payload) {
		this.configData = payload;
	}

	public void acceptEmbyConfig(ModNetworkPayloads.EmbyConfigData payload) {
		this.embyConfigData = payload;
	}

	public void acceptPlexConfig(ModNetworkPayloads.PlexConfigData payload) {
		this.plexConfigData = payload;
	}

	public void acceptTunarrConfig(ModNetworkPayloads.TunarrConfigData payload) {
		this.tunarrConfigData = payload;
	}

	public OnDemandProvider browseProvider() {
		return this.browseProvider;
	}

	public ModNetworkPayloads.BrowseKind browseKind() {
		return this.browseKind;
	}

	public String browseSearch() {
		return this.browseSearch;
	}

	public int browsePage() {
		return this.browsePage;
	}

	public int browseTotal() {
		return this.browseTotal;
	}

	public List<JellyfinItemSummary> browseItems() {
		return this.browseItems;
	}

	public JellyfinStatus browseStatus() {
		return this.browseStatus;
	}

	public List<JellyfinItemSummary> children(OnDemandProvider provider, ModNetworkPayloads.ChildrenKind kind, String parentId) {
		if (this.childrenProvider == provider && this.childrenKind == kind && this.childrenParentId.equals(parentId)) {
			return this.children;
		}
		return List.of();
	}

	public ModNetworkPayloads.@Nullable JellyfinConfigData configData() {
		return this.configData;
	}

	public ModNetworkPayloads.@Nullable EmbyConfigData embyConfigData() {
		return this.embyConfigData;
	}

	public ModNetworkPayloads.@Nullable PlexConfigData plexConfigData() {
		return this.plexConfigData;
	}

	public ModNetworkPayloads.@Nullable TunarrConfigData tunarrConfigData() {
		return this.tunarrConfigData;
	}

	public void clear() {
		this.features = Features.defaults();
		this.browseItems = List.of();
		this.children = List.of();
		this.configData = null;
		this.embyConfigData = null;
		this.plexConfigData = null;
		this.tunarrConfigData = null;
		this.browseStatus = JellyfinStatus.notConfigured();
	}

	public record Features(
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
		public static Features defaults() {
			return new Features(
				true, true, false, false, true, false, false, false, false, false, true,
				false, false, false, false, false, false,
				JellyfinStatus.notConfigured(),
				JellyfinStatus.notConfigured(),
				JellyfinStatus.notConfigured(),
				LiveStatus.notConfigured()
			);
		}

		public List<OnDemandProvider> providersFor(ModNetworkPayloads.BrowseKind kind) {
			java.util.ArrayList<OnDemandProvider> list = new java.util.ArrayList<>();
			boolean movies = kind == ModNetworkPayloads.BrowseKind.MOVIES;
			if (movies ? this.jellyfinMovies : this.jellyfinShows) {
				list.add(OnDemandProvider.JELLYFIN);
			}
			if (movies ? this.embyMovies : this.embyShows) {
				list.add(OnDemandProvider.EMBY);
			}
			if (movies ? this.plexMovies : this.plexShows) {
				list.add(OnDemandProvider.PLEX);
			}
			return list;
		}
	}
}
