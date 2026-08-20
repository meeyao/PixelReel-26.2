package com.pixelreel.plex;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pixelreel.config.PixelReelConfig;
import com.pixelreel.jellyfin.JellyfinItemKind;
import com.pixelreel.jellyfin.JellyfinItemSummary;
import com.pixelreel.jellyfin.JellyfinLibrary;
import com.pixelreel.media.SubtitleTrack;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import org.jetbrains.annotations.Nullable;

/** blocking Plex Media Server REST client */
public final class PlexClient {
	private static final String CLIENT_ID = "pixelreel-minecraft";
	private static final String PRODUCT = "pixelReel";
	private static final String VERSION = "1.0.0";

	private final HttpClient http;
	private final String baseUrl;
	private final String token;
	private final int timeoutSeconds;

	public PlexClient(HttpClient http, PixelReelConfig config) {
		this.http = http;
		this.baseUrl = stripSlash(config.plexUrl);
		this.token = config.plexToken == null ? "" : config.plexToken.trim();
		this.timeoutSeconds = Math.max(1, config.networkTimeoutSeconds);
	}

	public void ping() throws Exception {
		this.getJson("/identity");
	}

	public List<JellyfinLibrary> listLibraries() throws Exception {
		JsonObject root = this.getJson("/library/sections");
		JsonArray directories = mediaContainerChildren(root, "Directory");
		List<JellyfinLibrary> libraries = new ArrayList<>();
		for (JsonElement element : directories) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject dir = element.getAsJsonObject();
			String key = text(dir, "key");
			String title = text(dir, "title");
			String type = text(dir, "type");
			if (key.isEmpty() || title.isEmpty()) {
				continue;
			}
			String collectionType = switch (type.toLowerCase(Locale.ROOT)) {
				case "movie" -> "movies";
				case "show" -> "tvshows";
				default -> type;
			};
			libraries.add(new JellyfinLibrary(key, title, collectionType));
		}
		return libraries;
	}

	private static final int SCAN_PAGE_SIZE = 200;

	public List<JellyfinItemSummary> listSectionItems(String sectionKey, String type) throws Exception {
		String typeCode = "movie".equalsIgnoreCase(type) ? "1" : "2";
		List<JellyfinItemSummary> all = new ArrayList<>();
		int start = 0;
		int totalSize = -1;
		while (true) {
			String path = "/library/sections/" + pathSeg(sectionKey) + "/all?type=" + typeCode
				+ "&X-Plex-Container-Start=" + start
				+ "&X-Plex-Container-Size=" + SCAN_PAGE_SIZE;
			JsonObject root = this.getJson(path);
			JsonObject container = root;
			if (root.has("MediaContainer") && root.get("MediaContainer").isJsonObject()) {
				container = root.getAsJsonObject("MediaContainer");
			}
			if (totalSize < 0) {
				totalSize = intField(container, "totalSize", intField(container, "TotalSize", -1));
			}
			List<JellyfinItemSummary> page = this.parseMetadataList(root);
			if (page.isEmpty()) {
				break;
			}
			all.addAll(page);
			start += page.size();
			if (page.size() < SCAN_PAGE_SIZE) {
				break;
			}
			if (totalSize >= 0 && start >= totalSize) {
				break;
			}
			if (start > 100_000) {
				break;
			}
		}
		return all;
	}

	public List<JellyfinItemSummary> listChildren(String ratingKey) throws Exception {
		List<JellyfinItemSummary> all = new ArrayList<>();
		int start = 0;
		int totalSize = -1;
		while (true) {
			String path = "/library/metadata/" + pathSeg(ratingKey) + "/children"
				+ "?X-Plex-Container-Start=" + start
				+ "&X-Plex-Container-Size=" + SCAN_PAGE_SIZE;
			JsonObject root = this.getJson(path);
			JsonObject container = root;
			if (root.has("MediaContainer") && root.get("MediaContainer").isJsonObject()) {
				container = root.getAsJsonObject("MediaContainer");
			}
			if (totalSize < 0) {
				totalSize = intField(container, "totalSize", intField(container, "TotalSize", -1));
			}
			List<JellyfinItemSummary> page = this.parseMetadataList(root);
			if (page.isEmpty()) {
				break;
			}
			all.addAll(page);
			start += page.size();
			if (page.size() < SCAN_PAGE_SIZE) {
				break;
			}
			if (totalSize >= 0 && start >= totalSize) {
				break;
			}
			if (start > 100_000) {
				break;
			}
		}
		return all;
	}

	public @Nullable JellyfinItemSummary getItem(String ratingKey) throws Exception {
		JsonObject root = this.getJson("/library/metadata/" + pathSeg(ratingKey));
		List<JellyfinItemSummary> items = this.parseMetadataList(root);
		return items.isEmpty() ? null : items.getFirst();
	}

	public PlaybackStart startPlayback(String ratingKey, long startPositionMs) throws Exception {
		JsonObject root = this.getJson("/library/metadata/" + pathSeg(ratingKey));
		JsonArray meta = mediaContainerChildren(root, "Metadata");
		if (meta.isEmpty() || !meta.get(0).isJsonObject()) {
			throw new IOException("Plex item has no metadata");
		}
		JsonObject item = meta.get(0).getAsJsonObject();
		PartSelection part = findBestPart(item);
		if (part.key().isEmpty()) {
			throw new IOException("Plex item has no playable media part");
		}
		String playSessionId = UUID.randomUUID().toString();
		String streamUrl = this.buildStreamUrl(
			ratingKey, part.mediaIndex(), part.partIndex(), part.key(), playSessionId, -1, Math.max(0L, startPositionMs)
		);
		return new PlaybackStart(
			streamUrl,
			playSessionId,
			ratingKey,
			Math.max(0L, startPositionMs) * 10_000L,
			part.hdr(),
			part.subtitles(),
			part.mediaIndex(),
			part.partIndex(),
			part.key()
		);
	}

	public String buildSubtitleUrl(int subtitleStreamId) {
		if (subtitleStreamId < 0) {
			return "";
		}
		return this.baseUrl + "/library/streams/" + subtitleStreamId + "?X-Plex-Token=" + enc(this.token);
	}

	public String buildStreamUrl(
		String ratingKey,
		int mediaIndex,
		int partIndex,
		String partKey,
		String playSessionId,
		int subtitleStreamId,
		long startPositionMs
	) {
		if (subtitleStreamId < 0) {
			String streamPath = partKey.startsWith("http") ? partKey : (this.baseUrl + partKey);
			String separator = streamPath.contains("?") ? "&" : "?";
			return streamPath + separator + "X-Plex-Token=" + enc(this.token);
		}
		long offsetSeconds = Math.max(0L, startPositionMs) / 1000L;
		return this.baseUrl + "/video/:/transcode/universal/start.mp4"
			+ "?path=" + enc("/library/metadata/" + ratingKey)
			+ "&mediaIndex=" + Math.max(0, mediaIndex)
			+ "&partIndex=" + Math.max(0, partIndex)
			+ "&protocol=http"
			+ "&fastSeek=1"
			+ "&directPlay=0"
			+ "&directStream=0"
			+ "&subtitleSize=100"
			+ "&subtitles=burn"
			+ "&subtitleStreamID=" + subtitleStreamId
			+ "&session=" + enc(playSessionId == null || playSessionId.isBlank() ? UUID.randomUUID().toString() : playSessionId)
			+ "&offset=" + offsetSeconds
			+ "&CopyTimestamps=1"
			+ "&X-Plex-Platform=Chrome"
			+ "&X-Plex-Client-Identifier=" + enc(CLIENT_ID)
			+ "&X-Plex-Product=" + enc(PRODUCT)
			+ "&X-Plex-Device=Minecraft"
			+ "&X-Plex-Token=" + enc(this.token);
	}

	public void reportTimeline(String ratingKey, String state, long positionMs, long durationMs) {
		try {
			long time = Math.max(0L, positionMs);
			long duration = Math.max(0L, durationMs);
			String path = "/:/timeline?ratingKey=" + enc(ratingKey)
				+ "&key=" + enc("/library/metadata/" + ratingKey)
				+ "&state=" + enc(state)
				+ "&time=" + time
				+ "&duration=" + duration
				+ "&X-Plex-Client-Identifier=" + enc(CLIENT_ID);
			this.send(this.request(path).GET().build(), path);
		} catch (Exception ignored) {
		}
	}

	public String imageUrl(String thumbPath) {
		if (thumbPath == null || thumbPath.isBlank() || this.baseUrl.isBlank()) {
			return "";
		}
		String path = thumbPath.startsWith("http") ? thumbPath : (this.baseUrl + thumbPath);
		String separator = path.contains("?") ? "&" : "?";
		return path + separator + "X-Plex-Token=" + enc(this.token) + "&width=600&height=900&minSize=1";
	}

	private List<JellyfinItemSummary> parseMetadataList(JsonObject root) {
		JsonArray meta = mediaContainerChildren(root, "Metadata");
		List<JellyfinItemSummary> items = new ArrayList<>();
		for (JsonElement element : meta) {
			if (!element.isJsonObject()) {
				continue;
			}
			JellyfinItemSummary summary = this.toSummary(element.getAsJsonObject());
			if (summary != null) {
				items.add(summary);
			}
		}
		return items;
	}

	private @Nullable JellyfinItemSummary toSummary(JsonObject obj) {
		String id = text(obj, "ratingKey");
		String title = text(obj, "title");
		if (id.isEmpty() || title.isEmpty()) {
			return null;
		}
		String type = text(obj, "type");
		JellyfinItemKind kind = switch (type.toLowerCase(Locale.ROOT)) {
			case "movie" -> JellyfinItemKind.MOVIE;
			case "show" -> JellyfinItemKind.SERIES;
			case "season" -> JellyfinItemKind.SEASON;
			case "episode" -> JellyfinItemKind.EPISODE;
			default -> JellyfinItemKind.UNKNOWN;
		};
		String overview = text(obj, "summary");
		int year = obj.has("year") && !obj.get("year").isJsonNull() ? obj.get("year").getAsInt() : 0;
		long durationMs = obj.has("duration") && !obj.get("duration").isJsonNull() ? obj.get("duration").getAsLong() : 0L;
		long runtimeTicks = durationMs * 10_000L;
		int index = obj.has("index") && !obj.get("index").isJsonNull() ? obj.get("index").getAsInt() : 0;
		int parentIndex = obj.has("parentIndex") && !obj.get("parentIndex").isJsonNull() ? obj.get("parentIndex").getAsInt() : 0;
		String seriesName = text(obj, "grandparentTitle");
		if (seriesName.isEmpty()) {
			seriesName = text(obj, "parentTitle");
		}
		String seriesId = text(obj, "grandparentRatingKey");
		String seasonId = text(obj, "parentRatingKey");
		if (kind == JellyfinItemKind.SEASON && seriesId.isEmpty()) {
			seriesId = text(obj, "parentRatingKey");
		}
		long viewOffset = obj.has("viewOffset") && !obj.get("viewOffset").isJsonNull() ? obj.get("viewOffset").getAsLong() : 0L;
		boolean played = obj.has("viewCount") && !obj.get("viewCount").isJsonNull() && obj.get("viewCount").getAsInt() > 0 && viewOffset <= 0L;
		int childCount = obj.has("leafCount") && !obj.get("leafCount").isJsonNull() ? obj.get("leafCount").getAsInt() : 0;
		String thumb = text(obj, "thumb");
		if (thumb.isEmpty()) {
			thumb = text(obj, "parentThumb");
		}
		if (thumb.isEmpty()) {
			thumb = text(obj, "grandparentThumb");
		}
		return new JellyfinItemSummary(
			id,
			kind,
			title,
			overview,
			year,
			runtimeTicks,
			this.imageUrl(thumb),
			viewOffset * 10_000L,
			runtimeTicks,
			played,
			childCount,
			index,
			parentIndex,
			seriesName,
			seriesId,
			seasonId
		);
	}

	private static PartSelection findBestPart(JsonObject metadata) {
		if (!metadata.has("Media") || !metadata.get("Media").isJsonArray()) {
			return new PartSelection("", false, 0, 0, List.of());
		}
		PartSelection first = new PartSelection("", false, 0, 0, List.of());
		PartSelection sdr = new PartSelection("", false, 0, 0, List.of());
		List<SubtitleTrack> allSubs = new ArrayList<>();
		JsonArray mediaArray = metadata.getAsJsonArray("Media");
		for (int mediaIndex = 0; mediaIndex < mediaArray.size(); mediaIndex++) {
			JsonElement mediaEl = mediaArray.get(mediaIndex);
			if (!mediaEl.isJsonObject()) {
				continue;
			}
			JsonObject media = mediaEl.getAsJsonObject();
			boolean hdr = mediaLooksHdr(media);
			mergeSubtitleTracks(allSubs, parseSubtitleTracks(media));
			if (!media.has("Part") || !media.get("Part").isJsonArray()) {
				continue;
			}
			JsonArray parts = media.getAsJsonArray("Part");
			for (int partIndex = 0; partIndex < parts.size(); partIndex++) {
				JsonElement partEl = parts.get(partIndex);
				if (!partEl.isJsonObject()) {
					continue;
				}
				JsonObject part = partEl.getAsJsonObject();
				String key = text(part, "key");
				if (key.isEmpty()) {
					continue;
				}
				List<SubtitleTrack> partSubs = parseSubtitleTracks(part);
				mergeSubtitleTracks(allSubs, partSubs);
				PartSelection candidate = new PartSelection(key, hdr, mediaIndex, partIndex, partSubs);
				if (first.key().isEmpty()) {
					first = candidate;
				}
				if (!hdr && sdr.key().isEmpty()) {
					sdr = candidate;
				}
			}
		}
		PartSelection chosen = sdr.key().isEmpty() ? first : sdr;
		List<SubtitleTrack> subs = chosen.subtitles().isEmpty() ? List.copyOf(allSubs) : mergeCopy(chosen.subtitles(), allSubs);
		return new PartSelection(chosen.key(), chosen.hdr(), chosen.mediaIndex(), chosen.partIndex(), subs);
	}

	private static List<SubtitleTrack> parseSubtitleTracks(JsonObject container) {
		JsonArray streams = null;
		if (container.has("Stream") && container.get("Stream").isJsonArray()) {
			streams = container.getAsJsonArray("Stream");
		} else if (container.has("stream") && container.get("stream").isJsonArray()) {
			streams = container.getAsJsonArray("stream");
		}
		if (streams == null) {
			return List.of();
		}
		List<SubtitleTrack> tracks = new ArrayList<>();
		for (JsonElement element : streams) {
			if (!element.isJsonObject() || tracks.size() >= SubtitleTrack.MAX_TRACKS) {
				continue;
			}
			JsonObject stream = element.getAsJsonObject();
			int streamType = intField(stream, "streamType", intField(stream, "StreamType", 0));
			if (streamType != 3) {
				continue;
			}
			int id = intField(stream, "id", -1);
			if (id < 0) {
				id = intField(stream, "index", -1);
			}
			if (id < 0) {
				continue;
			}
			String language = SubtitleTrack.languageCode(text(stream, "languageCode"));
			if (language.isEmpty()) {
				language = SubtitleTrack.languageCode(text(stream, "language"));
			}
			String format = SubtitleTrack.normalizeFormat(text(stream, "format"));
			if (format.isEmpty()) {
				format = SubtitleTrack.normalizeFormat(text(stream, "codec"));
			}
			String title = text(stream, "displayTitle");
			if (title.isEmpty()) {
				title = text(stream, "extendedDisplayTitle");
			}
			if (title.isEmpty()) {
				title = text(stream, "title");
			}
			if (title.isEmpty()) {
				title = language.isEmpty() ? "Subtitle " + id : SubtitleTrack.languageDisplayName(language);
			}
			boolean forced = boolField(stream, "forced");
			boolean isDefault = boolField(stream, "selected") || boolField(stream, "default");
			tracks.add(new SubtitleTrack(id, language, title, format, forced, isDefault));
		}
		return List.copyOf(tracks);
	}

	private static List<SubtitleTrack> mergeCopy(List<SubtitleTrack> primary, List<SubtitleTrack> extra) {
		List<SubtitleTrack> merged = new ArrayList<>(primary);
		mergeSubtitleTracks(merged, extra);
		return List.copyOf(merged);
	}

	private static void mergeSubtitleTracks(List<SubtitleTrack> into, List<SubtitleTrack> extra) {
		for (SubtitleTrack track : extra) {
			if (into.size() >= SubtitleTrack.MAX_TRACKS) {
				return;
			}
			boolean exists = false;
			for (SubtitleTrack existing : into) {
				if (existing.index() == track.index()) {
					exists = true;
					break;
				}
			}
			if (!exists) {
				into.add(track);
			}
		}
	}

	private static int intField(JsonObject obj, String key, int fallback) {
		if (!obj.has(key) || obj.get(key).isJsonNull()) {
			return fallback;
		}
		try {
			return obj.get(key).getAsInt();
		} catch (Exception e) {
			try {
				return Integer.parseInt(obj.get(key).getAsString().trim());
			} catch (Exception ignored) {
				return fallback;
			}
		}
	}

	private static boolean boolField(JsonObject obj, String key) {
		if (!obj.has(key) || obj.get(key).isJsonNull()) {
			return false;
		}
		try {
			return obj.get(key).getAsBoolean();
		} catch (Exception e) {
			String raw = text(obj, key).toLowerCase(Locale.ROOT);
			return raw.equals("1") || raw.equals("true");
		}
	}

	private static boolean mediaLooksHdr(JsonObject media) {
		String range = text(media, "videoRange");
		if (range.isEmpty()) {
			range = text(media, "VideoRange");
		}
		String lower = range.toLowerCase(Locale.ROOT);
		if (lower.contains("hdr") || lower.contains("dovi") || lower.contains("dolby") || lower.contains("hlg")) {
			return true;
		}
		String title = text(media, "title").toLowerCase(Locale.ROOT);
		return title.contains("hdr") || title.contains("dolby vision") || title.contains("dv ");
	}

	private record PartSelection(String key, boolean hdr, int mediaIndex, int partIndex, List<SubtitleTrack> subtitles) {
	}

	private static JsonArray mediaContainerChildren(JsonObject root, String key) {
		JsonObject container = root;
		if (root.has("MediaContainer") && root.get("MediaContainer").isJsonObject()) {
			container = root.getAsJsonObject("MediaContainer");
		}
		if (container.has(key) && container.get(key).isJsonArray()) {
			return container.getAsJsonArray(key);
		}
		return new JsonArray();
	}

	private JsonObject getJson(String path) throws Exception {
		HttpResponse<String> response = this.send(this.request(path).GET().build(), path);
		String body = response.body();
		if (body == null || body.isBlank()) {
			return new JsonObject();
		}
		JsonElement element = JsonParser.parseString(body);
		if (!element.isJsonObject()) {
			throw new IOException("Expected JSON object from Plex");
		}
		return element.getAsJsonObject();
	}

	private HttpRequest.Builder request(String path) {
		String url = path.startsWith("http") ? path : (this.baseUrl + path);
		return HttpRequest.newBuilder(URI.create(url))
			.timeout(Duration.ofSeconds(this.timeoutSeconds))
			.header("Accept", "application/json")
			.header("X-Plex-Token", this.token)
			.header("X-Plex-Client-Identifier", CLIENT_ID)
			.header("X-Plex-Product", PRODUCT)
			.header("X-Plex-Version", VERSION)
			.header("X-Plex-Device", "Minecraft")
			.header("X-Plex-Platform", "Java");
	}

	private HttpResponse<String> send(HttpRequest request, String path) throws Exception {
		HttpResponse<String> response = this.http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		int code = response.statusCode();
		if (code == 401 || code == 403) {
			throw new PlexAuthException("Authentication failed (HTTP " + code + ") — check the Plex token");
		}
		if (code / 100 != 2) {
			throw new IOException("HTTP " + code + " at " + path);
		}
		return response;
	}

	private static String stripSlash(String url) {
		String value = url == null ? "" : url.trim();
		while (value.endsWith("/")) {
			value = value.substring(0, value.length() - 1);
		}
		return value;
	}

	private static String text(JsonObject obj, String key) {
		if (!obj.has(key) || obj.get(key).isJsonNull()) {
			return "";
		}
		try {
			return obj.get(key).getAsString();
		} catch (Exception e) {
			return String.valueOf(obj.get(key));
		}
	}

	private static String enc(String value) {
		return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
	}

	private static String pathSeg(String value) {
		if (value == null) {
			return "";
		}
		return value.trim().replace("/", "").replace("?", "").replace("#", "");
	}

	public static String describeError(Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null
			&& (current instanceof java.util.concurrent.CompletionException || current instanceof java.util.concurrent.ExecutionException)) {
			current = current.getCause();
		}
		if (current instanceof PlexAuthException) {
			return current.getMessage() == null ? "Authentication failed" : current.getMessage();
		}
		if (current instanceof java.net.ConnectException) {
			return "Connection refused - is Plex running?";
		}
		if (current instanceof java.net.UnknownHostException) {
			return "Unknown host - check the Plex URL";
		}
		if (current instanceof java.net.http.HttpTimeoutException || current instanceof java.net.http.HttpConnectTimeoutException) {
			return "Plex did not respond in time";
		}
		String message = current.getMessage();
		return message != null && !message.isBlank() ? message : current.getClass().getSimpleName();
	}

	public static String sanitizeDetail(String detail) {
		if (detail == null) {
			return "";
		}
		String lower = detail.toLowerCase(Locale.ROOT);
		if (lower.contains("token") || lower.contains("x-plex")) {
			return "Request failed";
		}
		return detail.length() > 200 ? detail.substring(0, 200) : detail;
	}

	public static HttpClient createHttpClient(PixelReelConfig config, ExecutorService executor) {
		return HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(config.networkTimeoutSeconds))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.executor(executor)
			.build();
	}

	public record PlaybackStart(
		String streamUrl,
		String playSessionId,
		String mediaSourceId,
		long startPositionTicks,
		boolean hdr,
		List<SubtitleTrack> subtitles,
		int mediaIndex,
		int partIndex,
		String partKey
	) {
	}

	public static final class PlexAuthException extends IOException {
		public PlexAuthException(String message) {
			super(message);
		}
	}
}
