package com.pixelreel.channels;

import net.minecraft.network.FriendlyByteBuf;

/** need this for channel health */
public record LiveStatus(boolean configured, boolean reachable, int channelCount, String detail) {
	public static final int MAX_DETAIL_LENGTH = 256;

	public void writeToBuf(FriendlyByteBuf buf) {
		buf.writeBoolean(this.configured);
		buf.writeBoolean(this.reachable);
		buf.writeVarInt(this.channelCount);
		buf.writeUtf(this.detail, MAX_DETAIL_LENGTH);
	}

	public static LiveStatus readFromBuf(FriendlyByteBuf buf) {
		return new LiveStatus(
			buf.readBoolean(),
			buf.readBoolean(),
			buf.readVarInt(),
			buf.readUtf(MAX_DETAIL_LENGTH)
		);
	}

	public LiveStatus {
		if (detail == null) {
			detail = "";
		}
		if (detail.length() > MAX_DETAIL_LENGTH) {
			detail = detail.substring(0, MAX_DETAIL_LENGTH);
		}
		if (channelCount < -1) {
			channelCount = -1;
		}
	}

	public static LiveStatus notConfigured() {
		return new LiveStatus(false, false, -1, "No channel playlist configured");
	}

	public static LiveStatus offline(String reason) {
		return new LiveStatus(true, false, -1, reason);
	}

	public static LiveStatus online(int channelCount) {
		return new LiveStatus(true, true, channelCount, channelCount + " channel(s) available");
	}
}
