/**
 * UI-agnostic keyboard input handling.
 *
 * <p>This package provides a clean abstraction for keyboard navigation
 * that works identically across GLFW/Skia and Lanterna (terminal) UIs.
 *
 * <h2>Architecture</h2>
 *
 * <pre>{@code
 * Native Event (GLFW/Lanterna)
 *        │
 *        ▼
 * ┌─────────────────┐
 * │ KeyChordAdapter │  ← Translates native → KeyChord
 * └─────────────────┘
 *        │
 *        ▼
 *    KeyChord         ← UI-agnostic key representation
 *        │
 *        ▼
 * ┌─────────────────┐
 * │   KeyBindings   │  ← Resolves chord + context → action
 * └─────────────────┘
 *        │
 *        ▼
 *    NavAction        ← Logical action to execute
 * }</pre>
 *
 * <h2>Two-Layer Navigation Model</h2>
 *
 * <p>Navigation uses two layers of key bindings:
 *
 * <ul>
 *   <li><b>Intra-item (Alt+Arrow)</b>: Navigate within an Item's view -
 *       tree up/down, expand/collapse</li>
 *   <li><b>Inter-item (Alt+Shift+Arrow)</b>: Navigate between Items in
 *       a workspace - prev/next item, close/open</li>
 * </ul>
 *
 * <p>This avoids Ctrl+Arrow which is grabbed by macOS for Mission Control.
 *
 * <h2>Default Bindings</h2>
 *
 * <p>The {@link dev.everydaythings.graph.ui.input.DefaultKeyBindings} class
 * defines the standard navigation:
 *
 * <ul>
 *   <li><b>Alt+Arrow</b>: Tree navigation (intra-item)</li>
 *   <li><b>Alt+Shift+Arrow</b>: Item navigation (inter-item)</li>
 *   <li><b>Plain arrows</b>: Cursor/history in prompt mode</li>
 *   <li><b>Enter</b>: Execute command or enter panel</li>
 *   <li><b>Escape</b>: Exit panel to prompt</li>
 * </ul>
 *
 * <h2>Focus Model</h2>
 *
 * <p>There are two focus states ({@link dev.everydaythings.graph.ui.input.FocusContext}):
 *
 * <ul>
 *   <li><b>PROMPT_TREE</b>: The "home" state - you're in the prompt AND
 *       navigating the tree simultaneously. Alt+arrows move the tree,
 *       plain arrows edit text and navigate history.</li>
 *   <li><b>PANEL</b>: Focus is in the panel content. Keys pass through
 *       to forms/lists except Escape (exits) and Alt+arrows (still navigate).</li>
 * </ul>
 *
 * <h2>Platform Support</h2>
 *
 * <p>The {@link dev.everydaythings.graph.surface.input.Platform} enum detects
 * the current platform (macOS, Windows, Linux) for platform-specific bindings.
 * {@link dev.everydaythings.graph.ui.input.ConfigurableKeyBindings} allows
 * users to override specific bindings.
 *
 * @see dev.everydaythings.graph.surface.input.KeyChord
 * @see dev.everydaythings.graph.surface.input.KeyBindings
 * @see dev.everydaythings.graph.ui.input.NavAction
 * @see dev.everydaythings.graph.surface.input.Platform
 */
package dev.everydaythings.graph.ui.input;
