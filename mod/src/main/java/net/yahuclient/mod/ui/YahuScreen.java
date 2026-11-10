package net.yahuclient.mod.ui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The Yahu menu: our own mods and their settings, over the game or the title screen. Dark, with a slowly drifting light in
 * the background. On the left the mods with their switches, on the right the chosen one: its name, a live preview and the
 * settings in grouped cards, scrolling smoothly when they don't fit.
 *
 * Movement is kept short and quiet: the menu fades and settles in, the selection marker glides, the page crossfades when
 * another mod is picked.
 */
public class YahuScreen extends Screen {
	private static final int MAX_W = 560;
	private static final int MAX_H = 340;
	private static final int NAV_W = 168;
	private static final int ITEM_H = 46;
	private static final int PREVIEW_H = 70;

	private final Screen parent;
	private final List<ModEntry> mods;
	private final Runnable save;
	private final int closeKey;
	private final boolean inWorld;

	private int selected;
	private Control active;
	private long lastFrame = System.nanoTime();
	private float open;              // 0 -> 1 when the menu appears
	private float page = 1;          // 0 -> 1 when another mod is picked
	private float marker = -1;       // the selection marker's position, in items
	private float[] navHover;
	private float[] navOn;
	private float scroll, scrollTarget;
	private int contentHeight;

	public YahuScreen(Screen parent, List<ModEntry> mods, Runnable save, int closeKey, boolean inWorld) {
		super(Component.literal("Yahu Client"));
		this.parent = parent;
		this.mods = mods;
		this.save = save;
		this.closeKey = closeKey;
		this.inWorld = inWorld;
		this.navHover = new float[mods.size()];
		this.navOn = new float[mods.size()];
		for (int i = 0; i < mods.size(); i++) {
			navOn[i] = mods.get(i).enabled().get() ? 1 : 0;
		}
	}

	/** Shows this mod's page, as a click on it would. */
	public void select(int index) {
		int i = Math.max(0, Math.min(mods.size() - 1, index));
		if (i != selected) {
			selected = i;
			page = 0;
			scroll = scrollTarget = 0;
		}
	}

	private int panelW() {
		return Math.min(MAX_W, width - 16);
	}

	private int panelH() {
		return Math.min(MAX_H, height - 16);
	}

	private int left() {
		return (width - panelW()) / 2;
	}

	private int top() {
		return (height - panelH()) / 2;
	}

	private int contentX() {
		return left() + NAV_W + 18;
	}

	private int contentW() {
		return panelW() - NAV_W - 32;
	}

	private int contentTop() {
		return top() + 14;
	}

	private int contentBottom() {
		return top() + panelH() - 10;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		if (inWorld) {
			Background.veil(g, 0, 0, width, height);
		} else {
			Background.draw(g, 0, 0, width, height);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;
		open = Theme.approach(open, 1, dt, 12);
		page = Theme.approach(page, 1, dt, 14);

		super.extractRenderState(g, mouseX, mouseY, partialTick);

		float appear = Theme.easeOutCubic(open);
		int px = left(), pw = panelW(), ph = panelH();
		float py = top() + (1 - appear) * 10;

		g.pose().pushMatrix();
		g.pose().translate(0, py - top());

		Draw.shadow(g, px, top(), pw, ph, 14, appear);
		Draw.card(g, px, top(), pw, ph, 14, Theme.alpha(Theme.PANEL, appear), Theme.alpha(Theme.BORDER, appear));
		// the left column, a shade darker, with a hairline to the content
		Draw.round(g, px + 1, top() + 1, NAV_W, ph - 2, 13, Theme.alpha(0xFF0D0E13, appear));
		g.fill(px + NAV_W + 1, top() + 12, px + NAV_W + 2, top() + ph - 12, Theme.alpha(Theme.BORDER, appear));

		extractNav(g, mouseX, mouseY - Math.round(py - top()), dt, appear);
		if (selected < mods.size()) {
			extractPage(g, mods.get(selected), mouseX, mouseY - Math.round(py - top()), dt, appear);
		}
		g.pose().popMatrix();
	}

	private void extractNav(GuiGraphicsExtractor g, int mouseX, int mouseY, float dt, float appear) {
		int x = left() + 12, y = top() + 14;
		// the word mark: YAHU in the heading font, a small MODS tag after it
		var mark = Theme.display("YAHU");
		g.text(font, mark, x, y, Theme.alpha(Theme.TEXT, appear), false);
		int tagX = x + font.width(mark) + 6;
		var tag = Theme.ui("MODS");
		Draw.pill(g, tagX, y - 2, font.width(tag) + 10, 12, Theme.alpha(Theme.mix(Theme.CARD_HI, Theme.ACCENT, 0.35f), appear));
		g.text(font, tag, tagX + 5, y, Theme.alpha(Theme.ACCENT_HI, appear), false);

		int listY = top() + 40;
		marker = marker < 0 ? selected : Theme.approach(marker, selected, dt, 16);
		// the selection: a card that glides to the chosen mod, with an accent bar on its left edge
		float my = listY + marker * (ITEM_H + 4);
		Draw.round(g, x - 4, my, NAV_W - 16, ITEM_H, 9, Theme.alpha(Theme.CARD_HI, appear));
		Draw.pill(g, x - 4, my + 12, 3, ITEM_H - 24, Theme.alpha(Theme.ACCENT, appear));

		for (int i = 0; i < mods.size(); i++) {
			ModEntry mod = mods.get(i);
			int iy = listY + i * (ITEM_H + 4);
			boolean hot = inside(mouseX, mouseY, x - 4, iy, NAV_W - 16, ITEM_H);
			navHover[i] = Theme.approach(navHover[i], hot && i != selected ? 1 : 0, dt, 18);
			navOn[i] = Theme.approach(navOn[i], mod.enabled().get() ? 1 : 0, dt, 16);
			if (navHover[i] > 0.01f) {
				Draw.round(g, x - 4, iy, NAV_W - 16, ITEM_H, 9, Theme.alpha(0xFF15171D, navHover[i] * appear));
			}
			// the icon: a rounded square in the mod's color, a letter on it
			float iconAlpha = 0.25f + 0.75f * Math.max(navOn[i], i == selected ? 0.6f : 0);
			Draw.round(g, x + 4, iy + 11, 24, 24, 7, Theme.alpha(Theme.mix(0xFF20232C, mod.color(), 0.85f * iconAlpha), appear));
			var icon = Theme.display(mod.icon());
			g.text(font, icon, x + 16 - font.width(icon) / 2, iy + 19, Theme.alpha(0xFFFFFFFF, appear), false);
			g.text(font, Theme.ui(mod.name()), x + 36, iy + 12, Theme.alpha(i == selected ? Theme.TEXT : Theme.mix(Theme.MUTED, Theme.TEXT, navHover[i]), appear), false);
			var sub = Theme.ui(mod.enabled().get() ? mod.tagline() : "Off");
			g.pose().pushMatrix();
			g.pose().translate(x + 36, iy + 24);
			g.pose().scale(0.85f, 0.85f);
			g.text(font, sub, 0, 0, Theme.alpha(mod.enabled().get() ? Theme.FAINT : Theme.mix(Theme.FAINT, 0xFF40444F, 0.5f), appear), false);
			g.pose().popMatrix();
			Control.switchAt(g, x + NAV_W - 50, iy + 16, navOn[i], hot ? 1 : 0);
		}

		var hint = Theme.ui("Right Shift / Esc to close");
		g.pose().pushMatrix();
		g.pose().translate(x, top() + panelH() - 20);
		g.pose().scale(0.8f, 0.8f);
		g.text(font, hint, 0, 0, Theme.alpha(Theme.FAINT, appear), false);
		g.pose().popMatrix();
	}

	private void extractPage(GuiGraphicsExtractor g, ModEntry mod, int mouseX, int mouseY, float dt, float appear) {
		float fade = Theme.easeOutCubic(page) * appear;
		int cx = contentX(), cw = contentW(), top = contentTop(), bottom = contentBottom();
		scroll = Theme.approach(scroll, scrollTarget, dt, 14);
		int offset = Math.round(scroll);
		float slide = (1 - Theme.easeOutCubic(page)) * 8;

		g.enableScissor(cx - 4, top - 2, cx + cw + 6, bottom);
		g.pose().pushMatrix();
		g.pose().translate(slide, 0);
		int y = top - offset;

		// title and status
		g.pose().pushMatrix();
		g.pose().translate(cx, y + 2);
		g.pose().scale(1.5f, 1.5f);
		g.text(font, Theme.display(mod.name()), 0, 0, Theme.alpha(Theme.TEXT, fade), false);
		g.pose().popMatrix();
		boolean on = mod.enabled().get();
		var chip = Theme.ui(on ? "Active" : "Off");
		int chipW = font.width(chip) + 16;
		int chipX = cx + cw - chipW;
		Draw.pill(g, chipX, y + 2, chipW, 14, Theme.alpha(Theme.mix(0xFF20232C, on ? Theme.GOOD : 0xFF20232C, on ? 0.22f : 0), fade));
		Draw.circle(g, chipX + 7, y + 9, 2.2f, Theme.alpha(on ? Theme.GOOD : Theme.FAINT, fade));
		g.text(font, chip, chipX + 12, y + 5, Theme.alpha(on ? Theme.GOOD : Theme.MUTED, fade), false);
		y += 20;
		var lines = font.split(Theme.ui(mod.description()), cw);
		for (var line : lines) {
			g.text(font, line, cx, y, Theme.alpha(Theme.MUTED, fade), false);
			y += 10;
		}
		if (mod.status() != null) {
			String status = mod.status().get();
			if (status != null && !status.isEmpty()) {
				g.text(font, Theme.ui(status), cx, y + 2, Theme.alpha(Theme.ACCENT_HI, fade), false);
				y += 12;
			}
		}
		y += 8;

		if (mod.preview() != null) {
			Draw.card(g, cx, y, cw, PREVIEW_H, 10, Theme.alpha(Theme.FIELD, fade), Theme.alpha(Theme.BORDER, fade));
			g.enableScissor(cx + 1, Math.max(y + 1, top), cx + cw - 1, Math.min(y + PREVIEW_H - 1, bottom));
			mod.preview().extract(g, font, cx + 1, y + 1, cw - 2, PREVIEW_H - 2);
			g.disableScissor();
			y += PREVIEW_H + 10;
		}

		// the settings: a card per group, the group's title above it
		List<List<Control>> groups = new ArrayList<>();
		List<Control> group = null;
		for (Control c : mod.controls()) {
			if (c.isSection() || group == null) {
				group = new ArrayList<>();
				groups.add(group);
			}
			group.add(c);
		}
		for (List<Control> gr : groups) {
			int start = 0;
			if (gr.get(0).isSection()) {
				gr.get(0).place(cx + 2, y, cw);
				gr.get(0).extract(g, font, mouseX, mouseY, dt);
				y += gr.get(0).height();
				start = 1;
			}
			int h = 0;
			for (int i = start; i < gr.size(); i++) {
				h += gr.get(i).height();
			}
			if (h > 0) {
				Draw.card(g, cx, y, cw, h + 8, 9, Theme.alpha(Theme.CARD, fade), Theme.alpha(Theme.BORDER, fade));
				int ry = y + 4;
				for (int i = start; i < gr.size(); i++) {
					Control c = gr.get(i);
					c.place(cx + 10, ry, cw - 20);
					c.extract(g, font, mouseX - Math.round(slide), mouseY, dt);
					ry += c.height();
					if (i < gr.size() - 1 && !gr.get(i + 1).isSection()) {
						g.fill(cx + 10, ry, cx + cw - 10, ry + 1, Theme.alpha(0x0CFFFFFF, fade));
					}
				}
				y += h + 8 + 8;
			}
		}
		contentHeight = y + offset - top;
		g.pose().popMatrix();
		g.disableScissor();

		// soft edges where content scrolls out of view
		int max = Math.max(0, contentHeight - (bottom - top));
		if (max > 0) {
			if (scroll > 1) {
				g.fillGradient(cx - 4, top - 2, cx + cw + 6, top + 12, Theme.PANEL | 0xFF000000, 0x00111218);
			}
			if (scroll < max - 1) {
				g.fillGradient(cx - 4, bottom - 14, cx + cw + 6, bottom, 0x00111218, Theme.PANEL | 0xFF000000);
			}
			float track = bottom - top;
			float bar = Math.max(20, track * track / Math.max(1, contentHeight));
			float by = top + (track - bar) * (scroll / max);
			Draw.pill(g, left() + panelW() - 7, by, 3, bar, Theme.alpha(0xFFFFFFFF, 0.18f));
		}
		scrollTarget = Math.max(0, Math.min(scrollTarget, max));
	}

	private static boolean inside(double mx, double my, double x, double y, double w, double h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX >= contentX() - 8) {
			scrollTarget = Math.max(0, Math.min(Math.max(0, contentHeight - (contentBottom() - contentTop())), scrollTarget - (float) scrollY * 28));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x(), my = event.y();
		int x = left() + 12, listY = top() + 40;
		for (int i = 0; i < mods.size(); i++) {
			int iy = listY + i * (ITEM_H + 4);
			if (inside(mx, my, x - 4, iy, NAV_W - 16, ITEM_H)) {
				ModEntry mod = mods.get(i);
				if (mx >= x + NAV_W - 54) {
					mod.setEnabled().accept(!mod.enabled().get());
					save.run();
				}
				select(i);
				return true;
			}
		}
		if (selected < mods.size() && my >= contentTop() && my < contentBottom()) {
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
			active.drag(event.x(), event.y());
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
