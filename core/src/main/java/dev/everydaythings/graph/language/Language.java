package dev.everydaythings.graph.language;

import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.FrameOld;
import dev.everydaythings.graph.frame.FrameBodyOld;
import dev.everydaythings.graph.frame.ItemFrame;
import dev.everydaythings.graph.item.*;
import dev.everydaythings.graph.item.ItemOld;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Eval;
import dev.everydaythings.graph.runtime.LibrarianOld;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** TODO: what... are we going to do about "regions"?  Like... "en-us" and "en-uk"... we haven't dealt with that at all yet.
 * A natural language with its lexicon.
 *
 * <p>Language items contain:
 * <ul>
 *   <li>Language code (ISO 639-3, 3 letters)</li>
 *   <li>Lexicon - word→sememe mappings for this language</li>
 * </ul>
 *
 * <p>All ~7,000 ISO 639-3 languages are seeded at bootstrap as Language items
 * with deterministic IIDs derived from {@code "cg:language/<code>"}. Subclasses
 * (e.g., English) can add language-specific import logic.
 */
@Implements(Language.KEY)
@ItemSeed(key = Language.KEY)
public class Language extends ItemOld {

    // ==================================================================================
    // TYPE DEFINITION
    // ==================================================================================

    public static final String KEY = "cg.sememe:language";

    /** The English Language KEY and IID — used to scope seed English lexemes. */
    public static final String ENGLISH_KEY = "cg:language/eng";
    public static final ItemID ENGLISH = ItemID.fromString(ENGLISH_KEY);

    /** German Language KEY and IID. */
    public static final String GERMAN_KEY = "cg:language/deu";
    public static final ItemID GERMAN = ItemID.fromString(GERMAN_KEY);

    // ==================================================================================
    // FIELDS
    // ==================================================================================

    /** ISO 639 language code (2 or 3 letter). */
    @Getter
    @ItemFrame(predicate = CoreVocabulary.LanguageCode.KEY)
    protected String languageCode;

    // ==================================================================================
    // CONSTRUCTORS
    // ==================================================================================

    /**
     * Create a language from a Java Locale.
     *
     * @param librarian The librarian for storage
     * @param locale    The Java locale for this language
     */
    public Language(LibrarianOld librarian, Locale locale) {
        super(librarian);
        this.languageCode = locale.getISO3Language();  // Use ISO 639-3 (3-letter)
    }

    /**
     * Type seed constructor - creates a minimal Language for use as type seed.
     *
     * <p>Used by SeedStore to create type items. Does NOT create a Lexicon
     * since type seeds are just markers, not functional language instances.
     *
     * @param iid The type's ItemID
     */
    public Language(ItemID iid) {
        super(iid);
        // Type seeds don't have content - just the type marker
    }

    /**
     * Create a language with specific IID and language code.
     *
     * <p>Used for seeding language items from ISO 639-3 codes.
     * Does NOT create a Lexicon — seed items are minimal markers.
     * The Lexicon is created when the language is hydrated or used functionally.
     *
     * @param iid          The item ID (e.g., ItemID.fromString("cg:language/eng"))
     * @param languageCode The ISO 639-3 code (e.g., "eng")
     */
    public Language(ItemID iid, String languageCode) {
        super(iid);
        this.languageCode = languageCode;
        // No lexicon for seed items — avoids encoding cost and IllegalStateException
        // since Lexicon is not @Type/Canonical/simple-serializable
    }

    /**
     * Create a language with specific IID (for seed items with content).
     *
     * @param iid    The item ID
     * @param locale The Java locale
     */
    public Language(ItemID iid, Locale locale) {
        super(iid);
        this.languageCode = locale.getISO3Language();
    }

    /**
     * Hydration constructor - reconstructs a Language from a stored manifest.
     *
     * <p>Fields are bound via reflection in the base class hydrate() method.
     */
    @SuppressWarnings("unused")  // Used via reflection for hydration
    protected Language(LibrarianOld librarian, ManifestOld manifest) {
        super(librarian, manifest);
        // Fields are set by bindFieldsFromTable() via reflection during super() call
    }

    // ==================================================================================
    // DISPLAY
    // ==================================================================================

    @Override
    public String displayToken() {
        if (languageCode != null && !languageCode.isBlank()) {
            String name = LANGUAGE_NAMES.get(languageCode);
            if (name != null) return name;
        }
        return super.displayToken();
    }

    /** ISO 639-3 code → English name, loaded from bundled resource. */
    private static final Map<String, String> LANGUAGE_NAMES = loadLanguageNames();

    private static Map<String, String> loadLanguageNames() {
        Map<String, String> map = new java.util.HashMap<>();
        for (String[] entry : dev.everydaythings.graph.library.SeedVocabulary.loadLanguageCodes()) {
            map.put(entry[0], entry[1]);
        }
        return Map.copyOf(map);
    }

    // ==================================================================================
    // MORPHOLOGY
    // ==================================================================================

    /**
     * Inflect a lexeme for the given grammatical features.
     *
     * <p>Checks the lexeme's irregular form overrides first. If no override
     * exists for the feature set, falls back to {@link #regularInflection}
     * which applies the language's algorithmic rules.
     *
     * @param lexeme   The lexeme (carries the lemma, POS, and irregular overrides)
     * @param features Set of grammatical feature IIDs (e.g., {PAST}, {PLURAL})
     * @return The inflected surface form
     */
    public String inflect(Lexeme lexeme, Set<ItemID> features) {
        if (features == null || features.isEmpty()) return lexeme.word();
        String override = lexeme.lookupForm(features);
        if (override != null) return override;
        return regularInflection(lexeme.word(), lexeme.partOfSpeech(), features);
    }

    /**
     * Inflect a bare word with no override data (pure algorithm).
     *
     * <p>Useful for generating forms of new/unknown words where no
     * lexeme with overrides is available.
     *
     * @param lemma    The base form of the word
     * @param pos      Part of speech
     * @param features Set of grammatical feature IIDs
     * @return The inflected surface form
     */
    public String inflect(String lemma, ItemID pos, Set<ItemID> features) {
        if (features == null || features.isEmpty()) return lemma;
        return regularInflection(lemma, pos, features);
    }

    /**
     * Produce the regular inflected form for a given feature set.
     *
     * <p>Default returns the lemma unchanged. Language subclasses override
     * this with their specific regular morphology rules.
     *
     * @param lemma    The base form
     * @param pos      Part of speech
     * @param features Grammatical features driving inflection
     * @return The regular inflected form
     */
    protected String regularInflection(String lemma, ItemID pos, Set<ItemID> features) {
        return lemma;
    }

    /**
     * Get the grammatical feature sets that this language's morphology distinguishes.
     *
     * <p>Returns the feature combinations that the morphology engine can produce
     * inflected forms for. For example, English verbs distinguish:
     * {PAST}, {PARTICIPLE,PRESENT}, {PARTICIPLE,PAST}, {THIRD_PERSON}.
     *
     * <p>Used by import to know which inflected forms to generate and register
     * as TokenDictionary postings, and by UniMorph simplification to map
     * raw feature sets to the minimal sets this language actually uses.
     *
     * <p>Default returns empty — no inflection. Language subclasses override.
     *
     * @param pos Part of speech
     * @return The feature sets this language distinguishes for the given POS
     */
    public List<Set<ItemID>> inflectionFeatures(ItemID pos) {
        return List.of();
    }

    // ==================================================================================
    // Parsing — language-specific token interpretation
    // ==================================================================================

    /**
     * Result of language parsing — complete frames and unbound tokens.
     *
     * <p>Three meaningful states:
     * <ul>
     *   <li><b>Frames only</b> — executable commands</li>
     *   <li><b>Unbound only</b> — query pattern (bare nouns, literals)</li>
     *   <li><b>Both</b> — ambiguous input, should error</li>
     * </ul>
     *
     * @param frames  complete semantic frames (predicate + filled bindings)
     * @param unbound tokens the Language couldn't place into any frame
     */
    public record ParseResult(List<SemanticFrame> frames, List<Eval.ResolvedToken> unbound) {
        public boolean hasFrames() { return frames != null && !frames.isEmpty(); }
        public boolean hasUnbound() { return unbound != null && !unbound.isEmpty(); }
        public boolean isAmbiguous() { return hasFrames() && hasUnbound(); }

        public static ParseResult frames(List<SemanticFrame> frames) {
            return new ParseResult(frames, List.of());
        }
        public static ParseResult unbound(List<Eval.ResolvedToken> tokens) {
            return new ParseResult(List.of(), tokens);
        }
        public static ParseResult empty() {
            return new ParseResult(List.of(), List.of());
        }
    }

    /**
     * Parse resolved tokens into frames and unbound tokens.
     *
     * <p>The base implementation delegates to {@link FrameAssembler} for
     * language-agnostic parsing. Language subclasses (e.g., English) override
     * to add language-specific grammar rules: word order, auxiliary predicates
     * ("named", "as"), clause patterns, etc.
     *
     * <p>The parser receives BOTH the resolved token list AND the raw text,
     * so it can use positional cues, punctuation, or grammar that token
     * resolution strips away.
     *
     * @param tokens   ordered list of resolved/unresolved/ambiguous tokens
     * @param rawText  the original text as typed by the user
     * @param resolver resolves ItemIDs to Items
     * @param headVerbScorer scores verbs by context relevance
     * @return parse result with frames and/or unbound tokens
     */
    public ParseResult parse(
            List<Eval.ResolvedToken> tokens,
            String rawText,
            java.util.function.Function<ItemID, java.util.Optional<ItemOld>> resolver,
            java.util.function.ToIntFunction<Sememe> headVerbScorer) {
        List<SemanticFrame> frames = FrameAssembler.assembleAll(tokens, resolver, headVerbScorer);
        if (!frames.isEmpty()) {
            return ParseResult.frames(frames);
        }
        // No predicate found — all tokens are unbound (query pattern)
        return ParseResult.unbound(tokens);
    }

    // ==================================================================================
    // Expression — semantic frame → natural language text
    // ==================================================================================

    /**
     * Express a semantic frame as natural language text.
     *
     * <p>The dual of {@link #parse}: parse converts text → semantics,
     * express converts semantics → text. The Language chooses word order,
     * prepositions, verb forms, and register.
     *
     * <p>Base implementation uses the terse register: {@code "predicate: value1, value2"}.
     * Symbols are preferred over words when available (e.g., "=" not "equals").
     * The owner binding (the item being viewed) is omitted — it's implicit.
     *
     * @param body    the frame to express
     * @param ownerIid the item being viewed (its binding is omitted), nullable
     * @param lib     librarian for resolving item references to display text
     * @return natural language text expressing the frame's assertion
     */
    public String express(FrameBodyOld body, ItemID ownerIid, LibrarianOld lib) {
        if (body == null) return "frame";

        String predDisplay = predicateDisplay(body.predicate(), lib);
        boolean symbolic = isSymbolic(predDisplay);

        // For symbolic infix predicates (=, +, -, etc.), render as "left symbol right"
        // using the predicate's declared role order (THEME first, then VALUE/GOAL)
        if (symbolic) {
            String left = expressBinding(body, ThematicRole.Theme.IID, ownerIid, lib);
            // Try VALUE first (for EQUALS), then GOAL (for operators)
            String right = expressBinding(body, ThematicRole.Value.IID, ownerIid, lib);
            if (right == null) {
                right = expressBinding(body, ThematicRole.Goal.IID, ownerIid, lib);
            }
            if (left != null && right != null) {
                return left + " " + predDisplay + " " + right;
            }
            // Prefix unary: "-x"
            if (left != null) {
                return predDisplay + left;
            }
        }

        // Word predicates: "predicate: value1, value2"
        List<String> parts = new ArrayList<>();
        for (Binding b : body.frameBindings()) {
            // Skip owner binding — it's the item we're looking at
            ItemID tid = b.targetId();
            if (tid != null && ownerIid != null && tid.equals(ownerIid)) continue;

            String value = expressTarget(b.target(), ownerIid, lib);
            if (value != null && !value.isBlank()) {
                parts.add(value);
            }
        }

        if (parts.isEmpty()) return predDisplay != null ? predDisplay : "frame";
        return (predDisplay != null ? predDisplay + ": " : "") + String.join(", ", parts);
    }

    /**
     * Express a specific role's binding from a frame body.
     */
    private String expressBinding(FrameBodyOld body, ItemID role, ItemID ownerIid, LibrarianOld lib) {
        BindingTarget target = body.binding(role);
        if (target == null) return null;
        // Skip if it's the owner
        if (target instanceof BindingTarget.IidTarget iid
                && ownerIid != null && iid.iid().equals(ownerIid)) return null;
        return expressTarget(target, ownerIid, lib);
    }

    /**
     * Get the display text for a predicate — symbol first, then lexeme/display name.
     *
     * <p>Checks the item's SYMBOL frames directly (not the transient field,
     * which may not be hydrated on cached seeds).
     */
    protected String predicateDisplay(ItemID predicate, LibrarianOld lib) {
        if (predicate == null || lib == null) return null;

        Optional<ItemOld> item = lib.get(predicate);
        if (item.isEmpty()) return null;

        // Check SYMBOL frames on the item for a universal symbol
        ItemOld pred = item.get();
        if (pred.frames() != null) {
            for (FrameOld frame : pred.frames()) {
                FrameBodyOld fb = frame.body();
                if (fb == null) continue;
                if (!CoreVocabulary.Symbol.IID.equals(fb.predicate())) continue;
                // Extract the VALUE binding text
                BindingTarget vt = fb.binding(ThematicRole.Value.IID);
                if (vt instanceof Literal lit && Literal.TYPE_TEXT.equals(lit.valueType())) {
                    try {
                        String sym = lit.asText();
                        if (sym != null && !sym.isBlank()) return sym;
                    } catch (Exception ignored) {}
                }
            }
        }

        return pred.displayToken();
    }

    /**
     * Express a binding target as text.
     */
    protected String expressTarget(BindingTarget target, ItemID ownerIid, LibrarianOld lib) {
        if (target == null) return null;

        if (target instanceof Literal lit) {
            return expressLiteral(lit);
        }

        if (target instanceof BindingTarget.FrameTarget ft) {
            // Nested frame — recurse (e.g., ADD sub-expression)
            return express(ft.body(), ownerIid, lib);
        }

        if (target instanceof BindingTarget.IidTarget iid) {
            if (lib != null) {
                return lib.get(iid.iid()).map(ItemOld::displayToken).orElse(null);
            }
            return iid.iid().displayAtWidth(12);
        }

        if (target instanceof BindingTarget.RefTarget ref) {
            if (lib != null) {
                return lib.get(ref.asItemId()).map(ItemOld::displayToken).orElse(null);
            }
        }

        return null;
    }

    /**
     * Express a literal value as text.
     */
    protected String expressLiteral(Literal lit) {
        if (lit == null) return null;
        if (Literal.TYPE_TEXT.equals(lit.valueType())) {
            try {
                String text = lit.asText();
                if (text == null) return null;
                // Quote strings that look like they need quoting
                return "\"" + (text.length() > 40 ? text.substring(0, 37) + "..." : text) + "\"";
            } catch (Exception e) {
                return null;
            }
        }
        try { return String.valueOf(lit.asInteger()); } catch (Exception ignored) {}
        try { return String.valueOf(lit.asBoolean()); } catch (Exception ignored) {}
        return null;
    }

    /**
     * Check if a predicate display string is a symbol (not a word).
     */
    private static boolean isSymbolic(String s) {
        return s != null && !s.isEmpty() && !Character.isLetter(s.charAt(0));
    }

    /**
     * Simplify a raw UniMorph feature set to the minimal set this language uses.
     *
     * <p>UniMorph provides rich feature sets (e.g., {THIRD_PERSON, SINGULAR,
     * PRESENT} for English 3rd person singular). This method reduces them to
     * the minimal set the morphology engine actually keys on (e.g., just
     * {THIRD_PERSON} for English, since that's sufficient to disambiguate).
     *
     * <p>Default implementation finds the largest known feature set (from
     * {@link #inflectionFeatures}) that is a subset of the raw features.
     * Language subclasses can override for special cases (e.g., English
     * maps standalone {PARTICIPLE} to {PARTICIPLE, PRESENT}).
     *
     * @param rawFeatures The raw mapped features from UniMorph
     * @param pos         Part of speech
     * @return The simplified feature set, or empty if no match
     */
    public Set<ItemID> simplifyFeatures(Set<ItemID> rawFeatures, ItemID pos) {
        List<Set<ItemID>> known = inflectionFeatures(pos);
        Set<ItemID> best = Set.of();
        for (Set<ItemID> candidate : known) {
            if (rawFeatures.containsAll(candidate) && candidate.size() > best.size()) {
                best = candidate;
            }
        }
        return best;
    }

    // ==================================================================================
    // STATIC HELPERS
    // ==================================================================================

    /**
     * Compute the deterministic IID for a language from its ISO 639-3 code.
     *
     * @param iso639_3 The 3-letter language code (e.g., "eng", "spa", "jpn")
     * @return The deterministic ItemID
     */
    public static ItemID iidFor(String iso639_3) {
        return ItemID.fromString("cg:language/" + iso639_3);
    }
}
