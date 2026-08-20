package com.pixelreel.media;

import net.minecraft.nbt.CompoundTag;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** subtitle track */
public record SubtitleTrack(
	int index,
	String language,
	String title,
	String format,
	boolean forced,
	boolean isDefault
) {
	public static final int MAX_TRACKS = 32;
	public static final int MAX_TEXT = 96;
	public static final SubtitleTrack OFF = new SubtitleTrack(-1, "", "None", "", false, false);

	private static final Map<String, String> LANGUAGE_NAMES = Map.ofEntries(
		Map.entry("en", "English"),
		Map.entry("eng", "English"),
		Map.entry("es", "Spanish"),
		Map.entry("spa", "Spanish"),
		Map.entry("fr", "French"),
		Map.entry("fre", "French"),
		Map.entry("fra", "French"),
		Map.entry("de", "German"),
		Map.entry("ger", "German"),
		Map.entry("deu", "German"),
		Map.entry("it", "Italian"),
		Map.entry("ita", "Italian"),
		Map.entry("pt", "Portuguese"),
		Map.entry("por", "Portuguese"),
		Map.entry("ja", "Japanese"),
		Map.entry("jpn", "Japanese"),
		Map.entry("ko", "Korean"),
		Map.entry("kor", "Korean"),
		Map.entry("zh", "Chinese"),
		Map.entry("chi", "Chinese"),
		Map.entry("zho", "Chinese"),
		Map.entry("ru", "Russian"),
		Map.entry("rus", "Russian"),
		Map.entry("ar", "Arabic"),
		Map.entry("ara", "Arabic"),
		Map.entry("hi", "Hindi"),
		Map.entry("hin", "Hindi"),
		Map.entry("nl", "Dutch"),
		Map.entry("dut", "Dutch"),
		Map.entry("nld", "Dutch"),
		Map.entry("sv", "Swedish"),
		Map.entry("swe", "Swedish"),
		Map.entry("no", "Norwegian"),
		Map.entry("nor", "Norwegian"),
		Map.entry("da", "Danish"),
		Map.entry("dan", "Danish"),
		Map.entry("fi", "Finnish"),
		Map.entry("fin", "Finnish"),
		Map.entry("pl", "Polish"),
		Map.entry("pol", "Polish"),
		Map.entry("tr", "Turkish"),
		Map.entry("tur", "Turkish"),
		Map.entry("und", "Unknown")
	);


	public String displayLabel() {
		if (this.index < 0) {
			return "None";
		}
		String fmt = normalizeFormat(this.format);
		String lang = languageDisplayName(this.language);
		String rawTitle = this.title == null ? "" : this.title.trim();

		if (!rawTitle.isEmpty() && (rawTitle.contains("(") || rawTitle.toLowerCase(Locale.ROOT).contains("srt")
			|| rawTitle.toLowerCase(Locale.ROOT).contains("vobsub")
			|| rawTitle.toLowerCase(Locale.ROOT).contains("pgs")
			|| rawTitle.toLowerCase(Locale.ROOT).contains("ass"))) {
			String label = rawTitle;
			if (this.forced && !label.toLowerCase(Locale.ROOT).contains("forced")) {
				label = label + " (Forced)";
			}
			return truncate(label);
		}

		StringBuilder label = new StringBuilder();
		if (!lang.isEmpty()) {
			label.append(lang);
		} else if (!rawTitle.isEmpty()) {
			label.append(rawTitle);
		} else {
			label.append("Track ").append(this.index);
		}
		if (!fmt.isEmpty()) {
			label.append(" (").append(fmt.toUpperCase(Locale.ROOT)).append(')');
		} else if (!rawTitle.isEmpty() && !rawTitle.equalsIgnoreCase(lang)) {
			label.append(" — ").append(rawTitle);
		}
		if (this.forced) {
			label.append(" (Forced)");
		}
		return truncate(label.toString());
	}

	public String detailLabel() {
		if (this.index < 0) {
			return "Turn subtitles off";
		}
		String fmt = normalizeFormat(this.format);
		StringBuilder detail = new StringBuilder();
		if (!fmt.isEmpty()) {
			detail.append(fmt.toUpperCase(Locale.ROOT));
		}
		if (this.supportsTextOverlay()) {
			if (!detail.isEmpty()) {
				detail.append(" · ");
			}
			detail.append("Text overlay");
		} else if (!fmt.isEmpty()) {
			detail.append(" · Burned into video");
		}
		if (this.isDefault) {
			if (!detail.isEmpty()) {
				detail.append(" · ");
			}
			detail.append("Default");
		}
		return detail.toString();
	}

	public boolean supportsTextOverlay() {
		String fmt = normalizeFormat(this.format);
		if (fmt.isEmpty()) {
			return true;
		}
		return switch (fmt) {
			case "srt", "vtt", "webvtt", "ass", "ssa", "subrip", "mov_text", "text", "utf-8", "utf8" -> true;
			default -> false;
		};
	}

	public String toStorage() {
		return this.index + "\t"
			+ sanitize(this.language) + "\t"
			+ sanitize(this.title) + "\t"
			+ (this.forced ? "1" : "0") + "\t"
			+ (this.isDefault ? "1" : "0") + "\t"
			+ sanitize(this.format);
	}

	public static SubtitleTrack fromStorage(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String[] parts = raw.split("\t", -1);
		if (parts.length < 5) {
			return null;
		}
		try {
			return new SubtitleTrack(
				Integer.parseInt(parts[0].trim()),
				parts[1],
				parts[2],
				parts.length > 5 ? parts[5] : "",
				"1".equals(parts[3]),
				"1".equals(parts[4])
			);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public static void writeList(CompoundTag output, String key, List<SubtitleTrack> tracks) {
		StringBuilder sb = new StringBuilder();
		int count = 0;
		for (SubtitleTrack track : tracks) {
			if (track == null || track.index < 0 || count >= MAX_TRACKS) {
				continue;
			}
			if (!sb.isEmpty()) {
				sb.append('\n');
			}
			sb.append(track.toStorage());
			count++;
		}
		output.putString(key, sb.toString());
	}

	public static List<SubtitleTrack> readList(CompoundTag input, String key) {
		String raw = input.contains(key) ? input.getString(key) : "";
		if (raw.isBlank()) {
			return List.of();
		}
		List<SubtitleTrack> tracks = new ArrayList<>();
		for (String line : raw.split("\\n")) {
			if (tracks.size() >= MAX_TRACKS) {
				break;
			}
			SubtitleTrack track = fromStorage(line);
			if (track != null && track.index >= 0) {
				tracks.add(track);
			}
		}
		return List.copyOf(tracks);
	}

	public static String languageCode(String raw) {
		if (raw == null) {
			return "";
		}
		String value = raw.trim();
		if (value.length() > 16) {
			value = value.substring(0, 16);
		}
		return value.toLowerCase(Locale.ROOT);
	}

	public static String normalizeFormat(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		String value = raw.trim().toLowerCase(Locale.ROOT);
		if (value.startsWith(".")) {
			value = value.substring(1);
		}
		return switch (value) {
			case "subrip" -> "srt";
			case "webvtt" -> "vtt";
			case "dvd", "dvd_subtitle", "dvdsub" -> "vobsub";
			case "hdmv_pgs_subtitle", "pgssub", "hdmv" -> "pgs";
			default -> value.length() > 16 ? value.substring(0, 16) : value;
		};
	}

	public static String languageDisplayName(String code) {
		if (code == null || code.isBlank()) {
			return "";
		}
		String key = code.trim().toLowerCase(Locale.ROOT);
		String mapped = LANGUAGE_NAMES.get(key);
		if (mapped != null) {
			return mapped;
		}
		if (key.length() > 2 && LANGUAGE_NAMES.containsKey(key.substring(0, 2))) {
			return LANGUAGE_NAMES.get(key.substring(0, 2));
		}
		return code.trim();
	}

	private static String sanitize(String value) {
		return clamp(value).replace('\t', ' ').replace('\n', ' ');
	}

	private static String clamp(String value) {
		if (value == null) {
			return "";
		}
		return value.length() <= MAX_TEXT ? value : value.substring(0, MAX_TEXT);
	}

	private static String truncate(String label) {
		return label.length() > 48 ? label.substring(0, 48) : label;
	}
}
