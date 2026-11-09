package net.yahuclient.mod.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Minecraft's own buttons, sliders and menu backgrounds in the launcher's block look. The mixins in
 * net.yahuclient.mod.mixin call these in place of the vanilla sprites; the text on top stays vanilla's.
 */
public final class BlockWidgets {
	private BlockWidgets() {
	}

	private static int fade(int color, float alpha) {
		return YahuUi.withAlpha(color, ((color >>> 24) / 255f) * Math.max(0, Math.min(1, alpha)));
	}

	/** A button: a raised block, greener while hovered or focused, flat and dark while disabled. */
	public static void button(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean active, boolean hot, float alpha) {
		if (!active) {
			g.fill(x, y, x + w, y + h, fade(YahuUi.SURFACE, alpha));
			g.fill(x, y + h - 1, x + w, y + h, fade(0x60000000, alpha));
			return;
		}
		int face = hot ? YahuUi.mix(YahuUi.RAISED_HI, YahuUi.GRASS, 0.32f) : YahuUi.RAISED_HI;
		g.fill(x, y, x + w, y + h, fade(face, alpha));
		g.fill(x, y, x + w, y + 1, fade(hot ? 0x70FFFFFF : 0x30FFFFFF, alpha));
		g.fill(x, y + 1, x + 1, y + h - 2, fade(0x30FFFFFF, alpha));
		// a thick dark bottom edge, like the launcher's buttons; green while hovered
		g.fill(x, y + h - 2, x + w, y + h, fade(hot ? YahuUi.GRASS_LO : 0x80000000, alpha));
		g.fill(x + w - 1, y + 1, x + w, y + h - 2, fade(0x60000000, alpha));
	}

	/** A slider's track: sunk in, filled with grass up to the value. */
	public static void sliderTrack(GuiGraphicsExtractor g, int x, int y, int w, int h, double value, boolean active, float alpha) {
		g.fill(x, y, x + w, y + h, fade(YahuUi.INK, alpha));
		g.fill(x, y, x + w, y + 1, fade(0x70000000, alpha));
		g.fill(x, y + 1, x + 1, y + h, fade(0x70000000, alpha));
		g.fill(x, y + h - 1, x + w, y + h, fade(0x20FFFFFF, alpha));
		if (active) {
			int fill = (int) Math.round((w - 2) * Math.max(0, Math.min(1, value)));
			g.fill(x + 1, y + 1, x + 1 + fill, y + h - 1, fade(YahuUi.withAlpha(YahuUi.GRASS_LO, 0.55f), alpha));
		}
	}

	/** A slider's handle: a small raised block. */
	public static void sliderHandle(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hot, float alpha) {
		int face = hot ? YahuUi.GRASS_HI : YahuUi.TEXT;
		g.fill(x, y, x + w, y + h, fade(face, alpha));
		g.fill(x, y + h - 2, x + w, y + h, fade(0x80000000, alpha));
		g.fill(x + w - 1, y, x + w, y + h - 2, fade(0x50000000, alpha));
	}

	/**
	 * The menu background outside a world: a dark wall of night blue blocks, each a shade lighter or darker, the same as
	 * the launcher's backdrop. Blocks line up with the screen, so lists and the screen behind them fit together.
	 */
	public static void wall(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		final int block = 16;
		g.fill(x, y, x + w, y + h, YahuUi.INK);
		int startX = Math.floorDiv(x, block) * block, startY = Math.floorDiv(y, block) * block;
		for (int by = startY; by < y + h; by += block) {
			for (int bx = startX; bx < x + w; bx += block) {
				int hash = (bx / block) * 73856093 ^ (by / block) * 19349663;
				float shade = ((hash >>> 4) % 100) / 100f;
				int color = YahuUi.mix(YahuUi.INK, shade > 0.5f ? YahuUi.SURFACE : 0xFF000000, 0.15f + 0.4f * Math.abs(shade - 0.5f));
				int x0 = Math.max(bx, x), y0 = Math.max(by, y), x1 = Math.min(bx + block, x + w), y1 = Math.min(by + block, y + h);
				g.fill(x0, y0, x1, y1, color);
				if (by >= y) {
					g.fill(x0, by, x1, by + 1, 0x08FFFFFF);
				}
			}
		}
	}

	/** The background inside a world (pause menu, options while playing): the game behind a night blue veil. */
	public static void veil(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, YahuUi.withAlpha(YahuUi.INK, 0.62f));
	}
}
