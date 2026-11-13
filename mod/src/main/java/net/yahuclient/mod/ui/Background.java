package net.yahuclient.mod.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The background of the Yahu menu and of Minecraft's menus outside a world: near black, with three large soft lights
 * (violet, cyan, a little magenta) drifting very slowly on their own paths, fine grain on top and darker edges. Slow on
 * purpose: it should feel alive without pulling the eye.
 */
public final class Background {
	private static final long START = System.nanoTime();

	private Background() {
	}

	private static double seconds() {
		return (System.nanoTime() - START) / 1e9;
	}

	/** The full background, for menus outside a world. */
	public static void draw(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, Theme.BG);
		lights(g, x, y, w, h, 1f);
		Draw.grain(g, x, y, w, h, 0.035f);
		vignette(g, x, y, w, h);
	}

	/** Over the game: the world stays visible under a dark veil, with the lights faint on top. */
	public static void veil(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, Theme.alpha(Theme.BG, 0.74f));
		lights(g, x, y, w, h, 0.55f);
		vignette(g, x, y, w, h);
	}

	/**
	 * The tint over a blurred background, for the glass look: a soft violet to teal wash with the slow lights in it, so
	 * the frosted panes have color to pick up. Lighter in a world, so the game still shows.
	 */
	public static void tint(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean inWorld) {
		// a deep blue night over the blurred picture: it keeps the shapes and light but drowns its colors (the title
		// panorama is warm orange), so nothing behind the glass shouts; a little darker towards the edges
		float k = inWorld ? 0.62f : 0.84f;
		g.fillGradient(x, y, x + w, y + h, Theme.alpha(0xFF0B1220, k), Theme.alpha(0xFF070B14, Math.min(1, k + 0.06f)));
		int band = Math.max(24, h / 4);
		g.fillGradient(x, y, x + w, y + band, Theme.alpha(0xFF000000, 0.25f), 0x00000000);
		g.fillGradient(x, y + h - band, x + w, y + h, 0x00000000, Theme.alpha(0xFF000000, 0.35f));
	}

	private static void lights(GuiGraphicsExtractor g, int x, int y, int w, int h, float strength) {
		double t = seconds();
		float size = Math.max(w, h);
		// each light has its own slow loop (periods of about 40 to 70 seconds), so the picture never repeats quickly
		light(g, x + w * (0.22f + 0.10f * (float) Math.sin(t * 0.11)), y + h * (0.30f + 0.12f * (float) Math.cos(t * 0.09)), size * 0.55f,
				Theme.alpha(Theme.ACCENT, 0.30f * strength));
		light(g, x + w * (0.80f + 0.08f * (float) Math.cos(t * 0.13 + 1)), y + h * (0.68f + 0.10f * (float) Math.sin(t * 0.10 + 2)), size * 0.50f,
				Theme.alpha(Theme.ACCENT_2, 0.20f * strength));
		light(g, x + w * (0.55f + 0.15f * (float) Math.sin(t * 0.07 + 4)), y + h * (0.05f + 0.06f * (float) Math.cos(t * 0.12 + 3)), size * 0.40f,
				Theme.alpha(0xFFE05CC8, 0.12f * strength));
	}

	private static void light(GuiGraphicsExtractor g, float cx, float cy, float radius, int color) {
		Draw.glow(g, cx, cy, radius, color);
	}

	private static void vignette(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		int band = Math.max(24, h / 5);
		g.fillGradient(x, y, x + w, y + band, Theme.alpha(0xFF000000, 0.55f), 0x00000000);
		g.fillGradient(x, y + h - band, x + w, y + h, 0x00000000, Theme.alpha(0xFF000000, 0.65f));
	}
}
