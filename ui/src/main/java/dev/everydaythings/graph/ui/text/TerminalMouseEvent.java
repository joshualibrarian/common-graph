package dev.everydaythings.graph.ui.text;

/**
 * Parsed mouse event from terminal escape sequences.
 *
 * <p>Terminals report mouse events via escape sequences when mouse tracking
 * is enabled. This record represents the parsed event data.
 *
 * <p>Mouse protocols:
 * <ul>
 *   <li>X10 - Basic click reporting</li>
 *   <li>Normal - Press and release</li>
 *   <li>Button-event - Motion while pressed</li>
 *   <li>Any-event - All motion</li>
 *   <li>SGR - Extended encoding (coordinates > 223, better button info)</li>
 * </ul>
 *
 * <p>Sequence formats:
 * <ul>
 *   <li>X10/Normal: {@code ESC [ M Cb Cx Cy} (raw bytes, coordinates 1-based + 32)</li>
 *   <li>SGR: {@code ESC [ < Cb ; Cx ; Cy M} (press) or {@code m} (release)</li>
 * </ul>
 */
public record TerminalMouseEvent(
        Type type,
        Button button,
        int column,    // 0-based
        int row,       // 0-based
        boolean shift,
        boolean alt,
        boolean ctrl
) {

    public enum Type {
        PRESS,
        RELEASE,
        MOTION,
        SCROLL_UP,
        SCROLL_DOWN
    }

    public enum Button {
        LEFT,      // Button 1
        MIDDLE,    // Button 2
        RIGHT,     // Button 3
        NONE       // Motion without button
    }

    /**
     * Parse a mouse escape sequence.
     *
     * @param sequence The escape sequence (starting after ESC [)
     * @return Parsed event, or null if not a mouse sequence
     */
    public static TerminalMouseEvent parse(String sequence) {
        if (sequence == null || sequence.length() < 3) {
            return null;
        }

        // SGR extended format: ESC [ < Cb ; Cx ; Cy M/m
        if (sequence.startsWith("<")) {
            return parseSGR(sequence);
        }

        // X10/Normal format: ESC [ M Cb Cx Cy
        if (sequence.startsWith("M") && sequence.length() >= 4) {
            return parseX10(sequence);
        }

        return null;
    }

    /**
     * Parse SGR extended mouse format.
     * Format: < Cb ; Cx ; Cy M (press) or m (release)
     */
    private static TerminalMouseEvent parseSGR(String sequence) {
        // Remove leading '<'
        String data = sequence.substring(1);

        // Check for M (press) or m (release) at end
        boolean isRelease = data.endsWith("m");
        if (!data.endsWith("M") && !data.endsWith("m")) {
            return null;
        }
        data = data.substring(0, data.length() - 1);

        // Parse Cb;Cx;Cy
        String[] parts = data.split(";");
        if (parts.length != 3) {
            return null;
        }

        try {
            int cb = Integer.parseInt(parts[0]);
            int cx = Integer.parseInt(parts[1]);
            int cy = Integer.parseInt(parts[2]);

            return fromButtonCode(cb, cx - 1, cy - 1, isRelease);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parse X10/Normal mouse format.
     * Format: M Cb Cx Cy (raw bytes, coordinates are value + 32)
     */
    private static TerminalMouseEvent parseX10(String sequence) {
        if (sequence.length() < 4) {
            return null;
        }

        int cb = sequence.charAt(1) - 32;
        int cx = sequence.charAt(2) - 32;
        int cy = sequence.charAt(3) - 32;

        // X10 doesn't report releases for buttons 1-3
        // (release is reported as button 3)
        return fromButtonCode(cb, cx - 1, cy - 1, false);
    }

    /**
     * Create event from button code.
     */
    private static TerminalMouseEvent fromButtonCode(int cb, int col, int row, boolean isRelease) {
        // Modifiers are in bits 2, 3, 4
        boolean shift = (cb & 4) != 0;
        boolean alt = (cb & 8) != 0;
        boolean ctrl = (cb & 16) != 0;

        // Button is in bits 0-1, with bit 6 for motion, bit 7 for additional buttons
        int buttonBits = cb & 3;
        boolean motion = (cb & 32) != 0;
        boolean wheelOrExtra = (cb & 64) != 0;

        Type type;
        Button button;

        if (wheelOrExtra) {
            // Scroll wheel
            if (buttonBits == 0) {
                type = Type.SCROLL_UP;
                button = Button.NONE;
            } else if (buttonBits == 1) {
                type = Type.SCROLL_DOWN;
                button = Button.NONE;
            } else {
                // Buttons 4-7 (extra buttons)
                type = isRelease ? Type.RELEASE : Type.PRESS;
                button = Button.NONE;
            }
        } else if (motion) {
            type = Type.MOTION;
            button = switch (buttonBits) {
                case 0 -> Button.LEFT;
                case 1 -> Button.MIDDLE;
                case 2 -> Button.RIGHT;
                default -> Button.NONE;
            };
        } else {
            type = isRelease ? Type.RELEASE : Type.PRESS;
            button = switch (buttonBits) {
                case 0 -> Button.LEFT;
                case 1 -> Button.MIDDLE;
                case 2 -> Button.RIGHT;
                default -> Button.NONE;  // Release in X10
            };
        }

        return new TerminalMouseEvent(type, button, col, row, shift, alt, ctrl);
    }

    /**
     * Check if this is a left click (press).
     */
    public boolean isLeftClick() {
        return type == Type.PRESS && button == Button.LEFT;
    }

    /**
     * Check if this is a right click (press).
     */
    public boolean isRightClick() {
        return type == Type.PRESS && button == Button.RIGHT;
    }

    /**
     * Check if this is a scroll event.
     */
    public boolean isScroll() {
        return type == Type.SCROLL_UP || type == Type.SCROLL_DOWN;
    }

    /**
     * Escape sequence to enable SGR extended mouse tracking.
     */
    public static final String ENABLE_SGR = "\u001b[?1006h";

    /**
     * Escape sequence to enable button-event tracking.
     */
    public static final String ENABLE_BUTTON_EVENT = "\u001b[?1002h";

    /**
     * Escape sequence to enable any-event tracking (includes motion).
     */
    public static final String ENABLE_ANY_EVENT = "\u001b[?1003h";

    /**
     * Escape sequence to enable basic mouse reporting.
     */
    public static final String ENABLE_BASIC = "\u001b[?1000h";

    /**
     * Escape sequence to disable all mouse tracking.
     */
    public static final String DISABLE_ALL = "\u001b[?1000l\u001b[?1002l\u001b[?1003l\u001b[?1006l";

    /**
     * Enable mouse tracking with SGR extended mode (recommended).
     */
    public static String enableTracking() {
        return ENABLE_BASIC + ENABLE_BUTTON_EVENT + ENABLE_SGR;
    }

    /**
     * Disable all mouse tracking.
     */
    public static String disableTracking() {
        return DISABLE_ALL;
    }
}
