package net.yahuclient.mod;

import java.nio.file.Path;
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
import net.yahuclient.mod.config.LyricsMode;
import net.yahuclient.mod.config.YahuConfig;
import net.yahuclient.mod.crosshair.CrosshairRenderer;
import net.yahuclient.mod.lyrics.LyricsController;
import net.yahuclient.mod.lyrics.SkyAnchor;
import net.yahuclient.mod.lyrics.SkyLyricsRenderer;
import net.yahuclient.mod.lyrics.SkyWorldRenderer;
import net.yahuclient.mod.lyrics.SyncedLyrics;
import net.yahuclient.mod.ui.BlockWidgets;
import net.yahuclient.mod.ui.Control;
import net.yahuclient.mod.ui.ModEntry;
import net.yahuclient.mod.ui.YahuScreen;
import net.yahuclient.mod.ui.YahuUi;

/**
 * The Yahu mod: the Yahu menu (Right Shift, or the button in the pause menu) and our own mods, which the menu lists in its
 * Mods tab. Everything is client side and works on any server.
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

	/** What the lyrics show this frame: the line, how far into it, the next line and the song. */
	private record Shown(String current, double progress, String next, String title, Object lineKey) {
	}

	/**
	 * Lines to show instead of a playing song, for the in-game tests: they use made-up text, so no real song ends up in a
	 * screenshot. Null in normal play.
	 */
	private static volatile Shown testLyrics;

	public static void showTestLyrics(String current, double progress, String next, String title) {
		testLyrics = current == null ? null : new Shown(current, progress, next, title, current);
	}

	private Shown shownLyrics() {
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
		return new Shown(moment.current().text(), moment.progress(), moment.next() != null ? moment.next().text() : null,
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
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> lyrics.shutdown());

		// the custom crosshair replaces the vanilla one while it is on; off, the vanilla one comes back untouched
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (g, delta) -> {
			Minecraft mc = Minecraft.getInstance();
			if (!config.crosshair.enabled) {
				vanilla.extractRenderState(g, delta);
				return;
			}
			if (mc.options.getCameraType().isFirstPerson() && mc.player != null && !mc.player.isSpectator()) {
				var motion = mc.player.getDeltaMovement();
				var state = new CrosshairRenderer.State(
						mc.crosshairPickEntity != null,
						System.currentTimeMillis() - lastHitMs,
						mc.player.getAttackStrengthScale(0f),
						Math.sqrt(motion.x * motion.x + motion.z * motion.z),
						!mc.player.onGround(),
						mc.player.isSprinting());
				CrosshairRenderer.drawPvp(g, config.crosshair, state, g.guiWidth() / 2, g.guiHeight() / 2);
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
			Shown shown = shownLyrics();
			if (shown == null || config.lyrics.mode != LyricsMode.FOLLOW) {
				return;
			}
			SkyLyricsRenderer.draw(g, Minecraft.getInstance().font, config.lyrics, shown.current, shown.progress, shown.next, shown.title);
		});

		// lyrics in the sky: the default, fixed in place like a cloud
		LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
			Minecraft mc = Minecraft.getInstance();
			Shown shown = shownLyrics();
			if (shown == null || config.lyrics.mode != LyricsMode.SKY || mc.gui.hud.isHidden()) {
				skyAnchor.reset();
				return;
			}
			float yaw = skyAnchor.update(mc.gameRenderer.mainCamera().yRot(), shown.lineKey);
			if (shown.lineKey != shownLineKey) {
				shownLineKey = shown.lineKey;
				lineShownAtMs = System.currentTimeMillis();
			}
			SkyWorldRenderer.draw(context.poseStack(), context.submitNodeCollector(), mc.font, config.lyrics, yaw, shown.current, shown.progress,
					shown.next, shown.title, System.currentTimeMillis() - lineShownAtMs);
		});

		// a green "Yahu Mods" button at the top of the title screen and the pause menu, and Right Shift works there too
		// (key mappings only fire while playing, not in menus)
		ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
			if (screen instanceof PauseScreen || screen instanceof TitleScreen) {
				Button button = Button.builder(Component.literal("Yahu Mods"), b -> openMenu(mc, screen)).bounds(width / 2 - 60, 6, 120, 20).build();
				BlockWidgets.HIGHLIGHTED.add(button);
				Screens.getWidgets(screen).add(button);
				ScreenKeyboardEvents.afterKeyPress(screen).register((s, key) -> {
					if (key.key() == MENU_KEY) {
						openMenu(mc, s);
					}
				});
			}
		});
	}

	private void openMenu(Minecraft mc, Screen parent) {
		mc.setScreenAndShow(new YahuScreen(parent, mods(), () -> config.save(configFile), MENU_KEY));
	}

	private List<ModEntry> mods() {
		YahuConfig.Crosshair c = config.crosshair;
		ModEntry crosshair = new ModEntry("Crosshair", "Your own crosshair: shape, size, gap, thickness and color.", YahuUi.SKY,
				() -> c.enabled, v -> c.enabled = v,
				List.of(
						new Control.Heading("Look"),
						new Control.Cycle("Shape", () -> c.shape.label, () -> c.shape = c.shape.next()),
						new Control.Slider("Size", 1, 12, "", () -> c.size, v -> c.size = v),
						new Control.Slider("Gap", 0, 8, "", () -> c.gap, v -> c.gap = v),
						new Control.Slider("Thickness", 1, 4, "", () -> c.thickness, v -> c.thickness = v),
						new Control.Swatches("Color", () -> c.color, v -> c.color = v),
						new Control.Slider("Opacity", 10, 100, "%", () -> c.opacity, v -> c.opacity = v),
						new Control.Toggle("Outline", () -> c.outline, v -> c.outline = v),
						new Control.Swatches("Outline color", () -> c.outlineColor, v -> c.outlineColor = v),
						new Control.Toggle("Center dot", () -> c.dot, v -> c.dot = v),
						new Control.Heading("PvP"),
						new Control.Toggle("Color on target", () -> c.targetColorOn, v -> c.targetColorOn = v),
						new Control.Swatches("Target color", () -> c.targetColor, v -> c.targetColor = v),
						new Control.Toggle("Hit marker", () -> c.hitMarker, v -> c.hitMarker = v),
						new Control.Swatches("Hit marker color", () -> c.hitMarkerColor, v -> c.hitMarkerColor = v),
						new Control.Toggle("Attack charge bar", () -> c.cooldown, v -> c.cooldown = v),
						new Control.Toggle("Open up when moving", () -> c.dynamicGap, v -> c.dynamicGap = v)),
				(g, font, x, y, w, h) -> {
					// sky and grass, so the crosshair is seen on both: on the left as it is, on the right on a target with
					// a hit landing and the attack recharging, so every PvP setting shows at once
					g.fill(x, y, x + w, y + h / 2, 0xFF6FA8E8);
					g.fill(x, y + h / 2, x + w, y + h, 0xFF5E9E3A);
					long loop = System.currentTimeMillis() % 1200;
					var pvp = new CrosshairRenderer.State(true, loop, Math.min(1f, loop / 900f), 0, false, false);
					CrosshairRenderer.draw(g, c, x + w / 4, y + h / 2);
					CrosshairRenderer.drawPvp(g, c, pvp, x + 3 * w / 4, y + h / 2 - 4);
				},
				null);

		YahuConfig.Lyrics l = config.lyrics;
		ModEntry lyricsMod = new ModEntry("Lyrics", "Karaoke in the sky: the line being sung in the song you play (Spotify, browser...).", YahuUi.AMETHYST,
				() -> l.enabled, v -> l.enabled = v,
				List.of(
						new Control.Cycle("Where", () -> l.mode.label, () -> l.mode = l.mode.next()),
						new Control.Slider("Height", 0, 100, "%", () -> Math.round(l.height * 100), v -> l.height = v / 100f),
						new Control.Slider("Size", 50, 200, "%", () -> Math.round(l.scale * 100), v -> l.scale = v / 100f),
						new Control.Swatches("Sung color", () -> l.sungColor, v -> l.sungColor = v),
						new Control.Toggle("Show next line", () -> l.showNext, v -> l.showNext = v),
						new Control.Toggle("Show song title", () -> l.showSongTitle, v -> l.showSongTitle = v)),
				(g, font, x, y, w, h) -> {
					// a dusk sky with sample lines (our own words), the sweep running in a loop
					g.fillGradient(x, y, x + w, y + h, 0xFF2A3C9C, 0xFFE8709A);
					double t = (System.currentTimeMillis() % 4000) / 4000.0;
					YahuConfig.Lyrics preview = copyForPreview(l);
					SkyLyricsRenderer.drawAt(g, font, preview, "Your lyrics up here", t, "the next line waits below", "Song – Artist", x + w / 2, y + h / 2);
				},
				this::lyricsStatus);
		return List.of(crosshair, lyricsMod);
	}

	// the preview box is small: the same colors and switches, at a size that fits
	private static YahuConfig.Lyrics copyForPreview(YahuConfig.Lyrics l) {
		YahuConfig.Lyrics p = new YahuConfig.Lyrics();
		p.sungColor = l.sungColor;
		p.upcomingColor = l.upcomingColor;
		p.showNext = l.showNext;
		p.showSongTitle = l.showSongTitle;
		p.scale = 0.5f * l.scale;
		return p;
	}

	private String lyricsStatus() {
		var song = lyrics.song();
		return switch (lyrics.state(config.lyrics)) {
			case OFF -> "Off";
			case NOTHING_PLAYING -> "Nothing is playing. Start a song in Spotify or your browser.";
			case LOOKING_UP -> "Looking up lyrics for " + song.title() + "...";
			case NO_LYRICS -> "No synced lyrics found for " + song.title() + ".";
			case READY -> "Now: " + song.title() + " – " + song.artist();
		};
	}
}
