package dev.everydaythings.graph.ui.text;

import io.github.humbleui.skija.FontMgr;
import io.github.humbleui.skija.FontStyle;
import io.github.humbleui.skija.Typeface;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Unified font discovery and byte loading for both Skia and Filament renderers.
 *
 * <p>Builds a single ordered fallback chain of fonts. For any glyph, try font 0,
 * then font 1, and so on. The only distinction: monospace text uses a mono primary,
 * proportional text uses a proportional primary. After the primary, the chain is shared.
 *
 * <p>Does <b>discovery and byte loading only</b> — knows nothing about Skia FontCollection
 * or MSDF atlases. Each backend registers the same fonts into its own rendering system.
 */
public class FontRegistry {

    private static final Logger LOG = Logger.getLogger(FontRegistry.class.getName());

    /** Preferred nerd font base names, in priority order. */
    private static final String[] PREFERRED_NERD_BASES = {
            "FiraCode", "Hack", "JetBrainsMono", "0xProto", "Lilex", "IosevkaTermSlab"
    };

    private static final String BUNDLED_SYMBOLS_RESOURCE = "/fonts/SymbolsNerdFontMono-Regular.ttf";
    private static final String BUNDLED_SYMBOLS_FAMILY = "Symbols Nerd Font Mono";

    private static final String BUNDLED_TWEMOJI_RESOURCE = "/fonts/Twemoji.Mozilla.ttf";
    private static final String BUNDLED_TWEMOJI_FAMILY = "Twemoji Mozilla";
    private static final String APPLE_COLOR_EMOJI_FAMILY = "Apple Color Emoji";

    // ==================================================================================
    // Shared Instance
    // ==================================================================================

    private static volatile FontRegistry sharedInstance;

    /**
     * Process-level shared FontRegistry.
     *
     * <p>Font discovery and byte loading is expensive — this ensures it happens
     * once per process. The registry is immutable after construction, so sharing
     * is safe. Both the session and the host presence tray use the same fonts.
     */
    public static FontRegistry shared() {
        if (sharedInstance == null) {
            synchronized (FontRegistry.class) {
                if (sharedInstance == null) {
                    sharedInstance = new FontRegistry();
                }
            }
        }
        return sharedInstance;
    }

    // ==================================================================================
    // Instance State
    // ==================================================================================

    private final List<ResolvedFont> fonts = new ArrayList<>();
    private int monoPrimaryIndex = -1;
    private int proportionalPrimaryIndex = -1;

    /**
     * A font discovered by the registry: family name, optional raw bytes, optional filesystem path.
     *
     * <ul>
     *   <li>Bundled fonts always have {@code data} (loaded from classpath)</li>
     *   <li>System fonts found at filesystem paths have {@code sourcePath} and {@code data}</li>
     *   <li>System fonts discovered by name only have {@code familyName} (Skia can use,
     *       MSDF cannot)</li>
     * </ul>
     */
    public static class ResolvedFont {
        private final String familyName;
        private final byte[] data;
        private final String sourcePath;

        public ResolvedFont(String familyName, byte[] data, String sourcePath) {
            this.familyName = familyName;
            this.data = data;
            this.sourcePath = sourcePath;
        }

        public String familyName() { return familyName; }
        public byte[] data() { return data; }
        public String sourcePath() { return sourcePath; }

        @Override
        public String toString() {
            return familyName + (data != null ? " [bytes]" : sourcePath != null ? " [path]" : " [name]");
        }
    }

    public FontRegistry() {
        discover();
    }

    // ==================================================================================
    // Public API
    // ==================================================================================

    /** All discovered fonts in fallback order. */
    public List<ResolvedFont> fonts() { return fonts; }

    /** Full fallback chain starting with the mono primary. */
    public List<ResolvedFont> monoChain() {
        return chainStartingWith(monoPrimaryIndex);
    }

    /** Full fallback chain starting with the proportional primary. */
    public List<ResolvedFont> proportionalChain() {
        return chainStartingWith(proportionalPrimaryIndex);
    }

    /** Family names from the mono chain, for Skia FontCollection. */
    public String[] monoFamilies() {
        return monoChain().stream().map(ResolvedFont::familyName).toArray(String[]::new);
    }

    /** Family names from the proportional chain, for Skia FontCollection. */
    public String[] proportionalFamilies() {
        return proportionalChain().stream().map(ResolvedFont::familyName).toArray(String[]::new);
    }

    /**
     * Fallback chain with a requested family promoted to primary position.
     *
     * <p>Generic aliases map to the corresponding primary chain:
     * "monospace"/"mono"/"code" -> mono primary, otherwise proportional.
     */
    public List<ResolvedFont> chainForFamily(String requestedFamily) {
        String normalized = normalizeFamily(requestedFamily);
        if (normalized == null || normalized.isEmpty()
                || "sans-serif".equals(normalized) || "proportional".equals(normalized)) {
            return proportionalChain();
        }
        if ("monospace".equals(normalized) || "mono".equals(normalized) || "code".equals(normalized)) {
            return monoChain();
        }
        for (int i = 0; i < fonts.size(); i++) {
            if (fonts.get(i).familyName().equalsIgnoreCase(requestedFamily)) {
                return chainStartingWith(i);
            }
        }
        return proportionalChain();
    }

    /** Family names from {@link #chainForFamily(String)}. */
    public String[] familiesFor(String requestedFamily) {
        return chainForFamily(requestedFamily).stream()
                .map(ResolvedFont::familyName)
                .toArray(String[]::new);
    }

    /** Family names for emoji-first fallback (Twemoji first, then rest of chain). */
    public String[] emojiFamilies() {
        List<String> chain = new ArrayList<>();
        // Find the Twemoji entry and promote it
        int emojiIdx = -1;
        for (int i = 0; i < fonts.size(); i++) {
            if (BUNDLED_TWEMOJI_FAMILY.equals(fonts.get(i).familyName())) {
                emojiIdx = i;
                break;
            }
        }
        if (emojiIdx >= 0) {
            chain.add(fonts.get(emojiIdx).familyName());
        }
        for (int i = 0; i < fonts.size(); i++) {
            if (i != emojiIdx) {
                chain.add(fonts.get(i).familyName());
            }
        }
        return chain.toArray(new String[0]);
    }

    // ==================================================================================
    // Discovery
    // ==================================================================================

    private void discover() {
        String home = System.getProperty("user.home", "");

        // 1. Discover mono primary (Nerd Font Mono)
        monoPrimaryIndex = discoverNerdFont(home, true);

        // 2. Discover proportional primary (Nerd Font Propo)
        proportionalPrimaryIndex = discoverNerdFont(home, false);

        // 3. If no mono found, try fallback mono paths
        if (monoPrimaryIndex < 0) {
            monoPrimaryIndex = discoverFallbackMono(home);
        }

        // 4. If no proportional found, try fallback proportional paths
        if (proportionalPrimaryIndex < 0) {
            proportionalPrimaryIndex = discoverFallbackProportional(home);
        }

        // 5. Bundled Symbols Nerd Font Mono
        loadBundled(BUNDLED_SYMBOLS_RESOURCE, BUNDLED_SYMBOLS_FAMILY);

        // 6. Emoji/symbol chain (shared by Skia + Filament):
        //    keep bundled Twemoji in the chain on all platforms so MSDF has a
        //    vector COLRv0 fallback even when the system emoji font is bitmap-only.
        loadBundled(BUNDLED_TWEMOJI_RESOURCE, BUNDLED_TWEMOJI_FAMILY);
        tryFilesystemFont(new String[]{
                "/System/Library/Fonts/Apple Color Emoji.ttc",
        }, APPLE_COLOR_EMOJI_FAMILY);
        tryFilesystemFont(new String[]{
                "/System/Library/Fonts/Apple Symbols.ttf",
        }, "Apple Symbols");

        // 7. Outline symbol/emoji fallback
        tryFilesystemFont(new String[]{
                "/usr/share/fonts/truetype/ancient-scripts/Symbola_hint.ttf",
                "/usr/share/fonts/truetype/ancient-scripts/Symbola.ttf",
        }, "Symbola");
        tryFilesystemFont(new String[]{
                "/usr/share/fonts/truetype/noto/NotoSansSymbols2-Regular.ttf",
        }, "Noto Sans Symbols 2");

        // 8. Broad Unicode coverage
        tryFilesystemFont(new String[]{
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                "/usr/share/fonts/TTF/DejaVuSans.ttf",
        }, "DejaVu Sans");
        tryFilesystemFont(new String[]{
                "/usr/share/fonts/truetype/freefont/FreeSans.ttf",
        }, "FreeSans");

        // 9. Terminal system fallback names (Skia resolves these via FontMgr)
        addNameOnly("monospace");
        addNameOnly("sans-serif");

        LOG.info("FontRegistry: " + fonts.size() + " fonts in chain"
                + " | mono=" + primaryName(monoPrimaryIndex)
                + " | proportional=" + primaryName(proportionalPrimaryIndex));
    }

    /**
     * Discover a nerd font primary by trying filesystem paths, then Skia FontMgr name scan.
     *
     * @param home user home directory
     * @param mono true for "Nerd Font Mono", false for "Nerd Font Propo"
     * @return index in fonts list, or -1 if not found
     */
    private int discoverNerdFont(String home, boolean mono) {
        String suffix = mono ? "NerdFontMono" : "NerdFontPropo";
        String displaySuffix = mono ? " Nerd Font Mono" : " Nerd Font Propo";
        String os = System.getProperty("os.name", "").toLowerCase();

        // Try filesystem paths for each preferred base
        for (String base : PREFERRED_NERD_BASES) {
            String[] paths = {
                    home + "/.local/share/fonts/" + base + "/" + base + suffix + "-Regular.ttf",
                    "/usr/share/fonts/truetype/" + base.toLowerCase() + "/" + base + suffix + "-Regular.ttf",
                    "/usr/share/fonts/TTF/" + base + suffix + "-Regular.ttf",
            };
            for (String path : paths) {
                byte[] data = loadFile(path);
                if (data != null) {
                    String familyName = base + displaySuffix;
                    int idx = fonts.size();
                    fonts.add(new ResolvedFont(familyName, data, path));
                    LOG.info("Discovered " + (mono ? "mono" : "proportional")
                            + " primary from filesystem: " + familyName + " at " + path);
                    return idx;
                }
            }
        }

        // On macOS, name-only Nerd Font matches can produce unstable paragraph
        // metrics in this app's layout pipeline. Prefer explicit file-backed fonts.
        if (os.contains("mac")) {
            LOG.info("Skipping name-only nerd font discovery on macOS");
            return -1;
        }

        // Fall back to Skia FontMgr name-based discovery
        FontMgr mgr = FontMgr.getDefault();
        for (String base : PREFERRED_NERD_BASES) {
            String candidate = base + displaySuffix;
            try (Typeface face = mgr.matchFamilyStyle(candidate, FontStyle.NORMAL)) {
                if (face != null) {
                    int idx = fonts.size();
                    fonts.add(new ResolvedFont(candidate, null, null));
                    LOG.info("Discovered " + (mono ? "mono" : "proportional")
                            + " primary from system: " + candidate);
                    return idx;
                }
            }
        }

        // Scan all system families for any nerd font with matching suffix
        for (int i = 0; i < mgr.getFamiliesCount(); i++) {
            String name = mgr.getFamilyName(i);
            if (name.endsWith(displaySuffix)) {
                int idx = fonts.size();
                fonts.add(new ResolvedFont(name, null, null));
                LOG.info("Discovered " + (mono ? "mono" : "proportional")
                        + " primary from system scan: " + name);
                return idx;
            }
        }

        LOG.info("No " + (mono ? "mono" : "proportional") + " nerd font found");
        return -1;
    }

    /**
     * Fallback mono discovery when no nerd font was found.
     */
    private int discoverFallbackMono(String home) {
        String[] paths = {
                "/System/Library/Fonts/SFNSMono.ttf",
                "/System/Library/Fonts/Menlo.ttc",
                "/System/Library/Fonts/Supplemental/Andale Mono.ttf",
                "/System/Library/Fonts/Supplemental/Courier New.ttf",
                "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf",
                "/usr/share/fonts/TTF/DejaVuSansMono.ttf",
                "/usr/share/fonts/truetype/liberation/LiberationMono-Regular.ttf",
                "/usr/share/fonts/truetype/freefont/FreeMono.ttf",
        };
        String[] names = {
                "SF Mono", "Menlo", "Andale Mono", "Courier New",
                "DejaVu Sans Mono", "DejaVu Sans Mono",
                "Liberation Mono", "FreeMono"
        };
        for (int i = 0; i < paths.length; i++) {
            byte[] data = loadFile(paths[i]);
            if (data != null) {
                int idx = fonts.size();
                fonts.add(new ResolvedFont(names[i], data, paths[i]));
                LOG.info("Fallback mono: " + names[i] + " at " + paths[i]);
                return idx;
            }
        }
        return -1;
    }

    /**
     * Fallback proportional discovery when no nerd font was found.
     */
    private int discoverFallbackProportional(String home) {
        String[] paths = {
                "/System/Library/Fonts/SFNS.ttf",
                "/System/Library/Fonts/Supplemental/Arial.ttf",
                "/System/Library/Fonts/Supplemental/Helvetica.ttc",
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                "/usr/share/fonts/TTF/DejaVuSans.ttf",
                "/usr/share/fonts/truetype/freefont/FreeSans.ttf",
        };
        String[] names = {"SF Pro", "Arial", "Helvetica", "DejaVu Sans", "DejaVu Sans", "FreeSans"};
        for (int i = 0; i < paths.length; i++) {
            byte[] data = loadFile(paths[i]);
            if (data != null) {
                int idx = fonts.size();
                fonts.add(new ResolvedFont(names[i], data, paths[i]));
                LOG.info("Fallback proportional: " + names[i] + " at " + paths[i]);
                return idx;
            }
        }
        return -1;
    }

    // ==================================================================================
    // Font Loading Helpers
    // ==================================================================================

    private void loadBundled(String resource, String familyName) {
        try (InputStream is = getClass().getResourceAsStream(resource)) {
            if (is == null) {
                LOG.warning("Bundled font not found: " + resource);
                return;
            }
            byte[] data = is.readAllBytes();
            fonts.add(new ResolvedFont(familyName, data, null));
            LOG.fine("Loaded bundled font: " + familyName);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Error loading bundled font: " + resource, e);
        }
    }

    /**
     * Try loading a font from filesystem paths. If found, add to chain with bytes.
     */
    private void tryFilesystemFont(String[] paths, String familyName) {
        for (String path : paths) {
            byte[] data = loadFile(path);
            if (data != null) {
                fonts.add(new ResolvedFont(familyName, data, path));
                LOG.fine("System font: " + familyName + " at " + path);
                return;
            }
        }
    }

    private void addNameOnly(String familyName) {
        fonts.add(new ResolvedFont(familyName, null, null));
    }

    private static byte[] loadFile(String path) {
        try {
            Path p = Path.of(path);
            if (Files.exists(p)) {
                return Files.readAllBytes(p);
            }
        } catch (IOException e) {
            // try next
        }
        return null;
    }

    private boolean hasFamily(String familyName) {
        for (ResolvedFont font : fonts) {
            if (familyName.equals(font.familyName())) {
                return true;
            }
        }
        return false;
    }

    // ==================================================================================
    // Chain Helpers
    // ==================================================================================

    /**
     * Build a chain with the given index promoted to position 0,
     * followed by all other fonts in their original order.
     */
    private List<ResolvedFont> chainStartingWith(int primaryIndex) {
        if (primaryIndex < 0 || primaryIndex >= fonts.size()) {
            return List.copyOf(fonts);
        }
        List<ResolvedFont> chain = new ArrayList<>(fonts.size());
        chain.add(fonts.get(primaryIndex));
        for (int i = 0; i < fonts.size(); i++) {
            if (i != primaryIndex) {
                chain.add(fonts.get(i));
            }
        }
        return chain;
    }

    private String primaryName(int index) {
        return index >= 0 && index < fonts.size() ? fonts.get(index).familyName() : "none";
    }

    private static String normalizeFamily(String family) {
        return family == null ? "" : family.trim().toLowerCase();
    }
}
