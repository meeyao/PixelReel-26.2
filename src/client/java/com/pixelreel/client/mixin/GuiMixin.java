package com.pixelreel.client.mixin;

import com.pixelreel.client.render.GlassesOverlay;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void pixelreel$hideHudWhileGlasses(GuiGraphics graphics, float deltaTracker, CallbackInfo ci) {
		if (GlassesOverlay.shouldHideHud()) {
			ci.cancel();
		}
	}
}
