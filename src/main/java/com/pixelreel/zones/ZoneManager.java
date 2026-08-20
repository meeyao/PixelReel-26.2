package com.pixelreel.zones;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.pixelreel.PixelReel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

/**
 * Manages all audio zones. Persists to {@code config/pixelreel-zones.json}.
 * Thread-safe for reads; mutations must run on the server thread.
 */
public final class ZoneManager {
	private static final String FILE_NAME = "pixelreel-zones.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public static final ZoneManager INSTANCE = new ZoneManager();

	private final Map<String, Zone> zones = new ConcurrentHashMap<>();
	private Path filePath;

	private ZoneManager() {
	}

	// --- persistence ---

	public void load() {
		if (filePath == null) {
			filePath = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
		}
		if (!Files.exists(filePath)) {
			zones.clear();
			return;
		}
		try {
			String text = Files.readString(filePath, StandardCharsets.UTF_8);
			JsonObject root = GSON.fromJson(text, JsonObject.class);
			zones.clear();
			if (root != null && root.has("zones")) {
				for (var element : root.getAsJsonArray("zones")) {
					Zone zone = Zone.fromJson(element.getAsJsonObject());
					zones.put(zone.name(), zone);
				}
			}
			PixelReel.LOGGER.info("Loaded {} audio zone(s) from {}", zones.size(), filePath);
		} catch (JsonSyntaxException e) {
			PixelReel.LOGGER.error("Audio zones file {} is not valid JSON; keeping in-memory state.", filePath, e);
		} catch (IOException e) {
			PixelReel.LOGGER.error("Could not read audio zones from {}; keeping in-memory state.", filePath, e);
		}
	}

	public void save() {
		if (filePath == null) {
			filePath = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
		}
		JsonObject root = new JsonObject();
		JsonArray array = new JsonArray();
		for (Zone zone : zones.values()) {
			array.add(zone.toJson());
		}
		root.add("zones", array);
		try {
			Files.createDirectories(filePath.getParent());
			Path temp = filePath.resolveSibling(filePath.getFileName() + ".tmp");
			Files.writeString(temp, GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
			try {
				Files.move(temp, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, filePath, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			PixelReel.LOGGER.error("Failed to write audio zones to {}", filePath, e);
		}
	}

	// --- CRUD ---

	public void addZone(Zone zone) {
		zones.put(zone.name(), zone);
		save();
	}

	public boolean removeZone(String name) {
		boolean removed = zones.remove(name) != null;
		if (removed) {
			save();
		}
		return removed;
	}

	public void clearAll() {
		zones.clear();
		save();
	}

	public Optional<Zone> getZone(String name) {
		return Optional.ofNullable(zones.get(name));
	}

	public List<Zone> allZones() {
		return List.copyOf(zones.values());
	}

	public boolean hasZone(String name) {
		return zones.containsKey(name);
	}

	// --- spatial queries ---

	public Optional<Zone> zoneAt(String dimension, BlockPos pos) {
		for (Zone zone : zones.values()) {
			if (zone.contains(dimension, pos)) {
				return Optional.of(zone);
			}
		}
		return Optional.empty();
	}

	public Optional<Zone> playerZone(MinecraftServer server, net.minecraft.server.level.ServerPlayer player) {
		Level level = player.level();
		String dimension = level.dimension().location().toString();
		return zoneAt(dimension, player.blockPosition());
	}

	public List<Zone> zonesForDimension(String dimension) {
		List<Zone> result = new ArrayList<>();
		for (Zone zone : zones.values()) {
			if (zone.dimension().equals(dimension)) {
				result.add(zone);
			}
		}
		return result;
	}
}
