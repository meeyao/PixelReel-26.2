package com.pixelreel.networking;

import com.pixelreel.PixelReel;
import com.pixelreel.config.ConfigManager;
import com.pixelreel.config.PixelReelConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.core.BlockPos;

/**
 * Server-side relay for on-demand media streams and subtitles. Players' VLC fetches from this
 * proxy so provider api keys never leave the server; each stream is behind an opaque, revocable
 * token that is stable for the lifetime of a given stream URL (so VLC seeking and reconnect reuse
 * the same URL and the same client player).
 */
public final class MediaProxy {
	public static final MediaProxy INSTANCE = new MediaProxy();

	private static final Duration TICKET_TTL = Duration.ofHours(24);
	private static final int MAX_TICKETS = 4096;
	private static final Duration UPSTREAM_HEADER_TIMEOUT = Duration.ofSeconds(30);
	private static final String[] RELAY_HEADERS = {
		"Content-Type", "Content-Length", "Content-Range", "Accept-Ranges",
		"Content-Disposition", "Cache-Control", "ETag", "Last-Modified"
	};

	private final ConcurrentHashMap<String, Ticket> tickets = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, String> tokensByUrl = new ConcurrentHashMap<>();

	private final HttpClient upstream = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(15))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	private volatile HttpServer server;
	private volatile ExecutorService executor;

	private MediaProxy() {
	}

	public synchronized void start() {
		if (this.server != null) {
			return;
		}
		PixelReelConfig config = ConfigManager.get();
		int port = config.proxyPort;
		try {
			HttpServer http = HttpServer.create(new InetSocketAddress(port), 0);
			this.executor = Executors.newFixedThreadPool(24, runnable -> {
				Thread thread = new Thread(runnable, "pixelreel-proxy");
				thread.setDaemon(true);
				return thread;
			});
			http.setExecutor(this.executor);
			http.createContext("/stream/", this::handle);
			http.createContext("/subtitle/", this::handle);
			http.start();
			this.server = http;
			PixelReel.LOGGER.info("pixelReel media proxy listening on port {}", port);
		} catch (IOException e) {
			PixelReel.LOGGER.error("pixelReel media proxy could not bind port {}; playback will fall back to direct URLs", port, e);
		}
	}

	public synchronized void stop() {
		if (this.server != null) {
			this.server.stop(0);
			this.server = null;
		}
		if (this.executor != null) {
			this.executor.shutdownNow();
			this.executor = null;
		}
		this.tickets.clear();
		this.tokensByUrl.clear();
	}

	public boolean isActive() {
		return this.server != null;
	}

	public static String displayKey(String dimension, BlockPos pos) {
		return dimension + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	/** Returns an opaque token path suffix for the given stream URL, reusing the token while the URL is unchanged. */
	public synchronized String issue(String displayKey, String url) {
		if (url == null || url.isBlank()) {
			return "";
		}
		String urlKey = displayKey + "|" + url;
		String existing = this.tokensByUrl.get(urlKey);
		if (existing != null) {
			Ticket ticket = this.tickets.get(existing);
			if (ticket != null && !ticket.expired()) {
				return existing;
			}
		}
		this.sweep();
		String token = UUID.randomUUID().toString().replace("-", "");
		this.tokensByUrl.put(urlKey, token);
		this.tickets.put(token, new Ticket(token, displayKey, url, System.currentTimeMillis() + TICKET_TTL.toMillis()));
		return token;
	}

	public void revoke(String displayKey) {
		if (this.tickets.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<String, Ticket>> it = this.tickets.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, Ticket> entry = it.next();
			if (entry.getValue().displayKey().equals(displayKey)) {
				it.remove();
				this.tokensByUrl.remove(displayKey + "|" + entry.getValue().url());
			}
		}
	}

	private void sweep() {
		long now = System.currentTimeMillis();
		Iterator<Map.Entry<String, Ticket>> it = this.tickets.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, Ticket> entry = it.next();
			if (entry.getValue().expiresAtMillis() < now) {
				it.remove();
				this.tokensByUrl.remove(entry.getValue().displayKey() + "|" + entry.getValue().url());
			}
		}
		if (this.tickets.size() >= MAX_TICKETS) {
			String oldestToken = null;
			long oldest = Long.MAX_VALUE;
			for (Map.Entry<String, Ticket> entry : this.tickets.entrySet()) {
				if (entry.getValue().expiresAtMillis() < oldest) {
					oldest = entry.getValue().expiresAtMillis();
					oldestToken = entry.getKey();
				}
			}
			if (oldestToken != null) {
				Ticket removed = this.tickets.remove(oldestToken);
				if (removed != null) {
					this.tokensByUrl.remove(removed.displayKey() + "|" + removed.url());
				}
			}
		}
	}

	private void handle(HttpExchange exchange) {
		try {
			String method = exchange.getRequestMethod();
			boolean head = "HEAD".equals(method);
			if (!head && !"GET".equals(method)) {
				exchange.sendResponseHeaders(405, -1);
				return;
			}
			String path = exchange.getRequestURI().getPath();
			String token = path.substring(path.lastIndexOf('/') + 1);
			if (token.isEmpty()) {
				exchange.sendResponseHeaders(404, -1);
				return;
			}
			Ticket ticket = this.tickets.get(token);
			if (ticket == null || ticket.expired()) {
				if (ticket != null) {
					this.tickets.remove(token);
					this.tokensByUrl.remove(ticket.displayKey() + "|" + ticket.url());
				}
				exchange.sendResponseHeaders(404, -1);
				return;
			}
			relay(exchange, ticket, head);
		} catch (Exception e) {
			PixelReel.LOGGER.debug("Media proxy request failed", e);
		} finally {
			exchange.close();
		}
	}

	private void relay(HttpExchange exchange, Ticket ticket, boolean head) {
		URI target;
		try {
			target = URI.create(ticket.url());
		} catch (IllegalArgumentException e) {
			respond(exchange, 500, null);
			return;
		}
		HttpRequest.Builder request = HttpRequest.newBuilder(target).timeout(UPSTREAM_HEADER_TIMEOUT);
		if (head) {
			request.header("Range", "bytes=0-0");
		} else {
			String range = exchange.getRequestHeaders().getFirst("Range");
			if (range != null && !range.isBlank()) {
				request.header("Range", range);
			}
		}
		HttpResponse<InputStream> response;
		try {
			response = this.upstream.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
		} catch (IOException e) {
			respond(exchange, 502, null);
			return;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			respond(exchange, 502, null);
			return;
		}
		int status = response.statusCode();
		try {
			long length = -1L;
			for (String name : RELAY_HEADERS) {
				String value = response.headers().firstValue(name).orElse(null);
				if (value == null) {
					continue;
				}
				if (name.equals("Content-Length")) {
					try {
						length = Long.parseLong(value);
					} catch (NumberFormatException ignored) {
						length = -1L;
					}
					continue;
				}
				exchange.getResponseHeaders().add(name, value);
			}
			if (head) {
				exchange.sendResponseHeaders(status, -1);
				return;
			}
			exchange.sendResponseHeaders(status, length >= 0L ? length : -1L);
			try (InputStream body = response.body()) {
				body.transferTo(exchange.getResponseBody());
			}
		} catch (IOException e) {
			PixelReel.LOGGER.debug("Media proxy relay aborted for a client", e);
		}
	}

	private static void respond(HttpExchange exchange, int status, byte[] body) {
		try {
			if (body == null) {
				exchange.sendResponseHeaders(status, -1);
			} else {
				exchange.sendResponseHeaders(status, body.length);
				exchange.getResponseBody().write(body);
			}
		} catch (IOException ignored) {
		}
	}

	private record Ticket(String token, String displayKey, String url, long expiresAtMillis) {
		boolean expired() {
			return System.currentTimeMillis() > this.expiresAtMillis;
		}
	}
}
