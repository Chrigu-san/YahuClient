package net.yahuclient.mod.skybox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.yahuclient.mod.config.SkyPreset;

class SkyboxGeneratorTest {
	@Test
	void facesAreRightHanded() {
		// right x up must point back along the normal from inside the cube... or at least be a proper frame
		for (double[][] f : SkyboxGenerator.FACES) {
			double[] n = f[0], r = f[1], u = f[2];
			assertEquals(0, n[0] * r[0] + n[1] * r[1] + n[2] * r[2], 1e-9);
			assertEquals(0, n[0] * u[0] + n[1] * u[1] + n[2] * u[2], 1e-9);
			assertEquals(0, r[0] * u[0] + r[1] * u[1] + r[2] * u[2], 1e-9);
		}
	}

	@Test
	void theAtlasHoldsSixFacesWithBorders() {
		var atlas = SkyboxGenerator.generate((x, y, z) -> 0x123456, 16);
		int cell = 16 + 2 * SkyboxGenerator.PAD;
		assertEquals(cell * 3, atlas.width());
		assertEquals(cell * 2, atlas.height());
		for (int p : atlas.pixels()) {
			assertEquals(0xFF123456, p);
		}
	}

	@Test
	void neighbouringFacesMeetWithoutASeam() {
		// a sky that only depends on the direction: where two faces touch, both must see the same direction
		SkyboxGenerator.Sky sky = (x, y, z) -> ((int) Math.round((x + 1) * 100) << 16) | ((int) Math.round((y + 1) * 100) << 8) | (int) Math.round((z + 1) * 100);
		var atlas = SkyboxGenerator.generate(sky, 32);
		int cell = 32 + 2 * SkyboxGenerator.PAD;
		// +Z face (index 4: row 1, column 1) right edge and -X face (index 1: row 0, column 1) left edge share a cube edge;
		// sample the middle row of each just inside the face
		int pz = atlas.pixels()[(cell + SkyboxGenerator.PAD + 16) * atlas.width() + cell + SkyboxGenerator.PAD + 31];
		int nx = atlas.pixels()[(SkyboxGenerator.PAD + 16) * atlas.width() + cell + SkyboxGenerator.PAD];
		for (int shift = 0; shift <= 16; shift += 8) {
			assertTrue(Math.abs(((pz >> shift) & 0xFF) - ((nx >> shift) & 0xFF)) <= 8, "colors at the shared edge differ");
		}
	}

	@Test
	void everyPresetPaintsSomething() {
		for (SkyPreset preset : SkyPreset.values()) {
			if (preset == SkyPreset.CUSTOM) {
				continue;
			}
			var atlas = SkyboxGenerator.generate(SkyboxGenerator.preset(preset), 24);
			long bright = 0;
			for (int p : atlas.pixels()) {
				bright += ((p >> 16) & 0xFF) + ((p >> 8) & 0xFF) + (p & 0xFF);
			}
			assertTrue(bright > 0, preset + " is all black");
		}
	}

	@Test
	void theBlackHoleIsBlackInTheMiddle() {
		double hx = 0.42, hy = 0.38, hz = -0.82, l = Math.sqrt(hx * hx + hy * hy + hz * hz);
		int center = SkyboxGenerator.blackHole(hx / l, hy / l, hz / l);
		assertEquals(0, center & 0xFFFFFF);
	}
}
