package net.yahuclient.mod.ui;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.ReloadableTexture;
import net.minecraft.resources.Identifier;

/**
 * The menu background: the title panorama of Minecraft 1.18 (the mountains of Caves & Cliffs), in black and white,
 * instead of the newest one. It is not shipped with the mod (it belongs to Mojang): once, in the background, the mod reads
 * Mojang's official version list and the 1.18 asset index, downloads the six panorama pictures from Mojang's resource
 * server, checks each against Mojang's checksum, turns them black and white and keeps them in config/yahu. Until then (or
 * without internet) the game's own panorama shows.
 */
public final class ClassicPanorama {
	public static final String VERSION = "1.18.2";
	public static final Identifier PANORAMA = Identifier.withDefaultNamespace("textures/gui/title/background/panorama");
	private static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
	private static final String INNER = "minecraft/textures/gui/title/background/panorama_%d.png";
	private static final String RESOURCES = "https://resources.download.minecraft.net/";
	private static volatile boolean started;
	/** Whether the panorama texture currently shows our faces (set when a picture is put in, ReloadableTextureMixin). */
	public static volatile boolean shown;
	/** The last stacked picture we made, to tell it apart from Minecraft's own when the texture takes one. */
	public static volatile Object ours;
	private static long lastTry;

	private ClassicPanorama() {
	}

	public static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve("yahu").resolve("panorama-" + VERSION);
	}

	public static Path face(int i) {
		return folder().resolve("panorama_" + i + ".png");
	}

	/** True when all six black and white faces are there. */
	public static boolean ready() {
		for (int i = 0; i < 6; i++) {
			if (!Files.isRegularFile(face(i))) {
				return false;
			}
		}
		return true;
	}

	/** Fetches the faces once, in the background, if they aren't there yet; then shows them. */
	public static void fetchIfNeeded() {
		if (started || ready()) {
			return;
		}
		started = true;
		Thread t = new Thread(() -> {
			try {
				fetch();
				Minecraft.getInstance().execute(ClassicPanorama::reload);
			} catch (Exception e) {
				LogUtils.getLogger().warn("Yahu: could not get the {} panorama: {}", VERSION, e.toString());
			}
		}, "Yahu panorama");
		t.setDaemon(true);
		t.start();
	}

	/**
	 * Called while the main menu draws: if the faces are there but the panorama still shows Minecraft's own (it loaded
	 * before the download was done), load it again now.
	 */
	public static void showWhenReady(boolean wanted) {
		long now = System.currentTimeMillis();
		if (now - lastTry < 2000) {
			return;  // a reload may still be under way: give it time
		}
		if ((wanted && !shown && ready()) || (!wanted && shown)) {
			lastTry = now;
			reload();
		}
	}
	/** Makes the title panorama load again (picks up our faces, see CubeMapTextureMixin). */
	public static void reload() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getTextureManager().getTexture(PANORAMA) instanceof ReloadableTexture texture) {
			try {
				texture.apply(texture.loadContents(mc.getResourceManager()));
			} catch (IOException e) {
				LogUtils.getLogger().warn("Yahu: could not reload the panorama: {}", e.toString());
			}
		}
	}

	private static void fetch() throws Exception {
		HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build();
		JsonObject manifest = json(http, MANIFEST);
		String versionUrl = null;
		for (var v : manifest.getAsJsonArray("versions")) {
			JsonObject o = v.getAsJsonObject();
			if (VERSION.equals(o.get("id").getAsString())) {
				versionUrl = o.get("url").getAsString();
			}
		}
		if (versionUrl == null) {
			throw new IOException("version " + VERSION + " is not in Mojang's list");
		}
		// the panorama pictures are not in the client jar (it only holds placeholders) but in the version's asset index
		String indexUrl = json(http, versionUrl).getAsJsonObject("assetIndex").get("url").getAsString();
		JsonObject objects = json(http, indexUrl).getAsJsonObject("objects");
		Files.createDirectories(folder());
		for (int i = 0; i < 6; i++) {
			JsonObject object = objects.getAsJsonObject(INNER.formatted(i));
			if (object == null) {
				throw new IOException("panorama_" + i + " is not in the asset index");
			}
			String hash = object.get("hash").getAsString();
			HttpResponse<byte[]> r = http.send(HttpRequest.newBuilder(URI.create(RESOURCES + hash.substring(0, 2) + "/" + hash))
					.timeout(Duration.ofMinutes(2)).build(), HttpResponse.BodyHandlers.ofByteArray());
			if (r.statusCode() != 200) {
				throw new IOException("panorama_" + i + " answered " + r.statusCode());
			}
			if (!hash.equalsIgnoreCase(sha1(r.body()))) {
				throw new IOException("panorama_" + i + " doesn't match Mojang's checksum");
			}
			try (NativeImage image = NativeImage.read(r.body())) {
				blackAndWhite(image);
				Path tmp = face(i).resolveSibling("panorama_" + i + ".png.part");
				image.writeToFile(tmp);
				Files.move(tmp, face(i), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}
	/** Turns a picture black and white by its brightness, a touch more contrast. */
	static void blackAndWhite(NativeImage image) {
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int c = image.getPixel(x, y);
				int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
				double l = (.2126 * r + .7152 * g + .0722 * b) / 255.0;
				l = Math.max(0, Math.min(1, (l - .5) * 1.12 + .5));
				int v = (int) Math.round(l * 255);
				image.setPixel(x, y, (c & 0xFF000000) | (v << 16) | (v << 8) | v);
			}
		}
	}

	private static JsonObject json(HttpClient http, String url) throws Exception {
		HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.ofString());
		if (r.statusCode() != 200) {
			throw new IOException(url + " answered " + r.statusCode());
		}
		return JsonParser.parseString(r.body()).getAsJsonObject();
	}

	private static String sha1(byte[] data) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
	}
}