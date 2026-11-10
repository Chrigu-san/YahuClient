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
		public int opacity = 100;       // percent
		public boolean outline = true;  // a dark rim so it reads on bright blocks
		public int outlineColor = 0xFF000000;
		public boolean dot = false;     // an extra dot in the middle

		// PvP
		public boolean targetColorOn = true;       // another color while aiming at a player or mob
		public int targetColor = 0xFFFF5252;
		public boolean hitMarker = true;           // a short X when a hit lands
		public int hitMarkerColor = 0xFFFFFFFF;
		public boolean cooldown = true;            // the attack charge as a bar under the crosshair
		public boolean dynamicGap = false;         // opens up while moving, sprinting or in the air
	}

	public static final class Lyrics {
		public boolean enabled = true;
		public LyricsMode mode = LyricsMode.SKY;
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
		if (lyrics.mode == null) {
			lyrics.mode = LyricsMode.SKY;
		}
	}
}
