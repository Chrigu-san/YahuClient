package net.yahuclient.mod.inventory;

import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.yahuclient.mod.YahuMod;
import net.yahuclient.mod.config.InventoryStyle;
import net.yahuclient.mod.config.YahuConfig;
import net.yahuclient.mod.mixin.AbstractContainerScreenAccessor;
import net.yahuclient.mod.ui.Draw;
import net.yahuclient.mod.ui.Glass;
import net.yahuclient.mod.ui.Theme;

/**
 * The inventory and hotbar mod: slots, hotbar, slot highlights and the panels of the simple containers (the player's
 * inventory, chests, shulker boxes, dispensers, hoppers) in one of a few looks, all matching the Yahu menus.
 *
 * The hotbar and the highlights are Minecraft sprites, swapped in ModernSprites. The container panels are whole textures:
 * their blit is skipped (GuiGraphicsExtractorMixin) and a panel with a slot under every slot of the menu drawn instead, so
 * it fits any size of chest.
 */
public final class InventoryLook {
	/** Container textures that are nothing but a panel and slots, so they can be redrawn from the menu's slots. */
	private static final Set<String> SIMPLE = Set.of("textures/gui/container/inventory.png", "textures/gui/container/generic_54.png",
			"textures/gui/container/shulker_box.png", "textures/gui/container/dispenser.png", "textures/gui/container/hopper.png");

	private static boolean redrawnThisFrame;

	private InventoryLook() {
	}

	/** A screen starts drawing: no container redrawn yet in this frame. */
	public static void newFrame() {
		redrawnThisFrame = false;
	}

	private static YahuConfig.Inventory config() {
		return YahuMod.config().inventory;
	}

	public static boolean hotbarOn() {
		return config().enabled && config().hotbar;
	}

	public static boolean containersOn() {
		return config().enabled && config().containers;
	}

	// --- styles ---

	private static float radius(float size) {
		YahuConfig.Inventory c = config();
		float r = c.style == InventoryStyle.SOFT ? Math.max(c.roundness, 5) : c.roundness;
		return Math.min(r, size / 2f);
	}

	private static int fade(int color) {
		return Theme.alpha(color, Math.max(0.2f, Math.min(1.5f, config().opacity / 100f)));
	}

	/** One slot: the box an item sits in (x, y the box's corner, size its width). */
	public static void slot(GuiGraphicsExtractor g, float x, float y, float size) {
		YahuConfig.Inventory c = config();
		float r = radius(size);
		switch (c.style) {
			case GLASS -> Glass.pane(g, x, y, size, size, r, fade(0x7A1C2230), fade(0x2EFFFFFF), 1f);
			case MINIMAL -> Draw.round(g, x, y, size, size, r, fade(0x46000000));
			case NEON -> {
				Draw.round(g, x, y, size, size, r, fade(0xB00A0B12));
				Draw.outline(g, x, y, size, size, r, fade(Theme.alpha(c.accent, 0.35f)));
			}
			case SOFT -> Draw.round(g, x, y, size, size, r, fade(0x3CFFFFFF));
		}
	}

	/** The selection around a slot box (the hotbar's chosen slot). */
	public static void selection(GuiGraphicsExtractor g, float x, float y, float size) {
		YahuConfig.Inventory c = config();
		float r = radius(size) + 1;
		float o = 1;
		switch (c.style) {
			case GLASS -> {
				Glass.pane(g, x - o, y - o, size + 2 * o, size + 2 * o, r, 0x24FFFFFF, 0xD0FFFFFF, 1f);
				Draw.pill(g, x + size * 0.25f, y + size - 1.5f, size * 0.5f, 1.5f, c.accent | 0xFF000000);
			}
			case MINIMAL -> Draw.outline(g, x - o, y - o, size + 2 * o, size + 2 * o, r, 0xE6FFFFFF);
			case NEON -> {
				Draw.glow(g, x + size / 2f, y + size / 2f, size * 1.1f, Theme.alpha(c.accent, 0.45f));
				Draw.round(g, x - o, y - o, size + 2 * o, size + 2 * o, r, Theme.alpha(c.accent, 0.22f));
				Draw.outline(g, x - o, y - o, size + 2 * o, size + 2 * o, r, c.accent | 0xFF000000);
			}
			case SOFT -> {
				Draw.round(g, x - o, y - o, size + 2 * o, size + 2 * o, r, 0x50FFFFFF);
				Draw.outline(g, x - o, y - o, size + 2 * o, size + 2 * o, r, 0x90FFFFFF);
			}
		}
	}

	/** A container's panel. */
	public static void panel(GuiGraphicsExtractor g, float x, float y, float w, float h) {
		YahuConfig.Inventory c = config();
		switch (c.style) {
			case GLASS -> Glass.panel(g, x, y, w, h, 6, 1f);
			case MINIMAL -> Draw.round(g, x, y, w, h, 4, fade(0xD80C0E13));
			case NEON -> {
				Draw.shadow(g, x, y, w, h, 6, 0.8f);
				Draw.round(g, x, y, w, h, 6, fade(0xE6090A10));
				Draw.outline(g, x, y, w, h, 6, Theme.alpha(c.accent, 0.6f), c.accent | 0xFF000000);
			}
			case SOFT -> {
				Draw.shadow(g, x, y, w, h, 10, 0.6f);
				Draw.round(g, x, y, w, h, 10, fade(0xD8232A38));
				Draw.outline(g, x, y, w, h, 10, 0x22FFFFFF, 0x40FFFFFF);
			}
		}
	}

	/** The highlight under the slot the mouse is over (sprite at x, y, 24 by 24 around the 16 by 16 item). */
	public static void highlightBack(GuiGraphicsExtractor g, int x, int y) {
		YahuConfig.Inventory c = config();
		int fill = c.style == InventoryStyle.NEON ? Theme.alpha(c.accent, 0.3f) : 0x30FFFFFF;
		Draw.round(g, x + 3, y + 3, 18, 18, radius(18), fill);
	}

	/** The highlight over the slot the mouse is over: a bright edge. */
	public static void highlightFront(GuiGraphicsExtractor g, int x, int y) {
		YahuConfig.Inventory c = config();
		int edge = c.style == InventoryStyle.NEON ? c.accent | 0xFF000000 : 0xC0FFFFFF;
		Draw.outline(g, x + 3, y + 3, 18, 18, radius(18), edge);
	}

	// --- the hotbar (sprites, see ModernSprites) ---

	/** hud/hotbar: 182 by 22, nine slots 20 apart; items sit at x + 3 + 20 i, y + 3. */
	public static void hotbar(GuiGraphicsExtractor g, int x, int y) {
		for (int i = 0; i < 9; i++) {
			slot(g, x + 2 + i * 20, y + 2, 18);
		}
	}

	/** hud/hotbar_selection: 24 by 23 at the chosen slot, one pixel up and left of it. */
	public static void hotbarSelection(GuiGraphicsExtractor g, int x, int y) {
		selection(g, x + 3, y + 3, 18);
	}

	/** hud/hotbar_offhand_left / right: 29 by 24, the slot on the far or near side. */
	public static void offhand(GuiGraphicsExtractor g, int x, int y, boolean left) {
		slot(g, left ? x + 2 : x + 9, y + 3, 18);
	}

	// --- containers ---

	/** Whether this texture is a simple container panel to redraw. */
	public static boolean simpleContainer(Identifier texture) {
		return "minecraft".equals(texture.getNamespace()) && SIMPLE.contains(texture.getPath());
	}

	/**
	 * Draws the panel of the open container screen in place of its texture: once per frame, on the blit at the top of
	 * the screen (a chest's texture comes in two blits, the second is only skipped).
	 */
	public static boolean replaceContainerTexture(GuiGraphicsExtractor g, Identifier texture, int x, int y) {
		if (!containersOn() || !simpleContainer(texture)) {
			return false;
		}
		if (!(Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> screen)) {
			return false;
		}
		var access = (AbstractContainerScreenAccessor) screen;
		int left = access.yahu$leftPos(), top = access.yahu$topPos();
		if (x != left || y != top) {
			return true;  // the lower half of a chest: already drawn
		}
		redrawnThisFrame = true;
		panel(g, left, top, access.yahu$imageWidth(), access.yahu$imageHeight());
		for (Slot slot : screen.getMenu().slots) {
			if (slot.isActive()) {
				slot(g, left + slot.x - 1, top + slot.y - 1, 18);
			}
		}
		if (screen instanceof InventoryScreen) {
			// the player's frame and the crafting arrow, which were part of the texture
			Glass.pane(g, left + 26, top + 8, 49, 70, radius(18) + 1, 0x8C0C0E14, 0x24FFFFFF, 1f);
			arrow(g, left + 135, top + 29);
		}
		return true;
	}

	/** A small arrow pointing right, from the crafting grid to its result. */
	private static void arrow(GuiGraphicsExtractor g, float x, float y) {
		int color = 0x90FFFFFF;
		Draw.pill(g, x, y + 5, 11, 2, color);
		g.pose().pushMatrix();
		g.pose().translate(x + 12, y + 6);
		g.pose().rotate((float) Math.toRadians(45));
		Draw.pill(g, -5, -1, 6, 2, color);
		g.pose().popMatrix();
		g.pose().pushMatrix();
		g.pose().translate(x + 12, y + 6);
		g.pose().rotate((float) Math.toRadians(-45));
		Draw.pill(g, -5, -1, 6, 2, color);
		g.pose().popMatrix();
	}

	/** The color for container labels: light on our dark panels, Minecraft's dark grey on its own textures. */
	public static int labelColor(int original) {
		return redrawnThisFrame ? 0xFFE4E7EE : original;
	}
}
