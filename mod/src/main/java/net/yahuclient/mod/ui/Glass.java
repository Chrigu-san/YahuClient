package net.yahuclient.mod.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Frosted glass: milky, see-through surfaces over the blurred game (or the blurred title panorama). A faint white fill, a
 * thin light edge that is brighter along the top, as if light hits it from above, and a soft shadow under bigger panels.
 * Everything in the Yahu look is built from these.
 */
public final class Glass {
	public static final int FILL = 0x1CFFFFFF;
	public static final int FILL_HOT = 0x34FFFFFF;
	public static final int FILL_DIM = 0x0CFFFFFF;
	public static final int FILL_DARK = 0x30000000;   // text fields: a darker pane
	public static final int EDGE = 0x2EFFFFFF;
	public static final int EDGE_HOT = 0x80FFFFFF;
	public static final int TINT = 0xFF8B6CFF;        // the accent, for tinted glass

	private Glass() {
	}

	/** A glass pane: fill, a light edge, a shine along the top. */
	public static void pane(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int fill, int edge, float alpha) {
		Draw.round(g, x, y, w, h, r, Theme.alpha(fill, alpha));
		outline(g, x, y, w, h, r, Theme.alpha(edge, alpha));
		// the top edge catches the light
		int x0 = Math.round(x + r), x1 = Math.round(x + w - r), y0 = Math.round(y);
		if (x1 > x0) {
			g.fill(x0, y0, x1, y0 + 1, Theme.alpha(0x30FFFFFF, alpha));
		}
	}

	/** A big floating panel: a pane with a soft shadow under it. */
	public static void panel(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, float alpha) {
		Draw.shadow(g, x, y, w, h, r, 0.7f * alpha);
		pane(g, x, y, w, h, r, 0x22FFFFFF, 0x3AFFFFFF, alpha);
	}

	/** A glass button. */
	public static void button(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean active, boolean hot, float alpha) {
		if (!active) {
			pane(g, x, y, w, h, 5, FILL_DIM, 0x14FFFFFF, alpha);
		} else {
			pane(g, x, y, w, h, 5, hot ? FILL_HOT : FILL, hot ? EDGE_HOT : EDGE, alpha);
		}
	}

	/** A glass button tinted in the accent: our own entries. */
	public static void tinted(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hot, float alpha) {
		pane(g, x, y, w, h, 5, Theme.alpha(TINT, hot ? 0.75f : 0.55f), hot ? 0xA0FFFFFF : 0x50FFFFFF, alpha);
	}

	/**
	 * A thin outline of a rounded rectangle, drawn as lines so it doesn't add up under a see-through fill (a filled
	 * border shape would show through the glass).
	 */
	public static void outline(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int color) {
		if ((color >>> 24) == 0) {
			return;
		}
		int x0 = Math.round(x), y0 = Math.round(y), x1 = Math.round(x + w), y1 = Math.round(y + h);
		int ri = Math.round(Math.max(0, Math.min(r, Math.min(w, h) / 2f)));
		g.fill(x0 + ri, y0, x1 - ri, y0 + 1, color);
		g.fill(x0 + ri, y1 - 1, x1 - ri, y1, color);
		g.fill(x0, y0 + ri, x0 + 1, y1 - ri, color);
		g.fill(x1 - 1, y0 + ri, x1, y1 - ri, color);
		if (ri <= 0) {
			return;
		}
		// the corners: single pixels along a quarter circle
		int steps = ri + 1;
		int lastDx = -1, lastDy = -1;
		for (int i = 1; i < steps; i++) {
			double a = Math.PI / 2 * i / steps;
			int dx = (int) Math.round(ri - ri * Math.cos(a));
			int dy = (int) Math.round(ri - ri * Math.sin(a));
			if (dx == lastDx && dy == lastDy) {
				continue;
			}
			lastDx = dx;
			lastDy = dy;
			g.fill(x0 + dx, y0 + dy, x0 + dx + 1, y0 + dy + 1, color);
			g.fill(x1 - dx - 1, y0 + dy, x1 - dx, y0 + dy + 1, color);
			g.fill(x0 + dx, y1 - dy - 1, x0 + dx + 1, y1 - dy, color);
			g.fill(x1 - dx - 1, y1 - dy - 1, x1 - dx, y1 - dy, color);
		}
	}
}
