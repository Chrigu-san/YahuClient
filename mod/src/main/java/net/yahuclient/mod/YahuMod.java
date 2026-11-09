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
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.yahuclient.mod.config.YahuConfig;
import net.yahuclient.mod.crosshair.CrosshairRenderer;
import net.yahuclient.mod.lyrics.LyricsController;
import net.yahuclient.mod.lyrics.SkyLyricsRenderer;
import net.yahuclient.mod.lyrics.SyncedLyrics;
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
				CrosshairRenderer.draw(g, config.crosshair, g.guiWidth() / 2, g.guiHeight() / 2);
			}
		});

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(ID, "lyrics"), (g, delta) -> {
			SyncedLyrics.Moment moment = lyrics.moment();
			if (!config.lyrics.enabled || moment == null || moment.current() == null) {
				return;
			}
			var song = lyrics.song();
			SkyLyricsRenderer.draw(g, Minecraft.getInstance().font, config.lyrics, moment.current().text(), moment.progress(),
					moment.next() != null ? moment.next().text() : null, song != null ? song.title() + " – " + song.artist() : null);
		});

		// a small Yahu button in the top left corner of the pause menu
		ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
			if (screen instanceof PauseScreen) {
				Screens.getWidgets(screen).add(Button.builder(Component.literal("Yahu"), b -> openMenu(mc, screen)).bounds(6, 6, 48, 20).build());
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
						new Control.Cycle("Shape", () -> c.shape.label, () -> c.shape = c.shape.next()),
						new Control.Slider("Size", 1, 12, "", () -> c.size, v -> c.size = v),
						new Control.Slider("Gap", 0, 8, "", () -> c.gap, v -> c.gap = v),
						new Control.Slider("Thickness", 1, 4, "", () -> c.thickness, v -> c.thickness = v),
						new Control.Swatches("Color", () -> c.color, v -> c.color = v),
						new Control.Toggle("Dark outline", () -> c.outline, v -> c.outline = v),
						new Control.Toggle("Center dot", () -> c.dot, v -> c.dot = v)),
				(g, font, x, y, w, h) -> {
					// a strip of sky and grass, so the crosshair is seen on both
					g.fill(x, y, x + w, y + h / 2, 0xFF6FA8E8);
					g.fill(x, y + h / 2, x + w, y + h, 0xFF5E9E3A);
					CrosshairRenderer.draw(g, c, x + w / 2, y + h / 2);
				},
				null);

		YahuConfig.Lyrics l = config.lyrics;
		ModEntry lyricsMod = new ModEntry("Lyrics", "Karaoke in the sky: the line being sung in the song you play (Spotify, browser...).", YahuUi.AMETHYST,
				() -> l.enabled, v -> l.enabled = v,
				List.of(
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
