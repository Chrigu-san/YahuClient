package net.yahuclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LogoRenderer;
import net.yahuclient.mod.ui.Icons;
import net.yahuclient.mod.ui.Spaced;
import net.yahuclient.mod.ui.Theme;
import net.yahuclient.mod.ui.TitleLayout;

/** The Yahu logo and word mark instead of the Minecraft logo (main menu, and wherever else Minecraft shows its logo). */
@Mixin(LogoRenderer.class)
public abstract class TitleScreenLookMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IFI)V", at = @At("HEAD"), cancellable = true)
	private void yahu$logo(GuiGraphicsExtractor g, int width, float alpha, int heightOffset, CallbackInfo ci) {
		if (TitleLayout.onTitle()) {
			TitleLayout.drawLogo(g, width, alpha);
		} else {
			int size = TitleLayout.LOGO;
			Icons.draw(g, "logo", Icons.LOGO, (width - size) / 2f, heightOffset, size, Theme.alpha(0xFFFFFFFF, alpha));
			Spaced.draw(g, Theme.display("YAHU CLIENT"), width / 2f, heightOffset + size + 7, 1.2f, Theme.alpha(0xFFFFFFFF, alpha), true);
		}
		ci.cancel();
	}
}
