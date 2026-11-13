package net.yahuclient.mod.mixin;

import java.util.function.Supplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;

import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.renderer.texture.AbstractTexture;

/**
 * Minecraft samples every font texture with nearest filtering, made for its pixel font. Our fonts are smooth TTF fonts
 * rasterized larger than shown (oversample): sampled nearest they get jagged when drawn smaller, so the glyph sheets of
 * the Yahu fonts (named "yahu:...") are sampled linearly instead. Minecraft's own fonts stay as they are.
 */
@Mixin(FontTexture.class)
public abstract class FontTextureMixin extends AbstractTexture {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void yahu$smooth(Supplier<String> label, GlyphRenderTypes renderTypes, boolean colored, CallbackInfo ci) {
		// the pixel font stays sharp-edged on purpose
		if (label.get().startsWith("yahu:") && !label.get().startsWith("yahu:pixel")) {
			this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		}
	}
}
