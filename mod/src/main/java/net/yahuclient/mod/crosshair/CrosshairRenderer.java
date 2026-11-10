package net.yahuclient.mod.crosshair;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.yahuclient.mod.config.YahuConfig;
import net.yahuclient.mod.ui.Draw;
import net.yahuclient.mod.ui.Theme;

/**
 * Draws the crosshair in real screen pixels, whatever the GUI scale: one pixel of thickness is one pixel on the screen. The
 * parts are combined from the settings (arms, dot, ring), each with an optional outline drawn first. For PvP it can change
 * color on a target, flash a hit marker, show the attack charge as a bar or a ring and open up while moving.
 */
public final class CrosshairRenderer {
	/** What the player is doing this frame, as far as the crosshair cares. */
	public record State(boolean onTarget, long sinceHitMs, float attackStrength, double horizontalSpeed, boolean inAir, boolean sprinting) {
		public static final State IDLE = new State(false, Long.MAX_VALUE, 1f, 0, false, false);
	}

	private CrosshairRenderer() {
	}

	/** The crosshair centered at (x, y) in the current GUI coordinates. */
	public static void draw(GuiGraphicsExtractor g, YahuConfig.Crosshair c, State s, float x, float y) {
		float scale = (float) Minecraft.getInstance().getWindow().getGuiScale();
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(1f / scale, 1f / scale);
		drawPixels(g, c, s);
		g.pose().popMatrix();
	}

	/** Draws around (0, 0) where one unit is one screen pixel. */
	private static void drawPixels(GuiGraphicsExtractor g, YahuConfig.Crosshair c, State s) {
		float opacity = clamp(c.opacity / 100f);
		int color = Theme.alpha((c.targetColorOn && s.onTarget() ? c.targetColor : c.color) | 0xFF000000, opacity);
		int dotColor = c.dotOwnColor ? Theme.alpha(c.dotColor | 0xFF000000, opacity) : color;
		int outlineColor = Theme.alpha(c.outlineColor | 0xFF000000, clamp(c.outlineOpacity / 100f) * opacity);
		int ow = Math.max(0, c.outline);
		int gap = c.gap + CrosshairGeometry.spread(c.spread, s.horizontalSpeed(), s.inAir(), s.sprinting());

		// outlines first, so the parts sit on top of them
		if (ow > 0) {
			arms(g, c, gap, ow, outlineColor);
			if (c.dot) {
				dot(g, c, ow, outlineColor);
			}
			if (c.ring) {
				for (var r : CrosshairGeometry.ring(c.ringRadius + ow, c.ringThickness + 2 * ow)) {
					fill(g, r, outlineColor);
				}
			}
		}
		arms(g, c, gap, 0, color);
		if (c.dot) {
			dot(g, c, 0, dotColor);
		}
		if (c.ring) {
			for (var r : CrosshairGeometry.ring(c.ringRadius, c.ringThickness)) {
				fill(g, r, color);
			}
		}

		int reach = Math.max(c.arms == YahuConfig.Arms.NONE ? 0 : gap + c.armLength, c.ring ? c.ringRadius : 0);
		reach = Math.max(reach, c.dot ? c.dotSize : 0);

		if (c.hitMarker && s.sinceHitMs() >= 0 && s.sinceHitMs() < c.hitMarkerMs) {
			float fade = 1f - s.sinceHitMs() / (float) Math.max(1, c.hitMarkerMs);
			hitMarker(g, reach + 3, Math.max(2, c.hitMarkerSize), Theme.alpha(c.hitMarkerColor | 0xFF000000, fade), outlineColor, fade);
		}

		if (s.attackStrength() < 1f) {
			int charged = Theme.alpha(c.color | 0xFF000000, opacity);
			if (c.charge == YahuConfig.Charge.BAR) {
				int w = Math.max(16, 2 * reach + 1);
				int y = reach + 5;
				g.fill(-w / 2 - 1, y - 1, w / 2 + 2, y + 3, Theme.alpha(0xFF000000, 0.55f * opacity));
				g.fill(-w / 2, y, -w / 2 + Math.round(w * clamp(s.attackStrength())), y + 2, charged);
			} else if (c.charge == YahuConfig.Charge.RING) {
				chargeRing(g, reach + 5, s.attackStrength(), charged);
			}
		}
	}

	private static void arms(GuiGraphicsExtractor g, YahuConfig.Crosshair c, int gap, int grow, int color) {
		if (c.arms == YahuConfig.Arms.X) {
			// the cross turned a quarter turn: diagonal arms, drawn rotated by the GPU so they stay straight
			g.pose().pushMatrix();
			g.pose().rotate((float) Math.toRadians(45));
			for (var r : CrosshairGeometry.arms(YahuConfig.Arms.CROSS, c.armLength, c.armThickness, gap)) {
				fill(g, r.grow(grow), color);
			}
			g.pose().popMatrix();
			return;
		}
		for (var r : CrosshairGeometry.arms(c.arms, c.armLength, c.armThickness, gap)) {
			fill(g, r.grow(grow), color);
		}
	}

	private static void dot(GuiGraphicsExtractor g, YahuConfig.Crosshair c, int grow, int color) {
		if (c.dotRound) {
			// a smooth disk; an odd size is centered on the middle pixel
			float center = (c.dotSize % 2 == 1) ? 0.5f : 0f;
			Draw.circle(g, center, center, c.dotSize / 2f + grow, color);
		} else {
			fill(g, CrosshairGeometry.squareDot(c.dotSize).grow(grow), color);
		}
	}

	private static void hitMarker(GuiGraphicsExtractor g, int radius, int size, int color, int outline, float fade) {
		g.pose().pushMatrix();
		g.pose().rotate((float) Math.toRadians(45));
		int t = 2;
		int[][] arms = { { -t / 2, -radius - size, t, size }, { -t / 2, radius, t, size }, { -radius - size, -t / 2, size, t }, { radius, -t / 2, size, t } };
		for (int[] a : arms) {
			g.fill(a[0] - 1, a[1] - 1, a[0] + a[2] + 1, a[1] + a[3] + 1, Theme.alpha(outline, fade));
		}
		for (int[] a : arms) {
			g.fill(a[0], a[1], a[0] + a[2], a[1] + a[3], color);
		}
		g.pose().popMatrix();
	}

	private static void chargeRing(GuiGraphicsExtractor g, int radius, float strength, int color) {
		// small squares along the circle, clockwise from the top, as far as the attack has charged
		int steps = Math.max(24, radius * 6);
		int shown = Math.round(steps * clamp(strength));
		for (int i = 0; i < steps; i++) {
			double a = -Math.PI / 2 + i * 2 * Math.PI / steps;
			int px = (int) Math.round(Math.cos(a) * radius), py = (int) Math.round(Math.sin(a) * radius);
			g.fill(px, py, px + 2, py + 2, i < shown ? color : Theme.alpha(0xFF000000, 0.45f));
		}
	}

	private static void fill(GuiGraphicsExtractor g, CrosshairGeometry.Rect r, int color) {
		g.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), color);
	}

	private static float clamp(float v) {
		return Math.max(0, Math.min(1, v));
	}
}
