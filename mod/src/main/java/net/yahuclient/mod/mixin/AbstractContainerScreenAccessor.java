package net.yahuclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/** Where a container screen's panel is, for redrawing it (InventoryLook). */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("leftPos")
	int yahu$leftPos();

	@Accessor("topPos")
	int yahu$topPos();

	@Accessor("imageWidth")
	int yahu$imageWidth();

	@Accessor("imageHeight")
	int yahu$imageHeight();
}
