package net.yahuclient.mod.crosshair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.yahuclient.mod.config.YahuConfig;

class CrosshairGeometryTest {
	private static boolean covers(List<CrosshairGeometry.Rect> rects, int x, int y) {
		return rects.stream().anyMatch(r -> x >= r.x() && x < r.x() + r.w() && y >= r.y() && y < r.y() + r.h());
	}

	@Test
	void thinCrossKeepsTheGapAndIsSymmetric() {
		var rects = CrosshairGeometry.arms(YahuConfig.Arms.CROSS, 4, 1, 2);
		assertFalse(covers(rects, 0, 0));
		assertFalse(covers(rects, 0, -2));
		assertTrue(covers(rects, 0, -3));
		assertTrue(covers(rects, 0, -6));
		assertFalse(covers(rects, 0, -7));
		for (int i = 1; i <= 10; i++) {
			assertEquals(covers(rects, 0, -i), covers(rects, 0, i), "vertical " + i);
			assertEquals(covers(rects, -i, 0), covers(rects, i, 0), "horizontal " + i);
		}
	}

	@Test
	void evenThicknessSitsAroundTheMiddle() {
		var rects = CrosshairGeometry.arms(YahuConfig.Arms.CROSS, 5, 2, 0);
		// a 2px thick arm covers the middle pixel column and the one left of it
		assertTrue(covers(rects, -1, -3));
		assertTrue(covers(rects, 0, -3));
		assertFalse(covers(rects, 1, -3));
	}

	@Test
	void negativeGapOverlapsTheMiddle() {
		var rects = CrosshairGeometry.arms(YahuConfig.Arms.CROSS, 4, 1, -1);
		assertTrue(covers(rects, 0, 0));
	}

	@Test
	void tHasNoTopArmAndNoneHasNothing() {
		var t = CrosshairGeometry.arms(YahuConfig.Arms.T, 4, 1, 1);
		assertFalse(covers(t, 0, -3));
		assertTrue(covers(t, 0, 3));
		assertTrue(CrosshairGeometry.arms(YahuConfig.Arms.NONE, 4, 1, 1).isEmpty());
	}

	@Test
	void dotsCanBeAnySize() {
		// the old crosshair could not make a proper dot; any size works now, odd ones centered on the middle pixel
		for (int size = 1; size <= 20; size++) {
			var dot = CrosshairGeometry.squareDot(size);
			assertEquals(size, dot.w());
			assertEquals(size, dot.h());
			assertTrue(covers(List.of(dot), 0, 0));
		}
		var three = CrosshairGeometry.squareDot(3);
		assertTrue(covers(List.of(three), -1, -1) && covers(List.of(three), 1, 1));
	}

	@Test
	void ringIsHollowAndRound() {
		var ring = CrosshairGeometry.ring(8, 1);
		assertFalse(covers(ring, 0, 0));
		assertTrue(covers(ring, 8, 0));
		assertTrue(covers(ring, 0, -8));
		assertFalse(covers(ring, 4, 0));
	}

	@Test
	void spreadOpensWithMovement() {
		assertEquals(0, CrosshairGeometry.spread(0, 0.3, true, true));
		assertEquals(0, CrosshairGeometry.spread(6, 0, false, false));
		int walk = CrosshairGeometry.spread(6, 0.13, false, false);
		int sprint = CrosshairGeometry.spread(6, 0.28, false, true);
		int jump = CrosshairGeometry.spread(6, 0.28, true, true);
		assertTrue(walk > 0 && sprint > walk && jump > sprint);
	}
}
