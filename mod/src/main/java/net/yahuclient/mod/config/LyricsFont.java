package net.yahuclient.mod.config;

import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/** The typefaces the lyrics can use; the TTF ones are bundled in assets/yahu/font (all under the SIL Open Font License). */
public enum LyricsFont {
	MINECRAFT("Minecraft", FontDescription.DEFAULT),
	UNBOUNDED("Unbounded", yahu("display")),
	INSTRUMENT("Instrument Sans", yahu("ui")),
	BEBAS("Bebas Neue", yahu("lyrics_bebas")),
	ARCHIVO("Archivo Black", yahu("lyrics_archivo")),
	RIGHTEOUS("Righteous", yahu("lyrics_righteous")),
	PACIFICO("Pacifico", yahu("lyrics_pacifico")),
	BUNGEE("Bungee", yahu("lyrics_bungee")),
	PIXEL("Silkscreen", yahu("pixel"));

	public final String label;
	public final FontDescription font;

	LyricsFont(String label, FontDescription font) {
		this.label = label;
		this.font = font;
	}

	private static FontDescription yahu(String name) {
		return new FontDescription.Resource(Identifier.fromNamespaceAndPath("yahu", name));
	}
}
