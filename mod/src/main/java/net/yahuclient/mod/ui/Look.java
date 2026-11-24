package net.yahuclient.mod.ui;

import net.minecraft.client.Minecraft;
import net.yahuclient.mod.YahuMod;

/**
 * Whether Minecraft's own menus get the Yahu look (the Menu card in the Yahu menu). Off: every menu is Minecraft's own
 * again (buttons, backgrounds, fonts, the main menu's layout and logo, the panorama); only the Yahu menu keeps its look,
 * and the Yahu Mods button stays so it can still be opened.
 */
public final class Look {
	private Look() {
	}

	public static boolean on() {
		return YahuMod.config().menu.glass;
	}

	/** The Yahu menu is open: it keeps its own look either way. */
	public static boolean yahuScreen() {
		return Minecraft.getInstance().gui.screen() instanceof YahuScreen;
	}
}
