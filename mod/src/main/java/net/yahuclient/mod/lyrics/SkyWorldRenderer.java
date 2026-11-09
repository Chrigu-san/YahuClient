package net.yahuclient.mod.lyrics;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.yahuclient.mod.config.YahuConfig;
import net.yahuclient.mod.ui.YahuUi;

/**
 * The lyrics as a big sign hung in the sky, like a cloud: at a fixed compass direction (SkyAnchor) and a fixed height above
 * the horizon, far away, facing the player. It is placed relative to the camera, so walking doesn't bring it closer, and
 * turning the camera leaves it where it is.
 *
 * The look: bold text with a dark outline; the part already sung runs from grass to sky blue, the letter being sung right
 * now lifts a little and lights up; the song title sits small and gold above, the next line small and pale below. A new
 * line fades and grows in.
 */
public final class SkyWorldRenderer {
	private static final float DISTANCE = 64f;      // blocks away; the fog of short render distances starts further out
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final int OUTLINE = 0xFF0B0E18;

	private SkyWorldRenderer() {
	}

	/**
	 * @param yaw       compass direction of the lyrics, in Minecraft yaw degrees
	 * @param lineAgeMs how long the current line has been up, for the fade-in
	 */
	public static void draw(PoseStack pose, SubmitNodeCollector out, Font font, YahuConfig.Lyrics config, float yaw, String current,
			double progress, String next, String title, long lineAgeMs) {
		float elevation = SkyLyricsRenderer.elevation(config.height) + 6;  // a little higher than on screen: it is the sky
		double yawRad = Math.toRadians(yaw), elevRad = Math.toRadians(elevation);
		float x = (float) (-Math.sin(yawRad) * Math.cos(elevRad) * DISTANCE);
		float y = (float) (Math.sin(elevRad) * DISTANCE);
		float z = (float) (Math.cos(yawRad) * Math.cos(elevRad) * DISTANCE);

		float appear = Math.min(1f, lineAgeMs / 280f);
		float ease = 1f - (1f - appear) * (1f - appear) * (1f - appear);
		float scale = DISTANCE * 0.011f * Math.max(0.5f, Math.min(2f, config.scale)) * (0.9f + 0.1f * ease);

		pose.pushPose();
		pose.translate(x, y, z);
		// face the player the way a camera at the player looking at this spot would see it
		pose.rotate(new Quaternionf().rotationYXZ((float) (Math.PI - yawRad), (float) elevRad, 0f));
		pose.scale(scale, -scale, scale);

		if (config.showSongTitle && title != null && !title.isEmpty()) {
			pose.pushPose();
			pose.translate(0, -font.lineHeight * 1.6f, 0);
			pose.scale(0.5f, 0.5f, 0.5f);
			String t = "♪ " + title;
			text(pose, out, font, t, -font.width(t) / 2f, 0, YahuUi.withAlpha(YahuUi.GOLD, 0.9f * ease));
			pose.popPose();
		}

		if (current != null && !current.isEmpty()) {
			drawSweep(pose, out, font, config, current, progress, ease);
		}

		if (config.showNext && next != null && !next.isEmpty()) {
			pose.pushPose();
			pose.translate(0, font.lineHeight * 1.35f, 0);
			pose.scale(0.55f, 0.55f, 0.55f);
			text(pose, out, font, next, -font.width(next) / 2f, 0, YahuUi.withAlpha(YahuUi.TEXT, 0.55f * ease));
			pose.popPose();
		}
		pose.popPose();
	}

	/** The current line letter by letter: sung letters in the gradient, the letter being sung lifted, the rest white. */
	private static void drawSweep(PoseStack pose, SubmitNodeCollector out, Font font, YahuConfig.Lyrics config, String line, double progress,
			float ease) {
		int[] codepoints = line.codePoints().toArray();
		float total = font.width(line);
		float left = -total / 2f;
		// how many letters are sung; the fraction is how far into the next one
		double sungExact = Math.max(0, Math.min(1, progress)) * codepoints.length;
		int sungWhole = (int) Math.floor(sungExact);
		float partial = (float) (sungExact - sungWhole);
		int sungStart = config.sungColor | 0xFF000000;
		int sungEnd = YahuUi.SKY;
		int upcoming = config.upcomingColor | 0xFF000000;

		float cursor = left;
		for (int i = 0; i < codepoints.length; i++) {
			String letter = new String(Character.toChars(codepoints[i]));
			float w = font.width(letter);
			float t = codepoints.length > 1 ? i / (float) (codepoints.length - 1) : 0f;
			int sungColor = YahuUi.mix(sungStart, sungEnd, t);
			int color;
			float lift = 0;
			if (i < sungWhole) {
				color = sungColor;
			} else if (i == sungWhole) {
				// the letter being sung: lights up from white to its color and hops a little
				color = YahuUi.mix(upcoming, YahuUi.mix(sungColor, 0xFFFFFFFF, 0.35f), partial);
				lift = (float) Math.sin(partial * Math.PI) * 1.6f;
			} else {
				color = upcoming;
			}
			text(pose, out, font, letter, cursor, -lift, YahuUi.withAlpha(color, ease));
			cursor += w;
		}
	}

	private static void text(PoseStack pose, SubmitNodeCollector out, Font font, String text, float x, float y, int color) {
		out.submitText(pose, x, y, FormattedCharSequence.forward(text, Style.EMPTY), false, Font.DisplayMode.NORMAL, FULL_BRIGHT, color, 0,
				YahuUi.withAlpha(OUTLINE, ((color >>> 24) / 255f)));
	}
}
