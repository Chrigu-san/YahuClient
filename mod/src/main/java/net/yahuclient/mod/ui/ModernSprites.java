package net.yahuclient.mod.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** The dark Yahu versions of Minecraft's widget sprites (see GuiGraphicsExtractorMixin). */
public final class ModernSprites {
	private ModernSprites() {
	}

	/** Draws the Yahu version of this sprite; false if it isn't one we replace. */
	public static boolean draw(GuiGraphicsExtractor g, Identifier sprite, int x, int y, int w, int h, int color) {
		if (!"minecraft".equals(sprite.getNamespace()) || !sprite.getPath().startsWith("widget/")) {
			return false;
		}
		float alpha = (color >>> 24) / 255f;
		String name = sprite.getPath().substring("widget/".length());
		switch (name) {
			case "button", "button_highlighted", "button_disabled" ->
				BlockWidgets.button(g, x, y, w, h, !name.endsWith("disabled"), name.endsWith("highlighted"), alpha);
			case "text_field", "text_field_highlighted" -> {
				boolean focused = name.endsWith("highlighted");
				Draw.card(g, x, y, w, h, 5, Theme.alpha(Theme.FIELD, alpha), Theme.alpha(focused ? Theme.ACCENT : Theme.BORDER_HI, alpha));
			}
			case "checkbox", "checkbox_highlighted", "checkbox_selected", "checkbox_selected_highlighted" -> {
				boolean on = name.contains("selected");
				boolean hot = name.endsWith("highlighted");
				if (on) {
					Draw.round(g, x, y, w, h, 4, Theme.alpha(hot ? Theme.ACCENT_HI : Theme.ACCENT, alpha));
					check(g, x, y, w, h, Theme.alpha(0xFFFFFFFF, alpha));
				} else {
					Draw.card(g, x, y, w, h, 4, Theme.alpha(Theme.FIELD, alpha), Theme.alpha(hot ? Theme.ACCENT : Theme.BORDER_HI, alpha));
				}
			}
			case "scroller" -> Draw.pill(g, x + w / 2f - 2, y, 4, h, Theme.alpha(0x55FFFFFF, alpha));
			case "scroller_background" -> Draw.pill(g, x + w / 2f - 2, y, 4, h, Theme.alpha(0x14FFFFFF, alpha));
			case "tab", "tab_highlighted", "tab_selected", "tab_selected_highlighted" -> {
				boolean selected = name.startsWith("tab_selected");
				boolean hot = name.endsWith("highlighted");
				if (selected) {
					Draw.round(g, x + 2, y + 2, w - 4, h - 4, 7, Theme.alpha(Theme.CARD_HI, alpha));
					Draw.pill(g, x + w / 2f - Math.min(14, w / 4f), y + h - 4, Math.min(28, w / 2f), 2, Theme.alpha(Theme.ACCENT, alpha));
				} else if (hot) {
					Draw.round(g, x + 2, y + 2, w - 4, h - 4, 7, Theme.alpha(Theme.CARD, alpha));
				}
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	/** A check mark: two strokes, turned. */
	private static void check(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		float s = Math.min(w, h) / 20f;
		g.pose().pushMatrix();
		g.pose().translate(x + w * 0.42f, y + h * 0.66f);
		g.pose().rotate((float) Math.toRadians(-45));
		g.pose().scale(s, s);
		g.fill(-1, -6, 2, 1, color);
		g.fill(-1, -2, 11, 1, color);
		g.pose().popMatrix();
	}
}
