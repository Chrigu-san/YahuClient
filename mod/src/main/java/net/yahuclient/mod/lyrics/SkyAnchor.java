package net.yahuclient.mod.lyrics;

/**
 * Where in the sky the lyrics hang: a fixed compass direction, like a cloud. Turning the camera doesn't move it. When a new
 * line starts while the old spot is out of sight, the lyrics move to where the player looks now, so a new line is never
 * behind them.
 */
public final class SkyAnchor {
	/** How far the view may be turned away from the lyrics before a new line moves them back in front. */
	static final float MAX_AWAY = 60f;

	private float yaw = Float.NaN;
	private Object line;

	public float yaw() {
		return yaw;
	}

	/** Call every frame with the camera's yaw and the line being shown; returns the yaw the lyrics hang at. */
	public float update(float cameraYaw, Object currentLine) {
		boolean newLine = currentLine != null && currentLine != line;
		if (Float.isNaN(yaw) || (newLine && Math.abs(difference(cameraYaw, yaw)) > MAX_AWAY)) {
			yaw = wrap(cameraYaw);
		}
		if (currentLine != null) {
			line = currentLine;
		}
		return yaw;
	}

	public void reset() {
		yaw = Float.NaN;
		line = null;
	}

	/** The signed angle from b to a in degrees, -180 to 180. */
	static float difference(float a, float b) {
		return wrap(a - b);
	}

	static float wrap(float degrees) {
		float d = degrees % 360f;
		if (d >= 180f) {
			d -= 360f;
		} else if (d < -180f) {
			d += 360f;
		}
		return d;
	}
}
