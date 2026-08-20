package com.pixelreel.channels;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

/** parses an extended M3U playlist into channels */
public final class M3uParser {
	private static final Pattern ATTRIBUTE = Pattern.compile("([A-Za-z0-9_-]+)=\"([^\"]*)\"");

	private M3uParser() {
	}

	public static List<Channel> parse(String playlist) {
		List<Channel> channels = new ArrayList<>();
		if (playlist == null || playlist.isBlank()) {
			return channels;
		}

		String pendingExtinf = null;
		int fallbackNumber = 0;
		for (String rawLine : playlist.split("\r?\n")) {
			String line = rawLine.strip();
			if (line.isEmpty()) {
				continue;
			}
			if (line.startsWith("#EXTINF")) {
				pendingExtinf = line;
			} else if (!line.startsWith("#")) {
				if (pendingExtinf != null) {
					fallbackNumber++;
					Channel channel = fromExtinf(pendingExtinf, line, fallbackNumber);
					if (channel != null) {
						channels.add(channel);
					}
					pendingExtinf = null;
				}
			}
		}
		return channels;
	}

	private static @Nullable Channel fromExtinf(String extinf, String url, int fallbackNumber) {
		if (url.isEmpty()) {
			return null;
		}

		String attributesAndTitle = extinf.substring(extinf.indexOf(':') + 1);
		String title = "";
		int comma = lastTopLevelComma(attributesAndTitle);
		if (comma >= 0) {
			title = attributesAndTitle.substring(comma + 1).strip();
		}

		Matcher matcher = ATTRIBUTE.matcher(attributesAndTitle);
		String tvgId = "";
		String tvgName = "";
		String logo = "";
		String channelId = "";
		int number = -1;
		while (matcher.find()) {
			String key = matcher.group(1).toLowerCase(java.util.Locale.ROOT);
			String value = matcher.group(2).strip();
			switch (key) {
				case "tvg-id" -> tvgId = value;
				case "tvg-name" -> tvgName = value;
				case "tvg-logo" -> logo = firstNonEmpty(logo, value);
				case "logo", "icon", "tvg-logo-small" -> logo = firstNonEmpty(logo, value);
				case "channel-id", "cuid" -> channelId = firstNonEmpty(channelId, value);
				case "tvg-chno", "channel-number", "tvg-num" -> number = parseNumber(value, number);
				default -> {
				}
			}
		}

		String id = firstNonEmpty(tvgId, channelId);
		String name = firstNonEmpty(title, tvgName);
		if (number < 0) {
			number = fallbackNumber;
		}
		if (id.isEmpty()) {
			id = "num:" + number;
		}
		if (name.isEmpty()) {
			name = "Channel " + number;
		}
		return new Channel(id, number, name, logo, "", url);
	}

	private static int lastTopLevelComma(String value) {
		boolean inQuotes = false;
		int last = -1;
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c == '"') {
				inQuotes = !inQuotes;
			} else if (c == ',' && !inQuotes) {
				last = i;
			}
		}
		return last;
	}

	private static int parseNumber(String value, int fallback) {
		try {
			return Integer.parseInt(value.split("\\.")[0].strip());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static String firstNonEmpty(String a, String b) {
		return a != null && !a.isEmpty() ? a : (b == null ? "" : b);
	}
}
