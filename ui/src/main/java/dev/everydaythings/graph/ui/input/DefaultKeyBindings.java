package dev.everydaythings.graph.ui.input;

import java.util.Optional;

import static dev.everydaythings.graph.ui.input.FocusContext.*;
import static dev.everydaythings.graph.ui.input.NavAction.*;
import static dev.everydaythings.graph.ui.input.SpecialKey.*;

/**
 * Default key bindings for navigation.
 *
 * <p>Uses a two-layer model:
 * <ul>
 *   <li><b>Intra-item (Alt+Arrow)</b>: Navigate within an Item's view</li>
 *   <li><b>Inter-item (Alt+Shift+Arrow)</b>: Navigate between Items in workspace</li>
 * </ul>
 *
 * <p>This avoids Ctrl+Arrow which is grabbed by macOS for Mission Control.
 *
 * <h3>Binding Summary</h3>
 * <table>
 *   <tr><th>Key</th><th>Context</th><th>Action</th></tr>
 *   <tr><td colspan="3"><b>Intra-item (Alt+Arrow)</b></td></tr>
 *   <tr><td>Alt+Up</td><td>Any</td><td>TREE_UP</td></tr>
 *   <tr><td>Alt+Down</td><td>Any</td><td>TREE_DOWN</td></tr>
 *   <tr><td>Alt+Left</td><td>Any</td><td>TREE_COLLAPSE</td></tr>
 *   <tr><td>Alt+Right</td><td>Any</td><td>TREE_EXPAND</td></tr>
 *   <tr><td colspan="3"><b>Inter-item (Alt+Shift+Arrow)</b></td></tr>
 *   <tr><td>Alt+Shift+Up</td><td>Any</td><td>ITEM_PREV</td></tr>
 *   <tr><td>Alt+Shift+Down</td><td>Any</td><td>ITEM_NEXT</td></tr>
 *   <tr><td>Alt+Shift+Left</td><td>Any</td><td>ITEM_CLOSE</td></tr>
 *   <tr><td>Alt+Shift+Right</td><td>Any</td><td>ITEM_OPEN</td></tr>
 *   <tr><td colspan="3"><b>Prompt (plain keys in PROMPT_TREE)</b></td></tr>
 *   <tr><td>Up</td><td>PROMPT_TREE</td><td>HISTORY_UP</td></tr>
 *   <tr><td>Down</td><td>PROMPT_TREE</td><td>HISTORY_DOWN</td></tr>
 *   <tr><td>Left</td><td>PROMPT_TREE</td><td>CURSOR_LEFT</td></tr>
 *   <tr><td>Right</td><td>PROMPT_TREE</td><td>CURSOR_RIGHT</td></tr>
 *   <tr><td>Enter</td><td>PROMPT_TREE (text)</td><td>EXECUTE</td></tr>
 *   <tr><td>Enter</td><td>PROMPT_TREE (empty)</td><td>ENTER_PANEL</td></tr>
 *   <tr><td>Escape</td><td>PANEL</td><td>EXIT_TO_PROMPT</td></tr>
 * </table>
 */
public class DefaultKeyBindings implements KeyBindings {

    private final Platform platform;

    /**
     * Create bindings for the current platform.
     */
    public static DefaultKeyBindings forCurrentPlatform() {
        return new DefaultKeyBindings(Platform.current());
    }

    /**
     * Create bindings for a specific platform.
     */
    public static DefaultKeyBindings forPlatform(Platform platform) {
        return new DefaultKeyBindings(platform);
    }

    /**
     * Create bindings with default platform detection.
     */
    public DefaultKeyBindings() {
        this(Platform.current());
    }

    /**
     * Create bindings for a specific platform.
     */
    public DefaultKeyBindings(Platform platform) {
        this.platform = platform;
    }

    @Override
    public Platform platform() {
        return platform;
    }

    @Override
    public Optional<NavAction> resolve(KeyChord chord, FocusContext context) {
        return resolve(chord, context, false);
    }

    @Override
    public Optional<NavAction> resolve(KeyChord chord, FocusContext context, boolean promptHasText) {

        // === Ctrl+1/2/3/4: View mode switching (works in any context) ===
        // Like macOS Finder: Cmd+1=Table, Cmd+2=Tiles, Cmd+3=List, Cmd+4=Gallery
        // Note: On Mac, Cmd is captured as Ctrl by the key adapter
        if (chord.ctrl() && !chord.alt() && !chord.shift()) {
            Optional<NavAction> viewMode = resolveViewMode(chord);
            if (viewMode.isPresent()) {
                return viewMode;
            }
        }

        // === Alt+Shift+Arrow: Inter-item navigation (works in any context) ===
        if (chord.alt() && chord.shift() && !chord.ctrl()) {
            Optional<NavAction> interItem = resolveInterItem(chord);
            if (interItem.isPresent()) {
                return interItem;
            }
        }

        // === Alt+Arrow (no shift): Intra-item tree navigation (works in any context) ===
        if (chord.alt() && !chord.shift() && !chord.ctrl()) {
            Optional<NavAction> intraItem = resolveIntraItemTree(chord);
            if (intraItem.isPresent()) {
                return intraItem;
            }
        }

        // === Ctrl+W for delete word (common terminal binding) ===
        if (chord.ctrl() && !chord.alt() && !chord.shift()) {
            if (chord.isChar('w') || chord.isChar('W')) {
                if (context == PROMPT_TREE) {
                    return Optional.of(DELETE_WORD);
                }
            }
        }

        // === Context-specific bindings ===
        return switch (context) {
            case PROMPT_TREE -> resolvePromptTree(chord, promptHasText);
            case PANEL -> resolvePanel(chord);
        };
    }

    /**
     * Resolve inter-item navigation (Alt+Shift+Arrow).
     */
    private Optional<NavAction> resolveInterItem(KeyChord chord) {
        if (chord.isKey(UP)) return Optional.of(ITEM_PREV);
        if (chord.isKey(DOWN)) return Optional.of(ITEM_NEXT);
        if (chord.isKey(LEFT)) return Optional.of(ITEM_CLOSE);
        if (chord.isKey(RIGHT)) return Optional.of(ITEM_OPEN);
        return Optional.empty();
    }

    /**
     * Resolve intra-item tree navigation (Alt+Arrow).
     */
    private Optional<NavAction> resolveIntraItemTree(KeyChord chord) {
        if (chord.isKey(UP)) return Optional.of(TREE_UP);
        if (chord.isKey(DOWN)) return Optional.of(TREE_DOWN);
        if (chord.isKey(LEFT)) return Optional.of(TREE_COLLAPSE);
        if (chord.isKey(RIGHT)) return Optional.of(TREE_EXPAND);
        return Optional.empty();
    }

    /**
     * Resolve view mode switching (Cmd/Ctrl+1/2/3/4).
     * Like macOS Finder view mode shortcuts.
     */
    private Optional<NavAction> resolveViewMode(KeyChord chord) {
        if (chord.isChar('1')) return Optional.of(VIEW_TABLE);
        if (chord.isChar('2')) return Optional.of(VIEW_TILES);
        if (chord.isChar('3')) return Optional.of(VIEW_LIST);
        if (chord.isChar('4')) return Optional.of(VIEW_GALLERY);
        return Optional.empty();
    }

    /**
     * Resolve bindings in PROMPT_TREE context.
     */
    private Optional<NavAction> resolvePromptTree(KeyChord chord, boolean promptHasText) {
        // Only handle plain (unmodified) keys for prompt editing
        if (chord.isPlain()) {
            if (chord.isKey(UP)) return Optional.of(HISTORY_UP);
            if (chord.isKey(DOWN)) return Optional.of(HISTORY_DOWN);
            if (chord.isKey(LEFT)) return Optional.of(CURSOR_LEFT);
            if (chord.isKey(RIGHT)) return Optional.of(CURSOR_RIGHT);
            if (chord.isKey(HOME)) return Optional.of(CURSOR_HOME);
            if (chord.isKey(END)) return Optional.of(CURSOR_END);
            if (chord.isKey(BACKSPACE)) return Optional.of(DELETE_BACK);
            if (chord.isKey(DELETE)) return Optional.of(DELETE_FORWARD);

            // Enter: execute if text, enter panel if empty
            if (chord.isKey(ENTER)) {
                return Optional.of(promptHasText ? EXECUTE : ENTER_PANEL);
            }
        }

        // Not bound in this context
        return Optional.empty();
    }

    /**
     * Resolve bindings in PANEL context.
     */
    private Optional<NavAction> resolvePanel(KeyChord chord) {
        // Escape exits panel
        if (chord.isPlain() && chord.isKey(ESCAPE)) {
            return Optional.of(EXIT_TO_PROMPT);
        }

        // Everything else passes through to panel content
        return Optional.empty();
    }
}
