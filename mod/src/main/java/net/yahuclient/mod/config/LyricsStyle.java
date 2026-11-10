package net.yahuclient.mod.config;

/** How the lyrics appear. */
public enum LyricsStyle {
	/** The whole line, the sung part coloring in letter by letter. */
	LINE("Line"),
	/** Each word pops up when it is sung. */
	WORDS("Word by word"),
	/** Words pop up stacked like a poster: some big, some small and tilted, building the line up row by row. */
	KINETIC("Kinetic");

	public final String label;

	LyricsStyle(String label) {
		this.label = label;
	}
}
