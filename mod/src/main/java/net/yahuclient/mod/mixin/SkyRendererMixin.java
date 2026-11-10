package net.yahuclient.mod.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;

import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.yahuclient.mod.YahuMod;
import net.yahuclient.mod.skybox.Skybox;

/** With the Skybox mod on, the custom sky is drawn in place of the normal one (sky color, sun, moon, stars). */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
	@Shadow
	@Final
	private RenderTarget renderTarget;

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void yahu$customSky(GpuBufferSlice skyFog, SkyRenderState state, CallbackInfo ci) {
		var config = YahuMod.config().skybox;
		if (Skybox.shouldDraw(config, state)) {
			RenderSystem.setShaderFog(skyFog);
			Skybox.render(config, state, renderTarget);
			ci.cancel();
		}
	}
}
