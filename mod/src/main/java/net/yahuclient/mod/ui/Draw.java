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
		if (Warp.current() != null) {
			roundWarped(Warp.current(), g, x, y, w, h, radius, color, color);
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

	/** A filled rounded rectangle whose color runs from top to bottom. */
	public static void roundGradient(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int top, int bottom) {
		if (w <= 0 || h <= 0) {
			return;
		}
		if (Warp.current() != null) {
			roundWarped(Warp.current(), g, x, y, w, h, radius, top, bottom);
			return;
		}
		float s = begin(g);
		int x0 = Math.round(x * s), y0 = Math.round(y * s), x1 = Math.round((x + w) * s), y1 = Math.round((y + h) * s);
		int r = Math.round(Math.max(0, Math.min(radius * s, Math.min(x1 - x0, y1 - y0) / 2f)));
		int hh = Math.max(1, y1 - y0);
		java.util.function.IntUnaryOperator at = yy -> Theme.mix(top, bottom, (yy - y0) / (float) hh);
		g.fillGradient(x0 + r, y0, x1 - r, y1, top, bottom);
		if (r > 0) {
			g.fillGradient(x0, y0 + r, x0 + r, y1 - r, at.applyAsInt(y0 + r), at.applyAsInt(y1 - r));
			g.fillGradient(x1 - r, y0 + r, x1, y1 - r, at.applyAsInt(y0 + r), at.applyAsInt(y1 - r));
			Identifier disk = Tex.disk(2 * r);
			quarter(g, disk, x0, y0, r, 0, 0, at.applyAsInt(y0 + r / 2));
			quarter(g, disk, x1 - r, y0, r, r, 0, at.applyAsInt(y0 + r / 2));
			quarter(g, disk, x0, y1 - r, r, 0, r, at.applyAsInt(y1 - r / 2));
			quarter(g, disk, x1 - r, y1 - r, r, r, r, at.applyAsInt(y1 - r / 2));
		}
		end(g);
	}

	/** A rounded rectangle under a warp: quads for the body, disk quarters for the corners, all projected. */
	private static void roundWarped(Warp wp, GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int top, int bottom) {
		float s = scale();
		float r = Math.max(0, Math.min(radius, Math.min(w, h) / 2f));
		java.util.function.DoubleUnaryOperator t = yy -> (yy - y) / h;
		int cTop = top, cBot = bottom;
		java.util.function.DoubleFunction<Integer> at = yy -> Theme.mix(cTop, cBot, (float) Math.max(0, Math.min(1, t.applyAsDouble(yy))));
		wp.quad(g, x + r, y, x + w - r, y + h, top, bottom);
		if (r > 0) {
			wp.quad(g, x, y + r, x + r, y + h - r, at.apply(y + r), at.apply(y + h - r));
			wp.quad(g, x + w - r, y + r, x + w, y + h - r, at.apply(y + r), at.apply(y + h - r));
			int d = Math.max(2, Math.round(2 * r * s));
			Identifier disk = Tex.disk(d);
			wp.texture(g, disk, x, y, x + r, y + r, 0, 0, .5f, .5f, at.apply(y + r / 2));
			wp.texture(g, disk, x + w - r, y, x + w, y + r, .5f, 0, 1, .5f, at.apply(y + r / 2));
			wp.texture(g, disk, x, y + h - r, x + r, y + h, 0, .5f, .5f, 1, at.apply(y + h - r / 2));
			wp.texture(g, disk, x + w - r, y + h - r, x + w, y + h, .5f, .5f, 1, 1, at.apply(y + h - r / 2));
		}
	}

	/** An outline under a warp: thin projected quads and ring quarters. */
	private static void outlineWarped(Warp wp, GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int color, int top) {
		float s = scale();
		float px = 1f / s;
		float r = Math.max(0, Math.min(radius, Math.min(w, h) / 2f));
		wp.quad(g, x + r, y, x + w - r, y + px, top, top);
		wp.quad(g, x + r, y + h - px, x + w - r, y + h, color, color);
		wp.quad(g, x, y + Math.max(r, px), x + px, y + h - Math.max(r, px), top, color);
		wp.quad(g, x + w - px, y + Math.max(r, px), x + w, y + h - Math.max(r, px), top, color);
		if (r > 0) {
			int d = Math.max(2, Math.round(2 * r * s));
			Identifier ring = Tex.ring(d);
			wp.texture(g, ring, x, y, x + r, y + r, 0, 0, .5f, .5f, top);
			wp.texture(g, ring, x + w - r, y, x + w, y + r, .5f, 0, 1, .5f, top);
			wp.texture(g, ring, x, y + h - r, x + r, y + h, 0, .5f, .5f, 1, color);
			wp.texture(g, ring, x + w - r, y + h - r, x + w, y + h, .5f, .5f, 1, 1, color);
		}
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
		if (Warp.current() != null) {
			outlineWarped(Warp.current(), g, x, y, w, h, radius, color, top);
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
		Warp wp = Warp.current();
		if (wp != null) {
			// small and round: placed where its middle lands, shrunk with distance
			float[] p = wp.project(cx, cy);
			Warp.flat(() -> circle(g, p[0], p[1], radius * p[2], color));
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
		Warp wp = Warp.current();
		if (wp != null) {
			float[] p = wp.project(cx, cy);
			Warp.flat(() -> glow(g, p[0], p[1], radius * p[2], color));
			return;
		}
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
