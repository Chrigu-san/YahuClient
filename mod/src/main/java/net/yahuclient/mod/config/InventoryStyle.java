package net.yahuclient.mod.config;

/** The looks of the inventory and hotbar mod. */
public enum InventoryStyle {
	GLASS("Glass"),
	MINIMAL("Minimal"),
	NEON("Neon"),
	SOFT("Soft");

	public final String label;

	InventoryStyle(String label) {
		this.label = label;
	}
}
