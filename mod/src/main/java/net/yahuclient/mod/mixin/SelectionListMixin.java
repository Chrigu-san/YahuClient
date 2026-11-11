package net.yahuclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.network.chat.Component;
import net.yahuclient.mod.ui.Draw;
import net.yahuclient.mod.ui.Theme;

/**
 * Lists (worlds, servers, resource packs...): a soft dark panel instead of the dirt or dark texture, hairlines instead of the
 * pixel separators, and the selected entry as a rounded card with an accent edge instead of a white frame.
 */
@Mixin(AbstractSelectionList.class)
public abstract class SelectionListMixin extends AbstractWidget {
	private SelectionListMixin(int x, int y, int width, int height, Component message) {
		super(x, y, width, height, message);
	}

	@Inject(method = "extractListBackground", at = @At("HEAD"), cancellable = true)
	private void yahu$background(GuiGraphicsExtractor g, CallbackInfo ci) {
		g.fill(getX(), getY(), getRight(), getBottom(), Theme.alpha(0xFF000000, 0.14f));
		ci.cancel();
	}

	@Inject(method = "extractListSeparators", at = @At("HEAD"), cancellable = true)
	private void yahu$separators(GuiGraphicsExtractor g, CallbackInfo ci) {
		g.fill(getX(), getY() - 1, getRight(), getY(), Theme.BORDER);
		g.fill(getX(), getBottom(), getRight(), getBottom() + 1, Theme.BORDER);
		ci.cancel();
	}

	@Inject(method = "extractSelection", at = @At("HEAD"), cancellable = true)
	private void yahu$selection(GuiGraphicsExtractor g, @Coerce LayoutElement entry, int outlineColor, CallbackInfo ci) {
		int x = entry.getX(), y = entry.getY(), w = entry.getWidth(), h = entry.getHeight();
		net.yahuclient.mod.ui.Glass.pane(g, x, y, w, h, 5, 0x2AFFFFFF, isFocused() ? 0x90FFFFFF : 0x50FFFFFF, 1f);
		ci.cancel();
	}
}
