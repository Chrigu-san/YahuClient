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
		// a dense outer ring keeps the edge round; an inner ring at half the width fills between it and the letter
		int outer = width <= 0.6f ? 16 : 20, inner = 10;
		float[] out = new float[(outer + inner) * 2];
		for (int i = 0; i < outer; i++) {
			double a = Math.PI * 2 * i / outer;
			out[i * 2] = (float) (Math.cos(a) * width);
			out[i * 2 + 1] = (float) (Math.sin(a) * width);
		}
		for (int i = 0; i < inner; i++) {
			double a = Math.PI * 2 * (i + .5) / inner;
			out[(outer + i) * 2] = (float) (Math.cos(a) * width * .55);
			out[(outer + i) * 2 + 1] = (float) (Math.sin(a) * width * .55);
		}
		return out;
	}
}
