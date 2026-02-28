package dev.everydaythings.graph.ui.input;

import dev.everydaythings.graph.expression.ExpressionToken;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.runtime.Librarian;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A complete input session tying together controller, bindings, and renderer.
 *
 * <p>This is the main entry point for input handling. It coordinates:
 * <ul>
 *   <li>{@link InputController} - the brain (state + logic)</li>
 *   <li>{@link InputBindings} - key chord to action mapping</li>
 *   <li>{@link InputRenderer} - the view (rendering)</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * InputSession session = InputSession.create()
 *     .withLibrarian(librarian)
 *     .withRenderer(new MyRenderer())
 *     .onResult(result -> handleResult(result))
 *     .start();
 *
 * // In event loop:
 * KeyChord chord = adapter.fromNative(event);
 * session.handleKey(chord);
 * }</pre>
 *
 * <p>For one-shot mode:
 * <pre>{@code
 * InputResult result = InputSession.oneShot()
 *     .withLibrarian(librarian)
 *     .withInitialInput("create ")
 *     .run();  // blocks until complete
 * }</pre>
 */
public class InputSession {

    private final InputController controller;
    private InputBindings bindings;
    private InputRenderer renderer;
    private Consumer<InputResult> resultHandler;
    private boolean running;

    private InputSession() {
        this.controller = new InputController();
        this.bindings = InputBindings.defaults();
    }

    /**
     * Create a new input session.
     */
    public static InputSession create() {
        return new InputSession();
    }

    /**
     * Create a one-shot input session for CLI use.
     */
    public static OneShotBuilder oneShot() {
        return new OneShotBuilder();
    }

    // ==================================================================================
    // Configuration
    // ==================================================================================

    /**
     * Set the librarian for vocabulary-based lookup and dispatch.
     */
    public InputSession withLibrarian(Librarian librarian) {
        controller.withLibrarian(librarian);

        // Set up default lookup from token dictionary
        if (librarian != null) {
            controller.withLookup(text -> {
                var tokenDict = librarian.tokenIndex();
                if (tokenDict != null) {
                    return tokenDict.lookup(text).limit(renderer != null ? renderer.maxCompletions() : 10).toList();
                }
                return List.of();
            });
        }

        return this;
    }

    /**
     * Set custom lookup function.
     */
    public InputSession withLookup(Function<String, List<Posting>> lookup) {
        controller.withLookup(lookup);
        return this;
    }

    /**
     * Set custom dispatch function.
     */
    public InputSession withDispatch(Function<List<ExpressionToken>, InputResult> dispatch) {
        controller.withDispatch(dispatch);
        return this;
    }

    /**
     * Set the key bindings.
     */
    public InputSession withBindings(InputBindings bindings) {
        this.bindings = bindings;
        return this;
    }

    /**
     * Set the renderer.
     */
    public InputSession withRenderer(InputRenderer renderer) {
        this.renderer = renderer;
        controller.onStateChange(renderer::render);
        return this;
    }

    /**
     * Set the result handler (called when input completes).
     */
    public InputSession onResult(Consumer<InputResult> handler) {
        this.resultHandler = handler;
        return this;
    }

    /**
     * Set the prompt string.
     */
    public InputSession withPrompt(String prompt) {
        controller.withPrompt(prompt);
        return this;
    }

    /**
     * Set the hint text.
     */
    public InputSession withHint(String hint) {
        controller.withHint(hint);
        return this;
    }

    /**
     * Set minimum lookup length.
     */
    public InputSession withMinLookupLength(int length) {
        controller.withMinLookupLength(length);
        return this;
    }

    // ==================================================================================
    // Lifecycle
    // ==================================================================================

    /**
     * Start the session.
     */
    public InputSession start() {
        running = true;
        if (renderer != null) {
            renderer.render(controller.currentState());
            renderer.focus();
        }
        return this;
    }

    /**
     * Stop the session.
     */
    public void stop() {
        running = false;
        if (renderer != null) {
            renderer.blur();
            renderer.dispose();
        }
    }

    /**
     * Check if the session is running.
     */
    public boolean isRunning() {
        return running;
    }

    // ==================================================================================
    // Input Handling
    // ==================================================================================

    /**
     * Handle a key chord.
     *
     * <p>This is the main entry point for physical key events.
     * The chord is resolved to an action and processed.
     *
     * @param chord The key chord from the physical event
     * @return Result if input was completed, empty otherwise
     */
    public Optional<InputResult> handleKey(KeyChord chord) {
        if (!running) {
            return Optional.empty();
        }

        InputState state = controller.currentState();
        Optional<InputAction> action = bindings.resolve(chord, state.hasVisibleCompletions());

        if (action.isEmpty()) {
            return Optional.empty();
        }

        Optional<InputResult> result = controller.handle(action.get());

        // Notify handlers
        result.ifPresent(r -> {
            if (renderer != null) {
                renderer.onComplete(r);
            }
            if (resultHandler != null) {
                resultHandler.accept(r);
            }
        });

        return result;
    }

    /**
     * Handle an input action directly.
     *
     * @param action The action to process
     * @return Result if input was completed, empty otherwise
     */
    public Optional<InputResult> handleAction(InputAction action) {
        if (!running) {
            return Optional.empty();
        }

        Optional<InputResult> result = controller.handle(action);

        result.ifPresent(r -> {
            if (renderer != null) {
                renderer.onComplete(r);
            }
            if (resultHandler != null) {
                resultHandler.accept(r);
            }
        });

        return result;
    }

    /**
     * Get the current input state.
     */
    public InputState currentState() {
        return controller.currentState();
    }

    /**
     * Get the controller for advanced access.
     */
    public InputController controller() {
        return controller;
    }

    /**
     * Set the input text directly.
     */
    public void setInput(String text) {
        controller.setInput(text);
    }

    /**
     * Reset to empty state.
     */
    public void reset() {
        controller.reset();
    }

    // ==================================================================================
    // One-Shot Builder
    // ==================================================================================

    /**
     * Builder for one-shot CLI input.
     */
    public static class OneShotBuilder {
        private Librarian librarian;
        private String initialInput = "";
        private String prompt = "> ";
        private Function<String, List<Posting>> lookup;

        public OneShotBuilder withLibrarian(Librarian librarian) {
            this.librarian = librarian;
            return this;
        }

        public OneShotBuilder withInitialInput(String input) {
            this.initialInput = input;
            return this;
        }

        public OneShotBuilder withPrompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        public OneShotBuilder withLookup(Function<String, List<Posting>> lookup) {
            this.lookup = lookup;
            return this;
        }

        /**
         * Run the one-shot input and return the result.
         *
         * <p>This blocks until the user completes input (Enter) or cancels (Ctrl+C/Escape).
         *
         * @return The input result, or null if cancelled
         */
        public InputResult run() {
            // For CLI one-shot, we'll need a simple readline-style implementation
            // This is a placeholder - actual implementation depends on terminal library
            throw new UnsupportedOperationException(
                    "One-shot mode requires terminal implementation. " +
                    "Use InputSession with appropriate renderer instead.");
        }

        /**
         * Run with completions shown inline (like fzf).
         */
        public InputResult runWithInlineCompletions() {
            throw new UnsupportedOperationException(
                    "Inline completions require terminal implementation.");
        }
    }
}
