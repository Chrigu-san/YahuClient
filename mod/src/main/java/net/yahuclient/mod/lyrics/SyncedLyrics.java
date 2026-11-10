package net.yahuclient.mod.lyrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Time-stamped lyrics in the LRC format ("[01:23.45] a line"), and where a song is at a given moment. Enhanced LRC, with a
 * stamp per word ("[00:12.00] <00:12.00>a <00:12.40>line"), gives exact word times; otherwise they are estimated from the
 * length of each word. The text comes from a lyrics service at runtime; nothing is bundled with the mod.
 */
public final class SyncedLyrics {
	/** A word and when it starts, in milliseconds from the start of the song. */
	public record Word(String text, long startMs) {
	}

	/** One line, when it starts, and its words with exact times if the lyrics had them (else empty). */
	public record Line(long startMs, String text, List<Word> timedWords) {
		public Line(long startMs, String text) {
			this(startMs, text, List.of());
		}
	}

	/**
	 * The line being sung and how far the song is: progress through the line (0 to 1), the line that comes next, and the
	 * start time of every word of the current line.
	 */
	public record Moment(Line current, double progress, Line next, long positionMs, List<Word> words) {
	}

	private static final Pattern TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
	private static final Pattern WORD_TIME = Pattern.compile("<(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?>");
	// the last line has no successor to end it; it stays up this long at most
	private static final long LAST_LINE_MS = 6000;
	// a line is sung a bit faster than the gap to the next one; the words are spread over this share of it
	private static final double SUNG_SHARE = 0.85;

	private final List<Line> lines;

	private SyncedLyrics(List<Line> lines) {
		this.lines = lines;
	}

	public List<Line> lines() {
		return lines;
	}

	public boolean isEmpty() {
		return lines.isEmpty();
	}

	private static long millis(Matcher m) {
		long minutes = Long.parseLong(m.group(1));
		long seconds = Long.parseLong(m.group(2));
		long fraction = 0;
		if (m.group(3) != null) {
			String f = m.group(3);
			// ".5" is half a second, ".05" fifty milliseconds, ".005" five
			fraction = Long.parseLong(f) * (f.length() == 1 ? 100 : f.length() == 2 ? 10 : 1);
		}
		return minutes * 60_000 + seconds * 1000 + fraction;
	}

	/** Reads LRC text. Lines without a time stamp (tags, plain lyrics) are skipped; a line may carry several stamps. */
	public static SyncedLyrics parse(String lrc) {
		List<Line> lines = new ArrayList<>();
		if (lrc != null) {
			for (String raw : lrc.split("\\R")) {
				Matcher m = TIME.matcher(raw);
				List<Long> stamps = new ArrayList<>();
				int textStart = 0;
				while (m.find() && m.start() == textStart) {
					stamps.add(millis(m));
					textStart = m.end();
				}
				if (stamps.isEmpty()) {
					continue;
				}
				String body = raw.substring(textStart);
				List<Word> words = wordStamps(body);
				String text = WORD_TIME.matcher(body).replaceAll("").replaceAll("\\s+", " ").trim();
				for (long stamp : stamps) {
					lines.add(new Line(stamp, text, words));
				}
			}
		}
		lines.sort(Comparator.comparingLong(Line::startMs));
		return new SyncedLyrics(List.copyOf(lines));
	}

	// the "<mm:ss.xx>word" stamps of enhanced LRC, if any
	private static List<Word> wordStamps(String body) {
		Matcher m = WORD_TIME.matcher(body);
		List<Word> words = new ArrayList<>();
		List<int[]> spans = new ArrayList<>();
		List<Long> times = new ArrayList<>();
		while (m.find()) {
			spans.add(new int[] { m.start(), m.end() });
			times.add(millis(m));
		}
		for (int i = 0; i < spans.size(); i++) {
			int from = spans.get(i)[1];
			int to = i + 1 < spans.size() ? spans.get(i + 1)[0] : body.length();
			for (String w : body.substring(from, to).trim().split("\\s+")) {
				if (!w.isEmpty()) {
					words.add(new Word(w, times.get(i)));
				}
			}
		}
		return List.copyOf(words);
	}

	/**
	 * When each word of a line starts: the exact times if the lyrics had them, else the line's time spread over the words by
	 * their length (a long word takes longer to sing).
	 */
	public static List<Word> wordTimes(Line line, long endMs) {
		if (!line.timedWords().isEmpty()) {
			return line.timedWords();
		}
		String[] parts = line.text().trim().split("\\s+");
		List<Word> words = new ArrayList<>();
		if (line.text().isBlank()) {
			return words;
		}
		double total = 0;
		for (String p : parts) {
			total += p.length() + 2;
		}
		double sung = Math.max(1, (endMs - line.startMs()) * SUNG_SHARE);
		double at = 0;
		for (String p : parts) {
			words.add(new Word(p, line.startMs() + Math.round(at / total * sung)));
			at += p.length() + 2;
		}
		return words;
	}

	/** What is sung at this point of the song. Empty lines (instrumental parts) count as "nothing", so they hide the text. */
	public Moment at(long positionMs) {
		int index = -1;
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).startMs() <= positionMs) {
				index = i;
			} else {
				break;
			}
		}
		Line next = index + 1 < lines.size() ? lines.get(index + 1) : null;
		if (index < 0) {
			return new Moment(null, 0, next, positionMs, List.of());
		}
		Line current = lines.get(index);
		long end = next != null ? next.startMs() : current.startMs() + LAST_LINE_MS;
		if (positionMs >= end || current.text().isEmpty()) {
			return new Moment(null, 0, next, positionMs, List.of());
		}
		double length = Math.max(1, (end - current.startMs()) * SUNG_SHARE);
		double progress = Math.min(1.0, (positionMs - current.startMs()) / length);
		return new Moment(current, progress, next, positionMs, wordTimes(current, end));
	}
}
