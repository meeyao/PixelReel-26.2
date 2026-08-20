package com.pixelreel.registry;

import com.pixelreel.PixelReel;
import com.pixelreel.blocks.DisplayBlock;
import com.pixelreel.blocks.DisplayType;
import com.pixelreel.blocks.ScreenPanelBlock;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class ModBlocks {
	public static final DisplayBlock COMPACT_TELEVISION = registerDisplay(DisplayType.COMPACT_TELEVISION);
	public static final DisplayBlock WALL_TELEVISION = registerDisplay(DisplayType.WALL_TELEVISION);
	public static final DisplayBlock ULTRAWIDE_MONITOR = registerDisplay(DisplayType.ULTRAWIDE_MONITOR);
	public static final DisplayBlock CINEMA_SCREEN = registerDisplay(DisplayType.CINEMA_SCREEN);
	public static final DisplayBlock CURVED_CINEMA_SCREEN = registerDisplay(DisplayType.CURVED_CINEMA_SCREEN);
	public static final ScreenPanelBlock SCREEN_PANEL = registerPanel();

	public static final List<DisplayBlock> ALL_DISPLAYS = List.of(
		COMPACT_TELEVISION, WALL_TELEVISION, ULTRAWIDE_MONITOR, CINEMA_SCREEN, CURVED_CINEMA_SCREEN
	);

	private ModBlocks() {
	}

	private static DisplayBlock registerDisplay(DisplayType type) {
		BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_BLACK)
			.strength(1.5F, 6.0F)
			.sound(SoundType.METAL)
			.noOcclusion()
			.pushReaction(PushReaction.BLOCK)
			.isRedstoneConductor((state, level, pos) -> false)
			.isSuffocating((state, level, pos) -> false)
			.isViewBlocking((state, level, pos) -> false);
		return Registry.register(BuiltInRegistries.BLOCK, PixelReel.id(type.id()), new DisplayBlock(properties, type));
	}

	private static ScreenPanelBlock registerPanel() {
		BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
			.mapColor(MapColor.NONE)
			.strength(-1.0F, 3600000.0F)
			.noOcclusion()
			.pushReaction(PushReaction.BLOCK)
			.noLootTable()
			.isRedstoneConductor((state, level, pos) -> false)
			.isSuffocating((state, level, pos) -> false)
			.isViewBlocking((state, level, pos) -> false);
		return Registry.register(BuiltInRegistries.BLOCK, PixelReel.id("screen_panel"), new ScreenPanelBlock(properties));
	}

	public static void init() {
	}
}
