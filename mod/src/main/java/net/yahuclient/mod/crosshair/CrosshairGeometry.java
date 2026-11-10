package net.yahuclient.mod.crosshair;

import java.util.ArrayList;
import java.util.List;

import net.yahuclient.mod.config.YahuConfig;

/**
 * The square parts of the crosshair as rectangles in screen pixels around the center pixel (0, 0): the arms and a square
 * dot, and the ring as rows of pixels. Kept apart from drawing, so the shapes can be checked without a game. A round dot and
 * the diagonal X arms are drawn by the renderer directly (a smooth disk, rotated arms).
 */
public final class CrosshairGeometry {
	/** A filled rectangle from (x, y), w by h pixels. */
	public record Rect(int x, int y, int w, int h) {
		public Rect grow(int by) {
			return new Rect(x - by, y - by, w + 2 * by, h + 2 * by);
		}
	}

	private CrosshairGeometry() {
	}

	/** Where a part of thickness t starts so it sits centered on the middle pixel (odd t) or just around it (even t). */
	static int start(int thickness) {
		return -(thickness / 2);
	}

	/** The straight arms (cross or T) with this gap; X is the cross turned by 45 degrees in the renderer. */
	public static List<Rect> arms(YahuConfig.Arms arms, int length, int thickness, int gap) {
		List<Rect> rects = new ArrayList<>();
		if (arms == YahuConfig.Arms.NONE || length <= 0) {
			return rects;
		}
		int t = Math.max(1, thickness);
		int s = start(t);
		// the gap is measured from the edge of the middle pixel block, so a gap of 0 makes arms touch it
		int inner = s + t + gap;            // first pixel right of / below the middle
		int innerNeg = s - 1 - gap;         // first pixel left of / above the middle
		if (arms != YahuConfig.Arms.T) {
			rects.add(new Rect(s, innerNeg - length + 1, t, length));   // up
		}
		rects.add(new Rect(s, inner, t, length));                       // down
		rects.add(new Rect(innerNeg - length + 1, s, length, t));       // left
		rects.add(new Rect(inner, s, length, t));                       // right
		return rects;
	}

	/** A square dot of this size, centered. */
	public static Rect squareDot(int size) {
		int d = Math.max(1, size);
		return new Rect(start(d), start(d), d, d);
	}

	/** A ring of pixels: every pixel whose center lies within the stroke, as runs per row. */
	public static List<Rect> ring(int radius, int thickness) {
		List<Rect> rects = new ArrayList<>();
		double outer = radius + 0.5;
		double inner = Math.max(0, radius + 0.5 - Math.max(1, thickness));
		int r = radius + 1;
		for (int y = -r; y <= r; y++) {
			int runStart = Integer.MIN_VALUE;
			for (int x = -r; x <= r + 1; x++) {
				double d = Math.hypot(x, y);
				boolean on = x <= r && d <= outer && d > inner;
				if (on && runStart == Integer.MIN_VALUE) {
					runStart = x;
				} else if (!on && runStart != Integer.MIN_VALUE) {
					rects.add(new Rect(runStart, y, x - runStart, 1));
					runStart = Integer.MIN_VALUE;
				}
			}
		}
		return rects;
	}

	/** How many pixels the crosshair opens: the setting at a sprint, less while walking, a little more in the air. */
	public static int spread(int amount, double horizontalSpeed, boolean inAir, boolean sprinting) {
		if (amount <= 0) {
			return 0;
		}
		double move = Math.min(1.0, horizontalSpeed / 0.28) * (sprinting ? 1.0 : 0.6);
		double air = inAir ? 0.35 : 0;
		return (int) Math.round(amount * Math.min(1.2, move + air));
	}
}
