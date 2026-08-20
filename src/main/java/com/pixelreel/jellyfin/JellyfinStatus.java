package com.pixelreel.jellyfin;

import net.minecraft.network.FriendlyByteBuf;

/** connection / library status */
public record JellyfinStatus(
	boolean configured,
	boolean reachable,
	boolean authenticated,
	int movieCount,
	int seriesCount,
	String detail
) {
	public void writeToBuf(FriendlyByteBuf buf) {
		buf.writeBoolean(this.configured);
		buf.writeBoolean(this.reachable);
		buf.writeBoolean(this.authenticated);
		buf.writeVarInt(this.movieCount);
		buf.writeVarInt(this.seriesCount);
		buf.writeUtf(this.detail, 256);
	}

	public static JellyfinStatus readFromBuf(FriendlyByteBuf buf) {
		return new JellyfinStatus(
			buf.readBoolean(),
			buf.readBoolean(),
			buf.readBoolean(),
			buf.readVarInt(),
			buf.readVarInt(),
			buf.readUtf(256)
		);
	}

	public static JellyfinStatus notConfigured() {
		return new JellyfinStatus(false, false, false, 0, 0, "Jellyfin is not configured");
	}

	public static JellyfinStatus offline(String detail) {
		return new JellyfinStatus(true, false, false, 0, 0, detail == null ? "Unavailable" : detail);
	}

	public static JellyfinStatus authFailed(String detail) {
		return new JellyfinStatus(true, true, false, 0, 0, detail == null ? "Authentication failed" : detail);
	}

	public static JellyfinStatus online(int movies, int series, String detail) {
		return new JellyfinStatus(true, true, true, movies, series, detail == null ? "" : detail);
	}
}
