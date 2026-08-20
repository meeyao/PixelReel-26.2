package com.pixelreel.client.gui.shared;

import com.mojang.blaze3d.systems.RenderSystem;
import com.pixelreel.client.texture.PosterCache;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** helpers for poster / thumbnail cards. */
public final class SharedPoster {
	private SharedPoster() {
	}

	public static void blitCover(GuiGraphics graphics, PosterCache.Poster poster, int x, int y, int width, int height) {
		ResourceLocation texture = poster.texture();
		if (texture == null) {
			return;
		}
		int texW = Math.max(1, poster.width());
		int texH = Math.max(1, poster.height());
		float texAspect = (float)texW / texH;
		float slotAspect = (float)width / height;
		float u;
		float v;
		int regionW;
		int regionH;
		if (texAspect > slotAspect) {
			regionH = texH;
			regionW = Math.max(1, Math.round(texH * slotAspect));
			u = (texW - regionW) * 0.5F;
			v = 0.0F;
		} else {
			regionW = texW;
			regionH = Math.max(1, Math.round(texW / slotAspect));
			u = 0.0F;
			v = (texH - regionH) * 0.5F;
		}
		blit(graphics, texture, x, y, width, height, u, v, regionW, regionH, texW, texH);
	}

	public static void blitPlaceholder(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height) {
		// Stretch the full 16x16 placeholder into the card slot.
		blit(graphics, texture, x, y, width, height, 0.0F, 0.0F, 16, 16, 16, 16);
	}

	private static void blit(
		GuiGraphics graphics,
		ResourceLocation texture,
		int x,
		int y,
		int width,
		int height,
		float u,
		float v,
		int regionW,
		int regionH,
		int texW,
		int texH
	) {
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		graphics.blit(texture, x, y, width, height, u, v, regionW, regionH, texW, texH);
		RenderSystem.disableBlend();
	}
}
