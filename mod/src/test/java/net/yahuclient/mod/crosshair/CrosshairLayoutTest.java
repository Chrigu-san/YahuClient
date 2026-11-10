package net.yahuclient.mod.crosshair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.yahuclient.mod.config.CrosshairShape;
import net.yahuclient.mod.config.YahuConfig;

class CrosshairLayoutTest {
	private static YahuConfig.Crosshair config(CrosshairShape shape, int size, int gap, int thickness) {
		YahuConfig.Crosshair c = new YahuConfig.Crosshair();
		c.shape = shape;
		c.size = size;
		c.gap = gap;
		c.thickness = thickness;
		c.dot = false;
		return c;
	}

	private static boolean covers(List<CrosshairLayout.Rect> rects, int x, int y) {
		return rects.stream().anyMatch(r -> x >= r.x() && x < r.x() + r.w() && y >= r.y() && y < r.y() + r.h());
	}

	@Test
	void crossKeepsTheGapFree() {
		List<CrosshairLayout.Rect> rects = CrosshairLayout.of(config(CrosshairShape.CROSS, 4, 2, 1));
		assertEquals(4, rects.size());
		assertFalse(covers(rects, 0, 0));
		assertFalse(covers(rects, 0, -2));
		assertTrue(covers(rects, 0, -3));
		assertTrue(covers(rects, 0, -6));
		assertFalse(covers(rects, 0, -7));
		assertTrue(covers(rects, 3, 0));
		assertTrue(covers(rects, -6, 0));
	}

	@Test
	void crossIsSymmetric() {
		List<CrosshairLayout.Rect> rects = CrosshairLayout.of(config(CrosshairShape.CROSS, 5, 3, 1));
		for (int i = 1; i <= 10; i++) {
			assertEquals(covers(rects, 0, -i), covers(rects, 0, i), "vertical at " + i);
			assertEquals(covers(rects, -i, 0), covers(rects, i, 0), "horizontal at " + i);
		}
	}

	@Test
	void tHasNoTopArm() {
		List<CrosshairLayout.Rect> rects = CrosshairLayout.of(config(CrosshairShape.T, 4, 1, 1));
		assertFalse(covers(rects, 0, -3));
		assertTrue(covers(rects, 0, 3));
	}

	@Test
	void circleIsARing() {
		List<CrosshairLayout.Rect> rects = CrosshairLayout.of(config(CrosshairShape.CIRCLE, 4, 2, 1));
		assertFalse(covers(rects, 0, 0));
		assertTrue(covers(rects, 6, 0));
		assertTrue(covers(rects, 0, -6));
		assertFalse(covers(rects, 3, 0));
	}

	@Test
	void boxHasAHollowMiddle() {
		List<CrosshairLayout.Rect> rects = CrosshairLayout.of(config(CrosshairShape.BOX, 3, 1, 1));
		assertTrue(covers(rects, -4, -4));
		assertTrue(covers(rects, 4, 4));
		assertFalse(covers(rects, 0, 0));
	}

	@Test
	void centerDotOnRequest() {
		YahuConfig.Crosshair c = config(CrosshairShape.CROSS, 4, 2, 1);
		c.dot = true;
		assertTrue(covers(CrosshairLayout.of(c), 0, 0));
	}
}
