package com.pixelreel.client.playback.subtitle;

import com.pixelreel.PixelReel;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.Nullable;

/** subtitle loader */
public final class SubtitleOverlay {
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	private final AtomicReference<List<SubtitleParser.Cue>> cues = new AtomicReference<>(List.of());
	private volatile String loadedUrl = "";
	private volatile @Nullable String activeText = "";

	public void setSourceUrl(@Nullable String url) {
		String next = url == null ? "" : url.trim();
		if (next.equals(this.loadedUrl)) {
			return;
		}
		this.loadedUrl = next;
		this.cues.set(List.of());
		this.activeText = "";
		if (next.isEmpty()) {
			return;
		}
		String fetchUrl = next;
		CompletableFuture.runAsync(() -> this.load(fetchUrl));
	}

	public void clear() {
		this.loadedUrl = "";
		this.cues.set(List.of());
		this.activeText = "";
	}

	public void tick(long mediaTimeMs) {
		List<SubtitleParser.Cue> list = this.cues.get();
		if (list.isEmpty()) {
			this.activeText = "";
			return;
		}
		String found = "";
		for (SubtitleParser.Cue cue : list) {
			if (mediaTimeMs >= cue.startMs() && mediaTimeMs < cue.endMs()) {
				found = cue.text();
				break;
			}
		}
		this.activeText = found;
	}

	public @Nullable String activeText() {
		String text = this.activeText;
		return text == null || text.isBlank() ? null : text;
	}

	private void load(String url) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(20))
				.header("Accept", "text/vtt, text/plain, */*")
				.GET()
				.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() / 100 != 2) {
				PixelReel.LOGGER.warn("Subtitle download failed HTTP {} for {}", response.statusCode(), sanitize(url));
				return;
			}
			if (!url.equals(this.loadedUrl)) {
				return;
			}
			List<SubtitleParser.Cue> parsed = SubtitleParser.parse(response.body());
			this.cues.set(parsed);
			PixelReel.LOGGER.info("Loaded {} subtitle cue(s) from {}", parsed.size(), sanitize(url));
		} catch (Exception e) {
			PixelReel.LOGGER.warn("Subtitle download failed for {}: {}", sanitize(url), e.toString());
		}
	}

	private static String sanitize(String url) {
		int q = url.indexOf('?');
		return q >= 0 ? url.substring(0, q) : url;
	}
}
