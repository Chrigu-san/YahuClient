package net.yahuclient.mod.test;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.PauseScreen;
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
			context.clickScreenButton("Yahu");
			context.waitForScreen(YahuScreen.class);
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitForScreen(PauseScreen.class);
		}
	}
}
