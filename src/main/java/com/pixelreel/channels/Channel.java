package com.pixelreel.channels;

import java.util.Locale;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

/** one live channel from the playlist and guide */
public record Channel(String id, int number, String name, String logoUrl, String guideIconUrl, String streamUrl) {
	public static final int MAX_TEXT = 128;
	public static final int MAX_URL = 1024;

	public static final StreamCodec<RegistryFriendlyByteBuf, Channel> STREAM_CODEC = StreamCodec.of(
		(buf, value) -> {
			buf.writeUtf(value.id, MAX_TEXT);
			buf.writeVarInt(value.number);
			buf.writeUtf(value.name, MAX_TEXT);
			buf.writeUtf(value.logoUrl, MAX_URL);
			buf.writeUtf(value.guideIconUrl, MAX_URL);
			buf.writeUtf(value.streamUrl, MAX_URL);
		},
		buf -> new Channel(
			buf.readUtf(MAX_TEXT), buf.readVarInt(), buf.readUtf(MAX_TEXT), buf.readUtf(MAX_URL), buf.readUtf(MAX_URL), buf.readUtf(MAX_URL)
		)
	);

	public Channel {
		id = truncate(id, MAX_TEXT);
		name = truncate(name, MAX_TEXT);
		logoUrl = truncate(logoUrl, MAX_URL);
		guideIconUrl = truncate(guideIconUrl, MAX_URL);
		streamUrl = truncate(streamUrl, MAX_URL);
	}

	public boolean isPlayable() {
		return !this.streamUrl.isEmpty();
	}

	public String normalizedName() {
		return normalize(this.name);
	}

	public boolean matches(String query) {
		if (query == null || query.isBlank()) {
			return true;
		}
		String q = query.trim().toLowerCase(Locale.ROOT);
		return this.name.toLowerCase(Locale.ROOT).contains(q) || Integer.toString(this.number).startsWith(q);
	}

	public static String normalize(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

	private static String truncate(@Nullable String value, int max) {
		if (value == null) {
			return "";
		}
		return value.length() <= max ? value : value.substring(0, max);
	}
}
