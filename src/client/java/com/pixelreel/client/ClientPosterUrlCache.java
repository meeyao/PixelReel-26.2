package com.pixelreel.client;

import com.pixelreel.client.playback.PlaybackManager;
import com.pixelreel.networking.ModNetworkPayloads;
import com.pixelreel.ondemand.OnDemandProvider;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Resolves on-demand poster URLs one at a time so provider api keys never sit in list packets. */
public final class ClientPosterUrlCache {
	public static final ClientPosterUrlCache INSTANCE = new ClientPosterUrlCache();
	private static final int MAX_CACHED_URLS = 5_000;

	private final Map<String, String> urls = new HashMap<>();
	private final Set<String> pending = new HashSet<>();

	private ClientPosterUrlCache() {
	}

	public String url(OnDemandProvider provider, String itemId) {
		if (itemId == null || itemId.isBlank()) {
			return "";
		}
		String key = key(provider, itemId);
		String cached = this.urls.get(key);
		if (cached != null) {
			return cached;
		}
		if (this.urls.size() >= MAX_CACHED_URLS) {
			this.urls.clear();
		}
		if (this.pending.add(key)) {
			ClientNetworking.requestPoster(provider, itemId);
		}
		return "";
	}

	public void accept(ModNetworkPayloads.PosterUrl payload) {
		String key = key(payload.provider(), payload.itemId());
		String url = payload.url() == null ? "" : payload.url();
		if (url.startsWith("/")) {
			String host = payload.proxyHost();
			if (host == null || host.isBlank()) {
				host = PlaybackManager.serverProxyHost();
			}
			if (host == null || host.isBlank() || payload.proxyPort() <= 0) {
				url = "";
			} else {
				url = "http://" + host + ":" + payload.proxyPort() + url;
			}
		}
		this.urls.put(key, url);
		this.pending.remove(key);
	}

	public void clear() {
		this.urls.clear();
		this.pending.clear();
	}

	private static String key(OnDemandProvider provider, String itemId) {
		return provider.name() + ":" + itemId;
	}
}
