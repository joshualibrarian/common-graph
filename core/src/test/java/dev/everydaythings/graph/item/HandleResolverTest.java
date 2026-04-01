package dev.everydaythings.graph.item;

import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.FrameBody;
import dev.everydaythings.graph.frame.ItemFrame;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.library.SeedItemFactory;
import dev.everydaythings.graph.runtime.Librarian;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link HandleResolver} — dynamic handle labels from EXPECTS frames.
 */
class HandleResolverTest {

    // Test type with EXPECTS declarations
    private static final String TYPE_KEY = "test:type/book";
    private static final String AUTHOR_KEY = CoreVocabulary.Author.KEY;
    private static final String TITLE_KEY = CoreVocabulary.Title.KEY;

    @ItemSeed(key = TYPE_KEY)
    static class BookType {
        // EXPECTS — Author is more salient than Title
        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                fieldAs = @ItemFrame.Bind(role = ThematicRole.Topic.KEY,
                        qualifiers = {AUTHOR_KEY}))
        static final ItemID expectAuthor = ItemID.fromString(AUTHOR_KEY);

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                fieldAs = @ItemFrame.Bind(role = ThematicRole.Topic.KEY,
                        qualifiers = {TITLE_KEY}))
        static final ItemID expectTitle = ItemID.fromString(TITLE_KEY);
    }

    @Implements(TYPE_KEY)
    static class BookItem extends Item {
        BookItem(Librarian lib) { super(lib); }
    }

    private Librarian librarian;

    @BeforeEach
    void setUp() {
        librarian = Librarian.createInMemory();
        // Create and cache the test type seed (not in bootstrap vocabulary)
        Item bookTypeSeed = SeedItemFactory.create(BookType.class);
        assertThat(bookTypeSeed).isNotNull();
        librarian.put(bookTypeSeed);
    }

    @Test
    void resolve_returnsNullForItemWithoutType() {
        Item plain = new Item(librarian);
        assertThat(HandleResolver.resolve(plain)).isNull();
    }

    @Test
    void resolve_returnsNullWhenNoExpectsFrames() {
        // Create an item with a type that has no EXPECTS
        Item item = new Item(librarian);
        assertThat(HandleResolver.resolve(item)).isNull();
    }

    @Test
    void resolve_extractsFeatureFromMatchingFrame() {
        BookItem book = new BookItem(librarian);
        librarian.put(book);

        // Add an Author frame with a literal name
        FrameBody authorFrame = new FrameBody(
                ItemID.fromString(AUTHOR_KEY),
                List.of(
                        new Binding(ThematicRole.Theme.IID, BindingTarget.iid(book.iid())),
                        Binding.literal(ThematicRole.Agent.IID, Literal.ofText("Tolkien"))
                ));
        book.endorseFrame(authorFrame);

        String label = HandleResolver.resolve(book);
        assertThat(label).isEqualTo("Tolkien");
    }

    @Test
    void resolve_respectsSalienceOrder() {
        BookItem book = new BookItem(librarian);
        librarian.put(book);

        // Add both Author and Title frames
        FrameBody authorFrame = new FrameBody(
                ItemID.fromString(AUTHOR_KEY),
                List.of(
                        new Binding(ThematicRole.Theme.IID, BindingTarget.iid(book.iid())),
                        Binding.literal(ThematicRole.Agent.IID, Literal.ofText("Tolkien"))
                ));
        book.endorseFrame(authorFrame);

        FrameBody titleFrame = new FrameBody(
                ItemID.fromString(TITLE_KEY),
                List.of(
                        new Binding(ThematicRole.Theme.IID, BindingTarget.iid(book.iid())),
                        Binding.literal(ThematicRole.Referent.IID, Literal.ofText("The Hobbit"))
                ));
        book.endorseFrame(titleFrame);

        // Author should be first (higher salience)
        String label = HandleResolver.resolve(book);
        assertThat(label).isEqualTo("Tolkien");
    }

    @Test
    void disambiguate_addsFeaturesUntilUnique() {
        BookItem book1 = new BookItem(librarian);
        BookItem book2 = new BookItem(librarian);
        librarian.put(book1);
        librarian.put(book2);

        // Both by Tolkien
        book1.endorseFrame(new FrameBody(ItemID.fromString(AUTHOR_KEY), List.of(
                new Binding(ThematicRole.Theme.IID, BindingTarget.iid(book1.iid())),
                Binding.literal(ThematicRole.Agent.IID, Literal.ofText("Tolkien")))));
        book1.endorseFrame(new FrameBody(ItemID.fromString(TITLE_KEY), List.of(
                new Binding(ThematicRole.Theme.IID, BindingTarget.iid(book1.iid())),
                Binding.literal(ThematicRole.Referent.IID, Literal.ofText("The Hobbit")))));

        book2.endorseFrame(new FrameBody(ItemID.fromString(AUTHOR_KEY), List.of(
                new Binding(ThematicRole.Theme.IID, BindingTarget.iid(book2.iid())),
                Binding.literal(ThematicRole.Agent.IID, Literal.ofText("Tolkien")))));
        book2.endorseFrame(new FrameBody(ItemID.fromString(TITLE_KEY), List.of(
                new Binding(ThematicRole.Theme.IID, BindingTarget.iid(book2.iid())),
                Binding.literal(ThematicRole.Referent.IID, Literal.ofText("The Silmarillion")))));

        // Author alone doesn't disambiguate (both "Tolkien")
        // Should add Title to distinguish
        String label1 = HandleResolver.resolve(book1, List.of(book2));
        String label2 = HandleResolver.resolve(book2, List.of(book1));

        assertThat(label1).isEqualTo("Tolkien, The Hobbit");
        assertThat(label2).isEqualTo("Tolkien, The Silmarillion");
    }

    @Test
    void disambiguate_firstFeatureSufficientWhenUnique() {
        BookItem book1 = new BookItem(librarian);
        BookItem book2 = new BookItem(librarian);
        librarian.put(book1);
        librarian.put(book2);

        // Different authors — first feature alone disambiguates
        book1.endorseFrame(new FrameBody(ItemID.fromString(AUTHOR_KEY), List.of(
                new Binding(ThematicRole.Theme.IID, BindingTarget.iid(book1.iid())),
                Binding.literal(ThematicRole.Agent.IID, Literal.ofText("Tolkien")))));
        book1.endorseFrame(new FrameBody(ItemID.fromString(TITLE_KEY), List.of(
                new Binding(ThematicRole.Theme.IID, BindingTarget.iid(book1.iid())),
                Binding.literal(ThematicRole.Referent.IID, Literal.ofText("The Hobbit")))));

        book2.endorseFrame(new FrameBody(ItemID.fromString(AUTHOR_KEY), List.of(
                new Binding(ThematicRole.Theme.IID, BindingTarget.iid(book2.iid())),
                Binding.literal(ThematicRole.Agent.IID, Literal.ofText("Rowling")))));
        book2.endorseFrame(new FrameBody(ItemID.fromString(TITLE_KEY), List.of(
                new Binding(ThematicRole.Theme.IID, BindingTarget.iid(book2.iid())),
                Binding.literal(ThematicRole.Referent.IID, Literal.ofText("Harry Potter")))));

        // Author alone distinguishes — no need for title
        String label1 = HandleResolver.resolve(book1, List.of(book2));
        assertThat(label1).isEqualTo("Tolkien");

        String label2 = HandleResolver.resolve(book2, List.of(book1));
        assertThat(label2).isEqualTo("Rowling");
    }

    // ==================================================================================
    // Universal fallback — types without EXPECTS
    // ==================================================================================

    @Test
    void fallback_extractsNameFromFrames() {
        // Plain Item with no @Implements — no type, no EXPECTS
        // Add a frame with a NAME binding
        Item item = new Item(librarian);
        librarian.put(item);

        FrameBody titleFrame = new FrameBody(
                ItemID.fromString(CoreVocabulary.Title.KEY),
                List.of(
                        new Binding(ThematicRole.Theme.IID, BindingTarget.iid(item.iid())),
                        Binding.literal(ThematicRole.Referent.IID, Literal.ofText("My Document"))
                ));
        item.endorseFrame(titleFrame);

        // Should fall back to scanning frames and find the NAME/REFERENT value
        String label = HandleResolver.resolve(item);
        assertThat(label).isEqualTo("My Document");
    }

    @Test
    void fallback_prioritizesNameOverAgent() {
        Item item = new Item(librarian);
        librarian.put(item);

        // Add both an Agent and a Name frame — Name should win (higher priority)
        item.endorseFrame(new FrameBody(
                ItemID.fromString(CoreVocabulary.Author.KEY),
                List.of(
                        new Binding(ThematicRole.Theme.IID, BindingTarget.iid(item.iid())),
                        Binding.literal(ThematicRole.Agent.IID, Literal.ofText("Tolkien"))
                )));

        item.endorseFrame(new FrameBody(
                ItemID.fromString(CoreVocabulary.Title.KEY),
                List.of(
                        new Binding(ThematicRole.Theme.IID, BindingTarget.iid(item.iid())),
                        Binding.literal(ThematicRole.Value.IID, Literal.ofText("The Hobbit"))
                )));

        String label = HandleResolver.resolve(item);
        // Name role has higher priority than Agent in fallback
        assertThat(label).isEqualTo("The Hobbit");
    }
}
