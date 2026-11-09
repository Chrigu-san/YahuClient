package net.yahuclient.mod.ui;

import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * One setting in the Yahu menu: a label on the left, the control on the right. The screen places it, draws it and passes the
 * mouse on; every change is written straight into the config and saved by the screen.
 */
public abstract class Control {
	public static final int HEIGHT = 18;

	protected final String label;
	protected int x, y, w;

	protected Control(String label) {
		this.label = label;
	}

	public void place(int x, int y, int w) {
		this.x = x;
		this.y = y;
		this.w = w;
	}

	public int height() {
		return HEIGHT;
	}

	public void extract(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
		g.text(font, label, x, y + (HEIGHT - font.lineHeight) / 2 + 1, YahuUi.MUTED, false);
	}

	/** True if the click was used. */
	public boolean click(double mouseX, double mouseY) {
		return false;
	}

	public void drag(double mouseX) {
	}

	public void release() {
	}

	protected boolean inside(double mx, double my, int bx, int by, int bw, int bh) {
		return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
	}

	/** The right half of the row, where the control itself sits. */
	protected int fieldX() {
		return x + w / 2;
	}

	protected int fieldW() {
		return w - w / 2;
	}

	/** An on/off switch: a sunk track with a block that slides to the right and turns green when on. */
	public static final class Toggle extends Control {
		private final Supplier<Boolean> get;
		private final Consumer<Boolean> set;

		public Toggle(String label, Supplier<Boolean> get, Consumer<Boolean> set) {
			super(label);
			this.get = get;
			this.set = set;
		}

		@Override
		public void extract(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
			super.extract(g, font, mouseX, mouseY);
			boolean on = get.get();
			int tx = x + w - 30, ty = y + 3;
			YahuUi.sunk(g, tx, ty, 30, 12, on ? YahuUi.mix(YahuUi.INK, YahuUi.GRASS, 0.35f) : YahuUi.INK);
			int knob = on ? tx + 16 : tx + 2;
			YahuUi.block(g, knob, ty + 2, 12, 8, on ? YahuUi.GRASS : YahuUi.LINE_HI);
		}

		@Override
		public boolean click(double mx, double my) {
			if (inside(mx, my, x, y, w, HEIGHT)) {
				set.accept(!get.get());
				return true;
			}
			return false;
		}
	}

	/** A slider for whole numbers: a sunk track filled in grass up to the value, with a block handle. */
	public static final class Slider extends Control {
		private final int min, max;
		private final IntSupplier get;
		private final IntConsumer set;
		private final String unit;
		private boolean dragging;

		public Slider(String label, int min, int max, String unit, IntSupplier get, IntConsumer set) {
			super(label);
			this.min = min;
			this.max = max;
			this.unit = unit;
			this.get = get;
			this.set = set;
		}

		private int trackX() {
			return fieldX();
		}

		private int trackW() {
			return fieldW() - 28;
		}

		@Override
		public void extract(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
			super.extract(g, font, mouseX, mouseY);
			int value = get.getAsInt();
			float t = max == min ? 0 : (value - min) / (float) (max - min);
			int tx = trackX(), tw = trackW(), ty = y + 7;
			YahuUi.sunk(g, tx, ty, tw, 5, YahuUi.INK);
			g.fill(tx + 1, ty + 1, tx + 1 + Math.round((tw - 2) * t), ty + 4, YahuUi.GRASS);
			int hx = tx + Math.round((tw - 6) * t);
			boolean hot = dragging || inside(mouseX, mouseY, tx, y, tw, HEIGHT);
			YahuUi.block(g, hx, y + 3, 6, 12, hot ? YahuUi.GRASS_HI : YahuUi.TEXT);
			String text = value + unit;
			g.text(font, text, x + w - font.width(text), y + (HEIGHT - font.lineHeight) / 2 + 1, YahuUi.TEXT, false);
		}

		@Override
		public boolean click(double mx, double my) {
			if (inside(mx, my, trackX() - 3, y, trackW() + 6, HEIGHT)) {
				dragging = true;
				drag(mx);
				return true;
			}
			return false;
		}

		@Override
		public void drag(double mx) {
			if (dragging) {
				float t = (float) ((mx - trackX() - 3) / Math.max(1, trackW() - 6));
				t = Math.max(0, Math.min(1, t));
				set.accept(min + Math.round(t * (max - min)));
			}
		}

		@Override
		public void release() {
			dragging = false;
		}
	}

	/** Steps through a fixed list of choices: the current one in a block button, a click moves to the next. */
	public static final class Cycle extends Control {
		private final Supplier<String> current;
		private final Runnable next;

		public Cycle(String label, Supplier<String> current, Runnable next) {
			super(label);
			this.current = current;
			this.next = next;
		}

		@Override
		public void extract(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
			super.extract(g, font, mouseX, mouseY);
			int bx = fieldX(), bw = fieldW();
			boolean hot = inside(mouseX, mouseY, bx, y + 1, bw, HEIGHT - 2);
			YahuUi.block(g, bx, y + 1, bw, HEIGHT - 2, hot ? YahuUi.LINE_HI : YahuUi.RAISED_HI);
			g.centeredText(font, current.get() + "  >", bx + bw / 2, y + (HEIGHT - font.lineHeight) / 2 + 1, YahuUi.TEXT);
		}

		@Override
		public boolean click(double mx, double my) {
			if (inside(mx, my, fieldX(), y + 1, fieldW(), HEIGHT - 2)) {
				next.run();
				return true;
			}
			return false;
		}
	}

	/** A row of color blocks; the chosen one gets a white frame. */
	public static final class Swatches extends Control {
		private final IntSupplier get;
		private final IntConsumer set;

		public Swatches(String label, IntSupplier get, IntConsumer set) {
			super(label);
			this.get = get;
			this.set = set;
		}

		private int size() {
			return Math.min(14, (fieldW() + 2) / YahuUi.SWATCHES.length - 2);
		}

		@Override
		public void extract(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
			super.extract(g, font, mouseX, mouseY);
			int s = size();
			for (int i = 0; i < YahuUi.SWATCHES.length; i++) {
				int sx = fieldX() + i * (s + 2), sy = y + (HEIGHT - s) / 2;
				int color = YahuUi.SWATCHES[i];
				if ((color & 0xFFFFFF) == (get.getAsInt() & 0xFFFFFF)) {
					g.fill(sx - 2, sy - 2, sx + s + 2, sy + s + 2, YahuUi.TEXT);
				}
				YahuUi.block(g, sx, sy, s, s, color);
			}
		}

		@Override
		public boolean click(double mx, double my) {
			int s = size();
			for (int i = 0; i < YahuUi.SWATCHES.length; i++) {
				int sx = fieldX() + i * (s + 2), sy = y + (HEIGHT - s) / 2;
				if (inside(mx, my, sx - 1, sy - 1, s + 2, s + 2)) {
					set.accept(YahuUi.SWATCHES[i]);
					return true;
				}
			}
			return false;
		}
	}
}
