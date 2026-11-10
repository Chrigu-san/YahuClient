package net.yahuclient.mod.ui;

import java.util.function.IntBinaryOperator;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Small textures the mod draws itself on first use, white with alpha so they can be tinted: a smooth disk (round corners,
 * dots, switch knobs), a soft light (the background's glow, shadows) and fine grain (so dark surfaces don't look flat).
 * They are smooth at any GUI scale because they are drawn larger than they are shown, with linear filtering.
 */
public final class Tex {
	public static final int DISK_SIZE = 256;
	public static final int GLOW_SIZE = 256;
	public static final int GRAIN_SIZE = 128;

	public static final Identifier DISK = Identifier.fromNamespaceAndPath("yahu", "generated/disk");
	public static final Identifier GLOW = Identifier.fromNamespaceAndPath("yahu", "generated/glow");
	public static final Identifier GRAIN = Identifier.fromNamespaceAndPath("yahu", "generated/grain");

	private static boolean ready;

	private Tex() {
	}

	/** A texture with smooth (linear) sampling, clamped at the edges, or repeating for tiles. */
	public static final class Smooth extends DynamicTexture {
		public Smooth(String label, NativeImage image, boolean repeat) {
			super(() -> label, image);
			sampler = repeat ? RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR) : RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		}
	}

	/** Makes the textures once; call before drawing with them. */
	public static void ensure() {
		if (ready) {
			return;
		}
		ready = true;
		var textures = Minecraft.getInstance().getTextureManager();

		// a disk with one pixel of soft edge at full size, so corners stay smooth when drawn small
		textures.register(DISK, new Smooth("yahu disk", image(DISK_SIZE, (x, y) -> {
			double r = DISK_SIZE / 2.0;
			double d = Math.hypot(x + 0.5 - r, y + 0.5 - r);
			double a = Math.max(0, Math.min(1, r - d));
			return argb(a, 0xFFFFFF);
		}), false));

		// a light that fades out smoothly to the edge (a smoothstep of the distance, squared for a soft core)
		textures.register(GLOW, new Smooth("yahu glow", image(GLOW_SIZE, (x, y) -> {
			double r = GLOW_SIZE / 2.0;
			double d = Math.min(1, Math.hypot(x + 0.5 - r, y + 0.5 - r) / r);
			double t = 1 - d;
			double a = t * t * (3 - 2 * t);
			return argb(a * a, 0xFFFFFF);
		}), false));

		// grain: random light and dark specks, tiled over surfaces at a few percent
		java.util.Random random = new java.util.Random(7);
		textures.register(GRAIN, new Smooth("yahu grain", image(GRAIN_SIZE, (x, y) -> {
			double v = random.nextDouble();
			return v > 0.5 ? argb((v - 0.5) * 2, 0xFFFFFF) : argb((0.5 - v) * 2, 0x000000);
		}), true));
	}

	private static NativeImage image(int size, IntBinaryOperator pixel) {
		NativeImage image = new NativeImage(size, size, false);
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				image.setPixel(x, y, pixel.applyAsInt(x, y));
			}
		}
		return image;
	}

	private static int argb(double alpha, int rgb) {
		return ((int) Math.round(Math.max(0, Math.min(1, alpha)) * 255) << 24) | (rgb & 0xFFFFFF);
	}
}
