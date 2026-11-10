package net.yahuclient.mod.lyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.yahuclient.mod.config.LyricsStyle;

// Made-up words only: no real song text in the repository.
class LyricLayoutTest {
	// a stand-in for the font: 6 pixels per letter
	private static int width(String s) {
		return s.length() * 6;
	}

	private static List<String> words(String line) {
		return Arrays.asList(line.split(" "));
	}

	@Test
	void longLinesWrapInsteadOfRunningOff() {
		var layout = LyricLayout.layout(words("a fairly long made up line that would never fit into one narrow row at all"), LyricsStyle.LINE,
				LyricLayoutTest::width, 9, 120);
		for (var p : layout.words()) {
			float half = width(p.text()) / 2f;
			assertTrue(p.x() - half >= -60.5f && p.x() + half <= 60.5f, "word " + p.text() + " leaves the row");
		}
		assertTrue(layout.height() > 9, "it took more than one row");
		assertTrue(layout.width() <= 120);
	}

	@Test
	void shortLinesStayOnOneCenteredRow() {
		var layout = LyricLayout.layout(words("hello sky"), LyricsStyle.WORDS, LyricLayoutTest::width, 9, 200);
		assertEquals(2, layout.words().size());
		assertEquals(layout.words().get(0).y(), layout.words().get(1).y(), 1e-4);
		float left = layout.words().get(0).x() - width("hello") / 2f;
		float right = layout.words().get(1).x() + width("sky") / 2f;
		assertEquals(-right, left, 0.51f);
	}

	@Test
	void kineticStacksWordsWithSizesAndTiltsThatStayTheSame() {
		List<String> line = words("every word gets its own little moment here");
		var first = LyricLayout.layout(line, LyricsStyle.KINETIC, LyricLayoutTest::width, 9, 260);
		var again = LyricLayout.layout(line, LyricsStyle.KINETIC, LyricLayoutTest::width, 9, 260);
		assertEquals(first.words(), again.words(), "the same line always looks the same");
		long sizes = first.words().stream().map(LyricLayout.Placed::scale).distinct().count();
		long tilts = first.words().stream().map(LyricLayout.Placed::rotation).distinct().count();
		long rows = first.words().stream().map(LyricLayout.Placed::y).distinct().count();
		assertTrue(sizes >= 3, "words come in different sizes");
		assertTrue(tilts >= 3, "words are tilted differently");
		assertTrue(rows >= 2, "words stack into rows");
		for (var p : first.words()) {
			assertTrue(Math.abs(p.x()) + width(p.text()) * p.scale() / 2f <= 260 / 2f + 20, "kinetic block stays narrow");
		}
	}

	@Test
	void wordTimesFollowTheLine() {
		var line = new SyncedLyrics.Line(1000, "one three fivefive");
		var words = SyncedLyrics.wordTimes(line, 5000);
		assertEquals(3, words.size());
		assertEquals(1000, words.get(0).startMs());
		assertTrue(words.get(1).startMs() > words.get(0).startMs());
		assertTrue(words.get(2).startMs() > words.get(1).startMs());
		assertTrue(words.get(2).startMs() < 5000);
	}

	@Test
	void enhancedLrcGivesExactWordTimes() {
		var lyrics = SyncedLyrics.parse("[00:10.00] <00:10.00>made <00:10.50>up <00:11.25>words");
		var line = lyrics.lines().get(0);
		assertEquals("made up words", line.text());
		assertEquals(3, line.timedWords().size());
		assertEquals(10500, line.timedWords().get(1).startMs());
		var moment = lyrics.at(10600);
		assertEquals(3, moment.words().size());
		assertEquals(11250, moment.words().get(2).startMs());
	}
}
