package net.yahuclient.mod.ui;

import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.yahuclient.mod.mixin.StyleAccessor;

/**
 * Menu text in the Yahu UI font instead of Minecraft's pixel font. On while a screen draws (ScreenMixin turns it on and off
 * around it), so text in the world, the chat and the HUD keep Minecraft's font. Only the render thread draws screens.
 *
 * Text is measured (StyleMixin) and recorded for drawing (GuiTextRenderStateMixin) with the same font, so centering fits.
 */
public final class MenuFont {
	public static final FontDescription FONT = Theme.UI_FONT;
	private static int depth;

	private MenuFont() {
	}

	public static boolean active() {
		return depth > 0;
	}

	public static void begin() {
		depth++;
	}

	public static void end() {
		depth = Math.max(0, depth - 1);
	}

	/** The style with the UI font if it doesn't ask for a font of its own. */
	public static Style apply(Style style) {
		return ((StyleAccessor) (Object) style).yahu$font() == null ? style.withFont(FONT) : style;
	}

	/** The same text, with the UI font wherever it had no font of its own. */
	public static FormattedCharSequence wrap(FormattedCharSequence text) {
		return sink -> text.accept((index, style, codepoint) -> sink.accept(index, apply(style), codepoint));
	}
}
