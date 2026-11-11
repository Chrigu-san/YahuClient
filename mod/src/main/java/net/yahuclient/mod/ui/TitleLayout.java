package net.yahuclient.mod.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * The main menu in the Yahu layout instead of Minecraft's centered button column: the word mark top left, under it a slim
 * navigation of left-aligned entries (a violet bar slides in on hover), the small icon buttons bottom right. Works on the
 * widgets the title screen (and other mods, like Mod Menu's "Mods") put there, it only moves and restyles them, so every
 * button keeps doing what it did.
 */
public final class TitleLayout {
	/** Buttons drawn as navigation entries (see AbstractButtonMixin). */
	public static final Set<Object> NAV = Collections.newSetFromMap(new WeakHashMap<>());
	private static final Map<Object, float[]> HOVER = new WeakHashMap<>();

	public static final int ITEM_H = 18;
	private static final int ITEM_GAP = 1;
	private static final float LOGO_SCALE = 3f;
	private static final int LOGO_H = (int) (9 * LOGO_SCALE) + 4 + 10;

	private static int left = 28;
	private static int logoTop = 30;
	private static int panelX, panelY, panelW, panelH;
	private static long lastFrame = System.nanoTime();
	private static float dt;

	private TitleLayout() {
	}

	public static int left() {
		return left;
	}

	public static int logoTop() {
		return logoTop;
	}

	public static float logoScale() {
		return LOGO_SCALE;
	}

	/** True while the title screen is the one shown: then the word mark goes top left. */
	public static boolean onTitle() {
		return Minecraft.getInstance().gui.screen() instanceof TitleScreen;
	}

	/** Moves the title screen's widgets into the Yahu layout. Run after the screen (and other mods) built them. */
	public static void apply(Screen screen, List<AbstractWidget> widgets) {
		List<AbstractWidget> nav = new ArrayList<>();
		List<AbstractWidget> end = new ArrayList<>();     // Options and Quit, after a little gap
		List<AbstractWidget> icons = new ArrayList<>();
		for (AbstractWidget w : widgets) {
			if (w instanceof PlainTextButton) {
				// the copyright line stays bottom right
				w.setPosition(screen.width - w.getWidth() - 4, screen.height - 12);
			} else if (w.getWidth() <= 24 && w.getHeight() <= 24) {
				icons.add(w);
			} else if (w instanceof AbstractButton) {
				String key = key(w.getMessage());
				(("menu.options".equals(key) || "menu.quit".equals(key)) ? end : nav).add(w);
			}
		}
		// keep the order they had, top to bottom, left to right
		java.util.Comparator<AbstractWidget> order = java.util.Comparator.comparingInt(AbstractWidget::getY).thenComparingInt(AbstractWidget::getX);
		nav.sort(order);
		end.sort(order);
		icons.sort(order);

		int width = Math.max(120, Math.min(170, screen.width / 3));
		left = Math.max(30, Math.min(48, screen.width / 14));
		int items = nav.size() + end.size();
		int gap = end.isEmpty() ? 0 : 8;
		int total = LOGO_H + 14 + items * (ITEM_H + ITEM_GAP) + gap;
		logoTop = Math.max(18, (screen.height - total) / 2 + 2);
		int y = logoTop + LOGO_H + 14;
		for (AbstractWidget w : nav) {
			place(w, y, width);
			y += ITEM_H + ITEM_GAP;
		}
		y += gap;
		for (AbstractWidget w : end) {
			place(w, y, width);
			y += ITEM_H + ITEM_GAP;
		}
		// the glass panel around the word mark and the navigation
		panelX = left - 16;
		panelY = logoTop - 14;
		panelW = width + 16;
		panelH = y - panelY + 8;
		// icon buttons in a row bottom right, above the copyright line
		int x = screen.width - 6;
		for (int i = icons.size() - 1; i >= 0; i--) {
			AbstractWidget w = icons.get(i);
			x -= w.getWidth();
			w.setPosition(x, screen.height - 16 - w.getHeight());
			x -= 4;
		}
	}

	private static void place(AbstractWidget w, int y, int width) {
		w.setPosition(left - 8, y);
		w.setWidth(width);
		w.setHeight(ITEM_H);
		NAV.add(w);
	}

	private static String key(Component message) {
		return message.getContents() instanceof TranslatableContents t ? t.getKey() : "";
	}

	/** The frosted panel behind the word mark and the navigation. */
	public static void panel(GuiGraphicsExtractor g) {
		if (panelW > 0) {
			Glass.panel(g, panelX, panelY, panelW, panelH, 10, 1f);
		}
	}
	/** Called once per drawn frame for the hover animation. */
	public static void frame() {
		long now = System.nanoTime();
		dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;
	}

	/** One navigation entry: just its label, on hover a soft fill, the label moves a little and a violet bar slides in. */
	public static void drawEntry(GuiGraphicsExtractor g, AbstractWidget w, boolean active, boolean hot, boolean highlighted, float alpha) {
		float[] h = HOVER.computeIfAbsent(w, k -> new float[1]);
		h[0] = Theme.approach(h[0], hot && active ? 1 : 0, dt, 16);
		float t = h[0];
		int x = w.getX(), y = w.getY(), wd = w.getWidth(), ht = w.getHeight();
		if (t > 0.01f) {
			// a frosted pill fades in under the mouse
			Glass.pane(g, x, y, wd, ht, 6, Theme.alpha(Glass.FILL_HOT, t), Theme.alpha(0x60FFFFFF, t), alpha);
		}
		int color;
		if (!active) {
			color = Theme.FAINT;
		} else if (highlighted) {
			color = Theme.mix(Theme.ACCENT_HI, 0xFFFFFFFF, t * 0.5f);
		} else {
			color = Theme.mix(0xFFDCE0E8, 0xFFFFFFFF, t);
		}
		var font = Minecraft.getInstance().font;
		int tx = x + 8 + Math.round(3 * t);
		int ty = y + (ht - 8) / 2;
		if (highlighted) {
			Draw.circle(g, tx + 2.5f, y + ht / 2f, 2.5f, Theme.alpha(Theme.ACCENT, alpha));
			tx += 9;
		}
		g.text(font, w.getMessage(), tx, ty, Theme.alpha(color, alpha), false);
	}
}
