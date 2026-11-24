package net.yahuclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.yahuclient.mod.inventory.InventoryLook;

/** Container titles are dark grey for Minecraft's light panels: light on the Yahu panels (InventoryLook). */
@Mixin({AbstractContainerScreen.class, InventoryScreen.class})
public abstract class ContainerLabelsMixin {
	@ModifyArg(method = "extractLabels", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"),
			index = 4)
	private int yahu$labelColor(int color) {
		return InventoryLook.labelColor(color);
	}
}
