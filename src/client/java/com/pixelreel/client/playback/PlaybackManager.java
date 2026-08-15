package com.pixelreel.client.playback;

import com.pixelreel.PixelReel;
import com.pixelreel.blockentities.DisplayBlockEntity;
import com.pixelreel.client.ClientNetworking;
import com.pixelreel.client.playback.video.VideoTexture;
import com.pixelreel.config.ConfigManager;
import com.pixelreel.config.PixelReelConfig;
import com.pixelreel.items.PixelGlassesItem;
import com.pixelreel.networking.ScreenAction;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** the channels are managed and played */
public final class PlaybackManager {
	public static final PlaybackManager INSTANCE = new PlaybackManager();
	private static final int RESCAN_INTERVAL_TICKS = 20;
	private static final double KEEP_ALIVE_MULTIPLIER = 1.75;

	private final Map<String, ChannelPlayer> players = new HashMap<>();
	private final Map<String, Integer> playerEpochs = new HashMap<>();
	private final Map<BlockPos, PlaybackTicket> playbackTickets = new HashMap<>();
	private final Set<String> pendingTicketRequests = new HashSet<>();
	private final Set<BlockPos> nearbyControllers = new HashSet<>();
	private final Set<String> endedReports = new HashSet<>();
	private final Set<String> durationReports = new HashSet<>();
	private int tickCounter;

	private PlaybackManager() {
	}

	public @Nullable ChannelPlayer player(String url) {
		return url == null || url.isEmpty() ? null : this.players.get(url);
	}

	public @Nullable ChannelPlayer player(DisplayBlockEntity display) {
		String url = this.playbackUrl(display);
		return url.isEmpty() ? null : this.players.get(url);
	}

	public int activePlayerCount() {
		return this.players.size();
	}

	public void prewarm(List<String> urls) {
	}

	public void retry(BlockPos controllerPos) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null && minecraft.level.getBlockEntity(controllerPos) instanceof DisplayBlockEntity display) {
			ChannelPlayer player = this.player(display);
			if (player != null) {
				player.retry();
			}
		}
	}

	public void acceptPlaybackUrl(BlockPos pos, int epoch, int proxyPort, String proxyHost, String streamUrl, String subtitleUrl) {
		BlockPos key = pos.immutable();
		this.playbackTickets.put(
			key,
			new PlaybackTicket(
				epoch,
				this.proxyUrl(proxyPort, proxyHost, streamUrl),
				this.proxyUrl(proxyPort, proxyHost, subtitleUrl)
			)
		);
		this.pendingTicketRequests.remove(ticketRequestKey(key, epoch));
	}

	private String proxyUrl(int proxyPort, String proxyHost, String url) {
		if (url == null || url.isBlank()) {
			return "";
		}
		if (!url.startsWith("/")) {
			return url;
		}
		String host = proxyHost == null || proxyHost.isBlank() ? serverProxyHost() : proxyHost;
		if (host == null || proxyPort <= 0) {
			return "";
		}
		return "http://" + host + ":" + proxyPort + url;
	}

	public static String serverProxyHost() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.getConnection() == null) {
			return null;
		}
		try {
			SocketAddress address = minecraft.getConnection().getConnection().getRemoteAddress();
			if (address instanceof InetSocketAddress inet) {
				String host = inet.getHostString();
				if (host != null && !host.isBlank()) {
					return host.contains(":") ? "[" + host + "]" : host;
				}
			}
		} catch (RuntimeException ignored) {
		}
		return null;
	}

	public void clientTick() {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		Player player = minecraft.player;
		if (level == null || player == null) {
			this.releaseAll();
			return;
		}

		if (this.tickCounter++ % RESCAN_INTERVAL_TICKS == 0) {
			this.rescan(level, player);
		}

		this.driveSessions(level, player);
	}

	private void rescan(ClientLevel level, Player player) {
		this.nearbyControllers.clear();
		double keepAlive = ConfigManager.get().maximumPlaybackDistance * KEEP_ALIVE_MULTIPLIER;
		int chunkRadius = Math.max(1, SectionPos.blockToSectionCoord((int)Math.ceil(keepAlive)) + 1);
		int centreX = SectionPos.blockToSectionCoord(player.getBlockX());
		int centreZ = SectionPos.blockToSectionCoord(player.getBlockZ());
		double keepAliveSqr = keepAlive * keepAlive;

		for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
			for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
				LevelChunk chunk = level.getChunkSource().getChunk(centreX + dx, centreZ + dz, ChunkStatus.FULL, false);
				if (chunk == null) {
					continue;
				}
				for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
					if (entry.getValue() instanceof DisplayBlockEntity display
						&& player.distanceToSqr(display.screenCentre()) <= keepAliveSqr) {
						this.nearbyControllers.add(entry.getKey().immutable());
					}
				}
			}
		}
	}

	private void driveSessions(ClientLevel level, Player player) {
		PixelReelConfig config = ConfigManager.get();
		double startDistance = config.maximumPlaybackDistance;
		double startDistanceSqr = startDistance * startDistance;
		double keepAliveDistance = startDistance * KEEP_ALIVE_MULTIPLIER;
		double keepAliveDistanceSqr = keepAliveDistance * keepAliveDistance;

		record Binding(DisplayBlockEntity display, Vec3 centre, double distanceSqr) {
		}
		Map<String, List<Binding>> desired = new HashMap<>();

		for (BlockPos pos : this.nearbyControllers) {
			if (!(level.getBlockEntity(pos) instanceof DisplayBlockEntity display)) {
				continue;
			}
			if (!display.shouldPlay()) {
				continue;
			}
			String url = this.playbackUrl(display);
			if (url == null || url.isEmpty()) {
				this.requestPlaybackUrl(display);
				continue;
			}
			Vec3 centre = display.screenCentre();
			double distanceSqr = player.distanceToSqr(centre);
			boolean alreadyPlaying = this.players.containsKey(url);
			double limitSqr = alreadyPlaying ? keepAliveDistanceSqr : startDistanceSqr;
			if (distanceSqr > limitSqr) {
				continue;
			}
			desired.computeIfAbsent(url, key -> new ArrayList<>()).add(new Binding(display, centre, distanceSqr));
		}

		LinkedHashSet<String> ranked = desired.entrySet().stream()
			.sorted(Comparator.comparingDouble(entry -> entry.getValue().stream().mapToDouble(Binding::distanceSqr).min().orElse(Double.MAX_VALUE)))
			.map(Map.Entry::getKey)
			.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

		int budget = config.maxSimultaneousChannels;
		Set<String> allowed = new HashSet<>();
		for (String url : ranked) {
			if (allowed.size() >= budget) {
				break;
			}
			allowed.add(url);
		}

		for (String url : allowed) {
			List<Binding> bindings = desired.get(url);
			Binding nearest = bindings == null || bindings.isEmpty()
				? null
				: bindings.stream().min(Comparator.comparingDouble(Binding::distanceSqr)).orElse(null);
			if (nearest == null) {
				continue;
			}
			DisplayBlockEntity display = nearest.display();
			boolean onDemand = display.isOnDemand();
			int epoch = display.getChannelEpoch();
			ChannelPlayer channelPlayer = this.players.get(url);
			Integer previousEpoch = this.playerEpochs.get(url);
			if (channelPlayer == null) {
				channelPlayer = new ChannelPlayer(
					url,
					onDemand,
					onDemand ? display.getStartPositionMs() : 0L,
					onDemand && display.isHdrContent()
				);
				this.players.put(url, channelPlayer);
				this.playerEpochs.put(url, epoch);
				this.endedReports.remove(url);
				this.durationReports.remove(url);
				PixelReel.LOGGER.info("Playing {} stream ({}) — {} active", onDemand ? "on-demand" : "channel", host(url), this.players.size());
			} else if (previousEpoch == null || previousEpoch != epoch) {
				this.playerEpochs.put(url, epoch);
				this.endedReports.remove(url);
				this.durationReports.remove(url);
				if (onDemand) {
					channelPlayer.requestSeek(display.currentPlaybackPositionMs());
				}
			}
		}

		float globalVolume = (float)config.globalTvVolume;
		Iterator<Map.Entry<String, ChannelPlayer>> iterator = this.players.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<String, ChannelPlayer> entry = iterator.next();
			ChannelPlayer channelPlayer = entry.getValue();
			List<Binding> bindings = desired.get(entry.getKey());
			boolean active = allowed.contains(entry.getKey()) && bindings != null && !bindings.isEmpty();
			if (!active) {
				PixelReel.LOGGER.info("Stopping channel stream ({}) — out of range or switched away", host(entry.getKey()));
				iterator.remove();
				this.playerEpochs.remove(entry.getKey());
				this.endedReports.remove(entry.getKey());
				this.durationReports.remove(entry.getKey());
				channelPlayer.close();
				continue;
			}

			Binding nearest = bindings.stream().min(Comparator.comparingDouble(Binding::distanceSqr)).orElseThrow();
			DisplayBlockEntity display = nearest.display();
			if (display.isOnDemand()) {
				channelPlayer.setSubtitleUrl(this.subtitleUrl(display));
				boolean paused = display.isPlaybackPaused() || display.isSuspended();
				channelPlayer.setDesiredPaused(paused);
				long target = display.currentPlaybackPositionMs();
				long actual = channelPlayer.mediaTimeMs();
				if (!paused && Math.abs(actual - target) > 2500L) {
					channelPlayer.requestSeek(target);
				}
				long length = channelPlayer.mediaLengthMs();
				if (length > 0L && display.getPlaybackDurationMs() <= 0L && this.durationReports.add(entry.getKey())) {
					ClientNetworking.sendControl(display.getBlockPos(), ScreenAction.REPORT_DURATION, (float)length);
				}
			} else {
				channelPlayer.setDesiredPaused(false);
			}

			channelPlayer.tick();
			if (display.isOnDemand()
				&& channelPlayer.status() == PlaybackStatus.ENDED
				&& this.endedReports.add(entry.getKey())) {
				ClientNetworking.reportMediaEnded(display.getBlockPos(), display.getChannelEpoch());
			}
			float volume = 0.0F;
			float audioRange = 8.0F;
			for (Binding binding : bindings) {
				volume = Math.max(volume, binding.display().getVolume());
				audioRange = Math.max(audioRange, binding.display().type().audioRange());
			}
			boolean glasses = PixelGlassesItem.isWearing(player);
			double distance = Math.sqrt(nearest.distanceSqr());
			// Soft knee near the edge so gain does not slam to zero and mute-thrash.
			// Cinema glasses listen at full screen volume (no distance falloff).
			double normalized = glasses ? 1.0 : softDistanceGain(distance, audioRange);
			float gain = (float)(volume * globalVolume * normalized);
			if (display.isOnDemand() && display.isPlaybackPaused()) {
				gain = 0.0F;
			}
			channelPlayer.tickAudio(gain);
		}
	}

	private static double softDistanceGain(double distance, double audioRange) {
		if (distance <= 0.0 || audioRange <= 0.0) {
			return 1.0;
		}
		if (distance >= audioRange) {
			return 0.0;
		}
		double half = audioRange * 0.5;
		if (distance <= half) {
			return 1.0;
		}
		return Math.clamp(1.0 - (distance - half) / (audioRange - half), 0.0, 1.0);
	}

	private static String host(String url) {
		return com.pixelreel.channels.ChannelService.hostOnly(url);
	}

	public @Nullable PictureHandle pictureFor(DisplayBlockEntity display) {
		String url = this.playbackUrl(display);
		ChannelPlayer current = this.players.get(url);
		if (current != null && current.hasPicture() && current.uploadFrame()) {
			VideoTexture texture = current.videoTexture();
			return new PictureHandle(
				texture.textureId(),
				texture.displayAspect(),
				texture.contentU0(),
				texture.contentV0(),
				texture.contentU1(),
				texture.contentV1()
			);
		}
		return null;
	}

	public @Nullable PlaybackStatus statusFor(DisplayBlockEntity display) {
		ChannelPlayer player = this.player(display);
		return player == null ? null : player.status();
	}

	public @Nullable DisplayBlockEntity nearestPlayingDisplay(Player player, ClientLevel level) {
		DisplayBlockEntity best = null;
		double bestDistanceSqr = Double.MAX_VALUE;
		for (BlockPos pos : this.nearbyControllers) {
			if (!(level.getBlockEntity(pos) instanceof DisplayBlockEntity display) || !display.shouldPlay()) {
				continue;
			}
			double distanceSqr = player.distanceToSqr(display.screenCentre());
			if (distanceSqr < bestDistanceSqr) {
				bestDistanceSqr = distanceSqr;
				best = display;
			}
		}
		return best;
	}

	public void release(BlockPos controllerPos) {
		this.nearbyControllers.remove(controllerPos);
	}

	public void releaseAll() {
		if (!this.players.isEmpty()) {
			PixelReel.LOGGER.debug("Releasing {} channel player(s)", this.players.size());
			this.players.values().forEach(ChannelPlayer::close);
			this.players.clear();
		}
		this.playerEpochs.clear();
		this.playbackTickets.clear();
		this.pendingTicketRequests.clear();
		this.endedReports.clear();
		this.durationReports.clear();
		this.nearbyControllers.clear();
	}

	private String playbackUrl(DisplayBlockEntity display) {
		String direct = display.getStreamUrl();
		if (direct != null && !direct.isEmpty()) {
			return direct;
		}
		PlaybackTicket ticket = this.playbackTickets.get(display.getBlockPos());
		if (ticket == null || ticket.epoch() != display.getChannelEpoch()) {
			return "";
		}
		return ticket.streamUrl();
	}

	private String subtitleUrl(DisplayBlockEntity display) {
		String direct = display.getSubtitleFetchUrl();
		if (direct != null && !direct.isEmpty()) {
			return direct;
		}
		PlaybackTicket ticket = this.playbackTickets.get(display.getBlockPos());
		if (ticket == null || ticket.epoch() != display.getChannelEpoch()) {
			return "";
		}
		return ticket.subtitleUrl();
	}

	private void requestPlaybackUrl(DisplayBlockEntity display) {
		String key = ticketRequestKey(display.getBlockPos(), display.getChannelEpoch());
		if (this.pendingTicketRequests.add(key)) {
			ClientNetworking.requestPlaybackUrl(display.getBlockPos(), display.getChannelEpoch());
		}
	}

	private static String ticketRequestKey(BlockPos pos, int epoch) {
		return pos.asLong() + ":" + epoch;
	}

	private record PlaybackTicket(int epoch, String streamUrl, String subtitleUrl) {
	}

	public record PictureHandle(
		net.minecraft.resources.Identifier textureId,
		float aspect,
		float u0,
		float v0,
		float u1,
		float v1
	) {
	}
}
