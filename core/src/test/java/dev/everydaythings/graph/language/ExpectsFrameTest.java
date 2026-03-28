package dev.everydaythings.graph.language;

import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.Frame;
import dev.everydaythings.graph.frame.FrameBody;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.library.SeedItemFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that EXPECTS frames declared via {@code @ItemFrame} annotations
 * are properly materialized by {@link SeedItemFactory}.
 *
 * <p>EXPECTS frames on a type declare what frames instances should carry.
 * Their declaration order is the salience order for handle disambiguation.
 */
class ExpectsFrameTest {

    /**
     * Minimal test seed with EXPECTS declarations via @ItemFrame.
     * Uses CoreVocabulary predicates that exist in core (no games dependency).
     *
     * <p>The expected predicate is a qualifier on TOPIC — this ensures unique
     * FrameKeys so frames don't overwrite each other. Same pattern as LEXEME
     * where [ENGLISH, VERB, LEMMA] distinguishes different lexemes.
     */
    @dev.everydaythings.graph.item.ItemSeed(key = TestType.KEY)
    static class TestType {
        static final String KEY = "test:type/document";

        @dev.everydaythings.graph.frame.ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                fieldAs = @dev.everydaythings.graph.frame.ItemFrame.Bind(
                        role = ThematicRole.Topic.KEY,
                        qualifiers = {CoreVocabulary.Author.KEY}))
        static final ItemID expectAuthor = ItemID.fromString(CoreVocabulary.Author.KEY);

        @dev.everydaythings.graph.frame.ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                fieldAs = @dev.everydaythings.graph.frame.ItemFrame.Bind(
                        role = ThematicRole.Topic.KEY,
                        qualifiers = {CoreVocabulary.Title.KEY}))
        static final ItemID expectTitle = ItemID.fromString(CoreVocabulary.Title.KEY);

        @dev.everydaythings.graph.frame.ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                fieldAs = @dev.everydaythings.graph.frame.ItemFrame.Bind(
                        role = ThematicRole.Topic.KEY,
                        qualifiers = {CoreVocabulary.Description.KEY}))
        static final ItemID expectDescription = ItemID.fromString(CoreVocabulary.Description.KEY);
    }

    @Test
    void expects_framesAreMaterialized() {
        Item item = SeedItemFactory.create(TestType.class);
        assertThat(item).isNotNull();

        List<FrameBody> expectsBodies = expectsFrames(item);
        assertThat(expectsBodies).hasSize(3);
    }

    @Test
    void expects_preserveDeclarationOrder() {
        Item item = SeedItemFactory.create(TestType.class);
        assertThat(item).isNotNull();

        List<FrameBody> expectsBodies = expectsFrames(item);

        // Declaration order = salience order: Author, Title, Description
        assertThat(topicId(expectsBodies.get(0)))
                .isEqualTo(ItemID.fromString(CoreVocabulary.Author.KEY));
        assertThat(topicId(expectsBodies.get(1)))
                .isEqualTo(ItemID.fromString(CoreVocabulary.Title.KEY));
        assertThat(topicId(expectsBodies.get(2)))
                .isEqualTo(ItemID.fromString(CoreVocabulary.Description.KEY));
    }

    @Test
    void expects_homeBindingIsThisType() {
        Item item = SeedItemFactory.create(TestType.class);
        assertThat(item).isNotNull();

        List<FrameBody> expectsBodies = expectsFrames(item);
        for (FrameBody body : expectsBodies) {
            assertThat(body.homeId())
                    .as("EXPECTS frame home should be the type item")
                    .isEqualTo(item.iid());
        }
    }

    @Test
    void expects_predicateIsExpects() {
        Item item = SeedItemFactory.create(TestType.class);
        assertThat(item).isNotNull();

        List<FrameBody> expectsBodies = expectsFrames(item);
        for (FrameBody body : expectsBodies) {
            assertThat(body.predicate())
                    .isEqualTo(CoreVocabulary.Expects.IID);
        }
    }

    @Test
    void expects_qualifiersArePreserved() {
        // Seed with qualified EXPECTS (like chess Player(WHITE) vs Player(BLACK))
        @dev.everydaythings.graph.item.ItemSeed(key = "test:type/game")
        class GameType {
            @dev.everydaythings.graph.frame.ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                    fieldAs = @dev.everydaythings.graph.frame.ItemFrame.Bind(
                            role = ThematicRole.Topic.KEY,
                            qualifiers = {ColorVocabulary.White.KEY}))
            static final ItemID expectWhite = ItemID.fromString(CoreVocabulary.Author.KEY);

            @dev.everydaythings.graph.frame.ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                    fieldAs = @dev.everydaythings.graph.frame.ItemFrame.Bind(
                            role = ThematicRole.Topic.KEY,
                            qualifiers = {ColorVocabulary.Black.KEY}))
            static final ItemID expectBlack = ItemID.fromString(CoreVocabulary.Author.KEY);
        }

        Item item = SeedItemFactory.create(GameType.class);
        assertThat(item).isNotNull();

        List<FrameBody> expectsBodies = expectsFrames(item);
        assertThat(expectsBodies).hasSize(2);

        // First: TOPIC:[WHITE] → Author
        Binding topic0 = expectsBodies.get(0).getBindingByRole(ThematicRole.Topic.IID);
        assertThat(topic0).isNotNull();
        assertThat(topic0.hasQualifiers()).isTrue();
        assertThat(topic0.key()).contains(ItemID.fromString(ColorVocabulary.White.KEY));

        // Second: TOPIC:[BLACK] → Author
        Binding topic1 = expectsBodies.get(1).getBindingByRole(ThematicRole.Topic.IID);
        assertThat(topic1).isNotNull();
        assertThat(topic1.hasQualifiers()).isTrue();
        assertThat(topic1.key()).contains(ItemID.fromString(ColorVocabulary.Black.KEY));
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    /** Extract all EXPECTS FrameBodies from an item, in frame iteration order. */
    private static List<FrameBody> expectsFrames(Item item) {
        List<FrameBody> result = new ArrayList<>();
        for (Frame frame : item.frames()) {
            if (frame.body() != null
                    && CoreVocabulary.Expects.IID.equals(frame.body().predicate())) {
                result.add(frame.body());
            }
        }
        return result;
    }

    /** Get the TOPIC binding's target IID from an EXPECTS frame body. */
    private static ItemID topicId(FrameBody body) {
        Binding topic = body.getBindingByRole(ThematicRole.Topic.IID);
        return topic != null ? topic.targetId() : null;
    }
}
