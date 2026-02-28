package dev.everydaythings.graph.ui.skia;

import dev.everydaythings.graph.ui.input.KeyChord;
import dev.everydaythings.graph.ui.input.PhysicalKey;
import dev.everydaythings.graph.ui.input.SpecialKey;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Adapts GLFW key events to {@link KeyChord}.
 *
 * <p>Maps GLFW key codes to platform-agnostic {@link SpecialKey}/{@link PhysicalKey},
 * extracts modifier state, and produces a {@link KeyChord}.
 */
public class SkiaKeyAdapter {

    /**
     * Convert a GLFW key event to a KeyChord.
     *
     * @param key      GLFW key code (GLFW_KEY_*)
     * @param scancode Platform scancode
     * @param action   GLFW_PRESS, GLFW_RELEASE, or GLFW_REPEAT
     * @param mods     Modifier bitmask (GLFW_MOD_CONTROL, GLFW_MOD_ALT, GLFW_MOD_SHIFT)
     * @return The UI-agnostic KeyChord, or null if unmappable
     */
    public KeyChord fromNative(int key, int scancode, int action, int mods) {
        // Only handle press and repeat (ignore release)
        if (action == GLFW_RELEASE) {
            return null;
        }

        boolean ctrl = (mods & GLFW_MOD_CONTROL) != 0;
        boolean alt = (mods & GLFW_MOD_ALT) != 0;
        boolean shift = (mods & GLFW_MOD_SHIFT) != 0;

        PhysicalKey physicalKey = mapKey(key);
        if (physicalKey == null) {
            return null;
        }

        // For printable characters without Ctrl/Alt, let onChar handle them
        // to avoid double-dispatch (GLFW fires both onKey and onChar).
        if (!ctrl && !alt && physicalKey instanceof PhysicalKey.Char) {
            return null;
        }

        return new KeyChord(physicalKey, ctrl, alt, shift);
    }

    /**
     * Convert a GLFW character callback codepoint to a KeyChord.
     */
    public KeyChord fromChar(int codepoint) {
        char c = (char) codepoint;
        return new KeyChord(PhysicalKey.of(c), false, false, false);
    }

    private PhysicalKey mapKey(int glfwKey) {
        SpecialKey special = switch (glfwKey) {
            case GLFW_KEY_UP -> SpecialKey.UP;
            case GLFW_KEY_DOWN -> SpecialKey.DOWN;
            case GLFW_KEY_LEFT -> SpecialKey.LEFT;
            case GLFW_KEY_RIGHT -> SpecialKey.RIGHT;
            case GLFW_KEY_ENTER, GLFW_KEY_KP_ENTER -> SpecialKey.ENTER;
            case GLFW_KEY_ESCAPE -> SpecialKey.ESCAPE;
            case GLFW_KEY_TAB -> SpecialKey.TAB;
            case GLFW_KEY_BACKSPACE -> SpecialKey.BACKSPACE;
            case GLFW_KEY_DELETE -> SpecialKey.DELETE;
            case GLFW_KEY_HOME -> SpecialKey.HOME;
            case GLFW_KEY_END -> SpecialKey.END;
            case GLFW_KEY_PAGE_UP -> SpecialKey.PAGE_UP;
            case GLFW_KEY_PAGE_DOWN -> SpecialKey.PAGE_DOWN;
            case GLFW_KEY_INSERT -> SpecialKey.INSERT;
            case GLFW_KEY_F1 -> SpecialKey.F1;
            case GLFW_KEY_F2 -> SpecialKey.F2;
            case GLFW_KEY_F3 -> SpecialKey.F3;
            case GLFW_KEY_F4 -> SpecialKey.F4;
            case GLFW_KEY_F5 -> SpecialKey.F5;
            case GLFW_KEY_F6 -> SpecialKey.F6;
            case GLFW_KEY_F7 -> SpecialKey.F7;
            case GLFW_KEY_F8 -> SpecialKey.F8;
            case GLFW_KEY_F9 -> SpecialKey.F9;
            case GLFW_KEY_F10 -> SpecialKey.F10;
            case GLFW_KEY_F11 -> SpecialKey.F11;
            case GLFW_KEY_F12 -> SpecialKey.F12;
            default -> null;
        };

        if (special != null) {
            return PhysicalKey.of(special);
        }

        // Letter keys (A-Z)
        if (glfwKey >= GLFW_KEY_A && glfwKey <= GLFW_KEY_Z) {
            char c = (char) ('a' + (glfwKey - GLFW_KEY_A));
            return PhysicalKey.of(c);
        }

        // Digit keys (0-9)
        if (glfwKey >= GLFW_KEY_0 && glfwKey <= GLFW_KEY_9) {
            char c = (char) ('0' + (glfwKey - GLFW_KEY_0));
            return PhysicalKey.of(c);
        }

        // Punctuation keys needed for shortcuts (Ctrl+=, Ctrl+-, etc.)
        if (glfwKey == GLFW_KEY_EQUAL) return PhysicalKey.of('=');
        if (glfwKey == GLFW_KEY_MINUS) return PhysicalKey.of('-');

        // Space
        if (glfwKey == GLFW_KEY_SPACE) {
            return PhysicalKey.of(' ');
        }

        return null;
    }
}
