package net.yahuclient.mod.config;

/** The shapes the custom crosshair can take. */
public enum CrosshairShape {
	CROSS("Cross"),
	T("T"),
	DOT("Dot"),
	CIRCLE("Circle"),
	BOX("Box"),
	X("X");

	public final String label;

	CrosshairShape(String label) {
		this.label = label;
	}

	public CrosshairShape next() {
		CrosshairShape[] all = values();
		return all[(ordinal() + 1) % all.length];
	}
}
