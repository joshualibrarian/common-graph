package dev.everydaythings.graph.ui.input;

import dev.everydaythings.graph.parse.InputAction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link InputBindings} — key chord to InputAction mapping.
 */
class InputBindingsTest {

    @Test
    void specialKeysResolveCorrectly() {
        InputBindings bindings = InputBindings.defaults();

        assertThat(bindings.resolve(KeyChord.of(SpecialKey.ENTER), false))
                .contains(InputAction.accept());
        assertThat(bindings.resolve(KeyChord.of(SpecialKey.TAB), false))
                .contains(InputAction.tab());
        assertThat(bindings.resolve(KeyChord.of(SpecialKey.ESCAPE), false))
                .contains(InputAction.cancel());
        assertThat(bindings.resolve(KeyChord.of(SpecialKey.BACKSPACE), false))
                .contains(InputAction.backspace());
    }

    @Test
    void ctrlBackspaceDeletesWord() {
        InputBindings bindings = InputBindings.defaults();
        assertThat(bindings.resolve(KeyChord.ctrl(SpecialKey.BACKSPACE), false))
                .contains(InputAction.deleteWord());
    }

    @Test
    void arrowKeysWithCompletions() {
        InputBindings bindings = InputBindings.defaults();
        assertThat(bindings.resolve(KeyChord.of(SpecialKey.UP), true))
                .contains(InputAction.completionUp());
        assertThat(bindings.resolve(KeyChord.of(SpecialKey.DOWN), true))
                .contains(InputAction.completionDown());
    }

    @Test
    void arrowKeysWithoutCompletionsNavigateHistory() {
        InputBindings bindings = InputBindings.defaults();
        assertThat(bindings.resolve(KeyChord.of(SpecialKey.UP), false))
                .contains(InputAction.historyPrev());
        assertThat(bindings.resolve(KeyChord.of(SpecialKey.DOWN), false))
                .contains(InputAction.historyNext());
    }

    @Test
    void characterInputResolves() {
        InputBindings bindings = InputBindings.defaults();
        assertThat(bindings.resolve(KeyChord.of('a'), false))
                .contains(InputAction.char_('a'));
    }

    @Test
    void readlineStyleBindings() {
        InputBindings bindings = InputBindings.defaults();
        assertThat(bindings.resolve(KeyChord.ctrl('w'), false))
                .contains(InputAction.deleteWord());
        assertThat(bindings.resolve(KeyChord.ctrl('a'), false))
                .contains(InputAction.cursorHome());
        assertThat(bindings.resolve(KeyChord.ctrl('e'), false))
                .contains(InputAction.cursorEnd());
    }

    @Test
    void spaceIsTokenBoundary() {
        InputBindings bindings = InputBindings.defaults();
        assertThat(bindings.resolve(KeyChord.of(' '), false))
                .contains(InputAction.tokenBoundary());
    }

    @Test
    void cursorKeys() {
        InputBindings bindings = InputBindings.defaults();
        assertThat(bindings.resolve(KeyChord.of(SpecialKey.LEFT), false))
                .contains(InputAction.cursorLeft());
        assertThat(bindings.resolve(KeyChord.of(SpecialKey.RIGHT), false))
                .contains(InputAction.cursorRight());
        assertThat(bindings.resolve(KeyChord.of(SpecialKey.HOME), false))
                .contains(InputAction.cursorHome());
        assertThat(bindings.resolve(KeyChord.of(SpecialKey.END), false))
                .contains(InputAction.cursorEnd());
    }
}
