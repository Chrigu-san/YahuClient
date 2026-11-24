package net.yahuclient.mod.ui;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The life of a glass element: it leans back under the cursor (the side the cursor is on goes back, like pressing on a
 * pane that turns about its middle), sways on a soft spring, sinks further while pressed, a glare follows the cursor, and
 * rain runs down it. One state per element, kept as long as the element lives.
 */
public final class GlassFx {
	/** Whether elements lean towards the cursor. */
	public static boolean tilt = true;
	/** An icon button's glass is drawn and its warp still open: its icon comes next (SpriteIconButtonMixin closes it). */
	public static boolean iconPending;

	private static final Map<Object, State> STATES = new WeakHashMap<>();

	public static final class State {
		float rx, ry, push, vrx, vry, vpush, hover;
		float glareX = .5f, glareY = .5f;
		long last = System.nanoTime();
		final Rain rain = new Rain();

		public float hover() {
			return hover;
		}

		/** How much rain lands on this pane (1 = as on a button). */
		public void rainDensity(float d) {
			rain.density = d;
		}
	}

	private GlassFx() {
	}

	public static State of(Object key) {
		return STATES.computeIfAbsent(key, k -> new State());
	}

	/** The cursor in GUI units. */
	public static double[] mouse() {
		Minecraft mc = Minecraft.getInstance();
		var win = mc.getWindow();
		double sx = win.getGuiScaledWidth() / (double) Math.max(1, win.getScreenWidth());
		double sy = win.getGuiScaledHeight() / (double) Math.max(1, win.getScreenHeight());
		return new double[] {mc.mouseHandler.xpos() * sx, mc.mouseHandler.ypos() * sy};
	}

	/**
	 * Steps the element's spring and rain and starts drawing it leaned (end with {@link #end}). strength is how far it
	 * leans at its edge, in degrees.
	 */
	public static State begin(GuiGraphicsExtractor g, Object key, float x, float y, float w, float h, boolean hot, boolean pressed, float strength) {
		if (iconPending) {
			// an icon button didn't close its warp (a button of another mod): close it now
			iconPending = false;
			Warp.end();
		}
		State s = of(key);
		long now = System.nanoTime();
		float dt = Math.min(1 / 20f, (now - s.last) / 1e9f);
		s.last = now;
		float trx = 0, tryy = 0, tpush = 0;
		// the cursor through the element's own (possibly translated) frame: the pose's offset
		double[] m = mouse();
		float ox = g.pose().m20(), oy = g.pose().m21();
		float mx = (float) m[0] - ox, my = (float) m[1] - oy;
		if (hot && tilt) {
			float u = Math.max(0, Math.min(1, (mx - x) / w)), v = Math.max(0, Math.min(1, (my - y) / h));
			tryy = (u - .5f) * strength;
			trx = (.5f - v) * strength * Math.min(2.2f, w / Math.max(1, h)) * .7f;
			tpush = pressed ? -9 : -5;
			s.glareX = u;
			s.glareY = v;
		}
		// a soft spring: pulled to its target, slowed by damping, so it settles with a small sway (frame rate independent)
		float k = 120f, damp = 14f;
		s.vrx += ((trx - s.rx) * k - s.vrx * damp) * dt;
		s.vry += ((tryy - s.ry) * k - s.vry * damp) * dt;
		s.vpush += ((tpush - s.push) * k - s.vpush * damp) * dt;
		s.rx += s.vrx * dt;
		s.ry += s.vry * dt;
		s.push += s.vpush * dt;
		s.hover = Theme.approach(s.hover, hot ? 1 : 0, dt, 14);
		s.rain.update(w, h, s.ry);
		Warp.begin(g, x + w / 2f, y + h / 2f, s.rx, s.ry, s.push);
		return s;
	}

	public static void end() {
		Warp.end();
	}

	/**
	 * A glass button as in the design: smoky grey glass, the lower half a shade darker, a soft lip of light along the top,
	 * a bright rim on top fading down the sides, a glare under the cursor, then the rain. Draw the label after it with
	 * {@link #label}.
	 */
	public static void button(GuiGraphicsExtractor g, State s, float x, float y, float w, float h, boolean accent, boolean quiet, float alpha) {
		button(g, s, x, y, w, h, accent, quiet, alpha, Glass.RADIUS + 1);
	}

	/** A big pane of dark glass with rain on it (a menu's panel): no glare, a quiet rim. */
	public static void panel(GuiGraphicsExtractor g, State s, float x, float y, float w, float h, float r, float alpha) {
		Draw.roundGradient(g, x, y, w, h, r, Theme.alpha(0xB0161922, alpha), Theme.alpha(0xC80C0E14, alpha));
		Draw.roundGradient(g, x + 1, y + 1, w - 2, Math.min(h * .3f, 26), r, Theme.alpha(0x10D2D8E4, alpha), Theme.alpha(0x00D2D8E4, alpha));
		s.rain.draw(g, x, y, alpha, r);
		Draw.outline(g, x, y, w, h, r, Theme.alpha(0x1CC8CEDA, alpha), Theme.alpha(0x40DCE2EC, alpha));
	}

	public static void button(GuiGraphicsExtractor g, State s, float x, float y, float w, float h, boolean accent, boolean quiet, float alpha, float r) {
		float hv = s.hover;
		int top = accent ? Theme.mix(0x5296849C, 0x6C9C88E8, hv) : Theme.mix(0x2EBEC6D4, 0x40C8D0DC, hv);
		int low = accent ? Theme.mix(0x4A5642B4, 0x605E48C4, hv) : Theme.mix(0x342C303A, 0x40343844, hv);
		if (quiet) {
			top = Theme.alpha(top, .75f);
			low = Theme.alpha(low, .75f);
		}
		// body: lighter glass at the top running into a darker bottom, like a thick pane
		Draw.roundGradient(g, x, y, w, h, r, Theme.alpha(top, alpha), Theme.alpha(low, alpha));
		// a lip of light just under the top edge
		Draw.roundGradient(g, x + 1, y + 1, w - 2, Math.min(h * .45f, 14), r, Theme.alpha(0x16D2D8E4, alpha), Theme.alpha(0x00D2D8E4, alpha));
		// a soft glare under the cursor, kept inside the pane
		if (hv > .01f) {
			float[] b = bounds(x, y, w, h);
			g.enableScissor((int) Math.floor(b[0]), (int) Math.floor(b[1]), (int) Math.ceil(b[2]), (int) Math.ceil(b[3]));
			Draw.glow(g, x + s.glareX * w, y + s.glareY * h, Math.min(h * 1.1f, w * .5f), Theme.alpha(0xFFD6DCE8, .09f * hv * alpha));
			g.disableScissor();
		}
		// the rain on it
		s.rain.draw(g, x, y, alpha, r);
		// the rim: bright along the top, fading down the sides, faint at the bottom
		Draw.outline(g, x, y, w, h, r, Theme.alpha(Theme.mix(0x1CC8CEDA, 0x40DCE2EC, hv), alpha), Theme.alpha(Theme.mix(0x60DCE2EC, 0x90E6EAF2, hv), alpha));
	}

	/** Where a rectangle lands under the current warp: x0, y0, x1, y1. */
	private static float[] bounds(float x, float y, float w, float h) {
		Warp wp = Warp.current();
		if (wp == null) {
			return new float[] {x, y, x + w, y + h};
		}
		float[] a = wp.project(x, y), b = wp.project(x + w, y), c = wp.project(x, y + h), d = wp.project(x + w, y + h);
		return new float[] {Math.min(Math.min(a[0], b[0]), Math.min(c[0], d[0])), Math.min(Math.min(a[1], b[1]), Math.min(c[1], d[1])),
			Math.max(Math.max(a[0], b[0]), Math.max(c[0], d[0])), Math.max(Math.max(a[1], b[1]), Math.max(c[1], d[1]))};
	}

	/** Draws a label centered on (cx, cy) so it sits on the leaned glass: placed and scaled like the glass there. */
	public static void label(GuiGraphicsExtractor g, float cx, float cy, Runnable draw) {
		Warp w = Warp.current();
		if (w == null) {
			draw.run();
			return;
		}
		float[] p = w.project(cx, cy);
		Warp.flat(() -> {
			g.pose().pushMatrix();
			g.pose().translate(p[0], p[1]);
			g.pose().scale(p[2], p[2]);
			g.pose().translate(-cx, -cy);
			draw.run();
			g.pose().popMatrix();
		});
	}
}
