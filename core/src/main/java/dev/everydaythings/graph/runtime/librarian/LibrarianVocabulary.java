package dev.everydaythings.graph.runtime.librarian;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.SchemaVocabulary;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.id.CompoundKey;
import dev.everydaythings.graph.id.DatumRef;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.language.*;
import dev.everydaythings.graph.runtime.RuntimeVocabulary;

import java.util.List;
import java.util.Optional;

public class LibrarianVocabulary {
    /**
     * The {@code LOOKUP} predicate — token-dictionary lookup.
     *
     * <p>A LOOKUP frame submitted to a Librarian consults the TokenDictionary and
     * returns response frames carrying postings. The frame's bindings:
     *
     * <ul>
     *   <li>{@code THEME → "<token>"} — the token text being looked up (required)</li>
     *   <li>{@code ATTRIBUTE[LIMIT] → <integer>} — optional; if present, prefix
     *       (range-scan) match with the given upper bound on results. If absent,
     *       exact (point) match.</li>
     * </ul>
     *
     * <p>LOOKUP frames are <b>ephemeral</b> — the predicate's manifest carries
     * {@code CONFIG[RETENTION] → @Ephemeral}, so submit doesn't persist body or
     * records. The handler fires; response frames flow back to the submitter; no
     * audit trail is kept. This is appropriate because LOOKUP is the per-keystroke
     * query a UI client issues during autocomplete or token disambiguation —
     * persisting every keystroke would be both privacy-toxic and storage-bloat.
     */
    @Seed.Item(key = Lookup.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Lookup {

        public static final String KEY = "cg.predicate:lookup";
        public static final ItemRef IID = ItemRef.fromString(KEY);

        private Lookup() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the predicate for token-dictionary lookup — submit a LOOKUP frame "
                        + "with a token in THEME to receive postings as ephemeral responses";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "look up";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "lookup";

        /**
         * CONFIG[RETENTION] → Ephemeral: LOOKUP frames are not persisted.
         */
        @Seed.Frame(predicate = CoreVocabulary.Config.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {SchemaVocabulary.Retention.KEY}))
        static final ItemRef retention = SchemaVocabulary.Ephemeral.IID;
    }

    /**
     * The {@code DELETE} predicate — a request to remove an item from local storage.
     *
     * <p>A DELETE frame is a <i>request</i>: it claims "the signer wants this item gone."
     * Each librarian receiving the frame decides independently whether to honor the
     * request, based on its own trust matrix. Phase 1 honors only DELETEs whose
     * records carry a signature verifiable against this librarian's own KEL — the
     * implicit "I'm asking my own librarian to delete this" case. Other-signed
     * DELETEs are stored as data but not acted upon.
     *
     * <p>When honored, the librarian's handler:
     * <ul>
     *   <li>Cascade-deletes the target item's manifest bodies and their records</li>
     *   <li>Evicts the item from the in-memory cache</li>
     *   <li>Does NOT cascade through endorsed frames — those may be referenced
     *       elsewhere; dangling references are an accepted cost.</li>
     * </ul>
     *
     * <p>The DELETE frame itself is retained as audit data regardless of whether
     * the action was taken — "the signer requested this; at time T; with this
     * authorization claim."
     *
     * <p>Bindings:
     * <ul>
     *   <li>{@code THEME → @<item-iid>} — required: the item to delete.</li>
     *   <li>{@code AGENT → @<signer>} — auto-populated from records.</li>
     * </ul>
     */
    @Seed.Item(key = Delete.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Delete {

        public static final String KEY = "cg.predicate:delete";
        public static final ItemRef IID = ItemRef.fromString(KEY);

        private Delete() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the predicate for requesting removal of an item from local storage — "
                        + "each librarian decides whether to honor based on its trust matrix";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "delete";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "deletion";
    }

    /**
     * The {@code CREATE} predicate — instantiate a fresh item.
     *
     * <p>A CREATE frame submitted to a Librarian causes a new item to be minted,
     * its initial manifest committed, and (optionally) a post-construct hook fired.
     * The CREATE frame itself is the durable record of the act; no separate
     * "CREATED" response frame is emitted.
     *
     * <p>This class is both the predicate's seed declaration AND the runtime
     * embodiment that handles incoming CREATE frames. {@code @Seed.Embodies}
     * adds an IMPLEMENTATION binding to the predicate's seed manifest so that
     * {@code fetchItem(Create.IID)} hydrates as this class and
     * {@link #onFrameAssembled} fires when CREATE frames are routed.
     *
     * <p>Bindings on a CREATE frame:
     * <ul>
     *   <li>{@code THEME → @<archetype>} — required: the kind of item to create.</li>
     *   <li>{@code AGENT → @<signer>} — auto-populated from the CREATE frame's records;
     *       identifies who authorized the creation.</li>
     *   <li>{@code INSTRUMENT → @<implementation>} — optional: a specific
     *       implementation to use. Can be a Java-class name (text target) or an item
     *       reference. When absent, the librarian falls back to the archetype's
     *       own IMPLEMENTATION binding.</li>
     *   <li>Other bindings carry forward as initial manifest bindings on the new item.</li>
     * </ul>
     */
    @Seed.Item(key = Create.KEY, head = CoreVocabulary.Predicate.KEY)
    @Seed.Embodies(key = Create.KEY)
    public static class Create extends Item {

        public static final String KEY = "cg.predicate:create";
        public static final ItemRef IID = ItemRef.fromString(KEY);

        public Create(ItemRef iid, Librarian librarian) {
            super(iid, librarian);
        }

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the predicate for instantiating a new item — submit a CREATE frame with "
                        + "a THEME archetype and an authorizing record; the new item is minted, "
                        + "committed, and post-constructed";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "create";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "creation";

        /**
         * When a CREATE frame is assembled, find an IMPLEMENTS frame for the THEME's
         * concept and instantiate its runnable Java class.
         *
         * <p>Walks: read THEME from the frame → query IMPLEMENTS frames where THEME
         * points at the named concept → for each, check it's an IMPLEMENTS frame
         * (head matches) and the AGENT binding's qualifier is Java → try to load
         * the class → instantiate via {@code (ItemRef, Librarian)} constructor and
         * commit. First runnable wins.
         */
        @Override
        public void onFrameAssembled(Frame frame) {
            Optional<Binding> themeBinding =
                    frame.body().binding(CompoundKey.of(ThematicRole.Theme.IID));
            if (themeBinding.isEmpty()) return;

            ItemRef conceptIid = extractIid(themeBinding.get().target());
            if (conceptIid == null) return;

            List<DatumRef> candidateBodyCids = librarian.library()
                    .bodyCidsForReferenceBinding(ThematicRole.Theme.IID, conceptIid);

            for (DatumRef bodyCid : candidateBodyCids) {
                Frame candidate = librarian.fetchFrame(bodyCid).orElse(null);
                if (candidate == null) continue;
                if (!isImplementsFrame(candidate)) continue;

                Class<? extends Item> instanceClass = readJavaImplementation(candidate);
                if (instanceClass == null) continue;

                try {
                    Item instance = instanceClass
                            .getConstructor(ItemRef.class, Librarian.class)
                            .newInstance(ItemRef.random(), librarian);
                    instance.commit(List.of());
                    return;  // first runnable wins
                } catch (ReflectiveOperationException e) {
                    // Try the next candidate.
                }
            }
        }

        private static boolean isImplementsFrame(Frame frame) {
            if (!(frame.body().head() instanceof ItemRef itemRef)) return false;
            return SchemaVocabulary.Implements.IID.equals(itemRef.iid());
        }

        /**
         * Read the Java instance class from an IMPLEMENTS frame's AGENT binding,
         * which should carry a Java-class name (text target) qualified by the Java runtime.
         * Returns null if the binding shape doesn't match — caller skips and tries
         * the next candidate.
         */
        @SuppressWarnings("unchecked")
        private static Class<? extends Item> readJavaImplementation(Frame frame) {
            for (Binding b : frame.body().bindings()) {
                if (!ThematicRole.Agent.IID.equals(b.role())) continue;
                boolean javaQualified = false;
                boolean classNameQualified = false;
                for (var q : b.qualifiers()) {
                    if (q instanceof CompoundKey.Sememe(ItemRef id)) {
                        if (RuntimeVocabulary.Java.IID.equals(id)) javaQualified = true;
                        if (RuntimeVocabulary.JavaClass.IID.equals(id)) {
                            classNameQualified = true;
                        }
                    }
                }
                if (!javaQualified || !classNameQualified) continue;
                if (!(b.target() instanceof String className)) continue;
                try {
                    Class<?> clazz = Class.forName(className, false,
                            Thread.currentThread().getContextClassLoader());
                    if (Item.class.isAssignableFrom(clazz)) {
                        return (Class<? extends Item>) clazz;
                    }
                } catch (ClassNotFoundException | RuntimeException ignored) {
                    // class not on classpath; not runnable here
                }
            }
            return null;
        }

        private static ItemRef extractIid(Object target) {
            if (target instanceof ItemRef ir && !ir.isPinned()) return ir;
            return null;
        }
    }
}
