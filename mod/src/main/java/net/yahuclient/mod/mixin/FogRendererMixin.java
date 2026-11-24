package net.yahuclient.mod.mixin;

import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import net.yahuclient.mod.YahuMod;
import net.yahuclient.mod.skybox.Skybox;

/**
 * Under a custom sky the fog takes the sky's color at the horizon, so the far land fades into it instead of into
 * Minecraft's blue (which showed as a light line between the ground and the sky). Under water, lava or snow the fog stays.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
	@Shadow
	private FogType getFogType(Camera camera) {
		throw new AssertionError();
	}

	@Inject(method = "computeFogColor", at = @At("TAIL"))
	private void yahu$skyFog(Camera camera, float partialTicks, ClientLevel level, int renderDistance, float darken, Vector4f dest, CallbackInfo ci) {
		FogType type = getFogType(camera);
		if (type != FogType.ATMOSPHERIC && type != FogType.NONE) {
			return;
		}
		float[] c = Skybox.fogColor(YahuMod.config().skybox);
		if (c != null) {
			dest.set(c[0], c[1], c[2], 1f);
		}
	}
}
