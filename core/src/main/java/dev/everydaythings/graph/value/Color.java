package dev.everydaythings.graph.value;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.operator.compare.Between;
import java.util.List;
import java.util.Objects;

/**
 * Color — the one-stop home for color in the graph.
 *
 * <p>This single file plays three roles:
 * <ol>
 *   <li><b>The Color archetype</b> — a {@link Value} shape whose
 *       instance bodies carry R/G/B/A (or H/S/L) channel bindings.  The
 *       canonical tiny-value-body shape:
 *       <pre>Body[head=Color, R=255, G=128, B=0]</pre></li>
 *   <li><b>The runtime mint class</b> — annotated {@link Seed.Mints @Seed.Mints} so
 *       CREATE-of-Color can dispatch here at runtime.  Construction is via the
 *       static factories on this class ({@link #rgb}, {@link #rgba},
 *       {@link #web}, {@link #fromPacked}); Color is a plain value-class, not
 *       an Item.</li>
 *   <li><b>The home of the named-color sememes</b> — White, Black, Red, etc.
 *       Each is a nested {@code @Seed.Item} whose manifest body IS a Color
 *       value (head=Color, R/G/B bindings) and whose lexeme/gloss frames make
 *       it findable by name in any language.</li>
 * </ol>
 *
 * <p>The R/G/B/A and H/S/L channel sememes (used as binding-roles inside any
 * Color-shaped body) also live here.  Visual binding-role qualities that
 * <i>target</i> colors (Foreground, Background, BorderColor, ...) live in
 * {@code quality/VisualVocabulary} — they're not colors themselves.
 */
@Seed.Item(key = Color.KEY, head = Value.KEY)
@Seed.Mints(key = Color.KEY)
@Seed.Cili("i63025")
public final class Color extends Value {

    public static final String KEY = "cg.archetype:color";

    // ==================================================================================
    // Archetype-level lexical and schema frames
    // ==================================================================================

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "the archetype of color values — bodies with R/G/B/A or H/S/L channel "
                    + "bindings; instances are color values, not items";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY,
        qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "color";

    // ==================================================================================
    // EXPECTS — instances of Color carry R, G, B, A bindings whose targets lie
    // in the 0..255 range.  Declared via schema-roled bindings on this
    // archetype's manifest; targets are inline BETWEEN bodies that, under
    // partial application, act as matchers over candidate target values.
    //
    //   !R = BETWEEN { SOURCE=0, GOAL=255 }
    //   !G = BETWEEN { SOURCE=0, GOAL=255 }
    //   !B = BETWEEN { SOURCE=0, GOAL=255 }
    //   !A = BETWEEN { SOURCE=0, GOAL=255 }
    // ==================================================================================

    @Seed.Property(schemaRole = R.KEY)
    static final Body expectsR = channelRange();

    @Seed.Property(schemaRole = G.KEY)
    static final Body expectsG = channelRange();

    @Seed.Property(schemaRole = B.KEY)
    static final Body expectsB = channelRange();

    @Seed.Property(schemaRole = A.KEY)
    static final Body expectsA = channelRange();

    /** Inline {@code BETWEEN { SOURCE=0, GOAL=255 }} body — the range a channel target must satisfy. */
    private static Body channelRange() {
        return (Body) Body.compose(ItemRef.iid(Between.KEY))
                .binding(ItemRef.iid(ThematicRole.Source.KEY)).target(0L)
                .binding(ItemRef.iid(ThematicRole.Goal.KEY)).target(255L)
                .build();
    }

    // ==================================================================================
    // Construction — every Color IS a Body (head=Color archetype, R/G/B/A bindings).
    // Identity is the structural Merkle hash inherited from Datum.
    // ==================================================================================

    /**
     * Build a Color from its four channel values.  The instance IS a Body with
     * head pointing at the Color archetype and four channel bindings.  Identity
     * (the {@code DatumRef}) is computed lazily on first call to
     * {@link #datumId()}; in-VM construction is cost-free until something asks
     * for the hash.
     */
    public Color(int red, int green, int blue, int alpha) {
        super(ItemRef.iid(KEY), channelBindings(red, green, blue, alpha));
    }

    private static List<Binding> channelBindings(
            int red, int green, int blue, int alpha) {
        checkRange(red,   "red");
        checkRange(green, "green");
        checkRange(blue,  "blue");
        checkRange(alpha, "alpha");
        return List.of(Binding.literal(ItemRef.iid(R.KEY), (long) red),
                Binding.literal(ItemRef.iid(G.KEY), (long) green),
                Binding.literal(ItemRef.iid(B.KEY), (long) blue),
                Binding.literal(ItemRef.iid(A.KEY), (long) alpha)
        );
    }

    // ==================================================================================
    // Static factories
    // ==================================================================================

    /** Opaque color from RGB components (0–255). */
    public static Color rgb(int r, int g, int b) {
        return new Color(r, g, b, 255);
    }

    /** Color from RGBA components (0–255). */
    public static Color rgba(int r, int g, int b, int a) {
        return new Color(r, g, b, a);
    }

    /** Opaque color from a packed 0xRRGGBB int. */
    public static Color fromPacked(int rgb) {
        return new Color(
                (rgb >> 16) & 0xFF,
                (rgb >> 8) & 0xFF,
                rgb & 0xFF,
                255);
    }

    /**
     * Parse a CSS-style hex color string.  Accepted forms (leading {@code #}
     * optional): {@code RGB}, {@code RRGGBB}, {@code RRGGBBAA}.
     */
    public static Color web(String hex) {
        Objects.requireNonNull(hex, "hex");
        String s = hex.startsWith("#") ? hex.substring(1) : hex;
        return switch (s.length()) {
            case 3 -> {
                int r = Integer.parseInt(s.substring(0, 1), 16);
                int g = Integer.parseInt(s.substring(1, 2), 16);
                int b = Integer.parseInt(s.substring(2, 3), 16);
                yield new Color(r * 17, g * 17, b * 17, 255);
            }
            case 6 -> {
                int v = Integer.parseInt(s, 16);
                yield new Color((v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF, 255);
            }
            case 8 -> {
                long v = Long.parseLong(s, 16);
                yield new Color(
                        (int) ((v >> 24) & 0xFF),
                        (int) ((v >> 16) & 0xFF),
                        (int) ((v >> 8) & 0xFF),
                        (int) (v & 0xFF));
            }
            default -> throw new IllegalArgumentException("Invalid hex color: " + hex);
        };
    }

    /**
     * Typed view over an existing Body whose head is the Color archetype.  Reads
     * R/G/B/A from the body's bindings (A defaults to 255 when missing) and
     * returns a Color with those channel values.  Bindings on the source body
     * other than R/G/B/A are dropped from the view; full-fidelity round-trip
     * arrives with the head-IID dispatch registry in {@link
     * dev.everydaythings.graph.encoding.CgCbor#decodeBody}.
     */
    public static Color from(Body body) {
        Objects.requireNonNull(body, "body");
        if (body instanceof Color color) return color;
        if (!ItemRef.iid(KEY).equals(body.headRef())) {
            throw new IllegalArgumentException(
                    "Body head is not the Color archetype: " + body.headRef());
        }
        return new Color(
                channelOf(body, R.KEY, 0),
                channelOf(body, G.KEY, 0),
                channelOf(body, B.KEY, 0),
                channelOf(body, A.KEY, 255));
    }

    private static int channelOf(Body body, String roleKey, int defaultValue) {
        ItemRef role = ItemRef.iid(roleKey);
        for (dev.everydaythings.graph.datum.Binding b : body.bindings()) {
            if (b.role().equals(role) && b.target() instanceof Long n) return n.intValue();
        }
        return defaultValue;
    }

    // ==================================================================================
    // Channel accessors — read from the underlying bindings list.
    // ==================================================================================

    public int red()   { return channel(R.KEY); }
    public int green() { return channel(G.KEY); }
    public int blue()  { return channel(B.KEY); }
    public int alpha() { return channel(A.KEY); }

    public double redDouble()   { return red()   / 255.0; }
    public double greenDouble() { return green() / 255.0; }
    public double blueDouble()  { return blue()  / 255.0; }
    public double alphaDouble() { return alpha() / 255.0; }

    private int channel(String roleKey) {
        ItemRef role = ItemRef.iid(roleKey);
        for (dev.everydaythings.graph.datum.DatumNode entry : entries) {
            if (!(entry instanceof dev.everydaythings.graph.datum.Binding b)) continue;
            if (b.role().equals(role) && b.target() instanceof Long n) return n.intValue();
        }
        return 0;
    }

    // ==================================================================================
    // Derived operations
    // ==================================================================================

    /** Pack as 0xRRGGBB (alpha dropped). */
    public int toPacked() {
        return (red() << 16) | (green() << 8) | blue();
    }

    /** Copy with the given alpha (0–255). */
    public Color withAlpha(int a) {
        return new Color(red(), green(), blue(), a);
    }

    /** Darker copy.  Factor 0.0 = black, 1.0 = unchanged. */
    public Color darken(double factor) {
        if (factor < 0 || factor > 1)
            throw new IllegalArgumentException("factor must be 0.0–1.0, got " + factor);
        return new Color(
                (int) (red()   * factor),
                (int) (green() * factor),
                (int) (blue()  * factor),
                alpha());
    }

    /** 24-bit ANSI foreground escape. */
    public String toAnsiForeground() {
        return String.format("\\u001B[38;2;%d;%d;%dm", red(), green(), blue());
    }

    /** 24-bit ANSI background escape (darkened to 40% for readability). */
    public String toAnsiBackground() {
        return String.format("\\u001B[48;2;%d;%d;%dm",
                (int) (red()   * 0.4),
                (int) (green() * 0.4),
                (int) (blue()  * 0.4));
    }

    @Override
    public String toString() {
        int a = alpha();
        return a == 255
                ? String.format("#%02X%02X%02X", red(), green(), blue())
                : String.format("#%02X%02X%02X%02X", red(), green(), blue(), a);
    }

    private static void checkRange(int value, String name) {
        if (value < 0 || value > 255)
            throw new IllegalArgumentException(name + " must be 0–255, got " + value);
    }

    // ==================================================================================
    // RGBA channel sememes — binding-roles inside Color-shaped value bodies
    // ==================================================================================

    /** Red channel of an RGB color value (0–255). */
    @Seed.Item(key = R.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class R {
        public static final String KEY = "cg.color:r";
        private R() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the red channel of an RGB color";
    }

    /** Green channel of an RGB color value (0–255). */
    @Seed.Item(key = G.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class G {
        public static final String KEY = "cg.color:g";
        private G() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the green channel of an RGB color";
    }

    /** Blue channel of an RGB color value (0–255). */
    @Seed.Item(key = B.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class B {
        public static final String KEY = "cg.color:b";
        private B() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the blue channel of an RGB color";
    }

    /** Alpha (opacity) channel of an RGBA color value (0–255). */
    @Seed.Item(key = A.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class A {
        public static final String KEY = "cg.color:a";
        private A() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the alpha (opacity) channel of an RGBA color";
    }

    // ==================================================================================
    // HSL channel sememes — alternative to RGB for color-space-aware bodies
    // ==================================================================================

    /** Hue channel of an HSL color value. */
    @Seed.Item(key = H.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class H {
        public static final String KEY = "cg.color:h";
        private H() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the hue channel of an HSL color";
    }

    /** Saturation channel of an HSL color value. */
    @Seed.Item(key = S.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class S {
        public static final String KEY = "cg.color:s";
        private S() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the saturation channel of an HSL color";
    }

    /** Lightness channel of an HSL color value. */
    @Seed.Item(key = L.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class L {
        public static final String KEY = "cg.color:l";
        private L() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the lightness channel of an HSL color";
    }

    // ==================================================================================
    // Named color sememes — singletons whose manifest body IS a Color value.
    //
    // Channel data is declared field-level via @Seed.Property; the static fields
    // are also Java-callable (e.g., Color.White.r).  Lexeme/gloss frames make
    // each color findable by name in any language.
    // ==================================================================================

    // ----- Achromatic ------------------------------------------------------------------

    @Seed.Item(key = White.KEY, head = Color.KEY)
    public static final class White {
        public static final String KEY = "cg.color:white";
        private White() {}

        @Seed.Property(role = R.KEY) public static final int r = 255;
        @Seed.Property(role = G.KEY) public static final int g = 255;
        @Seed.Property(role = B.KEY) public static final int b = 255;

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "achromatic color of maximum lightness";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "white";
    }

    @Seed.Item(key = Black.KEY, head = Color.KEY)
    public static final class Black {
        public static final String KEY = "cg.color:black";
        private Black() {}

        @Seed.Property(role = R.KEY) public static final int r = 0;
        @Seed.Property(role = G.KEY) public static final int g = 0;
        @Seed.Property(role = B.KEY) public static final int b = 0;

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "achromatic color of minimum lightness";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "black";
    }

    @Seed.Item(key = Gray.KEY, head = Color.KEY)
    public static final class Gray {
        public static final String KEY = "cg.color:gray";
        private Gray() {}

        @Seed.Property(role = R.KEY) public static final int r = 128;
        @Seed.Property(role = G.KEY) public static final int g = 128;
        @Seed.Property(role = B.KEY) public static final int b = 128;

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "neutral midtone between black and white";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "gray";
    }

    // ----- Primary ---------------------------------------------------------------------

    @Seed.Item(key = Red.KEY, head = Color.KEY)
    public static final class Red {
        public static final String KEY = "cg.color:red";
        private Red() {}

        @Seed.Property(role = R.KEY) public static final int r = 255;
        @Seed.Property(role = G.KEY) public static final int g = 0;
        @Seed.Property(role = B.KEY) public static final int b = 0;

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the color red";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "red";
    }

    @Seed.Item(key = Green.KEY, head = Color.KEY)
    public static final class Green {
        public static final String KEY = "cg.color:green";
        private Green() {}

        @Seed.Property(role = R.KEY) public static final int r = 0;
        @Seed.Property(role = G.KEY) public static final int g = 255;
        @Seed.Property(role = B.KEY) public static final int b = 0;

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the color green";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "green";
    }

    @Seed.Item(key = Blue.KEY, head = Color.KEY)
    public static final class Blue {
        public static final String KEY = "cg.color:blue";
        private Blue() {}

        @Seed.Property(role = R.KEY) public static final int r = 0;
        @Seed.Property(role = G.KEY) public static final int g = 0;
        @Seed.Property(role = B.KEY) public static final int b = 255;

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the color blue";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "blue";
    }

    // ----- Secondary -------------------------------------------------------------------

    @Seed.Item(key = Yellow.KEY, head = Color.KEY)
    public static final class Yellow {
        public static final String KEY = "cg.color:yellow";
        private Yellow() {}

        @Seed.Property(role = R.KEY) public static final int r = 255;
        @Seed.Property(role = G.KEY) public static final int g = 255;
        @Seed.Property(role = B.KEY) public static final int b = 0;

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the color yellow";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "yellow";
    }

    @Seed.Item(key = Cyan.KEY, head = Color.KEY)
    public static final class Cyan {
        public static final String KEY = "cg.color:cyan";
        private Cyan() {}

        @Seed.Property(role = R.KEY) public static final int r = 0;
        @Seed.Property(role = G.KEY) public static final int g = 255;
        @Seed.Property(role = B.KEY) public static final int b = 255;

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the color cyan";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "cyan";
    }

    @Seed.Item(key = Magenta.KEY, head = Color.KEY)
    public static final class Magenta {
        public static final String KEY = "cg.color:magenta";
        private Magenta() {}

        @Seed.Property(role = R.KEY) public static final int r = 255;
        @Seed.Property(role = G.KEY) public static final int g = 0;
        @Seed.Property(role = B.KEY) public static final int b = 255;

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the color magenta";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "magenta";
    }

    // ----- Tertiary / common -----------------------------------------------------------

    @Seed.Item(key = Orange.KEY, head = Color.KEY)
    public static final class Orange {
        public static final String KEY = "cg.color:orange";
        private Orange() {}

        @Seed.Property(role = R.KEY) public static final int r = 255;
        @Seed.Property(role = G.KEY) public static final int g = 128;
        @Seed.Property(role = B.KEY) public static final int b = 0;

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the color orange";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "orange";
    }

    @Seed.Item(key = Purple.KEY, head = Color.KEY)
    public static final class Purple {
        public static final String KEY = "cg.color:purple";
        private Purple() {}

        @Seed.Property(role = R.KEY) public static final int r = 128;
        @Seed.Property(role = G.KEY) public static final int g = 0;
        @Seed.Property(role = B.KEY) public static final int b = 128;

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the color purple";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "purple";
    }

    @Seed.Item(key = Pink.KEY, head = Color.KEY)
    public static final class Pink {
        public static final String KEY = "cg.color:pink";
        private Pink() {}

        @Seed.Property(role = R.KEY) public static final int r = 255;
        @Seed.Property(role = G.KEY) public static final int g = 192;
        @Seed.Property(role = B.KEY) public static final int b = 203;

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the color pink";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "pink";
    }

    @Seed.Item(key = Brown.KEY, head = Color.KEY)
    public static final class Brown {
        public static final String KEY = "cg.color:brown";
        private Brown() {}

        @Seed.Property(role = R.KEY) public static final int r = 139;
        @Seed.Property(role = G.KEY) public static final int g = 69;
        @Seed.Property(role = B.KEY) public static final int b = 19;

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the color brown";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "brown";
    }
}
