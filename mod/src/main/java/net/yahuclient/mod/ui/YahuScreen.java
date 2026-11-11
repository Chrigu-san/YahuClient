package net.yahuclient.mod.ui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
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
	private static final int NAV_W = 150;
	private static final int ITEM_H = 26;
	private static final int LIST_Y = 46;
	private static final int MAX_CONTENT_W = 420;
	private static final int PREVIEW_H = 70;

	private final Screen parent;
	private final List<ModEntry> mods;
	private final Runnable save;
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

	public YahuScreen(Screen parent, List<ModEntry> mods, Runnable save, boolean inWorld) {
		super(Component.literal("Yahu Client"));
		this.parent = parent;
		this.mods = mods;
		this.save = save;
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

	// full screen: the mod list as a sidebar on the left edge, the chosen mod's page to its right
	private int contentW() {
		return Math.min(MAX_CONTENT_W, width - NAV_W - 48);
	}

	private int contentX() {
		return NAV_W + 24 + Math.max(0, (width - NAV_W - 48 - contentW()) / 2);
	}

	private int contentTop() {
		return 18;
	}

	private int contentBottom() {
		return height - 10;
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
		// the sidebar slides in from the left edge, the page fades in
		float slideIn = (1 - appear) * -12;
		g.pose().pushMatrix();
		g.pose().translate(slideIn, 0);
		Glass.panel(g, 8, 8, NAV_W - 8, height - 16, 10, appear);
		extractNav(g, mouseX - Math.round(slideIn), mouseY, dt, appear);
		g.pose().popMatrix();
		if (selected < mods.size()) {
			extractPage(g, mods.get(selected), mouseX, mouseY, dt, appear);
		}
	}

	private void extractNav(GuiGraphicsExtractor g, int mouseX, int mouseY, float dt, float appear) {
		int x = 20, y = 22;
		// the word mark: YAHU in the heading font, MODS after it in the accent
		var mark = Theme.display("YAHU");
		g.text(font, mark, x, y, Theme.alpha(Theme.TEXT, appear), false);
		g.text(font, Theme.ui("MODS"), x + font.width(mark) + 5, y, Theme.alpha(Theme.ACCENT_HI, appear), false);

		marker = marker < 0 ? selected : Theme.approach(marker, selected, dt, 16);
		// the selection: a full-width row that glides to the chosen mod, an accent edge on the left
		int my = Math.round(LIST_Y + marker * ITEM_H);
		Glass.pane(g, 13, my + 1, NAV_W - 18, ITEM_H - 2, 6, Glass.FILL_HOT, 0x60FFFFFF, appear);

		for (int i = 0; i < mods.size(); i++) {
			ModEntry mod = mods.get(i);
			int iy = LIST_Y + i * ITEM_H;
			boolean hot = inside(mouseX, mouseY, 0, iy, NAV_W, ITEM_H);
			navHover[i] = Theme.approach(navHover[i], hot && i != selected ? 1 : 0, dt, 18);
			navOn[i] = Theme.approach(navOn[i], mod.enabled().get() ? 1 : 0, dt, 16);
			if (navHover[i] > 0.01f) {
				Draw.round(g, 13, iy + 1, NAV_W - 18, ITEM_H - 2, 6, Theme.alpha(Glass.FILL_DIM, navHover[i] * appear));
			}
			// a small square in the mod's color, bright while it is on
			Draw.round(g, x, iy + ITEM_H / 2f - 3, 6, 6, 1.5f, Theme.alpha(Theme.mix(0xFF3A3E4A, mod.color(), navOn[i]), appear));
			int nameColor = i == selected ? 0xFFFFFFFF : Theme.mix(0xFFC4C9D4, 0xFFFFFFFF, navHover[i]);
			g.text(font, Theme.ui(mod.name()), x + 13, iy + (ITEM_H - 8) / 2, Theme.alpha(nameColor, appear), false);
			Control.switchAt(g, NAV_W - 38, iy + (ITEM_H - 11) / 2f, navOn[i], hot ? 1 : 0);
		}

		g.text(font, Theme.ui("Esc to close"), x, height - 26, Theme.alpha(Theme.FAINT, appear), false);
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
		g.pose().scale(1.35f, 1.35f);
		g.text(font, Theme.display(mod.name()), 0, 0, Theme.alpha(Theme.TEXT, fade), false);
		g.pose().popMatrix();
		boolean on = mod.enabled().get();
		var state = Theme.ui(on ? "ACTIVE" : "OFF");
		int stateX = cx + cw - font.width(state);
		g.fill(stateX - 8, y + 6, stateX - 4, y + 10, Theme.alpha(on ? Theme.GOOD : Theme.FAINT, fade));
		g.text(font, state, stateX, y + 4, Theme.alpha(on ? Theme.GOOD : Theme.MUTED, fade), false);
		y += 20;
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
			Glass.pane(g, cx, y, cw, PREVIEW_H, 8, Glass.FILL_DARK, Glass.EDGE, fade);
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
				Glass.panel(g, cx, y, cw, h + 8, 8, fade);
				int ry = y + 4;
				for (int i = start; i < gr.size(); i++) {
					Control c = gr.get(i);
					c.place(cx + 10, ry, cw - 20);
					c.extract(g, font, mouseX - Math.round(slide), mouseY, dt);
					ry += c.height();
					if (i < gr.size() - 1 && !gr.get(i + 1).isSection()) {
						g.fill(cx + 10, ry, cx + cw - 10, ry + 1, Theme.alpha(0x14FFFFFF, fade));
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

			float track = bottom - top;
			float bar = Math.max(20, track * track / Math.max(1, contentHeight));
			float by = top + (track - bar) * (scroll / max);
			g.fill(width - 5, Math.round(by), width - 3, Math.round(by + bar), Theme.alpha(0xFFFFFFFF, 0.2f));
		}
		scrollTarget = Math.max(0, Math.min(scrollTarget, max));
	}

	private static boolean inside(double mx, double my, double x, double y, double w, double h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX >= NAV_W + 4) {
			scrollTarget = Math.max(0, Math.min(Math.max(0, contentHeight - (contentBottom() - contentTop())), scrollTarget - (float) scrollY * 28));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x(), my = event.y();
		for (int i = 0; i < mods.size(); i++) {
			int iy = LIST_Y + i * ITEM_H;
			if (inside(mx, my, 0, iy, NAV_W, ITEM_H)) {
				ModEntry mod = mods.get(i);
				if (mx >= NAV_W - 44) {
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
	public void onClose() {
		save.run();
		minecraft.setScreenAndShow(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
