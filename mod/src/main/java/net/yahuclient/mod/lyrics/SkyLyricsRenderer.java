package net.yahuclient.mod.lyrics;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.yahuclient.mod.config.YahuConfig;
import net.yahuclient.mod.ui.YahuUi;

/**
 * Draws the line being sung big in the sky, karaoke style: the part already sung turns into the sung color from left to
 * right, the next line waits small underneath.
 *
 * The text hangs at a fixed height above the horizon: looking up moves it down the screen and looking down moves it up, as
 * if it were in the sky. It follows where you look sideways so it is never behind you.
 */
public final class SkyLyricsRenderer {
	private SkyLyricsRenderer() {
	}

	/** The degrees above the horizon the lyrics hang at, for the height setting (0 to 1). */
	static float elevation(float height) {
		return 4 + Math.max(0, Math.min(1, height)) * 26;
	}

	/** Screen y of a point this many degrees above the horizon, for a camera pitched down by pitch degrees. */
	static float screenY(float elevationDeg, float pitchDeg, float verticalFovDeg, int screenHeight) {
		double angle = Math.toRadians(elevationDeg + pitchDeg);
		double half = Math.toRadians(verticalFovDeg / 2);
		if (angle >= Math.PI / 2 - 0.01) {
			return -1000;
		}
		return (float) (screenHeight / 2.0 - Math.tan(angle) / Math.tan(half) * screenHeight / 2.0);
	}

	public static void draw(GuiGraphicsExtractor g, Font font, YahuConfig.Lyrics config, String current, double progress, String next,
			String title) {
		Minecraft mc = Minecraft.getInstance();
		Camera camera = mc.gameRenderer.mainCamera();
		int w = g.guiWidth(), h = g.guiHeight();
		float y = camera.isInitialized() ? screenY(elevation(config.height), camera.xRot(), camera.getFov(), h) : h * 0.25f;
		// never leave the screen: when you look far down it stays at the top edge
		y = Math.max(18, Math.min(h * 0.45f, y));
		drawAt(g, font, config, current, progress, next, title, w / 2, Math.round(y));
	}

	/** Draws the lyrics centered on (cx, y); the menu preview uses this too. */
	public static void drawAt(GuiGraphicsExtractor g, Font font, YahuConfig.Lyrics config, String current, double progress, String next,
			String title, int cx, int y) {
		float scale = 2.0f * Math.max(0.5f, Math.min(2.0f, config.scale));
		if (config.showSongTitle && title != null && !title.isEmpty()) {
			String t = "♪ " + title;
			g.text(font, t, cx - font.width(t) / 2, y - 12 - Math.round(font.lineHeight * scale / 2), YahuUi.withAlpha(YahuUi.MUTED, 0.85f), true);
		}
		if (current != null && !current.isEmpty()) {
			int textW = font.width(current);
			float left = cx - textW * scale / 2;
			float top = y - font.lineHeight * scale / 2;
			int upcoming = config.upcomingColor | 0xFF000000;
			int sung = config.sungColor | 0xFF000000;

			g.pose().pushMatrix();
			g.pose().translate(left, top);
			g.pose().scale(scale, scale);
			g.text(font, current, 0, 0, upcoming, true);
			g.pose().popMatrix();

			// the sung part: the same text again in the sung color, cut off where the song is
			int sweep = Math.round(textW * scale * (float) Math.max(0, Math.min(1, progress)));
			if (sweep > 0) {
				g.enableScissor(Math.round(left) - 1, Math.round(top) - 2, Math.round(left) + sweep, Math.round(top + font.lineHeight * scale) + 2);
				g.pose().pushMatrix();
				g.pose().translate(left, top);
				g.pose().scale(scale, scale);
				g.text(font, current, 0, 0, sung, true);
				g.pose().popMatrix();
				g.disableScissor();
			}
		}
		if (config.showNext && next != null && !next.isEmpty()) {
			int ny = y + Math.round(font.lineHeight * scale / 2) + 6;
			g.text(font, next, cx - font.width(next) / 2, ny, YahuUi.withAlpha(YahuUi.TEXT, 0.6f), true);
		}
	}
}
