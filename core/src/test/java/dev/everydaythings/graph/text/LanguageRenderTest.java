package dev.everydaythings.graph.text;

import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.Body;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.ItemRef;
import dev.everydaythings.graph.linguistics.Language;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.semantics.ThematicRole;
import dev.everydaythings.graph.text.FrameMap.BindingMap;
import dev.everydaythings.graph.text.FrameMap.Part;
import dev.everydaythings.graph.value.Add;
import dev.everydaythings.graph.value.Decimal;
import dev.everydaythings.graph.value.Multiply;
import dev.everydaythings.graph.value.NotationVocabulary;
import dev.everydaythings.graph.value.Subtract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke tests for {@link Language#render} — the inverse of parsing.
 *
 * <p>Tests the universal-notation infix path on the base {@code Language} class:
 * a frame whose predicate has a {@link NotationVocabulary.Symbol Symbol} frame
 * endorsed renders as {@code <left> <symbol> <right>}.
 */
class LanguageRenderTest {

    private static final ItemID ADD_IID = ItemID.fromString("test.predicate:add");

    @Test
    @DisplayName("ADD frame with two integer-literal bindings renders infix as '5 + 3'")
    void infixRender() {
        Librarian lib = Librarian.inMemory();

        // 1. Create the ADD item and register it with the librarian.
        Item add = new Item(ADD_IID, lib);
        lib.register(add);

        // 2. Persist a Symbol body declaring "+" is the symbol of ADD.
        Body symbolBody = Body.of(
                ItemRef.of(dev.everydaythings.graph.linguistics.Lexeme.IID),
                List.of(
                        Binding.ref(ThematicRole.Theme.IID, ADD_IID),
                        new Binding(
                                ThematicRole.Value.IID,
                                List.of(),
                                Literal.ofText("+"))));
        ContentID symbolCid = lib.persist(symbolBody);

        // 3. Commit ADD's manifest with ENDORSES binding pointing at the Symbol body.
        add.commit(lib, List.of(
                new Binding(Manifest.ENDORSES, BindingTarget.ref(symbolCid))));

        // 4. Construct a FrameMap representing ADD { THEME → 5, GOAL → 3 }.
        FrameMap framemap = new FrameMap(
                null,
                new Part<>(ItemRef.of(ADD_IID), Decimal.parse("1.0"), List.of()),
                List.of(
                        new BindingMap(
                                new Part<>(ItemRef.of(ThematicRole.Theme.IID), Decimal.parse("1.0"), List.of()),
                                List.of(),
                                new Part<>(Literal.ofInteger(5), Decimal.parse("1.0"), List.of())),
                        new BindingMap(
                                new Part<>(ItemRef.of(ThematicRole.Goal.IID), Decimal.parse("1.0"), List.of()),
                                List.of(),
                                new Part<>(Literal.ofInteger(3), Decimal.parse("1.0"), List.of()))),
                List.of());

        // 5. Render via a base Language instance — universal notation, language-neutral.
        Language language = new Language(Language.IID, lib);
        FrameMap rendered = language.render(framemap, ParseParams.defaults());

        // 6. Verify text is the infix form.
        assertThat(rendered.text()).isEqualTo("5 + 3");
    }

    @Test
    @DisplayName("frame with no Symbol on its predicate renders unchanged (no infix output)")
    void noSymbolMeansUnchanged() {
        Librarian lib = Librarian.inMemory();

        ItemID barePredicate = ItemID.fromString("test.predicate:no-symbol");
        Item bare = new Item(barePredicate, lib);
        lib.register(bare);
        // No Symbol frame, no commit — bare item with no manifest.

        FrameMap framemap = new FrameMap(
                null,
                new Part<>(ItemRef.of(barePredicate), Decimal.parse("1.0"), List.of()),
                List.of(
                        new BindingMap(
                                new Part<>(ItemRef.of(ThematicRole.Theme.IID), Decimal.parse("1.0"), List.of()),
                                List.of(),
                                new Part<>(Literal.ofInteger(5), Decimal.parse("1.0"), List.of()))),
                List.of());

        Language language = new Language(Language.IID, lib);
        FrameMap rendered = language.render(framemap, ParseParams.defaults());

        assertThat(rendered.text()).isNull();
    }

    @Test
    @DisplayName("seeded Subtract and Multiply: bootstrap correctly indexes their symbols")
    void otherOperatorsSeedAndIndex() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        assertThat(lib.lookupToken("-"))
                .as("'-' token resolves to Subtract (or Negate — both share the symbol)")
                .anyMatch(p -> Subtract.IID.equals(p.target()));

        assertThat(lib.lookupToken("*"))
                .as("'*' token resolves to Multiply")
                .anyMatch(p -> Multiply.IID.equals(p.target()));
    }

    @Test
    @DisplayName("seeded Add: TokenDictionary auto-indexing produces postings with target=Add.IID for '+' and 'add'")
    void seededAddTokensIndexedWithTarget() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        // The universal symbol "+" should resolve to a posting whose target is Add.
        // Same for the English verb lemma "add" — it's also a Lexeme frame on Add,
        // and the THEME → Add back-link added by SeedProcessor lets the
        // TokenDictionary's posting carry the proper target IID.
        var plusPostings = lib.lookupToken("+");
        assertThat(plusPostings).isNotEmpty();
        assertThat(plusPostings)
                .as("'+' posting target = Add.IID")
                .anyMatch(p -> Add.IID.equals(p.target()));

        var addPostings = lib.lookupToken("add");
        assertThat(addPostings).isNotEmpty();
        assertThat(addPostings)
                .as("'add' posting target = Add.IID")
                .anyMatch(p -> Add.IID.equals(p.target()));
    }

    @Test
    @DisplayName("real seeded Add operator: bootstrap creates Symbol/Fixity/Lexeme frames; render produces '5 + 3'")
    void seededAddRendersInfix() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();   // SeedProcessor scans @Seed classes, persists Add's manifest with its @Bind frames endorsed

        // Construct a frame against the real seeded Add IID.
        FrameMap framemap = new FrameMap(
                null,
                new Part<>(ItemRef.of(Add.IID), Decimal.parse("1.0"), List.of()),
                List.of(
                        new BindingMap(
                                new Part<>(ItemRef.of(ThematicRole.Theme.IID), Decimal.parse("1.0"), List.of()),
                                List.of(),
                                new Part<>(Literal.ofInteger(5), Decimal.parse("1.0"), List.of())),
                        new BindingMap(
                                new Part<>(ItemRef.of(ThematicRole.Goal.IID), Decimal.parse("1.0"), List.of()),
                                List.of(),
                                new Part<>(Literal.ofInteger(3), Decimal.parse("1.0"), List.of()))),
                List.of());

        Language language = new Language(Language.IID, lib);
        FrameMap rendered = language.render(framemap, ParseParams.defaults());

        assertThat(rendered.text()).isEqualTo("5 + 3");
    }

    @Test
    @DisplayName("frame with Symbol but only one binding does not render infix")
    void singleBindingDoesNotRenderInfix() {
        Librarian lib = Librarian.inMemory();

        Item add = new Item(ADD_IID, lib);
        lib.register(add);
        Body symbolBody = Body.of(
                ItemRef.of(dev.everydaythings.graph.linguistics.Lexeme.IID),
                List.of(
                        Binding.ref(ThematicRole.Theme.IID, ADD_IID),
                        new Binding(
                                ThematicRole.Value.IID,
                                List.of(),
                                Literal.ofText("+"))));
        ContentID symbolCid = lib.persist(symbolBody);
        add.commit(lib, List.of(
                new Binding(Manifest.ENDORSES, BindingTarget.ref(symbolCid))));

        FrameMap framemap = new FrameMap(
                null,
                new Part<>(ItemRef.of(ADD_IID), Decimal.parse("1.0"), List.of()),
                List.of(
                        new BindingMap(
                                new Part<>(ItemRef.of(ThematicRole.Theme.IID), Decimal.parse("1.0"), List.of()),
                                List.of(),
                                new Part<>(Literal.ofInteger(5), Decimal.parse("1.0"), List.of()))),
                List.of());

        Language language = new Language(Language.IID, lib);
        FrameMap rendered = language.render(framemap, ParseParams.defaults());

        assertThat(rendered.text()).isNull();
    }
}
