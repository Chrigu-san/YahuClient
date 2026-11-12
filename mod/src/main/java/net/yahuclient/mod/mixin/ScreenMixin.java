package net.yahuclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import net.yahuclient.mod.ui.Background;
import net.yahuclient.mod.ui.TitleLayout;

/**
 * Menu backgrounds for the glass look: what is behind a menu (the title panorama, or the game) is blurred by Minecraft's
 * own menu blur, and instead of the dirt texture or the grey veil a soft colored tint goes over it. The glass panes and
 * buttons then sit on that.
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {
	@Inject(method = "extractMenuBackgroundTexture", at = @At("HEAD"), cancellable = true)
	private static void yahu$menuBackground(GuiGraphicsExtractor g, Identifier texture, int x, int y, float u, float v, int w, int h, CallbackInfo ci) {
		// small cut-outs (the chosen tab on the create world screen) would show as a darker box: leave them out
		if (h >= 40 || w >= Minecraft.getInstance().getWindow().getGuiScaledWidth() * 0.6f) {
			Background.tint(g, x, y, w, h, Minecraft.getInstance().level != null);
		}
		ci.cancel();
	}

	// screens size their labels while they are built; measure with the UI font then too, or labels get cut short
	@Inject(method = "init(II)V", at = @At("HEAD"))
	private void yahu$initFontOn(int width, int height, CallbackInfo ci) {
		net.yahuclient.mod.ui.MenuFont.begin();
	}

	@Inject(method = "init(II)V", at = @At("RETURN"))
	private void yahu$initFontOff(int width, int height, CallbackInfo ci) {
		net.yahuclient.mod.ui.MenuFont.end();
	}

	// menu text in the Yahu UI font while a screen draws (see MenuFont)
	@Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"))
	private void yahu$fontOn(GuiGraphicsExtractor g, int mouseX, int mouseY, float a, CallbackInfo ci) {
		net.yahuclient.mod.ui.MenuFont.begin();
	}

	@Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("RETURN"))
	private void yahu$fontOff(GuiGraphicsExtractor g, int mouseX, int mouseY, float a, CallbackInfo ci) {
		net.yahuclient.mod.ui.MenuFont.end();
	}

	// the title screen draws its panorama without blur or menu background: blur it here, tint it, and put the glass
	// panel for the main menu on it
	@Inject(method = "extractPanorama", at = @At("TAIL"))
	private void yahu$panorama(GuiGraphicsExtractor g, float alpha, CallbackInfo ci) {
		if ((Object) this instanceof TitleScreen) {
			g.blurBeforeThisStratum();
			Background.tint(g, 0, 0, g.guiWidth(), g.guiHeight(), false);
			TitleLayout.frame();
		}
	}

	@Inject(method = "extractTransparentBackground", at = @At("HEAD"), cancellable = true)
	private void yahu$transparentBackground(GuiGraphicsExtractor g, CallbackInfo ci) {
		Background.tint(g, 0, 0, g.guiWidth(), g.guiHeight(), true);
		ci.cancel();
	}
}
