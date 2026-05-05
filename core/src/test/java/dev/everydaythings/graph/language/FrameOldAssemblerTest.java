package dev.everydaythings.graph.language;

import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.FrameBodyOld;
import dev.everydaythings.graph.item.ItemOld;
import dev.everydaythings.graph.item.id.CompoundKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Eval.ResolvedToken;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FrameAssembler} — order-agnostic semantic frame assembly.
 *
 * <p>Constructs test Sememe instances matching the old seed definitions from
 * CoreVocabulary and PrepositionVocabulary. These carry the slot roles and
 * assigned roles that FrameAssembler needs for dispatch.
 */
class FrameOldAssemblerTest {

    // Construct verb sememes with EXPECTS frames (matching slot role declarations)
    private static final Sememe CREATE = withExpects(new Sememe(CoreVocabulary.Create.KEY),
            ThematicRole.Theme.IID, ThematicRole.Goal.IID,
            ThematicRole.Value.IID, ThematicRole.Partner.IID,
            ThematicRole.Source.IID);
    private static final Sememe SHOW = withExpects(new Sememe(CoreVocabulary.Show.KEY),
            ThematicRole.Theme.IID);
    private static final Sememe EDIT = withExpects(new Sememe(CoreVocabulary.Edit.KEY),
            ThematicRole.Patient.IID);

    // Construct preposition sememes with assigned roles
    private static final Sememe ON = new Sememe(PrepositionVocabulary.On.KEY)
            .role(ThematicRole.Goal.KEY);
    private static final Sememe BETWEEN = new Sememe(PrepositionVocabulary.Between.KEY)
            .role(ThematicRole.Partner.KEY);
    private static final Sememe NAMED = new Sememe(PrepositionVocabulary.Named.KEY)
            .role(ThematicRole.Value.KEY);
    private static final Sememe FROM = new Sememe(PrepositionVocabulary.From.KEY)
            .role(ThematicRole.Source.KEY);

    // Conjunction — proper subclass
    private static final Sememe AND = new Sememe.And();

    // Test modifier sememes
    private static final Sememe PUBLIC_ADJ = new Sememe("cg:test/public")
            .gloss("en", "accessible to everyone")
            .word(PartOfSpeech.ADJECTIVE, Sememe.LEMMA, Sememe.ENG, "public");
    private static final Sememe QUIETLY = new Sememe("cg:test/quietly")
            .gloss("en", "without notification")
            .word(PartOfSpeech.ADVERB, Sememe.LEMMA, Sememe.ENG, "quietly");

    // Mock "noun" items — use random IIDs
    private static final ItemID CHESS_IID = ItemID.fromString("cg:test/chess");
    private static final ItemID LIBRARIAN_IID = ItemID.fromString("cg:test/librarian");
    private static final ItemID BOB_IID = ItemID.fromString("cg:test/bob");
    private static final ItemID JANE_IID = ItemID.fromString("cg:test/jane");

    // Simple stub items for nouns (we just need iid() and displayToken())
    private static final ItemOld CHESS_ITEM = new Sememe("cg:test/chess")
            .gloss("en", "chess");
    private static final ItemOld LIBRARIAN_ITEM = new Sememe("cg:test/librarian")
            .gloss("en", "librarian");
    private static final ItemOld BOB_ITEM = new Sememe("cg:test/bob")
            .gloss("en", "bob");
    private static final ItemOld JANE_ITEM = new Sememe("cg:test/jane")
            .gloss("en", "jane");

    /**
     * Resolver that knows about our test sememes and mock items.
     */
    private final Function<ItemID, Optional<ItemOld>> resolver = iid -> {
        if (iid.equals(CREATE.iid())) return Optional.of(CREATE);
        if (iid.equals(SHOW.iid())) return Optional.of(SHOW);
        if (iid.equals(EDIT.iid())) return Optional.of(EDIT);
        if (iid.equals(ON.iid())) return Optional.of(ON);
        if (iid.equals(BETWEEN.iid())) return Optional.of(BETWEEN);
        if (iid.equals(NAMED.iid())) return Optional.of(NAMED);
        if (iid.equals(FROM.iid())) return Optional.of(FROM);
        if (iid.equals(AND.iid())) return Optional.of(AND);
        if (iid.equals(PUBLIC_ADJ.iid())) return Optional.of(PUBLIC_ADJ);
        if (iid.equals(QUIETLY.iid())) return Optional.of(QUIETLY);
        if (iid.equals(CHESS_IID)) return Optional.of(CHESS_ITEM);
        if (iid.equals(LIBRARIAN_IID)) return Optional.of(LIBRARIAN_ITEM);
        if (iid.equals(BOB_IID)) return Optional.of(BOB_ITEM);
        if (iid.equals(JANE_IID)) return Optional.of(JANE_ITEM);
        return Optional.empty();
    };

    private ResolvedToken link(ItemOld item) {
        return new ResolvedToken.Link(item.iid(), item.displayToken());
    }

    private ResolvedToken link(ItemOld item, ItemID pos) {
        return new ResolvedToken.Link(item.iid(), item.displayToken(), Set.of(pos));
    }

    private ResolvedToken link(ItemID iid, String token) {
        return new ResolvedToken.Link(iid, token);
    }

    private ResolvedToken literal(String value) {
        return new ResolvedToken.Literal(value, value);
    }

    // ==================================================================================
    // Basic verb-noun
    // ==================================================================================

    @Test
    void basicVerbNoun() {
        // [CREATE, chess] → verb=CREATE, THEME=chess
        var tokens = List.of(link(CREATE), link(CHESS_IID, "chess"));

        var frame = FrameAssembler.assemble(tokens, resolver);

        assertThat(frame).isPresent();
        assertThat(frame.get().verb()).isSameAs(CREATE);
        assertThat(frame.get().bindings()).containsEntry(ThematicRole.Theme.IID, CHESS_ITEM);
        assertThat(frame.get().unmatchedArgs()).isEmpty();
        // CREATE has 5 slots; only THEME filled → 4 unbound
        assertThat(frame.get().unboundRoles()).hasSize(4);
    }

    // ==================================================================================
    // Verb-noun-prep-object
    // ==================================================================================

    @Test
    void verbNounPrepObject() {
        // [CREATE, chess, ON, librarian] → verb=CREATE, THEME=chess, TARGET=librarian
        var tokens = List.of(
                link(CREATE), link(CHESS_IID, "chess"),
                link(ON), link(LIBRARIAN_IID, "librarian"));

        var frame = FrameAssembler.assemble(tokens, resolver);

        assertThat(frame).isPresent();
        assertThat(frame.get().verb()).isSameAs(CREATE);
        assertThat(frame.get().bindings())
                .containsEntry(ThematicRole.Theme.IID, CHESS_ITEM)
                .containsEntry(ThematicRole.Goal.IID, LIBRARIAN_ITEM);
        assertThat(frame.get().unmatchedArgs()).isEmpty();
        // CREATE has 5 slots; THEME+TARGET filled → 3 unbound
        assertThat(frame.get().unboundRoles()).hasSize(3);
    }

    // ==================================================================================
    // Reordered: prep-first
    // ==================================================================================

    @Test
    void reorderedPrepFirst() {
        // [ON, librarian, CREATE, chess] → same frame as above
        var tokens = List.of(
                link(ON), link(LIBRARIAN_IID, "librarian"),
                link(CREATE), link(CHESS_IID, "chess"));

        var frame = FrameAssembler.assemble(tokens, resolver);

        assertThat(frame).isPresent();
        assertThat(frame.get().verb()).isSameAs(CREATE);
        assertThat(frame.get().bindings())
                .containsEntry(ThematicRole.Theme.IID, CHESS_ITEM)
                .containsEntry(ThematicRole.Goal.IID, LIBRARIAN_ITEM);
        assertThat(frame.get().unmatchedArgs()).isEmpty();
    }

    // ==================================================================================
    // Reordered: noun-first
    // ==================================================================================

    @Test
    void reorderedNounFirst() {
        // [chess, CREATE, ON, librarian] → same frame
        var tokens = List.of(
                link(CHESS_IID, "chess"), link(CREATE),
                link(ON), link(LIBRARIAN_IID, "librarian"));

        var frame = FrameAssembler.assemble(tokens, resolver);

        assertThat(frame).isPresent();
        assertThat(frame.get().verb()).isSameAs(CREATE);
        assertThat(frame.get().bindings())
                .containsEntry(ThematicRole.Theme.IID, CHESS_ITEM)
                .containsEntry(ThematicRole.Goal.IID, LIBRARIAN_ITEM);
        assertThat(frame.get().unmatchedArgs()).isEmpty();
    }

    // ==================================================================================
    // Verb only
    // ==================================================================================

    @Test
    void verbOnly() {
        // [CREATE] → frame with verb=CREATE, no bindings
        var tokens = List.of(link(CREATE));

        var frame = FrameAssembler.assemble(tokens, resolver);

        assertThat(frame).isPresent();
        assertThat(frame.get().verb()).isSameAs(CREATE);
        assertThat(frame.get().bindings()).isEmpty();
        assertThat(frame.get().unmatchedArgs()).isEmpty();
        // CREATE has 5 slot roles, none filled
        assertThat(frame.get().isComplete()).isFalse();
        assertThat(frame.get().unboundRoles()).hasSize(5); // THEME, TARGET, NAME, COMITATIVE, SOURCE
    }

    // ==================================================================================
    // No verb → empty
    // ==================================================================================

    @Test
    void noVerb() {
        // [chess, librarian] → Optional.empty()
        var tokens = List.of(
                link(CHESS_IID, "chess"), link(LIBRARIAN_IID, "librarian"));

        var frame = FrameAssembler.assemble(tokens, resolver);

        assertThat(frame).isEmpty();
    }

    // ==================================================================================
    // Verb with literal
    // ==================================================================================

    @Test
    void verbWithLiteral() {
        // [EDIT, "e2e4"] → frame with verb=EDIT, unmatched=["e2e4"]
        var tokens = List.of(link(EDIT), literal("e2e4"));

        var frame = FrameAssembler.assemble(tokens, resolver);

        assertThat(frame).isPresent();
        assertThat(frame.get().verb()).isSameAs(EDIT);
        assertThat(frame.get().bindings()).isEmpty();
        assertThat(frame.get().unmatchedArgs()).hasSize(1);
        assertThat(frame.get().unmatchedArgs().get(0))
                .isInstanceOf(ResolvedToken.Literal.class);
    }

    // ==================================================================================
    // Dangling preposition
    // ==================================================================================

    @Test
    void danglingPreposition() {
        // [CREATE, chess, ON] → ON goes unmatched (no object follows)
        var tokens = List.of(
                link(CREATE), link(CHESS_IID, "chess"), link(ON));

        var frame = FrameAssembler.assemble(tokens, resolver);

        assertThat(frame).isPresent();
        assertThat(frame.get().verb()).isSameAs(CREATE);
        assertThat(frame.get().bindings()).containsEntry(ThematicRole.Theme.IID, CHESS_ITEM);
        // ON is unmatched because it has no object
        assertThat(frame.get().unmatchedArgs()).hasSize(1);
    }

    // ==================================================================================
    // Multiple verbs → first wins
    // ==================================================================================

    @Test
    void multipleVerbs() {
        // [CREATE, SHOW] → first verb wins, second goes unmatched
        var tokens = List.of(link(CREATE), link(SHOW));

        var frame = FrameAssembler.assemble(tokens, resolver);

        assertThat(frame).isPresent();
        assertThat(frame.get().verb()).isSameAs(CREATE);
        // Extra verbs are not matched to argument slots — they go unmatched
        assertThat(frame.get().bindings()).isEmpty();
        assertThat(frame.get().unmatchedArgs()).hasSize(1);
    }

    // ==================================================================================
    // Empty tokens
    // ==================================================================================

    @Test
    void emptyTokens() {
        var frame = FrameAssembler.assemble(List.of(), resolver);
        assertThat(frame).isEmpty();
    }

    // ==================================================================================
    // Required slots tracked as unbound
    // ==================================================================================

    @Test
    void requiredSlotsTracked() {
        // GET has a required THEME slot (via EXPECTS frame)
        var getVerb = withExpects(new Sememe(CoreVocabulary.Get.KEY), ThematicRole.Theme.IID);
        Function<ItemID, Optional<ItemOld>> r = iid -> {
            if (iid.equals(getVerb.iid())) return Optional.of(getVerb);
            return Optional.empty();
        };

        // [GET] with no arguments → unboundRoles has THEME
        var tokens = List.of(link(getVerb));
        var frame = FrameAssembler.assemble(tokens, r);

        assertThat(frame).isPresent();
        assertThat(frame.get().isComplete()).isFalse();
        assertThat(frame.get().unboundRoles()).hasSize(1);
        assertThat(frame.get().unboundRoles().get(0)).isEqualTo(ThematicRole.Theme.IID);
    }

    // ==================================================================================
    // Conjunction: "between bob and jane"
    // ==================================================================================

    @Test
    @SuppressWarnings("unchecked")
    void betweenWithConjunction() {
        // [CREATE, chess, BETWEEN, bob, AND, jane]
        var tokens = List.of(
                link(CREATE), link(CHESS_IID, "chess"),
                link(BETWEEN), link(BOB_IID, "bob"),
                link(AND), link(JANE_IID, "jane"));

        var frame = FrameAssembler.assemble(tokens, resolver);

        assertThat(frame).isPresent();
        assertThat(frame.get().verb()).isSameAs(CREATE);
        assertThat(frame.get().bindings()).containsEntry(ThematicRole.Theme.IID, CHESS_ITEM);

        // COMITATIVE should be a List of [bob, jane]
        Object comitative = frame.get().bindings().get(ThematicRole.Partner.IID);
        assertThat(comitative).isInstanceOf(List.class);
        List<Object> players = (List<Object>) comitative;
        assertThat(players).hasSize(2);
        assertThat(players.get(0)).isSameAs(BOB_ITEM);
        assertThat(players.get(1)).isSameAs(JANE_ITEM);

        assertThat(frame.get().unmatchedArgs()).isEmpty();
    }

    // ==================================================================================
    // Named preposition: "create chess named its-on!"
    // ==================================================================================

    @Test
    void namedPreposition() {
        // [CREATE, chess, NAMED, "its-on!"]
        var tokens = List.of(
                link(CREATE), link(CHESS_IID, "chess"),
                link(NAMED), literal("its-on!"));

        var frame = FrameAssembler.assemble(tokens, resolver);

        assertThat(frame).isPresent();
        assertThat(frame.get().verb()).isSameAs(CREATE);
        assertThat(frame.get().bindings())
                .containsEntry(ThematicRole.Theme.IID, CHESS_ITEM)
                .containsEntry(ThematicRole.Value.IID, "its-on!");
        assertThat(frame.get().unmatchedArgs()).isEmpty();
    }

    // ==================================================================================
    // From source: "create chess from /path/to/game.pgn"
    // ==================================================================================

    @Test
    void fromSource() {
        // [CREATE, chess, FROM, "/path/to/game.pgn"]
        var tokens = List.of(
                link(CREATE), link(CHESS_IID, "chess"),
                link(FROM), literal("/path/to/game.pgn"));

        var frame = FrameAssembler.assemble(tokens, resolver);

        assertThat(frame).isPresent();
        assertThat(frame.get().verb()).isSameAs(CREATE);
        assertThat(frame.get().bindings())
                .containsEntry(ThematicRole.Theme.IID, CHESS_ITEM)
                .containsEntry(ThematicRole.Source.IID, "/path/to/game.pgn");
        assertThat(frame.get().unmatchedArgs()).isEmpty();
    }

    // ==================================================================================
    // Composed: "create chess between bob and jane named its-on!"
    // ==================================================================================

    @Test
    @SuppressWarnings("unchecked")
    void composedExpression() {
        // [CREATE, chess, BETWEEN, bob, AND, jane, NAMED, "its-on!"]
        var tokens = List.of(
                link(CREATE), link(CHESS_IID, "chess"),
                link(BETWEEN), link(BOB_IID, "bob"),
                link(AND), link(JANE_IID, "jane"),
                link(NAMED), literal("its-on!"));

        var frame = FrameAssembler.assemble(tokens, resolver);

        assertThat(frame).isPresent();
        assertThat(frame.get().verb()).isSameAs(CREATE);
        assertThat(frame.get().bindings()).containsEntry(ThematicRole.Theme.IID, CHESS_ITEM);
        assertThat(frame.get().bindings()).containsEntry(ThematicRole.Value.IID, "its-on!");

        List<Object> players = (List<Object>) frame.get().bindings().get(ThematicRole.Partner.IID);
        assertThat(players).containsExactly(BOB_ITEM, JANE_ITEM);

        assertThat(frame.get().unmatchedArgs()).isEmpty();
    }

    // ==================================================================================
    // Single value preposition (no conjunction) stays as single value
    // ==================================================================================

    @Test
    void singlePrepObjectNotWrappedInList() {
        // [CREATE, chess, BETWEEN, bob] → COMITATIVE=bob (not a list)
        var tokens = List.of(
                link(CREATE), link(CHESS_IID, "chess"),
                link(BETWEEN), link(BOB_IID, "bob"));

        var frame = FrameAssembler.assemble(tokens, resolver);

        assertThat(frame).isPresent();
        assertThat(frame.get().bindings().get(ThematicRole.Partner.IID)).isSameAs(BOB_ITEM);
    }

    // ==================================================================================
    // Adverb modifies verb: "quietly create chess"
    // ==================================================================================

    @Test
    void adverbModifiesVerb() {
        var tokens = List.of(
                link(QUIETLY, PartOfSpeech.ADVERB), link(CREATE), link(CHESS_IID, "chess"));

        var frame = FrameAssembler.assemble(tokens, resolver);

        assertThat(frame).isPresent();
        assertThat(frame.get().verb()).isSameAs(CREATE);
        assertThat(frame.get().bindings()).containsEntry(ThematicRole.Theme.IID, CHESS_ITEM);
        assertThat(frame.get().verbModifiers()).containsExactly(QUIETLY);
        assertThat(frame.get().unmatchedArgs()).isEmpty();
    }

    // ==================================================================================
    // Adjective modifies noun: "create public chess"
    // ==================================================================================

    @Test
    void adjectiveModifiesNoun() {
        var tokens = List.of(
                link(CREATE), link(PUBLIC_ADJ, PartOfSpeech.ADJECTIVE), link(CHESS_IID, "chess"));

        var frame = FrameAssembler.assemble(tokens, resolver);

        assertThat(frame).isPresent();
        assertThat(frame.get().verb()).isSameAs(CREATE);
        assertThat(frame.get().bindings()).containsEntry(ThematicRole.Theme.IID, CHESS_ITEM);
        // Adjective should be keyed by role since chess was bound to THEME
        assertThat(frame.get().modifiersFor(ThematicRole.Theme.IID)).containsExactly(PUBLIC_ADJ);
        assertThat(frame.get().unmatchedArgs()).isEmpty();
    }

    // ==================================================================================
    // Both modifiers: "quietly create public chess"
    // ==================================================================================

    @Test
    void adverbAndAdjective() {
        var tokens = List.of(
                link(QUIETLY, PartOfSpeech.ADVERB), link(CREATE), link(PUBLIC_ADJ, PartOfSpeech.ADJECTIVE), link(CHESS_IID, "chess"));

        var frame = FrameAssembler.assemble(tokens, resolver);

        assertThat(frame).isPresent();
        assertThat(frame.get().verb()).isSameAs(CREATE);
        assertThat(frame.get().verbModifiers()).containsExactly(QUIETLY);
        assertThat(frame.get().modifiersFor(ThematicRole.Theme.IID)).containsExactly(PUBLIC_ADJ);
    }

    // ==================================================================================
    // Multi-verb conjunction: "create chess and show librarian"
    // ==================================================================================

    @Test
    void multiVerbConjunction() {
        var tokens = List.of(
                link(CREATE), link(CHESS_IID, "chess"),
                link(AND),
                link(SHOW), link(LIBRARIAN_IID, "librarian"));

        var frames = FrameAssembler.assembleAll(tokens, resolver);

        assertThat(frames).hasSize(2);

        // First frame: create chess
        assertThat(frames.get(0).verb()).isSameAs(CREATE);
        assertThat(frames.get(0).bindings()).containsEntry(ThematicRole.Theme.IID, CHESS_ITEM);

        // Second frame: show librarian
        assertThat(frames.get(1).verb()).isSameAs(SHOW);
        assertThat(frames.get(1).bindings()).containsEntry(ThematicRole.Theme.IID, LIBRARIAN_ITEM);
    }

    // ==================================================================================
    // Single verb with conjunction noun is NOT split
    // ==================================================================================

    @Test
    void singleVerbConjunctionNotSplit() {
        // "create chess between bob and jane" should NOT split
        var tokens = List.of(
                link(CREATE), link(CHESS_IID, "chess"),
                link(BETWEEN), link(BOB_IID, "bob"),
                link(AND), link(JANE_IID, "jane"));

        var frames = FrameAssembler.assembleAll(tokens, resolver);

        // Should produce 1 frame (not split at AND — no verb after AND)
        assertThat(frames).hasSize(1);
        assertThat(frames.get(0).verb()).isSameAs(CREATE);
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    /** Add EXPECTS ROLE frames for the given role IIDs to a sememe. */
    private static Sememe withExpects(Sememe sememe, ItemID... roleIids) {
        for (ItemID roleIid : roleIids) {
            // Qualified with ROLE (ThematicRole.KEY) so slotRoles() picks them up
            List<CompoundKey.FrameToken> qualifiers = List.of(
                    new CompoundKey.Sememe(ItemID.fromString(ThematicRole.KEY)),
                    new CompoundKey.Sememe(roleIid));
            var binding = new Binding(ThematicRole.Topic.IID, qualifiers,
                    dev.everydaythings.graph.frame.BindingTarget.iid(roleIid), true, false);
            sememe.endorseFrame(new FrameBodyOld(CoreVocabulary.Expects.IID,
                    List.of(Binding.ref(ThematicRole.Theme.IID, sememe.iid()), binding)));
        }
        return sememe;
    }
}
