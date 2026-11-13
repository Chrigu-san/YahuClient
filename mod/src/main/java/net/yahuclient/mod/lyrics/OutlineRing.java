package net.yahuclient.mod.lyrics;

/**
 * Where the outline copies of a text go: evenly around a circle of the outline's width, so the outline is equally thick
 * in every direction and can be any width, a quarter of a font pixel included. More copies for wider outlines, so their
 * edge stays round instead of turning into a star.
 */
final class OutlineRing {
	private OutlineRing() {
	}

	/** Offsets as x0, y0, x1, y1, ... for an outline of this width in font pixels (empty for none). */
	static float[] offsets(float width) {
		if (width <= 0.01f) {
			return new float[0];
		}
		int copies = width <= 0.6f ? 8 : width <= 1.5f ? 12 : 16;
		float[] out = new float[copies * 2];
		for (int i = 0; i < copies; i++) {
			double a = Math.PI * 2 * i / copies;
			out[i * 2] = (float) (Math.cos(a) * width);
			out[i * 2 + 1] = (float) (Math.sin(a) * width);
		}
		return out;
	}
}
