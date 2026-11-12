package net.yahuclient.mod.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** The dark Yahu versions of Minecraft's widget sprites (see GuiGraphicsExtractorMixin). */
public final class ModernSprites {
	private ModernSprites() {
	}

	/** Draws the Yahu version of this sprite; false if it isn't one we replace. */
	public static boolean draw(GuiGraphicsExtractor g, Identifier sprite, int x, int y, int w, int h, int color) {
		if ("minecraft".equals(sprite.getNamespace()) && inventorySprite(g, sprite.getPath(), x, y)) {
			return true;
		}
		if (!"minecraft".equals(sprite.getNamespace()) || !sprite.getPath().startsWith("widget/")) {
			return false;
		}
		float alpha = (color >>> 24) / 255f;
		String name = sprite.getPath().substring("widget/".length());
		switch (name) {
			case "button", "button_highlighted", "button_disabled" ->
				BlockWidgets.button(g, x, y, w, h, !name.endsWith("disabled"), name.endsWith("highlighted"), alpha);
			case "text_field", "text_field_highlighted" -> {
				boolean focused = name.endsWith("highlighted");
				// a darker pane, its edge lights up while typing
				Glass.pane(g, x, y, w, h, 5, Glass.FILL_DARK, focused ? 0xB0FFFFFF : Glass.EDGE, alpha);
			}
			case "checkbox", "checkbox_highlighted", "checkbox_selected", "checkbox_selected_highlighted" -> {
				boolean on = name.contains("selected");
				boolean hot = name.endsWith("highlighted");
				if (on) {
					Draw.round(g, x, y, w, h, 4, Theme.alpha(hot ? 0xFFFFFFFF : 0xE6FFFFFF, alpha));
					check(g, x, y, w, h, Theme.alpha(0xFF1B1D26, alpha));
				} else {
					Glass.pane(g, x, y, w, h, 4, hot ? Glass.FILL_HOT : Glass.FILL, hot ? Glass.EDGE_HOT : Glass.EDGE, alpha);
				}
			}
			case "scroller" -> Draw.pill(g, x + w / 2f - 1.5f, y, 3, h, Theme.alpha(0x80FFFFFF, alpha));
			case "scroller_background" -> Draw.pill(g, x + w / 2f - 1.5f, y, 3, h, Theme.alpha(0x14FFFFFF, alpha));
			case "tab", "tab_highlighted", "tab_selected", "tab_selected_highlighted" -> {
				// the chosen tab is a frosted pill, the others only frost under the mouse
				boolean selected = name.startsWith("tab_selected");
				boolean hot = name.endsWith("highlighted");
				if (selected) {
					Glass.pane(g, x + 3, y + 3, w - 6, h - 6, 6, Glass.FILL_HOT, Glass.EDGE_HOT, alpha);
				} else if (hot) {
					Glass.pane(g, x + 3, y + 3, w - 6, h - 6, 6, Glass.FILL_DIM, Glass.EDGE, alpha);
				}
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	/** The hotbar and slot highlights in the inventory mod's look; false if the mod leaves this sprite alone. */
	private static boolean inventorySprite(GuiGraphicsExtractor g, String path, int x, int y) {
		switch (path) {
			case "hud/hotbar" -> {
				if (!net.yahuclient.mod.inventory.InventoryLook.hotbarOn()) {
					return false;
				}
				net.yahuclient.mod.inventory.InventoryLook.hotbar(g, x, y);
			}
			case "hud/hotbar_selection" -> {
				if (!net.yahuclient.mod.inventory.InventoryLook.hotbarOn()) {
					return false;
				}
				net.yahuclient.mod.inventory.InventoryLook.hotbarSelection(g, x, y);
			}
			case "hud/hotbar_offhand_left", "hud/hotbar_offhand_right" -> {
				if (!net.yahuclient.mod.inventory.InventoryLook.hotbarOn()) {
					return false;
				}
				net.yahuclient.mod.inventory.InventoryLook.offhand(g, x, y, path.endsWith("left"));
			}
			case "container/slot_highlight_back" -> {
				if (!net.yahuclient.mod.inventory.InventoryLook.containersOn()) {
					return false;
				}
				net.yahuclient.mod.inventory.InventoryLook.highlightBack(g, x, y);
			}
			case "container/slot_highlight_front" -> {
				if (!net.yahuclient.mod.inventory.InventoryLook.containersOn()) {
					return false;
				}
				net.yahuclient.mod.inventory.InventoryLook.highlightFront(g, x, y);
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	/** A check mark: two strokes, turned. */
	private static void check(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		float s = Math.min(w, h) / 20f;
		g.pose().pushMatrix();
		g.pose().translate(x + w * 0.42f, y + h * 0.66f);
		g.pose().rotate((float) Math.toRadians(-45));
		g.pose().scale(s, s);
		g.fill(-1, -6, 2, 1, color);
		g.fill(-1, -2, 11, 1, color);
		g.pose().popMatrix();
	}
}
