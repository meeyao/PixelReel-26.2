package com.pixelreel.channels;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record ChannelEntry(Channel channel, GuideInfo guide) {
	public static final StreamCodec<RegistryFriendlyByteBuf, ChannelEntry> STREAM_CODEC = StreamCodec.composite(
		Channel.STREAM_CODEC, ChannelEntry::channel, GuideInfo.STREAM_CODEC, ChannelEntry::guide, ChannelEntry::new
	);
}
