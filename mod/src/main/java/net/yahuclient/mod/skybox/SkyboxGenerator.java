package net.yahuclient.mod.skybox;

import net.yahuclient.mod.config.SkyPreset;

/**
 * Paints the built-in skies as cube maps, pixel by pixel from the direction each pixel looks in, so the six faces meet
 * without seams. Everything is computed here (noise, stars, light); no outside pictures are used.
 *
 * The cube map comes as one atlas, 3 faces wide and 2 high, each face with a small border painted from just beyond its edge,
 * so smooth filtering never mixes in the neighbouring face.
 */
public final class SkyboxGenerator {
	/** Face order in the atlas: +X, -X, +Y (up), -Y (down), +Z, -Z. Each face: normal, right, up. */
	static final double[][][] FACES = {
		{ { 1, 0, 0 }, { 0, 0, 1 }, { 0, 1, 0 } },
		{ { -1, 0, 0 }, { 0, 0, -1 }, { 0, 1, 0 } },
		{ { 0, 1, 0 }, { -1, 0, 0 }, { 0, 0, -1 } },
		{ { 0, -1, 0 }, { -1, 0, 0 }, { 0, 0, 1 } },
		{ { 0, 0, 1 }, { -1, 0, 0 }, { 0, 1, 0 } },
		{ { 0, 0, -1 }, { 1, 0, 0 }, { 0, 1, 0 } },
	};
	public static final int PAD = 2;

	/** A sky: the color (0xRRGGBB) seen in a direction (unit vector). */
	public interface Sky {
		int color(double x, double y, double z);
	}

	/** The finished atlas: ARGB pixels, its size and the size of one face without the border. */
	public record Atlas(int[] pixels, int width, int height, int face) {
	}

	private SkyboxGenerator() {
	}

	public static Atlas generate(Sky sky, int face) {
		int cell = face + 2 * PAD;
		int width = cell * 3, height = cell * 2;
		int[] pixels = new int[width * height];
		for (int f = 0; f < 6; f++) {
			double[] n = FACES[f][0], r = FACES[f][1], u = FACES[f][2];
			int ox = (f % 3) * cell, oy = (f / 3) * cell;
			for (int j = 0; j < cell; j++) {
				// the border pixels look a little beyond the face's edge, which is the neighbouring face's sky
				double t = 1 - (j - PAD + 0.5) / face * 2;
				for (int i = 0; i < cell; i++) {
					double s = (i - PAD + 0.5) / face * 2 - 1;
					double x = n[0] + s * r[0] + t * u[0];
					double y = n[1] + s * r[1] + t * u[1];
					double z = n[2] + s * r[2] + t * u[2];
					double len = Math.sqrt(x * x + y * y + z * z);
					pixels[(oy + j) * width + ox + i] = 0xFF000000 | sky.color(x / len, y / len, z / len);
				}
			}
		}
		return new Atlas(pixels, width, height, face);
	}

	public static Sky preset(SkyPreset preset) {
		return switch (preset) {
			case BLACK_HOLE -> SkyboxGenerator::blackHole;
			case CLEAR_DAY -> (x, y, z) -> day(x, y, z, false);
			case GOLDEN_HOUR -> (x, y, z) -> day(x, y, z, true);
			case AURORA -> SkyboxGenerator::aurora;
			case NEBULA -> SkyboxGenerator::nebula;
			case CUSTOM -> (x, y, z) -> 0;
		};
	}

	// ---------------------------------------------------------------------------------------------------------------
	// building blocks

	static double hash(int x, int y, int z) {
		long h = x * 374761393L + y * 668265263L + z * 2147483647L;
		h = (h ^ (h >>> 13)) * 1274126177L;
		h ^= h >>> 16;
		return (h & 0xFFFFFF) / (double) 0xFFFFFF;
	}

	/** Smooth value noise in 3D, 0 to 1. */
	static double noise(double x, double y, double z) {
		int xi = (int) Math.floor(x), yi = (int) Math.floor(y), zi = (int) Math.floor(z);
		double xf = x - xi, yf = y - yi, zf = z - zi;
		double u = xf * xf * (3 - 2 * xf), v = yf * yf * (3 - 2 * yf), w = zf * zf * (3 - 2 * zf);
		double c000 = hash(xi, yi, zi), c100 = hash(xi + 1, yi, zi), c010 = hash(xi, yi + 1, zi), c110 = hash(xi + 1, yi + 1, zi);
		double c001 = hash(xi, yi, zi + 1), c101 = hash(xi + 1, yi, zi + 1), c011 = hash(xi, yi + 1, zi + 1), c111 = hash(xi + 1, yi + 1, zi + 1);
		double x00 = lerp(c000, c100, u), x10 = lerp(c010, c110, u), x01 = lerp(c001, c101, u), x11 = lerp(c011, c111, u);
		return lerp(lerp(x00, x10, v), lerp(x01, x11, v), w);
	}

	/** Layers of noise, finer and fainter each time: clouds, gas, curtains. */
	static double fbm(double x, double y, double z, int octaves) {
		double sum = 0, amp = 0.5, freq = 1, norm = 0;
		for (int i = 0; i < octaves; i++) {
			sum += amp * noise(x * freq, y * freq, z * freq);
			norm += amp;
			amp *= 0.5;
			freq *= 2.03;
		}
		return sum / norm;
	}

	/** Stars: one possible star per cell of a fine grid on the sphere, sharp, in slightly different colors. */
	static double[] stars(double x, double y, double z, double density, double scale) {
		double sx = x * scale, sy = y * scale, sz = z * scale;
		int cx = (int) Math.floor(sx), cy = (int) Math.floor(sy), cz = (int) Math.floor(sz);
		double h = hash(cx, cy, cz);
		if (h > density) {
			return new double[] { 0, 0, 0 };
		}
		double px = cx + hash(cx + 11, cy, cz), py = cy + hash(cx, cy + 7, cz), pz = cz + hash(cx, cy, cz + 3);
		double d = Math.sqrt((sx - px) * (sx - px) + (sy - py) * (sy - py) + (sz - pz) * (sz - pz));
		double size = 0.05 + 0.1 * hash(cx + 5, cy + 5, cz);
		double b = Math.max(0, 1 - d / size);
		b = b * b * (0.4 + 0.6 * hash(cx + 2, cy + 9, cz + 4));
		double tint = hash(cx + 13, cy + 1, cz + 7);
		// some stars warm, some cool
		return new double[] { b * (0.85 + 0.3 * tint), b * 0.92, b * (1.15 - 0.3 * tint) };
	}

	static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}

	static double clamp(double v) {
		return Math.max(0, Math.min(1, v));
	}

	static double smooth(double edge0, double edge1, double v) {
		double t = clamp((v - edge0) / (edge1 - edge0));
		return t * t * (3 - 2 * t);
	}

	static int rgb(double r, double g, double b) {
		// a gentle tone curve, so bright parts roll off instead of clipping hard
		r = 1 - Math.exp(-r * 1.4);
		g = 1 - Math.exp(-g * 1.4);
		b = 1 - Math.exp(-b * 1.4);
		return ((int) Math.round(clamp(r) * 255) << 16) | ((int) Math.round(clamp(g) * 255) << 8) | (int) Math.round(clamp(b) * 255);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// the skies

	/** Deep space with a black hole: bent starlight, a bright photon ring and a glowing, tilted disk around it. */
	static int blackHole(double x, double y, double z) {
		// where the hole is: up and to the north-east
		double hx = 0.42, hy = 0.38, hz = -0.82;
		double hl = Math.sqrt(hx * hx + hy * hy + hz * hz);
		hx /= hl;
		hy /= hl;
		hz /= hl;
		double cos = x * hx + y * hy + z * hz;
		double angle = Math.acos(Math.max(-1, Math.min(1, cos)));
		double horizon = 0.085;

		// a frame around the hole: a = sideways, b = up along the disk's tilt
		double ax = hz, ay = 0, az = -hx;
		double al = Math.sqrt(ax * ax + az * az);
		ax /= al;
		az /= al;
		double bx = hy * az - hz * ay, by = hz * ax - hx * az, bz = hx * ay - hy * ax;
		double px = x * ax + y * ay + z * az, py = x * bx + y * by + z * bz;

		// starlight bends around the hole: look a little further out than the pixel does
		double bend = angle > horizon ? horizon * horizon * 1.6 / angle : 0;
		double lx = x - hx * bend * 3, ly = y - hy * bend * 3, lz = z - hz * bend * 3;
		double ll = Math.sqrt(lx * lx + ly * ly + lz * lz);
		lx /= ll;
		ly /= ll;
		lz /= ll;

		double[] s = stars(lx, ly, lz, 0.045, 140);
		double[] s2 = stars(lx, ly, lz, 0.12, 60);
		double r = s[0] + s2[0] * 0.5, g = s[1] + s2[1] * 0.5, b = s[2] + s2[2] * 0.5;

		// faint blue and violet gas far behind
		double gas = fbm(lx * 2.2 + 3, ly * 2.2, lz * 2.2, 5);
		gas = smooth(0.45, 0.85, gas);
		r += gas * 0.10;
		g += gas * 0.05;
		b += gas * 0.20;
		double milky = Math.exp(-Math.pow((ly * 0.9 + lx * 0.3) / 0.25, 2)) * fbm(lx * 6, ly * 6, lz * 6, 4);
		r += milky * 0.10;
		g += milky * 0.09;
		b += milky * 0.13;

		// the disk, seen from slightly above: an ellipse in the hole's frame
		double dy = py / 0.22;
		double disk = Math.sqrt(px * px + dy * dy);
		if (angle < 0.6) {
			double band = smooth(horizon * 1.25, horizon * 1.6, disk) * (1 - smooth(horizon * 2.4, horizon * 5.5, disk));
			double turbulence = 0.6 + 0.4 * fbm(px * 40, py * 40, disk * 30, 3);
			double doppler = 1 + 0.75 * clamp(-px / (horizon * 4));  // the side coming towards us is brighter
			double heat = 1 - smooth(horizon * 1.3, horizon * 4.5, disk);  // white-hot inside, orange, then red
			double light = band * turbulence * doppler * 2.4;
			// in front of the hole only on the near (lower) half, it disappears behind it on the far half
			boolean behind = py > 0 && angle < horizon;
			if (!behind) {
				r += light * (1.0);
				g += light * (0.45 + 0.45 * heat);
				b += light * (0.15 + 0.55 * heat * heat);
			}
			// the far side of the disk, bent over the top of the hole into an arc
			double arcR = Math.sqrt(px * px + Math.pow(py - horizon * 0.15, 2));
			double arc = Math.exp(-Math.pow((arcR - horizon * 1.45) / (horizon * 0.22), 2)) * (py > -horizon * 0.2 ? 1 : 0.25);
			r += arc * 1.4;
			g += arc * 0.75;
			b += arc * 0.35;
		}
		// the photon ring: a thin bright circle right at the edge
		double ring = Math.exp(-Math.pow((angle - horizon * 1.08) / (horizon * 0.06), 2));
		r += ring * 1.6;
		g += ring * 1.2;
		b += ring * 0.8;
		// the hole itself
		if (angle < horizon) {
			double edge = smooth(horizon * 0.94, horizon, angle);
			r *= edge;
			g *= edge;
			b *= edge;
		}
		return rgb(r, g, b);
	}

	/** A clear day (or golden hour): a sky that brightens towards the horizon, the sun, soft clouds. */
	static int day(double x, double y, double z, boolean golden) {
		double sunElev = golden ? Math.toRadians(5) : Math.toRadians(48);
		double sunAz = golden ? Math.toRadians(250) : Math.toRadians(150);
		double sx = Math.cos(sunElev) * Math.sin(sunAz), sy = Math.sin(sunElev), sz = -Math.cos(sunElev) * Math.cos(sunAz);
		double toSun = clamp(x * sx + y * sy + z * sz);
		double elev = y;

		double r, g, b;
		if (!golden) {
			double t = Math.pow(clamp(elev), 0.45);
			r = lerp(0.70, 0.16, t);
			g = lerp(0.82, 0.38, t);
			b = lerp(0.95, 0.85, t);
		} else {
			double t = Math.pow(clamp(elev), 0.5);
			// gold at the horizon, rose above, a deep blue-violet high up
			r = lerp(1.45, 0.20, t) + 0.25 * Math.pow(toSun, 6);
			g = lerp(0.62, 0.24, t);
			b = lerp(0.30, 0.55, t);
			double rose = Math.exp(-Math.pow((elev - 0.18) / 0.15, 2));
			r += rose * 0.25;
			b += rose * 0.15;
		}
		if (elev < 0) {
			// below the horizon: a hazy band, then darker
			double d = clamp(-elev * 3);
			r = lerp(r, golden ? 0.35 : 0.42, d);
			g = lerp(g, golden ? 0.22 : 0.48, d);
			b = lerp(b, golden ? 0.22 : 0.55, d);
		}
		// sun: a bright disk and a wide glow
		double glow = Math.pow(toSun, golden ? 18 : 60) * (golden ? 1.4 : 0.9) + Math.pow(toSun, 4) * (golden ? 0.35 : 0.12);
		double disk = smooth(0.9993, 0.9996, toSun);
		r += glow * (golden ? 1.3 : 1.0) + disk * 3;
		g += glow * (golden ? 0.75 : 0.95) + disk * 2.7;
		b += glow * (golden ? 0.35 : 0.85) + disk * 2.2;

		// clouds on a flat layer above: the further away, the flatter and fainter
		if (elev > 0.02) {
			double px = x / elev, pz = z / elev;
			double c = fbm(px * 0.55 + 10, pz * 0.55, 1.7, 6);
			double cover = smooth(golden ? 0.52 : 0.55, golden ? 0.72 : 0.75, c);
			double fade = smooth(0.02, 0.25, elev);
			cover *= fade;
			double shade = 0.75 + 0.35 * fbm(px * 1.4 + 3, pz * 1.4, 4.2, 4);
			double cr, cg, cb;
			if (!golden) {
				cr = 0.98 * shade;
				cg = 0.99 * shade;
				cb = 1.02 * shade;
			} else {
				// lit from below by the low sun: orange and pink edges
				double lit = 0.6 + 0.6 * Math.pow(toSun, 3);
				cr = 1.25 * lit * shade;
				cg = 0.55 * lit * shade;
				cb = 0.45 * lit * shade;
			}
			r = lerp(r, cr, cover * 0.92);
			g = lerp(g, cg, cover * 0.92);
			b = lerp(b, cb, cover * 0.92);
		}
		return rgb(r * 0.85, g * 0.85, b * 0.85);
	}

	/** A night with stars and green-violet aurora curtains. */
	static int aurora(double x, double y, double z) {
		double elev = y;
		double t = clamp(elev);
		double r = lerp(0.03, 0.005, t), g = lerp(0.06, 0.01, t), b = lerp(0.12, 0.04, t);
		double[] s = stars(x, y, z, 0.06, 150);
		double[] s2 = stars(x, y, z, 0.2, 70);
		double starFade = smooth(-0.05, 0.15, elev);
		r += (s[0] + s2[0] * 0.4) * starFade;
		g += (s[1] + s2[1] * 0.4) * starFade;
		b += (s[2] + s2[2] * 0.4) * starFade;

		if (elev > -0.05) {
			double az = Math.atan2(x, -z);
			// two curtains that wave along the horizon
			for (int k = 0; k < 2; k++) {
				double center = 0.32 + 0.18 * k + 0.12 * (fbm(az * 1.3 + k * 7, k * 3.1, 0.5, 4) - 0.5) * 2;
				double width = 0.07 + 0.05 * k;
				double band = Math.exp(-Math.pow((elev - center) / width, 2));
				// fine vertical rays in the curtain
				double rays = 0.45 + 0.55 * fbm(az * 30 + k * 13, elev * 2, k, 3);
				double top = smooth(center - width, center + width * 1.5, elev);
				double a = band * rays * (k == 0 ? 1.2 : 0.7);
				r += a * (0.10 + 0.45 * top);
				g += a * (0.95 - 0.55 * top);
				b += a * (0.45 + 0.35 * top);
			}
		}
		if (elev < 0) {
			double d = clamp(-elev * 4);
			r = lerp(r, 0.02, d);
			g = lerp(g, 0.03, d);
			b = lerp(b, 0.05, d);
		}
		return rgb(r, g, b);
	}

	/** Colourful gas clouds in space, many stars, one bright star in the middle of it all. */
	static int nebula(double x, double y, double z) {
		double[] s = stars(x, y, z, 0.07, 160);
		double[] s2 = stars(x, y, z, 0.25, 80);
		double r = s[0] + s2[0] * 0.45, g = s[1] + s2[1] * 0.45, b = s[2] + s2[2] * 0.45;
		double n1 = fbm(x * 1.8 + 5, y * 1.8, z * 1.8, 6);
		double n2 = fbm(x * 2.6, y * 2.6 + 9, z * 2.6, 6);
		double n3 = fbm(x * 4.0 + 2, y * 4.0, z * 4.0 + 6, 5);
		double magenta = smooth(0.48, 0.8, n1);
		double teal = smooth(0.5, 0.82, n2);
		double dust = smooth(0.55, 0.75, n3);
		r += magenta * 0.75 + teal * 0.05 + dust * magenta * 0.4;
		g += magenta * 0.12 + teal * 0.55;
		b += magenta * 0.65 + teal * 0.65;
		// dark lanes of dust across the glow
		double lanes = smooth(0.62, 0.78, fbm(x * 5 + 1, y * 5 + 4, z * 5, 4));
		r *= 1 - lanes * 0.6;
		g *= 1 - lanes * 0.6;
		b *= 1 - lanes * 0.6;
		double sx = -0.3, sy = 0.45, sz = -0.84;
		double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
		double toStar = clamp((x * sx + y * sy + z * sz) / sl);
		double star = Math.pow(toStar, 400) * 3 + Math.pow(toStar, 40) * 0.5;
		r += star;
		g += star * 0.9;
		b += star;
		return rgb(r, g, b);
	}

	/** A picture of your own, as a panorama (twice as wide as high): the color in a direction. */
	public static Sky equirect(int[] argb, int width, int height) {
		return (x, y, z) -> {
			double lon = Math.atan2(x, -z);
			double lat = Math.asin(Math.max(-1, Math.min(1, y)));
			double u = (lon / (2 * Math.PI) + 0.5) * width;
			double v = (0.5 - lat / Math.PI) * height;
			return bilinear(argb, width, height, u, v, true);
		};
	}

	/** A picture of your own laid out as a cross (4 wide, 3 high): +Y on top, -X +Z +X -Z in the middle row, -Y below. */
	public static Sky cross(int[] argb, int width, int height) {
		int f = width / 4;
		return (x, y, z) -> {
			double ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z);
			int col, row;
			double s, t;
			if (ay >= ax && ay >= az) {
				col = 1;
				row = y > 0 ? 0 : 2;
				s = x / ay;
				t = y > 0 ? z / ay : -z / ay;
			} else if (ax >= az) {
				row = 1;
				col = x > 0 ? 2 : 0;
				s = x > 0 ? z / ax : -z / ax;
				t = -y / ax;
			} else {
				row = 1;
				col = z > 0 ? 1 : 3;
				s = z > 0 ? -x / az : x / az;
				t = -y / az;
			}
			double u = col * f + (s + 1) / 2 * f;
			double v = row * f + (t + 1) / 2 * f;
			return bilinear(argb, width, height, u, v, false);
		};
	}

	static int bilinear(int[] argb, int width, int height, double u, double v, boolean wrapX) {
		u -= 0.5;
		v -= 0.5;
		int x0 = (int) Math.floor(u), y0 = (int) Math.floor(v);
		double fx = u - x0, fy = v - y0;
		int[] c = new int[4];
		int[][] at = { { x0, y0 }, { x0 + 1, y0 }, { x0, y0 + 1 }, { x0 + 1, y0 + 1 } };
		for (int i = 0; i < 4; i++) {
			int px = at[i][0], py = Math.max(0, Math.min(height - 1, at[i][1]));
			px = wrapX ? Math.floorMod(px, width) : Math.max(0, Math.min(width - 1, px));
			c[i] = argb[py * width + px];
		}
		int out = 0;
		for (int shift = 0; shift <= 16; shift += 8) {
			double a = (c[0] >> shift) & 0xFF, b = (c[1] >> shift) & 0xFF, d = (c[2] >> shift) & 0xFF, e = (c[3] >> shift) & 0xFF;
			double val = lerp(lerp(a, b, fx), lerp(d, e, fx), fy);
			out |= ((int) Math.round(val) & 0xFF) << shift;
		}
		return out;
	}
}
