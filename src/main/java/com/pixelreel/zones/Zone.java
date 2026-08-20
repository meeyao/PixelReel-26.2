package com.pixelreel.zones;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;

/**
 * A named rectangular region in the world. Displays assigned to a zone provide
 * audio only to players standing inside that zone, preventing bleed between
 * nearby screens.
 */
public final class Zone {
	private final String name;
	private final String dimension;
	private final int x0, y0, z0;
	private final int x1, y1, z1;

	public Zone(String name, String dimension, BlockPos corner1, BlockPos corner2) {
		this.name = name;
		this.dimension = dimension;
		this.x0 = Math.min(corner1.getX(), corner2.getX());
		this.y0 = Math.min(corner1.getY(), corner2.getY());
		this.z0 = Math.min(corner1.getZ(), corner2.getZ());
		this.x1 = Math.max(corner1.getX(), corner2.getX());
		this.y1 = Math.max(corner1.getY(), corner2.getY());
		this.z1 = Math.max(corner1.getZ(), corner2.getZ());
	}

	private Zone(String name, String dimension, int x0, int y0, int z0, int x1, int y1, int z1) {
		this.name = name;
		this.dimension = dimension;
		this.x0 = x0;
		this.y0 = y0;
		this.z0 = z0;
		this.x1 = x1;
		this.y1 = y1;
		this.z1 = z1;
	}

	public String name() {
		return this.name;
	}

	public String dimension() {
		return this.dimension;
	}

	public BlockPos min() {
		return new BlockPos(x0, y0, z0);
	}

	public BlockPos max() {
		return new BlockPos(x1, y1, z1);
	}

	public boolean contains(String dimension, BlockPos pos) {
		if (!this.dimension.equals(dimension)) {
			return false;
		}
		return pos.getX() >= x0 && pos.getX() <= x1
			&& pos.getY() >= y0 && pos.getY() <= y1
			&& pos.getZ() >= z0 && pos.getZ() <= z1;
	}

	public boolean contains(String dimension, double x, double y, double z) {
		if (!this.dimension.equals(dimension)) {
			return false;
		}
		return x >= x0 && x <= x1 + 1.0
			&& y >= y0 && y <= y1 + 1.0
			&& z >= z0 && z <= z1 + 1.0;
	}

	public BlockPos centre() {
		return new BlockPos((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2);
	}

	public int volumeBlocks() {
		return (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);
	}

	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("name", name);
		json.addProperty("dimension", dimension);
		json.addProperty("x0", x0);
		json.addProperty("y0", y0);
		json.addProperty("z0", z0);
		json.addProperty("x1", x1);
		json.addProperty("y1", y1);
		json.addProperty("z1", z1);
		return json;
	}

	public static Zone fromJson(JsonObject json) {
		return new Zone(
			json.get("name").getAsString(),
			json.get("dimension").getAsString(),
			json.get("x0").getAsInt(),
			json.get("y0").getAsInt(),
			json.get("z0").getAsInt(),
			json.get("x1").getAsInt(),
			json.get("y1").getAsInt(),
			json.get("z1").getAsInt()
		);
	}
}
