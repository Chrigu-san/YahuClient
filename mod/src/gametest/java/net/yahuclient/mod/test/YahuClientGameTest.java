package net.yahuclient.mod.test;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.yahuclient.mod.YahuMod;
import net.yahuclient.mod.config.LyricsMode;
import net.yahuclient.mod.config.LyricsStyle;
import net.yahuclient.mod.config.SkyPreset;
import net.yahuclient.mod.config.YahuConfig;
import net.yahuclient.mod.ui.YahuScreen;

/**
 * Plays through the Yahu mod in a real game and takes screenshots for a look by eye (run/clientGameTest/screenshots): the
 * restyled title screen and options, the Yahu menu with every mod page, the crosshair, the lyrics in the sky in every style
 * and each built-in sky.
 *
 * The live lyrics stay off: they would show whatever song the machine happens to play. The lyrics are checked with made-up
 * lines instead.
 */
public class YahuClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			YahuMod.config().lyrics.enabled = false;
			YahuMod.config().skybox.enabled = false;
		});

		context.waitForScreen(TitleScreen.class);
		context.waitTicks(60);  // the title screen fades its buttons in
		context.takeScreenshot("yahu-title-screen");
		// the mouse over the first entry: the hover look of the navigation
		double[] at = context.computeOnClient(mc -> {
			var w = net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(mc.gui.screen()).stream()
					.filter(b -> b.getMessage().getString().equals("Singleplayer")).findFirst().orElseThrow();
			double s = mc.getWindow().getGuiScale();
			return new double[] {(w.getX() + 30) * s, (w.getY() + w.getHeight() / 2.0) * s};
		});
		context.getInput().setCursorPos(at[0], at[1]);
		context.waitTicks(10);
		context.takeScreenshot("yahu-title-hover");
		context.getInput().setCursorPos(0, 0);
		context.clickScreenButton("menu.options");
		context.waitTicks(5);
		context.takeScreenshot("yahu-options");
		context.clickScreenButton("options.video");
		context.waitTicks(5);
		context.takeScreenshot("yahu-video-settings");
		context.setScreen(() -> new TitleScreen());
		context.waitForScreen(TitleScreen.class);

		// with no worlds yet, Singleplayer opens the create world screen: tabs, text fields, switches
		context.clickScreenButton("menu.singleplayer");
		context.waitTicks(10);
		context.takeScreenshot("yahu-create-world");
		context.setScreen(() -> new TitleScreen());
		context.waitForScreen(TitleScreen.class);

		// the Yahu menu from the title screen: every page
		context.getInput().pressKey(InputConstants.KEY_RSHIFT);
		context.waitForScreen(YahuScreen.class);
		context.waitTicks(10);
		context.takeScreenshot("yahu-menu-crosshair");
		for (int page = 1; page <= 3; page++) {
			int p = page;
			context.runOnClient(mc -> ((YahuScreen) mc.gui.screen()).select(p));
			context.waitTicks(10);
			context.takeScreenshot("yahu-menu-" + (page == 1 ? "lyrics" : page == 2 ? "skybox" : "inventory"));
		}
		// scrolled down on the crosshair page, where the PvP settings are
		context.runOnClient(mc -> ((YahuScreen) mc.gui.screen()).select(0));
		context.waitTicks(5);
		context.runOnClient(mc -> {
			var screen = (YahuScreen) mc.gui.screen();
			screen.mouseScrolled(screen.width - 40, screen.height / 2.0, 0, -40);
		});
		context.waitTicks(15);
		context.takeScreenshot("yahu-menu-crosshair-scrolled");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(TitleScreen.class);

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getServer().runCommand("time set day");
			world.getServer().runCommand("weather clear");
			context.waitTicks(40);
			context.getInput().lookAt(0, 0);
			context.waitTicks(5);
			context.takeScreenshot("yahu-crosshair-default");

			context.runOnClient(mc -> {
				YahuConfig.Crosshair c = YahuMod.config().crosshair;
				c.arms = YahuConfig.Arms.CROSS;
				c.armLength = 5;
				c.armThickness = 1;
				c.gap = 2;
				c.dot = true;
				c.dotSize = 2;
				c.ring = true;
				c.ringRadius = 10;
				c.color = 0xFF3FD5F2;
			});
			context.waitTicks(2);
			context.takeScreenshot("yahu-crosshair-custom");

			// lyrics in the sky, three styles, made-up lines
			for (LyricsStyle style : LyricsStyle.values()) {
				context.runOnClient(mc -> {
					YahuMod.config().lyrics.enabled = true;
					YahuMod.config().lyrics.mode = LyricsMode.SKY;
					YahuMod.config().lyrics.style = style;
					YahuMod.showTestLyrics("these made up words float up into the evening sky", 0.7, "and here comes another line", "Test Song – Yahu");
				});
				context.getInput().lookAt(0, -22);
				context.waitTicks(20);
				context.takeScreenshot("yahu-lyrics-" + style.name().toLowerCase());
			}
			// a look preset in the sky: a different typeface, gradient and a thin outline
			context.runOnClient(mc -> {
				var l = YahuMod.config().lyrics;
				l.style = LyricsStyle.WORDS;
				l.font = net.yahuclient.mod.config.LyricsFont.RIGHTEOUS;
				l.sungColor = 0xFF3FD5F2;
				l.gradientColor = 0xFFB46CFF;
				l.upcomingColor = 0xFFE8E6FF;
				l.outline = 0.5f;
				l.outlineColor = 0xFF140A2A;
			});
			context.waitTicks(20);
			context.takeScreenshot("yahu-lyrics-preset-neon");
			context.runOnClient(mc -> {
				YahuMod.config().lyrics.mode = LyricsMode.FOLLOW;
				YahuMod.config().lyrics.style = LyricsStyle.KINETIC;
			});
			context.waitTicks(10);
			context.takeScreenshot("yahu-lyrics-follow-kinetic");
			context.runOnClient(mc -> {
				YahuMod.config().lyrics.enabled = false;
				YahuMod.showTestLyrics(null, 0, null, null);
			});

			// every built-in sky, looking up a little
			for (SkyPreset preset : SkyPreset.values()) {
				if (preset == SkyPreset.CUSTOM) {
					continue;
				}
				context.runOnClient(mc -> {
					YahuMod.config().skybox.enabled = true;
					YahuMod.config().skybox.preset = preset;
					YahuMod.config().skybox.rotationSpeed = 0;
				});
				context.waitTicks(80);  // painting a sky takes a moment
				context.getInput().lookAt(-160, -25);
				context.waitTicks(3);
				context.takeScreenshot("yahu-sky-" + preset.name().toLowerCase());
			}
			context.runOnClient(mc -> YahuMod.config().skybox.enabled = false);

			// the inventory mod: the player's inventory with a few items, the hotbar under it
			world.getServer().runCommand("give @a minecraft:diamond_sword");
			world.getServer().runCommand("give @a minecraft:oak_log 32");
			world.getServer().runCommand("give @a minecraft:apple 5");
			context.waitTicks(10);
			context.setScreen(() -> new net.minecraft.client.gui.screens.inventory.InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));
			context.waitTicks(10);
			context.takeScreenshot("yahu-inventory");
			context.setScreen(() -> null);
			context.waitTicks(5);
			context.takeScreenshot("yahu-hotbar");

			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitForScreen(PauseScreen.class);
			context.waitTicks(5);
			context.takeScreenshot("yahu-pause-menu");
			context.clickScreenButton("Yahu Mods");
			context.waitForScreen(YahuScreen.class);
			context.waitTicks(10);
			context.takeScreenshot("yahu-menu-in-world");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitForScreen(PauseScreen.class);
		}
	}
}
