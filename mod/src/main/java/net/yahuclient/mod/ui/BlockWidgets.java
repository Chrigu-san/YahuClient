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
		if (!active) {
			Draw.card(g, x, y, w, h, 6, Theme.alpha(0xFF101116, alpha * 0.9f), Theme.alpha(0x10FFFFFF, alpha));
			return;
		}
		int fill = hot ? 0xFF22252F : 0xE0181A21;
		int border = hot ? Theme.alpha(Theme.ACCENT, 0.9f) : Theme.BORDER_HI;
		Draw.card(g, x, y, w, h, 6, Theme.alpha(fill, alpha), Theme.alpha(border, alpha));
		// a faint light along the top edge
		g.fill(x + 6, y + 1, x + w - 6, y + 2, Theme.alpha(0x14FFFFFF, alpha));
	}

	/** A highlighted button: the accent, a little lighter while hovered. */
	public static void greenButton(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hot, float alpha) {
		if (hot) {
			Draw.glow(g, x + w / 2f, y + h / 2f, w * 0.7f, Theme.alpha(Theme.ACCENT, 0.25f * alpha));
		}
		Draw.round(g, x, y, w, h, 6, Theme.alpha(hot ? Theme.ACCENT_HI : Theme.ACCENT, alpha));
		g.fillGradient(x + 2, y + 1, x + w - 2, y + h / 2, Theme.alpha(0x22FFFFFF, alpha), 0x00FFFFFF);
	}

	public static void sliderTrack(GuiGraphicsExtractor g, int x, int y, int w, int h, double value, boolean active, float alpha) {
		Draw.card(g, x, y, w, h, 6, Theme.alpha(0xE0121319, alpha), Theme.alpha(Theme.BORDER, alpha));
		if (active) {
			float t = (float) Math.max(0, Math.min(1, value));
			Draw.pill(g, x + 6, y + h - 5, w - 12, 2, Theme.alpha(0xFF2A2D38, alpha));
			Draw.pill(g, x + 6, y + h - 5, Math.max(2, (w - 12) * t), 2, Theme.alpha(Theme.ACCENT, alpha));
		}
	}

	public static void sliderHandle(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hot, float alpha) {
		float cx = x + w / 2f, cy = y + h - 4;
		if (hot) {
			Draw.circle(g, cx, cy, 6, Theme.alpha(Theme.ACCENT, 0.3f * alpha));
		}
		Draw.circle(g, cx, cy, 3.5f, Theme.alpha(0xFFFFFFFF, alpha));
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
