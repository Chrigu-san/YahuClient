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

		/** Whether this direction has fine detail (thin rings, sharp edges) worth several samples per pixel. */
		default boolean detailed(double x, double y, double z) {
			return false;
		}
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
		// rows in parallel: a 1024 face sky is six million pixels
		java.util.stream.IntStream.range(0, 6 * cell).parallel().forEach(row -> {
			int f = row / cell, j = row % cell;
			double[] n = FACES[f][0], r = FACES[f][1], u = FACES[f][2];
			int ox = (f % 3) * cell, oy = (f / 3) * cell;
			for (int i = 0; i < cell; i++) {
				// the border pixels look a little beyond the face's edge, which is the neighbouring face's sky
				pixels[(oy + j) * width + ox + i] = 0xFF000000 | sample(sky, n, r, u, i, j, face);
			}
		});
		return new Atlas(pixels, width, height, face);
	}

	/** One pixel: one look in its direction, or 3 by 3 looks averaged where the sky has fine detail. */
	private static int sample(Sky sky, double[] n, double[] r, double[] u, int i, int j, int face) {
		double[] d = dir(n, r, u, i + .5, j + .5, face);
		if (!sky.detailed(d[0], d[1], d[2])) {
			return sky.color(d[0], d[1], d[2]);
		}
		int rr = 0, gg = 0, bb = 0;
		for (int sj = 0; sj < 3; sj++) {
			for (int si = 0; si < 3; si++) {
				double[] e = dir(n, r, u, i + (si + .5) / 3, j + (sj + .5) / 3, face);
				int c = sky.color(e[0], e[1], e[2]);
				rr += (c >> 16) & 0xFF;
				gg += (c >> 8) & 0xFF;
				bb += c & 0xFF;
			}
		}
		return ((rr / 9) << 16) | ((gg / 9) << 8) | (bb / 9);
	}

	private static double[] dir(double[] n, double[] r, double[] u, double i, double j, int face) {
		double t = 1 - (j - PAD) / face * 2;
		double s = (i - PAD) / face * 2 - 1;
		double x = n[0] + s * r[0] + t * u[0];
		double y = n[1] + s * r[1] + t * u[1];
		double z = n[2] + s * r[2] + t * u[2];
		double len = Math.sqrt(x * x + y * y + z * z);
		return new double[] {x / len, y / len, z / len};
	}
	public static Sky preset(SkyPreset preset) {
		return switch (preset) {
			case BLACK_HOLE -> new BlackHole();
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
		double size = 0.08 + 0.13 * hash(cx + 5, cy + 5, cz);
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

	/**
	 * Deep space with a black hole, in the style of the well known renderings: the dark shadow, a thin blinding photon ring,
	 * a hot disk seen almost edge on crossing in front of the hole, and the disk's far side bent by gravity into a bright
	 * arc over the top (and a fainter one under the bottom). The stars behind are lensed with the lens equation: near the
	 * hole they are pulled into a ring and doubled. The side of the disk turning towards us is brighter and whiter.
	 */
	public static final class BlackHole implements Sky {
		// where the hole is (up and to the north-east), and a frame around it: a sideways, b up along the disk's tilt
		private static final double HX, HY, HZ, AX, AY, AZ, BX, BY, BZ;
		private static final double SHADOW = 0.075;            // radius of the shadow, radians
		private static final double EINSTEIN = SHADOW * 1.32;  // Einstein ring of the lens
		private static final double INNER = SHADOW * 1.55, OUTER = SHADOW * 6.5;
		private static final double TILT = 0.16;               // how open the disk looks (cos of the view angle)

		static {
			double hx = 0.42, hy = 0.34, hz = -0.84, hl = Math.sqrt(hx * hx + hy * hy + hz * hz);
			HX = hx / hl;
			HY = hy / hl;
			HZ = hz / hl;
			double ax = HZ, az = -HX, al = Math.sqrt(ax * ax + az * az);
			// a slight roll of the disk
			double roll = 0.12;
			double ax0 = ax / al, ay0 = 0, az0 = az / al;
			double bx0 = HY * az0 - HZ * ay0, by0 = HZ * ax0 - HX * az0, bz0 = HX * ay0 - HY * ax0;
			AX = ax0 * Math.cos(roll) + bx0 * Math.sin(roll);
			AY = ay0 * Math.cos(roll) + by0 * Math.sin(roll);
			AZ = az0 * Math.cos(roll) + bz0 * Math.sin(roll);
			BX = bx0 * Math.cos(roll) - ax0 * Math.sin(roll);
			BY = by0 * Math.cos(roll) - ay0 * Math.sin(roll);
			BZ = bz0 * Math.cos(roll) - az0 * Math.sin(roll);
		}

		@Override
		public boolean detailed(double x, double y, double z) {
			return x * HX + y * HY + z * HZ > Math.cos(0.75);
		}

		@Override
		public int color(double x, double y, double z) {
			double cos = Math.max(-1, Math.min(1, x * HX + y * HY + z * HZ));
			double theta = Math.acos(cos);
			// position in the plane around the hole (radians, small angles): along the disk and across it
			double px = x * AX + y * AY + z * AZ, py = x * BX + y * BY + z * BZ;
			double pr = Math.max(1e-9, Math.hypot(px, py));
			double ux = px / pr, uy = py / pr;
			double r = 0, g = 0, b = 0;

			// the sky behind, lensed: a ray passing at theta comes from beta = theta - E^2 / theta (negative: the far side)
			if (theta > SHADOW) {
				double beta = theta - EINSTEIN * EINSTEIN / theta;
				double[] d = theta < 1.2 ? toward(x, y, z, cos, theta, beta) : new double[] {x, y, z};
				double magnify = theta < 1.2 ? Math.min(3, Math.max(1, theta / Math.max(1e-4, Math.abs(beta)))) : 1;
				double[] s = stars(d[0], d[1], d[2], 0.05, 150);
				double[] s2 = stars(d[0], d[1], d[2], 0.14, 62);
				r += (s[0] + s2[0] * 0.5) * magnify;
				g += (s[1] + s2[1] * 0.5) * magnify;
				b += (s[2] + s2[2] * 0.5) * magnify;
				double gas = smooth(0.48, 0.86, fbm(d[0] * 2.2 + 3, d[1] * 2.2, d[2] * 2.2, 6));
				r += gas * 0.07;
				g += gas * 0.04;
				b += gas * 0.16;
				double band = Math.exp(-Math.pow((d[1] * 0.9 + d[0] * 0.3) / 0.22, 2)) * fbm(d[0] * 7, d[1] * 7, d[2] * 7, 5);
				r += band * 0.09;
				g += band * 0.08;
				b += band * 0.12;
			}

			if (theta < 0.75) {
				// the disk seen directly: an ellipse; its far half hides behind the shadow
				double rd = Math.hypot(px, py / TILT);
				boolean hidden = theta < SHADOW && py > 0;
				if (!hidden) {
					double[] c = disk(rd, px, Math.atan2(py / TILT, px));
					r += c[0];
					g += c[1];
					b += c[2];
				}
				// the far side of the disk, lensed into a ring around the shadow: brightest over the top
				if (theta > SHADOW && theta < SHADOW * 2.4) {
					// rays just outside the shadow went round the hole and see the disk behind it: a narrow band hugging the
					// shadow, the disk's inner edge right at it; bright over the top, a faint second image under the bottom
					double rv = INNER + (theta - SHADOW * 1.02) / (SHADOW * 1.2) * (OUTER - INNER) * 0.35;
					double fade = Math.exp(-Math.pow((theta - SHADOW * 1.1) / (SHADOW * 0.36), 2));
					double top = smooth(-0.15, 0.75, uy), bottom = smooth(0.25, 0.95, -uy);
					double[] c = disk(rv, px, Math.atan2(uy, ux) * 3);
					double halo = (top * 1.05 + bottom * 0.4) * fade;
					r += c[0] * halo;
					g += c[1] * halo;
					b += c[2] * halo;
				}
			}

			// the photon ring: thin, right at the edge of the shadow
			double ring = Math.exp(-Math.pow((theta - SHADOW * 1.015) / (SHADOW * 0.018), 2));
			double ringDoppler = 1 + 0.5 * clamp(-ux);
			r += ring * 2.2 * ringDoppler;
			g += ring * 1.75 * ringDoppler;
			b += ring * 1.2 * ringDoppler;

			// the shadow itself, with an edge a fraction of a pixel soft
			if (theta < SHADOW) {
				double edge = smooth(SHADOW * 0.985, SHADOW, theta);
				r *= edge;
				g *= edge;
				b *= edge;
			}
			return rgb(r, g, b);
		}

		/** A point of the disk at radius rd: brighter inside, streaky, the side coming towards us brighter and whiter. */
		private static double[] disk(double rd, double px, double phi) {
			if (rd < INNER * 0.9 || rd > OUTER) {
				return new double[] {0, 0, 0};
			}
			double edge = smooth(INNER * 0.9, INNER * 1.08, rd) * (1 - smooth(OUTER * 0.55, OUTER, rd));
			double falloff = Math.pow(INNER / rd, 1.9);
			// streaks: fine in radius, long around the disk
			double streak = 0.55 + 0.45 * fbm(rd * 230, Math.cos(phi) * 2.2, Math.sin(phi) * 2.2, 4);
			streak *= 0.8 + 0.2 * noise(rd * 900, phi * 3, 7.3);
			double doppler = 1 + 0.85 * clamp(-px / (rd + 1e-6));     // approaching on the left
			double dim = 1 - 0.45 * clamp(px / (rd + 1e-6));           // receding on the right
			double light = edge * falloff * streak * doppler * dim * 2.6;
			double heat = clamp(Math.pow(INNER / rd, 1.3)) * (0.75 + 0.25 * doppler / 1.85);
			return new double[] {light * 1.0, light * (0.5 + 0.42 * heat), light * (0.2 + 0.62 * heat * heat)};
		}

		/** The direction at angle beta from the hole on the pixel's side (or the opposite side if beta is negative). */
		private static double[] toward(double x, double y, double z, double cos, double theta, double beta) {
			double tx = x - HX * cos, ty = y - HY * cos, tz = z - HZ * cos;
			double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
			if (tl < 1e-9) {
				return new double[] {x, y, z};
			}
			tx /= tl;
			ty /= tl;
			tz /= tl;
			double c = Math.cos(beta), s = Math.sin(beta);
			return new double[] {HX * c + tx * s, HY * c + ty * s, HZ * c + tz * s};
		}
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
