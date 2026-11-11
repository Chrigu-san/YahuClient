package net.yahuclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.util.FormattedCharSequence;
import net.yahuclient.mod.ui.MenuFont;

/**
 * Menu text is recorded first and drawn later, so the font has to be fixed when it is recorded: text added while a screen
 * draws gets the Yahu UI font wherever it doesn't ask for a font of its own.
 */
@Mixin(GuiTextRenderState.class)
public abstract class GuiTextRenderStateMixin {
	@ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
	private static FormattedCharSequence yahu$menuFont(FormattedCharSequence text) {
		return MenuFont.active() ? MenuFont.wrap(text) : text;
	}
}
