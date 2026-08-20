package com.pixelreel.jellyfin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pixelreel.PixelReel;
import com.pixelreel.config.PixelReelConfig;
import com.pixelreel.media.SubtitleTrack;
import com.pixelreel.ondemand.EmbyStyleConnection;
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

/** thin Jellyfin/Emby REST client. All methods must run off the server. */
public final class JellyfinClient {
	private static final String CLIENT_NAME = "pixelReel";
	private static final String DEVICE_NAME = "Minecraft";
	private static final String DEVICE_ID = "pixelreel-minecraft";
	private static final String VERSION = "1.0.0";

	private final HttpClient http;
	private final EmbyStyleConnection connection;
	private final int networkTimeoutSeconds;

	public JellyfinClient(HttpClient http, PixelReelConfig config) {
		this(http, config.jellyfinConnection(), config.networkTimeoutSeconds);
	}

	public JellyfinClient(HttpClient http, EmbyStyleConnection connection, int networkTimeoutSeconds) {
		this.http = http;
		this.connection = connection;
		this.networkTimeoutSeconds = Math.max(1, networkTimeoutSeconds);
	}

	public JsonObject getSystemInfo() throws Exception {
		try {
			return this.getJson("/System/Info");
		} catch (IOException primary) {
			try {
				return this.getJson("/System/Info/Public");
			} catch (IOException ignored) {
				throw primary;
			}
		}
	}

	public String resolveUserId() throws Exception {
		if (this.connection.userId() != null && !this.connection.userId().isBlank()) {
			return this.connection.userId().trim();
		}
		String fromList = this.resolveUserIdFromUserList();
		if (!fromList.isEmpty()) {
			return fromList;
		}
		try {
			JsonObject me = this.getJson("/Users/Me");
			String id = text(me, "Id");
			if (!id.isEmpty()) {
				return id;
			}
		} catch (IOException ignored) {
			// ya ya again i left it blank idk it breaks things ok will fix it
		}
		throw new IOException(
			"Could not determine " + this.connection.serverName()
				+ " user id. Open Dashboard → Users, copy a user id into the User ID field, then Save."
		);
	}

	private String resolveUserIdFromUserList() {
		try {
			JsonElement element = this.getJsonElement("/Users");
			JsonArray users;
			if (element.isJsonArray()) {
				users = element.getAsJsonArray();
			} else if (element.isJsonObject() && element.getAsJsonObject().has("Items")) {
				users = element.getAsJsonObject().getAsJsonArray("Items");
			} else {
				return "";
			}
			String firstEnabled = "";
			for (JsonElement entry : users) {
				if (!entry.isJsonObject()) {
					continue;
				}
				JsonObject user = entry.getAsJsonObject();
				String id = text(user, "Id");
				if (id.isEmpty()) {
					continue;
				}
				boolean disabled = user.has("Policy")
					&& user.get("Policy").isJsonObject()
					&& user.getAsJsonObject("Policy").has("IsDisabled")
					&& user.getAsJsonObject("Policy").get("IsDisabled").getAsBoolean();
				if (disabled) {
					continue;
				}
				boolean admin = user.has("Policy")
					&& user.get("Policy").isJsonObject()
					&& user.getAsJsonObject("Policy").has("IsAdministrator")
					&& user.getAsJsonObject("Policy").get("IsAdministrator").getAsBoolean();
				if (admin) {
					PixelReel.LOGGER.info("Using Jellyfin admin user {} for API key access", text(user, "Name"));
					return id;
				}
				if (firstEnabled.isEmpty()) {
					firstEnabled = id;
				}
			}
			return firstEnabled;
		} catch (Exception e) {
			PixelReel.LOGGER.debug("Could not list Jellyfin users: {}", describeError(e));
			return "";
		}
	}

	public List<JellyfinLibrary> listLibraries(String userId) throws Exception {
		JsonObject root = this.getJson("/Users/" + pathSeg(userId) + "/Views");
		JsonArray items = root.has("Items") && root.get("Items").isJsonArray() ? root.getAsJsonArray("Items") : new JsonArray();
		List<JellyfinLibrary> libraries = new ArrayList<>();
		for (JsonElement element : items) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject obj = element.getAsJsonObject();
			String id = text(obj, "Id");
			String name = text(obj, "Name");
			String collectionType = text(obj, "CollectionType");
			if (id.isEmpty() || name.isEmpty()) {
				continue;
			}
			if (collectionType.isEmpty()) {
				collectionType = text(obj, "Type");
			}
			libraries.add(new JellyfinLibrary(id, name, collectionType));
		}
		if (!libraries.isEmpty()) {
			return libraries;
		}
		return this.listVirtualFolders();
	}

	private List<JellyfinLibrary> listVirtualFolders() throws Exception {
		JsonElement element = this.getJsonElement("/Library/VirtualFolders");
		if (!element.isJsonArray()) {
			return List.of();
		}
		List<JellyfinLibrary> libraries = new ArrayList<>();
		for (JsonElement entry : element.getAsJsonArray()) {
			if (!entry.isJsonObject()) {
				continue;
			}
			JsonObject obj = entry.getAsJsonObject();
			String id = text(obj, "ItemId");
			if (id.isEmpty()) {
				id = text(obj, "Guid");
			}
			if (id.isEmpty()) {
				id = text(obj, "Id");
			}
			String name = text(obj, "Name");
			String collectionType = text(obj, "CollectionType");
			if (id.isEmpty() || name.isEmpty()) {
				continue;
			}
			libraries.add(new JellyfinLibrary(id, name, collectionType));
		}
		return libraries;
	}

	public List<JellyfinItemSummary> listItems(
		String userId,
		List<String> parentIds,
		String includeTypes,
		@Nullable String search,
		boolean recursive
	) throws Exception {
		List<JellyfinItemSummary> all = new ArrayList<>();
		if (parentIds.isEmpty()) {
			all.addAll(this.fetchItems(userId, null, includeTypes, search, recursive));
			return all;
		}
		for (String parentId : parentIds) {
			all.addAll(this.fetchItems(userId, parentId, includeTypes, search, recursive));
		}
		return all;
	}

	public List<JellyfinItemSummary> listChildren(String userId, String parentId, String includeTypes) throws Exception {
		return this.fetchItems(userId, parentId, includeTypes, null, false);
	}

	public @Nullable JellyfinItemSummary getItem(String userId, String itemId) throws Exception {
		JsonObject obj = this.getJson(
			"/Users/" + pathSeg(userId) + "/Items/" + pathSeg(itemId)
				+ "?Fields=Overview,Path,MediaSources,UserData,RecursiveItemCount,SeriesName,SeasonId,IndexNumber,ParentIndexNumber,RunTimeTicks,ProductionYear,ChildCount"
		);
		return this.toSummary(obj);
	}

	public PlaybackStart startPlayback(String userId, String itemId, long startPositionTicks) throws Exception {
		JsonObject body = new JsonObject();
		body.addProperty("UserId", userId);
		JsonObject dto = this.postJson("/Items/" + pathSeg(itemId) + "/PlaybackInfo?UserId=" + enc(userId), body);
		String playSessionId = text(dto, "PlaySessionId");
		if (playSessionId.isEmpty()) {
			playSessionId = UUID.randomUUID().toString();
		}
		String mediaSourceId = itemId;
		boolean supportsDirect = true;
		boolean hdr = false;
		List<SubtitleTrack> subtitles = List.of();
		if (dto.has("MediaSources") && dto.get("MediaSources").isJsonArray()) {
			JsonArray sources = dto.getAsJsonArray("MediaSources");
			if (!sources.isEmpty() && sources.get(0).isJsonObject()) {
				JsonObject source = sources.get(0).getAsJsonObject();
				mediaSourceId = text(source, "Id");
				if (mediaSourceId.isEmpty()) {
					mediaSourceId = itemId;
				}
				if (source.has("SupportsDirectStream")) {
					supportsDirect = source.get("SupportsDirectStream").getAsBoolean();
				}
				hdr = mediaSourceLooksHdr(source);
				subtitles = parseSubtitleTracks(source);
			}
		}
		if (subtitles.isEmpty()) {
			subtitles = this.fetchItemSubtitleTracks(userId, itemId);
		}
		String streamUrl = this.buildStreamUrl(itemId, mediaSourceId, playSessionId, supportsDirect, -1);
		return new PlaybackStart(streamUrl, playSessionId, mediaSourceId, startPositionTicks, hdr, subtitles);
	}

	public String buildStreamUrl(String itemId, String mediaSourceId, String playSessionId, int subtitleIndex) {
		return this.buildStreamUrl(itemId, mediaSourceId, playSessionId, subtitleIndex < 0, subtitleIndex);
	}

	public String buildSubtitleUrl(String itemId, String mediaSourceId, int subtitleIndex) {
		if (subtitleIndex < 0 || itemId == null || itemId.isBlank()) {
			return "";
		}
		String source = mediaSourceId == null || mediaSourceId.isBlank() ? itemId : mediaSourceId;
		return this.base()
			+ "/Videos/" + pathSeg(itemId)
			+ "/" + pathSeg(source)
			+ "/Subtitles/" + subtitleIndex
			+ "/Stream.vtt?api_key=" + enc(this.connection.apiKey());
	}

	private List<SubtitleTrack> fetchItemSubtitleTracks(String userId, String itemId) {
		try {
			JsonObject item = this.getJson(
				"/Users/" + pathSeg(userId) + "/Items/" + pathSeg(itemId) + "?Fields=MediaStreams,MediaSources"
			);
			List<SubtitleTrack> fromItem = parseSubtitleTracks(item);
			if (!fromItem.isEmpty()) {
				return fromItem;
			}
			if (item.has("MediaSources") && item.get("MediaSources").isJsonArray()) {
				List<SubtitleTrack> merged = new ArrayList<>();
				for (JsonElement element : item.getAsJsonArray("MediaSources")) {
					if (element.isJsonObject()) {
						mergeSubtitleTracks(merged, parseSubtitleTracks(element.getAsJsonObject()));
					}
				}
				return List.copyOf(merged);
			}
		} catch (Exception e) {
			PixelReel.LOGGER.debug("Could not load subtitle tracks for {}: {}", itemId, e.toString());
		}
		return List.of();
	}

	private static List<SubtitleTrack> parseSubtitleTracks(JsonObject source) {
		if (!source.has("MediaStreams") || !source.get("MediaStreams").isJsonArray()) {
			return List.of();
		}
		List<SubtitleTrack> tracks = new ArrayList<>();
		for (JsonElement element : source.getAsJsonArray("MediaStreams")) {
			if (!element.isJsonObject() || tracks.size() >= SubtitleTrack.MAX_TRACKS) {
				continue;
			}
			JsonObject stream = element.getAsJsonObject();
			if (!"Subtitle".equalsIgnoreCase(text(stream, "Type"))) {
				continue;
			}
			if (stream.has("IsExternalUrl") && !stream.get("IsExternalUrl").isJsonNull()
				&& stream.get("IsExternalUrl").getAsBoolean()) {
				continue;
			}
			int index = stream.has("Index") && !stream.get("Index").isJsonNull() ? stream.get("Index").getAsInt() : -1;
			if (index < 0) {
				continue;
			}
			String language = SubtitleTrack.languageCode(text(stream, "Language"));
			String format = SubtitleTrack.normalizeFormat(text(stream, "Codec"));
			if (format.isEmpty()) {
				// Infer only well-known codecs from display title — never treat "English" as a format.
				String display = text(stream, "DisplayTitle").toLowerCase(Locale.ROOT);
				if (display.contains("srt") || display.contains("subrip")) {
					format = "srt";
				} else if (display.contains("vtt") || display.contains("webvtt")) {
					format = "vtt";
				} else if (display.contains("ass") || display.contains("ssa")) {
					format = "ass";
				} else if (display.contains("vobsub") || display.contains("dvd")) {
					format = "vobsub";
				} else if (display.contains("pgs") || display.contains("pgssub")) {
					format = "pgs";
				}
			}
			String title = text(stream, "DisplayTitle");
			if (title.isEmpty()) {
				title = text(stream, "Title");
			}
			if (title.isEmpty()) {
				title = language.isEmpty() ? "Subtitle " + index : SubtitleTrack.languageDisplayName(language);
			}
			boolean external = stream.has("IsExternal") && !stream.get("IsExternal").isJsonNull()
				&& stream.get("IsExternal").getAsBoolean();
			if (external && !title.toLowerCase(Locale.ROOT).contains("external")) {
				title = title + " (External)";
			}
			boolean forced = stream.has("IsForced") && !stream.get("IsForced").isJsonNull()
				&& stream.get("IsForced").getAsBoolean();
			boolean isDefault = stream.has("IsDefault") && !stream.get("IsDefault").isJsonNull()
				&& stream.get("IsDefault").getAsBoolean();
			tracks.add(new SubtitleTrack(index, language, title, format, forced, isDefault));
		}
		return List.copyOf(tracks);
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

	private static boolean mediaSourceLooksHdr(JsonObject source) {
		if ("hdr".equalsIgnoreCase(text(source, "VideoRange"))
			|| text(source, "VideoRangeType").toLowerCase(Locale.ROOT).contains("hdr")
			|| text(source, "VideoRangeType").toLowerCase(Locale.ROOT).contains("dovi")
			|| text(source, "VideoRangeType").toLowerCase(Locale.ROOT).contains("dolby")
			|| text(source, "VideoRangeType").toLowerCase(Locale.ROOT).contains("hlg")) {
			return true;
		}
		if (!source.has("MediaStreams") || !source.get("MediaStreams").isJsonArray()) {
			return false;
		}
		for (JsonElement element : source.getAsJsonArray("MediaStreams")) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject stream = element.getAsJsonObject();
			if (!"Video".equalsIgnoreCase(text(stream, "Type"))) {
				continue;
			}
			String range = text(stream, "VideoRange");
			String rangeType = text(stream, "VideoRangeType");
			String combined = (range + " " + rangeType).toLowerCase(Locale.ROOT);
			if (combined.contains("hdr") || combined.contains("dovi") || combined.contains("dolby") || combined.contains("hlg")) {
				return true;
			}
			String codec = text(stream, "VideoCodecTag") + " " + text(stream, "Profile");
			if (codec.toLowerCase(Locale.ROOT).contains("dolby") || codec.toLowerCase(Locale.ROOT).contains("dvhe")) {
				return true;
			}
		}
		return false;
	}

	public void reportPlaying(String itemId, String mediaSourceId, String playSessionId, long positionTicks, boolean paused) {
		try {
			JsonObject body = playbackBody(itemId, mediaSourceId, playSessionId, positionTicks, paused);
			this.postEmpty("/Sessions/Playing", body);
		} catch (Exception e) {
			PixelReel.LOGGER.debug("Jellyfin playing report failed: {}", e.toString());
		}
	}

	public void reportProgress(String itemId, String mediaSourceId, String playSessionId, long positionTicks, boolean paused) {
		try {
			JsonObject body = playbackBody(itemId, mediaSourceId, playSessionId, positionTicks, paused);
			this.postEmpty("/Sessions/Playing/Progress", body);
		} catch (Exception e) {
			PixelReel.LOGGER.debug("Jellyfin progress report failed: {}", e.toString());
		}
	}

	public void reportStopped(String itemId, String mediaSourceId, String playSessionId, long positionTicks, boolean completed) {
		try {
			JsonObject body = playbackBody(itemId, mediaSourceId, playSessionId, positionTicks, false);
			if (completed) {
				body.addProperty("PlayedToCompletion", true);
			}
			this.postEmpty("/Sessions/Playing/Stopped", body);
		} catch (Exception e) {
			PixelReel.LOGGER.debug("Jellyfin stopped report failed: {}", e.toString());
		}
	}

	private static final int SCAN_PAGE_SIZE = 200;

	private List<JellyfinItemSummary> fetchItems(
		String userId,
		@Nullable String parentId,
		String includeTypes,
		@Nullable String search,
		boolean recursive
	) throws Exception {
		List<JellyfinItemSummary> result = new ArrayList<>();
		int startIndex = 0;
		int totalCount = -1;
		while (true) {
			StringBuilder path = new StringBuilder("/Users/")
				.append(pathSeg(userId))
				.append("/Items?IncludeItemTypes=")
				.append(enc(includeTypes))
				.append("&Recursive=")
				.append(recursive)
				.append("&SortBy=SortName&SortOrder=Ascending")
				.append("&Fields=Overview,UserData,RecursiveItemCount,SeriesName,SeasonId,IndexNumber,ParentIndexNumber,RunTimeTicks,ProductionYear,ChildCount")
				.append("&EnableImageTypes=Primary&ImageTypeLimit=1")
				.append("&EnableTotalRecordCount=true")
				.append("&StartIndex=").append(startIndex)
				.append("&Limit=").append(SCAN_PAGE_SIZE);
			if (parentId != null && !parentId.isBlank()) {
				path.append("&ParentId=").append(enc(parentId));
			}
			if (search != null && !search.isBlank()) {
				path.append("&SearchTerm=").append(enc(search.trim()));
			}
			JsonObject root = this.getJson(path.toString());
			if (totalCount < 0 && root.has("TotalRecordCount") && !root.get("TotalRecordCount").isJsonNull()) {
				try {
					totalCount = Math.max(0, root.get("TotalRecordCount").getAsInt());
				} catch (Exception ignored) {
					totalCount = -1;
				}
			}
			JsonArray items = root.has("Items") && root.get("Items").isJsonArray() ? root.getAsJsonArray("Items") : new JsonArray();
			if (items.isEmpty()) {
				break;
			}
			int before = result.size();
			for (JsonElement element : items) {
				if (element.isJsonObject()) {
					JellyfinItemSummary summary = this.toSummary(element.getAsJsonObject());
					if (summary != null) {
						result.add(summary);
					}
				}
			}
			startIndex += items.size();
			if (items.size() < SCAN_PAGE_SIZE) {
				break;
			}
			if (totalCount >= 0 && startIndex >= totalCount) {
				break;
			}
			if (result.size() == before || startIndex > 100_000) {
				break;
			}
		}
		return result;
	}

	private @Nullable JellyfinItemSummary toSummary(JsonObject obj) {
		String id = text(obj, "Id");
		String name = text(obj, "Name");
		if (id.isEmpty() || name.isEmpty()) {
			return null;
		}
		JellyfinItemKind kind = JellyfinItemKind.fromApi(text(obj, "Type"));
		String overview = text(obj, "Overview");
		int year = obj.has("ProductionYear") && !obj.get("ProductionYear").isJsonNull() ? obj.get("ProductionYear").getAsInt() : 0;
		long runtimeTicks = longVal(obj, "RunTimeTicks");
		int childCount = obj.has("ChildCount") && !obj.get("ChildCount").isJsonNull()
			? obj.get("ChildCount").getAsInt()
			: (obj.has("RecursiveItemCount") && !obj.get("RecursiveItemCount").isJsonNull() ? obj.get("RecursiveItemCount").getAsInt() : 0);
		int indexNumber = obj.has("IndexNumber") && !obj.get("IndexNumber").isJsonNull() ? obj.get("IndexNumber").getAsInt() : 0;
		int parentIndexNumber = obj.has("ParentIndexNumber") && !obj.get("ParentIndexNumber").isJsonNull()
			? obj.get("ParentIndexNumber").getAsInt()
			: 0;
		String seriesName = text(obj, "SeriesName");
		String seriesId = text(obj, "SeriesId");
		String seasonId = text(obj, "SeasonId");
		long positionTicks = 0L;
		boolean played = false;
		if (obj.has("UserData") && obj.get("UserData").isJsonObject()) {
			JsonObject userData = obj.getAsJsonObject("UserData");
			positionTicks = longVal(userData, "PlaybackPositionTicks");
			played = userData.has("Played") && userData.get("Played").getAsBoolean();
		}
		String imageUrl = this.imageUrl(id);
		return new JellyfinItemSummary(
			id,
			kind,
			name,
			overview,
			year,
			runtimeTicks,
			imageUrl,
			positionTicks,
			runtimeTicks,
			played,
			childCount,
			indexNumber,
			parentIndexNumber,
			seriesName,
			seriesId,
			seasonId
		);
	}

	public String imageUrl(String itemId) {
		if (itemId == null || itemId.isBlank() || this.connection.baseUrl().isBlank()) {
			return "";
		}
		return this.base() + "/Items/" + pathSeg(itemId) + "/Images/Primary?maxHeight=900&quality=96&api_key=" + enc(this.connection.apiKey());
	}

	private String buildStreamUrl(
		String itemId,
		String mediaSourceId,
		String playSessionId,
		boolean supportsDirect,
		int subtitleIndex
	) {
		boolean burnSubs = subtitleIndex >= 0;
		StringBuilder url = new StringBuilder(this.base())
			.append("/Videos/")
			.append(pathSeg(itemId))
			.append("/stream?static=")
			.append(!burnSubs && supportsDirect)
			.append("&mediaSourceId=")
			.append(enc(mediaSourceId))
			.append("&PlaySessionId=")
			.append(enc(playSessionId))
			.append("&api_key=")
			.append(enc(this.connection.apiKey()));
		if (burnSubs) {
			url.append("&SubtitleStreamIndex=").append(subtitleIndex)
				.append("&SubtitleMethod=Encode")
				.append("&VideoCodec=h264")
				.append("&AudioCodec=aac")
				.append("&TranscodingMaxAudioChannels=6");
		}
		return url.toString();
	}

	private static JsonObject playbackBody(String itemId, String mediaSourceId, String playSessionId, long positionTicks, boolean paused) {
		JsonObject body = new JsonObject();
		body.addProperty("ItemId", itemId);
		body.addProperty("MediaSourceId", mediaSourceId == null || mediaSourceId.isBlank() ? itemId : mediaSourceId);
		body.addProperty("PlaySessionId", playSessionId);
		body.addProperty("PositionTicks", Math.max(0L, positionTicks));
		body.addProperty("IsPaused", paused);
		body.addProperty("PlayMethod", "DirectStream");
		body.addProperty("CanSeek", true);
		return body;
	}

	private JsonObject getJson(String path) throws Exception {
		return parseObject(this.getJsonElement(path));
	}

	private JsonElement getJsonElement(String path) throws Exception {
		HttpResponse<String> response = this.send(this.request(path).GET().build(), path);
		String body = response.body();
		if (body == null || body.isBlank()) {
			return new JsonObject();
		}
		return JsonParser.parseString(body);
	}

	private JsonObject postJson(String path, JsonObject body) throws Exception {
		HttpResponse<String> response = this.send(
			this.request(path)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
				.build(),
			path
		);
		return parseObject(JsonParser.parseString(response.body() == null || response.body().isBlank() ? "{}" : response.body()));
	}

	private void postEmpty(String path, JsonObject body) throws Exception {
		this.send(
			this.request(path)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
				.build(),
			path
		);
	}

	private HttpRequest.Builder request(String path) {
		return HttpRequest.newBuilder(URI.create(this.base() + path))
			.timeout(Duration.ofSeconds(this.networkTimeoutSeconds))
			.header("User-Agent", CLIENT_NAME + "/" + VERSION)
			.header("Accept", "application/json")
			.header("X-Emby-Token", this.connection.apiKey())
			.header("Authorization", authorizationHeader(this.connection.apiKey()));
	}

	private HttpResponse<String> send(HttpRequest request, String path) throws Exception {
		HttpResponse<String> response = this.http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		int code = response.statusCode();
		if (code == 401 || code == 403) {
			throw new JellyfinAuthException("Authentication failed (HTTP " + code + ") — check the API key");
		}
		if (code == 404) {
			throw new IOException(
				"HTTP 404 at " + path + " — if User ID is blank, paste a " + this.connection.serverName() + " user id and Save again"
			);
		}
		if (code / 100 != 2) {
			throw new IOException("HTTP " + code + " at " + path);
		}
		return response;
	}

	private String base() {
		String url = this.connection.baseUrl() == null ? "" : this.connection.baseUrl();
		while (url.endsWith("/")) {
			url = url.substring(0, url.length() - 1);
		}
		return url;
	}

	private static String authorizationHeader(String token) {
		return "MediaBrowser Client=\"" + CLIENT_NAME + "\", Device=\"" + DEVICE_NAME + "\", DeviceId=\"" + DEVICE_ID
			+ "\", Version=\"" + VERSION + "\", Token=\"" + token + "\"";
	}

	private static JsonObject parseObject(JsonElement element) throws IOException {
		if (element == null || element.isJsonNull()) {
			return new JsonObject();
		}
		if (!element.isJsonObject()) {
			throw new IOException("Expected JSON object from Jellyfin");
		}
		return element.getAsJsonObject();
	}

	private static String text(JsonObject obj, String key) {
		if (!obj.has(key) || obj.get(key).isJsonNull()) {
			return "";
		}
		try {
			return obj.get(key).getAsString();
		} catch (Exception e) {
			return "";
		}
	}

	private static long longVal(JsonObject obj, String key) {
		if (!obj.has(key) || obj.get(key).isJsonNull()) {
			return 0L;
		}
		try {
			return obj.get(key).getAsLong();
		} catch (Exception e) {
			return 0L;
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
		if (current instanceof JellyfinAuthException) {
			return current.getMessage() == null ? "Authentication failed" : current.getMessage();
		}
		if (current instanceof java.net.ConnectException) {
			return "Connection refused - is the media server running?";
		}
		if (current instanceof java.net.UnknownHostException) {
			return "Unknown host - check the server URL";
		}
		if (current instanceof java.net.http.HttpConnectTimeoutException) {
			return "Connection timed out";
		}
		if (current instanceof java.net.http.HttpTimeoutException) {
			return "Media server did not respond in time";
		}
		if (current instanceof IllegalArgumentException) {
			return "Invalid server URL";
		}
		String message = current.getMessage();
		return message != null && !message.isBlank() ? message : current.getClass().getSimpleName();
	}

	public static boolean isUrlValid(String raw) {
		String normalized = PixelReelConfig.normalizeUrl(raw);
		if (normalized.isEmpty()) {
			return false;
		}
		try {
			URI uri = URI.create(normalized);
			String host = uri.getHost();
			return host != null && !host.isBlank();
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	public record PlaybackStart(
		String streamUrl,
		String playSessionId,
		String mediaSourceId,
		long startPositionTicks,
		boolean hdr,
		List<SubtitleTrack> subtitles
	) {
	}

	public static final class JellyfinAuthException extends IOException {
		public JellyfinAuthException(String message) {
			super(message);
		}
	}

	public static HttpClient createHttpClient(PixelReelConfig config, ExecutorService executor) {
		return HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(config.networkTimeoutSeconds))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.executor(executor)
			.build();
	}

	public static String hostOnly(String url) {
		try {
			URI uri = URI.create(url);
			String host = uri.getHost();
			return host == null ? "?" : host + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
		} catch (IllegalArgumentException e) {
			return "?";
		}
	}

	public static String sanitizeDetail(String detail) {
		if (detail == null) {
			return "";
		}
		String lower = detail.toLowerCase(Locale.ROOT);
		if (lower.contains("api_key") || lower.contains("token") || lower.contains("authorization")) {
			return "Request failed";
		}
		return detail.length() > 200 ? detail.substring(0, 200) : detail;
	}
}
