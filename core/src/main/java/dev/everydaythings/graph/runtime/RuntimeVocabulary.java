package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.language.*;
import dev.everydaythings.graph.runtime.librarian.LibrarianVocabulary;

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
public final class RuntimeVocabulary {

    private RuntimeVocabulary() {}

    /** The Java Virtual Machine runtime. */
    @Seed.Item(key = Java.KEY)
    public static final class Java {
        public static final String KEY = "cg.runtime:java";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private Java() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "Java programming language runtime (JVM)";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "java";
    }

    /** The Python runtime (CPython, or via GraalPy on GraalVM). */
    @Seed.Item(key = Python.KEY)
    public static final class Python {
        public static final String KEY = "cg.runtime:python";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private Python() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "Python programming language runtime";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "python";
    }

    /** The JavaScript runtime (V8, or via GraalJS on GraalVM). */
    @Seed.Item(key = JavaScript.KEY)
    public static final class JavaScript {
        public static final String KEY = "cg.runtime:javascript";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private JavaScript() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "JavaScript programming language runtime";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "javascript";
    }

    /** The Rust runtime / native FFI target. */
    @Seed.Item(key = Rust.KEY)
    public static final class Rust {
        public static final String KEY = "cg.runtime:rust";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private Rust() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "Rust programming language runtime (native FFI)";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "rust";
    }

    /** The Clojure runtime (JVM-hosted). */
    @Seed.Item(key = Clojure.KEY)
    public static final class Clojure {
        public static final String KEY = "cg.runtime:clojure";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private Clojure() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "Clojure programming language runtime (JVM-hosted Lisp)";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "clojure";
    }

    /**
     * The Lisp runtime — the Lisp programming language family generally
     * (Common Lisp, Scheme, etc.). Specific dialects like {@link Clojure}
     * have their own sememes; use this for cases where the dialect is
     * unspecified or the implementation is dialect-agnostic.
     */
    @Seed.Item(key = Lisp.KEY)
    public static final class Lisp {
        public static final String KEY = "cg.runtime:lisp";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private Lisp() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "Lisp programming language family runtime";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "lisp";
    }

    /**
     * The {@code CONSTRUCT} predicate — post-instantiation hook for new items.
     *
     * <p>After a {@link LibrarianVocabulary.Create} frame produces a new item and commits its initial
     * manifest, the librarian submits a CONSTRUCT frame targeting the new item.
     * An archetype that wants to set up domain-specific initial state (default
     * bindings, child structures, etc.) declares a {@code @Handler(predicate=Construct.KEY)}
     * on its embodying class.
     *
     * <p>By default no archetype handles CONSTRUCT — the hook is a no-op for items
     * with no special initialization needs.
     *
     * <p>Bindings:
     *
     * <ul>
     *   <li>{@code THEME → @<new-item>} — the freshly created item.</li>
     *   <li>{@code AGENT → @<creator>} — same agent who created (carried through).</li>
     * </ul>
     */
    @Seed.Item(key = Construct.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Construct {

        public static final String KEY = "cg.predicate:construct";
        public static final ItemRef IID = ItemRef.fromString(KEY);

        private Construct() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the post-create hook predicate — fires after a CREATE produces a new item, "
                        + "so the archetype can set up its initial state (default bindings, etc.)";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "construct";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "construction";
    }

    /**
     * The archetype of all code-items — items that represent implementations of
     * an archetype's contract (a library, a class, a bundle, a script).
     *
     * <p>Distinct from {@link Item} itself: an item is a domain entity (a chess
     * game, a book, a person, a librarian); a code-item is the artifact that
     * implements an archetype's behavior in some host language or runtime. The
     * two are orthogonal — a chess game (Item) has its rules encoded in some
     * code-item (a Code instance), and that code-item is itself an Item too.
     *
     * <p>Code-items typically carry:
     * <ul>
     *   <li>An {@code ITEM_ID} binding (as every manifest body does)</li>
     *   <li>{@code ENDORSES} bindings pointing at frame bodies the code embodies
     *       — e.g., the {@code HANDLES} frames declaring its dispatch surface</li>
     *   <li>Some marker of the implementation form — class name (text), bytecode
     *       (binary), source (text), etc. Phase 1 leaves this binding off; the
     *       host language is implicit in the IID convention until we need otherwise.</li>
     * </ul>
     *
     * <p>The Java inheritance hierarchy of the implementing class is completely
     * separate from this archetypal hierarchy. Code's head is Archetype (the root);
     * it is not a kind of Item.
     */
    @Seed.Item(key = Code.KEY)
    public static final class Code {
        public static final String KEY = "cg.archetype:code";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private Code() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the archetype of code-items — items that represent implementations "
                        + "(libraries, classes, scripts, bundles), distinct from the "
                        + "data they operate on";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "code";
    }

    /**
     * Qualifier marking a binding's text target as a class identifier — typically
     * a fully-qualified class name in the binding's language. Used composed with
     * a language sememe as the binding's role (e.g., {@code JAVA:[ClassName]} or
     * {@code PYTHON:[ClassName]}).
     */
    @Seed.Item(key = ClassName.KEY)
    public static final class ClassName {
        public static final String KEY = "cg.qualifier:class-name";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private ClassName() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "qualifier marking a binding's text target as a class identifier "
                        + "(typically a fully-qualified class name) in the binding's "
                        + "language";
    }

    /**
     * Qualifier marking a binding's text target as source code in the binding's
     * language. Used composed with a language sememe as the binding's role
     * (e.g., {@code PYTHON:[SourceCode]} or {@code LISP:[SourceCode]}).
     *
     * <p>For interpreted languages, source code can live directly on the
     * manifest. For compiled languages, prefer a Bytecode qualifier (not yet
     * defined) addressing the compiled bytes by CID.
     */
    @Seed.Item(key = SourceCode.KEY)
    public static final class SourceCode {
        public static final String KEY = "cg.qualifier:source-code";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private SourceCode() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "qualifier marking a binding's text target as source code in the "
                        + "binding's language";
    }
}
