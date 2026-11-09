package net.yahuclient.mod.ui;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The Yahu menu inside the game, opened with Right Shift or the Yahu button in the pause menu. It looks like the launcher:
 * a block panel with a header, a tab bar (Mods for now), the list of our mods on the left with on/off switches and the
 * chosen mod's settings and live preview on the right.
 *
 * The panel shrinks to the window (Minecraft's default window is only 240 GUI pixels high), and the settings scroll with the
 * mouse wheel when they don't fit.
 */
public class YahuScreen extends Screen {
	private static final int MAX_W = 420;
	private static final int MAX_H = 270;
	private static final int HEADER_H = 22;
	private static final int TAB_H = 16;
	private static final int LIST_W = 130;
	private static final int CARD_H = 30;
	private static final int PREVIEW_H = 48;

	private final Screen parent;
	private final List<ModEntry> mods;
	private final Runnable save;
	private final int closeKey;
	private int selected;
	private int scroll;      // how far the settings column is scrolled down
	private int contentH;    // height of the settings column, from the last frame
	private Control active;  // the control being dragged

	public YahuScreen(Screen parent, List<ModEntry> mods, Runnable save, int closeKey) {
		super(Component.literal("Yahu Client"));
		this.parent = parent;
		this.mods = mods;
		this.save = save;
		this.closeKey = closeKey;
	}

	/** Shows this mod's page, as a click on its card would. */
	public void select(int index) {
		selected = Math.max(0, Math.min(mods.size() - 1, index));
		scroll = 0;
	}

	private int panelW() {
		return Math.min(MAX_W, width - 8);
	}

	private int panelH() {
		return Math.min(MAX_H, height - 8);
	}

	private int left() {
		return (width - panelW()) / 2;
	}

	private int top() {
		return (height - panelH()) / 2;
	}

	private int bodyTop() {
		return top() + HEADER_H + TAB_H + 6;
	}

	private int bodyBottom() {
		return top() + panelH() - 6;
	}

	private int pageX() {
		return left() + 10 + LIST_W + 10;
	}

	private int pageW() {
		return panelW() - LIST_W - 30;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		// the game stays visible behind a night blue veil, like the launcher's dimmed sheets
		g.fill(0, 0, width, height, YahuUi.withAlpha(YahuUi.INK, 0.72f));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		int x = left(), y = top(), pw = panelW(), ph = panelH();

		// the panel with the header
		YahuUi.block(g, x, y, pw, ph, YahuUi.SURFACE);
		g.fill(x, y, x + pw, y + 3, YahuUi.GRASS);
		YahuUi.block(g, x + 10, y + 7, 10, 10, YahuUi.GRASS);
		g.fill(x + 12, y + 9, x + 18, y + 11, YahuUi.GRASS_HI);
		g.text(font, "YAHU CLIENT", x + 26, y + 8, YahuUi.TEXT, true);
		String hint = "Right Shift to close";
		g.text(font, hint, x + pw - 10 - font.width(hint), y + 8, YahuUi.FAINT, false);

		// tab bar: Mods is the only tab for now, so it is always the active one
		int tabY = y + HEADER_H;
		g.fill(x + 6, tabY + TAB_H, x + pw - 6, tabY + TAB_H + 1, YahuUi.LINE);
		int tabW = font.width("Mods") + 20;
		YahuUi.block(g, x + 10, tabY, tabW, TAB_H, YahuUi.GRASS);
		g.text(font, "Mods", x + 20, tabY + 4, YahuUi.INK, false);

		// the mods
		int listX = x + 10, listY = bodyTop();
		for (int i = 0; i < mods.size(); i++) {
			ModEntry mod = mods.get(i);
			int cy = listY + i * (CARD_H + 4);
			boolean hot = mouseX >= listX && mouseX < listX + LIST_W && mouseY >= cy && mouseY < cy + CARD_H;
			YahuUi.block(g, listX, cy, LIST_W, CARD_H, i == selected || hot ? YahuUi.RAISED_HI : YahuUi.RAISED);
			g.fill(listX, cy, listX + 3, cy + CARD_H, i == selected ? mod.color() : YahuUi.withAlpha(mod.color(), 0.5f));
			g.text(font, mod.name(), listX + 9, cy + 6, YahuUi.TEXT, false);
			boolean on = mod.enabled().get();
			g.text(font, on ? "On" : "Off", listX + 9, cy + 17, on ? YahuUi.GRASS : YahuUi.FAINT, false);
			int sx = listX + LIST_W - 34, sy = cy + 9;
			YahuUi.sunk(g, sx, sy, 26, 12, on ? YahuUi.mix(YahuUi.INK, YahuUi.GRASS, 0.35f) : YahuUi.INK);
			YahuUi.block(g, on ? sx + 14 : sx + 2, sy + 2, 10, 8, on ? YahuUi.GRASS : YahuUi.LINE_HI);
		}

		// the chosen mod's page, scrolling inside the body when it is taller than the window allows
		if (selected < mods.size()) {
			ModEntry mod = mods.get(selected);
			int px = pageX(), pw2 = pageW(), top = bodyTop(), bottom = bodyBottom();
			int maxScroll = Math.max(0, contentH - (bottom - top));
			scroll = Math.max(0, Math.min(scroll, maxScroll));
			g.enableScissor(px - 2, top - 2, px + pw2 + 2, bottom);
			int cy = top - scroll;
			g.text(font, mod.name(), px, cy, mod.color(), true);
			cy += 11;
			g.text(font, font.plainSubstrByWidth(mod.description(), pw2), px, cy, YahuUi.MUTED, false);
			cy += 11;
			if (mod.status() != null) {
				String status = mod.status().get();
				if (status != null && !status.isEmpty()) {
					g.text(font, font.plainSubstrByWidth(status, pw2), px, cy, YahuUi.FAINT, false);
					cy += 11;
				}
			}
			cy += 2;
			if (mod.preview() != null) {
				YahuUi.sunk(g, px, cy, pw2, PREVIEW_H, YahuUi.INK);
				g.enableScissor(Math.max(px + 1, px), Math.max(cy + 1, top), px + pw2 - 1, Math.min(cy + PREVIEW_H - 1, bottom));
				mod.preview().extract(g, font, px + 1, cy + 1, pw2 - 2, PREVIEW_H - 2);
				g.disableScissor();
				cy += PREVIEW_H + 4;
			}
			for (Control control : mod.controls()) {
				control.place(px, cy, pw2);
				control.extract(g, font, mouseX, mouseY);
				cy += control.height();
			}
			g.disableScissor();
			contentH = cy + scroll - top;

			// a thin scroll bar on the right edge when there is more below or above
			if (maxScroll > 0) {
				int trackH = bottom - top;
				int barH = Math.max(12, trackH * trackH / Math.max(1, contentH));
				int barY = top + (trackH - barH) * scroll / maxScroll;
				g.fill(x + pw - 6, top, x + pw - 4, bottom, YahuUi.withAlpha(YahuUi.LINE, 0.8f));
				g.fill(x + pw - 6, barY, x + pw - 4, barY + barH, YahuUi.LINE_HI);
			}
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX >= pageX() && mouseX < left() + panelW()) {
			scroll -= (int) Math.round(scrollY * 16);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x(), my = event.y();
		int listX = left() + 10, listY = bodyTop();
		for (int i = 0; i < mods.size(); i++) {
			int cy = listY + i * (CARD_H + 4);
			if (mx >= listX && mx < listX + LIST_W && my >= cy && my < cy + CARD_H) {
				ModEntry mod = mods.get(i);
				// the switch toggles, the rest of the card opens the settings
				if (mx >= listX + LIST_W - 36) {
					mod.setEnabled().accept(!mod.enabled().get());
					save.run();
				}
				if (i != selected) {
					select(i);
				}
				return true;
			}
		}
		// controls scrolled out of the visible body can't be clicked
		if (selected < mods.size() && my >= bodyTop() && my < bodyBottom()) {
			for (Control control : mods.get(selected).controls()) {
				if (control.click(mx, my)) {
					active = control;
					save.run();
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (active != null) {
			active.drag(event.x());
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (active != null) {
			active.release();
			active = null;
			save.run();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == closeKey) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		save.run();
		minecraft.setScreenAndShow(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
