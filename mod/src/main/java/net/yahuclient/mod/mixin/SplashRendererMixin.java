package net.yahuclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.SplashRenderer;

/** No yellow splash text next to the logo: it belongs to the Minecraft look. */
@Mixin(SplashRenderer.class)
public abstract class SplashRendererMixin {
	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void yahu$noSplash(GuiGraphicsExtractor g, int screenWidth, Font font, float alpha, CallbackInfo ci) {
		ci.cancel();
	}
}
