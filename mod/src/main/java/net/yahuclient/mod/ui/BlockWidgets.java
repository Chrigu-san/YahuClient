package net.yahuclient.mod.ui;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Minecraft's own buttons, sliders and menu backgrounds in the dark Yahu look: rounded dark buttons with a thin border that
 * lights up in the accent on hover, slim sliders with a round knob. The mixins in net.yahuclient.mod.mixin call these in
 * place of the vanilla sprites; the text on top stays vanilla's.
 */
public final class BlockWidgets {
	/** Buttons drawn in the accent instead of dark: our "Yahu Mods" entries. */
	public static final Set<Object> HIGHLIGHTED = Collections.newSetFromMap(new WeakHashMap<>());

	private BlockWidgets() {
	}

	public static void button(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean active, boolean hot, float alpha) {
		Glass.button(g, x, y, w, h, active, hot, alpha);
	}

	/** A highlighted button: glass tinted in the accent. */
	public static void greenButton(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hot, float alpha) {
		Glass.tinted(g, x, y, w, h, hot, alpha);
	}

	/** A slider: a glass button, a brighter frost up to the value. */
	public static void sliderTrack(GuiGraphicsExtractor g, int x, int y, int w, int h, double value, boolean active, float alpha) {
		Glass.button(g, x, y, w, h, active, false, alpha);
		if (active) {
			float t = (float) Math.max(0, Math.min(1, value));
			float end = 4 + (w - 8) * t;
			Draw.round(g, x + 1, y + 1, Math.max(4, end), h - 2, 4, Theme.alpha(0x1EFFFFFF, alpha));
		}
	}

	/** The slider's handle: a slim white pill with a soft light around it. */
	public static void sliderHandle(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hot, float alpha) {
		float cx = x + w / 2f;
		if (hot) {
			Draw.glow(g, cx, y + h / 2f, 10, Theme.alpha(0xFFFFFFFF, 0.25f * alpha));
		}
		Draw.pill(g, cx - 1.5f, y + 4, 3, h - 8, Theme.alpha(0xFFFFFFFF, alpha));
	}

	/** The menu background outside a world. */
	public static void wall(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		Background.draw(g, x, y, w, h);
	}

	/** The background inside a world: the game under a dark veil. */
	public static void veil(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		Background.veil(g, x, y, w, h);
	}
}
