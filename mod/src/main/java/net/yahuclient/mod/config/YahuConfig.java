package net.yahuclient.mod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Everything the Yahu mods remember, saved as config/yahu.json in the instance. Missing fields keep their defaults. */
public final class YahuConfig {
	public Crosshair crosshair = new Crosshair();
	public Lyrics lyrics = new Lyrics();

	public static final class Crosshair {
		public boolean enabled = true;
		public CrosshairShape shape = CrosshairShape.CROSS;
		public int size = 5;        // arm length in pixels (GUI scale)
		public int gap = 2;         // free space around the middle
		public int thickness = 1;
		public int color = 0xFFFFFFFF;  // ARGB
		public boolean outline = true;  // a dark rim so it reads on bright blocks
		public boolean dot = false;     // an extra dot in the middle
	}

	public static final class Lyrics {
		public boolean enabled = true;
		public float height = 0.62f;    // how high in the sky, 0 = horizon, 1 = straight up
		public float scale = 1.0f;
		public int sungColor = 0xFF6CD83E;     // the part already sung: grass green
		public int upcomingColor = 0xFFFFFFFF;
		public boolean showNext = true;  // the next line, small, under the current one
		public boolean showSongTitle = true;
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static YahuConfig load(Path file) {
		if (Files.isRegularFile(file)) {
			try {
				YahuConfig config = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), YahuConfig.class);
				if (config != null) {
					config.fillGaps();
					return config;
				}
			} catch (IOException | JsonParseException e) {
				// a broken file falls back to the defaults; the next save writes a good one
			}
		}
		return new YahuConfig();
	}

	public void save(Path file) {
		try {
			Files.createDirectories(file.getParent());
			Path temp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(temp, GSON.toJson(this), StandardCharsets.UTF_8);
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			// not being able to save settings must never stop the game
		}
	}

	// sections missing from an older file come back as defaults instead of null
	private void fillGaps() {
		if (crosshair == null) {
			crosshair = new Crosshair();
		}
		if (crosshair.shape == null) {
			crosshair.shape = CrosshairShape.CROSS;
		}
		if (lyrics == null) {
			lyrics = new Lyrics();
		}
	}
}
