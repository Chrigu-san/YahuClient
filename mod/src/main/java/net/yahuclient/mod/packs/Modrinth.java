package net.yahuclient.mod.packs;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/**
 * Resource packs and shader packs from Modrinth, found and installed from inside the game: one search, one click, the file
 * lands in the instance's folder after its checksum matched. Everything runs on a background thread; the menu only reads the
 * results and the states.
 */
public final class Modrinth {
	public enum Kind {
		RESOURCE_PACKS("Resource packs", "resourcepack", "resourcepacks"),
		SHADERS("Shaders", "shader", "shaderpacks");

		public final String label, type, folder;

		Kind(String label, String type, String folder) {
			this.label = label;
			this.type = type;
			this.folder = folder;
		}
	}

	public enum Sort {
		POPULAR("Popular", "downloads"), NEWEST("Newest", "newest"), UPDATED("Updated", "updated");

		public final String label, index;

		Sort(String label, String index) {
			this.label = label;
			this.index = index;
		}
	}

	public record Hit(String id, String title, String author, String description, int downloads) {
	}

	public enum State { INSTALLING, DONE, FAILED }

	public record Progress(State state, String note) {
	}

	private static final String API = "https://api.modrinth.com/v2";
	private static final String AGENT = "Chrigu-san/YahuClient (in-game packs)";
	private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10)).build();

	/** What the install buttons show, by project id. */
	private static final Map<String, Progress> PROGRESS = new ConcurrentHashMap<>();

	private Modrinth() {
	}

	public static Progress progress(String id) {
		return PROGRESS.get(id);
	}

	/** The game version the packs should be for. */
	public static String gameVersion() {
		return FabricLoader.getInstance().getModContainer("minecraft").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("26.3");
	}

	public static Path folder(Kind kind) {
		return FabricLoader.getInstance().getGameDir().resolve(kind.folder);
	}

	// -------------------------------------------------------------------------------------------------------------------

	/** The facets for a search: the kind, and the game version when asked. */
	static String facets(Kind kind, String version) {
		JsonArray all = new JsonArray();
		all.add(one("project_type:" + kind.type));
		if (version != null) {
			all.add(one("versions:" + version));
		}
		return all.toString();
	}

	private static JsonArray one(String s) {
		JsonArray a = new JsonArray();
		a.add(s);
		return a;
	}

	static String searchUrl(Kind kind, String query, Sort sort, String version, int offset) {
		return API + "/search?query=" + URLEncoder.encode(query == null ? "" : query.trim(), StandardCharsets.UTF_8)
				+ "&facets=" + URLEncoder.encode(facets(kind, version), StandardCharsets.UTF_8) + "&index=" + sort.index + "&limit=20&offset=" + offset;
	}

	static List<Hit> parseHits(String json) {
		List<Hit> hits = new ArrayList<>();
		for (JsonElement e : JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("hits")) {
			JsonObject o = e.getAsJsonObject();
			hits.add(new Hit(o.get("project_id").getAsString(), o.get("title").getAsString(), string(o, "author"), string(o, "description"),
					o.has("downloads") ? o.get("downloads").getAsInt() : 0));
		}
		return hits;
	}

	private static String string(JsonObject o, String key) {
		return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
	}

	/** Searches; packs made for this game version first, and if there are none, the best of all versions (the install picks a matching file or says why not). */
	public static CompletableFuture<List<Hit>> search(Kind kind, String query, Sort sort) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				List<Hit> hits = parseHits(get(searchUrl(kind, query, sort, gameVersion(), 0)));
				return hits.isEmpty() ? parseHits(get(searchUrl(kind, query, sort, null, 0))) : hits;
			} catch (IOException | InterruptedException e) {
				throw new RuntimeException(e.getMessage() == null ? "No connection to Modrinth" : e.getMessage(), e);
			}
		});
	}

	/** A request that is tried up to three times when the connection drops or the server has a hiccup. */
	private static <T> T retrying(Attempt<T> attempt) throws IOException, InterruptedException {
		IOException last = null;
		for (int i = 0; i < 3; i++) {
			try {
				return attempt.run();
			} catch (NoRetry e) {
				throw e;
			} catch (IOException e) {
				last = e;
				Thread.sleep(1500L * (i + 1));
			}
		}
		throw last;
	}

	private interface Attempt<T> {
		T run() throws IOException, InterruptedException;
	}

	private static String get(String url) throws IOException, InterruptedException {
		return retrying(() -> {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", AGENT).timeout(Duration.ofSeconds(20)).build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() >= 500 || response.statusCode() == 429) {
				throw new IOException("Modrinth answered " + response.statusCode());
			}
			if (response.statusCode() != 200) {
				throw new NoRetry("Modrinth answered " + response.statusCode());
			}
			return response.body();
		});
	}

	/** An error that trying again won't fix. */
	private static final class NoRetry extends IOException {
		NoRetry(String message) {
			super(message);
		}
	}

	// -------------------------------------------------------------------------------------------------------------------

	/** The file to take from a project's versions: the newest version made for this game version, else the newest, its main file. */
	record Pick(String url, String name, String sha512, boolean matchesGame) {
	}

	static Pick pick(String versionsJson, String game) {
		JsonArray versions = JsonParser.parseString(versionsJson).getAsJsonArray();
		JsonObject chosen = null;
		boolean matches = false;
		for (JsonElement e : versions) {
			JsonObject v = e.getAsJsonObject();
			boolean fits = false;
			for (JsonElement g : v.getAsJsonArray("game_versions")) {
				fits |= g.getAsString().equals(game);
			}
			if (fits) {
				chosen = v;
				matches = true;
				break;
			}
			if (chosen == null) {
				chosen = v;
			}
		}
		if (chosen == null) {
			return null;
		}
		JsonObject file = null;
		for (JsonElement e : chosen.getAsJsonArray("files")) {
			JsonObject f = e.getAsJsonObject();
			if (file == null || (f.has("primary") && f.get("primary").getAsBoolean())) {
				file = f;
			}
		}
		if (file == null) {
			return null;
		}
		return new Pick(file.get("url").getAsString(), file.get("filename").getAsString(), file.getAsJsonObject("hashes").get("sha512").getAsString(), matches);
	}

	/** A file name that stays in its folder. */
	static String safeName(String name) {
		String n = name.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1F]", "_").replaceAll("^\\.+", "");
		return n.isEmpty() ? "pack.zip" : n;
	}

	/** Installs a project into the instance's folder. The state shows in {@link #progress}. */
	public static void install(Kind kind, Hit hit) {
		Progress now = PROGRESS.get(hit.id());
		if (now != null && now.state() == State.INSTALLING) {
			return;
		}
		PROGRESS.put(hit.id(), new Progress(State.INSTALLING, "Downloading..."));
		CompletableFuture.runAsync(() -> {
			try {
				Pick pick = pick(get(API + "/project/" + hit.id() + "/version"), gameVersion());
				if (pick == null) {
					throw new IOException("No download for this pack");
				}
				Path dir = folder(kind);
				Files.createDirectories(dir);
				Path target = dir.resolve(safeName(pick.name()));
				Path part = dir.resolve(target.getFileName() + ".part");
				HttpRequest request = HttpRequest.newBuilder(URI.create(pick.url())).header("User-Agent", AGENT).build();
				retrying(() -> {
					HttpResponse<Path> response = HTTP.send(request, HttpResponse.BodyHandlers.ofFile(part));
					if (response.statusCode() != 200) {
						Files.deleteIfExists(part);
						throw new IOException("Download failed (" + response.statusCode() + ")");
					}
					return response;
				});
				String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512").digest(Files.readAllBytes(part)));
				if (!actual.equalsIgnoreCase(pick.sha512())) {
					Files.deleteIfExists(part);
					throw new IOException("The file did not match its checksum");
				}
				Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
				PROGRESS.put(hit.id(), new Progress(State.DONE, pick.matchesGame() ? "Installed" : "Installed (made for another version)"));
				if (kind == Kind.RESOURCE_PACKS) {
					Minecraft mc = Minecraft.getInstance();
					mc.execute(() -> mc.getResourcePackRepository().reload());
				}
			} catch (Exception e) {
				PROGRESS.put(hit.id(), new Progress(State.FAILED, e.getMessage() == null ? "Failed" : e.getMessage()));
			}
		});
	}
}
