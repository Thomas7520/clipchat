package dev.thomas7520.clipchat.clipboard.model;

/**
 * Outcome of a clipboard action. {@link Failed} carries a translation key, never a message
 * built from clipboard text.
 */
public sealed interface ActionResult {
	record Ok() implements ActionResult {
	}

	record Unsupported(ClipboardCapability missing) implements ActionResult {
	}

	record Failed(String translationKey) implements ActionResult {
	}

	ActionResult OK = new Ok();

	default boolean succeeded() {
		return this instanceof Ok;
	}

	static ActionResult unsupported(ClipboardCapability missing) {
		return new Unsupported(missing);
	}

	static ActionResult failed(String translationKey) {
		return new Failed(translationKey);
	}
}
