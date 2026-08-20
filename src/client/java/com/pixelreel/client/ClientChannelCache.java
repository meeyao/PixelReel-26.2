package com.pixelreel.client;

import com.pixelreel.channels.ChannelEntry;
import com.pixelreel.channels.LiveStatus;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** new channel list */
public final class ClientChannelCache {
	public static final ClientChannelCache INSTANCE = new ClientChannelCache();

	private volatile List<ChannelEntry> channels = List.of();
	private volatile LiveStatus status = LiveStatus.notConfigured();
	private volatile long receivedAt;

	private ClientChannelCache() {
	}

	public List<ChannelEntry> channels() {
		return this.channels;
	}

	public LiveStatus status() {
		return this.status;
	}

	public boolean hasFreshData() {
		return this.receivedAt != 0L && System.currentTimeMillis() - this.receivedAt < 60_000L;
	}

	public void accept(List<ChannelEntry> channels, LiveStatus status) {
		this.channels = List.copyOf(channels);
		this.status = status;
		this.receivedAt = System.currentTimeMillis();
	}

	public @Nullable ChannelEntry byNumber(int number) {
		for (ChannelEntry entry : this.channels) {
			if (entry.channel().number() == number) {
				return entry;
			}
		}
		return null;
	}

	public void clear() {
		this.channels = List.of();
		this.status = LiveStatus.notConfigured();
		this.receivedAt = 0L;
	}
}
