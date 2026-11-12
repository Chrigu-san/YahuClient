package net.yahuclient.mod.skybox;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.CompletableFuture;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.dimension.DimensionType;
import net.yahuclient.mod.config.SkyPreset;
import net.yahuclient.mod.config.YahuConfig;
import net.yahuclient.mod.ui.Tex;

/**
 * The custom sky: a cube around the camera with a cube-map texture, drawn instead of the normal sky (see SkyRendererMixin),
 * the same way Minecraft draws the End's sky. The texture is a built-in sky painted by SkyboxGenerator, or a picture of your
 * own from config/yahu/skyboxes (a panorama twice as wide as high, or a cross 4 by 3).
 *
 * Painting a sky takes a moment, so it happens in the background; until it is ready the normal sky shows.
 */
public final class Skybox {
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("yahu", "generated/skybox");
	private static final int FACE = 512;
	private static final long START = System.nanoTime();

	private static String wantedKey = "";
	private static String readyKey = "";
	private static CompletableFuture<SkyboxGenerator.Atlas> painting;
	private static String paintingKey = "";
	private static GpuBuffer cube;
	private static String problem = "";

	private Skybox() {
	}

	public static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve("yahu").resolve("skyboxes");
	}

	/** The pictures in the skybox folder, by file name. */
	public static List<String> customFiles() {
		List<String> files = new ArrayList<>();
		try {
			Files.createDirectories(folder());
			try (DirectoryStream<Path> dir = Files.newDirectoryStream(folder(), "*.{png,PNG,jpg,jpeg}")) {
				for (Path p : dir) {
					files.add(p.getFileName().toString());
				}
			}
		} catch (IOException ignored) {
			// no folder, no files
		}
		files.sort(String.CASE_INSENSITIVE_ORDER);
		return files;
	}

	/** What the menu says about the sky, e.g. that a picture couldn't be read. */
	public static String status(YahuConfig.Skybox config) {
		if (!config.enabled) {
			return "Off";
		}
		if (!problem.isEmpty()) {
			return problem;
		}
		return readyKey.equals(key(config)) ? "Showing: " + label(config) : "Painting the sky...";
	}

	private static String label(YahuConfig.Skybox config) {
		return config.preset == SkyPreset.CUSTOM ? config.customFile : config.preset.label;
	}

	private static String key(YahuConfig.Skybox config) {
		return config.preset == SkyPreset.CUSTOM ? "custom:" + config.customFile : config.preset.name();
	}

	/** Starts painting the wanted sky if it isn't there yet; called every tick. */
	public static void update(YahuConfig.Skybox config) {
		if (!config.enabled) {
			return;
		}
		wantedKey = key(config);
		if (wantedKey.equals(readyKey) || wantedKey.equals(paintingKey)) {
			if (painting != null && painting.isDone() && paintingKey.equals(wantedKey)) {
				upload();
			}
			return;
		}
		paintingKey = wantedKey;
		problem = "";
		SkyPreset preset = config.preset;
		String file = config.customFile;
		painting = CompletableFuture.supplyAsync(() -> {
			if (preset != SkyPreset.CUSTOM) {
				return SkyboxGenerator.generate(SkyboxGenerator.preset(preset), FACE);
			}
			return fromPicture(file);
		});
	}

	private static SkyboxGenerator.Atlas fromPicture(String file) {
		if (file == null || file.isBlank()) {
			throw new IllegalStateException("Pick a picture first: put one into the skybox folder.");
		}
		try (InputStream in = Files.newInputStream(folder().resolve(file)); NativeImage image = NativeImage.read(in)) {
			int w = image.getWidth(), h = image.getHeight();
			int[] argb = new int[w * h];
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					argb[y * w + x] = image.getPixel(x, y);
				}
			}
			SkyboxGenerator.Sky sky = Math.abs(w * 3 - h * 4) <= 4 ? SkyboxGenerator.cross(argb, w, h) : SkyboxGenerator.equirect(argb, w, h);
			int face = Math.max(256, Math.min(1024, Math.abs(w * 3 - h * 4) <= 4 ? w / 4 : w / 4));
			return SkyboxGenerator.generate(sky, face);
		} catch (IOException e) {
			throw new IllegalStateException("Couldn't read " + file + ". Use a PNG panorama (twice as wide as high).");
		}
	}

	// on the render thread: the painted sky becomes the texture and the cube is built to match it
	private static void upload() {
		SkyboxGenerator.Atlas atlas;
		try {
			atlas = painting.join();
		} catch (RuntimeException e) {
			Throwable cause = e.getCause() != null ? e.getCause() : e;
			problem = cause.getMessage() != null ? cause.getMessage() : "The sky couldn't be painted.";
			readyKey = paintingKey;
			painting = null;
			return;
		}
		painting = null;
		NativeImage image = new NativeImage(atlas.width(), atlas.height(), false);
		for (int y = 0; y < atlas.height(); y++) {
			for (int x = 0; x < atlas.width(); x++) {
				image.setPixel(x, y, atlas.pixels()[y * atlas.width() + x]);
			}
		}
		var textures = Minecraft.getInstance().getTextureManager();
		textures.release(TEXTURE);
		textures.register(TEXTURE, new Tex.Smooth("yahu skybox", image, false));
		if (cube != null) {
			cube.close();
		}
		cube = buildCube(atlas);
		readyKey = paintingKey;
	}

	/** The cube: six faces, each mapped to its cell in the atlas (inside the border), drawn from both sides. */
	private static GpuBuffer buildCube(SkyboxGenerator.Atlas atlas) {
		int cell = atlas.face() + 2 * SkyboxGenerator.PAD;
		float size = 100f;
		try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(48 * DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize())) {
			BufferBuilder b = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
			for (int f = 0; f < 6; f++) {
				double[] n = SkyboxGenerator.FACES[f][0], r = SkyboxGenerator.FACES[f][1], u = SkyboxGenerator.FACES[f][2];
				float u0 = ((f % 3) * cell + SkyboxGenerator.PAD) / (float) atlas.width();
				float u1 = ((f % 3) * cell + SkyboxGenerator.PAD + atlas.face()) / (float) atlas.width();
				float v0 = ((f / 3) * cell + SkyboxGenerator.PAD) / (float) atlas.height();
				float v1 = ((f / 3) * cell + SkyboxGenerator.PAD + atlas.face()) / (float) atlas.height();
				float[][] corners = { { -1, 1, u0, v0 }, { 1, 1, u1, v0 }, { 1, -1, u1, v1 }, { -1, -1, u0, v1 } };
				for (int side = 0; side < 2; side++) {
					for (int k = 0; k < 4; k++) {
						float[] c = corners[side == 0 ? k : 3 - k];
						float x = (float) (n[0] + c[0] * r[0] + c[1] * u[0]) * size;
						float y = (float) (n[1] + c[0] * r[1] + c[1] * u[1]) * size;
						float z = (float) (n[2] + c[0] * r[2] + c[1] * u[2]) * size;
						b.addVertex(x, y, z).setUv(c[2], c[3]).setColor(0xFFFFFFFF);
					}
				}
			}
			try (MeshData mesh = b.buildOrThrow()) {
				return RenderSystem.getDevice().createBuffer(() -> "Yahu skybox", 40, mesh.vertexBuffer());
			}
		}
	}

	// the menu's preview: a small panorama strip of the chosen sky, painted in the background too
	private static final Identifier PREVIEW = Identifier.fromNamespaceAndPath("yahu", "generated/sky_preview");
	private static final int PREVIEW_W = 320, PREVIEW_H = 80;
	private static String previewKey = "";
	private static String previewWanted = "";
	private static CompletableFuture<int[]> previewPainting;

	/** Draws the chosen sky as a strip around the horizon (all the way round, from a little below to high up). */
	public static void drawPreview(net.minecraft.client.gui.GuiGraphicsExtractor g, YahuConfig.Skybox config, int x, int y, int w, int h) {
		String key = key(config);
		if (!key.equals(previewWanted)) {
			previewWanted = key;
			SkyPreset preset = config.preset;
			String file = config.customFile;
			previewPainting = CompletableFuture.supplyAsync(() -> {
				SkyboxGenerator.Sky sky = preset == SkyPreset.CUSTOM ? pictureSky(file) : SkyboxGenerator.preset(preset);
				int[] px = new int[PREVIEW_W * PREVIEW_H];
				for (int j = 0; j < PREVIEW_H; j++) {
					double lat = Math.toRadians(62 - j * 80.0 / PREVIEW_H);
					for (int i = 0; i < PREVIEW_W; i++) {
						double lon = Math.toRadians(i * 360.0 / PREVIEW_W) + Math.PI * 0.75;
						double cx = Math.cos(lat) * Math.sin(lon), cy = Math.sin(lat), cz = -Math.cos(lat) * Math.cos(lon);
						px[j * PREVIEW_W + i] = 0xFF000000 | sky.color(cx, cy, cz);
					}
				}
				return px;
			});
		}
		if (previewPainting != null && previewPainting.isDone() && !previewKey.equals(previewWanted)) {
			try {
				int[] px = previewPainting.join();
				NativeImage image = new NativeImage(PREVIEW_W, PREVIEW_H, false);
				for (int j = 0; j < PREVIEW_H; j++) {
					for (int i = 0; i < PREVIEW_W; i++) {
						image.setPixel(i, j, px[j * PREVIEW_W + i]);
					}
				}
				var textures = Minecraft.getInstance().getTextureManager();
				textures.release(PREVIEW);
				textures.register(PREVIEW, new Tex.Smooth("yahu sky preview", image, false));
			} catch (RuntimeException e) {
				// a picture that can't be read: no preview
			}
			previewKey = previewWanted;
		}
		if (previewKey.equals(key)) {
			// the strip drifts slowly, so it reads as a view all the way round
			float shift = (float) ((System.nanoTime() / 1e9 * 6) % PREVIEW_W);
			int srcW = Math.round(PREVIEW_W * Math.min(1f, w / (float) h * PREVIEW_H / PREVIEW_W));
			g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, PREVIEW, x, y, shift, 0, w, h, srcW, PREVIEW_H, PREVIEW_W, PREVIEW_H, 0xFFFFFFFF);
		} else {
			g.fill(x, y, x + w, y + h, 0xFF0D0E13);
		}
	}

	private static SkyboxGenerator.Sky pictureSky(String file) {
		try (InputStream in = Files.newInputStream(folder().resolve(file)); NativeImage image = NativeImage.read(in)) {
			int w = image.getWidth(), h = image.getHeight();
			int[] argb = new int[w * h];
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					argb[y * w + x] = image.getPixel(x, y);
				}
			}
			return Math.abs(w * 3 - h * 4) <= 4 ? SkyboxGenerator.cross(argb, w, h) : SkyboxGenerator.equirect(argb, w, h);
		} catch (IOException | RuntimeException e) {
			return (cx, cy, cz) -> 0x0D0E13;
		}
	}

	/** Whether to draw the custom sky instead of the normal one this frame. */
	private static volatile long lastDrawn;

	/** The custom sky was just drawn (the normal sky was replaced). */
	public static void markDrawn() {
		lastDrawn = System.nanoTime();
	}

	/** Clouds stay away while a custom sky is up and the player turned them off. */
	public static boolean hideClouds(YahuConfig.Skybox config) {
		return config.enabled && config.hideClouds && System.nanoTime() - lastDrawn < 500_000_000L;
	}

	public static boolean shouldDraw(YahuConfig.Skybox config, SkyRenderState state) {
		if (!config.enabled || cube == null || !readyKey.equals(wantedKey) || !problem.isEmpty()) {
			return false;
		}
		if (config.overworldOnly) {
			return state.skybox == DimensionType.Skybox.OVERWORLD;
		}
		return state.skybox != DimensionType.Skybox.NONE;
	}

	public static void render(YahuConfig.Skybox config, SkyRenderState state, RenderTarget target) {
		var texture = Minecraft.getInstance().getTextureManager().getTexture(TEXTURE);
		float brightness = Math.max(0, config.brightness / 100f);
		if (config.followDayNight && state.skyColor != null) {
			// the normal sky's color says how bright it is out; scale ours by it, never quite to black
			float day = Math.max(state.skyColor.x(), Math.max(state.skyColor.y(), state.skyColor.z()));
			brightness *= Math.max(0.12f, Math.min(1f, day / 0.75f));
		}
		double minutes = (System.nanoTime() - START) / 60e9;
		Matrix4f view = RenderSystem.getModelViewMatrixCopy().rotateY((float) Math.toRadians(minutes * config.rotationSpeed));
		var transforms = RenderSystem.getDynamicUniforms().writeTransform(view, new Vector4f(brightness, brightness, brightness, 1f));
		var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
		var indexBuffer = indices.getBuffer(72);
		try (RenderPass pass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(() -> "Yahu sky", target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
			RenderSystem.bindDefaultUniforms(pass);
			pass.setPipeline(RenderSystem.getCompiledPipeline(RenderPipelines.END_SKY));
			pass.setUniform("DynamicTransforms", transforms);
			pass.setUniform("Sampler0", texture.getTextureView(), texture.getSampler());
			pass.setVertexBuffer(0, cube.slice());
			pass.setIndexBuffer(indexBuffer, indices.type());
			pass.drawIndexed(72, 1, 0, 0, 0);
		}
	}
}
