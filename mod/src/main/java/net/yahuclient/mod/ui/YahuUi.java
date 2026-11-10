package net.yahuclient.mod.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The look of Yahu Client inside the game, the same as the launcher's "Overworld" theme: night blue surfaces, blocks with
 * light top and left edges and dark bottom and right edges, and a block color for each kind of thing.
 */
public final class YahuUi {
	public static final int INK = 0xFF10131F;
	public static final int SURFACE = 0xFF171C2C;
	public static final int RAISED = 0xFF1F2639;
	public static final int RAISED_HI = 0xFF283149;
	public static final int LINE = 0xFF2A3350;
	public static final int LINE_HI = 0xFF3A4670;
	public static final int TEXT = 0xFFF7F8FC;
	public static final int MUTED = 0xFFB4BCD6;
	public static final int FAINT = 0xFF6E789A;
	public static final int GRASS = 0xFF6CD83E;
	public static final int GRASS_HI = 0xFF9CF06C;
	public static final int GRASS_LO = 0xFF3E9A22;
	public static final int SKY = 0xFF55B4FF;
	public static final int GOLD = 0xFFFFC94A;
	public static final int AMETHYST = 0xFFB583FF;
	public static final int COPPER = 0xFFFF9945;
	public static final int DIAMOND = 0xFF4AE3D3;
	public static final int REDSTONE = 0xFFFF5252;

	/** Colors offered by the color pickers, in the order of the block palette, plus white and black. */
	public static final int[] SWATCHES = { 0xFFFFFFFF, GRASS, SKY, GOLD, AMETHYST, COPPER, DIAMOND, REDSTONE, 0xFF000000 };

	private YahuUi() {
	}

	/** A raised block: the face, a light edge on top and left and a dark one at the bottom and right. */
	public static void block(GuiGraphicsExtractor g, int x, int y, int w, int h, int face) {
		g.fill(x, y, x + w, y + h, face);
		g.fill(x, y, x + w, y + 1, 0x30FFFFFF);
		g.fill(x, y + 1, x + 1, y + h - 1, 0x30FFFFFF);
		g.fill(x, y + h - 2, x + w, y + h, 0x60000000);
		g.fill(x + w - 1, y + 1, x + w, y + h - 2, 0x60000000);
	}

	/** A sunk-in field (inputs, tracks, slots): dark on top and left, light at the bottom and right. */
	public static void sunk(GuiGraphicsExtractor g, int x, int y, int w, int h, int face) {
		g.fill(x, y, x + w, y + h, face);
		g.fill(x, y, x + w, y + 1, 0x70000000);
		g.fill(x, y + 1, x + 1, y + h, 0x70000000);
		g.fill(x, y + h - 1, x + w, y + h, 0x20FFFFFF);
		g.fill(x + w - 1, y + 1, x + w, y + h - 1, 0x20FFFFFF);
	}

	/** A panel: a raised block with a stripe in its color along the top, like the launcher's panels. */
	public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int stripe) {
		block(g, x, y, w, h, SURFACE);
		g.fill(x, y, x + w, y + 2, stripe);
	}

	public static int mix(int a, int b, float t) {
		t = Math.max(0, Math.min(1, t));
		int aa = a >>> 24, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int ba = b >>> 24, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16) | (Math.round(ag + (bg - ag) * t) << 8)
				| Math.round(ab + (bb - ab) * t);
	}

	public static int withAlpha(int color, float alpha) {
		return (Math.round(Math.max(0, Math.min(1, alpha)) * 255) << 24) | (color & 0xFFFFFF);
	}
}
