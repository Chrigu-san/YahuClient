package net.yahuclient.mod.lyrics;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

import net.yahuclient.mod.config.LyricsStyle;

/**
 * Places the words of a line, in font pixels around (0, 0): wrapped into centered rows no wider than the maximum (so a long
 * line never runs off), or for the kinetic style stacked like a poster, each word with its own size and tilt.
 *
 * The kinetic look is chosen from the line's text, so the same line always looks the same and nothing jumps while it plays.
 */
public final class LyricLayout {
	/** A placed word: its center, size and tilt. */
	public record Placed(String text, int index, float x, float y, float scale, float rotation) {
	}

	public record Layout(List<Placed> words, float width, float height) {
	}

	// the kinetic rhythm: sizes and tilts that repeat in a pattern picked per line
	private static final float[] SIZES = { 1.55f, 0.85f, 1.15f, 0.75f, 1.3f, 0.95f };
	private static final float[] TILTS = { -6f, 4f, 0f, 7f, -3f, 2f };

	private LyricLayout() {
	}

	public static Layout layout(List<String> words, LyricsStyle style, ToIntFunction<String> width, int lineHeight, int maxWidth) {
		return style == LyricsStyle.KINETIC ? kinetic(words, width, lineHeight, maxWidth) : rows(words, width, lineHeight, maxWidth);
	}

	private record Row(List<Integer> words, float width, float height) {
	}

	/** Plain rows: words left to right with a space between, wrapped before the maximum width, each row centered. */
	static Layout rows(List<String> words, ToIntFunction<String> width, int lineHeight, int maxWidth) {
		int space = width.applyAsInt(" ");
		List<List<Integer>> rows = new ArrayList<>();
		List<Integer> row = new ArrayList<>();
		float rowWidth = 0;
		for (int i = 0; i < words.size(); i++) {
			int w = width.applyAsInt(words.get(i));
			float next = row.isEmpty() ? w : rowWidth + space + w;
			if (!row.isEmpty() && next > maxWidth) {
				rows.add(row);
				row = new ArrayList<>();
				next = w;
			}
			row.add(i);
			rowWidth = next;
		}
		if (!row.isEmpty()) {
			rows.add(row);
		}
		List<Placed> placed = new ArrayList<>();
		float rowStep = lineHeight + 3;
		float height = rows.size() * rowStep - 3;
		float widest = 0;
		for (int r = 0; r < rows.size(); r++) {
			List<Integer> ids = rows.get(r);
			float total = -space;
			for (int id : ids) {
				total += width.applyAsInt(words.get(id)) + space;
			}
			widest = Math.max(widest, total);
			float x = -total / 2;
			float y = -height / 2 + r * rowStep + lineHeight / 2f;
			for (int id : ids) {
				int w = width.applyAsInt(words.get(id));
				placed.add(new Placed(words.get(id), id, x + w / 2f, y, 1f, 0f));
				x += w + space;
			}
		}
		return new Layout(placed, widest, Math.max(0, height));
	}

	/**
	 * The poster: words get sizes and tilts from a pattern; a big word takes a row of its own, small ones share a row, and the
	 * rows stack downwards, centered, each nudged a little sideways so the block doesn't look like a table.
	 */
	static Layout kinetic(List<String> words, ToIntFunction<String> width, int lineHeight, int maxWidth) {
		int seed = Math.abs(String.join(" ", words).hashCode());
		int space = width.applyAsInt(" ");
		float rowLimit = maxWidth * 0.8f;
		List<Row> rows = new ArrayList<>();
		List<Integer> current = new ArrayList<>();
		float rowWidth = 0, rowHeight = 0;
		float[] scales = new float[words.size()];
		float[] tilts = new float[words.size()];
		for (int i = 0; i < words.size(); i++) {
			float s = SIZES[(seed + i) % SIZES.length];
			// short filler words ("a", "to") never get the biggest size
			if (words.get(i).length() <= 2) {
				s = Math.min(s, 1.0f);
			}
			scales[i] = s;
			tilts[i] = TILTS[(seed / 7 + i) % TILTS.length];
			float w = width.applyAsInt(words.get(i)) * s;
			boolean big = s >= 1.5f;
			boolean fits = current.isEmpty() || rowWidth + space * s + w <= rowLimit;
			if (!current.isEmpty() && (big || !fits)) {
				rows.add(new Row(current, rowWidth, rowHeight));
				current = new ArrayList<>();
				rowWidth = 0;
				rowHeight = 0;
			}
			rowWidth = current.isEmpty() ? w : rowWidth + space * s + w;
			rowHeight = Math.max(rowHeight, lineHeight * s);
			current.add(i);
			if (big) {
				rows.add(new Row(current, rowWidth, rowHeight));
				current = new ArrayList<>();
				rowWidth = 0;
				rowHeight = 0;
			}
		}
		if (!current.isEmpty()) {
			rows.add(new Row(current, rowWidth, rowHeight));
		}
		float total = 0;
		for (Row r : rows) {
			total += r.height() + 2;
		}
		total = Math.max(0, total - 2);
		List<Placed> placed = new ArrayList<>();
		float y = -total / 2;
		float widest = 0;
		for (int r = 0; r < rows.size(); r++) {
			Row row = rows.get(r);
			float nudge = ((seed >> r) % 3 - 1) * 6f;
			float x = -row.width() / 2 + nudge;
			widest = Math.max(widest, row.width() + Math.abs(nudge) * 2);
			for (int id : row.words()) {
				float s = scales[id];
				float w = width.applyAsInt(words.get(id)) * s;
				placed.add(new Placed(words.get(id), id, x + w / 2f, y + row.height() / 2f, s, tilts[id]));
				x += w + space * s;
			}
			y += row.height() + 2;
		}
		return new Layout(placed, widest, total);
	}
}
