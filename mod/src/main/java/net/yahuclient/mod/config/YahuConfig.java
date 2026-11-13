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
	public int version = 2;
	public Crosshair crosshair = new Crosshair();
	public Lyrics lyrics = new Lyrics();
	public Skybox skybox = new Skybox();
	public Inventory inventory = new Inventory();

	/** The crosshair is built from three parts that can be combined: arms, a center dot and a ring. Sizes are screen pixels. */
	public static final class Crosshair {
		public boolean enabled = true;

		public Arms arms = Arms.CROSS;
		public int armLength = 6;
		public int armThickness = 2;
		public int gap = 3;           // may go below zero: the arms then overlap the middle

		public boolean dot = false;
		public int dotSize = 3;
		public boolean dotRound = true;
		public int dotColor = 0xFFFFFFFF;
		public boolean dotOwnColor = false;

		public boolean ring = false;
		public int ringRadius = 9;
		public int ringThickness = 1;

		public int color = 0xFFFFFFFF;
		public int opacity = 100;
		public int outline = 1;       // outline width in pixels, 0 = none
		public int outlineColor = 0xFF000000;
		public int outlineOpacity = 80;

		// PvP
		public boolean targetColorOn = true;
		public int targetColor = 0xFFFF5C7A;
		public boolean hitMarker = true;
		public int hitMarkerColor = 0xFFFFFFFF;
		public int hitMarkerSize = 6;
		public int hitMarkerMs = 260;
		public Charge charge = Charge.BAR;
		public int spread = 0;        // how many pixels the crosshair opens up while moving, 0 = off
		public boolean hideInThirdPerson = true;
	}

	public enum Arms { NONE, CROSS, T, X }

	public enum Charge { OFF, BAR, RING }

	public static final class Lyrics {
		public boolean enabled = true;
		public LyricsMode mode = LyricsMode.SKY;      // where
		public LyricsStyle style = LyricsStyle.WORDS; // how they appear
		public LyricsFont font = LyricsFont.UNBOUNDED;
		public float height = 0.55f;    // how high in the sky, 0 = horizon, 1 = high up
		public float scale = 1.0f;
		public int maxWidth = 70;       // the line wraps beyond this share of the view, in percent, so it never runs off
		public float outline = 0.75f;   // outline width in font pixels, 0 = none (quarter steps)
		public int outlineColor = 0xFF0B0C12;
		public int sungColor = 0xFF8B6CFF;
		public int upcomingColor = 0xFFFFFFFF;
		public boolean gradient = true; // sung text runs from the sung color to the gradient color
		public int gradientColor = 0xFF3FD5F2;
		public boolean uppercase = false;
		public boolean showNext = true;
		public boolean showSongTitle = true;
		public int popStrength = 60;    // how much words pop when they appear, in percent
	}

	public static final class Inventory {
		public boolean enabled = true;
		public InventoryStyle style = InventoryStyle.GLASS;
		public int accent = 0xFF8B6CFF;
		public int opacity = 100;       // percent, how solid the slots and panels are
		public int roundness = 3;       // corner radius of the slots, in GUI pixels
		public boolean hotbar = true;
		public boolean containers = true;
	}

	public static final class Skybox {
		public boolean enabled = false;
		public SkyPreset preset = SkyPreset.BLACK_HOLE;
		public String customFile = "";
		public boolean followDayNight = false;  // darker at night, like the normal sky
		public int brightness = 100;
		public int rotationSpeed = 10;          // degrees per minute
		public boolean overworldOnly = true;
		public boolean hideClouds = true;       // no clouds under a custom sky
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

	// sections and enums missing from an older file come back as defaults instead of null
	private void fillGaps() {
		if (crosshair == null || version < 2) {
			// the first crosshair settings meant something else; start fresh rather than misread them
			crosshair = new Crosshair();
		}
		if (crosshair.arms == null) {
			crosshair.arms = Arms.CROSS;
		}
		if (crosshair.charge == null) {
			crosshair.charge = Charge.BAR;
		}
		if (lyrics == null) {
			lyrics = new Lyrics();
		}
		if (lyrics.mode == null) {
			lyrics.mode = LyricsMode.SKY;
		}
		if (lyrics.style == null) {
			lyrics.style = LyricsStyle.WORDS;
		}
		if (lyrics.font == null) {
			lyrics.font = LyricsFont.UNBOUNDED;
		}
		if (lyrics.maxWidth < 20 || lyrics.maxWidth > 100) {
			// older files held font pixels here; a share of the view is what works in the sky and on the screen alike
			lyrics.maxWidth = 70;
		}
		if (skybox == null) {
			skybox = new Skybox();
		}
		if (skybox.preset == null) {
			skybox.preset = SkyPreset.BLACK_HOLE;
		}
		if (skybox.customFile == null) {
			skybox.customFile = "";
		}
		version = 2;
	}
}
