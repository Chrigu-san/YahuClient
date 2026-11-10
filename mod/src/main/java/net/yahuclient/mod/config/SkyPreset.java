package net.yahuclient.mod.config;

/** The skies that come with the mod, drawn by the mod itself (see SkyboxGenerator), and a picture of your own. */
public enum SkyPreset {
	BLACK_HOLE("Black hole"),
	CLEAR_DAY("Clear day"),
	GOLDEN_HOUR("Golden hour"),
	AURORA("Aurora night"),
	NEBULA("Nebula"),
	CUSTOM("Your own");

	public final String label;

	SkyPreset(String label) {
		this.label = label;
	}
}
