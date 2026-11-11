package net.yahuclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.yahuclient.mod.ui.Background;

/** The create world screen draws its tab header with a texture of its own: the whole screen gets the glass tint instead. */
@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin {
	@Inject(method = "extractMenuBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V", at = @At("HEAD"), cancellable = true)
	private void yahu$background(GuiGraphicsExtractor g, CallbackInfo ci) {
		Background.tint(g, 0, 0, g.guiWidth(), g.guiHeight(), Minecraft.getInstance().level != null);
		ci.cancel();
	}
}
