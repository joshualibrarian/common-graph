package dev.everydaythings.graph.linguistics;

import dev.everydaythings.graph.item.Seed;
import dev.everydaythings.graph.item.id.ItemID;

/**
 * Language sememes — language-identifying targets of {@code LANGUAGE}-qualified
 * bindings on glosses, lexemes, and any other language-scoped data.
 *
 * <p>The outer class itself is the language meta-sememe ({@code cg.sememe:language}),
 * usable as a qualifier on EXPECTS bindings declaring "the target is a language."
 * Inner classes are specific languages.
 *
 * <p>Canonical-key prefix: {@code cg.lang:} followed by the ISO 639-3 three-letter
 * code (e.g., {@code cg.lang:eng} for English).
 *
 * <p>Currently only English is seeded. Other languages (Spanish, Mandarin, German,
 * Japanese, ...) will be added when concrete needs arise — e.g., the WordNet
 * import will bring its own language sememes per the data being imported.
 *
 * <p>Pure-data seeds.
 */
@Seed(key = Language.KEY)
public final class Language {

    /** Canonical key for the language meta-sememe. */
    public static final String KEY = "cg.sememe:language";

    /** The deterministic IID for the language meta-sememe. */
    public static final ItemID IID = ItemID.fromString(KEY);

    private Language() {}

    /** English — ISO 639-3 code "eng". */
    @Seed(key = English.KEY)
    public static final class English {
        public static final String KEY = "cg.lang:eng";
        public static final ItemID IID = ItemID.fromString(KEY);
        private English() {}
    }
}
