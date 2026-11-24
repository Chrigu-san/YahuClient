package net.yahuclient.mod.ui;

import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.resources.Identifier;

/**
 * Real perspective for tilting glass. Minecraft's GUI is flat (every element is a rectangle under a 2D transform), so a
 * button can't lean back on its own. While a warp is active, Draw hands its shapes here instead: each corner is turned in
 * 3D around the element's middle (rotated about X and Y, pushed back), projected with a perspective, and the four points
 * go to the GUI as an element of our own. Straight edges stay straight, the far side gets smaller, like the real thing.
 */
public final class Warp {
	private static @Nullable Warp current;

	private final float cx, cy, cosX, sinX, cosY, sinY, push, distance;
	private final Matrix3x2f pose;

	private Warp(GuiGraphicsExtractor g, float cx, float cy, float rxDeg, float ryDeg, float push, float distance) {
		this.cx = cx;
		this.cy = cy;
		double rx = Math.toRadians(rxDeg), ry = Math.toRadians(ryDeg);
		this.cosX = (float) Math.cos(rx);
		this.sinX = (float) Math.sin(rx);
		this.cosY = (float) Math.cos(ry);
		this.sinY = (float) Math.sin(ry);
		this.push = push;
		this.distance = distance;
		this.pose = new Matrix3x2f(g.pose());
	}

	/**
	 * Starts drawing tilted around (cx, cy): rx leans the top back (positive) or forward, ry leans the right side back,
	 * push moves the whole thing away (negative values are further away), all in GUI units.
	 */
	public static void begin(GuiGraphicsExtractor g, float cx, float cy, float rxDeg, float ryDeg, float push) {
		current = new Warp(g, cx, cy, rxDeg, ryDeg, push, 420f);
	}

	public static void end() {
		current = null;
	}

	public static @Nullable Warp current() {
		return current;
	}

	/** Runs something flat (no warp), for text and other things placed with project(). */
	public static void flat(Runnable r) {
		Warp w = current;
		current = null;
		try {
			r.run();
		} finally {
			current = w;
		}
	}

	/** Where a point lands: x, y and how much it shrank with distance there. */
	public float[] project(float x, float y) {
		float lx = x - cx, ly = y - cy;
		// lean the right side back (about Y), then the top back (about X)
		float x1 = lx * cosY;
		float z1 = -lx * sinY;
		float y2 = ly * cosX;
		float z2 = z1 + ly * sinX + push;
		float s = distance / (distance - z2);
		return new float[] {cx + x1 * s, cy + y2 * s, s};
	}

	// --- shapes ---

	/** A solid quad, top and bottom color (a vertical gradient when they differ). */
	public void quad(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int top, int bottom) {
		if (x1 <= x0 || y1 <= y0) {
			return;
		}
		float[] a = project(x0, y0), b = project(x0, y1), c = project(x1, y1), d = project(x1, y0);
		g.guiRenderState.addGuiElement(new Quad(RenderPipelines.GUI, TextureSetup.noTexture(), pose, a, b, c, d, 0, 0, 0, 0, top, bottom, false,
				g.scissorStack.peek()));
	}

	/** A textured quad (u, v from 0 to 1 across the texture). */
	public void texture(GuiGraphicsExtractor g, Identifier texture, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1,
			int color) {
		if (x1 <= x0 || y1 <= y0) {
			return;
		}
		var tex = Minecraft.getInstance().getTextureManager().getTexture(texture);
		float[] a = project(x0, y0), b = project(x0, y1), c = project(x1, y1), d = project(x1, y0);
		g.guiRenderState.addGuiElement(new Quad(RenderPipelines.GUI_TEXTURED, TextureSetup.singleTexture(tex.getTextureView(), tex.getSampler()), pose, a,
				b, c, d, u0, v0, u1, v1, color, color, true, g.scissorStack.peek()));
	}

	/** Our own GUI element: a quad with four free corners (they come in projected). */
	private record Quad(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2fc pose, float[] a, float[] b, float[] c, float[] d, float u0,
			float v0, float u1, float v1, int top, int bottom, boolean textured, @Nullable ScreenRectangle scissorArea)
			implements GuiElementRenderState {
		@Override
		public void buildVertices(VertexConsumer out) {
			if (textured) {
				out.addVertexWith2DPose(pose, a[0], a[1]).setUv(u0, v0).setColor(top);
				out.addVertexWith2DPose(pose, b[0], b[1]).setUv(u0, v1).setColor(bottom);
				out.addVertexWith2DPose(pose, c[0], c[1]).setUv(u1, v1).setColor(bottom);
				out.addVertexWith2DPose(pose, d[0], d[1]).setUv(u1, v0).setColor(top);
			} else {
				out.addVertexWith2DPose(pose, a[0], a[1]).setColor(top);
				out.addVertexWith2DPose(pose, b[0], b[1]).setColor(bottom);
				out.addVertexWith2DPose(pose, c[0], c[1]).setColor(bottom);
				out.addVertexWith2DPose(pose, d[0], d[1]).setColor(top);
			}
		}

		@Override
		public @Nullable ScreenRectangle bounds() {
			float minX = Math.min(Math.min(a[0], b[0]), Math.min(c[0], d[0])), maxX = Math.max(Math.max(a[0], b[0]), Math.max(c[0], d[0]));
			float minY = Math.min(Math.min(a[1], b[1]), Math.min(c[1], d[1])), maxY = Math.max(Math.max(a[1], b[1]), Math.max(c[1], d[1]));
			int x = (int) Math.floor(minX), y = (int) Math.floor(minY);
			ScreenRectangle r = new ScreenRectangle(x, y, (int) Math.ceil(maxX) - x + 1, (int) Math.ceil(maxY) - y + 1).transformMaxBounds(pose);
			return scissorArea != null ? scissorArea.intersection(r) : r;
		}
	}
}
