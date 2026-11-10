package net.yahuclient.mod.lyrics;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.yahuclient.mod.config.LyricsStyle;
import net.yahuclient.mod.config.YahuConfig;
import net.yahuclient.mod.ui.Theme;

/**
 * Lays out and animates the lyrics, the same in the sky and on the screen; a {@link Sink} puts the text where it belongs.
 *
 * - Line: the whole line, the sung part coloring in letter by letter.
 * - Word by word: each word appears when it is sung, popping in; the word being sung is in the sung color.
 * - Kinetic: the same, but stacked like a poster, words big and small, tilted.
 *
 * Coordinates are font pixels around (0, 0), the middle of the lyrics block.
 */
public final class LyricsPainter {
	/** Where text goes: a text at a center point, scaled and tilted, with an outline of the given width. */
	public interface Sink {
		void text(Component text, float centerX, float centerY, float scale, float rotation, int color, int outlineColor, int outlineWidth);
	}

	/** What to show: the moment of the song, or sample text for previews and tests. */
	public record Frame(List<SyncedLyrics.Word> words, long positionMs, double lineProgress, String next, String title, Object lineKey) {
	}

	private static final long POP_MS = 240;

	private LyricsPainter() {
	}

	public static Component styled(String text, YahuConfig.Lyrics config) {
		FontDescription font = config.font.font;
		return Component.literal(config.uppercase ? text.toUpperCase() : text).withStyle(Style.EMPTY.withFont(font));
	}

	/**
	 * Draws a frame around (0, 0); returns the height of the lyrics block in font pixels. The lines wrap at maxWidth font pixels:
	 * the caller works out how many fit its share of the view (the sky and the screen measure differently).
	 */
	public static float paint(Sink sink, Font font, YahuConfig.Lyrics config, Frame frame, float maxWidth) {
		List<String> texts = new ArrayList<>();
		for (var w : frame.words()) {
			texts.add(w.text());
		}
		int lineHeight = font.lineHeight;
		LyricLayout.Layout layout = LyricLayout.layout(texts, config.style, s -> font.width(styled(s, config)), lineHeight,
				Math.max(40, Math.round(maxWidth)));
		int outline = Math.max(0, config.outline);
		int outlineColor = config.outlineColor | 0xFF000000;
		int sung = config.sungColor | 0xFF000000;
		int upcoming = config.upcomingColor | 0xFF000000;
		int count = frame.words().size();

		// the word being sung: the last one whose time has come
		int currentWord = -1;
		for (int i = 0; i < count; i++) {
			if (frame.words().get(i).startMs() <= frame.positionMs()) {
				currentWord = i;
			}
		}

		for (LyricLayout.Placed p : layout.words()) {
			SyncedLyrics.Word word = frame.words().get(p.index());
			long since = frame.positionMs() - word.startMs();
			Component text = styled(p.text(), config);
			// along the line the sung color turns towards cyan, if the gradient is on
			int sungHere = config.gradient && count > 1 ? Theme.mix(sung, Theme.ACCENT_2, p.index() / (float) (count - 1)) : sung;

			if (config.style == LyricsStyle.LINE) {
				long end = p.index() + 1 < count ? frame.words().get(p.index() + 1).startMs() : word.startMs() + 600;
				float done = since <= 0 ? 0 : Math.min(1f, since / (float) Math.max(1, end - word.startMs()));
				sweepWord(sink, font, config, p, text, done, sungHere, upcoming, outlineColor, outline);
				continue;
			}

			if (since < 0) {
				continue;  // not sung yet: not there yet
			}
			float t = Math.min(1f, since / (float) POP_MS);
			float pop = Math.max(0, Math.min(100, config.popStrength)) / 100f;
			float scale = p.scale() * (1f - pop * 0.45f * (1f - Theme.easeOutBack(t)));
			float rise = (1f - Theme.easeOutCubic(t)) * 6f * pop;
			float alpha = Theme.easeOutCubic(Math.min(1f, t * 1.6f));
			boolean isCurrent = p.index() == currentWord;
			int color = isCurrent ? sungHere : Theme.mix(sungHere, upcoming, Math.min(1f, (since - POP_MS) / 600f));
			if (config.style == LyricsStyle.KINETIC && !isCurrent) {
				color = Theme.alpha(color, 0.9f);
			}
			float tilt = p.rotation() * (0.4f + 0.6f * Theme.easeOutCubic(t));
			sink.text(text, p.x(), p.y() + rise, scale, tilt, Theme.alpha(color, alpha), Theme.alpha(outlineColor, alpha), outline);
		}

		float half = layout.height() / 2f;
		if (config.showSongTitle && frame.title() != null && !frame.title().isEmpty()) {
			Component title = Component.literal("♪  " + frame.title()).withStyle(Style.EMPTY.withFont(Theme.UI_FONT));
			sink.text(title, 0, -half - lineHeight * 0.9f, 0.55f, 0, Theme.alpha(0xFFFFFFFF, 0.75f), Theme.alpha(outlineColor, 0.75f),
					Math.min(1, outline));
		}
		if (config.showNext && frame.next() != null && !frame.next().isEmpty()) {
			Component next = styled(frame.next(), config);
			float scale = 0.5f;
			// the next line wraps by being cut, it is only a hint
			sink.text(next, 0, half + lineHeight * 0.9f, scale, 0, Theme.alpha(upcoming, 0.5f), Theme.alpha(outlineColor, 0.5f), Math.min(1, outline));
		}
		return layout.height();
	}

	/** A word in the line style: the sung part in the sung color, letter by letter, the rest in the upcoming color. */
	private static void sweepWord(Sink sink, Font font, YahuConfig.Lyrics config, LyricLayout.Placed p, Component whole, float done, int sung,
			int upcoming, int outlineColor, int outline) {
		String text = config.uppercase ? p.text().toUpperCase() : p.text();
		int[] cps = text.codePoints().toArray();
		float width = font.width(whole);
		float letters = 0;
		for (int cp : cps) {
			letters += font.width(Component.literal(new String(Character.toChars(cp))).withStyle(Style.EMPTY.withFont(config.font.font)));
		}
		// letters measured alone add up a little wider than the word (kerning); squeeze them to the word's width
		float fit = letters > 0 ? width / letters : 1f;
		float x = p.x() - width / 2f;
		double sungLetters = done * cps.length;
		for (int i = 0; i < cps.length; i++) {
			String letter = new String(Character.toChars(cps[i]));
			Component c = Component.literal(letter).withStyle(Style.EMPTY.withFont(config.font.font));
			float w = font.width(c) * fit;
			int color;
			float lift = 0;
			if (i < Math.floor(sungLetters)) {
				color = sung;
			} else if (i == Math.floor(sungLetters) && done > 0 && done < 1) {
				float part = (float) (sungLetters - Math.floor(sungLetters));
				color = Theme.mix(upcoming, sung, part);
				lift = (float) Math.sin(part * Math.PI) * 1.2f;
			} else {
				color = upcoming;
			}
			sink.text(c, x + w / 2f, p.y() - lift, 1f, 0, color, outlineColor, outline);
			x += w;
		}
	}
}
