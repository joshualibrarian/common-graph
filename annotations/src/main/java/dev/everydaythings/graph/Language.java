package dev.everydaythings.graph;

/**
 * Foundational seed declarations for languages and the two universal lexical
 * predicates (Gloss, Lexeme).  Pure data — no behavior, no Item ancestry.
 *
 * <p>The runtime {@code Language extends Item} class in :core/language attaches
 * to the seed declared here via {@code @Seed.Embodies}.  Specific languages
 * (English, German, etc.) each have a runtime implementation in their respective
 * {@code :languages/*} module that does the same — declares behavior, binds
 * to the seed via {@code @Seed.Embodies}.
 *
 * <p>Canonical-key conventions:
 * <ul>
 *   <li>Meta-sememe: {@code cg.sememe:*}</li>
 *   <li>Specific languages: {@code cg.lang:*} (ISO 639-3 three-letter code)</li>
 * </ul>
 */
@Seed.Item(key = Language.KEY)
public final class Language {

    public static final String KEY = "cg.sememe:language";

    private Language() {}

    // ==================================================================================
    // Universal lexical predicates
    // ==================================================================================

    /** Gloss — a textual definition of a sememe in some natural language. */
    @Seed.Item(key = Gloss.KEY)
    public static final class Gloss {
        public static final String KEY = "cg.sememe:gloss";
        private Gloss() {}
    }

    /** Lexeme — a surface form (word, phrase) of a sememe in some natural language. */
    @Seed.Item(key = Lexeme.KEY)
    public static final class Lexeme {
        public static final String KEY = "cg.sememe:lexeme";
        private Lexeme() {}
    }

    // ==================================================================================
    // Specific languages — head = Language.KEY marks each as an instance of Language
    // ==================================================================================

    /** English — ISO 639-3 "eng". */
    @Seed.Item(key = English.KEY, head = Language.KEY)
    public static final class English {
        public static final String KEY = "cg.lang:eng";
        private English() {}
    }

    /** German (Deutsch) — ISO 639-3 "deu". */
    @Seed.Item(key = German.KEY, head = Language.KEY)
    public static final class German {
        public static final String KEY = "cg.lang:deu";
        private German() {}
    }

    /** Spanish (Español) — ISO 639-3 "spa". */
    @Seed.Item(key = Spanish.KEY, head = Language.KEY)
    public static final class Spanish {
        public static final String KEY = "cg.lang:spa";
        private Spanish() {}
    }

    /** French (Français) — ISO 639-3 "fra". */
    @Seed.Item(key = French.KEY, head = Language.KEY)
    public static final class French {
        public static final String KEY = "cg.lang:fra";
        private French() {}
    }

    /** Mandarin Chinese — ISO 639-3 "cmn" (the specific Mandarin macrolanguage member). */
    @Seed.Item(key = Mandarin.KEY, head = Language.KEY)
    public static final class Mandarin {
        public static final String KEY = "cg.lang:cmn";
        private Mandarin() {}
    }

    /** Japanese (日本語) — ISO 639-3 "jpn". */
    @Seed.Item(key = Japanese.KEY, head = Language.KEY)
    public static final class Japanese {
        public static final String KEY = "cg.lang:jpn";
        private Japanese() {}
    }
}
