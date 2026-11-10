package net.yahuclient.mod.ui;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * One of our own mods as the Mods tab shows it: name, a line about it, its block color, on/off, its settings and, if it has
 * one, a live preview drawn above the settings.
 */
public record ModEntry(String name, String description, int color, Supplier<Boolean> enabled, Consumer<Boolean> setEnabled,
		List<Control> controls, Preview preview, Supplier<String> status) {
	/** Draws a small live picture of the mod into the given box. */
	public interface Preview {
		void extract(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h);
	}
}
