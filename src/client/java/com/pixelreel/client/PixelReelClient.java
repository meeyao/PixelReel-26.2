package com.pixelreel.client;

import com.pixelreel.ClientBridge;
import com.pixelreel.blockentities.DisplayBlockEntity;
import com.pixelreel.blocks.DisplayBlock;
import com.pixelreel.PixelReel;
import com.pixelreel.client.playback.PlaybackManager;
import com.pixelreel.client.playback.video.VlcRuntime;
import com.pixelreel.client.render.DisplayBlockEntityRenderer;
import com.pixelreel.client.render.GlassesOverlay;
import com.pixelreel.client.texture.PosterCache;
import com.pixelreel.registry.ModBlockEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class PixelReelClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		VlcRuntime.ensureInitialised();
		ClientNetworking.register();
		GlassesControls.register();
		BlockEntityRenderers.register(ModBlockEntities.DISPLAY, DisplayBlockEntityRenderer::new);
		// Draw above the world / misc overlays so glasses take over the view.
		HudElementRegistry.addLast(PixelReel.id("pixel_glasses"), GlassesOverlay::extract);

		ClientBridge.setHandler(new ClientBridge.Handler() {
			@Override
			public void openChannelMenu(BlockPos controllerPos) {
				ClientNetworking.openMenu(controllerPos);
			}

			@Override
			public void releasePlayback(BlockPos controllerPos) {
				PlaybackManager.INSTANCE.release(controllerPos);
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			PlaybackManager.INSTANCE.clientTick();
			tickAutoplayPrompt(minecraft);
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> {
			PlaybackManager.INSTANCE.releaseAll();
			ClientChannelCache.INSTANCE.clear();
			ClientMediaCache.INSTANCE.clear();
			ClientPosterUrlCache.INSTANCE.clear();
		});

		ClientLifecycleEvents.CLIENT_STOPPING.register(minecraft -> {
			PlaybackManager.INSTANCE.releaseAll();
			PosterCache.INSTANCE.clear();
			VlcRuntime.shutdown();
		});
	}

	private static void tickAutoplayPrompt(Minecraft minecraft) {
		if (minecraft.player == null || minecraft.level == null || minecraft.gui.screen() != null) {
			return;
		}
		if (minecraft.player.tickCount % 20 != 0) {
			return;
		}
		HitResult hit = minecraft.hitResult;
		if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
			return;
		}
		DisplayBlockEntity display = DisplayBlock.controllerAt(minecraft.level, blockHit.getBlockPos());
		if (display == null || !display.hasAutoplayPending()) {
			return;
		}
		long remaining = Math.max(0L, (display.getAutoplayAtMillis() - System.currentTimeMillis() + 999L) / 1000L);
		minecraft.player.sendOverlayMessage(
			Component.translatable("gui.pixelreel.playback.next_overlay", display.getNextEpisodeTitle(), remaining)
		);
	}
}
