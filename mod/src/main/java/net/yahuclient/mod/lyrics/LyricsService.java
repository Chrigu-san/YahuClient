package net.yahuclient.mod.lyrics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Looks up time-stamped lyrics for a song on LRCLIB (https://lrclib.net), a free lyrics database. Results are kept for the
 * session, a song without synced lyrics included, so every song is asked for once.
 */
public final class LyricsService {
	private static final String API = "https://lrclib.net/api/get";
	private static final String SEARCH = "https://lrclib.net/api/search";

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();
	private final Map<String, CompletableFuture<SyncedLyrics>> cache = new ConcurrentHashMap<>();
	private final String userAgent;

	public LyricsService(String userAgent) {
		this.userAgent = userAgent;
	}

	/** Synced lyrics for the song, or empty ones if none are known. Never fails; network errors count as "none". */
	public CompletableFuture<SyncedLyrics> lookup(NowPlaying song) {
		String key = song.artist() + "\u0000" + song.title();
		return cache.computeIfAbsent(key, k -> fetchExact(song).thenCompose(found -> found.isEmpty() ? fetchSearch(song) : CompletableFuture.completedFuture(found)));
	}

	// the exact endpoint wants the song's length, which tells versions (radio edit, live) apart
	private CompletableFuture<SyncedLyrics> fetchExact(NowPlaying song) {
		StringBuilder url = new StringBuilder(API).append("?track_name=").append(encode(cleanTitle(song.title()))).append("&artist_name=").append(encode(song.artist()));
		if (!song.album().isBlank()) {
			url.append("&album_name=").append(encode(song.album()));
		}
		if (song.durationMs() > 0) {
			url.append("&duration=").append(Math.round(song.durationMs() / 1000.0));
		}
		return get(url.toString()).thenApply(body -> body == null ? SyncedLyrics.parse(null) : syncedOf(JsonParser.parseString(body)));
	}

	private CompletableFuture<SyncedLyrics> fetchSearch(NowPlaying song) {
		String url = SEARCH + "?track_name=" + encode(cleanTitle(song.title())) + "&artist_name=" + encode(song.artist());
		return get(url).thenApply(body -> {
			if (body == null) {
				return SyncedLyrics.parse(null);
			}
			JsonElement json = JsonParser.parseString(body);
			if (!json.isJsonArray()) {
				return SyncedLyrics.parse(null);
			}
			// the first hit with synced lyrics and about the right length
			for (JsonElement hit : json.getAsJsonArray()) {
				JsonObject o = hit.getAsJsonObject();
				if (song.durationMs() > 0 && o.has("duration") && !o.get("duration").isJsonNull()
						&& Math.abs(o.get("duration").getAsDouble() * 1000 - song.durationMs()) > 4000) {
					continue;
				}
				SyncedLyrics lyrics = syncedOf(o);
				if (!lyrics.isEmpty()) {
					return lyrics;
				}
			}
			return SyncedLyrics.parse(null);
		});
	}

	private static SyncedLyrics syncedOf(JsonElement json) {
		if (json == null || !json.isJsonObject()) {
			return SyncedLyrics.parse(null);
		}
		JsonObject o = json.getAsJsonObject();
		return o.has("syncedLyrics") && !o.get("syncedLyrics").isJsonNull() ? SyncedLyrics.parse(o.get("syncedLyrics").getAsString()) : SyncedLyrics.parse(null);
	}

	private CompletableFuture<String> get(String url) {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).header("User-Agent", userAgent).GET().build();
		return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(response -> response.statusCode() == 200 ? response.body() : null)
				.exceptionally(error -> null);
	}

	private static String encode(String text) {
		return URLEncoder.encode(text, StandardCharsets.UTF_8);
	}

	/** Drops what players add to titles but lyrics databases don't have: "(Remastered 2011)", "- Live", "[feat. X]". */
	static String cleanTitle(String title) {
		String t = title.replaceAll("(?i)\\s*[(\\[][^)\\]]*(remaster|live|version|edit|mono|stereo|feat\\.?|ft\\.)[^)\\]]*[)\\]]", "");
		t = t.replaceAll("(?i)\\s+-\\s+.*(remaster|live|version|edit|mono|stereo).*$", "");
		return t.trim().isEmpty() ? title : t.trim();
	}
}
