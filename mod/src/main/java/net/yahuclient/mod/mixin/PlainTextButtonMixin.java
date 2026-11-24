package net.yahuclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlainTextButton;
import net.yahuclient.mod.ui.TitleLayout;

/** The copyright line in the bottom right corner of the main menu: small, thin and faint (still a link to the credits). */
@Mixin(PlainTextButton.class)
public abstract class PlainTextButtonMixin {
	@Inject(method = "extractContents", at = @At("HEAD"), cancellable = true)
	private void yahu$quietCorner(GuiGraphicsExtractor g, int mouseX, int mouseY, float a, CallbackInfo ci) {
		PlainTextButton self = (PlainTextButton) (Object) this;
		if (TitleLayout.onTitle()) {
			TitleLayout.corner(g, self.getMessage().getString(), 4, true, self.isHoveredOrFocused() ? 255 : 200);
			ci.cancel();
		}
	}
}
