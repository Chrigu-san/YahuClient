package net.yahuclient.mod.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * Shapes for the dark UI: rounded rectangles with smooth corners, thin borders, circles, soft shadows and glows. Corners
 * are quarters of a large smooth disk drawn small, so they stay clean at any GUI scale.
 */
public final class Draw {
	private Draw() {
	}

	/** A filled rounded rectangle. */
	public static void round(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int color) {
		if (w <= 0 || h <= 0 || (color >>> 24) == 0) {
			return;
		}
		Tex.ensure();
		int r = Math.round(Math.max(0, Math.min(radius, Math.min(w, h) / 2f)));
		int x0 = Math.round(x), y0 = Math.round(y), x1 = Math.round(x + w), y1 = Math.round(y + h);
		if (r == 0) {
			g.fill(x0, y0, x1, y1, color);
			return;
		}
		g.fill(x0 + r, y0, x1 - r, y1, color);
		g.fill(x0, y0 + r, x0 + r, y1 - r, color);
		g.fill(x1 - r, y0 + r, x1, y1 - r, color);
		int half = Tex.DISK_SIZE / 2;
		corner(g, x0, y0, r, 0, 0, half, color);
		corner(g, x1 - r, y0, r, half, 0, half, color);
		corner(g, x0, y1 - r, r, 0, half, half, color);
		corner(g, x1 - r, y1 - r, r, half, half, half, color);
	}

	private static void corner(GuiGraphicsExtractor g, int x, int y, int size, int u, int v, int region, int color) {
		g.blit(RenderPipelines.GUI_TEXTURED, Tex.DISK, x, y, u, v, size, size, region, region, Tex.DISK_SIZE, Tex.DISK_SIZE, color);
	}

	/** A rounded rectangle with a border: the border color first, the fill inset by the border width. */
	public static void card(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int fill, int border) {
		round(g, x, y, w, h, radius, border);
		round(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, radius - 1), fill);
	}

	public static void circle(GuiGraphicsExtractor g, float cx, float cy, float radius, int color) {
		Tex.ensure();
		int d = Math.max(1, Math.round(radius * 2));
		g.blit(RenderPipelines.GUI_TEXTURED, Tex.DISK, Math.round(cx - radius), Math.round(cy - radius), 0, 0, d, d, Tex.DISK_SIZE, Tex.DISK_SIZE,
				Tex.DISK_SIZE, Tex.DISK_SIZE, color);
	}

	/** A soft light centered on (cx, cy). */
	public static void glow(GuiGraphicsExtractor g, float cx, float cy, float radius, int color) {
		Tex.ensure();
		int d = Math.max(1, Math.round(radius * 2));
		g.blit(RenderPipelines.GUI_TEXTURED, Tex.GLOW, Math.round(cx - radius), Math.round(cy - radius), 0, 0, d, d, Tex.GLOW_SIZE, Tex.GLOW_SIZE,
				Tex.GLOW_SIZE, Tex.GLOW_SIZE, color);
	}

	/** A soft shadow under a rounded box, a few rings spreading out and fading. */
	public static void shadow(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, float strength) {
		for (int i = 6; i >= 1; i--) {
			round(g, x - i * 1.5f, y - i * 1.5f + 4, w + i * 3, h + i * 3, radius + i * 1.5f, Theme.alpha(0xFF000000, 0.05f * strength));
		}
	}

	/** Fine grain over a rectangle, very faint. */
	public static void grain(GuiGraphicsExtractor g, int x, int y, int w, int h, float alpha) {
		Tex.ensure();
		g.blit(RenderPipelines.GUI_TEXTURED, Tex.GRAIN, x, y, 0, 0, w, h, w, h, Tex.GRAIN_SIZE, Tex.GRAIN_SIZE, Theme.alpha(0xFFFFFFFF, alpha));
	}

	/** A pill (fully round ends). */
	public static void pill(GuiGraphicsExtractor g, float x, float y, float w, float h, int color) {
		round(g, x, y, w, h, h / 2f, color);
	}
}
