package net.yahuclient.mod.lyrics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * A song Windows reports as playing (see now-playing.ps1). The position is extrapolated from the last report while it plays,
 * because players only report it when something changes (play, pause, seek).
 */
public record NowPlaying(String title, String artist, String album, long durationMs, long positionMs, long updatedMs, boolean playing) {
	/** Parses one line of the script's output; null for "{}" or anything unreadable. */
	public static NowPlaying parse(String json) {
		try {
			JsonObject o = JsonParser.parseString(json).getAsJsonObject();
			if (!o.has("title") || o.get("title").getAsString().isBlank()) {
				return null;
			}
			return new NowPlaying(o.get("title").getAsString(), text(o, "artist"), text(o, "album"), number(o, "durationMs"),
					number(o, "positionMs"), number(o, "updatedMs"), o.has("playing") && o.get("playing").getAsBoolean());
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static String text(JsonObject o, String key) {
		return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
	}

	private static long number(JsonObject o, String key) {
		return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsLong() : 0;
	}

	/** Where the song is at this wall-clock time, in milliseconds. */
	public long positionAt(long nowMs) {
		long position = positionMs;
		if (playing && updatedMs > 0) {
			position += Math.max(0, nowMs - updatedMs);
		}
		return durationMs > 0 ? Math.min(position, durationMs) : position;
	}

	/** True when both describe the same song, so the lyrics don't need to be looked up again. */
	public boolean sameSong(NowPlaying other) {
		return other != null && title.equals(other.title) && artist.equals(other.artist);
	}
}
