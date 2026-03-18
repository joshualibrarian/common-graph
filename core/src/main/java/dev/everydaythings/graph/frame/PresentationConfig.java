package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.PresentationVocabulary;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Typed payload for presentation configuration.
 *
 * <p>Stored in the {@code (PRESENTATION)} entry of a frame or manifest
 * config map. Contains:
 * <ul>
 *   <li><b>palette</b> — semantic color tokens (token sememe IID to packed RGB)</li>
 *   <li><b>rules</b> — stylesheet rules (selector + property list pairs)</li>
 *   <li><b>glyph</b> — emoji/icon character for display</li>
 *   <li><b>shape</b> — 3D shape hint (sphere, cube, disc)</li>
 * </ul>
 *
 * <p>The three-level cascade (instance, implementation, sememe) merges
 * these: instance palette tokens override implementation tokens, which
 * override sememe tokens. Rules concatenate with instance rules having
 * highest specificity.
 *
 * @see dev.everydaythings.graph.language.PresentationVocabulary
 */
@Canonical.Canonization
public final class PresentationConfig implements Canonical {

    @Canon(order = 0)
    private List<PaletteEntry> palette;

    @Canon(order = 1)
    private List<Rule> rules;

    @Canon(order = 2)
    private String glyph;

    @Canon(order = 3)
    private String shape;

    public PresentationConfig(List<PaletteEntry> palette, List<Rule> rules,
                              String glyph, String shape) {
        this.palette = palette != null ? List.copyOf(palette) : List.of();
        this.rules = rules != null ? List.copyOf(rules) : List.of();
        this.glyph = glyph;
        this.shape = shape;
    }

    public PresentationConfig(List<PaletteEntry> palette, List<Rule> rules) {
        this(palette, rules, null, null);
    }

    @SuppressWarnings("unused")
    private PresentationConfig() {}

    public List<PaletteEntry> palette() { return palette; }
    public List<Rule> rules() { return rules; }
    public String glyph() { return glyph; }
    public String shape() { return shape; }

    /**
     * The primary color from the palette, or -1 if not set.
     */
    public int primaryColor() {
        return paletteColor(PresentationVocabulary.Primary.IID);
    }

    /**
     * Look up a palette color by token sememe IID.
     *
     * @return packed RGB value, or -1 if not found
     */
    public int paletteColor(ItemID token) {
        if (palette == null) return -1;
        for (PaletteEntry e : palette) {
            if (token.equals(e.token)) return e.color;
        }
        return -1;
    }

    /**
     * Build a palette map (token IID to packed RGB) for convenient lookup.
     */
    public Map<ItemID, Integer> paletteMap() {
        Map<ItemID, Integer> map = new LinkedHashMap<>();
        if (palette != null) {
            for (PaletteEntry e : palette) {
                map.put(e.token, e.color);
            }
        }
        return map;
    }

    /**
     * Merge two configs. {@code override} takes precedence for palette tokens.
     * Rules from both are concatenated (override rules appended last = highest priority).
     */
    public PresentationConfig merge(PresentationConfig override) {
        if (override == null) return this;

        // Merge palettes — override wins for same token
        Map<ItemID, Integer> merged = paletteMap();
        merged.putAll(override.paletteMap());
        List<PaletteEntry> mergedPalette = new ArrayList<>();
        for (var entry : merged.entrySet()) {
            mergedPalette.add(new PaletteEntry(entry.getKey(), entry.getValue()));
        }

        // Concatenate rules — later rules have higher priority
        List<Rule> mergedRules = new ArrayList<>(this.rules);
        mergedRules.addAll(override.rules);

        // Override wins for glyph and shape
        String mergedGlyph = override.glyph != null ? override.glyph : this.glyph;
        String mergedShape = override.shape != null ? override.shape : this.shape;

        return new PresentationConfig(mergedPalette, mergedRules, mergedGlyph, mergedShape);
    }

    public static PresentationConfig empty() {
        return new PresentationConfig(List.of(), List.of(), null, null);
    }

    /**
     * Build a PresentationConfig from display metadata (glyph, color, shape).
     *
     * <p>The color is stored as the PRIMARY palette token. This is the
     * typical factory used during seed bootstrap from {@code @Type} annotations.
     */
    public static PresentationConfig of(String glyph, int color, String shape) {
        List<PaletteEntry> palette = List.of(
                new PaletteEntry(PresentationVocabulary.Primary.IID, color));
        return new PresentationConfig(palette, List.of(), glyph, shape);
    }

    // ==================================================================================
    // Palette Entry
    // ==================================================================================

    /**
     * A single palette token binding: semantic color token to packed RGB value.
     */
    @Canonical.Canonization
    public static final class PaletteEntry implements Canonical {

        @Canon(order = 0)
        private ItemID token;

        @Canon(order = 1)
        private int color;

        public PaletteEntry(ItemID token, int color) {
            this.token = Objects.requireNonNull(token, "token");
            this.color = color;
        }

        @SuppressWarnings("unused")
        private PaletteEntry() {}

        public ItemID token() { return token; }
        public int color() { return color; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PaletteEntry other)) return false;
            return color == other.color && Objects.equals(token, other.token);
        }

        @Override
        public int hashCode() { return Objects.hash(token, color); }
    }

    // ==================================================================================
    // Stylesheet Rule
    // ==================================================================================

    /**
     * A stylesheet rule — selector string + property list.
     *
     * <p>The selector syntax matches the Surface system's selector format.
     * Properties are string key-value pairs resolved by platform renderers.
     */
    @Canonical.Canonization
    public static final class Rule implements Canonical {

        @Canon(order = 0)
        private String selector;

        @Canon(order = 1)
        private List<Property> properties;

        public Rule(String selector, List<Property> properties) {
            this.selector = Objects.requireNonNull(selector, "selector");
            this.properties = properties != null ? List.copyOf(properties) : List.of();
        }

        @SuppressWarnings("unused")
        private Rule() {}

        public String selector() { return selector; }
        public List<Property> properties() { return properties; }
    }

    /**
     * A single style property — key-value string pair.
     */
    @Canonical.Canonization
    public static final class Property implements Canonical {

        @Canon(order = 0)
        private String key;

        @Canon(order = 1)
        private String value;

        public Property(String key, String value) {
            this.key = Objects.requireNonNull(key, "key");
            this.value = Objects.requireNonNull(value, "value");
        }

        @SuppressWarnings("unused")
        private Property() {}

        public String key() { return key; }
        public String value() { return value; }
    }
}
