package net.yahuclient.mod.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.yahuclient.mod.ui.MenuFont;

/**
 * While a menu draws, text that doesn't ask for a font of its own comes out in the Yahu UI font instead of Minecraft's
 * pixel font. Text in the world, the chat and the HUD keep the pixel font: the switch is only on while a screen draws.
 */
@Mixin(Style.class)
public abstract class StyleMixin {
	@Shadow
	@Final
	private FontDescription font;

	@Inject(method = "getFont", at = @At("HEAD"), cancellable = true)
	private void yahu$menuFont(CallbackInfoReturnable<FontDescription> cir) {
		if (font == null && MenuFont.active()) {
			cir.setReturnValue(net.yahuclient.mod.ui.Theme.uiFont());
		}
	}
}
