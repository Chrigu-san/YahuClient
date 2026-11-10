package net.yahuclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.yahuclient.mod.ui.BlockWidgets;

/**
 * Menu backgrounds in the launcher's look: outside a world the dark wall of night blue blocks instead of the dirt texture,
 * inside a world a night blue veil over the game instead of the grey one.
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {
	@Inject(method = "extractMenuBackgroundTexture", at = @At("HEAD"), cancellable = true)
	private static void yahu$menuBackground(GuiGraphicsExtractor g, Identifier texture, int x, int y, float u, float v, int w, int h, CallbackInfo ci) {
		if (Minecraft.getInstance().level == null) {
			BlockWidgets.wall(g, x, y, w, h);
		} else {
			BlockWidgets.veil(g, x, y, w, h);
		}
		ci.cancel();
	}

	// the title screen's spinning panorama makes way for the dark Yahu background
	@Inject(method = "extractPanorama", at = @At("HEAD"), cancellable = true)
	private void yahu$panorama(GuiGraphicsExtractor g, float alpha, CallbackInfo ci) {
		BlockWidgets.wall(g, 0, 0, g.guiWidth(), g.guiHeight());
		ci.cancel();
	}

	@Inject(method = "extractTransparentBackground", at = @At("HEAD"), cancellable = true)
	private void yahu$transparentBackground(GuiGraphicsExtractor g, CallbackInfo ci) {
		BlockWidgets.veil(g, 0, 0, g.guiWidth(), g.guiHeight());
		ci.cancel();
	}
}
