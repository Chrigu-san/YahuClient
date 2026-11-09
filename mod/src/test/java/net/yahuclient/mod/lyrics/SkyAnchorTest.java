package net.yahuclient.mod.lyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SkyAnchorTest {
	@Test
	void startsWhereThePlayerLooks() {
		SkyAnchor anchor = new SkyAnchor();
		assertEquals(30f, anchor.update(30f, "a"), 1e-4);
	}

	@Test
	void staysPutWhileTheCameraTurns() {
		SkyAnchor anchor = new SkyAnchor();
		Object line = "a";
		anchor.update(0f, line);
		assertEquals(0f, anchor.update(90f, line), 1e-4);
		assertEquals(0f, anchor.update(-170f, line), 1e-4);
	}

	@Test
	void aNewLineComesBackIntoViewOnlyWhenTheOldSpotIsOutOfSight() {
		SkyAnchor anchor = new SkyAnchor();
		anchor.update(0f, "a");
		assertEquals(0f, anchor.update(40f, "b"), 1e-4);    // still in sight: stays
		assertEquals(120f, anchor.update(120f, "c"), 1e-4); // far away: moves in front
	}

	@Test
	void anglesWrapAround() {
		assertEquals(-20f, SkyAnchor.difference(170f, -170f), 1e-4);
		assertEquals(10f, SkyAnchor.difference(-175f, 175f), 1e-4);
		assertEquals(-170f, SkyAnchor.wrap(190f), 1e-4);
	}
}
