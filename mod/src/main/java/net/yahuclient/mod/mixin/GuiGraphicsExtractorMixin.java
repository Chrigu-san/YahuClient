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

	// the glass panes show the frosted backdrop only in frames where Minecraft blurs (see Backdrop)
	@Inject(method = "blurBeforeThisStratum", at = @At("HEAD"))
	private void yahu$blurThisFrame(CallbackInfo ci) {
		net.yahuclient.mod.ui.Backdrop.markBlur();
	}
}
