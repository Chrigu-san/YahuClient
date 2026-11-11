package net.yahuclient.mod.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/**
 * The in-game look: a dark theme of its own (not the launcher's blocks, not vanilla stone). Near-black surfaces stacked in
 * a few steps of grey, thin light borders instead of bevels, a violet accent with a cyan second tone, and Instrument Sans
 * for text with Unbounded for headings.
 */
public final class Theme {
	// surfaces, darkest first
	public static final int BG = 0xFF0A0B0F;
	public static final int PANEL = 0xF0111218;
	public static final int CARD = 0xFF16181F;
	public static final int CARD_HI = 0xFF1D2029;
	public static final int FIELD = 0x40000000;
	public static final int BORDER = 0x1FFFFFFF;
	public static final int BORDER_HI = 0x38FFFFFF;

	public static final int TEXT = 0xFFF2F3F7;
	public static final int MUTED = 0xFFB8BDC8;
	public static final int FAINT = 0xFF858B98;

	public static final int ACCENT = 0xFF8B6CFF;     // violet
	public static final int ACCENT_HI = 0xFFA892FF;
	public static final int ACCENT_2 = 0xFF3FD5F2;   // cyan
	public static final int GOOD = 0xFF3DDC97;
	public static final int WARN = 0xFFFFB547;
	public static final int BAD = 0xFFFF5C7A;

	public static final Identifier UI_FONT_ID = Identifier.fromNamespaceAndPath("yahu", "ui");
	public static final Identifier DISPLAY_FONT_ID = Identifier.fromNamespaceAndPath("yahu", "display");
	public static final Identifier PIXEL_FONT_ID = Identifier.fromNamespaceAndPath("yahu", "pixel");
	public static final FontDescription UI_FONT = new FontDescription.Resource(UI_FONT_ID);
	public static final FontDescription DISPLAY_FONT = new FontDescription.Resource(DISPLAY_FONT_ID);

	private Theme() {
	}

	/** Text in the UI font. */
	public static MutableComponent ui(String text) {
		return Component.literal(text).withStyle(Style.EMPTY.withFont(UI_FONT));
	}

	/** Text in the heading font. */
	public static MutableComponent display(String text) {
		return Component.literal(text).withStyle(Style.EMPTY.withFont(DISPLAY_FONT));
	}

	public static int alpha(int color, float alpha) {
		int a = Math.round(((color >>> 24) / 255f) * Math.max(0, Math.min(1, alpha)) * 255);
		return (a << 24) | (color & 0xFFFFFF);
	}

	public static int mix(int a, int b, float t) {
		return YahuUi.mix(a, b, t);
	}

	/** Moves value towards target, the same speed at any frame rate; rate is how quick (about 1/time in seconds). */
	public static float approach(float value, float target, float seconds, float rate) {
		return value + (target - value) * (1f - (float) Math.exp(-rate * seconds));
	}

	public static float easeOutCubic(float t) {
		t = Math.max(0, Math.min(1, t));
		return 1 - (1 - t) * (1 - t) * (1 - t);
	}

	/** Overshoots a little and settles: for things that pop in. */
	public static float easeOutBack(float t) {
		t = Math.max(0, Math.min(1, t));
		float c = 1.70158f;
		return 1 + (c + 1) * (float) Math.pow(t - 1, 3) + c * (float) Math.pow(t - 1, 2);
	}
}
