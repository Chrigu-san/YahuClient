package net.yahuclient.mod.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Frosted glass: milky, see-through surfaces over the blurred game (or the blurred title panorama). A faint white fill
 * that is a touch brighter at the top, a hairline edge (one screen pixel) that catches the light along the top and fades
 * down the sides, and a soft shadow under bigger panels. Everything in the Yahu look is built from these.
 */
public final class Glass {
	public static final int FILL = 0x1CFFFFFF;
	public static final int FILL_HOT = 0x30FFFFFF;
	public static final int FILL_DIM = 0x0CFFFFFF;
	public static final int FILL_DARK = 0x2E000000;   // text fields: a darker pane
	public static final int EDGE = 0x24FFFFFF;
	public static final int EDGE_HOT = 0x66FFFFFF;
	public static final int TINT = 0xFF8B6CFF;        // the accent, for tinted glass

	private Glass() {
	}

	/** A glass pane: fill, a sheen over the top half, a hairline edge that is brighter at the top. */
	public static void pane(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int fill, int edge, float alpha) {
		Draw.round(g, x, y, w, h, r, Theme.alpha(fill, alpha));
		int topAlpha = Math.min(255, Math.round((edge >>> 24) * 1.8f));
		int top = Theme.alpha((topAlpha << 24) | (edge & 0xFFFFFF), alpha);
		Draw.outline(g, x, y, w, h, r, Theme.alpha(edge, alpha), top);
	}

	/** A big floating panel: a slightly brighter pane (no drop shadow: under see-through glass it shows as dark edges). */
	public static void panel(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, float alpha) {
		pane(g, x, y, w, h, r, 0x20FFFFFF, 0x30FFFFFF, alpha);
	}

	/** A glass button. */
	public static void button(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean active, boolean hot, float alpha) {
		if (!active) {
			pane(g, x, y, w, h, 6, FILL_DIM, 0x12FFFFFF, alpha);
		} else {
			pane(g, x, y, w, h, 6, hot ? FILL_HOT : FILL, hot ? EDGE_HOT : EDGE, alpha);
		}
	}

	/** A glass button tinted in the accent: our own entries. */
	public static void tinted(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hot, float alpha) {
		pane(g, x, y, w, h, 6, Theme.alpha(TINT, hot ? 0.78f : 0.6f), hot ? 0x90FFFFFF : 0x40FFFFFF, alpha);
	}
}
