package dev.everydaythings.graph.ui.input;

import java.util.Optional;

/**
 * Resolves key chords to navigation actions based on focus context.
 *
 * <p>This is the core abstraction for UI-agnostic key handling.
 * Implementations define the mapping from physical keys to logical actions.
 *
 * <p>Usage:
 * <pre>{@code
 * KeyBindings bindings = DefaultKeyBindings.forCurrentPlatform();
 * KeyChord chord = adapter.fromNativeEvent(event);
 * Optional<NavAction> action = bindings.resolve(chord, currentContext);
 * action.ifPresent(this::executeAction);
 * }</pre>
 */
public interface KeyBindings {

    /**
     * Resolve a key chord to a navigation action.
     *
     * @param chord   The key chord (key + modifiers)
     * @param context The current focus context
     * @return The action to perform, or empty if the chord is not bound
     */
    Optional<NavAction> resolve(KeyChord chord, FocusContext context);

    /**
     * Resolve a key chord with additional context about prompt state.
     *
     * <p>This overload is needed because Enter behaves differently
     * depending on whether the prompt has text.
     *
     * @param chord          The key chord
     * @param context        The current focus context
     * @param promptHasText  Whether the prompt currently has text
     * @return The action to perform, or empty if not bound
     */
    Optional<NavAction> resolve(KeyChord chord, FocusContext context, boolean promptHasText);

    /**
     * Get the platform this binding set is configured for.
     */
    default Platform platform() {
        return Platform.current();
    }
}
