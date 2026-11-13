package net.yahuclient.mod.ui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The Yahu menu, laid out like the big PvP clients: first every mod as a card in a grid (its icon, name, what it does, and
 * a bar along the bottom that switches it on and off); a click on a card opens that mod's settings, with an arrow (or Esc)
 * back to the grid. Both sit in one floating glass panel over the blurred background.
 *
 * Movement is short and quiet: the panel settles in, cards brighten under the mouse, the two views crossfade.
 */
public class YahuScreen extends Screen {
	private static final int MAX_PANEL_W = 540;
	private static final int CARD_MIN_W = 84;
	private static final int CARD_H = 92;
	private static final int GAP = 8;
	private static final int PREVIEW_H = 70;
	private static final int HEADER = 40;

	private final Screen parent;
	private final List<ModEntry> mods;
	private final Runnable save;
	private final boolean inWorld;

	private int selected = -1;       // -1: the grid; otherwise the mod whose settings are open
	private Control active;
	private long lastFrame = System.nanoTime();
	private float open;              // 0 -> 1 when the menu appears
	private float page = 1;          // 0 -> 1 when the view changes
	private final float[] cardHover;
	private final float[] cardOn;
	private float backHover;
	private float scroll, scrollTarget;
	private int contentHeight;

	public YahuScreen(Screen parent, List<ModEntry> mods, Runnable save, boolean inWorld) {
		super(Component.literal("Yahu Client"));
		this.parent = parent;
		this.mods = mods;
		this.save = save;
		this.inWorld = inWorld;
		this.cardHover = new float[mods.size()];
		this.cardOn = new float[mods.size()];
		for (int i = 0; i < mods.size(); i++) {
			cardOn[i] = mods.get(i).enabled().get() ? 1 : 0;
		}
	}

	/** Opens this mod's settings, as a click on its card would; -1 goes back to the grid. */
	public void select(int index) {
		int i = index < 0 ? -1 : Math.min(mods.size() - 1, index);
		if (i != selected) {
			selected = i;
			page = 0;
			scroll = scrollTarget = 0;
		}
	}

	// --- geometry: one panel in the middle ---

	private int panelW() {
		return Math.min(MAX_PANEL_W, width - 24);
	}

	private int panelX() {
		return (width - panelW()) / 2;
	}

	private int panelY() {
		return 12;
	}

	private int panelH() {
		return height - 24;
	}

	private int innerX() {
		return panelX() + 16;
	}

	private int innerW() {
		return panelW() - 32;
	}

	private int contentTop() {
		return panelY() + HEADER;
	}

	private int contentBottom() {
		return panelY() + panelH() - 8;
	}

	private int columns() {
		// as many columns as fit, but never more than there are mods
		return Math.max(1, Math.min(mods.size(), (innerW() + GAP) / (CARD_MIN_W + GAP)));
	}

	private int cardW() {
		int cols = columns();
		return (innerW() - GAP * (cols - 1)) / cols;
	}

	private int cardX(int i) {
		return innerX() + (i % columns()) * (cardW() + GAP);
	}

	private int cardY(int i) {
		return contentTop() + 4 + (i / columns()) * (CARD_H + GAP) - Math.round(scroll);
	}

	private static final int BACK = 20;

	private int backX() {
		return panelX() + 12;
	}

	private int backY() {
		return panelY() + 11;
	}

	private int headerSwitchX() {
		return panelX() + panelW() - 16 - 22;
	}

	// the background is Minecraft's own: the blurred game or panorama, with the Yahu tint (ScreenMixin)

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;
		open = Theme.approach(open, 1, dt, 12);
		page = Theme.approach(page, 1, dt, 14);

		super.extractRenderState(g, mouseX, mouseY, partialTick);

		float appear = Theme.easeOutCubic(open);
		float settle = (1 - appear) * 10;
		g.pose().pushMatrix();
		g.pose().translate(0, settle);
		int my = Math.round(mouseY - settle);
		Glass.panel(g, panelX(), panelY(), panelW(), panelH(), 8, appear);
		for (int i = 0; i < mods.size(); i++) {
			cardOn[i] = Theme.approach(cardOn[i], mods.get(i).enabled().get() ? 1 : 0, dt, 16);
		}
		float fade = Theme.easeOutCubic(page) * appear;
		if (selected < 0) {
			extractGrid(g, mouseX, my, dt, appear, fade);
		} else {
			extractDetail(g, mods.get(selected), mouseX, my, dt, appear, fade);
		}
		g.pose().popMatrix();
	}

	// --- the grid ---

	private void extractGrid(GuiGraphicsExtractor g, int mouseX, int mouseY, float dt, float appear, float fade) {
		// header: the word mark, the number of mods on the right
		int hx = innerX(), hy = panelY() + 16;
		var mark = Theme.display("YAHU");
		g.text(font, mark, hx, hy, Theme.alpha(Theme.TEXT, appear), false);
		Spaced.draw(g, Theme.ui("MODS"), hx + font.width(mark) + 6, hy, 1.2f, Theme.alpha(Theme.ACCENT_HI, appear), false);
		long on = mods.stream().filter(m -> m.enabled().get()).count();
		var count = Theme.ui(on + " of " + mods.size() + " on");
		g.text(font, count, innerX() + innerW() - font.width(count), hy, Theme.alpha(Theme.MUTED, appear), false);
		Draw.hairline(g, innerX(), innerX() + innerW(), panelY() + HEADER - 6, Theme.alpha(0x1CFFFFFF, appear));

		float rise = (1 - Theme.easeOutCubic(page)) * 6;
		scroll = Theme.approach(scroll, scrollTarget, dt, 14);
		int rows = (mods.size() + columns() - 1) / columns();
		contentHeight = 4 + rows * (CARD_H + GAP);
		scrollTarget = Math.max(0, Math.min(scrollTarget, Math.max(0, contentHeight - (contentBottom() - contentTop()))));
		g.enableScissor(panelX(), contentTop() - 2, panelX() + panelW(), contentBottom());
		int cw = cardW();
		// taglines only when every card has room for its own, so the cards stay alike
		boolean tags = mods.stream().allMatch(m -> font.width(Theme.ui(m.tagline())) <= cw - 8);
		for (int i = 0; i < mods.size(); i++) {
			ModEntry mod = mods.get(i);
			int x = cardX(i), y = Math.round(cardY(i) + rise);
			boolean hot = inside(mouseX, mouseY, x, y, cw, CARD_H);
			cardHover[i] = Theme.approach(cardHover[i], hot ? 1 : 0, dt, 18);
			float h = cardHover[i];
			float lift = h * 1.5f;
			Glass.pane(g, x, y - lift, cw, CARD_H, 6, Theme.mix(Glass.FILL, Glass.FILL_HOT, h), Theme.mix(Glass.EDGE, Glass.EDGE_HOT, h), fade);

			// the icon: a disk in the mod's color (dim while off), its sign on it
			float cx = x + cw / 2f, iy = y - lift + 26;
			float t = cardOn[i];
			Draw.glow(g, cx, iy, 26, Theme.alpha(mod.color(), 0.22f * t * fade));
			Draw.circle(g, cx, iy, 14, Theme.alpha(Theme.mix(0xFF2A2F3B, mod.color(), 0.35f + 0.65f * t), fade));
			var icon = Theme.display(mod.icon());
			g.text(font, icon, Math.round(cx - font.width(icon) / 2f), Math.round(iy - 4), Theme.alpha(0xFFFFFFFF, fade), false);

			var name = Theme.ui(mod.name());
			g.text(font, name, Math.round(cx - font.width(name) / 2f), Math.round(y - lift + 46), Theme.alpha(0xFFFFFFFF, fade), false);
			var tag = Theme.ui(mod.tagline());
			if (tags) {
				g.text(font, tag, Math.round(cx - font.width(tag) / 2f), Math.round(y - lift + 57), Theme.alpha(Theme.MUTED, fade), false);
			}

			// the switch bar along the bottom
			int bx = x + 6, by = Math.round(y - lift) + CARD_H - 22, bw = cw - 12, bh = 16;
			boolean barHot = inside(mouseX, mouseY, bx, by, bw, bh);
			int fill = Theme.mix(0x50202530, 0xC0249468, t);
			if (barHot) {
				fill = Theme.mix(fill, 0xFFFFFFFF, 0.08f);
			}
			Glass.pane(g, bx, by, bw, bh, 3, fill, Theme.mix(0x30FFFFFF, 0x7048E0A0, t), fade);
			var label = Theme.ui(mod.enabled().get() ? "ENABLED" : "DISABLED");
			Spaced.draw(g, label, bx + bw / 2f, by + 4, cw < 100 ? 0.6f : 1.2f, Theme.alpha(Theme.mix(0xFFB4B9C4, 0xFFFFFFFF, t), fade), true);
		}
		g.disableScissor();
	}

	// --- one mod's settings ---

	private void extractDetail(GuiGraphicsExtractor g, ModEntry mod, int mouseX, int mouseY, float dt, float appear, float fade) {
		// header: back arrow, the mod's name, its switch
		boolean backHot = inside(mouseX, mouseY, backX(), backY(), BACK, BACK);
		backHover = Theme.approach(backHover, backHot ? 1 : 0, dt, 18);
		Glass.pane(g, backX(), backY(), BACK, BACK, Glass.RADIUS, Theme.mix(Glass.FILL, Glass.FILL_HOT, backHover),
				Theme.mix(Glass.EDGE, Glass.EDGE_HOT, backHover), appear);
		float icon = BACK * 0.45f;
		Icons.draw(g, "back", Icons.BACK, backX() + (BACK - icon) / 2f - backHover * 1f, backY() + (BACK - icon) / 2f, icon,
				Theme.alpha(Theme.mix(0xD0FFFFFF, 0xFFFFFFFF, backHover), appear));
		g.text(font, Theme.heading(mod.name()), backX() + BACK + 10, backY() + 4, Theme.alpha(Theme.TEXT, fade), false);
		int idx = mods.indexOf(mod);
		Control.switchAt(g, headerSwitchX(), backY() + 4.5f, cardOn[idx], inside(mouseX, mouseY, headerSwitchX() - 4, backY(), 30, BACK) ? 1 : 0);
		var state = Theme.ui(mod.enabled().get() ? "ON" : "OFF");
		g.text(font, state, headerSwitchX() - 8 - font.width(state), backY() + 6, Theme.alpha(mod.enabled().get() ? Theme.GOOD : Theme.MUTED, fade), false);
		Draw.hairline(g, innerX(), innerX() + innerW(), panelY() + HEADER - 6, Theme.alpha(0x1CFFFFFF, appear));

		int cx = innerX(), cw = innerW(), top = contentTop(), bottom = contentBottom();
		scroll = Theme.approach(scroll, scrollTarget, dt, 14);
		int offset = Math.round(scroll);
		float slide = (1 - Theme.easeOutCubic(page)) * 8;

		g.enableScissor(cx - 4, top - 2, cx + cw + 6, bottom);
		g.pose().pushMatrix();
		g.pose().translate(slide, 0);
		int y = top + 2 - offset;

		var lines = font.split(Theme.ui(mod.description()), cw);
		for (var line : lines) {
			g.text(font, line, cx, y, Theme.alpha(0xFFD2D6DF, fade), false);
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
			Glass.pane(g, cx, y, cw, PREVIEW_H, 4, Glass.FILL_DARK, Glass.EDGE, fade);
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
				Glass.pane(g, cx, y, cw, h + 8, 6, 0x50141820, 0x24FFFFFF, fade);
				int ry = y + 4;
				for (int i = start; i < gr.size(); i++) {
					Control c = gr.get(i);
					c.place(cx + 10, ry, cw - 20);
					c.extract(g, font, mouseX - Math.round(slide), mouseY, dt);
					ry += c.height();
					if (i < gr.size() - 1 && !gr.get(i + 1).isSection()) {
						Draw.hairline(g, cx + 10, cx + cw - 10, ry, Theme.alpha(0x1CFFFFFF, fade));
					}
				}
				y += h + 8 + 8;
			}
		}
		contentHeight = y + offset - top;
		g.pose().popMatrix();
		g.disableScissor();

		int max = Math.max(0, contentHeight - (bottom - top));
		if (max > 0) {
			float track = bottom - top;
			float bar = Math.max(20, track * track / Math.max(1, contentHeight));
			float by = top + (track - bar) * (scroll / max);
			Draw.pill(g, panelX() + panelW() - 6, by, 2, bar, Theme.alpha(0xFFFFFFFF, 0.25f));
		}
		scrollTarget = Math.max(0, Math.min(scrollTarget, max));
	}

	private static boolean inside(double mx, double my, double x, double y, double w, double h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		// the grid and a mod's settings both scroll when they don't fit
		{
			scrollTarget = Math.max(0, Math.min(Math.max(0, contentHeight - (contentBottom() - contentTop())), scrollTarget - (float) scrollY * 28));
			return true;
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x(), my = event.y();
		if (selected < 0) {
			int cw = cardW();
			for (int i = 0; i < mods.size(); i++) {
				int x = cardX(i), y = cardY(i);
				if (inside(mx, my, x, y, cw, CARD_H)) {
					ModEntry mod = mods.get(i);
					if (inside(mx, my, x + 6, y + CARD_H - 22, cw - 12, 16)) {
						mod.setEnabled().accept(!mod.enabled().get());
						save.run();
					} else {
						select(i);
					}
					return true;
				}
			}
			return super.mouseClicked(event, doubleClick);
		}

		if (inside(mx, my, backX(), backY(), BACK, BACK)) {
			select(-1);
			return true;
		}
		if (inside(mx, my, headerSwitchX() - 4, backY(), 30, BACK)) {
			ModEntry mod = mods.get(selected);
			mod.setEnabled().accept(!mod.enabled().get());
			save.run();
			return true;
		}
		if (my >= contentTop() && my < contentBottom()) {
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
		// Esc in a mod's settings goes back to the grid first
		if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE && selected >= 0) {
			select(-1);
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
