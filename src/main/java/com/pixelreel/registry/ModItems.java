package com.pixelreel.registry;

import com.pixelreel.PixelReel;
import com.pixelreel.blocks.DisplayBlock;
import com.pixelreel.items.DisplayBlockItem;
import com.pixelreel.items.PixelGlassesItem;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public final class ModItems {
	public static final Item COMPACT_TELEVISION = registerDisplayItem(ModBlocks.COMPACT_TELEVISION, "item.pixelreel.display.type.television");
	public static final Item WALL_TELEVISION = registerDisplayItem(ModBlocks.WALL_TELEVISION, "item.pixelreel.display.type.television");
	public static final Item ULTRAWIDE_MONITOR = registerDisplayItem(ModBlocks.ULTRAWIDE_MONITOR, "item.pixelreel.display.type.monitor");
	public static final Item CINEMA_SCREEN = registerDisplayItem(ModBlocks.CINEMA_SCREEN, "item.pixelreel.display.type.cinema");
	public static final Item CURVED_CINEMA_SCREEN = registerDisplayItem(ModBlocks.CURVED_CINEMA_SCREEN, "item.pixelreel.display.type.cinema");
	public static final Item PIXEL_GLASSES = register(
		"pixel_glasses",
		PixelGlassesItem::new,
		// PixelGlassesItem.use() handles equip/unequip to avoid creative-dupe swap behaviour.
		new Item.Properties().stacksTo(1)
	);

	public static final List<Item> TAB_CONTENTS = List.of(
		COMPACT_TELEVISION, WALL_TELEVISION, ULTRAWIDE_MONITOR, CINEMA_SCREEN, CURVED_CINEMA_SCREEN, PIXEL_GLASSES
	);

	private ModItems() {
	}

	private static Item registerDisplayItem(DisplayBlock block, String tooltipKey) {
		String name = BuiltInRegistries.BLOCK.getKey(block).getPath();
		return register(name, properties -> new DisplayBlockItem(block, properties, tooltipKey), new Item.Properties());
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		Item item = factory.apply(properties);
		if (item instanceof BlockItem blockItem) {
			Item.BY_BLOCK.put(blockItem.getBlock(), item);
		}
		return Registry.register(BuiltInRegistries.ITEM, PixelReel.id(name), item);
	}

	public static void init() {
	}
}
