package net.yahuclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.yahuclient.mod.ui.ModernSprites;

/**
 * Minecraft draws its widgets (text fields, checkboxes, scroll bars, tabs, buttons) from named sprites; every way of drawing
 * one ends in this method. Known widget sprites are swapped for the dark Yahu look here, so other mods' widgets that use
 * the same sprites match too. Everything else (icons, items) is drawn as usual.
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin {
	@Inject(method = "blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V", at = @At("HEAD"),
			cancellable = true)
	private void yahu$modernSprite(RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height, int color, CallbackInfo ci) {
		if (ModernSprites.draw((GuiGraphicsExtractor) (Object) this, sprite, x, y, width, height, color)) {
			ci.cancel();
		}
	}

	// the simple container panels (inventory, chests, ...) are redrawn by the inventory mod instead of their texture
	@Inject(method = "blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V", at = @At("HEAD"),
			cancellable = true)
	private void yahu$containerPanel(RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v, int width, int height, int srcWidth,
			int srcHeight, int textureWidth, int textureHeight, int color, CallbackInfo ci) {
		if (net.yahuclient.mod.inventory.InventoryLook.replaceContainerTexture((GuiGraphicsExtractor) (Object) this, texture, x, y)) {
			ci.cancel();
		}
	}
}