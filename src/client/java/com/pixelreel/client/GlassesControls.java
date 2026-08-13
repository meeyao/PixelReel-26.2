package com.pixelreel.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.pixelreel.PixelReel;
import com.pixelreel.items.PixelGlassesItem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * added different Ways Ways to leave pixel-glasses fullscreen.
 */
public final class GlassesControls {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(PixelReel.id("pixelreel"));
	private static KeyMapping removeGlassesKey;

	private GlassesControls() {
	}

	public static void register() {
		removeGlassesKey = new KeyMapping(
			"key.pixelreel.remove_pixel_glasses",
			InputConstants.Type.KEYSYM,
			InputConstants.KEY_X,
			CATEGORY
		);

		ClientTickEvents.END_CLIENT_TICK.register(GlassesControls::tick);

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof PauseScreen) || client.player == null) {
				return;
			}
			if (!PixelGlassesItem.isWearing(client.player)) {
				return;
			}
			requestRemove(client);
			client.gui.setScreen(null);
		});
	}

	public static KeyMapping removeKey() {
		return removeGlassesKey;
	}

	private static void tick(Minecraft minecraft) {
		if (minecraft.player == null || minecraft.level == null || minecraft.gui.screen() != null) {
			return;
		}
		if (!PixelGlassesItem.isWearing(minecraft.player)) {
			return;
		}
		if (removeGlassesKey.consumeClick()) {
			requestRemove(minecraft);
		}
	}

	public static void requestRemove(Minecraft minecraft) {
		if (minecraft.player == null || !PixelGlassesItem.isWearing(minecraft.player)) {
			return;
		}
		minecraft.player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		ClientNetworking.unequipPixelGlasses();
	}
}
