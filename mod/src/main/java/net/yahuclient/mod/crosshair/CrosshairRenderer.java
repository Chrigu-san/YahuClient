package net.yahuclient.mod.crosshair;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.yahuclient.mod.config.YahuConfig;
import net.yahuclient.mod.ui.YahuUi;

/**
 * Draws the custom crosshair around a point: an optional rim first, then the shape. For PvP it can also change color while
 * aiming at a player or mob, open up while moving, flash a hit marker when a hit lands and show the attack charge.
 */
public final class CrosshairRenderer {
	private static final long HIT_MARKER_MS = 260;

	/** What the player is doing this frame, as far as the crosshair cares. */
	public record State(boolean onTarget, long sinceHitMs, float attackStrength, double horizontalSpeed, boolean inAir, boolean sprinting) {
		public static final State IDLE = new State(false, Long.MAX_VALUE, 1f, 0, false, false);
	}

	private CrosshairRenderer() {
	}

	/** The plain crosshair, as in the menu preview. */
	public static void draw(GuiGraphicsExtractor g, YahuConfig.Crosshair c, int cx, int cy) {
		drawPvp(g, c, State.IDLE, cx, cy);
	}

	public static void drawPvp(GuiGraphicsExtractor g, YahuConfig.Crosshair c, State s, int cx, int cy) {
		float opacity = Math.max(0.05f, Math.min(1f, c.opacity / 100f));
		YahuConfig.Crosshair shape = c;
		int extraGap = c.dynamicGap ? dynamicGap(s) : 0;
		if (extraGap > 0) {
			shape = copyWithGap(c, c.gap + extraGap);
		}
		var rects = CrosshairLayout.of(shape);

		int color = c.targetColorOn && s.onTarget() ? c.targetColor : c.color;
		color = YahuUi.withAlpha(color, opacity);
		if (c.outline) {
			int rim = YahuUi.withAlpha(c.outlineColor, 0.75f * opacity);
			for (var r : rects) {
				g.fill(cx + r.x() - 1, cy + r.y() - 1, cx + r.x() + r.w() + 1, cy + r.y() + r.h() + 1, rim);
			}
		}
		for (var r : rects) {
			g.fill(cx + r.x(), cy + r.y(), cx + r.x() + r.w(), cy + r.y() + r.h(), color);
		}

		if (c.hitMarker && s.sinceHitMs() >= 0 && s.sinceHitMs() < HIT_MARKER_MS) {
			drawHitMarker(g, c, cx, cy, 1f - s.sinceHitMs() / (float) HIT_MARKER_MS, c.gap + extraGap + c.size + 3);
		}

		if (c.cooldown && s.attackStrength() < 1f) {
			int w = 2 * (c.gap + c.size) + 1;
			int x = cx - w / 2, y = cy + c.gap + extraGap + c.size + 5;
			g.fill(x - 1, y - 1, x + w + 1, y + 3, YahuUi.withAlpha(0xFF000000, 0.6f * opacity));
			g.fill(x, y, x + Math.round(w * Math.max(0, s.attackStrength())), y + 2, YahuUi.withAlpha(c.color, opacity));
		}
	}

	/** Four short diagonal strokes around the crosshair, fading out. */
	private static void drawHitMarker(GuiGraphicsExtractor g, YahuConfig.Crosshair c, int cx, int cy, float strength, int radius) {
		int color = YahuUi.withAlpha(c.hitMarkerColor, strength);
		for (int i = 0; i < 3; i++) {
			int d = radius + i;
			g.fill(cx + d, cy + d, cx + d + 1, cy + d + 1, color);
			g.fill(cx - d, cy + d, cx - d + 1, cy + d + 1, color);
			g.fill(cx + d, cy - d, cx + d + 1, cy - d + 1, color);
			g.fill(cx - d, cy - d, cx - d + 1, cy - d + 1, color);
		}
	}

	/** How many pixels the crosshair opens up: a little while walking, more while sprinting, most in the air. */
	static int dynamicGap(State s) {
		double spread = Math.min(1.0, s.horizontalSpeed() / 0.28) * (s.sprinting() ? 4 : 2.5);
		if (s.inAir()) {
			spread += 3;
		}
		return (int) Math.round(spread);
	}

	private static YahuConfig.Crosshair copyWithGap(YahuConfig.Crosshair c, int gap) {
		YahuConfig.Crosshair copy = new YahuConfig.Crosshair();
		copy.shape = c.shape;
		copy.size = c.size;
		copy.gap = gap;
		copy.thickness = c.thickness;
		copy.dot = c.dot;
		return copy;
	}
}
