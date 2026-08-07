package dev.thomas7520.clipchat.ui.model;

/**
 * The screen corner a widget position is measured from. A widget anchored to a corner stays at
 * that corner when the window is resized or the GUI scale changes.
 */
public enum Anchor {
	TOP_LEFT,
	TOP_RIGHT,
	BOTTOM_LEFT,
	BOTTOM_RIGHT;

	public boolean isRight() {
		return this == TOP_RIGHT || this == BOTTOM_RIGHT;
	}

	public boolean isBottom() {
		return this == BOTTOM_LEFT || this == BOTTOM_RIGHT;
	}

	public static Anchor of(boolean right, boolean bottom) {
		if (bottom) {
			return right ? BOTTOM_RIGHT : BOTTOM_LEFT;
		}

		return right ? TOP_RIGHT : TOP_LEFT;
	}

	public static Anchor nearest(int centerX, int centerY, int screenWidth, int screenHeight) {
		return of(centerX * 2 >= screenWidth, centerY * 2 >= screenHeight);
	}

	public static Anchor byName(String name, Anchor fallback) {
		if (name == null) {
			return fallback;
		}

		for (Anchor anchor : values()) {
			if (anchor.name().equalsIgnoreCase(name)) {
				return anchor;
			}
		}

		return fallback;
	}
}
