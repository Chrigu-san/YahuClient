package net.yahuclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import net.yahuclient.mod.ui.TitleLayout;

/** The version line in the bottom left corner: small, thin and faint, so it hardly shows. */
@Mixin(TitleScreen.class)
public abstract class TitleScreenCornerMixin {
	@Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"))
	private void yahu$quietVersion(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
		TitleLayout.corner(g, text, 4, false, color >>> 24);
	}
}
