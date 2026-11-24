package net.yahuclient.mod.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Rain on a pane of glass, with real behaviour: every drop has a mass that grows with its area; small beads cling where
 * they land, heavier drops are pulled down the glass. The glass is not even (a smooth friction field), so a running drop
 * hesitates, speeds up and swerves instead of falling straight; it leaves tiny beads behind and swallows the ones in its
 * way, getting heavier and faster. Tilting the pane tips its gravity sideways. Sizes are in GUI pixels.
 */
public final class Rain {
	/** How much rain: 0 none, 1 light, 2 strong. */
	public static int level = 2;

	private static final float CLING = .36f;     // radius below which a bead just sticks
	private static final float MAX_R = 1.35f;
	private static final float G = 420f;         // pull along the glass for a heavy drop, GUI px/s²

	private final List<Drop> drops = new ArrayList<>();
	/** How much of the rain lands on this pane (big panels get less, so it doesn't turn into grain). */
	public float density = 1;
	private final Random random = new Random();
	private final float seed = new Random().nextFloat() * 100;
	private float w, h;
	private long last = System.nanoTime();

	private static final class Drop {
		float x, y, r, vx, vy, trail;
		boolean dead;

		Drop(float x, float y, float r) {
			this.x = x;
			this.y = y;
			this.r = r;
		}
	}

	/** Steps the rain for a pane of this size; tilt is the pane's lean to the right in degrees. */
	public void update(float width, float height, float tiltDeg) {
		long now = System.nanoTime();
		float dt = Math.min(1 / 30f, (now - last) / 1e9f);
		last = now;
		if (width != w || height != h) {
			w = width;
			h = height;
			drops.clear();
			// already wet: a dusting of tiny beads
			int n = Math.round(w * h / 70 * density);
			for (int i = 0; i < n; i++) {
				drops.add(new Drop(random.nextFloat() * w, random.nextFloat() * h, .14f + (float) Math.pow(random.nextFloat(), 2) * .3f));
			}
		}
		if (level == 0) {
			return;
		}
		float gx = (float) Math.sin(Math.toRadians(tiltDeg)) * 1.4f + .06f, gy = 1;

		// rain keeps landing: mostly fine beads, often a heavier drop that runs at once
		float rate = (level == 2 ? 3f : 1.2f) * w * h / 1400f * density;
		float n = rate * dt;
		while (n > 0) {
			if (random.nextFloat() < n) {
				boolean big = random.nextFloat() < .45f;
				Drop d = new Drop(random.nextFloat() * w, random.nextFloat() * h * (big ? .5f : 1f),
						big ? .42f + random.nextFloat() * .42f : .14f + random.nextFloat() * .22f);
				if (big) {
					d.vy = 4 + random.nextFloat() * 6;
				}
				drops.add(d);
			}
			n -= 1;
		}

		List<Drop> born = new ArrayList<>();
		for (Drop p : drops) {
			if (p.r < CLING) {
				p.vx = p.vy = 0;
				continue;
			}
			float m = p.r * p.r;
			float mu = friction(p.x, p.y);
			float pull = G * Math.min(1, (p.r - CLING) / .8f);
			float ax = gx * pull, ay = gy * pull;
			float sp = (float) Math.hypot(p.vx, p.vy);
			float drag = mu * 7f / Math.max(.2f, m);
			ax -= p.vx * drag;
			ay -= p.vy * drag;
			// sticky patches push the path aside
			float e = .5f, side = friction(p.x + e, p.y) - friction(p.x - e, p.y);
			ax -= side * 500 * Math.min(1, sp / 9);
			p.vx += ax * dt;
			p.vy += ay * dt;
			p.x += p.vx * dt;
			p.y += p.vy * dt;
			// water left behind
			p.trail += sp * dt;
			if (p.trail > 1.2f + random.nextFloat() * 1.8f) {
				p.trail = 0;
				float lost = .1f + random.nextFloat() * .08f;
				born.add(new Drop(p.x - p.vx * .01f, p.y - p.vy * .012f - p.r * .7f, lost));
				p.r = (float) Math.sqrt(Math.max(.04f, p.r * p.r - lost * lost * .7f));
			}
		}
		drops.addAll(born);

		// touching drops merge: areas add up, momentum is kept
		for (Drop a : drops) {
			if (a.dead || a.r < CLING) {
				continue;
			}
			for (Drop q : drops) {
				if (q == a || q.dead || Math.abs(q.y - a.y) > 3) {
					continue;
				}
				if (Math.hypot(q.x - a.x, q.y - a.y) < (a.r + q.r) * .85f) {
					float ma = a.r * a.r, mq = q.r * q.r, mm = ma + mq;
					a.vx = (a.vx * ma + q.vx * mq) / mm;
					a.vy = (a.vy * ma + q.vy * mq) / mm;
					a.x = (a.x * ma + q.x * mq) / mm;
					a.y = (a.y * ma + q.y * mq) / mm;
					a.r = Math.min(MAX_R, (float) Math.sqrt(mm));
					q.dead = true;
				}
			}
		}
		drops.removeIf(p -> p.dead || p.y - p.r > h + 1 || p.x < -2 || p.x > w + 2);
		int max = Math.round(w * h / 40 * density);
		if (drops.size() > max) {
			drops.subList(0, drops.size() - max).clear();
		}
	}

	/** Draws the drops with the pane's top left corner at (x, y); only those lying wholly on the pane (corner radius). */
	public void draw(GuiGraphicsExtractor g, float x, float y, float alpha, float corner) {
		draw(g, x, y, alpha, x, y, w, h, corner);
	}

	/**
	 * Draws the drops with their area's top left corner at (x, y), only those lying wholly inside the rounded rectangle
	 * (cx, cy, cw, ch, corner): a drop never hangs over the edge of the glass into the air. Under a warp they follow its tilt.
	 */
	public void draw(GuiGraphicsExtractor g, float x, float y, float alpha, float clipX, float clipY, float clipW, float clipH, float corner) {
		if (level == 0) {
			return;
		}
		float hx = clipW / 2f, hy = clipH / 2f, mx = clipX + hx, my = clipY + hy;
		float cr = Math.min(corner, Math.min(hx, hy));
		for (Drop p : drops) {
			float cx = x + p.x, cy = y + p.y, r = p.r;
			// distance from the drop's middle to the glass's rounded edge (negative inside); the whole drop must fit
			float qx = Math.abs(cx - mx) - hx + cr, qy = Math.abs(cy - my) - hy + cr;
			float edge = (float) Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - cr;
			if (edge > -(r + .35f)) {
				continue;
			}
			if (r < .26f) {
				// a fine bead: a faint dark speck with a soft glint, so it reads as water, not as dust
				Draw.circle(g, cx, cy, Math.max(.22f, r), Theme.alpha(0x2806080E, alpha));
				Draw.circle(g, cx - r * .2f, cy - r * .25f, Math.max(.14f, r * .5f), Theme.alpha(0x40DCE2EC, alpha));
				continue;
			}
			float speed = (float) Math.hypot(p.vx, p.vy);
			// a small water lens: dark rim, lighter body, a bright glint at the top left; running drops trail a little
			if (speed > 2) {
				Draw.circle(g, cx - p.vx * .012f, cy - p.vy * .018f, r * .7f, Theme.alpha(0x3A06080E, alpha));
			}
			Draw.circle(g, cx, cy, r, Theme.alpha(0x6006080E, alpha));
			Draw.circle(g, cx - r * .12f, cy - r * .15f, r * .74f, Theme.alpha(0x30C4CCDA, alpha));
			Draw.circle(g, cx - r * .32f, cy - r * .38f, Math.max(.16f, r * .26f), Theme.alpha(0x96EAEEF4, alpha));
		}
	}

	// smooth value noise: how sticky the glass is at a point
	private float friction(float x, float y) {
		return .55f + .45f * noise(x * .16f + seed, y * .16f - seed) + .25f * noise(x * .55f - seed, y * .55f + seed);
	}

	private static float hash(int x, int y) {
		double s = Math.sin(x * 127.1 + y * 311.7) * 43758.5453;
		return (float) (s - Math.floor(s));
	}

	private static float noise(float x, float y) {
		int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
		float xf = x - xi, yf = y - yi;
		float u = xf * xf * (3 - 2 * xf), v = yf * yf * (3 - 2 * yf);
		float a = hash(xi, yi), b = hash(xi + 1, yi), c = hash(xi, yi + 1), d = hash(xi + 1, yi + 1);
		return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
	}
}
