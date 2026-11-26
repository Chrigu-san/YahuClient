package net.yahuclient.mod.ui;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.yahuclient.mod.packs.Modrinth;

/** The two controls of the Packs page: a search field and the list of results with an install button each. */
public final class PackControls {
	private PackControls() {
	}

	/** The text in the menu's font, cut with an ellipsis so it fits the width. */
	static net.minecraft.network.chat.MutableComponent clip(Font font, String text, int width) {
		String t = text.replace("\n", " ");
		if (font.width(Theme.ui(t)) <= width) {
			return Theme.ui(t);
		}
		while (t.length() > 1 && font.width(Theme.ui(t + "...")) > width) {
			t = t.substring(0, t.length() - 1);
		}
		return Theme.ui(t + "...");
	}

	/** A text field. Click it to type; Enter (or leaving it) hands the text on. */
	public static final class Search extends Control {
		private final Supplier<String> get;
		private final Consumer<String> set;
		private final Runnable submit;
		private boolean focused;

		public Search(String label, Supplier<String> get, Consumer<String> set, Runnable submit) {
			super(label);
			this.get = get;
			this.set = set;
			this.submit = submit;
		}

		@Override
		public void extract(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, float seconds) {
			super.extract(g, font, mouseX, mouseY, seconds);
			int fx = fieldX(), fw = fieldW(), fy = y + 3, fh = ROW - 6;
			Glass.pane(g, fx, fy, fw, fh, 6, Glass.FILL_DARK, focused ? Theme.ACCENT_HI : Glass.EDGE, 1f);
			String text = get.get();
			boolean caret = focused && (System.currentTimeMillis() / 500) % 2 == 0;
			String shown = text.isEmpty() && !focused ? "Type to search" : text + (caret ? "|" : "");
			var clipped = clip(font, shown, fw - 12);
			g.text(font, clipped, fx + 6, fy + (fh - 8) / 2, text.isEmpty() && !focused ? Theme.FAINT : Theme.TEXT, false);
		}

		@Override
		public boolean click(double mx, double my) {
			boolean in = inside(mx, my, fieldX(), y + 3, fieldW(), ROW - 6);
			if (focused && !in) {
				focused = false;
			}
			if (in) {
				focused = true;
			}
			return in;
		}

		/** Whether this field takes the keyboard. */
		public boolean focused() {
			return focused;
		}

		public boolean typed(int codepoint) {
			if (!focused || codepoint < 32 || codepoint == 127 || get.get().length() >= 60) {
				return false;
			}
			set.accept(get.get() + Character.toString(codepoint));
			return true;
		}

		public boolean key(int key) {
			if (!focused) {
				return false;
			}
			if (key == InputConstants.KEY_BACKSPACE) {
				String t = get.get();
				if (!t.isEmpty()) {
					set.accept(t.substring(0, t.offsetByCodePoints(t.length(), -1)));
				}
				return true;
			}
			if (key == InputConstants.KEY_ESCAPE) {
				focused = false;
				return true;
			}
			if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
				focused = false;
				submit.run();
				return true;
			}
			return false;
		}
	}

	/** The results: a card per pack with its name, author, downloads, a line about it and the install button. */
	public static final class Results extends Control {
		private static final int CARD = 46, GAP = 4, BUTTON_W = 62;

		private final Supplier<List<Modrinth.Hit>> hits;
		private final Supplier<String> status;
		private final Consumer<Modrinth.Hit> install;
		private float[] hot = new float[0];

		public Results(Supplier<List<Modrinth.Hit>> hits, Supplier<String> status, Consumer<Modrinth.Hit> install) {
			super("");
			this.hits = hits;
			this.status = status;
			this.install = install;
		}

		@Override
		public int height() {
			List<Modrinth.Hit> list = hits.get();
			return list.isEmpty() ? 22 : list.size() * (CARD + GAP);
		}

		private int cardY(int i) {
			return y + i * (CARD + GAP);
		}

		@Override
		public void extract(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, float seconds) {
			List<Modrinth.Hit> list = hits.get();
			if (list.isEmpty()) {
				g.text(font, Theme.ui(status.get()), x, y + 6, Theme.FAINT, false);
				return;
			}
			if (hot.length != list.size()) {
				hot = new float[list.size()];
			}
			for (int i = 0; i < list.size(); i++) {
				Modrinth.Hit hit = list.get(i);
				int cy = cardY(i);
				Glass.pane(g, x, cy, w, CARD, 4, 0x2814181F, 0x1CFFFFFF, 1f);

				int textW = w - BUTTON_W - 22;
				g.text(font, clip(font, hit.title(), textW), x + 8, cy + 6, Theme.TEXT, false);
				String meta = "by " + hit.author() + "  -  " + compact(hit.downloads()) + " downloads";
				g.text(font, clip(font, meta, textW), x + 8, cy + 18, Theme.MUTED, false);
				g.text(font, clip(font, hit.description(), textW), x + 8, cy + 31, Theme.FAINT, false);

				// the install button on the right: its state says what is happening
				Modrinth.Progress p = Modrinth.progress(hit.id());
				String label = p == null ? "Install" : switch (p.state()) {
					case INSTALLING -> "...";
					case DONE -> "Installed";
					case FAILED -> "Retry";
				};
				int bx = x + w - BUTTON_W - 8, by = cy + (CARD - 18) / 2;
				boolean over = inside(mouseX, mouseY, bx, by, BUTTON_W, 18) && (p == null || p.state() != Modrinth.State.INSTALLING);
				hot[i] = Theme.approach(hot[i], over ? 1 : 0, seconds, 18);
				boolean done = p != null && p.state() == Modrinth.State.DONE;
				Glass.pane(g, bx, by, BUTTON_W, 18, 6, Theme.mix(done ? 0x3040C080 : Glass.FILL, Glass.FILL_HOT, hot[i]), Theme.mix(done ? 0x6040C080 : Glass.EDGE, Glass.EDGE_HOT, hot[i]), 1f);
				var text = Theme.ui(label);
				g.text(font, text, Math.round(bx + BUTTON_W / 2f - font.width(text) / 2f), by + 5, done ? Theme.GOOD : Theme.TEXT, false);
				if (p != null && p.state() == Modrinth.State.FAILED) {
					g.text(font, clip(font, p.note(), BUTTON_W + 8), bx - 2, by + 20, Theme.FAINT, false);
				}
			}
		}

		@Override
		public boolean click(double mx, double my) {
			List<Modrinth.Hit> list = hits.get();
			for (int i = 0; i < list.size(); i++) {
				int bx = x + w - BUTTON_W - 8, by = cardY(i) + (CARD - 18) / 2;
				if (inside(mx, my, bx, by, BUTTON_W, 18)) {
					Modrinth.Progress p = Modrinth.progress(list.get(i).id());
					if (p == null || p.state() == Modrinth.State.FAILED) {
						install.accept(list.get(i));
					}
					return true;
				}
			}
			return false;
		}

		static String compact(int n) {
			if (n >= 1_000_000) {
				return String.format("%.1fM", n / 1_000_000f);
			}
			if (n >= 1000) {
				return String.format("%.1fk", n / 1000f);
			}
			return Integer.toString(n);
		}
	}
}
