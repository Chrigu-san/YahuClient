package net.yahuclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.GameRenderer;
import net.yahuclient.mod.ui.Backdrop;

/** Around Minecraft's menu blur: keeps a frosted copy for the glass panes and the frame around them sharp (see Backdrop). */
@Mixin(GameRenderer.class)
public abstract class GameRendererBlurMixin {
	@Inject(method = "processBlurEffect", at = @At("HEAD"))
	private void yahu$beforeBlur(CallbackInfo ci) {
		Backdrop.beforeBlur(((GameRenderer) (Object) this).mainRenderTarget());
	}

	@Inject(method = "processBlurEffect", at = @At("TAIL"))
	private void yahu$afterBlur(CallbackInfo ci) {
		GameRenderer self = (GameRenderer) (Object) this;
		Backdrop.afterBlur(self, self.mainRenderTarget());
	}
}
