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
	/** Column buttons drawn a little quieter (Quit). */
	public static final Set<Object> QUIET = Collections.newSetFromMap(new WeakHashMap<>());
	/** The main menu's corner lines (copyright), drawn small and faint wherever the main menu shows. */
	public static final Set<Object> CORNER = Collections.newSetFromMap(new WeakHashMap<>());
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
				CORNER.add(w);
			} else if (w.getWidth() <= 24 && w.getHeight() <= 24) {
				icons.add(w);
			} else if (w instanceof AbstractButton) {
				String key = key(w.getMessage());
				if ("menu.options".equals(key)) {
					options = w;
				} else if ("menu.quit".equals(key)) {
					quit = w;
				} else if ("menu.online".equals(key) && !net.yahuclient.mod.YahuMod.config().menu.showRealms) {
					// Realms is a paid Mojang subscription few people use: hidden unless turned on in the Yahu menu
					w.visible = false;
					w.active = false;
				} else {
					column.add(w);
				}
			}
		}
		Comparator<AbstractWidget> order = Comparator.comparingInt(AbstractWidget::getY).thenComparingInt(AbstractWidget::getX);
		column.sort(order);
		icons.sort(order);
		// Quit at the end of the column, a little quieter than the rest
		if (quit != null) {
			column.add(quit);
			QUIET.add(quit);
		}

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

	/** A corner line (version, copyright): small, faint, in the bottom left or right corner. */
	public static void corner(GuiGraphicsExtractor g, String text, int margin, boolean right, int alpha) {
		var font = Minecraft.getInstance().font;
		var line = Theme.ui(text);
		float scale = .72f;
		float w = font.width(line) * scale;
		float x = right ? g.guiWidth() - margin - w : margin, y = g.guiHeight() - margin - 7 * scale;
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.text(font, line, 0, 0, Theme.alpha(0xFFC8CDD7, .32f * alpha / 255f), false);
		g.pose().popMatrix();
	}

	/** Called once per drawn frame for the hover animation. */
	public static void frame() {
		long now = System.nanoTime();
		dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;
	}

	/** The logo and the word mark, centered above the column. */
	public static void drawLogo(GuiGraphicsExtractor g, int width, float alpha) {
		float x = (width - LOGO) / 2f, y = logoTop;
		// the glass tile leans to the cursor like the buttons, and gets rained on too
		double[] m = GlassFx.mouse();
		boolean hot = m[0] >= x && m[0] < x + LOGO && m[1] >= y && m[1] < y + LOGO;
		GlassFx.State s = GlassFx.begin(g, LOGO_KEY, x, y, LOGO, LOGO, hot, false, 26);
		Icons.draw(g, "logo_a", Icons.LOGO_A, x, y, LOGO, Theme.alpha(0xFFFFFFFF, alpha));
		// only on the tile itself: the rounded square inside the logo (8 % in from each side, corners of 26 %)
		s.rain.draw(g, x, y, alpha, x + LOGO * .08f, y + LOGO * .08f, LOGO * .84f, LOGO * .84f, LOGO * .26f);
		GlassFx.end();
		Spaced.draw(g, Theme.display("YAHU"), width / 2f, logoTop + LOGO + 7, 2.6f, Theme.alpha(0xFFDCDFE6, alpha), true);
		Spaced.draw(g, Theme.ui("CLIENT"), width / 2f, logoTop + LOGO + 18, 3.2f, Theme.alpha(0x8CCDD2DC, alpha), true);
	}

	private static final Object LOGO_KEY = new Object();

	/** One column entry: a slim glass button, its label in spaced capitals; the edge brightens under the mouse. */
	public static void drawEntry(GuiGraphicsExtractor g, AbstractWidget w, boolean active, boolean hot, boolean highlighted, float alpha) {
		int x = w.getX(), y = w.getY(), wd = w.getWidth(), ht = w.getHeight();
		boolean pressed = hot && active && Minecraft.getInstance().mouseHandler.isLeftPressed();
		GlassFx.State s = GlassFx.begin(g, w, x, y, wd, ht, hot && active, pressed, 9);
		float t = s.hover;
		GlassFx.button(g, s, x, y, wd, ht, highlighted, QUIET.contains(w), alpha);
		int color = active ? Theme.mix(QUIET.contains(w) ? 0xFFB9BDC7 : 0xFFDFE2E9, 0xFFF2F4F8, t) : 0x80DFE2E9;
		// spaced capitals; the trailing dots of "Options..." go, they only crowd the label
		var label = Theme.ui(w.getMessage().getString().replaceAll("(\\.\\.\\.|…)$", "").trim().toUpperCase(Locale.ROOT));
		// as much spacing as fits (narrow buttons get tighter letters)
		int letters = label.getString().codePointCount(0, label.getString().length());
		float plain = Spaced.width(label, 0);
		float spacing = letters > 1 ? Math.max(0.2f, Math.min(1.4f, (wd - 12 - plain) / (letters - 1))) : 0;
		float sp = spacing;
		GlassFx.label(g, x + wd / 2f, y + ht / 2f, () -> Spaced.draw(g, label, x + wd / 2f, y + (ht - 8) / 2f, sp, Theme.alpha(color, alpha), true));
		GlassFx.end();
	}

	/** A button shown as one of our icons: a square glass button, the icon in the middle. */
	public static void drawIcon(GuiGraphicsExtractor g, AbstractWidget w, String name, boolean hot, float alpha) {
		int x = w.getX(), y = w.getY(), s = w.getWidth();
		GlassFx.State st = GlassFx.begin(g, w, x, y, s, w.getHeight(), hot, hot && Minecraft.getInstance().mouseHandler.isLeftPressed(), 22);
		GlassFx.button(g, st, x, y, s, w.getHeight(), false, false, alpha);
		float icon = s * 0.5f;
		Tex.Shape shape = "close".equals(name) ? Icons.CLOSE : Icons.GEAR;
		Icons.draw(g, name, shape, x + (s - icon) / 2f, y + (w.getHeight() - icon) / 2f, icon, Theme.alpha(Theme.mix(0xFFCDD1DA, 0xFFF2F4F8, st.hover), alpha));
		GlassFx.end();
	}
}
