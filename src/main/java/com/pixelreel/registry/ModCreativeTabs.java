package com.pixelreel.registry;

import com.pixelreel.PixelReel;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class ModCreativeTabs {
	public static final ResourceKey<CreativeModeTab> PIXEL_REEL = ResourceKey.create(Registries.CREATIVE_MODE_TAB, PixelReel.id("pixelreel"));

	private ModCreativeTabs() {
	}

	public static void init() {
		Registry.register(
			BuiltInRegistries.CREATIVE_MODE_TAB,
			PIXEL_REEL,
			FabricItemGroup.builder()
				.title(Component.translatable("itemGroup.pixelreel.pixelreel"))
				.icon(() -> new ItemStack(ModItems.WALL_TELEVISION))
				.displayItems((parameters, output) -> ModItems.TAB_CONTENTS.forEach(output::accept))
				.build()
		);
	}
}
