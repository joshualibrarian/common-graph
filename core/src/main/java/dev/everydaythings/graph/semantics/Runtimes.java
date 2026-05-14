package dev.everydaythings.graph.semantics;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.id.ItemID;
import dev.everydaythings.graph.linguistics.GrammaticalFeature;
import dev.everydaythings.graph.linguistics.Gloss;
import dev.everydaythings.graph.linguistics.Language;
import dev.everydaythings.graph.linguistics.Lexeme;
import dev.everydaythings.graph.linguistics.PartOfSpeech;
import static dev.everydaythings.graph.Seed.*;

/**
 * Runtime/language sememes — used as qualifiers on AGENT bindings of IMPLEMENTS
 * frames to indicate which runtime is needed to execute an implementation.
 *
 * <p>Distinct from natural-language sememes (which use the {@code cg.lang:} prefix
 * and ISO 639-3 codes). Runtime sememes use {@code cg.runtime:} for clarity.
 *
 * <p>Polyglot support comes online as we wire each runtime's invocation
 * machinery (GraalVM hosts, native FFI, WASM, etc.).
 */
public final class Runtimes {

    private Runtimes() {}

    /** The Java Virtual Machine runtime. */
    @Seed.Item(key = Java.KEY)
    public static final class Java {
        public static final String KEY = "cg.runtime:java";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Java() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "Java programming language runtime (JVM)";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "java";
    }

    /** The Python runtime (CPython, or via GraalPy on GraalVM). */
    @Seed.Item(key = Python.KEY)
    public static final class Python {
        public static final String KEY = "cg.runtime:python";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Python() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "Python programming language runtime";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "python";
    }

    /** The JavaScript runtime (V8, or via GraalJS on GraalVM). */
    @Seed.Item(key = JavaScript.KEY)
    public static final class JavaScript {
        public static final String KEY = "cg.runtime:javascript";
        public static final ItemID IID = ItemID.fromString(KEY);
        private JavaScript() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "JavaScript programming language runtime";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "javascript";
    }

    /** The Rust runtime / native FFI target. */
    @Seed.Item(key = Rust.KEY)
    public static final class Rust {
        public static final String KEY = "cg.runtime:rust";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Rust() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "Rust programming language runtime (native FFI)";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "rust";
    }

    /** The Clojure runtime (JVM-hosted). */
    @Seed.Item(key = Clojure.KEY)
    public static final class Clojure {
        public static final String KEY = "cg.runtime:clojure";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Clojure() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "Clojure programming language runtime (JVM-hosted Lisp)";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "clojure";
    }
}
