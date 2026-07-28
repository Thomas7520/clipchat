package dev.thomas7520.clipchat.ui.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WidgetGeometryTest {
	private static final int SCREEN_WIDTH = 400;
	private static final int SCREEN_HEIGHT = 300;

	@Test
	void clampsSizeIntoTheAllowedRange() {
		WidgetGeometry tiny = new WidgetGeometry(Anchor.TOP_LEFT, 0, 0, 1, 1, false);

		assertEquals(WidgetGeometry.MIN_WIDTH, tiny.width());
		assertEquals(WidgetGeometry.MIN_HEIGHT, tiny.height());

		WidgetGeometry huge = new WidgetGeometry(Anchor.TOP_LEFT, 0, 0, 9999, 9999, false);

		assertEquals(WidgetGeometry.MAX_WIDTH, huge.width());
		assertEquals(WidgetGeometry.MAX_HEIGHT, huge.height());
	}

	@Test
	void measuresARightAnchorFromTheRightEdge() {
		WidgetGeometry geometry = new WidgetGeometry(Anchor.TOP_RIGHT, 10, 0, 150, 130, false);

		assertEquals(SCREEN_WIDTH - 10 - 150, geometry.resolveX(SCREEN_WIDTH));
	}

	@Test
	void measuresABottomAnchorFromTheBottomEdge() {
		WidgetGeometry geometry = new WidgetGeometry(Anchor.BOTTOM_LEFT, 0, 10, 150, 130, false);

		assertEquals(SCREEN_HEIGHT - 10 - 130, geometry.resolveY(SCREEN_HEIGHT, 130));
	}

	/** The stored offset is clamped only on resolve, so a window that grows back restores it. */
	@Test
	void survivesAWindowTooSmallToShowIt() {
		WidgetGeometry geometry = new WidgetGeometry(Anchor.TOP_LEFT, 300, 200, 150, 130, false);

		assertEquals(0, geometry.resolveX(100));
		assertEquals(300, geometry.resolveX(SCREEN_WIDTH + 200));
	}

	@Test
	void collapsingReportsOnlyTheTitleBarHeight() {
		WidgetGeometry geometry = WidgetGeometry.DEFAULT.withCollapsed(true);

		assertEquals(12, geometry.visibleHeight(12));
		assertEquals(WidgetGeometry.DEFAULT.height(), geometry.height());
	}

	@Test
	void reAnchorsToWhicheverCornerItWasDraggedNearest() {
		WidgetGeometry moved = WidgetGeometry.DEFAULT.movedTo(5, 5, SCREEN_WIDTH, SCREEN_HEIGHT, 130);

		assertEquals(Anchor.TOP_LEFT, moved.anchor());

		WidgetGeometry bottomRight = WidgetGeometry.DEFAULT
				.movedTo(SCREEN_WIDTH, SCREEN_HEIGHT, SCREEN_WIDTH, SCREEN_HEIGHT, 130);

		assertEquals(Anchor.BOTTOM_RIGHT, bottomRight.anchor());
	}

	@Test
	void keepsThePositionItWasDraggedTo() {
		WidgetGeometry moved = WidgetGeometry.DEFAULT.movedTo(30, 40, SCREEN_WIDTH, SCREEN_HEIGHT, 130);

		assertEquals(30, moved.resolveX(SCREEN_WIDTH));
		assertEquals(40, moved.resolveY(SCREEN_HEIGHT, 130));
	}

	/** Resizing grows from the bottom right grip, whichever corner the widget is anchored to. */
	@Test
	void resizingHoldsTheTopLeftCornerStill() {
		WidgetGeometry anchored = new WidgetGeometry(Anchor.BOTTOM_RIGHT, 20, 20, 150, 130, false);
		int x = anchored.resolveX(SCREEN_WIDTH);
		int y = anchored.resolveY(SCREEN_HEIGHT, 130);

		WidgetGeometry resized = anchored.resizedTo(160, 140, SCREEN_WIDTH, SCREEN_HEIGHT);

		assertEquals(160, resized.width());
		assertEquals(140, resized.height());
		assertEquals(x, resized.resolveX(SCREEN_WIDTH));
		assertEquals(y, resized.resolveY(SCREEN_HEIGHT, 140));
	}

	/** Staying on screen takes priority over holding the corner still when the size cannot fit. */
	@Test
	void aResizeTooBigToFitPullsTheWidgetBackOnScreen() {
		WidgetGeometry anchored = new WidgetGeometry(Anchor.BOTTOM_RIGHT, 20, 20, 150, 130, false);
		WidgetGeometry resized = anchored.resizedTo(300, 260, SCREEN_WIDTH, SCREEN_HEIGHT);

		assertTrue(resized.resolveX(SCREEN_WIDTH) + resized.width() <= SCREEN_WIDTH);
		assertTrue(resized.resolveY(SCREEN_HEIGHT, resized.height()) + resized.height() <= SCREEN_HEIGHT);
	}

	@Test
	void fallsBackToAnAnchorWhenNoneIsStored() {
		assertEquals(Anchor.TOP_RIGHT, new WidgetGeometry(null, 0, 0, 150, 130, false).anchor());
		assertEquals(Anchor.BOTTOM_LEFT, Anchor.byName("bottom_left", Anchor.TOP_RIGHT));
		assertEquals(Anchor.TOP_RIGHT, Anchor.byName("nonsense", Anchor.TOP_RIGHT));
	}
}
