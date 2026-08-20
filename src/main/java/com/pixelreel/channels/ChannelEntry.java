package com.pixelreel.channels;

import net.minecraft.network.FriendlyByteBuf;

public record ChannelEntry(Channel channel, GuideInfo guide) {
	public void writeToBuf(FriendlyByteBuf buf) {
		this.channel.writeToBuf(buf);
		this.guide.writeToBuf(buf);
	}

	public static ChannelEntry readFromBuf(FriendlyByteBuf buf) {
		return new ChannelEntry(Channel.readFromBuf(buf), GuideInfo.readFromBuf(buf));
	}
}
