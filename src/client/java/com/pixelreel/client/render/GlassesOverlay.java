package com.pixelreel.client.render;

import com.pixelreel.PixelReel;
import com.pixelreel.blockentities.DisplayBlockEntity;
import com.pixelreel.client.GlassesControls;
import com.pixelreel.client.playback.PlaybackManager;
import com.pixelreel.client.playback.PlaybackStatus;
import com.pixelreel.items.PixelGlassesItem;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Fullscreen of the nearest playing screen when pixel glasses are put on.
 */
public final class GlassesOverlay {
	private static final ResourceLocation CONNECTING = PixelReel.id("textures/block/screen_connecting.png");
	private static final ResourceLocation ERROR = PixelReel.id("textures/block/screen_error.png");
	private static final ResourceLocation NO_SIGNAL = PixelReel.id("textures/block/screen_no_signal.png");
	private static final ResourceLocation NO_PLAYER = PixelReel.id("textures/block/screen_no_player.png");
	private static final float STATUS_ASPECT = 2.0F;
	private static final int COLOR_BLACK = 0xFF000000;
	private static final long HINT_HOLD_MS = 2500L;
	private static final long HINT_FADE_MS = 1800L;

	private static boolean wasWearing;
	private static long hintStartedAtMs = -1L;

	private GlassesOverlay() {
	}

	/** True while the fullscreen glasses picture is covering the playfield. */
	public static boolean shouldHideHud() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player != null
			&& minecraft.screen == null
			&& PixelGlassesItem.isWearing(minecraft.player);
	}

	public static void render(GuiGraphics graphics, float deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null) {
			wasWearing = false;
			return;
		}
		boolean wearing = PixelGlassesItem.isWearing(minecraft.player);
		if (!wearing) {
			wasWearing = false;
			hintStartedAtMs = -1L;
			return;
		}
		if (!wasWearing) {
			wasWearing = true;
			hintStartedAtMs = System.currentTimeMillis();
		}
		if (minecraft.screen != null) {
			return;
		}

		int width = minecraft.getWindow().getGuiScaledWidth();
		int height = minecraft.getWindow().getGuiScaledHeight();
		graphics.fill(0, 0, width, height, COLOR_BLACK);

		DisplayBlockEntity display = PlaybackManager.INSTANCE.nearestPlayingDisplay(minecraft.player, minecraft.level);
		if (display == null) {
			blitContain(graphics, NO_SIGNAL, STATUS_ASPECT, 0.0F, 0.0F, 1.0F, 1.0F, width, height);
		} else {
			PlaybackManager.PictureHandle picture = PlaybackManager.INSTANCE.pictureFor(display);
			if (picture != null) {
				blitContain(graphics, picture.textureId(), picture.aspect(), picture.u0(), picture.v0(), picture.u1(), picture.v1(), width, height);
			} else {
				PlaybackStatus status = PlaybackManager.INSTANCE.statusFor(display);
				ResourceLocation texture = switch (status == null ? PlaybackStatus.CONNECTING : status) {
					case UNAVAILABLE -> NO_PLAYER;
					case ERROR, ENDED -> ERROR;
					case CONNECTING, BUFFERING, PLAYING, IDLE -> CONNECTING;
				};
				blitContain(graphics, texture, STATUS_ASPECT, 0.0F, 0.0F, 1.0F, 1.0F, width, height);
			}
		}

		drawFadeHint(graphics, minecraft, width, height);
	}

	private static void drawFadeHint(GuiGraphics graphics, Minecraft minecraft, int width, int height) {
		float alpha = hintAlpha();
		if (alpha <= 0.02F) {
			return;
		}
		KeyMapping removeKey = GlassesControls.removeKey();
		Component keyName = removeKey != null
			? removeKey.getTranslatedKeyMessage()
			: Component.literal("X");
		int color = withAlpha(0xFFFFFF, alpha);
		graphics.drawCenteredString(
			minecraft.font,
			Component.translatable("gui.pixelreel.glasses.escape_hint", keyName),
			width / 2,
			height - 18,
			color
		);
	}

	private static float hintAlpha() {
		if (hintStartedAtMs < 0L) {
			return 0.0F;
		}
		long elapsed = System.currentTimeMillis() - hintStartedAtMs;
		if (elapsed < HINT_HOLD_MS) {
			return 1.0F;
		}
		float fade = 1.0F - (elapsed - HINT_HOLD_MS) / (float)HINT_FADE_MS;
		return Mth.clamp(fade, 0.0F, 1.0F);
	}

	private static int withAlpha(int rgb, float alpha) {
		int a = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
		return (a << 24) | (rgb & 0x00FFFFFF);
	}

	private static void blitContain(
		GuiGraphics graphics,
		ResourceLocation texture,
		float aspect,
		float u0,
		float v0,
		float u1,
		float v1,
		int screenWidth,
		int screenHeight
	) {
		if (aspect <= 0.05F) {
			aspect = 16.0F / 9.0F;
		}
		float screenAspect = (float)screenWidth / (float)screenHeight;
		int drawWidth;
		int drawHeight;
		if (aspect > screenAspect) {
			drawWidth = screenWidth;
			drawHeight = Math.max(1, Math.round(screenWidth / aspect));
		} else {
			drawHeight = screenHeight;
			drawWidth = Math.max(1, Math.round(screenHeight * aspect));
		}
		int x0 = (screenWidth - drawWidth) / 2;
		int y0 = (screenHeight - drawHeight) / 2;
		int texSize = 256;
		int uWidth = Math.max(1, Math.round((u1 - u0) * texSize));
		int vHeight = Math.max(1, Math.round((v1 - v0) * texSize));
		graphics.blit(
			texture,
			x0,
			y0,
			drawWidth,
			drawHeight,
			u0 * texSize,
			v0 * texSize,
			uWidth,
			vHeight,
			texSize,
			texSize
		);
	}
}
