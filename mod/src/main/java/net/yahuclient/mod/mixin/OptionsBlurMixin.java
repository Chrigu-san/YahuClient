package net.yahuclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Options;

/**
 * Frosted glass needs a strongly blurred background: menus blur at least at strength 6 (of 10). A player who turned the
 * menu blur off (0) keeps it off.
 */
@Mixin(Options.class)
public abstract class OptionsBlurMixin {
	@Inject(method = "getMenuBackgroundBlurriness", at = @At("RETURN"), cancellable = true)
	private void yahu$frosted(CallbackInfoReturnable<Integer> cir) {
		if (!net.yahuclient.mod.ui.Look.on()) {
			return;
		}
		int value = cir.getReturnValue();
		if (value > 0 && value < 6) {
			cir.setReturnValue(6);
		}
	}
}
