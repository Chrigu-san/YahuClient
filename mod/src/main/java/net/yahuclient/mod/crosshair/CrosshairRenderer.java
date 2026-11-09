package net.yahuclient.mod.crosshair;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.yahuclient.mod.config.YahuConfig;

/** Draws the custom crosshair around a point: an optional dark rim first, then the shape in its color. */
public final class CrosshairRenderer {
	private CrosshairRenderer() {
	}

	public static void draw(GuiGraphicsExtractor g, YahuConfig.Crosshair c, int cx, int cy) {
		var rects = CrosshairLayout.of(c);
		if (c.outline) {
			int rim = 0xC0000000;
			for (var r : rects) {
				g.fill(cx + r.x() - 1, cy + r.y() - 1, cx + r.x() + r.w() + 1, cy + r.y() + r.h() + 1, rim);
			}
		}
		int color = (c.color >>> 24) == 0 ? 0xFF000000 | c.color : c.color;
		for (var r : rects) {
			g.fill(cx + r.x(), cy + r.y(), cx + r.x() + r.w(), cy + r.y() + r.h(), color);
		}
	}
}
