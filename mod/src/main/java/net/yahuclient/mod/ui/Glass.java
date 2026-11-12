package net.yahuclient.mod.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Smoked glass over a dark, blurred background: slate fills you can see through, a fine light edge one screen pixel thin,
 * small corners. Quiet on purpose, so nothing in the menu shouts; only our own entries carry a violet tint.
 */
public final class Glass {
	public static final int FILL = 0x7A2A303C;
	public static final int FILL_HOT = 0x9A3A4254;
	public static final int FILL_DIM = 0x50202530;
	public static final int FILL_DARK = 0x8C10131A;   // text fields: a darker pane
	public static final int EDGE = 0x33FFFFFF;
	public static final int EDGE_HOT = 0x80FFFFFF;
	public static final int TINT = 0xFF8B6CFF;        // the accent, for tinted glass
	public static final float RADIUS = 3;

	private Glass() {
	}

	/** A glass pane: the fill and a fine edge, a touch brighter along the top. */
	public static void pane(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int fill, int edge, float alpha) {
		Draw.round(g, x, y, w, h, r, Theme.alpha(fill, alpha));
		int topAlpha = Math.min(255, Math.round((edge >>> 24) * 1.5f));
		int top = Theme.alpha((topAlpha << 24) | (edge & 0xFFFFFF), alpha);
		Draw.outline(g, x, y, w, h, r, Theme.alpha(edge, alpha), top);
	}

	/** A big panel: a darker pane with a soft shadow. */
	public static void panel(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, float alpha) {
		Draw.shadow(g, x, y, w, h, r, 0.6f * alpha);
		pane(g, x, y, w, h, r, 0xB0141820, 0x2AFFFFFF, alpha);
	}

	/** A glass button. */
	public static void button(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean active, boolean hot, float alpha) {
		if (!active) {
			pane(g, x, y, w, h, RADIUS, FILL_DIM, 0x18FFFFFF, alpha);
		} else {
			pane(g, x, y, w, h, RADIUS, hot ? FILL_HOT : FILL, hot ? EDGE_HOT : EDGE, alpha);
		}
	}

	/** A glass button tinted in the accent: our own entries. */
	public static void tinted(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hot, float alpha) {
		pane(g, x, y, w, h, RADIUS, Theme.alpha(TINT, hot ? 0.62f : 0.42f), hot ? 0xB0D2C6FF : 0x70B9A8FF, alpha);
	}
}
