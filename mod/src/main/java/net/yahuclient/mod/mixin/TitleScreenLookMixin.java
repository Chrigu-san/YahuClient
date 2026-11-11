package net.yahuclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LogoRenderer;
import net.yahuclient.mod.ui.Draw;
import net.yahuclient.mod.ui.Theme;

/** The title screen shows the Yahu word mark instead of the Minecraft logo. */
@Mixin(LogoRenderer.class)
public abstract class TitleScreenLookMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IFI)V", at = @At("HEAD"), cancellable = true)
	private void yahu$wordMark(GuiGraphicsExtractor g, int width, float alpha, int heightOffset, CallbackInfo ci) {
		var font = net.minecraft.client.Minecraft.getInstance().font;
		var mark = Theme.display("YAHU");
		float scale = 3.2f;
		float cx = width / 2f, top = heightOffset - 6;
		Draw.glow(g, cx, top + 16, 95, Theme.alpha(Theme.ACCENT, 0.22f * alpha));
		g.pose().pushMatrix();
		g.pose().translate(cx - font.width(mark) * scale / 2f, top);
		g.pose().scale(scale, scale);
		g.text(font, mark, 0, 0, Theme.alpha(0xFFFFFFFF, alpha), false);
		g.pose().popMatrix();
		// "CLIENT" spaced out underneath, in the accent
		var sub = Theme.ui("C L I E N T");
		g.pose().pushMatrix();
		g.pose().translate(cx - font.width(sub) * 1.1f / 2f, top + 9 * scale + 4);
		g.pose().scale(1.1f, 1.1f);
		g.text(font, sub, 0, 0, Theme.alpha(Theme.ACCENT_HI, alpha), false);
		g.pose().popMatrix();
		ci.cancel();
	}
}
