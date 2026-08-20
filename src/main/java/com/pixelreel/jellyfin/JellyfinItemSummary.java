package com.pixelreel.jellyfin;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** jellyfin content summary */
public record JellyfinItemSummary(
	String id,
	JellyfinItemKind kind,
	String title,
	String overview,
	int productionYear,
	long runtimeTicks,
	String imageUrl,
	long playbackPositionTicks,
	long runTimeTicks,
	boolean played,
	int childCount,
	int indexNumber,
	int parentIndexNumber,
	String seriesName,
	String seriesId,
	String seasonId
) {
	public static final int MAX_TITLE = 256;
	public static final int MAX_OVERVIEW = 2048;
	public static final int MAX_URL = 2048;
	public static final int MAX_ID = 128;

	public static final StreamCodec<RegistryFriendlyByteBuf, JellyfinItemSummary> STREAM_CODEC = StreamCodec.of(
		(buf, value) -> {
			buf.writeUtf(value.id, MAX_ID);
			buf.writeVarInt(value.kind.ordinal());
			buf.writeUtf(value.title, MAX_TITLE);
			buf.writeUtf(value.overview, MAX_OVERVIEW);
			buf.writeVarInt(value.productionYear);
			buf.writeLong(value.runtimeTicks);
			buf.writeUtf(value.imageUrl, MAX_URL);
			buf.writeLong(value.playbackPositionTicks);
			buf.writeLong(value.runTimeTicks);
			buf.writeBoolean(value.played);
			buf.writeVarInt(value.childCount);
			buf.writeVarInt(value.indexNumber);
			buf.writeVarInt(value.parentIndexNumber);
			buf.writeUtf(value.seriesName, MAX_TITLE);
			buf.writeUtf(value.seriesId, MAX_ID);
			buf.writeUtf(value.seasonId, MAX_ID);
		},
		buf -> new JellyfinItemSummary(
			buf.readUtf(MAX_ID),
			kindByOrdinal(buf.readVarInt()),
			buf.readUtf(MAX_TITLE),
			buf.readUtf(MAX_OVERVIEW),
			buf.readVarInt(),
			buf.readLong(),
			buf.readUtf(MAX_URL),
			buf.readLong(),
			buf.readLong(),
			buf.readBoolean(),
			buf.readVarInt(),
			buf.readVarInt(),
			buf.readVarInt(),
			buf.readUtf(MAX_TITLE),
			buf.readUtf(MAX_ID),
			buf.readUtf(MAX_ID)
		)
	);

	public JellyfinItemSummary forBrowsePacket() {
		if (this.overview == null || this.overview.isEmpty()) {
			return this;
		}
		return new JellyfinItemSummary(
			this.id,
			this.kind,
			this.title,
			"",
			this.productionYear,
			this.runtimeTicks,
			this.imageUrl,
			this.playbackPositionTicks,
			this.runTimeTicks,
			this.played,
			this.childCount,
			this.indexNumber,
			this.parentIndexNumber,
			this.seriesName,
			this.seriesId,
			this.seasonId
		);
	}

	public boolean hasResume() {
		return this.playbackPositionTicks > 0L && !this.played;
	}

	public long resumePositionMs() {
		return Math.max(0L, this.playbackPositionTicks / 10_000L);
	}

	public long runtimeMs() {
		long ticks = this.runTimeTicks > 0L ? this.runTimeTicks : this.runtimeTicks;
		return Math.max(0L, ticks / 10_000L);
	}

	public int resolvedSeasonNumber() {
		if (this.indexNumber > 0) {
			return this.indexNumber;
		}
		return parseLeadingSeasonNumber(this.title);
	}

	public String seasonLabel() {
		if (this.indexNumber == 0 && isSpecialsTitle(this.title)) {
			return "Specials";
		}
		int season = this.resolvedSeasonNumber();
		if (season > 0) {
			return "Season " + season;
		}
		if (isSpecialsTitle(this.title)) {
			return "Specials";
		}
		return this.title.isBlank() ? "Season" : this.title.trim();
	}

	public String episodeLabel(int seasonNumber) {
		int season = seasonNumber > 0 ? seasonNumber : this.parentIndexNumber;
		int episode = this.indexNumber;
		String name = this.title == null ? "" : this.title.trim();
		if (season > 0 && episode > 0) {
			return String.format(Locale.ROOT, "S%02dE%02d  %s", season, episode, name).trim();
		}
		if (episode > 0) {
			return ("E" + episode + "  " + name).trim();
		}
		return name;
	}

	private static final Pattern SEASON_NUMBER = Pattern.compile(
		"(?i)(?:^|\\b)(?:season|s)\\s*0*([0-9]{1,3})\\b|^\\s*0*([0-9]{1,3})\\s*$"
	);

	private static int parseLeadingSeasonNumber(String title) {
		if (title == null || title.isBlank()) {
			return 0;
		}
		Matcher matcher = SEASON_NUMBER.matcher(title.trim());
		if (!matcher.find()) {
			return 0;
		}
		String digits = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
		try {
			return Math.max(0, Integer.parseInt(digits));
		} catch (NumberFormatException ignored) {
			return 0;
		}
	}

	private static boolean isSpecialsTitle(String title) {
		if (title == null || title.isBlank()) {
			return false;
		}
		String lower = title.trim().toLowerCase(Locale.ROOT);
		return lower.equals("specials") || lower.equals("special") || lower.contains("specials");
	}

	private static JellyfinItemKind kindByOrdinal(int ordinal) {
		JellyfinItemKind[] values = JellyfinItemKind.values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : JellyfinItemKind.UNKNOWN;
	}
}
