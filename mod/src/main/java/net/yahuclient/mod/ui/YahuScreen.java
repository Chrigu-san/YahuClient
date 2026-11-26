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
	private static final int MAX_PANEL_W = 440;
	private static final float RADIUS = 2.5f;
	private static final Object PANEL_KEY = new Object();
	private static final int CARD_MIN_W = 104;
	private static final int CARD_H = 66;
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
	private float closeHover;
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
			cardOn[i] = mods.get(i).enabled() == null || mods.get(i).enabled().get() ? 1 : 0;
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
		return (height - panelH()) / 2;
	}

	// the grid's panel is as tall as its cards need (centered); a mod's settings use the full height
	private int panelH() {
		if (selected >= 0) {
			return height - 24;
		}
		int rows = (mods.size() + columns() - 1) / columns();
		return Math.min(height - 24, HEADER + 4 + rows * (CARD_H + GAP) - GAP + 16);
	}

	private int closeX() {
		return panelX() + panelW() - 16 - 10;
	}

	private int closeY() {
		return panelY() + 13;
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
		// one calm panel of dark glass with rain on it, small corners (it doesn't lean: it is the whole menu)
		GlassFx.State ps = GlassFx.begin(g, PANEL_KEY, panelX(), panelY(), panelW(), panelH(), false, false, 0);
		ps.rainDensity(.25f);
		GlassFx.panel(g, ps, panelX(), panelY(), panelW(), panelH(), RADIUS, appear);
		GlassFx.end();
		for (int i = 0; i < mods.size(); i++) {
			cardOn[i] = Theme.approach(cardOn[i], mods.get(i).enabled() == null || mods.get(i).enabled().get() ? 1 : 0, dt, 16);
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
		g.text(font, mark, hx, hy, Theme.alpha(0xFFDCDFE6, appear), false);
		Spaced.draw(g, Theme.ui("MODS"), hx + font.width(mark) + 6, hy, 1.2f, Theme.alpha(0xFFA898E6, appear), false);
		// top right: a clean X to leave the menu, brighter under the mouse
		boolean closeHot = inside(mouseX, mouseY, closeX() - 3, closeY() - 3, 16, 16);
		closeHover = Theme.approach(closeHover, closeHot ? 1 : 0, dt, 18);
		Icons.draw(g, "close", Icons.CLOSE, closeX(), closeY(), 10, Theme.alpha(Theme.mix(0xFF8A909C, 0xFFF2F4F8, closeHover), appear));
		Draw.hairline(g, innerX(), innerX() + innerW(), panelY() + HEADER - 6, Theme.alpha(0x1CFFFFFF, appear));

		float rise = (1 - Theme.easeOutCubic(page)) * 6;
		scroll = Theme.approach(scroll, scrollTarget, dt, 14);
		int rows = (mods.size() + columns() - 1) / columns();
		contentHeight = 4 + rows * (CARD_H + GAP);
		scrollTarget = Math.max(0, Math.min(scrollTarget, Math.max(0, contentHeight - (contentBottom() - contentTop()))));
		g.enableScissor(panelX(), contentTop() - 2, panelX() + panelW(), contentBottom());
		int cw = cardW();
		// taglines only when every card has room for its own, so the cards stay alike
		boolean tags = mods.stream().allMatch(m -> font.width(Theme.ui(m.tagline())) <= cw - 16);
		boolean pressed = minecraft.mouseHandler.isLeftPressed();
		for (int i = 0; i < mods.size(); i++) {
			ModEntry mod = mods.get(i);
			int x = cardX(i), y = Math.round(cardY(i) + rise);
			boolean hot = inside(mouseX, mouseY, x, y, cw, CARD_H);
			// a glass card like the main menu's buttons: it leans to the cursor and the rain runs down it
			GlassFx.State s = GlassFx.begin(g, mod, x, y, cw, CARD_H, hot, hot && pressed, 7);
			GlassFx.button(g, s, x, y, cw, CARD_H, false, false, fade, RADIUS);
			float t = cardOn[i];
			boolean barHot = inside(mouseX, mouseY, x + cw - 34, y + CARD_H - 20, 30, 16);
			int cx = x, cy = y;
			GlassFx.label(g, x + cw / 2f, y + CARD_H / 2f, () -> {
				// the mod's sign in its color, its name, what it does
				var icon = Theme.display(mod.icon());
				g.text(font, icon, cx + 10, cy + 10, Theme.alpha(Theme.mix(0xFF6C7280, mod.color(), .35f + .65f * t), fade), false);
				g.text(font, Theme.ui(mod.name()), cx + 24, cy + 10, Theme.alpha(0xFFE2E5EB, fade), false);
				if (tags) {
					g.text(font, Theme.ui(mod.tagline()), cx + 10, cy + 24, Theme.alpha(0xFF8F95A1, fade), false);
				}
				// along the bottom: on or off, and the switch
				Draw.hairline(g, cx + 8, cx + cw - 8, cy + CARD_H - 23, Theme.alpha(0x14FFFFFF, fade));
				if (mod.enabled() == null) {
					g.text(font, Theme.ui("Open"), cx + 10, cy + CARD_H - 16, Theme.alpha(0xFF8F95A1, fade), false);
					return;
				}
				Draw.circle(g, cx + 12, cy + CARD_H - 12, 2, Theme.alpha(Theme.mix(0xFF5A606C, 0xFF4EE0A4, t), fade));
				g.text(font, Theme.ui(mod.enabled().get() ? "On" : "Off"), cx + 18, cy + CARD_H - 16, Theme.alpha(Theme.mix(0xFF8F95A1, 0xFFCFEFDF, t), fade), false);
				Control.switchAt(g, cx + cw - 32, cy + CARD_H - 17.5f, t, barHot ? 1 : 0);
			});
			GlassFx.end();
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
		if (mod.enabled() != null) {
			Control.switchAt(g, headerSwitchX(), backY() + 4.5f, cardOn[idx], inside(mouseX, mouseY, headerSwitchX() - 4, backY(), 30, BACK) ? 1 : 0);
			var state = Theme.ui(mod.enabled().get() ? "ON" : "OFF");
			g.text(font, state, headerSwitchX() - 8 - font.width(state), backY() + 6, Theme.alpha(mod.enabled().get() ? Theme.GOOD : Theme.MUTED, fade), false);
		}
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
				Glass.pane(g, cx, y, cw, h + 8, 3, 0x50141820, 0x20FFFFFF, fade);
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
			if (inside(mx, my, closeX() - 3, closeY() - 3, 16, 16)) {
				onClose();
				return true;
			}
			int cw = cardW();
			for (int i = 0; i < mods.size(); i++) {
				int x = cardX(i), y = cardY(i);
				if (inside(mx, my, x, y, cw, CARD_H)) {
					ModEntry mod = mods.get(i);
					if (mod.enabled() != null && inside(mx, my, x + cw - 34, y + CARD_H - 20, 30, 16)) {
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
		if (mods.get(selected).enabled() != null && inside(mx, my, headerSwitchX() - 4, backY(), 30, BACK)) {
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

	/** The search field that has the keyboard, if any. */
	private PackControls.Search typing() {
		if (selected < 0) {
			return null;
		}
		for (Control c : mods.get(selected).controls()) {
			if (c instanceof PackControls.Search s && s.focused()) {
				return s;
			}
		}
		return null;
	}

	@Override
	public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
		PackControls.Search field = typing();
		if (field != null && field.typed(event.codepoint())) {
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		PackControls.Search field = typing();
		if (field != null && (field.key(event.key()))) {
			return true;  // typing keeps the keys, Esc leaves the field
		}
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
