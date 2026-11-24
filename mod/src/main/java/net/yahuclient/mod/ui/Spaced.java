package net.yahuclient.mod.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/**
 * Text with extra space between the letters (tracking), for capital labels and the word mark. Each letter is placed on a
 * whole screen pixel, so the spacing never blurs a letter.
 */
public final class Spaced {
	private Spaced() {
	}

	public static float width(Component text, float spacing) {
		var font = Minecraft.getInstance().font;
		Style style = text.getStyle();
		String s = text.getString();
		float total = 0;
		int n = 0;
		for (int i = 0; i < s.length(); ) {
			int cp = s.codePointAt(i);
			total += font.width(Component.literal(new String(Character.toChars(cp))).withStyle(style));
			i += Character.charCount(cp);
			n++;
		}
		return total + spacing * Math.max(0, n - 1);
	}

	/** Draws text at (x, y), or centered on x. */
	public static void draw(GuiGraphicsExtractor g, Component text, float x, float y, float spacing, int color, boolean center) {
		var font = Minecraft.getInstance().font;
		Style style = text.getStyle();
		String s = text.getString();
		float scale = Draw.scale();
		float cx = center ? x - width(text, spacing) / 2f : x;
		float py = Math.round(y * scale) / scale;
		for (int i = 0; i < s.length(); ) {
			int cp = s.codePointAt(i);
			Component letter = Component.literal(new String(Character.toChars(cp))).withStyle(style);
			g.pose().pushMatrix();
			g.pose().translate(Math.round(cx * scale) / scale, py);
			g.text(font, letter, 0, 0, color, false);
			g.pose().popMatrix();
			cx += font.width(letter) + spacing;
			i += Character.charCount(cp);
		}
	}
}
