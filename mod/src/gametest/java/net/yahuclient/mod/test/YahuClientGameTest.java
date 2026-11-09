package net.yahuclient.mod.test;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.yahuclient.mod.YahuMod;
import net.yahuclient.mod.config.CrosshairShape;
import net.yahuclient.mod.ui.YahuScreen;
import net.yahuclient.mod.ui.YahuUi;

/**
 * Plays through the Yahu mod in a real game: the crosshair in a world, the menu opened with Right Shift, both mod pages and
 * the Yahu button in the pause menu. Screenshots land in run/clientGameTest/screenshots for a look by eye.
 *
 * The live lyrics stay off: they would show whatever song the machine happens to play. The lyrics page is checked through
 * its preview, which uses our own sample lines.
 */
public class YahuClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(mc -> YahuMod.config().lyrics.enabled = false);

		// Minecraft's own screens in the block look: title screen, then options with its buttons and sliders
		context.waitForScreen(TitleScreen.class);
		context.waitTicks(60);  // the title screen fades its buttons in
		context.takeScreenshot("yahu-title-screen");
		context.clickScreenButton("menu.options");
		context.waitTicks(5);
		context.takeScreenshot("yahu-options");
		context.clickScreenButton("options.video");
		context.waitTicks(5);
		context.takeScreenshot("yahu-video-settings");
		context.setScreen(() -> null);
		context.setScreen(() -> new TitleScreen());
		context.waitForScreen(TitleScreen.class);

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getServer().runCommand("time set day");
			world.getServer().runCommand("weather clear");
			context.waitTicks(40);
			context.getInput().lookAt(0, 0);
			context.waitTicks(5);
			context.takeScreenshot("yahu-crosshair-default");

			context.runOnClient(mc -> {
				var c = YahuMod.config().crosshair;
				c.shape = CrosshairShape.CIRCLE;
				c.color = YahuUi.SKY;
				c.size = 6;
				c.dot = true;
			});
			context.waitTicks(2);
			context.takeScreenshot("yahu-crosshair-circle");

			// lyrics in the sky, with made-up lines in place of a real song
			context.runOnClient(mc -> {
				YahuMod.config().lyrics.enabled = true;
				YahuMod.showTestLyrics("Hello from the sky above", 0.55, "this line comes up next", "Test Song – Yahu");
			});
			context.getInput().lookAt(0, -20);
			context.waitTicks(20);
			context.takeScreenshot("yahu-lyrics-sky");
			// turning the camera must leave the lyrics where they are in the sky, like a cloud
			context.getInput().lookAt(25, -20);
			context.waitTicks(5);
			context.takeScreenshot("yahu-lyrics-sky-turned");
			context.runOnClient(mc -> YahuMod.config().lyrics.mode = net.yahuclient.mod.config.LyricsMode.FOLLOW);
			context.waitTicks(5);
			context.takeScreenshot("yahu-lyrics-follow");
			context.runOnClient(mc -> {
				YahuMod.config().lyrics.mode = net.yahuclient.mod.config.LyricsMode.SKY;
				YahuMod.config().lyrics.enabled = false;
				YahuMod.showTestLyrics(null, 0, null, null);
			});
			context.getInput().lookAt(0, 0);
			context.waitTicks(2);

			// Right Shift opens the menu on the Mods tab with the first mod, Right Shift closes it again
			context.getInput().pressKey(InputConstants.KEY_RSHIFT);
			context.waitForScreen(YahuScreen.class);
			context.takeScreenshot("yahu-menu-crosshair");

			context.runOnClient(mc -> ((YahuScreen) mc.gui.screen()).select(1));
			context.waitTicks(10);
			context.takeScreenshot("yahu-menu-lyrics");

			// in a small window the settings scroll; the last switch must be reachable
			context.runOnClient(mc -> {
				var screen = (YahuScreen) mc.gui.screen();
				screen.mouseScrolled(screen.width - 20, screen.height / 2.0, 0, -10);
			});
			context.waitTicks(2);
			context.takeScreenshot("yahu-menu-lyrics-scrolled");

			context.getInput().pressKey(InputConstants.KEY_RSHIFT);
			context.waitForScreen(null);

			// the pause menu has a Yahu button that opens the same menu
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitForScreen(PauseScreen.class);
			context.takeScreenshot("yahu-pause-menu");
			context.clickScreenButton("Yahu Mods");
			context.waitForScreen(YahuScreen.class);
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitForScreen(PauseScreen.class);
		}
	}
}
