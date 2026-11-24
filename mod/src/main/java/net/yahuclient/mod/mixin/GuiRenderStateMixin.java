package net.yahuclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.state.gui.GuiRenderState;

/**
 * Minecraft can blur the menu background only once per frame and crashes on a second blur. Our main menu blurs its own
 * panorama; a screen opened over it (the Friends overlay) blurs again. A second blur in the same frame is skipped instead:
 * everything under it is blurred already.
 */
@Mixin(GuiRenderState.class)
public abstract class GuiRenderStateMixin {
	@Shadow
	private int firstStratumAfterBlur;

	@Inject(method = "blurBeforeThisStratum", at = @At("HEAD"), cancellable = true)
	private void yahu$blurOnce(CallbackInfo ci) {
		if (firstStratumAfterBlur != Integer.MAX_VALUE) {
			ci.cancel();
		}
	}
}
