package net.yahuclient.mod.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
 * The main menu in the Yahu layout: everything centered and calm. The logo with the word mark under it, a short column
 * of slim glass buttons with spaced capital letters, the small icon buttons (settings and Minecraft's own) in a row at
 * the bottom, and a close X in the top right corner instead of a Quit button.
 *
 * It only moves and restyles the widgets the title screen (and other mods, like Mod Menu's "Mods") built, so every button
 * keeps doing what it did.
 */
public final class TitleLayout {
	/** Column buttons, drawn with a spaced capital label (see AbstractButtonMixin). */
	public static final Set<Object> NAV = Collections.newSetFromMap(new WeakHashMap<>());
	/** Buttons shown as an icon instead of their label. */
	public static final Map<Object, String> ICON = new WeakHashMap<>();
	private static final Map<Object, float[]> HOVER = new WeakHashMap<>();

	public static final int ITEM_H = 20;
	private static final int ITEM_GAP = 5;
	public static final int LOGO = 46;
	private static final int ICON_SIZE = 20;

	private static int logoTop = 30;
	private static long lastFrame = System.nanoTime();
	private static float dt;

	private TitleLayout() {
	}

	public static int logoTop() {
		return logoTop;
	}

	/** True while the title screen is the one shown. */
	public static boolean onTitle() {
		return Minecraft.getInstance().gui.screen() instanceof TitleScreen;
	}

	/** Moves the title screen's widgets into the Yahu layout. Run after the screen (and other mods) built them. */
	public static void apply(Screen screen, List<AbstractWidget> widgets) {
		List<AbstractWidget> column = new ArrayList<>();
		List<AbstractWidget> icons = new ArrayList<>();
		AbstractWidget options = null, quit = null;
		for (AbstractWidget w : widgets) {
			if (w instanceof PlainTextButton) {
				w.setPosition(screen.width - w.getWidth() - 4, screen.height - 12);
			} else if (w.getWidth() <= 24 && w.getHeight() <= 24) {
				icons.add(w);
			} else if (w instanceof AbstractButton) {
				String key = key(w.getMessage());
				if ("menu.options".equals(key)) {
					options = w;
				} else if ("menu.quit".equals(key)) {
					quit = w;
				} else {
					column.add(w);
				}
			}
		}
		Comparator<AbstractWidget> order = Comparator.comparingInt(AbstractWidget::getY).thenComparingInt(AbstractWidget::getX);
		column.sort(order);
		icons.sort(order);

		int width = Math.min(200, screen.width - 40);
		int cx = screen.width / 2;
		int total = LOGO + 6 + 9 + 18 + column.size() * (ITEM_H + ITEM_GAP) - ITEM_GAP;
		logoTop = Math.max(10, (screen.height - total) / 2 - 10);
		int y = logoTop + LOGO + 6 + 9 + 18;
		for (AbstractWidget w : column) {
			w.setPosition(cx - width / 2, y);
			w.setWidth(width);
			w.setHeight(ITEM_H);
			NAV.add(w);
			y += ITEM_H + ITEM_GAP;
		}

		// settings first, then Minecraft's own small buttons, in one row at the bottom
		if (options != null) {
			icon(options, "gear");
			icons.add(0, options);
		}
		int rowW = icons.size() * ICON_SIZE + Math.max(0, icons.size() - 1) * 6;
		int x = cx - rowW / 2;
		for (AbstractWidget w : icons) {
			w.setPosition(x, screen.height - 34);
			x += w.getWidth() + 6;
		}
		if (quit != null) {
			icon(quit, "close");
			quit.setPosition(screen.width - ICON_SIZE - 8, 8);
		}
	}

	/** Gives a screen's text buttons the main menu's look (spaced capitals) without moving them: the pause menu. */
	public static void restyle(List<AbstractWidget> widgets) {
		for (AbstractWidget w : widgets) {
			if (w instanceof AbstractButton && w.getWidth() > 40 && !(w instanceof PlainTextButton)) {
				NAV.add(w);
			}
		}
	}

	private static void icon(AbstractWidget w, String name) {
		w.setWidth(ICON_SIZE);
		w.setHeight(ICON_SIZE);
		ICON.put(w, name);
	}

	private static String key(Component message) {
		return message.getContents() instanceof TranslatableContents t ? t.getKey() : "";
	}

	/** Called once per drawn frame for the hover animation. */
	public static void frame() {
		long now = System.nanoTime();
		dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;
	}

	/** The logo and the word mark, centered above the column. */
	public static void drawLogo(GuiGraphicsExtractor g, int width, float alpha) {
		float x = (width - LOGO) / 2f;
		Icons.draw(g, "logo", Icons.LOGO, x, logoTop, LOGO, Theme.alpha(0xFFFFFFFF, alpha));
		Spaced.draw(g, Theme.display("YAHU CLIENT"), width / 2f, logoTop + LOGO + 7, 1.2f, Theme.alpha(0xFFFFFFFF, alpha), true);
	}

	/** One column entry: a slim glass button, its label in spaced capitals; the edge brightens under the mouse. */
	public static void drawEntry(GuiGraphicsExtractor g, AbstractWidget w, boolean active, boolean hot, boolean highlighted, float alpha) {
		float[] h = HOVER.computeIfAbsent(w, k -> new float[1]);
		h[0] = Theme.approach(h[0], hot && active ? 1 : 0, dt, 18);
		float t = h[0];
		int x = w.getX(), y = w.getY(), wd = w.getWidth(), ht = w.getHeight();
		if (highlighted) {
			Glass.pane(g, x, y, wd, ht, Glass.RADIUS, Theme.mix(Theme.alpha(Glass.TINT, 0.40f), Theme.alpha(Glass.TINT, 0.6f), t),
					Theme.mix(0x70B9A8FF, 0xC0D8CEFF, t), alpha);
		} else {
			Glass.pane(g, x, y, wd, ht, Glass.RADIUS, Theme.mix(Glass.FILL, Glass.FILL_HOT, t), Theme.mix(Glass.EDGE, Glass.EDGE_HOT, t), alpha);
		}
		int color = active ? Theme.mix(0xE0FFFFFF, 0xFFFFFFFF, t) : 0x80FFFFFF;
		// spaced capitals; the trailing dots of "Options..." go, they only crowd the label
		var label = Theme.ui(w.getMessage().getString().replaceAll("(\\.\\.\\.|…)$", "").trim().toUpperCase(Locale.ROOT));
		// as much spacing as fits (narrow buttons get tighter letters)
		int letters = label.getString().codePointCount(0, label.getString().length());
		float plain = Spaced.width(label, 0);
		float spacing = letters > 1 ? Math.max(0.2f, Math.min(1.4f, (wd - 12 - plain) / (letters - 1))) : 0;
		Spaced.draw(g, label, x + wd / 2f, y + (ht - 8) / 2f, spacing, Theme.alpha(color, alpha), true);
	}

	/** A button shown as one of our icons: a square glass button, the icon in the middle. */
	public static void drawIcon(GuiGraphicsExtractor g, AbstractWidget w, String name, boolean hot, float alpha) {
		float[] h = HOVER.computeIfAbsent(w, k -> new float[1]);
		h[0] = Theme.approach(h[0], hot ? 1 : 0, dt, 18);
		float t = h[0];
		int x = w.getX(), y = w.getY(), s = w.getWidth();
		Glass.pane(g, x, y, s, w.getHeight(), Glass.RADIUS, Theme.mix(Glass.FILL, Glass.FILL_HOT, t), Theme.mix(Glass.EDGE, Glass.EDGE_HOT, t), alpha);
		float icon = s * 0.5f;
		Tex.Shape shape = "close".equals(name) ? Icons.CLOSE : Icons.GEAR;
		Icons.draw(g, name, shape, x + (s - icon) / 2f, y + (w.getHeight() - icon) / 2f, icon, Theme.alpha(Theme.mix(0xD0FFFFFF, 0xFFFFFFFF, t), alpha));
	}
}
