package net.yahuclient.mod.lyrics;

import java.util.concurrent.CompletableFuture;

import net.yahuclient.mod.config.YahuConfig;

/**
 * Ties the media watcher to the lyrics lookup: runs the watcher while the lyrics mod is on, fetches lyrics when the song
 * changes and answers "what is sung right now".
 */
public final class LyricsController {
	public enum State { OFF, NOTHING_PLAYING, LOOKING_UP, NO_LYRICS, READY }

	private final MediaWatcher watcher = new MediaWatcher();
	private final LyricsService service;
	private NowPlaying song;
	private CompletableFuture<SyncedLyrics> lyrics;

	public LyricsController(String userAgent) {
		this.service = new LyricsService(userAgent);
	}

	/** Called every client tick. */
	public void tick(YahuConfig.Lyrics config) {
		if (!config.enabled) {
			if (watcher.isRunning()) {
				watcher.stop();
			}
			song = null;
			lyrics = null;
			return;
		}
		watcher.start();
		NowPlaying now = watcher.current();
		if (now == null) {
			song = null;
			lyrics = null;
			return;
		}
		if (!now.sameSong(song)) {
			lyrics = service.lookup(now);
		}
		song = now;
	}

	public void shutdown() {
		watcher.stop();
	}

	public NowPlaying song() {
		return song;
	}

	public State state(YahuConfig.Lyrics config) {
		if (!config.enabled) {
			return State.OFF;
		}
		if (song == null) {
			return State.NOTHING_PLAYING;
		}
		if (lyrics == null || !lyrics.isDone()) {
			return State.LOOKING_UP;
		}
		return lyrics.join().isEmpty() ? State.NO_LYRICS : State.READY;
	}

	/** The moment of the song right now, or null when nothing can be shown. */
	public SyncedLyrics.Moment moment() {
		if (song == null || lyrics == null || !lyrics.isDone()) {
			return null;
		}
		SyncedLyrics found = lyrics.join();
		return found.isEmpty() ? null : found.at(song.positionAt(System.currentTimeMillis()));
	}
}
