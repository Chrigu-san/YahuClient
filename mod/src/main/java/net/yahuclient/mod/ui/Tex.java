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

	private static final java.util.Map<Integer, Identifier> DISKS = new java.util.HashMap<>();
	private static final java.util.Map<Integer, Identifier> RINGS = new java.util.HashMap<>();
	public static final int SHADOW_SIZE = 64;
	public static final int SHADOW_EDGE = 28;
	public static final Identifier SHADOW = Identifier.fromNamespaceAndPath("yahu", "generated/shadow");
	private static boolean shadowReady;

	/**
	 * A disk exactly d screen pixels wide, its edge smoothed over one pixel. Made once per size: drawn 1:1, so a corner
	 * is as smooth as the screen allows, at any size (a big disk scaled down gets jagged without mipmaps).
	 */
	public static Identifier disk(int d) {
		int size = Math.max(1, Math.min(512, d));
		return DISKS.computeIfAbsent(size, k -> {
			Identifier id = Identifier.fromNamespaceAndPath("yahu", "generated/disk_" + k);
			Minecraft.getInstance().getTextureManager().register(id, new Smooth("yahu disk " + k, image(k, (x, y) -> {
				double r = k / 2.0;
				double dist = Math.hypot(x + 0.5 - r, y + 0.5 - r);
				return argb(coverage(r - dist), 0xFFFFFF);
			}), false));
			return id;
		});
	}

	/** A ring one screen pixel thick along the edge of a d pixel disk: the corners of hairline outlines. */
	public static Identifier ring(int d) {
		int size = Math.max(2, Math.min(512, d));
		return RINGS.computeIfAbsent(size, k -> {
			Identifier id = Identifier.fromNamespaceAndPath("yahu", "generated/ring_" + k);
			Minecraft.getInstance().getTextureManager().register(id, new Smooth("yahu ring " + k, image(k, (x, y) -> {
				double r = k / 2.0;
				double dist = Math.hypot(x + 0.5 - r, y + 0.5 - r);
				return argb(coverage(r - dist) - coverage(r - 1 - dist), 0xFFFFFF);
			}), false));
			return id;
		});
	}

	/**
	 * A soft shadow for nine-slicing: solid in the middle (SHADOW_EDGE to SIZE - SHADOW_EDGE), fading out smoothly to the
	 * border, so it stretches to any box without steps.
	 */
	public static Identifier shadow() {
		if (!shadowReady) {
			shadowReady = true;
			Minecraft.getInstance().getTextureManager().register(SHADOW, new Smooth("yahu shadow", image(SHADOW_SIZE, (x, y) -> {
				double lo = SHADOW_EDGE, hi = SHADOW_SIZE - SHADOW_EDGE;
				double dx = Math.max(0, Math.max(lo - (x + 0.5), (x + 0.5) - hi));
				double dy = Math.max(0, Math.max(lo - (y + 0.5), (y + 0.5) - hi));
				double t = 1 - Math.min(1, Math.hypot(dx, dy) / SHADOW_EDGE);
				return argb(t * t * (3 - 2 * t) * t, 0x000000);
			}), false));
		}
		return SHADOW;
	}

	/** How much of a pixel an edge at this signed distance covers (0 outside, 1 inside, smooth over one pixel). */
	private static double coverage(double distance) {
		return Math.max(0, Math.min(1, distance + 0.5));
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
