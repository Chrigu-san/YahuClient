package net.yahuclient.mod.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Shapes for the Yahu UI, drawn in real screen pixels rather than GUI pixels (one GUI pixel is 2 to 4 screen pixels): edges
 * land on exact pixels, outlines are one screen pixel thin and round corners use a disk made for exactly their size, so
 * nothing looks stepped or blurry at any GUI scale. Positions and sizes are still given in GUI units.
 */
public final class Draw {
	private Draw() {
	}

	/** Screen pixels per GUI pixel. */
	public static float scale() {
		return (float) Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
	}

	/** Draws in screen pixels until end(). */
	private static float begin(GuiGraphicsExtractor g) {
		float s = scale();
		g.pose().pushMatrix();
		g.pose().scale(1f / s, 1f / s);
		return s;
	}

	private static void end(GuiGraphicsExtractor g) {
		g.pose().popMatrix();
	}

	/** A filled rounded rectangle. */
	public static void round(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int color) {
		if (w <= 0 || h <= 0 || (color >>> 24) == 0) {
			return;
		}
		float s = begin(g);
		int x0 = Math.round(x * s), y0 = Math.round(y * s), x1 = Math.round((x + w) * s), y1 = Math.round((y + h) * s);
		int r = Math.round(Math.max(0, Math.min(radius * s, Math.min(x1 - x0, y1 - y0) / 2f)));
		if (r == 0) {
			g.fill(x0, y0, x1, y1, color);
		} else {
			g.fill(x0 + r, y0, x1 - r, y1, color);
			g.fill(x0, y0 + r, x0 + r, y1 - r, color);
			g.fill(x1 - r, y0 + r, x1, y1 - r, color);
			Identifier disk = Tex.disk(2 * r);
			quarter(g, disk, x0, y0, r, 0, 0, color);
			quarter(g, disk, x1 - r, y0, r, r, 0, color);
			quarter(g, disk, x0, y1 - r, r, 0, r, color);
			quarter(g, disk, x1 - r, y1 - r, r, r, r, color);
		}
		end(g);
	}

	/** A one screen pixel outline of a rounded rectangle (lines and thin ring corners, so it never adds up under glass). */
	public static void outline(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int color) {
		outline(g, x, y, w, h, radius, color, color);
	}

	/** An outline whose top edge (and top corners) has its own color: light catching the top of a pane. */
	public static void outline(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int color, int top) {
		if (w <= 0 || h <= 0 || ((color | top) >>> 24) == 0) {
			return;
		}
		float s = begin(g);
		int x0 = Math.round(x * s), y0 = Math.round(y * s), x1 = Math.round((x + w) * s), y1 = Math.round((y + h) * s);
		int r = Math.round(Math.max(0, Math.min(radius * s, Math.min(x1 - x0, y1 - y0) / 2f)));
		int edge = Math.max(r, 1);
		g.fill(x0 + r, y0, x1 - r, y0 + 1, top);
		g.fill(x0 + r, y1 - 1, x1 - r, y1, color);
		// the sides fade from the top color into the side color over the upper half
		g.fillGradient(x0, y0 + edge, x0 + 1, (y0 + y1) / 2, top, color);
		g.fillGradient(x1 - 1, y0 + edge, x1, (y0 + y1) / 2, top, color);
		g.fill(x0, (y0 + y1) / 2, x0 + 1, y1 - edge, color);
		g.fill(x1 - 1, (y0 + y1) / 2, x1, y1 - edge, color);
		if (r > 0) {
			Identifier ring = Tex.ring(2 * r);
			quarter(g, ring, x0, y0, r, 0, 0, top);
			quarter(g, ring, x1 - r, y0, r, r, 0, top);
			quarter(g, ring, x0, y1 - r, r, 0, r, color);
			quarter(g, ring, x1 - r, y1 - r, r, r, r, color);
		}
		end(g);
	}

	private static void quarter(GuiGraphicsExtractor g, Identifier tex, int x, int y, int r, int u, int v, int color) {
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, u, v, r, r, r, r, 2 * r, 2 * r, color);
	}

	/** A rounded rectangle with a hairline border. */
	public static void card(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int fill, int border) {
		round(g, x, y, w, h, radius, fill);
		outline(g, x, y, w, h, radius, border);
	}

	public static void circle(GuiGraphicsExtractor g, float cx, float cy, float radius, int color) {
		if ((color >>> 24) == 0) {
			return;
		}
		float s = begin(g);
		int d = Math.max(1, Math.round(radius * 2 * s));
		int x = Math.round(cx * s - d / 2f), y = Math.round(cy * s - d / 2f);
		g.blit(RenderPipelines.GUI_TEXTURED, Tex.disk(d), x, y, 0, 0, d, d, d, d, d, d, color);
		end(g);
	}

	/** A line one screen pixel high, from x0 to x1 (GUI units). */
	public static void hairline(GuiGraphicsExtractor g, float x0, float x1, float y, int color) {
		float s = begin(g);
		int yy = Math.round(y * s);
		g.fill(Math.round(x0 * s), yy, Math.round(x1 * s), yy + 1, color);
		end(g);
	}

	/** A soft light centered on (cx, cy). */
	public static void glow(GuiGraphicsExtractor g, float cx, float cy, float radius, int color) {
		Tex.ensure();
		int d = Math.max(1, Math.round(radius * 2));
		g.blit(RenderPipelines.GUI_TEXTURED, Tex.GLOW, Math.round(cx - radius), Math.round(cy - radius), 0, 0, d, d, Tex.GLOW_SIZE, Tex.GLOW_SIZE,
				Tex.GLOW_SIZE, Tex.GLOW_SIZE, color);
	}

	/**
	 * A soft shadow around a box, a little lower than it (light from above). Only outside the box: under see-through glass
	 * a shadow inside would darken the pane.
	 */
	public static void shadow(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, float strength) {
		if (strength <= 0.01f) {
			return;
		}
		Identifier tex = Tex.shadow();
		int color = Theme.alpha(0xFF000000, 0.32f * strength);
		float s = begin(g);
		// the box itself, a little lower: the shadow starts at the pane's edge and only spreads outwards
		int x0 = Math.round(x * s), y0 = Math.round((y + 2) * s);
		int x1 = Math.round((x + w) * s), y1 = Math.round((y + h + 2) * s);
		int b = Math.round(12 * s);
		int e = Tex.SHADOW_EDGE, size = Tex.SHADOW_SIZE, mid = size - 2 * e;
		int cw = Math.max(0, x1 - x0), ch = Math.max(0, y1 - y0);
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x0 - b, y0 - b, 0, 0, b, b, e, e, size, size, color);
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x1, y0 - b, e + mid, 0, b, b, e, e, size, size, color);
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x0 - b, y1, 0, e + mid, b, b, e, e, size, size, color);
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x1, y1, e + mid, e + mid, b, b, e, e, size, size, color);
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x0, y0 - b, e, 0, cw, b, mid, e, size, size, color);
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x0, y1, e, e + mid, cw, b, mid, e, size, size, color);
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x0 - b, y0, 0, e, b, ch, e, mid, size, size, color);
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x1, y0, e + mid, e, b, ch, e, mid, size, size, color);
		end(g);
	}

	/** Fine grain over a rectangle, very faint. */
	public static void grain(GuiGraphicsExtractor g, int x, int y, int w, int h, float alpha) {
		Tex.ensure();
		g.blit(RenderPipelines.GUI_TEXTURED, Tex.GRAIN, x, y, 0, 0, w, h, w, h, Tex.GRAIN_SIZE, Tex.GRAIN_SIZE, Theme.alpha(0xFFFFFFFF, alpha));
	}

	/** A pill (fully round ends). */
	public static void pill(GuiGraphicsExtractor g, float x, float y, float w, float h, int color) {
		round(g, x, y, w, h, Math.min(w, h) / 2f, color);
	}
}
