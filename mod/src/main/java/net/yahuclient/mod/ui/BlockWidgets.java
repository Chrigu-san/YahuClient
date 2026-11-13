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
			Draw.round(g, x, y, Math.max(12, end), h, 6, Theme.alpha(0x1AFFFFFF, alpha));
		}
	}

	/** The slider's handle: a short white pill along the bottom edge, under the label instead of over it. */
	public static void sliderHandle(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hot, float alpha) {
		float cx = x + w / 2f;
		float pw = hot ? 12 : 9;
		Draw.pill(g, cx - pw / 2f, y + h - 4.5f, pw, 2.5f, Theme.alpha(0xFFFFFFFF, alpha));
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
