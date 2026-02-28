package dev.everydaythings.graph.ui.input;

import dev.everydaythings.graph.expression.ExpressionToken;
import dev.everydaythings.graph.language.Posting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the unified InputController.
 */
class InputControllerTest {

    private InputController controller;
    private List<InputState> stateChanges;

    @BeforeEach
    void setUp() {
        stateChanges = new ArrayList<>();
        controller = new InputController()
                .onStateChange(stateChanges::add)
                .withPrompt("> ");
    }

    @Test
    void typeCharacters() {
        controller.handle(InputAction.char_('h'));
        controller.handle(InputAction.char_('e'));
        controller.handle(InputAction.char_('l'));
        controller.handle(InputAction.char_('l'));
        controller.handle(InputAction.char_('o'));

        InputState state = controller.currentState();
        assertThat(state.pendingText()).isEqualTo("hello");
        assertThat(state.cursorPosition()).isEqualTo(5);
        assertThat(state.tokens()).isEmpty();
    }

    @Test
    void backspaceDeletesCharacter() {
        controller.handle(InputAction.char_('a'));
        controller.handle(InputAction.char_('b'));
        controller.handle(InputAction.char_('c'));
        controller.handle(InputAction.backspace());

        InputState state = controller.currentState();
        assertThat(state.pendingText()).isEqualTo("ab");
        assertThat(state.cursorPosition()).isEqualTo(2);
    }

    @Test
    void cursorMovement() {
        controller.handle(InputAction.char_('a'));
        controller.handle(InputAction.char_('b'));
        controller.handle(InputAction.char_('c'));

        // Move left
        controller.handle(InputAction.cursorLeft());
        assertThat(controller.currentState().cursorPosition()).isEqualTo(2);

        controller.handle(InputAction.cursorLeft());
        assertThat(controller.currentState().cursorPosition()).isEqualTo(1);

        // Move right
        controller.handle(InputAction.cursorRight());
        assertThat(controller.currentState().cursorPosition()).isEqualTo(2);

        // Home
        controller.handle(InputAction.cursorHome());
        assertThat(controller.currentState().cursorPosition()).isEqualTo(0);

        // End
        controller.handle(InputAction.cursorEnd());
        assertThat(controller.currentState().cursorPosition()).isEqualTo(3);
    }

    @Test
    void deleteWord() {
        // Type "hello world"
        for (char c : "hello world".toCharArray()) {
            controller.handle(InputAction.char_(c));
        }

        // Delete word should remove "world"
        controller.handle(InputAction.deleteWord());
        assertThat(controller.currentState().pendingText()).isEqualTo("hello ");

        // Delete again should remove "hello"
        controller.handle(InputAction.deleteWord());
        assertThat(controller.currentState().pendingText()).isEmpty();
    }

    @Test
    void completionsFromLookup() {
        // Set up mock lookup
        controller.withLookup(text -> List.of(
                createPosting("create", "verb"),
                createPosting("creator", "type"),
                createPosting("creature", "type")
        ));

        // Type "cre"
        controller.handle(InputAction.char_('c'));
        controller.handle(InputAction.char_('r'));
        controller.handle(InputAction.char_('e'));

        InputState state = controller.currentState();
        assertThat(state.completions()).hasSize(3);
        assertThat(state.showCompletions()).isTrue();
        assertThat(state.selectedCompletion()).isEqualTo(0);
    }

    @Test
    void navigateCompletions() {
        controller.withLookup(text -> List.of(
                createPosting("alpha", "type"),
                createPosting("beta", "type"),
                createPosting("gamma", "type")
        ));

        controller.handle(InputAction.char_('a'));

        // Navigate down
        controller.handle(InputAction.completionDown());
        assertThat(controller.currentState().selectedCompletion()).isEqualTo(1);

        controller.handle(InputAction.completionDown());
        assertThat(controller.currentState().selectedCompletion()).isEqualTo(2);

        // Wrap around
        controller.handle(InputAction.completionDown());
        assertThat(controller.currentState().selectedCompletion()).isEqualTo(0);

        // Navigate up (wraps)
        controller.handle(InputAction.completionUp());
        assertThat(controller.currentState().selectedCompletion()).isEqualTo(2);
    }

    @Test
    void acceptCompletion() {
        controller.withLookup(text -> List.of(
                createPosting("create", "verb")
        ));

        controller.handle(InputAction.char_('c'));
        controller.handle(InputAction.char_('r'));
        controller.handle(InputAction.char_('e'));

        // Accept completion
        controller.handle(InputAction.accept());

        InputState state = controller.currentState();
        assertThat(state.tokens()).hasSize(1);
        assertThat(state.tokens().get(0)).isInstanceOf(ExpressionToken.RefToken.class);
        assertThat(state.tokens().get(0).displayText()).isEqualTo("create");
        assertThat(state.pendingText()).isEmpty();
        assertThat(state.showCompletions()).isFalse();
    }

    @Test
    void tabAutocompleteSingleMatch() {
        controller.withLookup(text -> List.of(
                createPosting("unique_match", "type")
        ));

        controller.handle(InputAction.char_('u'));
        controller.handle(InputAction.char_('n'));
        controller.handle(InputAction.char_('i'));

        // Tab with single match should accept it
        controller.handle(InputAction.tab());

        assertThat(controller.currentState().tokens()).hasSize(1);
        assertThat(controller.currentState().tokens().get(0).displayText()).isEqualTo("unique_match");
    }

    @Test
    void tabCyclesMultipleMatches() {
        controller.withLookup(text -> List.of(
                createPosting("alpha", "type"),
                createPosting("also", "type")
        ));

        controller.handle(InputAction.char_('a'));

        // After typing, selection starts at 0
        assertThat(controller.currentState().selectedCompletion()).isEqualTo(0);

        // Tab cycles to next match
        controller.handle(InputAction.tab());
        assertThat(controller.currentState().selectedCompletion()).isEqualTo(1);

        controller.handle(InputAction.tab());
        assertThat(controller.currentState().selectedCompletion()).isEqualTo(0);

        controller.handle(InputAction.tab());
        assertThat(controller.currentState().selectedCompletion()).isEqualTo(1);
    }

    @Test
    void cancelDismissesCompletions() {
        controller.withLookup(text -> List.of(createPosting("test", "type")));

        controller.handle(InputAction.char_('t'));
        assertThat(controller.currentState().showCompletions()).isTrue();

        controller.handle(InputAction.cancel());
        assertThat(controller.currentState().showCompletions()).isFalse();
        // Text is preserved
        assertThat(controller.currentState().pendingText()).isEqualTo("t");
    }

    @Test
    void cancelTwiceClearsInput() {
        controller.handle(InputAction.char_('t'));
        controller.handle(InputAction.char_('e'));
        controller.handle(InputAction.char_('s'));
        controller.handle(InputAction.char_('t'));

        controller.handle(InputAction.cancel()); // First cancel (if there were completions, would dismiss them)
        controller.handle(InputAction.cancel()); // Second cancel clears input

        assertThat(controller.currentState().pendingText()).isEmpty();
    }

    @Test
    void acceptWithNoCompletionsCompletesInput() {
        // Set up dispatch function
        List<List<ExpressionToken>> dispatched = new ArrayList<>();
        controller.withDispatch(tokens -> {
            dispatched.add(new ArrayList<>(tokens));
            return InputResult.success(tokens);
        });

        controller.handle(InputAction.char_('t'));
        controller.handle(InputAction.char_('e'));
        controller.handle(InputAction.char_('s'));
        controller.handle(InputAction.char_('t'));

        // No completions - accept should complete input
        controller.withLookup(text -> List.of());
        controller.handle(InputAction.accept());

        assertThat(dispatched).hasSize(1);
        assertThat(dispatched.get(0)).hasSize(1);
        assertThat(dispatched.get(0).get(0).displayText()).isEqualTo("\"test\""); // LiteralToken wraps in quotes
    }

    @Test
    void tokenBoundaryCommitsOperator() {
        for (char c : "&&".toCharArray()) {
            controller.handle(InputAction.char_(c));
        }

        controller.handle(InputAction.tokenBoundary());

        InputState state = controller.currentState();
        assertThat(state.tokens()).hasSize(1);
        assertThat(state.tokens().get(0)).isInstanceOf(ExpressionToken.OpToken.class);
        assertThat(state.pendingText()).isEmpty();
    }

    @Test
    void historyNavigation() {
        // Manually add history entries to test navigation
        controller.addToHistory("first");
        controller.addToHistory("second");

        // Navigate history
        controller.handle(InputAction.historyPrev());
        assertThat(controller.currentState().pendingText()).isEqualTo("second");

        controller.handle(InputAction.historyPrev());
        assertThat(controller.currentState().pendingText()).isEqualTo("first");

        controller.handle(InputAction.historyNext());
        assertThat(controller.currentState().pendingText()).isEqualTo("second");

        // Navigate past end returns to empty (saved input)
        controller.handle(InputAction.historyNext());
        assertThat(controller.currentState().pendingText()).isEmpty();
    }

    @Test
    void keyBindingsResolveCorrectly() {
        InputBindings bindings = InputBindings.defaults();

        // Special keys
        assertThat(bindings.resolve(KeyChord.of(SpecialKey.ENTER), false))
                .contains(InputAction.accept());
        assertThat(bindings.resolve(KeyChord.of(SpecialKey.TAB), false))
                .contains(InputAction.tab());
        assertThat(bindings.resolve(KeyChord.of(SpecialKey.ESCAPE), false))
                .contains(InputAction.cancel());
        assertThat(bindings.resolve(KeyChord.of(SpecialKey.BACKSPACE), false))
                .contains(InputAction.backspace());

        // Ctrl+Backspace = delete word
        assertThat(bindings.resolve(KeyChord.ctrl(SpecialKey.BACKSPACE), false))
                .contains(InputAction.deleteWord());

        // Arrow keys with completions
        assertThat(bindings.resolve(KeyChord.of(SpecialKey.UP), true))
                .contains(InputAction.completionUp());
        assertThat(bindings.resolve(KeyChord.of(SpecialKey.DOWN), true))
                .contains(InputAction.completionDown());

        // Arrow keys without completions = history
        assertThat(bindings.resolve(KeyChord.of(SpecialKey.UP), false))
                .contains(InputAction.historyPrev());
        assertThat(bindings.resolve(KeyChord.of(SpecialKey.DOWN), false))
                .contains(InputAction.historyNext());

        // Character input
        assertThat(bindings.resolve(KeyChord.of('a'), false))
                .contains(InputAction.char_('a'));

        // Readline-style bindings
        assertThat(bindings.resolve(KeyChord.ctrl('w'), false))
                .contains(InputAction.deleteWord());
        assertThat(bindings.resolve(KeyChord.ctrl('a'), false))
                .contains(InputAction.cursorHome());
        assertThat(bindings.resolve(KeyChord.ctrl('e'), false))
                .contains(InputAction.cursorEnd());
    }

    @Test
    void stateChangesNotified() {
        // Clear any state changes from setup
        stateChanges.clear();

        controller.handle(InputAction.char_('a'));
        controller.handle(InputAction.char_('b'));
        controller.handle(InputAction.backspace());

        // Each action should notify
        assertThat(stateChanges).hasSize(3);
    }

    // Helper to create test postings
    private static Posting createPosting(String token, String category) {
        return Posting.universal(
                token,
                dev.everydaythings.graph.item.id.ItemID.fromString("test:" + token),
                1.0f
        );
    }
}
