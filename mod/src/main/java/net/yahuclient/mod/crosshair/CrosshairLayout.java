package net.yahuclient.mod.crosshair;

import java.util.ArrayList;
import java.util.List;

import net.yahuclient.mod.config.CrosshairShape;
import net.yahuclient.mod.config.YahuConfig;

/**
 * The custom crosshair as a list of filled pixel rectangles around the screen center (0, 0), so the drawing code only fills
 * rectangles and the shapes can be checked without a game.
 */
public final class CrosshairLayout {
	/** A filled rectangle from (x, y), w by h pixels, relative to the center. */
	public record Rect(int x, int y, int w, int h) {
	}

	private CrosshairLayout() {
	}

	public static List<Rect> of(YahuConfig.Crosshair c) {
		int size = Math.max(1, c.size);
		int gap = Math.max(0, c.gap);
		int t = Math.max(1, c.thickness);
		// thickness grows from the middle; odd thicknesses sit centered on the pixel at (0, 0)
		int half = t / 2;
		List<Rect> rects = new ArrayList<>();
		switch (c.shape) {
			case CROSS -> {
				rects.add(new Rect(-half, -gap - size, t, size));           // up
				rects.add(new Rect(-half, gap + 1, t, size));                // down
				rects.add(new Rect(-gap - size, -half, size, t));           // left
				rects.add(new Rect(gap + 1, -half, size, t));                // right
			}
			case T -> {
				rects.add(new Rect(-half, gap + 1, t, size));
				rects.add(new Rect(-gap - size, -half, size, t));
				rects.add(new Rect(gap + 1, -half, size, t));
			}
			case DOT -> {
				int d = Math.max(t, Math.min(size, 4));
				rects.add(new Rect(-d / 2, -d / 2, d, d));
			}
			case BOX -> {
				int r = gap + size;
				rects.add(new Rect(-r, -r, 2 * r + 1, t));                  // top
				rects.add(new Rect(-r, r + 1 - t, 2 * r + 1, t));           // bottom
				rects.add(new Rect(-r, -r + t, t, 2 * r + 1 - 2 * t));       // left
				rects.add(new Rect(r + 1 - t, -r + t, t, 2 * r + 1 - 2 * t)); // right
			}
			case CIRCLE -> {
				// a pixel ring: every pixel whose distance to the middle is within the stroke
				int r = gap + size;
				double outer = r + 0.5;
				double inner = Math.max(0, r + 0.5 - t);
				for (int y = -r; y <= r; y++) {
					int runStart = Integer.MIN_VALUE;
					for (int x = -r; x <= r + 1; x++) {
						double d = Math.sqrt(x * x + y * y);
						boolean on = x <= r && d <= outer && d > inner;
						if (on && runStart == Integer.MIN_VALUE) {
							runStart = x;
						} else if (!on && runStart != Integer.MIN_VALUE) {
							rects.add(new Rect(runStart, y, x - runStart, 1));
							runStart = Integer.MIN_VALUE;
						}
					}
				}
			}
			case X -> {
				// diagonal arms built from small squares
				for (int i = gap + 1; i <= gap + size; i++) {
					rects.add(new Rect(i - half, i - half, t, t));
					rects.add(new Rect(-i - half, i - half, t, t));
					rects.add(new Rect(i - half, -i - half, t, t));
					rects.add(new Rect(-i - half, -i - half, t, t));
				}
			}
		}
		if (c.dot && c.shape != CrosshairShape.DOT) {
			rects.add(new Rect(-half, -half, t, t));
		}
		return rects;
	}
}
