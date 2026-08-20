package com.pixelreel.networking;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Small per-player fixed-window rate limiter so a misbehaving client cannot flood the server. */
public final class ServerRateLimit {
	private static final int MAX_PLAYERS = 256;

	private final int windowMillis;
	private final int maxRequests;
	private final ConcurrentHashMap<UUID, long[]> windows = new ConcurrentHashMap<>();

	public ServerRateLimit(int windowMillis, int maxRequests) {
		this.windowMillis = windowMillis;
		this.maxRequests = maxRequests;
	}

	public boolean allow(UUID player) {
		long now = System.currentTimeMillis();
		long[] result = this.windows.compute(player, (ignored, slot) -> {
			if (slot == null || now - slot[0] >= this.windowMillis) {
				return new long[] { now, 1L };
			}
			slot[1]++;
			return slot;
		});
		if (this.windows.size() > MAX_PLAYERS) {
			this.windows.entrySet().removeIf(entry -> !entry.getKey().equals(player));
		}
		return result[1] <= this.maxRequests;
	}
}
