package net.yahuclient.mod.lyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

// Made-up lines only: no real song text in the repository.
class SyncedLyricsTest {
	private static final String LRC = """
			[ar:Test Band]
			[ti:Test Song]
			[00:01.00]first line here
			[00:05.50]second line here
			[00:09.00]
			[00:12.25][00:30.00]a chorus line
			""";

	@Test
	void readsLinesInOrder() {
		SyncedLyrics lyrics = SyncedLyrics.parse(LRC);
		assertEquals(5, lyrics.lines().size());
		assertEquals(1000, lyrics.lines().get(0).startMs());
		assertEquals(5500, lyrics.lines().get(1).startMs());
		assertEquals(12250, lyrics.lines().get(3).startMs());
		assertEquals("a chorus line", lyrics.lines().get(4).text());
		assertEquals(30000, lyrics.lines().get(4).startMs());
	}

	@Test
	void fractionsOfOneTwoAndThreeDigits() {
		SyncedLyrics lyrics = SyncedLyrics.parse("[00:01.5]a\n[00:02.05]b\n[00:03.005]c\n[01:02]d");
		assertEquals(1500, lyrics.lines().get(0).startMs());
		assertEquals(2050, lyrics.lines().get(1).startMs());
		assertEquals(3005, lyrics.lines().get(2).startMs());
		assertEquals(62000, lyrics.lines().get(3).startMs());
	}

	@Test
	void findsTheLineBeingSung() {
		SyncedLyrics lyrics = SyncedLyrics.parse(LRC);
		assertNull(lyrics.at(500).current());
		assertEquals("first line here", lyrics.at(500).next().text());
		assertEquals("first line here", lyrics.at(1000).current().text());
		assertEquals(0.0, lyrics.at(1000).progress(), 1e-9);
		assertEquals("second line here", lyrics.at(3000).next().text());
		assertTrue(lyrics.at(3000).progress() > 0.4 && lyrics.at(3000).progress() < 0.6);
		assertEquals(1.0, lyrics.at(5400).progress(), 1e-9);
	}

	@Test
	void emptyLinesHideTheText() {
		SyncedLyrics lyrics = SyncedLyrics.parse(LRC);
		assertNull(lyrics.at(10000).current());
		assertEquals("a chorus line", lyrics.at(10000).next().text());
	}

	@Test
	void lastLineFadesAfterAWhile() {
		SyncedLyrics lyrics = SyncedLyrics.parse(LRC);
		assertEquals("a chorus line", lyrics.at(31000).current().text());
		assertNull(lyrics.at(40000).current());
	}

	@Test
	void plainTextHasNoSyncedLines() {
		assertTrue(SyncedLyrics.parse("just words\nwithout times").isEmpty());
		assertTrue(SyncedLyrics.parse(null).isEmpty());
	}

	@Test
	void cleansTitles() {
		assertEquals("Song", LyricsService.cleanTitle("Song (Remastered 2011)"));
		assertEquals("Song", LyricsService.cleanTitle("Song - Live at Somewhere"));
		assertEquals("Song", LyricsService.cleanTitle("Song [feat. Someone]"));
		assertEquals("Song (Part 2)", LyricsService.cleanTitle("Song (Part 2)"));
	}

	@Test
	void extrapolatesThePositionWhilePlaying() {
		NowPlaying song = NowPlaying.parse("{\"title\":\"T\",\"artist\":\"A\",\"durationMs\":200000,\"positionMs\":1000,\"updatedMs\":5000,\"playing\":true}");
		assertEquals(3000, song.positionAt(7000));
		NowPlaying paused = NowPlaying.parse("{\"title\":\"T\",\"artist\":\"A\",\"positionMs\":1000,\"updatedMs\":5000,\"playing\":false}");
		assertEquals(1000, paused.positionAt(7000));
		assertNull(NowPlaying.parse("{}"));
		assertNull(NowPlaying.parse("not json"));
	}
}
