package com.pixelreel.registry;

import com.pixelreel.PixelReel;
import com.pixelreel.blockentities.DisplayBlockEntity;
import com.pixelreel.blockentities.ScreenPanelBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
	public static final BlockEntityType<DisplayBlockEntity> DISPLAY = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		PixelReel.id("display"),
		FabricBlockEntityTypeBuilder.create(DisplayBlockEntity::new, displayBlocks()).build()
	);

	public static final BlockEntityType<ScreenPanelBlockEntity> SCREEN_PANEL = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		PixelReel.id("screen_panel"),
		FabricBlockEntityTypeBuilder.create(ScreenPanelBlockEntity::new, ModBlocks.SCREEN_PANEL).build()
	);

	private ModBlockEntities() {
	}

	private static Block[] displayBlocks() {
		return ModBlocks.ALL_DISPLAYS.toArray(Block[]::new);
	}

	public static void init() {
	}
}
