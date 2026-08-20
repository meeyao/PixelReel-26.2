package com.pixelreel.jellyfin;

import net.minecraft.network.FriendlyByteBuf;

/** jellyfin folder */
public record JellyfinLibrary(String id, String name, String collectionType) {
	public static final int MAX_TEXT = 128;

	public void writeToBuf(FriendlyByteBuf buf) {
		buf.writeUtf(this.id, MAX_TEXT);
		buf.writeUtf(this.name, MAX_TEXT);
		buf.writeUtf(this.collectionType, MAX_TEXT);
	}

	public static JellyfinLibrary readFromBuf(FriendlyByteBuf buf) {
		return new JellyfinLibrary(
			buf.readUtf(MAX_TEXT),
			buf.readUtf(MAX_TEXT),
			buf.readUtf(MAX_TEXT)
		);
	}

	public boolean isMovies() {
		return "movies".equalsIgnoreCase(this.collectionType);
	}

	public boolean isTvShows() {
		return "tvshows".equalsIgnoreCase(this.collectionType);
	}
}
