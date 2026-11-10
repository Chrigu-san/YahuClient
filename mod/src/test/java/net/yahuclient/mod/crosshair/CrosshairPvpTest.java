package net.yahuclient.mod.crosshair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CrosshairPvpTest {
	private static CrosshairRenderer.State moving(double speed, boolean inAir, boolean sprinting) {
		return new CrosshairRenderer.State(false, Long.MAX_VALUE, 1f, speed, inAir, sprinting);
	}

	@Test
	void standingStillKeepsItClosed() {
		assertEquals(0, CrosshairRenderer.dynamicGap(moving(0, false, false)));
	}

	@Test
	void opensMoreWithMoreMovement() {
		int walking = CrosshairRenderer.dynamicGap(moving(0.13, false, false));
		int sprinting = CrosshairRenderer.dynamicGap(moving(0.28, false, true));
		int jumping = CrosshairRenderer.dynamicGap(moving(0.28, true, true));
		assertTrue(walking > 0);
		assertTrue(sprinting > walking);
		assertTrue(jumping > sprinting);
	}
}
