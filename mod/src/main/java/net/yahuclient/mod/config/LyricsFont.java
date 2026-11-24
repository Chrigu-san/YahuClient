package net.yahuclient.mod.config;

import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/**
 * The typefaces the lyrics can use; the TTF ones are bundled in assets/yahu/font (all under the SIL Open Font License).
 *
 * Each TTF face comes in three sharpness tiers (rasterized at 4, 8 and 14 pixels per font pixel): text is drawn from the
 * tier closest to how big it really is on screen, so it is neither blurry from being blown up nor grainy from being
 * shrunk (font textures have no mipmaps).
 */
public enum LyricsFont {
	MINECRAFT("Minecraft", null),
	UNBOUNDED("Unbounded", "unbounded"),
	INSTRUMENT("Instrument Sans", "instrument"),
	BEBAS("Bebas Neue", "bebas"),
	ARCHIVO("Archivo Black", "archivo"),
	RIGHTEOUS("Righteous", "righteous"),
	PACIFICO("Pacifico", "pacifico"),
	BUNGEE("Bungee", "bungee"),
	PIXEL("Silkscreen", null);

	public final String label;
	private final FontDescription[] tiers;

	LyricsFont(String label, String base) {
		this.label = label;
		this.tiers = new FontDescription[Tiers.SCALES.length];
		for (int i = 0; i < Tiers.SCALES.length; i++) {
			if (base == null) {
				tiers[i] = "Silkscreen".equals(label) ? new FontDescription.Resource(Identifier.fromNamespaceAndPath("yahu", "pixel")) : FontDescription.DEFAULT;
			} else {
				tiers[i] = new FontDescription.Resource(Identifier.fromNamespaceAndPath("yahu", "lyrics_" + base + "_" + Tiers.SCALES[i]));
			}
		}
	}

	/** The face for text whose font pixel is this many screen pixels: the smallest tier at least that sharp. */
	public FontDescription forScale(float screenPixelsPerFontPixel) {
		for (int i = 0; i < Tiers.SCALES.length; i++) {
			if (Tiers.SCALES[i] >= screenPixelsPerFontPixel * 0.85f) {
				return tiers[i];
			}
		}
		return tiers[tiers.length - 1];
	}

	/** The sharpness tiers, in pixels per font pixel (a holder: an enum's constructor can't read its own static fields). */
	public static final class Tiers {
		public static final int[] SCALES = {4, 8, 14};

		private Tiers() {
		}
	}
}
