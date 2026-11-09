package net.yahuclient.mod.config;

/** Where the lyrics show. */
public enum LyricsMode {
	/** Fixed in the sky like a cloud: turning the camera doesn't move them. */
	SKY("In the sky"),
	/** On the screen, high up, always in view; looking up and down moves them a little. */
	FOLLOW("Follow view");

	public final String label;

	LyricsMode(String label) {
		this.label = label;
	}

	public LyricsMode next() {
		return values()[(ordinal() + 1) % values().length];
	}
}
