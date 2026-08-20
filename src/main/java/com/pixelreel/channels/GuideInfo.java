package com.pixelreel.channels;

import net.minecraft.network.FriendlyByteBuf;

/** current and next selection on a channel */
public record GuideInfo(
	String nowTitle, String nowEpisode, long nowStart, long nowEnd, String nowIconUrl, String nextTitle, long nextStart, long nextEnd
) {
	public static final GuideInfo EMPTY = new GuideInfo("", "", 0L, 0L, "", "", 0L, 0L);

	public void writeToBuf(FriendlyByteBuf buf) {
		buf.writeUtf(this.nowTitle, Channel.MAX_TEXT);
		buf.writeUtf(this.nowEpisode, Channel.MAX_TEXT);
		buf.writeLong(this.nowStart);
		buf.writeLong(this.nowEnd);
		buf.writeUtf(this.nowIconUrl, Channel.MAX_URL);
		buf.writeUtf(this.nextTitle, Channel.MAX_TEXT);
		buf.writeLong(this.nextStart);
		buf.writeLong(this.nextEnd);
	}

	public static GuideInfo readFromBuf(FriendlyByteBuf buf) {
		return new GuideInfo(
			buf.readUtf(Channel.MAX_TEXT),
			buf.readUtf(Channel.MAX_TEXT),
			buf.readLong(),
			buf.readLong(),
			buf.readUtf(Channel.MAX_URL),
			buf.readUtf(Channel.MAX_TEXT),
			buf.readLong(),
			buf.readLong()
		);
	}

	public boolean hasNow() {
		return !this.nowTitle.isEmpty();
	}

	public boolean hasNext() {
		return !this.nextTitle.isEmpty();
	}

	public float progressAt(long epochSeconds) {
		if (!this.hasNow() || this.nowEnd <= this.nowStart) {
			return -1.0F;
		}
		return Math.clamp((float)(epochSeconds - this.nowStart) / (this.nowEnd - this.nowStart), 0.0F, 1.0F);
	}
}
