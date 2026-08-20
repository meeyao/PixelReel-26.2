package com.pixelreel.client;

import com.pixelreel.zones.Zone;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;

/**
 * Client-side copy of audio zones, populated by the server on join and whenever
 * zones change. Used by {@code PlaybackManager} to filter audio by zone.
 */
public final class ClientZoneCache {
	public static final ClientZoneCache INSTANCE = new ClientZoneCache();

	private volatile List<Zone> zones = List.of();

	private ClientZoneCache() {
	}

	public void update(List<Zone> zones) {
		this.zones = zones == null ? List.of() : List.copyOf(zones);
	}

	public List<Zone> allZones() {
		return zones;
	}

	public Optional<Zone> playerZone(Player player) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return Optional.empty();
		}
		String dimension = level.dimension().location().toString();
		for (Zone zone : zones) {
			if (zone.contains(dimension, player.getX(), player.getY(), player.getZ())) {
				return Optional.of(zone);
			}
		}
		return Optional.empty();
	}

	public boolean isDisplayInPlayerZone(com.pixelreel.blockentities.DisplayBlockEntity display) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null) {
			return true;
		}
		String playerZoneName = playerZone(minecraft.player).map(Zone::name).orElse("");
		String displayZone = display.getZoneId();
		if (displayZone.isEmpty()) {
			return true;
		}
		return displayZone.equals(playerZoneName);
	}
}
