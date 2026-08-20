package com.pixelreel.client.mixin;

import com.pixelreel.client.render.GlassesOverlay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {
	@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
	private void pixelreel$hideCrosshairWhileGlasses(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (GlassesOverlay.shouldHideHud()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderHotbarAndDecorations", at = @At("HEAD"), cancellable = true)
	private void pixelreel$hideHotbarWhileGlasses(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (GlassesOverlay.shouldHideHud()) {
			ci.cancel();
		}
	}
}
