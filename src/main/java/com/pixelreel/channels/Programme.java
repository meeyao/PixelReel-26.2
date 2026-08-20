package com.pixelreel.channels;

import org.jetbrains.annotations.Nullable;

/** one XMLTV handler */
public record Programme(String title, String episode, long startEpochSeconds, long endEpochSeconds, String iconUrl) {
	public Programme {
		title = orEmpty(title);
		episode = orEmpty(episode);
		iconUrl = orEmpty(iconUrl);
	}

	public boolean isOnAirAt(long epochSeconds) {
		return epochSeconds >= this.startEpochSeconds && epochSeconds < this.endEpochSeconds;
	}

	private static String orEmpty(@Nullable String value) {
		return value == null ? "" : value;
	}
}
