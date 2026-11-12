package net.yahuclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.CloudRenderer;
import net.yahuclient.mod.YahuMod;
import net.yahuclient.mod.skybox.Skybox;

/** Under a custom sky the clouds can be turned off (Skybox mod, "Clouds"): both ways Minecraft draws them are skipped. */
@Mixin(CloudRenderer.class)
public abstract class CloudRendererMixin {
	@Inject(method = {"render(Lnet/minecraft/client/CloudStatus;Lcom/mojang/renderpearl/api/commands/RenderPass;)V", "renderOit"}, at = @At("HEAD"),
			cancellable = true)
	private void yahu$noClouds(CallbackInfo ci) {
		if (Skybox.hideClouds(YahuMod.config().skybox)) {
			ci.cancel();
		}
	}
}
