package dev.thomas7520.clipchat.ui.model;

/**
 * Where the widget sits and how big it is, in GUI-scaled pixels. The position is an offset from
 * an anchor corner, clamped to the screen when resolved rather than when stored, so shrinking and
 * re-enlarging the window restores the original position.
 */
public record WidgetGeometry(Anchor anchor, int offsetX, int offsetY, int width, int height, boolean collapsed) {
	public static final int MIN_WIDTH = 110;
	public static final int MAX_WIDTH = 400;
	public static final int MIN_HEIGHT = 60;
	public static final int MAX_HEIGHT = 400;

	public static final WidgetGeometry DEFAULT = new WidgetGeometry(Anchor.TOP_RIGHT, 4, 4, 150, 130, false);

	public WidgetGeometry {
		if (anchor == null) {
			anchor = Anchor.TOP_RIGHT;
		}

		offsetX = Math.max(0, offsetX);
		offsetY = Math.max(0, offsetY);
		width = Math.clamp(width, MIN_WIDTH, MAX_WIDTH);
		height = Math.clamp(height, MIN_HEIGHT, MAX_HEIGHT);
	}

	public int visibleHeight(int titleHeight) {
		return collapsed ? titleHeight : height;
	}

	public int resolveX(int screenWidth) {
		int raw = anchor.isRight() ? screenWidth - offsetX - width : offsetX;
		return clamp(raw, screenWidth - width);
	}

	public int resolveY(int screenHeight, int visibleHeight) {
		int raw = anchor.isBottom() ? screenHeight - offsetY - visibleHeight : offsetY;
		return clamp(raw, screenHeight - visibleHeight);
	}

	/** Moves the widget, re-anchoring it to whichever screen corner it now sits closest to. */
	public WidgetGeometry movedTo(int x, int y, int screenWidth, int screenHeight, int visibleHeight) {
		int clampedX = clamp(x, screenWidth - width);
		int clampedY = clamp(y, screenHeight - visibleHeight);
		Anchor moved = Anchor.nearest(clampedX + width / 2, clampedY + visibleHeight / 2, screenWidth, screenHeight);

		int movedOffsetX = moved.isRight() ? screenWidth - clampedX - width : clampedX;
		int movedOffsetY = moved.isBottom() ? screenHeight - clampedY - visibleHeight : clampedY;

		return new WidgetGeometry(moved, movedOffsetX, movedOffsetY, width, height, collapsed);
	}

	/** Resizes the widget, holding its top-left corner in place whatever the anchor is. */
	public WidgetGeometry resizedTo(int newWidth, int newHeight, int screenWidth, int screenHeight) {
		int x = resolveX(screenWidth);
		int y = resolveY(screenHeight, height);
		WidgetGeometry sized = new WidgetGeometry(anchor, offsetX, offsetY, newWidth, newHeight, collapsed);

		int sizedOffsetX = anchor.isRight() ? screenWidth - x - sized.width() : x;
		int sizedOffsetY = anchor.isBottom() ? screenHeight - y - sized.height() : y;

		return new WidgetGeometry(anchor, sizedOffsetX, sizedOffsetY, sized.width(), sized.height(), collapsed);
	}

	public WidgetGeometry withCollapsed(boolean value) {
		return value == collapsed ? this : new WidgetGeometry(anchor, offsetX, offsetY, width, height, value);
	}

	private static int clamp(int value, int max) {
		return max <= 0 ? 0 : Math.clamp(value, 0, max);
	}
}
