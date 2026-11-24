package net.yahuclient.mod.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Our own marks, drawn as shapes (signed distances) and painted for the exact screen size they are shown at, so their
 * edges are smooth at every GUI scale: the Yahu logo, the close X and the settings gear.
 */
public final class Icons {
	private Icons() {
	}

	/**
	 * The Yahu logo: a hexagon outline with rounded corners, a geometric Y inside it whose arms reach for the hexagon's
	 * upper sides, and a violet spark where the arms meet.
	 */
	public static final Tex.Shape LOGO = (x, y, px) -> {
		double hex = Math.abs(roundedHexagon(x, y, 0.80, 0.10)) - 0.055;
		double armL = segment(x, y, -0.38, -0.36, 0, 0.06);
		double armR = segment(x, y, 0.38, -0.36, 0, 0.06);
		double stem = segment(x, y, 0, 0.06, 0, 0.46);
		double letter = Math.min(Math.min(armL, armR), stem) - 0.085;
		double spark = Math.hypot(x, y - 0.06) - 0.115;
		double white = Tex.cover(Math.min(hex, letter), px);
		double violet = Tex.cover(spark, px);
		if (violet > 0) {
			// the spark sits on top of the letter, with a hairline gap around it
			double gap = Tex.cover(Math.hypot(x, y - 0.06) - 0.16, px);
			white = white * (1 - gap);
			return blend(0xFFFFFF, white, Theme.ACCENT_HI & 0xFFFFFF, violet);
		}
		double gap = Tex.cover(Math.hypot(x, y - 0.06) - 0.16, px);
		return argb(white * (1 - gap), 0xFFFFFF);
	};

	/**
	 * The Yahu logo: a glass tile (a rounded square, faintly filled, its edge bright at the top left and violet at the
	 * bottom right), a Y of three round strokes light at the top and lilac at the foot, a violet dot where they meet.
	 */
	public static final Tex.Shape LOGO_A = (x, y, px) -> {
		double box = roundBox(x, y, .84, .84, .52);
		double tile = Tex.cover(box, px);
		double rim = Tex.cover(Math.abs(box + .025) - .03, px);
		double armL = segment(x, y, -.38, -.44, 0, .02), armR = segment(x, y, .38, -.44, 0, .02), stem = segment(x, y, 0, .02, 0, .48);
		double letter = Tex.cover(Math.min(Math.min(armL, armR), stem) - .1, px);
		double ring = Tex.cover(Math.hypot(x, y - .02) - .18, px);
		double dot = Tex.cover(Math.hypot(x, y - .02) - .13, px);
		// where light falls: top left bright, bottom right violet
		double k = Math.max(0, Math.min(1, (x + y + 1.2) / 2.4));
		int rimColor = mixRgb(0xF2F4F8, 0xB9A8FF, k);
		int letterColor = mixRgb(0xF4F5F8, 0xD9D2FF, Math.max(0, Math.min(1, (y + .5))));
		// layers, bottom up: the faint tile, its rim, the letter (cut around the dot), the dark ring, the violet dot
		int out = argb(tile * .09, 0xFFFFFF);
		out = over(out, argb(rim * (.55 + .2 * (1 - k)), rimColor));
		out = over(out, argb(letter * (1 - ring), letterColor));
		out = over(out, argb(ring * (1 - dot) * .85, 0x0B0B12));
		out = over(out, argb(dot, 0xA892FF));
		return out;
	};

	/** A close X: two rounded strokes. */
	public static final Tex.Shape CLOSE = (x, y, px) -> {
		double d = Math.min(segment(x, y, -0.42, -0.42, 0.42, 0.42), segment(x, y, -0.42, 0.42, 0.42, -0.42)) - 0.085;
		return argb(Tex.cover(d, px), 0xFFFFFF);
	};

	/** A back arrow: a chevron pointing left with a short shaft. */
	public static final Tex.Shape BACK = (x, y, px) -> {
		double d = Math.min(Math.min(segment(x, y, -0.5, 0, 0.0, -0.5), segment(x, y, -0.5, 0, 0.0, 0.5)), segment(x, y, -0.5, 0, 0.55, 0)) - 0.085;
		return argb(Tex.cover(d, px), 0xFFFFFF);
	};

	/** A settings gear: eight rounded teeth around a ring. */
	public static final Tex.Shape GEAR = (x, y, px) -> {
		double r = Math.hypot(x, y);
		double angle = Math.atan2(y, x);
		double tooth = Math.max(-1, Math.min(1, Math.cos(angle * 8) * 2.4)) * 0.5 + 0.5;
		double outer = r - (0.62 + 0.22 * tooth);
		double hole = 0.30 - r;
		return argb(Tex.cover(Math.max(outer, hole), px), 0xFFFFFF);
	};

	/** Draws a shape size by size GUI units at (x, y), tinted with color, painted for the exact screen pixels. */
	public static void draw(GuiGraphicsExtractor g, String name, Tex.Shape shape, float x, float y, float size, int color) {
		float s = Draw.scale();
		Warp wp = Warp.current();
		int d = Math.max(2, Math.round(size * s * (wp != null ? wp.project(x + size / 2f, y + size / 2f)[2] : 1)));
		Identifier tex = Tex.shape(name, d, shape);
		if (wp != null) {
			wp.texture(g, tex, x, y, x + size, y + size, 0, 0, 1, 1, color);
			return;
		}
		g.pose().pushMatrix();
		g.pose().scale(1f / s, 1f / s);
		g.blit(RenderPipelines.GUI_TEXTURED, tex, Math.round(x * s), Math.round(y * s), 0, 0, d, d, d, d, d, d, color);
		g.pose().popMatrix();
	}

	// --- signed distances (negative inside) ---

	/** A rounded box of half size (hx, hy) with corner radius r. */
	private static double roundBox(double x, double y, double hx, double hy, double r) {
		double qx = Math.abs(x) - hx + r, qy = Math.abs(y) - hy + r;
		return Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - r;
	}

	private static int mixRgb(int a, int b, double t) {
		int r = (int) Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int gg = (int) Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bb = (int) Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return (r << 16) | (gg << 8) | bb;
	}

	/** Color b (ARGB, straight alpha) over color a. */
	private static int over(int a, int b) {
		double ab = (b >>> 24) / 255.0, aa = (a >>> 24) / 255.0;
		double out = ab + aa * (1 - ab);
		if (out <= 0) {
			return 0;
		}
		int r = (int) Math.round((((b >> 16) & 0xFF) * ab + ((a >> 16) & 0xFF) * aa * (1 - ab)) / out);
		int gg = (int) Math.round((((b >> 8) & 0xFF) * ab + ((a >> 8) & 0xFF) * aa * (1 - ab)) / out);
		int bb = (int) Math.round(((b & 0xFF) * ab + (a & 0xFF) * aa * (1 - ab)) / out);
		return argb(out, (r << 16) | (gg << 8) | bb);
	}

	private static double segment(double x, double y, double ax, double ay, double bx, double by) {
		double pax = x - ax, pay = y - ay, bax = bx - ax, bay = by - ay;
		double h = Math.max(0, Math.min(1, (pax * bax + pay * bay) / (bax * bax + bay * bay)));
		return Math.hypot(pax - bax * h, pay - bay * h);
	}

	/** A hexagon with a point at the top and bottom, r from the center to the middle of a side, corners rounded. */
	private static double roundedHexagon(double x, double y, double r, double round) {
		r -= round;
		// a flat-topped hexagon turned on its side: swap the axes
		double px = Math.abs(y), py = Math.abs(x);
		double kx = -0.866025404, ky = 0.5, kz = 0.577350269;
		double dot = Math.min(kx * px + ky * py, 0);
		px -= 2 * dot * kx;
		py -= 2 * dot * ky;
		px -= Math.max(-kz * r, Math.min(kz * r, px));
		py -= r;
		return Math.hypot(px, py) * Math.signum(py) - round;
	}

	private static int argb(double alpha, int rgb) {
		return ((int) Math.round(Math.max(0, Math.min(1, alpha)) * 255) << 24) | rgb;
	}

	/** Layer b (coverage tb) over layer a (coverage ta). */
	private static int blend(int a, double ta, int b, double tb) {
		double out = tb + ta * (1 - tb);
		if (out <= 0) {
			return 0;
		}
		int r = (int) Math.round((((b >> 16) & 0xFF) * tb + ((a >> 16) & 0xFF) * ta * (1 - tb)) / out);
		int gr = (int) Math.round((((b >> 8) & 0xFF) * tb + ((a >> 8) & 0xFF) * ta * (1 - tb)) / out);
		int bl = (int) Math.round(((b & 0xFF) * tb + (a & 0xFF) * ta * (1 - tb)) / out);
		return argb(out, (r << 16) | (gr << 8) | bl);
	}
}
