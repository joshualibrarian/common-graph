package dev.everydaythings.graph.ui.text;

import dev.everydaythings.graph.ui.input.KeyChord;
import dev.everydaythings.graph.ui.input.PhysicalKey;
import dev.everydaythings.graph.ui.input.SpecialKey;
import org.jline.terminal.Terminal;
import org.jline.utils.InfoCmp.Capability;

/**
 * Adapts JLine key sequences to KeyChord.
 *
 * <p>JLine represents keys differently depending on how you read them:
 * <ul>
 *   <li>LineReader with KeyMap bindings → Binding/Reference objects</li>
 *   <li>Terminal.reader() in raw mode → escape sequences as strings</li>
 * </ul>
 *
 * <p>This adapter handles the raw terminal approach, reading escape sequences
 * and converting them to our UI-agnostic KeyChord representation.
 *
 * <p>Common escape sequences:
 * <ul>
 *   <li>ESC [ A = Up</li>
 *   <li>ESC [ B = Down</li>
 *   <li>ESC [ C = Right</li>
 *   <li>ESC [ D = Left</li>
 *   <li>ESC [ H = Home</li>
 *   <li>ESC [ F = End</li>
 *   <li>ESC O P = F1 (some terminals)</li>
 * </ul>
 */
public class JLineKeyAdapter {

    private final Terminal terminal;

    // Escape sequence strings from terminal capabilities
    private String keyUp;
    private String keyDown;
    private String keyLeft;
    private String keyRight;
    private String keyHome;
    private String keyEnd;
    private String keyDc;   // delete
    private String keyIc;   // insert
    private String keyNpage; // page down
    private String keyPpage; // page up
    private String keyF1, keyF2, keyF3, keyF4, keyF5, keyF6;
    private String keyF7, keyF8, keyF9, keyF10, keyF11, keyF12;

    public JLineKeyAdapter(Terminal terminal) {
        this.terminal = terminal;
        loadCapabilities();
    }

    private void loadCapabilities() {
        keyUp = cap(Capability.key_up);
        keyDown = cap(Capability.key_down);
        keyLeft = cap(Capability.key_left);
        keyRight = cap(Capability.key_right);
        keyHome = cap(Capability.key_home);
        keyEnd = cap(Capability.key_end);
        keyDc = cap(Capability.key_dc);
        keyIc = cap(Capability.key_ic);
        keyNpage = cap(Capability.key_npage);
        keyPpage = cap(Capability.key_ppage);
        keyF1 = cap(Capability.key_f1);
        keyF2 = cap(Capability.key_f2);
        keyF3 = cap(Capability.key_f3);
        keyF4 = cap(Capability.key_f4);
        keyF5 = cap(Capability.key_f5);
        keyF6 = cap(Capability.key_f6);
        keyF7 = cap(Capability.key_f7);
        keyF8 = cap(Capability.key_f8);
        keyF9 = cap(Capability.key_f9);
        keyF10 = cap(Capability.key_f10);
        keyF11 = cap(Capability.key_f11);
        keyF12 = cap(Capability.key_f12);
    }

    private String cap(Capability cap) {
        return terminal.getStringCapability(cap);
    }

    /**
     * Convert a key sequence (escape sequence or single char) to KeyChord.
     *
     * @param seq The key sequence read from terminal
     * @return The KeyChord, or null if not recognized
     */
    public KeyChord fromSequence(String seq) {
        if (seq == null || seq.isEmpty()) {
            return null;
        }

        // Single character
        if (seq.length() == 1) {
            return fromSingleChar(seq.charAt(0));
        }

        // Check against terminal capabilities
        KeyChord fromCap = fromCapability(seq);
        if (fromCap != null) {
            return fromCap;
        }

        // Parse ANSI escape sequences
        return fromAnsiSequence(seq);
    }

    /**
     * Convert a single character to KeyChord.
     */
    private KeyChord fromSingleChar(char c) {
        // Control characters (Ctrl+letter produces 1-26) and DEL (127 = backspace)
        if (c < 32 || c == 127) {
            return switch (c) {
                case '\r', '\n' -> KeyChord.of(SpecialKey.ENTER);
                case '\t' -> KeyChord.of(SpecialKey.TAB);
                case 27 -> KeyChord.of(SpecialKey.ESCAPE);  // ESC alone
                case 127 -> KeyChord.of(SpecialKey.BACKSPACE);  // DEL
                case 8 -> KeyChord.of(SpecialKey.BACKSPACE);    // BS
                default -> {
                    // Ctrl+A = 1, Ctrl+B = 2, etc.
                    char letter = (char) ('a' + c - 1);
                    yield KeyChord.ctrl(letter);
                }
            };
        }

        // Normal printable character
        return KeyChord.of(c);
    }

    /**
     * Match against terminal capabilities (loaded from terminfo).
     */
    private KeyChord fromCapability(String seq) {
        // Arrow keys
        if (seq.equals(keyUp)) return KeyChord.of(SpecialKey.UP);
        if (seq.equals(keyDown)) return KeyChord.of(SpecialKey.DOWN);
        if (seq.equals(keyLeft)) return KeyChord.of(SpecialKey.LEFT);
        if (seq.equals(keyRight)) return KeyChord.of(SpecialKey.RIGHT);

        // Navigation
        if (seq.equals(keyHome)) return KeyChord.of(SpecialKey.HOME);
        if (seq.equals(keyEnd)) return KeyChord.of(SpecialKey.END);
        if (seq.equals(keyPpage)) return KeyChord.of(SpecialKey.PAGE_UP);
        if (seq.equals(keyNpage)) return KeyChord.of(SpecialKey.PAGE_DOWN);

        // Editing
        if (seq.equals(keyDc)) return KeyChord.of(SpecialKey.DELETE);
        if (seq.equals(keyIc)) return KeyChord.of(SpecialKey.INSERT);

        // Function keys
        if (seq.equals(keyF1)) return KeyChord.of(SpecialKey.F1);
        if (seq.equals(keyF2)) return KeyChord.of(SpecialKey.F2);
        if (seq.equals(keyF3)) return KeyChord.of(SpecialKey.F3);
        if (seq.equals(keyF4)) return KeyChord.of(SpecialKey.F4);
        if (seq.equals(keyF5)) return KeyChord.of(SpecialKey.F5);
        if (seq.equals(keyF6)) return KeyChord.of(SpecialKey.F6);
        if (seq.equals(keyF7)) return KeyChord.of(SpecialKey.F7);
        if (seq.equals(keyF8)) return KeyChord.of(SpecialKey.F8);
        if (seq.equals(keyF9)) return KeyChord.of(SpecialKey.F9);
        if (seq.equals(keyF10)) return KeyChord.of(SpecialKey.F10);
        if (seq.equals(keyF11)) return KeyChord.of(SpecialKey.F11);
        if (seq.equals(keyF12)) return KeyChord.of(SpecialKey.F12);

        return null;
    }

    /**
     * Parse ANSI escape sequences.
     *
     * <p>Format: ESC [ (modifier;) code
     * <ul>
     *   <li>ESC [ A = Up</li>
     *   <li>ESC [ 1;5A = Ctrl+Up (modifier 5 = Ctrl)</li>
     *   <li>ESC [ 1;3A = Alt+Up (modifier 3 = Alt)</li>
     *   <li>ESC [ 1;2A = Shift+Up (modifier 2 = Shift)</li>
     * </ul>
     */
    private KeyChord fromAnsiSequence(String seq) {
        // Must start with ESC [
        if (!seq.startsWith("\033[") && !seq.startsWith("\033O")) {
            // Check for Alt+char: ESC followed by a character
            if (seq.length() == 2 && seq.charAt(0) == 27) {
                char c = seq.charAt(1);
                if (c >= 32 && c < 127) {
                    return KeyChord.alt(c);
                }
            }
            return null;
        }

        String body = seq.substring(2);  // After ESC[ or ESC O
        boolean isSS3 = seq.charAt(1) == 'O';  // SS3 sequences (ESC O)

        // SS3 sequences (some terminals use ESC O for arrows and F-keys)
        if (isSS3) {
            return switch (body) {
                case "A" -> KeyChord.of(SpecialKey.UP);
                case "B" -> KeyChord.of(SpecialKey.DOWN);
                case "C" -> KeyChord.of(SpecialKey.RIGHT);
                case "D" -> KeyChord.of(SpecialKey.LEFT);
                case "P" -> KeyChord.of(SpecialKey.F1);
                case "Q" -> KeyChord.of(SpecialKey.F2);
                case "R" -> KeyChord.of(SpecialKey.F3);
                case "S" -> KeyChord.of(SpecialKey.F4);
                case "H" -> KeyChord.of(SpecialKey.HOME);
                case "F" -> KeyChord.of(SpecialKey.END);
                default -> null;
            };
        }

        // Parse modifiers: ESC [ 1;5A means Ctrl+Up
        boolean ctrl = false;
        boolean alt = false;
        boolean shift = false;
        String keyCode = body;

        int semiPos = body.indexOf(';');
        if (semiPos >= 0) {
            String modStr = body.substring(semiPos + 1, body.length() - 1);
            keyCode = body.substring(0, semiPos) + body.charAt(body.length() - 1);
            try {
                int mod = Integer.parseInt(modStr);
                // xterm modifier encoding: value = 1 + (shift?1:0) + (alt?2:0) + (ctrl?4:0)
                // Subtract the base 1 before extracting modifier flags
                int flags = mod - 1;
                shift = (flags & 1) != 0;
                alt = (flags & 2) != 0;
                ctrl = (flags & 4) != 0;
            } catch (NumberFormatException e) {
                // Ignore invalid modifier
            }
        }

        // Parse the key code
        SpecialKey special = parseAnsiKeyCode(keyCode);
        if (special != null) {
            return new KeyChord(PhysicalKey.of(special), ctrl, alt, shift);
        }

        return null;
    }

    private SpecialKey parseAnsiKeyCode(String code) {
        // Simple codes: A, B, C, D for arrows
        if (code.length() == 1) {
            return switch (code.charAt(0)) {
                case 'A' -> SpecialKey.UP;
                case 'B' -> SpecialKey.DOWN;
                case 'C' -> SpecialKey.RIGHT;
                case 'D' -> SpecialKey.LEFT;
                case 'H' -> SpecialKey.HOME;
                case 'F' -> SpecialKey.END;
                default -> null;
            };
        }

        // Extended codes: 1A, 2~, 3~, etc.
        if (code.endsWith("~")) {
            String num = code.substring(0, code.length() - 1);
            return switch (num) {
                case "1" -> SpecialKey.HOME;
                case "2" -> SpecialKey.INSERT;
                case "3" -> SpecialKey.DELETE;
                case "4" -> SpecialKey.END;
                case "5" -> SpecialKey.PAGE_UP;
                case "6" -> SpecialKey.PAGE_DOWN;
                case "11" -> SpecialKey.F1;
                case "12" -> SpecialKey.F2;
                case "13" -> SpecialKey.F3;
                case "14" -> SpecialKey.F4;
                case "15" -> SpecialKey.F5;
                case "17" -> SpecialKey.F6;
                case "18" -> SpecialKey.F7;
                case "19" -> SpecialKey.F8;
                case "20" -> SpecialKey.F9;
                case "21" -> SpecialKey.F10;
                case "23" -> SpecialKey.F11;
                case "24" -> SpecialKey.F12;
                default -> null;
            };
        }

        // Arrow with leading 1: 1A, 1B, etc. (from modifier sequences)
        if (code.length() == 2 && code.charAt(0) == '1') {
            return switch (code.charAt(1)) {
                case 'A' -> SpecialKey.UP;
                case 'B' -> SpecialKey.DOWN;
                case 'C' -> SpecialKey.RIGHT;
                case 'D' -> SpecialKey.LEFT;
                case 'H' -> SpecialKey.HOME;
                case 'F' -> SpecialKey.END;
                default -> null;
            };
        }

        return null;
    }
}
