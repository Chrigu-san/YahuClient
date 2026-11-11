package net.yahuclient.mod.ui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.GameRenderer;

/**
 * Real frosted glass: only what is behind a pane is blurred.
 *
 * Minecraft blurs the whole frame once when a menu opens (GameRenderer.processBlurEffect). Around that, the frame as it
 * should stay visible is saved (sharp in a world; on the title screen the lightly blurred panorama), the blur runs a
 * second time for a heavy frost and that result is kept in a texture of its own, then the saved frame is put back. Each
 * pane then shows exactly the part of the frosted frame that lies behind it, cut to its rounded shape.
 */
public final class Backdrop {
	private static GpuTexture saved;
	private static GpuTexture frosted;
	private static GpuTextureView frostedView;
	private static final net.minecraft.resources.Identifier ID = net.minecraft.resources.Identifier.fromNamespaceAndPath("yahu", "generated/backdrop");
	private static Holder holder;
	private static int width, height;
	private static boolean running;
	private static boolean blurThisFrame;
	/** The frame is upside down in its texture on some graphics backends: found out once from the API's convention. */
	private static final boolean FLIP = Boolean.parseBoolean(System.getProperty("yahu.backdropFlip", "true"));

	private Backdrop() {
	}

	/** A screen starts drawing: no blur yet in this frame. */
	public static void newFrame() {
		blurThisFrame = false;
	}

	/** Minecraft will blur this frame. */
	public static void markBlur() {
		blurThisFrame = true;
	}

	/** Whether panes can show the frosted backdrop in this frame. */
	public static boolean active() {
		return blurThisFrame && ready();
	}

	/** Makes the textures for the current frame size; false when there is no blur to show (menu blur set to 0). */
	public static boolean ready() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.getMenuBackgroundBlurriness() < 1) {
			return false;
		}
		RenderTarget main = mc.gameRenderer.mainRenderTarget();
		if (main.getColorTexture() == null) {
			return false;
		}
		if (frosted == null || width != main.width || height != main.height) {
			close();
			var device = RenderSystem.getDevice();
			var format = main.getColorTexture().getFormat();
			width = main.width;
			height = main.height;
			saved = device.createTexture("yahu backdrop saved", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC, format, width, height, 1, 1);
			frosted = device.createTexture("yahu backdrop frosted", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT, format, width, height, 1, 1);
			frostedView = device.createTextureView(frosted);
			device.createCommandEncoder().clearColorTexture(frosted, new org.joml.Vector4f(0, 0, 0, 0));
			if (holder == null) {
				holder = new Holder();
				mc.getTextureManager().register(ID, holder);
			}
			holder.set(frosted, frostedView);
		}
		return true;
	}

	private static void close() {
		if (frostedView != null) {
			frostedView.close();
		}
		if (frosted != null) {
			frosted.close();
		}
		if (saved != null) {
			saved.close();
		}
		frostedView = null;
		frosted = null;
		saved = null;
	}

	private static boolean usable(RenderTarget main) {
		return saved != null && !saved.isClosed() && main.getColorTexture() != null && main.width == width && main.height == height;
	}

	/** Before Minecraft's blur: in a world the sharp game is what stays visible around the panes. */
	public static void beforeBlur(RenderTarget main) {
		if (running || !usable(main)) {
			return;
		}
		if (Minecraft.getInstance().level != null) {
			copy(main.getColorTexture(), saved);
		}
	}

	/** After Minecraft's blur: frost again for the panes, keep that, and put the frame that stays visible back. */
	public static void afterBlur(GameRenderer renderer, RenderTarget main) {
		if (running || !usable(main)) {
			return;
		}
		boolean inWorld = Minecraft.getInstance().level != null;
		if (!inWorld) {
			// on the title screen the softly blurred panorama stays around the panes
			copy(main.getColorTexture(), saved);
		}
		running = true;
		try {
			renderer.processBlurEffect();
			renderer.processBlurEffect();
		} finally {
			running = false;
		}
		copy(main.getColorTexture(), frosted);
		copy(saved, main.getColorTexture());
	}

	private static void copy(GpuTexture from, GpuTexture to) {
		RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(from, to, 0, 0, 0, 0, 0, width, height);
	}

	/**
	 * The frosted frame behind a rounded rectangle (GUI units). The corners are cut row by row in screen pixels; the pane's
	 * outline goes over the edge and smooths it.
	 */
	public static void draw(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius) {
		if (!active()) {
			return;
		}
		float s = Draw.scale();
		int x0 = Math.round(x * s), y0 = Math.round(y * s), x1 = Math.round((x + w) * s), y1 = Math.round((y + h) * s);
		if (x1 <= x0 || y1 <= y0) {
			return;
		}
		// screen pixels to frame pixels (the GUI can be drawn translated: take the pose's offset into account)
		var pose = g.pose();
		float ox = pose.m20() * s, oy = pose.m21() * s;
		int r = Math.round(Math.max(0, Math.min(radius * s, Math.min(x1 - x0, y1 - y0) / 2f)));
		g.pose().pushMatrix();
		g.pose().scale(1f / s, 1f / s);
		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			int inset = (int) Math.round(r - Math.sqrt(Math.max(0, (double) r * r - dy * dy)));
			row(g, x0 + inset, x1 - inset, y0 + i, y0 + i + 1, ox, oy);
			row(g, x0 + inset, x1 - inset, y1 - i - 1, y1 - i, ox, oy);
		}
		row(g, x0, x1, y0 + r, y1 - r, ox, oy);
		g.pose().popMatrix();
	}

	private static void row(GuiGraphicsExtractor g, int x0, int x1, int y0, int y1, float ox, float oy) {
		if (x1 <= x0 || y1 <= y0) {
			return;
		}
		// opaque: the frame's alpha channel is not meant for showing (it is zero in places)
		int w = x1 - x0, h = y1 - y0;
		float u = x0 + ox, v = y0 + oy;
		if (FLIP) {
			g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_OPAQUE_TEXTURED_BACKGROUND, ID, x0, y0, u, height - v, w, h, w, -h, width, height);
		} else {
			g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_OPAQUE_TEXTURED_BACKGROUND, ID, x0, y0, u, v, w, h, w, h, width, height);
		}
	}

	/** The frosted frame as a texture the GUI can draw by name, sampled smoothly. */
	private static final class Holder extends net.minecraft.client.renderer.texture.AbstractTexture {
		Holder() {
			sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		}

		void set(GpuTexture texture, GpuTextureView view) {
			this.texture = texture;
			this.textureView = view;
		}

		@Override
		public void close() {
			// the textures belong to Backdrop
		}
	}
}
