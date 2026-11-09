package net.yahuclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.yahuclient.mod.ui.BlockWidgets;

/** Every vanilla button (title screen, pause menu, options...) is drawn as a Yahu block instead of the stone sprite. */
@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin extends AbstractWidget.WithInactiveMessage {
	private AbstractButtonMixin(int x, int y, int width, int height, Component message) {
		super(x, y, width, height, message);
	}

	@Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true)
	private void yahu$blockButton(GuiGraphicsExtractor g, CallbackInfo ci) {
		BlockWidgets.button(g, getX(), getY(), width, height, isActive(), isHoveredOrFocused(), alpha);
		ci.cancel();
	}
}
