package net.yahuclient.mod.config;

import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/** The typefaces the lyrics can use; the TTF ones are bundled in assets/yahu/font (all under the SIL Open Font License). */
public enum LyricsFont {
	MINECRAFT("Minecraft", FontDescription.DEFAULT),
	UNBOUNDED("Unbounded", new FontDescription.Resource(Identifier.fromNamespaceAndPath("yahu", "display"))),
	INSTRUMENT("Instrument Sans", new FontDescription.Resource(Identifier.fromNamespaceAndPath("yahu", "ui"))),
	PIXEL("Silkscreen", new FontDescription.Resource(Identifier.fromNamespaceAndPath("yahu", "pixel")));

	public final String label;
	public final FontDescription font;

	LyricsFont(String label, FontDescription font) {
		this.label = label;
		this.font = font;
	}
}
