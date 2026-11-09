package net.yahuclient.mod.lyrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Time-stamped lyrics in the LRC format ("[01:23.45] a line"), and where a song is at a given moment.
 * The text comes from a lyrics service at runtime; nothing is bundled with the mod.
 */
public final class SyncedLyrics {
	/** One line and when it starts, in milliseconds from the start of the song. */
	public record Line(long startMs, String text) {
	}

	/** The line being sung, how far into it the song is (0 to 1), and the line that comes next. */
	public record Moment(Line current, double progress, Line next) {
	}

	private static final Pattern TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
	// the last line has no successor to end it; it stays up this long at most
	private static final long LAST_LINE_MS = 6000;

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

	/** Reads LRC text. Lines without a time stamp (tags, plain lyrics) are skipped; a line may carry several stamps. */
	public static SyncedLyrics parse(String lrc) {
		List<Line> lines = new ArrayList<>();
		if (lrc != null) {
			for (String raw : lrc.split("\\R")) {
				Matcher m = TIME.matcher(raw);
				List<Long> stamps = new ArrayList<>();
				int textStart = 0;
				while (m.find() && m.start() == textStart) {
					long minutes = Long.parseLong(m.group(1));
					long seconds = Long.parseLong(m.group(2));
					long fraction = 0;
					if (m.group(3) != null) {
						String f = m.group(3);
						// ".5" is half a second, ".05" fifty milliseconds, ".005" five
						fraction = Long.parseLong(f) * (f.length() == 1 ? 100 : f.length() == 2 ? 10 : 1);
					}
					stamps.add(minutes * 60_000 + seconds * 1000 + fraction);
					textStart = m.end();
				}
				if (stamps.isEmpty()) {
					continue;
				}
				String text = raw.substring(textStart).trim();
				for (long stamp : stamps) {
					lines.add(new Line(stamp, text));
				}
			}
		}
		lines.sort(Comparator.comparingLong(Line::startMs));
		return new SyncedLyrics(List.copyOf(lines));
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
			return new Moment(null, 0, next);
		}
		Line current = lines.get(index);
		long end = next != null ? next.startMs() : current.startMs() + LAST_LINE_MS;
		if (positionMs >= end || current.text().isEmpty()) {
			return new Moment(null, 0, next);
		}
		// a line is sung a bit faster than the gap to the next one; finish the sweep at 85% of it
		double length = Math.max(1, (end - current.startMs()) * 0.85);
		double progress = Math.min(1.0, (positionMs - current.startMs()) / length);
		return new Moment(current, progress, next);
	}
}
