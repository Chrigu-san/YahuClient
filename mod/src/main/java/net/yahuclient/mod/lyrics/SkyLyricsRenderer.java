package net.yahuclient.mod.lyrics;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.yahuclient.mod.config.YahuConfig;

/**
 * The lyrics on the screen ("follow view"): always in view, high up, moving a little with looking up and down. Also draws
 * the preview in the Yahu menu.
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

	/** The lyrics on the screen, where the camera puts them. */
	public static void draw(GuiGraphicsExtractor g, Font font, YahuConfig.Lyrics config, LyricsPainter.Frame frame) {
		Minecraft mc = Minecraft.getInstance();
		Camera camera = mc.gameRenderer.mainCamera();
		int h = g.guiHeight();
		float y = camera.isInitialized() ? screenY(elevation(config.height), camera.xRot(), camera.getFov(), h) : h * 0.25f;
		y = Math.max(40, Math.min(h * 0.42f, y));
		drawAt(g, font, config, frame, g.guiWidth() / 2f, y, 2.0f * Math.max(0.5f, Math.min(2.5f, config.scale)), g.guiWidth());
	}

	/** The lyrics centered on (cx, cy), scaled, wrapping within their share of the given width; the menu preview uses this too. */
	public static void drawAt(GuiGraphicsExtractor g, Font font, YahuConfig.Lyrics config, LyricsPainter.Frame frame, float cx, float cy, float scale,
			float availableWidth) {
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale(scale, scale);
		LyricsPainter.paint(new HudSink(g, font), font, config, frame, availableWidth * Math.max(20, Math.min(100, config.maxWidth)) / 100f / scale);
		g.pose().popMatrix();
	}

	/** Text on the screen: outline copies around, then the text itself. */
	static final class HudSink implements LyricsPainter.Sink {
		private final GuiGraphicsExtractor g;
		private final Font font;

		HudSink(GuiGraphicsExtractor g, Font font) {
			this.g = g;
			this.font = font;
		}

		@Override
		public void text(Component text, float cx, float cy, float scale, float rotation, int color, int outlineColor, int outlineWidth) {
			if ((color >>> 24) < 4) {
				return;
			}
			int w = font.width(text);
			g.pose().pushMatrix();
			g.pose().translate(cx, cy);
			if (rotation != 0) {
				g.pose().rotate((float) Math.toRadians(rotation));
			}
			g.pose().scale(scale, scale);
			int x = -w / 2, y = -font.lineHeight / 2;
			for (int k = 1; k <= outlineWidth; k++) {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dy = -1; dy <= 1; dy++) {
						if (dx != 0 || dy != 0) {
							g.text(font, text, x + dx * k, y + dy * k, outlineColor, false);
						}
					}
				}
			}
			g.text(font, text, x, y, color, false);
			g.pose().popMatrix();
		}
	}
}
