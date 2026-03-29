package dev.everydaythings.graph.language;

import dev.everydaythings.graph.dispatch.Created;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.FrameBody;
import dev.everydaythings.graph.frame.ItemFrame;
import dev.everydaythings.graph.frame.ItemFrame.Bind;
import dev.everydaythings.graph.frame.eval.FrameAssemblyContext;
import dev.everydaythings.graph.item.Implements;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.user.Signer;
import dev.everydaythings.graph.runtime.Librarian;

/**
 * Core vocabulary seeds — the fundamental predicates and actions CG needs to function.
 *
 * <p>Contains action verbs (create, get, put, etc.), metadata predicates
 * (author, title, description), session verbs (exit, back, authenticate),
 * and infrastructure concepts (library, vault, key history).
 *
 * <p>Seeds here are plain {@link Sememe} instances. Part of speech is a
 * property on each seed, not a class identity.
 *
 * @see LexicalVocabulary for semantic/lexical relations (hypernym, antonym, etc.)
 * @see PrepositionVocabulary for thematic role carriers
 * @see dev.everydaythings.graph.network.RoutingVocabulary for network/routing concepts
 */
public final class CoreVocabulary {

    private CoreVocabulary() {}

    // ==================================================================================
    // TYPE SYSTEM
    // ==================================================================================

    @ItemSeed(key = ImplementedBy.KEY)
    public static class ImplementedBy {
        public static final String KEY = "cg.type:implemented-by";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "is implemented by; has its design applied by";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i33787";

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Goal.KEY}))
        static final ItemID expectGoal = ThematicRole.Goal.IID;
    }

    /**
     * Declares an expectation on a sememe — either a role binding (for predicates)
     * or a frame type (for item types).
     *
     * <p>The first qualifier on the TOPIC binding distinguishes the kind:
     * <ul>
     *   <li>ROLE ({@code cg.sememe:role}) — predicate expects a thematic role binding
     *       (e.g., ITEM_VIEW expects THEME, LOCATION)</li>
     *   <li>FRAME ({@code cg.sememe:frame}) — type expects a frame on its instances
     *       (e.g., CHESS expects PLAYER, MOVE frames)</li>
     * </ul>
     */
    @ItemSeed(key = Expects.KEY)
    public static class Expects {
        public static final String KEY = "cg.type:expects";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "declares an expectation — a role binding or a frame type";

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.Topic.KEY}))
        static final ItemID expectTopic = ThematicRole.Topic.IID;
    }


    // ==================================================================================
    // ACTION VERBS
    // ==================================================================================

    @Implements(Create.KEY)
    @ItemSeed(key = Create.KEY)
    public static class Create extends Sememe {
        public static final String KEY = "cg.verb:create";
        public static final ItemID IID = ItemID.fromString(KEY);

        public Create() { super(KEY); }
        protected Create(ItemID iid) { super(iid); }
        protected Create(Librarian lib, Manifest m) { super(lib, m); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "make or cause to be or to become";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i29849";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"create", "new", "make"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Goal.KEY}))
        static final ItemID expectGoal = ThematicRole.Goal.IID;

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Name.KEY}))
        static final ItemID expectName = ThematicRole.Name.IID;

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Partner.KEY}))
        static final ItemID expectPartner = ThematicRole.Partner.IID;

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Source.KEY}))
        static final ItemID expectSource = ThematicRole.Source.IID;

        @Override
        public void onFrameAssembled(FrameAssemblyContext ctx) {
            Item typeItem = ctx.item(ThematicRole.Theme.IID);
            if (typeItem == null) return;

            Class<?> implClass = typeItem.resolveImplementingClass().orElse(null);
            if (implClass == null || !Item.class.isAssignableFrom(implClass)) return;

            Librarian lib = ctx.scope().librarian();
            if (lib == null) return;

            Item newItem = Item.instantiateItem(implClass, lib);

            // INSTANCE_OF frame
            lib.storeFrame(FrameBody.builder(LexicalVocabulary.InstanceOf.IID)
                    .bind(ThematicRole.Theme.IID, newItem.iid())
                    .bind(ThematicRole.Goal.IID, typeItem.iid())
                    .build());

            // Optional name from NAME binding
            BindingTarget nameTarget = ctx.body().binding(ThematicRole.Name.IID);
            if (nameTarget instanceof Literal lit && lit.payload() != null) {
                String name = new String(lit.payload(), java.nio.charset.StandardCharsets.UTF_8);
                if (!name.isBlank()) {
                    if (newItem instanceof Signer signer) {
                        signer.setName(name);
                    } else {
                        lib.storeFrame(FrameBody.builder(Title.IID)
                                .bind(ThematicRole.Theme.IID, newItem.iid())
                                .bind(ThematicRole.Name.IID, name)
                                .build());
                    }
                }
            }

            if (ctx.signer() != null) {
                newItem.commit(ctx.signer());
            }
            lib.put(newItem);

            ctx.handled(new Created(newItem, typeItem));
        }
    }

    @ItemSeed(key = Get.KEY)
    public static class Get {
        public static final String KEY = "cg.verb:get";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "go or come after and bring or take back";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i28895";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"get", "retrieve", "fetch", "lookup"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;
    }

    @ItemSeed(key = Put.KEY)
    public static class Put {
        public static final String KEY = "cg.verb:put";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "find a place for and put away for storage";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i33146";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"put", "store", "add", "insert"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Goal.KEY}))
        static final ItemID expectGoal = ThematicRole.Goal.IID;
    }

    @ItemSeed(key = Remove.KEY)
    public static class Remove {
        public static final String KEY = "cg.verb:remove";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "remove something concrete, as by lifting, pushing, or taking off";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i22577";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"remove", "delete", "drop"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;
    }

    @ItemSeed(key = ListVerb.KEY)
    public static class ListVerb {
        public static final String KEY = "cg.verb:list";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "enumerate; list";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i26334";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"list", "enumerate", "tail", "latest"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;
    }

    @ItemSeed(key = Import.KEY)
    public static class Import {
        public static final String KEY = "cg.verb:import";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "transfer electronic data into a database or document";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i32905";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"import", "ingest", "load"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Source.KEY}))
        static final ItemID expectSource = ThematicRole.Source.IID;
    }

    @ItemSeed(key = Query.KEY)
    public static class Query {
        public static final String KEY = "cg.verb:query";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "pose a question";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i25610";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"query", "search"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;
    }

    @ItemSeed(key = Find.KEY)
    public static class Find {
        public static final String KEY = "cg.verb:find";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "find items related by a predicate";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i33164";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"find", "lookup"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Recipient.KEY}))
        static final ItemID expectRecipient = ThematicRole.Recipient.IID;

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Source.KEY}))
        static final ItemID expectSource = ThematicRole.Source.IID;
    }

    @ItemSeed(key = Show.KEY)
    public static class Show {
        public static final String KEY = "cg.verb:show";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "make visible or apparent";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i32454";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"show", "display"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;
    }

    @ItemSeed(key = Help.KEY)
    public static class Help {
        public static final String KEY = "cg.verb:help";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "give help or assistance; be of service";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i34433";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"help", "assist", "commands"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;
    }

    @ItemSeed(key = Edit.KEY)
    public static class Edit {
        public static final String KEY = "cg.verb:edit";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "prepare for publication or presentation by correcting, revising, or adapting";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i22726";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"edit", "modify", "change"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Patient.KEY}))
        static final ItemID expectPatient = ThematicRole.Patient.IID;
    }

    @ItemSeed(key = Count.KEY)
    public static class Count {
        public static final String KEY = "cg.verb:count";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "determine the number or amount of";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i26340";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"count", "size"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;
    }

    @ItemSeed(key = Describe.KEY)
    public static class Describe {
        public static final String KEY = "cg.verb:describe";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "give an account or representation of in words";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i26422";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"describe", "status", "info"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;
    }

    @ItemSeed(key = Inspect.KEY)
    public static class Inspect {
        public static final String KEY = "cg.verb:inspect";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "look over carefully";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i32580";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"inspect", "examine"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;
    }

    @ItemSeed(key = Cd.KEY)
    public static class Cd {
        public static final String KEY = "cg.verb:cd";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "change directory; navigate to";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"cd", "go", "enter"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Goal.KEY}))
        static final ItemID expectGoal = ThematicRole.Goal.IID;
    }

    @ItemSeed(key = Exit.KEY)
    public static class Exit {
        public static final String KEY = "cg.session:exit";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "exit the session";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i31816";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"exit", "quit", "q"};
    }

    @ItemSeed(key = Back.KEY)
    public static class Back {
        public static final String KEY = "cg.session:back";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "go back to previous item";

        @ItemFrame(predicate = CoreVocabulary.Symbol.KEY)
        static final String symbol = "..";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"back", "pop"};
    }

    @ItemSeed(key = Serve.KEY)
    public static class Serve {
        public static final String KEY = "cg.verb:serve";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "work for or be a servant to";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i96785";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"serve", "use"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;
    }

    @ItemSeed(key = Invite.KEY)
    public static class Invite {
        public static final String KEY = "cg.verb:invite";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "request someone's participation";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i32987";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"invite"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Goal.KEY}))
        static final ItemID expectGoal = ThematicRole.Goal.IID;
    }

    @ItemSeed(key = Authenticate.KEY)
    public static class Authenticate {
        public static final String KEY = "cg.session:authenticate";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "prove identity by demonstrating possession of private key";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i25047";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"authenticate", "auth", "login"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;
    }

    @ItemSeed(key = Switch.KEY)
    public static class Switch {
        public static final String KEY = "cg.session:switch";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "change the active user for the current view";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i22420";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"switch", "as"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;
    }

    @ItemSeed(key = Rename.KEY)
    public static class Rename {
        public static final String KEY = "cg.verb:rename";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "assign a new name to";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i25424";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"rename", "name"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;
    }

    // ==================================================================================
    // METADATA PREDICATES
    // ==================================================================================

    @ItemSeed(key = Author.KEY)
    public static class Author {
        public static final String KEY = "cg.core:author";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "the creator or originator of a work";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i90183";

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Agent.KEY}))
        static final ItemID expectAgent = ThematicRole.Agent.IID;
    }

    @ItemSeed(key = CreatedAt.KEY)
    public static class CreatedAt {
        public static final String KEY = "cg.core:created-at";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "the time at which something was created";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i36666";

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Time.KEY}))
        static final ItemID expectTime = ThematicRole.Time.IID;
    }

    @ItemSeed(key = ModifiedAt.KEY)
    public static class ModifiedAt {
        public static final String KEY = "cg.core:modified-at";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "the time at which something was last modified";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i22389";

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Time.KEY}))
        static final ItemID expectTime = ThematicRole.Time.IID;
    }

    @ItemSeed(key = Title.KEY)
    public static class Title {
        public static final String KEY = "cg.core:title";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "the name or title of something";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i69816";

        @ItemFrame(predicate = CoreVocabulary.IndexWeight.KEY)
        static final int indexWeight = 1000;

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Referent.KEY}))
        static final ItemID expectReferent = ThematicRole.Referent.IID;
    }

    @ItemSeed(key = Description.KEY)
    public static class Description {
        public static final String KEY = "cg.core:description";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a textual description of something";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i71841";

        @ItemFrame(predicate = CoreVocabulary.IndexWeight.KEY)
        static final int indexWeight = 500;

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Topic.KEY}))
        static final ItemID expectTopic = ThematicRole.Topic.IID;
    }

    @ItemSeed(key = Slot.KEY)
    public static class Slot {
        public static final String KEY = "cg.core:slot";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a position in a frame that expects a particular role";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i69534";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"slot"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;
    }

    @ItemSeed(key = LexemeSeed.KEY)
    public static class LexemeSeed {
        public static final String KEY = "cg.core:lexeme";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a word-meaning mapping in a language's lexicon";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i69622";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"lexeme"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Referent.KEY}))
        static final ItemID expectReferent = ThematicRole.Referent.IID;
    }

    @ItemSeed(key = Frequency.KEY)
    public static class Frequency {
        public static final String KEY = "cg.core:frequency";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "how often something occurs";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i73785";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"frequency"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Topic.KEY}))
        static final ItemID expectTopic = ThematicRole.Topic.IID;
    }

    @ItemSeed(key = Provenance.KEY)
    public static class Provenance {
        public static final String KEY = "cg.core:provenance";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "the origin or source of information";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i77490";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"provenance"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Source.KEY}))
        static final ItemID expectSource = ThematicRole.Source.IID;
    }

    @ItemSeed(key = Activity.KEY)
    public static class Activity {
        public static final String KEY = "cg.core:activity";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a record of actions or events";

        @ItemFrame(predicate = CoreVocabulary.CiliId.KEY)
        static final String cili = "i30955";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"activity"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Topic.KEY}))
        static final ItemID expectTopic = ThematicRole.Topic.IID;
    }

    // ==================================================================================
    // INFRASTRUCTURE PREDICATES
    // ==================================================================================

    @ItemSeed(key = Library.KEY)
    public static class Library {
        public static final String KEY = "cg.core:library";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a collection of stored items; local persistent storage";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"library"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;
    }

    @ItemSeed(key = Vault.KEY)
    public static class Vault {
        public static final String KEY = "cg.core:vault";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a secure store for private keys and secrets";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"vault"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;
    }

    @ItemSeed(key = KeyHistory.KEY)
    public static class KeyHistory {
        public static final String KEY = "cg.core:key-history";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a public key history stream recording key lifecycle events";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"key history"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Topic.KEY}))
        static final ItemID expectTopic = ThematicRole.Topic.IID;
    }

    @ItemSeed(key = CertHistory.KEY)
    public static class CertHistory {
        public static final String KEY = "cg.core:cert-history";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a certificate log tracking issued attestations";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"cert history"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Topic.KEY}))
        static final ItemID expectTopic = ThematicRole.Topic.IID;
    }

    @ItemSeed(key = HashKey.KEY)
    public static class HashKey {
        public static final String KEY = "cg.core:hash-key";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "the preimage string that was hashed to produce a deterministic identifier";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"hash key"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Referent.KEY}))
        static final ItemID expectReferent = ThematicRole.Referent.IID;
    }

    @ItemSeed(key = LanguageCode.KEY)
    public static class LanguageCode {
        public static final String KEY = "cg.core:language-code";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "an ISO 639 code identifying a language";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"language code"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Referent.KEY}))
        static final ItemID expectReferent = ThematicRole.Referent.IID;
    }

    @ItemSeed(key = Canonicalization.KEY)
    public static class Canonicalization {
        public static final String KEY = "cg.core:canonicalization";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "rules for normalizing and encoding values of a type";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"canonicalization"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Topic.KEY}))
        static final ItemID expectTopic = ThematicRole.Topic.IID;
    }

    @ItemSeed(key = Monitor.KEY)
    public static class Monitor {
        public static final String KEY = "cg.core:monitor";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "an observer of system health and resource usage";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"monitor"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;
    }

    // ==================================================================================
    // SEMEME METADATA PREDICATES
    // ==================================================================================

    @ItemSeed(key = Symbol.KEY)
    public static class Symbol {
        public static final String KEY = "cg.core:symbol";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a language-neutral symbol representing a concept";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"symbol"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Referent.KEY}))
        static final ItemID expectReferent = ThematicRole.Referent.IID;
    }

    @ItemSeed(key = IndexWeight.KEY)
    public static class IndexWeight {
        public static final String KEY = "cg.core:index-weight";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "how heavily to index relation targets using this predicate";

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Extent.KEY}))
        static final ItemID expectExtent = ThematicRole.Extent.IID;
    }

    @ItemSeed(key = AssignedRole.KEY)
    public static class AssignedRole {
        public static final String KEY = "cg.core:assigned-role";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "the thematic role a function word assigns to its object";

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Referent.KEY}))
        static final ItemID expectReferent = ThematicRole.Referent.IID;
    }

    @ItemSeed(key = Facet.KEY)
    public static class Facet {
        public static final String KEY = "cg.core:facet";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a domain, range, or cardinality constraint on a predicate";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"facet"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Topic.KEY}))
        static final ItemID expectTopic = ThematicRole.Topic.IID;
    }

    // ==================================================================================
    // BINDING QUALIFIERS
    //
    // Sememes used as compound key qualifiers on role bindings.
    // They modify HOW a role's value is accessed — not what the role IS.
    // E.g., (TOPIC, STREAM) means "the topic is a stream head,"
    //       (TOPIC, ENCRYPTED) means "the topic is encrypted."
    // ==================================================================================

    /** Qualifier: content is an append-only stream (compound key with Topic). */
    @ItemSeed(key = Stream.KEY)
    public static class Stream {
        public static final String KEY = "cg.core:stream";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "qualifier indicating append-only stream access mode";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"stream"};
    }

    /** Qualifier: content is stored externally at a filesystem path (compound key with Topic). */
    @ItemSeed(key = External.KEY)
    public static class External {
        public static final String KEY = "cg.core:external";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "qualifier indicating content stored externally at a filesystem path";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"external"};
    }

    /** Qualifier: content is encrypted (compound key with Topic). */
    @ItemSeed(key = Encrypted.KEY)
    public static class Encrypted {
        public static final String KEY = "cg.core:encrypted";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "qualifier indicating content is encrypted";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"encrypted"};
    }

    // ==================================================================================
    // EXTERNAL ID PREDICATES
    // ==================================================================================

    @ItemSeed(key = CiliId.KEY)
    public static class CiliId {
        public static final String KEY = "cg.core:cili-id";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a Collaborative Interlingual Index identifier anchoring a concept across languages";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"CILI"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Referent.KEY}))
        static final ItemID expectReferent = ThematicRole.Referent.IID;
    }

    // ==================================================================================
    // VALUE & OPERATOR PREDICATES
    // ==================================================================================

    @ItemSeed(key = Arity.KEY)
    public static class Arity {
        public static final String KEY = "cg.core:arity";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "number of operands or arguments";
    }

    @ItemSeed(key = Precedence.KEY)
    public static class Precedence {
        public static final String KEY = "cg.core:precedence";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "parsing priority of an operator";
    }

    @ItemSeed(key = Associativity.KEY)
    public static class Associativity {
        public static final String KEY = "cg.core:associativity";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "left or right grouping direction of an operator";
    }

    @ItemSeed(key = Fixity.KEY)
    public static class Fixity {
        public static final String KEY = "cg.core:fixity";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "position of an operator relative to its operands";
    }

    @ItemSeed(key = Category.KEY)
    public static class Category {
        public static final String KEY = "cg.core:category";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "classification or grouping";
    }

    @ItemSeed(key = Bounds.KEY)
    public static class Bounds {
        public static final String KEY = "cg.core:bounds";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "value range constraints";
    }

    @ItemSeed(key = UnitRules.KEY)
    public static class UnitRules {
        public static final String KEY = "cg.core:unit-rules";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "rules governing units for a value type";
    }

    @ItemSeed(key = DimensionFormula.KEY)
    public static class DimensionFormula {
        public static final String KEY = "cg.core:dimension-formula";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "dimensional analysis formula mapping dimensions to exponents";
    }

    @ItemSeed(key = ScaleNumerator.KEY)
    public static class ScaleNumerator {
        public static final String KEY = "cg.core:scale-numerator";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "numerator of a unit's scale factor relative to SI base";
    }

    @ItemSeed(key = ScaleDenominator.KEY)
    public static class ScaleDenominator {
        public static final String KEY = "cg.core:scale-denominator";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "denominator of a unit's scale factor relative to SI base";
    }

    @ItemSeed(key = Lexicon.KEY)
    public static class Lexicon {
        public static final String KEY = "cg.core:lexicon";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a language's word inventory";
    }

    @ItemSeed(key = Lexeme.KEY)
    public static class Lexeme {
        public static final String KEY = "cg.core:lexeme";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a word form realizing a sememe in a specific language";
    }

    @ItemSeed(key = DialectOf.KEY)
    public static class DialectOf {
        public static final String KEY = "cg.core:dialect-of";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "regional or dialectal variant of a parent language";
    }

    @ItemSeed(key = Names.KEY)
    public static class Names {
        public static final String KEY = "cg.core:names";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "language-tagged display names";
    }

    // ==================================================================================
    // EVALUATION PREDICATES (control flow for frame evaluation)
    // ==================================================================================

    /** Conditional branching: THEME=condition, RESULT=then-branch, GOAL=else-branch. */
    @ItemSeed(key = Conditional.KEY)
    public static class Conditional {
        public static final String KEY = "cg.eval:conditional";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "conditional evaluation; if-then-else branching";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"if"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Result.KEY}))
        static final ItemID expectResult = ThematicRole.Result.IID;

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Goal.KEY}))
        static final ItemID expectGoal = ThematicRole.Goal.IID;
    }

    /** Sequential evaluation: multiple THEME bindings evaluated in order, returns last. */
    @ItemSeed(key = Sequence.KEY)
    public static class Sequence {
        public static final String KEY = "cg.eval:sequence";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "sequential evaluation; evaluates steps in order";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"sequence"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;
    }

    /** Let binding: GOAL=name, THEME=value, RESULT=body evaluated in child scope. */
    @ItemSeed(key = Let.KEY)
    public static class Let {
        public static final String KEY = "cg.eval:let";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "scoped variable binding; let-in expression";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"let"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Goal.KEY}))
        static final ItemID expectGoal = ThematicRole.Goal.IID;

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Result.KEY}))
        static final ItemID expectResult = ThematicRole.Result.IID;
    }

    /** Variable resolution: THEME=name to look up in scope chain. */
    @ItemSeed(key = Resolve.KEY)
    public static class Resolve {
        public static final String KEY = "cg.eval:resolve";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "resolve a variable name from the scope chain";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"resolve"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;
    }

    /** Property access: THEME=object, GOAL=property to access. */
    @ItemSeed(key = Access.KEY)
    public static class Access {
        public static final String KEY = "cg.eval:access";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "access a property on an object";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"access"};

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;

        @ItemFrame(predicate = Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Goal.KEY}))
        static final ItemID expectGoal = ThematicRole.Goal.IID;
    }
}
