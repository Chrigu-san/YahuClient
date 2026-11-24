package net.yahuclient.mod.mixin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.renderer.texture.CubeMapTexture;
import net.minecraft.client.renderer.texture.MipmapStrategy;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.server.packs.resources.ResourceManager;
import net.yahuclient.mod.YahuMod;
import net.yahuclient.mod.ui.ClassicPanorama;

/**
 * The title panorama from the black and white 1.18 faces (ClassicPanorama) when they are there, stacked the way Minecraft
 * stacks its own (the same face order); otherwise Minecraft's own panorama.
 */
@Mixin(CubeMapTexture.class)
public abstract class CubeMapTextureMixin {
	// Minecraft's order of the six faces in the stacked picture
	private static final int[] ORDER = {1, 3, 5, 4, 0, 2};

	@Inject(method = "loadContents", at = @At("HEAD"), cancellable = true)
	private void yahu$classicPanorama(ResourceManager resources, CallbackInfoReturnable<TextureContents> cir) throws IOException {
		CubeMapTexture self = (CubeMapTexture) (Object) this;
		if (!ClassicPanorama.PANORAMA.equals(self.resourceId()) || !YahuMod.config().menu.classicPanorama || !net.yahuclient.mod.ui.Look.on() || !ClassicPanorama.ready()) {
			return;
		}
		NativeImage stacked = null;
		int width = 0, height = 0;
		for (int i = 0; i < 6; i++) {
			try (InputStream in = Files.newInputStream(ClassicPanorama.face(ORDER[i])); NativeImage part = NativeImage.read(in)) {
				if (stacked == null) {
					width = part.getWidth();
					height = part.getHeight();
					stacked = new NativeImage(width, height * 6, false);
				} else if (part.getWidth() != width || part.getHeight() != height) {
					stacked.close();
					return;  // something is off with the files: keep Minecraft's own
				}
				part.copyRect(stacked, 0, 0, 0, i * height, width, height, false, true);
			}
		}
		ClassicPanorama.ours = stacked;
		cir.setReturnValue(new TextureContents(stacked, new TextureMetadataSection(true, false, MipmapStrategy.MEAN, 0.0F)));
	}
}
