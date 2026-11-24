package net.yahuclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.yahuclient.mod.ui.GlassFx;

/**
 * Icon buttons (language, accessibility, skins, friends...): their glass is drawn in AbstractButtonMixin and its warp left
 * open, so the icon drawn after it leans with the glass; here, when the button is done, the warp is closed.
 */
@Mixin({SpriteIconButton.CenteredIcon.class, SpriteIconButton.TextAndIcon.class})
public abstract class SpriteIconButtonMixin {
	@Inject(method = "extractContents", at = @At("RETURN"))
	private void yahu$closeWarp(GuiGraphicsExtractor g, int mouseX, int mouseY, float a, CallbackInfo ci) {
		if (GlassFx.iconPending) {
			GlassFx.iconPending = false;
			GlassFx.end();
		}
	}
}
