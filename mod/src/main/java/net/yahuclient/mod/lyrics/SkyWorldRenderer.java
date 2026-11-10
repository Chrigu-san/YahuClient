package net.yahuclient.mod.lyrics;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.network.chat.Component;
import net.yahuclient.mod.config.YahuConfig;

/**
 * The lyrics as a big sign hung in the sky, like a cloud: at a fixed compass direction (SkyAnchor) and a fixed height above
 * the horizon, far away, facing the player. It is placed relative to the camera, so walking doesn't bring it closer, and
 * turning the camera leaves it where it is. What is drawn and how it moves comes from LyricsPainter.
 */
public final class SkyWorldRenderer {
	// blocks away: near enough that even a short render distance's fog doesn't reach it; the size is scaled to match
	private static final float DISTANCE = 20f;
	private static final int FULL_BRIGHT = 0xF000F0;

	private SkyWorldRenderer() {
	}

	/**
	 * @param yaw       compass direction of the lyrics, in Minecraft yaw degrees
	 * @param lineAgeMs how long the current line has been up, for the block's own fade-in
	 */
	public static void draw(PoseStack pose, SubmitNodeCollector out, Font font, YahuConfig.Lyrics config, float yaw, LyricsPainter.Frame frame,
			long lineAgeMs) {
		float elevation = SkyLyricsRenderer.elevation(config.height) + 6;  // a little higher than on screen: it is the sky
		double yawRad = Math.toRadians(yaw), elevRad = Math.toRadians(elevation);
		float x = (float) (-Math.sin(yawRad) * Math.cos(elevRad) * DISTANCE);
		float y = (float) (Math.sin(elevRad) * DISTANCE);
		float z = (float) (Math.cos(yawRad) * Math.cos(elevRad) * DISTANCE);

		float appear = Math.min(1f, lineAgeMs / 280f);
		float ease = 1f - (1f - appear) * (1f - appear) * (1f - appear);
		float scale = DISTANCE * 0.011f * Math.max(0.5f, Math.min(2.5f, config.scale)) * (0.94f + 0.06f * ease);

		pose.pushPose();
		pose.translate(x, y, z);
		// face the player the way a camera at the player looking at this spot would see it
		pose.rotate(new Quaternionf().rotationYXZ((float) (Math.PI - yawRad), (float) elevRad, 0f));
		pose.scale(scale, -scale, scale);
		// how many font pixels fit the share of the view: one font pixel spans 0.011 radians times the size setting
		var mc = net.minecraft.client.Minecraft.getInstance();
		double vFov = Math.toRadians(mc.gameRenderer.mainCamera().getFov());
		double aspect = mc.getWindow().getWidth() / (double) Math.max(1, mc.getWindow().getHeight());
		double hFov = 2 * Math.atan(Math.tan(vFov / 2) * aspect);
		float perPixel = 0.011f * Math.max(0.5f, Math.min(2.5f, config.scale));
		float maxWidth = (float) (hFov * Math.max(20, Math.min(100, config.maxWidth)) / 100.0 / perPixel);
		LyricsPainter.paint(new WorldSink(pose, out, font), font, config, frame, maxWidth);
		pose.popPose();
	}

	/** Text in the world: outline copies behind, the text itself pulled slightly forward so they never flicker. */
	static final class WorldSink implements LyricsPainter.Sink {
		private final PoseStack pose;
		private final SubmitNodeCollector out;
		private final Font font;

		WorldSink(PoseStack pose, SubmitNodeCollector out, Font font) {
			this.pose = pose;
			this.out = out;
			this.font = font;
		}

		@Override
		public void text(Component text, float cx, float cy, float scale, float rotation, int color, int outlineColor, int outlineWidth) {
			if ((color >>> 24) < 4) {
				return;
			}
			var sequence = text.getVisualOrderText();
			float w = font.width(text);
			pose.pushPose();
			pose.translate(cx, cy, 0);
			if (rotation != 0) {
				pose.rotate(Axis.ZP.rotationDegrees(rotation));
			}
			pose.scale(scale, scale, scale);
			float x = -w / 2f, y = -font.lineHeight / 2f;
			for (int k = 1; k <= outlineWidth; k++) {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dy = -1; dy <= 1; dy++) {
						if (dx != 0 || dy != 0) {
							out.submitText(pose, x + dx * k * 0.75f, y + dy * k * 0.75f, sequence, false, Font.DisplayMode.NORMAL, FULL_BRIGHT, outlineColor, 0, 0);
						}
					}
				}
			}
			out.submitText(pose, x, y, sequence, false, Font.DisplayMode.POLYGON_OFFSET, FULL_BRIGHT, color, 0, 0);
			pose.popPose();
		}
	}
}
