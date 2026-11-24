package net.yahuclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.texture.ReloadableTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.yahuclient.mod.ui.ClassicPanorama;

/**
 * Notes which picture the title panorama really shows: ours (black and white 1.18) or Minecraft's own. A resource reload
 * can put Minecraft's back after ours; then the main menu loads ours again (ClassicPanorama.showWhenReady).
 */
@Mixin(ReloadableTexture.class)
public abstract class ReloadableTextureMixin {
	@Inject(method = "apply", at = @At("HEAD"))
	private void yahu$noteShown(TextureContents contents, CallbackInfo ci) {
		if (ClassicPanorama.PANORAMA.equals(((ReloadableTexture) (Object) this).resourceId())) {
			ClassicPanorama.shown = contents.image() == ClassicPanorama.ours;
		}
	}
}
