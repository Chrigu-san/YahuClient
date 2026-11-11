package net.yahuclient.mod;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.yahuclient.mod.config.LyricsFont;
import net.yahuclient.mod.config.LyricsMode;
import net.yahuclient.mod.config.LyricsStyle;
import net.yahuclient.mod.config.SkyPreset;
import net.yahuclient.mod.config.YahuConfig;
import net.yahuclient.mod.crosshair.CrosshairRenderer;
import net.yahuclient.mod.lyrics.LyricsController;
import net.yahuclient.mod.lyrics.LyricsPainter;
import net.yahuclient.mod.lyrics.SkyAnchor;
import net.yahuclient.mod.lyrics.SkyLyricsRenderer;
import net.yahuclient.mod.lyrics.SkyWorldRenderer;
import net.yahuclient.mod.lyrics.SyncedLyrics;
import net.yahuclient.mod.skybox.Skybox;
import net.yahuclient.mod.ui.BlockWidgets;
import net.yahuclient.mod.ui.Control;
import net.yahuclient.mod.ui.Draw;
import net.yahuclient.mod.ui.ModEntry;
import net.yahuclient.mod.ui.Theme;
import net.yahuclient.mod.ui.YahuScreen;

/**
 * The Yahu mod: the Yahu menu (Right Shift, or the Yahu Mods button on the title screen and in the pause menu) and our own
 * mods, which the menu lists: Crosshair, Lyrics and Skybox. Everything is client side and works on any server.
 */
public class YahuMod implements ClientModInitializer {
	public static final String ID = "yahu";
	private static final int MENU_KEY = InputConstants.KEY_RSHIFT;  // 26.x has its own key codes, not GLFW's

	private static YahuMod instance;

	private Path configFile;
	private YahuConfig config;
	private KeyMapping menuKey;
	private LyricsController lyrics;

	private final SkyAnchor skyAnchor = new SkyAnchor();
	private Object shownLineKey;
	private long lineShownAtMs;
	private long lastHitMs;

	/**
	 * A frame to show instead of a playing song, for the in-game tests: made-up text only, so no real song ends up in a
	 * screenshot. Null in normal play.
	 */
	private static volatile LyricsPainter.Frame testLyrics;

	/** Shows made-up lines, this far through (0 to 1); null clears them. */
	public static void showTestLyrics(String line, double progress, String next, String title) {
		testLyrics = line == null ? null : sampleFrame(line, progress, next, title);
	}

	/** A frame for sample text: the line spread over four seconds, at this point of it. */
	public static LyricsPainter.Frame sampleFrame(String line, double progress, String next, String title) {
		var words = SyncedLyrics.wordTimes(new SyncedLyrics.Line(0, line), 4000);
		return new LyricsPainter.Frame(words, Math.round(progress * 3400), progress, next, title, line);
	}

	private LyricsPainter.Frame shownLyrics() {
		if (!config.lyrics.enabled) {
			return null;
		}
		if (testLyrics != null) {
			return testLyrics;
		}
		SyncedLyrics.Moment moment = lyrics.moment();
		if (moment == null || moment.current() == null) {
			return null;
		}
		var song = lyrics.song();
		return new LyricsPainter.Frame(moment.words(), moment.positionMs(), moment.progress(), moment.next() != null ? moment.next().text() : null,
				song != null ? song.title() + " – " + song.artist() : null, moment.current());
	}

	/** The running mod's settings; the in-game tests change them directly. */
	public static YahuConfig config() {
		return instance.config;
	}

	@Override
	public void onInitializeClient() {
		instance = this;
		configFile = FabricLoader.getInstance().getConfigDir().resolve("yahu.json");
		config = YahuConfig.load(configFile);
		String version = FabricLoader.getInstance().getModContainer(ID).map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("dev");
		lyrics = new LyricsController("YahuClient/" + version + " (https://github.com/Chrigu-san/YahuClient)");

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ID, "yahu"));
		menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.yahu.menu", InputConstants.Type.KEYBOARD, MENU_KEY, category));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (menuKey.consumeClick()) {
				openMenu(mc, null);
			}
			lyrics.tick(config.lyrics);
			Skybox.update(config.skybox);
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> lyrics.shutdown());

		// the custom crosshair replaces the vanilla one while it is on; off, the vanilla one comes back untouched
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (g, delta) -> {
			Minecraft mc = Minecraft.getInstance();
			if (!config.crosshair.enabled) {
				vanilla.extractRenderState(g, delta);
				return;
			}
			boolean firstPerson = mc.options.getCameraType().isFirstPerson();
			if ((firstPerson || !config.crosshair.hideInThirdPerson) && mc.player != null && !mc.player.isSpectator()) {
				var motion = mc.player.getDeltaMovement();
				var state = new CrosshairRenderer.State(mc.crosshairPickEntity != null, System.currentTimeMillis() - lastHitMs,
						mc.player.getAttackStrengthScale(0f), Math.sqrt(motion.x * motion.x + motion.z * motion.z), !mc.player.onGround(),
						mc.player.isSprinting());
				CrosshairRenderer.draw(g, config.crosshair, state, g.guiWidth() / 2f, g.guiHeight() / 2f);
			}
		});

		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide()) {
				lastHitMs = System.currentTimeMillis();
			}
			return InteractionResult.PASS;
		});

		// lyrics on the screen: only in the "follow view" mode
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(ID, "lyrics"), (g, delta) -> {
			LyricsPainter.Frame frame = shownLyrics();
			if (frame == null || config.lyrics.mode != LyricsMode.FOLLOW) {
				return;
			}
			SkyLyricsRenderer.draw(g, Minecraft.getInstance().font, config.lyrics, frame);
		});

		// lyrics in the sky: the default, fixed in place like a cloud
		LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
			Minecraft mc = Minecraft.getInstance();
			LyricsPainter.Frame frame = shownLyrics();
			if (frame == null || config.lyrics.mode != LyricsMode.SKY || mc.gui.hud.isHidden()) {
				skyAnchor.reset();
				return;
			}
			float yaw = skyAnchor.update(mc.gameRenderer.mainCamera().yRot(), frame.lineKey());
			if (frame.lineKey() != shownLineKey) {
				shownLineKey = frame.lineKey();
				lineShownAtMs = System.currentTimeMillis();
			}
			SkyWorldRenderer.draw(context.poseStack(), context.submitNodeCollector(), mc.font, config.lyrics, yaw, frame,
					System.currentTimeMillis() - lineShownAtMs);
		});

		// a "Yahu Mods" button at the top of the title screen and the pause menu, and Right Shift works there too
		// (key mappings only fire while playing, not in menus)
		ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
			if (screen instanceof PauseScreen || screen instanceof TitleScreen) {
				addMenuButton(mc, screen, width, screen instanceof TitleScreen ? "menu.online" : "menu.returnToGame");
				if (screen instanceof TitleScreen) {
					// the main menu gets the Yahu layout: logo top left, a slim navigation under it
					net.yahuclient.mod.ui.TitleLayout.apply(screen, Screens.getWidgets(screen));
				}
				ScreenKeyboardEvents.afterKeyPress(screen).register((s, key) -> {
					if (key.key() == MENU_KEY) {
						openMenu(mc, s);
					}
				});
			}
		});
	}

	/**
	 * Puts "Yahu Mods" into the screen's own button column, right under the anchor button (Realms on the title screen, Back to
	 * Game in the pause menu), as wide as it; everything below moves down a row. Without the anchor (another mod rebuilt the
	 * screen), it goes to the top.
	 */
	private void addMenuButton(Minecraft mc, Screen screen, int width, String anchorKey) {
		var widgets = Screens.getWidgets(screen);
		net.minecraft.client.gui.components.AbstractWidget anchor = null;
		for (var w : widgets) {
			if (w.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getKey().equals(anchorKey)) {
				anchor = w;
				break;
			}
		}
		Button button;
		if (anchor != null) {
			int row = anchor.getHeight() + 4;
			int below = anchor.getY() + anchor.getHeight();
			boolean title = screen instanceof TitleScreen;
			for (var w : widgets) {
				if (title && w.getY() < below) {
					// the title screen has no room at the bottom: the buttons above make space by moving up a row
					w.setY(w.getY() - row);
				} else if (!title && w.getY() >= below) {
					w.setY(w.getY() + row);
				}
			}
			// right under the anchor (on the title screen the anchor moved up, so that is where it used to be)
			button = Button.builder(Component.literal("Yahu Mods"), b -> openMenu(mc, screen))
					.bounds(anchor.getX(), anchor.getY() + row, anchor.getWidth(), anchor.getHeight()).build();
		} else {
			button = Button.builder(Component.literal("Yahu Mods"), b -> openMenu(mc, screen)).bounds(width / 2 - 60, 6, 120, 20).build();
		}
		BlockWidgets.HIGHLIGHTED.add(button);
		widgets.add(button);
	}

	private void openMenu(Minecraft mc, Screen parent) {
		mc.setScreenAndShow(new YahuScreen(parent, mods(), () -> config.save(configFile), mc.level != null));
	}

	// -------------------------------------------------------------------------------------------------------------------
	// the mods as the menu shows them

	private List<ModEntry> mods() {
		return List.of(crosshairMod(), lyricsMod(), skyboxMod());
	}

	private ModEntry crosshairMod() {
		YahuConfig.Crosshair c = config.crosshair;
		List<Control> controls = new ArrayList<>(List.of(
				new Control.Section("Presets"),
				new Control.Buttons("", List.of(
						new Control.Buttons.Action("Classic", () -> preset(c, YahuConfig.Arms.CROSS, 6, 2, 3, false, false)),
						new Control.Buttons.Action("Dot", () -> preset(c, YahuConfig.Arms.NONE, 0, 1, 0, true, false)),
						new Control.Buttons.Action("Pro", () -> preset(c, YahuConfig.Arms.CROSS, 5, 1, 2, true, false)),
						new Control.Buttons.Action("Circle", () -> preset(c, YahuConfig.Arms.NONE, 0, 1, 0, true, true)),
						new Control.Buttons.Action("Big", () -> preset(c, YahuConfig.Arms.CROSS, 14, 3, 6, false, false)))),
				new Control.Section("Arms"),
				new Control.Choice<>("Style", () -> Arrays.asList(YahuConfig.Arms.values()), a -> switch (a) {
					case NONE -> "None";
					case CROSS -> "Cross";
					case T -> "T";
					case X -> "X";
				}, () -> c.arms, v -> c.arms = v),
				Control.Slider.ints("Length", 0, 40, " px", () -> c.armLength, v -> c.armLength = v),
				Control.Slider.ints("Thickness", 1, 10, " px", () -> c.armThickness, v -> c.armThickness = v),
				Control.Slider.ints("Gap", -5, 30, " px", () -> c.gap, v -> c.gap = v),
				new Control.Section("Center dot"),
				new Control.Toggle("Dot", () -> c.dot, v -> c.dot = v),
				Control.Slider.ints("Size", 1, 24, " px", () -> c.dotSize, v -> c.dotSize = v),
				new Control.Toggle("Round", () -> c.dotRound, v -> c.dotRound = v),
				new Control.Toggle("Own color", () -> c.dotOwnColor, v -> c.dotOwnColor = v),
				new Control.Colors("Dot color", () -> c.dotColor, v -> c.dotColor = v),
				new Control.Section("Ring"),
				new Control.Toggle("Ring", () -> c.ring, v -> c.ring = v),
				Control.Slider.ints("Radius", 3, 40, " px", () -> c.ringRadius, v -> c.ringRadius = v),
				Control.Slider.ints("Thickness", 1, 6, " px", () -> c.ringThickness, v -> c.ringThickness = v),
				new Control.Section("Color"),
				new Control.Colors("Color", () -> c.color, v -> c.color = v),
				Control.Slider.ints("Opacity", 10, 100, "%", () -> c.opacity, v -> c.opacity = v),
				Control.Slider.ints("Outline", 0, 4, " px", () -> c.outline, v -> c.outline = v),
				new Control.Colors("Outline color", () -> c.outlineColor, v -> c.outlineColor = v),
				Control.Slider.ints("Outline opacity", 0, 100, "%", () -> c.outlineOpacity, v -> c.outlineOpacity = v),
				new Control.Section("PvP"),
				new Control.Toggle("Color on target", () -> c.targetColorOn, v -> c.targetColorOn = v),
				new Control.Colors("Target color", () -> c.targetColor, v -> c.targetColor = v),
				new Control.Toggle("Hit marker", () -> c.hitMarker, v -> c.hitMarker = v),
				new Control.Colors("Hit marker color", () -> c.hitMarkerColor, v -> c.hitMarkerColor = v),
				Control.Slider.ints("Hit marker size", 2, 14, " px", () -> c.hitMarkerSize, v -> c.hitMarkerSize = v),
				Control.Slider.ints("Hit marker time", 100, 800, " ms", () -> c.hitMarkerMs, v -> c.hitMarkerMs = v),
				new Control.Choice<>("Attack charge", () -> Arrays.asList(YahuConfig.Charge.values()), ch -> switch (ch) {
					case OFF -> "Off";
					case BAR -> "Bar";
					case RING -> "Ring";
				}, () -> c.charge, v -> c.charge = v),
				Control.Slider.ints("Spread when moving", 0, 20, " px", () -> c.spread, v -> c.spread = v),
				new Control.Toggle("Hide in third person", () -> c.hideInThirdPerson, v -> c.hideInThirdPerson = v)));
		return new ModEntry("Crosshair", "PvP ready", "A crosshair of your own, pixel exact: arms, dot and ring in any size, with outline, target color, hit marker and attack charge.",
				Theme.ACCENT_2, "+", () -> c.enabled, v -> c.enabled = v, controls,
				(g, font, x, y, w, h) -> {
					// half sky, half grass, so the colors read on both; idle on the left, in a fight on the right
					g.fillGradient(x, y, x + w, y + h / 2, 0xFF6E9FD8, 0xFF9CC3EC);
					g.fillGradient(x, y + h / 2, x + w, y + h, 0xFF5E8F3A, 0xFF46702A);
					long loop = System.currentTimeMillis() % 1400;
					CrosshairRenderer.draw(g, c, CrosshairRenderer.State.IDLE, x + w * 0.3f, y + h / 2f);
					var fight = new CrosshairRenderer.State(true, loop, Math.min(1f, loop / 1000f), 0.2, false, true);
					CrosshairRenderer.draw(g, c, fight, x + w * 0.7f, y + h / 2f);
				},
				null);
	}

	private static void preset(YahuConfig.Crosshair c, YahuConfig.Arms arms, int length, int thickness, int gap, boolean dot, boolean ring) {
		c.arms = arms;
		c.armLength = length;
		c.armThickness = thickness;
		c.gap = gap;
		c.dot = dot;
		c.dotSize = ring ? 2 : 4;
		c.dotRound = true;
		c.ring = ring;
		c.ringRadius = 9;
		c.ringThickness = 1;
		c.outline = 1;
	}

	private ModEntry lyricsMod() {
		YahuConfig.Lyrics l = config.lyrics;
		List<Control> controls = List.of(
				new Control.Section("Style"),
				new Control.Choice<>("Appear", () -> Arrays.asList(LyricsStyle.values()), s -> s.label, () -> l.style, v -> l.style = v),
				new Control.Choice<>("Where", () -> Arrays.asList(LyricsMode.values()), m -> m.label, () -> l.mode, v -> l.mode = v),
				new Control.Choice<>("Font", () -> Arrays.asList(LyricsFont.values()), f -> f.label, () -> l.font, v -> l.font = v),
				new Control.Slider("Size", 0.5, 2.5, 0.05, v -> Math.round(v * 100) + "%", () -> l.scale, v -> l.scale = v.floatValue()),
				new Control.Slider("Height", 0, 1, 0.01, v -> Math.round(v * 100) + "%", () -> l.height, v -> l.height = v.floatValue()),
				Control.Slider.ints("Line width", 30, 95, "%", () -> l.maxWidth, v -> l.maxWidth = v),
				Control.Slider.ints("Pop", 0, 100, "%", () -> l.popStrength, v -> l.popStrength = v),
				new Control.Section("Text"),
				Control.Slider.ints("Outline", 0, 4, " px", () -> l.outline, v -> l.outline = v),
				new Control.Colors("Outline color", () -> l.outlineColor, v -> l.outlineColor = v),
				new Control.Colors("Sung color", () -> l.sungColor, v -> l.sungColor = v),
				new Control.Colors("Text color", () -> l.upcomingColor, v -> l.upcomingColor = v),
				new Control.Toggle("Color gradient", () -> l.gradient, v -> l.gradient = v),
				new Control.Toggle("Uppercase", () -> l.uppercase, v -> l.uppercase = v),
				new Control.Section("Extras"),
				new Control.Toggle("Next line", () -> l.showNext, v -> l.showNext = v),
				new Control.Toggle("Song title", () -> l.showSongTitle, v -> l.showSongTitle = v),
				new Control.Note(() -> "Follows what plays in Spotify, your browser or any app in the Windows media overlay. While it is on, lyrics are looked up on LRCLIB."));
		return new ModEntry("Lyrics", "Karaoke in the sky", "The song you are playing, sung along in the sky: line by line, word by word or as a kinetic poster.",
				Theme.ACCENT, "♪", () -> l.enabled, v -> l.enabled = v, controls,
				(g, font, x, y, w, h) -> {
					g.fillGradient(x, y, x + w, y + h, 0xFF1A1F4A, 0xFF5A2F6E);
					Draw.glow(g, x + w * 0.8f, y + h * 0.9f, h, Theme.alpha(0xFFFF9A5A, 0.35f));
					double t = (System.currentTimeMillis() % 5200) / 4400.0;
					var frame = sampleFrame("your words land here one by one", Math.min(1, t), "and the next line waits below", "Song – Artist");
					float scale = l.style == LyricsStyle.KINETIC ? 0.75f : 0.9f;
					SkyLyricsRenderer.drawAt(g, font, previewCopy(l), frame, x + w / 2f, y + h / 2f, scale, w);
				},
				this::lyricsStatus);
	}

	// the preview box is small: everything as set, the line wrapped to fit it
	private static YahuConfig.Lyrics previewCopy(YahuConfig.Lyrics l) {
		YahuConfig.Lyrics p = new YahuConfig.Lyrics();
		p.style = l.style;
		p.font = l.font;
		p.outline = l.outline;
		p.outlineColor = l.outlineColor;
		p.sungColor = l.sungColor;
		p.upcomingColor = l.upcomingColor;
		p.gradient = l.gradient;
		p.uppercase = l.uppercase;
		p.showNext = false;
		p.showSongTitle = false;
		p.popStrength = l.popStrength;
		p.maxWidth = 92;
		return p;
	}

	private String lyricsStatus() {
		var song = lyrics.song();
		return switch (lyrics.state(config.lyrics)) {
			case OFF -> "";
			case NOTHING_PLAYING -> "Nothing is playing right now.";
			case LOOKING_UP -> "Looking up " + song.title() + "...";
			case NO_LYRICS -> "No synced lyrics for " + song.title() + ".";
			case READY -> "Now: " + song.title() + " – " + song.artist();
		};
	}

	private ModEntry skyboxMod() {
		YahuConfig.Skybox s = config.skybox;
		List<Control> controls = List.of(
				new Control.Section("Sky"),
				new Control.Choice<>("Sky", () -> Arrays.asList(SkyPreset.values()), p -> p.label, () -> s.preset, v -> s.preset = v),
				new Control.Choice<>("Picture", Skybox::customFiles, f -> f, () -> s.customFile, v -> {
					s.customFile = v;
					s.preset = SkyPreset.CUSTOM;
				}),
				new Control.Buttons("Your own", List.of(
						new Control.Buttons.Action("Open folder", () -> com.mojang.blaze3d.Blaze3D.openPath(Skybox.folder())),
						new Control.Buttons.Action("Use first picture", () -> {
							var files = Skybox.customFiles();
							if (!files.isEmpty()) {
								s.customFile = files.get(0);
								s.preset = SkyPreset.CUSTOM;
							}
						}))),
				new Control.Note(() -> "Put a PNG into the folder: a panorama twice as wide as high, or a cube cross 4 by 3. Then pick it under Picture."),
				new Control.Section("Look"),
				Control.Slider.ints("Brightness", 20, 150, "%", () -> s.brightness, v -> s.brightness = v),
				Control.Slider.ints("Rotation", 0, 60, "°/min", () -> s.rotationSpeed, v -> s.rotationSpeed = v),
				new Control.Toggle("Darker at night", () -> s.followDayNight, v -> s.followDayNight = v),
				new Control.Toggle("Overworld only", () -> s.overworldOnly, v -> s.overworldOnly = v));
		return new ModEntry("Skybox", "Your own sky", "Replace the sky with a black hole, a real-looking day, a golden hour, an aurora night, a nebula, or a picture of your own.",
				0xFFFF9945, "✦", () -> s.enabled, v -> s.enabled = v, controls,
				(g, font, x, y, w, h) -> Skybox.drawPreview(g, s, x, y, w, h),
				() -> Skybox.status(s));
	}
}
