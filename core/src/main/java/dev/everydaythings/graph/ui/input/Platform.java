package dev.everydaythings.graph.ui.input;

/**
 * Detected platform for platform-specific keybinding support.
 */
public enum Platform {
    MACOS,
    WINDOWS,
    LINUX,
    OTHER;

    private static Platform detected;

    /**
     * Detect the current platform.
     */
    public static Platform current() {
        if (detected == null) {
            detected = detect();
        }
        return detected;
    }

    /**
     * Override the detected platform (for testing).
     */
    public static void override(Platform platform) {
        detected = platform;
    }

    /**
     * Reset to auto-detection (for testing).
     */
    public static void reset() {
        detected = null;
    }

    private static Platform detect() {
        String os = System.getProperty("os.name", "").toLowerCase();

        if (os.contains("mac") || os.contains("darwin")) {
            return MACOS;
        } else if (os.contains("win")) {
            return WINDOWS;
        } else if (os.contains("nix") || os.contains("nux") || os.contains("bsd")) {
            return LINUX;
        } else {
            return OTHER;
        }
    }

    /**
     * Check if this is a Unix-like platform (macOS or Linux).
     */
    public boolean isUnixLike() {
        return this == MACOS || this == LINUX;
    }
}
