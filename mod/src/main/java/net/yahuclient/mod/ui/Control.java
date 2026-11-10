package net.yahuclient.mod.ui;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * One setting in the Yahu menu: the label on the left, the control on the right. The screen places it, draws it and passes
 * the mouse on; every change goes straight into the config and the screen saves it. Movements ease towards their target,
 * the same at any frame rate.
 */
public abstract class Control {
	public static final int ROW = 22;

	protected final String label;
	protected int x, y, w;
	protected float hover;

	protected Control(String label) {
		this.label = label;
	}

	public void place(int x, int y, int w) {
		this.x = x;
		this.y = y;
		this.w = w;
	}

	public int height() {
		return ROW;
	}

	/** Whether this control starts a new group (the screen draws a card per group). */
	public boolean isSection() {
		return false;
	}

	public void extract(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, float seconds) {
		hover = Theme.approach(hover, inside(mouseX, mouseY, x, y, w, height()) ? 1 : 0, seconds, 18);
		labelText(g, font);
	}

	protected void labelText(GuiGraphicsExtractor g, Font font) {
		g.text(font, Theme.ui(label), x, y + (ROW - font.lineHeight) / 2 + 1, Theme.mix(Theme.MUTED, Theme.TEXT, hover), false);
	}

	public boolean click(double mouseX, double mouseY) {
		return false;
	}

	public void drag(double mouseX, double mouseY) {
	}

	public void release() {
	}

	protected static boolean inside(double mx, double my, double bx, double by, double bw, double bh) {
		return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
	}

	/** Where the control itself starts: the right part of the row. */
	protected int fieldX() {
		return x + Math.max(90, w * 2 / 5);
	}

	protected int fieldW() {
		return x + w - fieldX();
	}

	// -----------------------------------------------------------------------------------------------------------------

	/** A small title over a group of settings. */
	public static final class Section extends Control {
		public Section(String label) {
			super(label);
		}

		@Override
		public int height() {
			return 20;
		}

		@Override
		public boolean isSection() {
			return true;
		}

		@Override
		public void extract(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, float seconds) {
			g.pose().pushMatrix();
			g.pose().translate(x, y + 9);
			g.pose().scale(0.85f, 0.85f);
			g.text(font, Theme.ui(label.toUpperCase()), 0, 0, Theme.FAINT, false);
			g.pose().popMatrix();
		}
	}

	/** A line of explaining text, muted, wrapped to the width. */
	public static final class Note extends Control {
		private final Supplier<String> text;
		private int lines = 1;

		public Note(Supplier<String> text) {
			super("");
			this.text = text;
		}

		@Override
		public int height() {
			return 4 + lines * 10;
		}

		@Override
		public void extract(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, float seconds) {
			var wrapped = font.split(Theme.ui(text.get()), Math.max(40, w));
			lines = Math.max(1, wrapped.size());
			for (int i = 0; i < wrapped.size(); i++) {
				g.text(font, wrapped.get(i), x, y + 2 + i * 10, Theme.FAINT, false);
			}
		}
	}

	/** An on/off switch: the knob slides across and the track fills with the accent. */
	public static final class Toggle extends Control {
		private final Supplier<Boolean> get;
		private final Consumer<Boolean> set;
		private float on = -1;

		public Toggle(String label, Supplier<Boolean> get, Consumer<Boolean> set) {
			super(label);
			this.get = get;
			this.set = set;
		}

		@Override
		public void extract(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, float seconds) {
			super.extract(g, font, mouseX, mouseY, seconds);
			float target = get.get() ? 1 : 0;
			on = on < 0 ? target : Theme.approach(on, target, seconds, 16);
			switchAt(g, x + w - 26, y + (ROW - 14) / 2, on, hover);
		}

		@Override
		public boolean click(double mx, double my) {
			if (inside(mx, my, x, y, w, ROW)) {
				set.accept(!get.get());
				return true;
			}
			return false;
		}
	}

	/** The switch itself, shared with the mod list: 26 by 14, t from off (0) to on (1). */
	public static void switchAt(GuiGraphicsExtractor g, float sx, float sy, float t, float hover) {
		int track = Theme.mix(Theme.mix(0xFF2A2D38, 0xFF343844, hover), Theme.ACCENT, t);
		Draw.pill(g, sx, sy, 26, 14, track);
		float knobX = sx + 7 + 12 * Theme.easeOutCubic(t);
		Draw.circle(g, knobX, sy + 7, 5.5f, Theme.mix(0xFFC9CDD6, 0xFFFFFFFF, Math.max(t, hover)));
	}

	/** A slider: a thin track filled with the accent up to the value, a round knob that grows under the mouse. */
	public static final class Slider extends Control {
		private final double min, max, step;
		private final DoubleSupplier get;
		private final Consumer<Double> set;
		private final DoubleFunction<String> format;
		private boolean dragging;
		private float grab;

		public Slider(String label, double min, double max, double step, DoubleFunction<String> format, DoubleSupplier get, Consumer<Double> set) {
			super(label);
			this.min = min;
			this.max = max;
			this.step = step;
			this.format = format;
			this.get = get;
			this.set = set;
		}

		/** A whole-number slider with a unit after the value. */
		public static Slider ints(String label, int min, int max, String unit, IntSupplier get, IntConsumer set) {
			return new Slider(label, min, max, 1, v -> Math.round(v) + unit, () -> get.getAsInt(), v -> set.accept((int) Math.round(v)));
		}

		private int trackX() {
			return fieldX();
		}

		private int trackW() {
			return fieldW() - 40;
		}

		@Override
		public void extract(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, float seconds) {
			super.extract(g, font, mouseX, mouseY, seconds);
			double value = get.getAsDouble();
			float t = (float) ((value - min) / Math.max(1e-9, max - min));
			t = Math.max(0, Math.min(1, t));
			boolean hot = dragging || inside(mouseX, mouseY, trackX() - 4, y, trackW() + 8, ROW);
			grab = Theme.approach(grab, hot ? 1 : 0, seconds, 18);
			float tx = trackX(), tw = trackW(), cy = y + ROW / 2f;
			Draw.pill(g, tx, cy - 1.5f, tw, 3, 0xFF2A2D38);
			Draw.pill(g, tx, cy - 1.5f, Math.max(3, tw * t), 3, Theme.mix(Theme.ACCENT, Theme.ACCENT_2, t * 0.6f));
			float kx = tx + tw * t;
			if (grab > 0.01f) {
				Draw.circle(g, kx, cy, 7 * grab + 3, Theme.alpha(Theme.ACCENT, 0.25f * grab));
			}
			Draw.circle(g, kx, cy, 4.5f + grab, 0xFFFFFFFF);
			String text = format.apply(value);
			var c = Theme.ui(text);
			g.text(font, c, x + w - font.width(c), y + (ROW - font.lineHeight) / 2 + 1, Theme.mix(Theme.MUTED, Theme.TEXT, grab), false);
		}

		@Override
		public boolean click(double mx, double my) {
			if (inside(mx, my, trackX() - 6, y, trackW() + 12, ROW)) {
				dragging = true;
				drag(mx, my);
				return true;
			}
			return false;
		}

		@Override
		public void drag(double mx, double my) {
			if (!dragging) {
				return;
			}
			double t = Math.max(0, Math.min(1, (mx - trackX()) / Math.max(1, trackW())));
			double v = min + t * (max - min);
			if (step > 0) {
				v = Math.round(v / step) * step;
			}
			set.accept(Math.max(min, Math.min(max, v)));
		}

		@Override
		public void release() {
			dragging = false;
		}
	}

	/**
	 * A choice of a few options. If they fit, they sit side by side and a highlight slides to the chosen one; if not,
	 * arrows step through them.
	 */
	public static final class Choice<T> extends Control {
		private final Supplier<List<T>> options;
		private final java.util.function.Function<T, String> name;
		private final Supplier<T> get;
		private final Consumer<T> set;
		private float slide = -1;
		private boolean segmented;

		public Choice(String label, Supplier<List<T>> options, java.util.function.Function<T, String> name, Supplier<T> get, Consumer<T> set) {
			super(label);
			this.options = options;
			this.name = name;
			this.get = get;
			this.set = set;
		}

		@Override
		public void extract(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, float seconds) {
			super.extract(g, font, mouseX, mouseY, seconds);
			List<T> all = options.get();
			int fx = fieldX(), fw = fieldW(), fy = y + 3, fh = ROW - 6;
			int needed = 4;
			for (T o : all) {
				needed += font.width(Theme.ui(name.apply(o))) + 14;
			}
			segmented = needed <= fw && !all.isEmpty();
			Draw.round(g, fx, fy, fw, fh, 5, Theme.FIELD);
			int index = Math.max(0, all.indexOf(get.get()));
			if (segmented) {
				float cell = (fw - 4) / (float) all.size();
				slide = slide < 0 ? index : Theme.approach(slide, index, seconds, 16);
				Draw.round(g, fx + 2 + slide * cell, fy + 2, cell, fh - 4, 4, Theme.mix(Theme.CARD_HI, Theme.ACCENT, 0.55f));
				for (int i = 0; i < all.size(); i++) {
					var c = Theme.ui(name.apply(all.get(i)));
					float cx = fx + 2 + i * cell + cell / 2f;
					boolean hot = inside(mouseX, mouseY, fx + 2 + i * cell, fy, cell, fh);
					int color = i == index ? Theme.TEXT : hot ? Theme.TEXT : Theme.MUTED;
					g.text(font, c, Math.round(cx - font.width(c) / 2f), fy + (fh - 8) / 2, color, false);
				}
			} else {
				var c = Theme.ui(all.isEmpty() ? "None" : name.apply(all.get(index)));
				g.text(font, c, Math.round(fx + fw / 2f - font.width(c) / 2f), fy + (fh - 8) / 2, Theme.TEXT, false);
				boolean hotLeft = inside(mouseX, mouseY, fx, fy, 18, fh), hotRight = inside(mouseX, mouseY, fx + fw - 18, fy, 18, fh);
				g.text(font, Theme.ui("‹"), fx + 6, fy + (fh - 8) / 2, hotLeft ? Theme.TEXT : Theme.FAINT, false);
				g.text(font, Theme.ui("›"), fx + fw - 10, fy + (fh - 8) / 2, hotRight ? Theme.TEXT : Theme.FAINT, false);
			}
		}

		@Override
		public boolean click(double mx, double my) {
			List<T> all = options.get();
			int fx = fieldX(), fw = fieldW();
			if (all.isEmpty() || !inside(mx, my, fx, y, fw, ROW)) {
				return false;
			}
			int index = Math.max(0, all.indexOf(get.get()));
			if (segmented) {
				float cell = (fw - 4) / (float) all.size();
				int i = (int) Math.floor((mx - fx - 2) / cell);
				set.accept(all.get(Math.max(0, Math.min(all.size() - 1, i))));
			} else {
				int dir = mx < fx + fw / 2.0 ? -1 : 1;
				set.accept(all.get(Math.floorMod(index + dir, all.size())));
			}
			return true;
		}
	}

	/** Color dots in a row; the chosen one has a ring. */
	public static final class Colors extends Control {
		public static final int[] PALETTE = { 0xFFFFFFFF, 0xFF8B6CFF, 0xFF3FD5F2, 0xFF3DDC97, 0xFFFFD84D, 0xFFFF9945, 0xFFFF5C7A, 0xFFFF6BD6,
			0xFF5C8BFF, 0xFF9AA0AC, 0xFF000000 };
		private final IntSupplier get;
		private final IntConsumer set;

		public Colors(String label, IntSupplier get, IntConsumer set) {
			super(label);
			this.get = get;
			this.set = set;
		}

		private float step() {
			return Math.min(15, fieldW() / (float) PALETTE.length);
		}

		@Override
		public void extract(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, float seconds) {
			super.extract(g, font, mouseX, mouseY, seconds);
			float step = step();
			float r = Math.min(5.5f, step / 2f - 1.5f);
			for (int i = 0; i < PALETTE.length; i++) {
				float cx = fieldX() + step * i + step / 2f, cy = y + ROW / 2f;
				boolean chosen = (PALETTE[i] & 0xFFFFFF) == (get.getAsInt() & 0xFFFFFF);
				boolean hot = inside(mouseX, mouseY, cx - step / 2f, y, step, ROW);
				if (chosen) {
					Draw.circle(g, cx, cy, r + 2.5f, 0xFFFFFFFF);
					Draw.circle(g, cx, cy, r + 1.2f, Theme.PANEL | 0xFF000000);
				}
				Draw.circle(g, cx, cy, r + (hot ? 0.8f : 0), PALETTE[i]);
				if ((PALETTE[i] & 0xFFFFFF) == 0) {
					Draw.circle(g, cx, cy, r - 1f, 0xFF16181F);
				}
			}
		}

		@Override
		public boolean click(double mx, double my) {
			float step = step();
			for (int i = 0; i < PALETTE.length; i++) {
				float cx = fieldX() + step * i;
				if (inside(mx, my, cx, y, step, ROW)) {
					set.accept(PALETTE[i]);
					return true;
				}
			}
			return false;
		}
	}

	/** A row with buttons; each runs something when clicked. */
	public static final class Buttons extends Control {
		public record Action(String text, Runnable run) {
		}

		private final List<Action> actions;
		private final float[] hot;

		public Buttons(String label, List<Action> actions) {
			super(label);
			this.actions = actions;
			this.hot = new float[actions.size()];
		}

		// buttons flow left to right and wrap onto another row when the next one doesn't fit
		private float[] bounds(Font font, int i) {
			float start = label.isEmpty() ? x : fieldX();
			float bx = start, row = 0;
			for (int k = 0; k <= i; k++) {
				float bw = font.width(Theme.ui(actions.get(k).text())) + 16;
				if (bx > start && bx + bw > x + w) {
					bx = start;
					row++;
				}
				if (k == i) {
					return new float[] { bx, y + 3 + row * ROW, bw, ROW - 6 };
				}
				bx += bw + 6;
			}
			return new float[] { start, y + 3, 0, 0 };
		}

		@Override
		public int height() {
			if (lastFont == null || actions.isEmpty()) {
				return ROW;
			}
			float[] last = bounds(lastFont, actions.size() - 1);
			return Math.round(last[1] - y - 3) + ROW;
		}

		private Font lastFont;

		@Override
		public void extract(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, float seconds) {
			lastFont = font;
			if (!label.isEmpty()) {
				super.extract(g, font, mouseX, mouseY, seconds);
			}
			for (int i = 0; i < actions.size(); i++) {
				float[] b = bounds(font, i);
				hot[i] = Theme.approach(hot[i], inside(mouseX, mouseY, b[0], b[1], b[2], b[3]) ? 1 : 0, seconds, 18);
				Draw.card(g, b[0], b[1], b[2], b[3], 5, Theme.mix(Theme.CARD_HI, 0xFF2A2D38, hot[i]), Theme.mix(Theme.BORDER_HI, Theme.ACCENT, hot[i]));
				var c = Theme.ui(actions.get(i).text());
				g.text(font, c, Math.round(b[0] + 8), Math.round(b[1] + (b[3] - 8) / 2f), Theme.TEXT, false);
			}
		}

		@Override
		public boolean click(double mx, double my) {
			if (lastFont == null) {
				return false;
			}
			for (int i = 0; i < actions.size(); i++) {
				float[] b = bounds(lastFont, i);
				if (inside(mx, my, b[0], b[1], b[2], b[3])) {
					actions.get(i).run().run();
					return true;
				}
			}
			return false;
		}
	}
}
