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
import dev.everydaythings.graph.operator.math.Add;
import dev.everydaythings.graph.value.Decimal;
import dev.everydaythings.graph.operator.math.Multiply;
import dev.everydaythings.graph.operator.math.Negate;
import dev.everydaythings.graph.operator.NotationVocabulary;
import dev.everydaythings.graph.operator.math.Subtract;
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

        // 2. Persist an operator-form Lexeme body declaring "+" as ADD's infix symbol.
        Body symbolBody = Body.of(
                ItemRef.of(dev.everydaythings.graph.linguistics.Lexeme.IID),
                List.of(
                        Binding.ref(ThematicRole.Theme.IID, ADD_IID),
                        new Binding(
                                ThematicRole.Value.IID,
                                List.of(new dev.everydaythings.graph.item.id.CompoundKey.Sememe(NotationVocabulary.Infix.IID)),
                                Literal.ofText("+"))));
        ContentID symbolCid = lib.persist(symbolBody);

        // 3. Commit ADD's manifest with ENDORSES binding pointing at the Lexeme body.
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
                                List.of(new dev.everydaythings.graph.item.id.CompoundKey.Sememe(NotationVocabulary.Infix.IID)),
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

    /**
     * Helper: build an outer ADD-style framemap whose two bindings are taken from
     * the supplied left/right targets. Used by the parens tests to assemble nested
     * expressions cheaply.
     */
    private static FrameMap addFrameMap(BindingTarget left, BindingTarget right) {
        return new FrameMap(
                null,
                new Part<>(ItemRef.of(Add.IID), Decimal.parse("1.0"), List.of()),
                List.of(
                        new BindingMap(
                                new Part<>(ItemRef.of(ThematicRole.Theme.IID), Decimal.parse("1.0"), List.of()),
                                List.of(),
                                new Part<>(left, Decimal.parse("1.0"), List.of())),
                        new BindingMap(
                                new Part<>(ItemRef.of(ThematicRole.Goal.IID), Decimal.parse("1.0"), List.of()),
                                List.of(),
                                new Part<>(right, Decimal.parse("1.0"), List.of()))),
                List.of());
    }

    /** Persist a binary frame body (predicate + two integer operands) and return its CID. */
    private static ContentID persistBinary(Librarian lib, ItemID predicate,
                                           long leftValue, long rightValue) {
        Body body = Body.of(
                ItemRef.of(predicate),
                List.of(
                        new Binding(ThematicRole.Theme.IID, Literal.ofInteger(leftValue)),
                        new Binding(ThematicRole.Goal.IID, Literal.ofInteger(rightValue))));
        return lib.persist(body);
    }

    /** Persist a unary frame body (predicate + one operand) and return its CID. */
    private static ContentID persistUnary(Librarian lib, ItemID predicate, BindingTarget operand) {
        Body body = Body.of(
                ItemRef.of(predicate),
                List.of(new Binding(ThematicRole.Theme.IID, operand)));
        return lib.persist(body);
    }

    /** Build a single-binding FrameMap (unary) with the given predicate and operand target. */
    private static FrameMap unaryFrameMap(ItemID predicate, BindingTarget operand) {
        return new FrameMap(
                null,
                new Part<>(ItemRef.of(predicate), Decimal.parse("1.0"), List.of()),
                List.of(
                        new BindingMap(
                                new Part<>(ItemRef.of(ThematicRole.Theme.IID), Decimal.parse("1.0"), List.of()),
                                List.of(),
                                new Part<>(operand, Decimal.parse("1.0"), List.of()))),
                List.of());
    }

    @Test
    @DisplayName("nested: '5 + 3 * 2' (multiply nested inside add) — inner binds tighter, no parens")
    void higherPrecedenceInnerNeedsNoParens() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        // GOAL of ADD = MULTIPLY { THEME → 3, GOAL → 2 } (precedence 20 > add's 10)
        ContentID multiplyCid = persistBinary(lib, Multiply.IID, 3, 2);
        FrameMap framemap = addFrameMap(
                Literal.ofInteger(5),
                BindingTarget.ref(multiplyCid));

        FrameMap rendered = new Language(Language.IID, lib).render(framemap, ParseParams.defaults());
        assertThat(rendered.text()).isEqualTo("5 + 3 * 2");
    }

    @Test
    @DisplayName("nested: '(5 + 3) * 2' — outer multiply, inner add; inner needs parens (lower precedence)")
    void lowerPrecedenceInnerNeedsParens() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        // Outer MULTIPLY { THEME → ADD{5,3}, GOAL → 2 }
        ContentID addCid = persistBinary(lib, Add.IID, 5, 3);
        FrameMap framemap = new FrameMap(
                null,
                new Part<>(ItemRef.of(Multiply.IID), Decimal.parse("1.0"), List.of()),
                List.of(
                        new BindingMap(
                                new Part<>(ItemRef.of(ThematicRole.Theme.IID), Decimal.parse("1.0"), List.of()),
                                List.of(),
                                new Part<>(BindingTarget.ref(addCid), Decimal.parse("1.0"), List.of())),
                        new BindingMap(
                                new Part<>(ItemRef.of(ThematicRole.Goal.IID), Decimal.parse("1.0"), List.of()),
                                List.of(),
                                new Part<>(Literal.ofInteger(2), Decimal.parse("1.0"), List.of()))),
                List.of());

        FrameMap rendered = new Language(Language.IID, lib).render(framemap, ParseParams.defaults());
        assertThat(rendered.text()).isEqualTo("(5 + 3) * 2");
    }

    @Test
    @DisplayName("equal precedence, left-associative: '5 - 3 - 2' — left side same prec needs no parens")
    void equalPrecedenceLeftSideLeftAssoc() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        // Outer SUBTRACT { THEME → SUBTRACT{5,3}, GOAL → 2 } — represents (5 - 3) - 2
        ContentID innerSubCid = persistBinary(lib, Subtract.IID, 5, 3);
        FrameMap framemap = new FrameMap(
                null,
                new Part<>(ItemRef.of(Subtract.IID), Decimal.parse("1.0"), List.of()),
                List.of(
                        new BindingMap(
                                new Part<>(ItemRef.of(ThematicRole.Theme.IID), Decimal.parse("1.0"), List.of()),
                                List.of(),
                                new Part<>(BindingTarget.ref(innerSubCid), Decimal.parse("1.0"), List.of())),
                        new BindingMap(
                                new Part<>(ItemRef.of(ThematicRole.Goal.IID), Decimal.parse("1.0"), List.of()),
                                List.of(),
                                new Part<>(Literal.ofInteger(2), Decimal.parse("1.0"), List.of()))),
                List.of());

        FrameMap rendered = new Language(Language.IID, lib).render(framemap, ParseParams.defaults());
        assertThat(rendered.text()).isEqualTo("5 - 3 - 2");
    }

    @Test
    @DisplayName("equal precedence, left-associative: '5 + (3 - 2)' — right side same prec needs parens")
    void equalPrecedenceRightSideLeftAssoc() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        // Outer ADD { THEME → 5, GOAL → SUBTRACT{3, 2} } — represents 5 + (3 - 2)
        ContentID innerSubCid = persistBinary(lib, Subtract.IID, 3, 2);
        FrameMap framemap = addFrameMap(
                Literal.ofInteger(5),
                BindingTarget.ref(innerSubCid));

        FrameMap rendered = new Language(Language.IID, lib).render(framemap, ParseParams.defaults());
        assertThat(rendered.text()).isEqualTo("5 + (3 - 2)");
    }

    @Test
    @DisplayName("unary prefix: NEGATE 5 renders as '-5' (no space after symbolic operator)")
    void unaryPrefixSimple() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        FrameMap framemap = unaryFrameMap(Negate.IID, Literal.ofInteger(5));
        FrameMap rendered = new Language(Language.IID, lib).render(framemap, ParseParams.defaults());
        assertThat(rendered.text()).isEqualTo("-5");
    }

    @Test
    @DisplayName("unary prefix on lower-precedence inner: NEGATE(ADD{5,3}) renders as '-(5 + 3)'")
    void unaryPrefixLowerPrecedenceInnerNeedsParens() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        // NEGATE { THEME → ADD{5,3} } — Negate prec 25 > Add prec 10, so inner needs parens.
        ContentID addCid = persistBinary(lib, Add.IID, 5, 3);
        FrameMap framemap = unaryFrameMap(Negate.IID, BindingTarget.ref(addCid));

        FrameMap rendered = new Language(Language.IID, lib).render(framemap, ParseParams.defaults());
        assertThat(rendered.text()).isEqualTo("-(5 + 3)");
    }

    @Test
    @DisplayName("unary prefix chained: NEGATE(NEGATE(5)) renders as '--5' (right-assoc, same prec)")
    void unaryPrefixChained() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        ContentID innerNegCid = persistUnary(lib, Negate.IID, Literal.ofInteger(5));
        FrameMap framemap = unaryFrameMap(Negate.IID, BindingTarget.ref(innerNegCid));

        FrameMap rendered = new Language(Language.IID, lib).render(framemap, ParseParams.defaults());
        assertThat(rendered.text()).isEqualTo("--5");
    }

    @Test
    @DisplayName("unary prefix as operand of binary: ADD{NEGATE(5), 3} renders as '-5 + 3' (no parens)")
    void unaryPrefixAsBinaryOperand() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();

        // ADD { THEME → NEGATE{5}, GOAL → 3 } — Negate prec 25 > Add prec 10, no parens.
        ContentID negCid = persistUnary(lib, Negate.IID, Literal.ofInteger(5));
        FrameMap framemap = addFrameMap(
                BindingTarget.ref(negCid),
                Literal.ofInteger(3));

        FrameMap rendered = new Language(Language.IID, lib).render(framemap, ParseParams.defaults());
        assertThat(rendered.text()).isEqualTo("-5 + 3");
    }

    /**
     * Manually wire a postfix operator into the librarian: register the predicate
     * item, persist its operator-form Lexeme (Value[Postfix] → symbol, precedence
     * 30 with left-associativity for postfix chains), and endorse the lexeme on the
     * predicate's manifest. Used to exercise postfix render without depending on a
     * seeded postfix operator (none exist yet in the codebase).
     */
    private static void wirePostfix(Librarian lib, ItemID predicate, String symbol,
                                    long precedence) {
        Item op = new Item(predicate, lib);
        lib.register(op);
        Body lexBody = Body.of(
                ItemRef.of(dev.everydaythings.graph.linguistics.Lexeme.IID),
                List.of(
                        Binding.ref(ThematicRole.Theme.IID, predicate),
                        new Binding(
                                ThematicRole.Value.IID,
                                List.of(new dev.everydaythings.graph.item.id.CompoundKey.Sememe(NotationVocabulary.Postfix.IID)),
                                Literal.ofText(symbol)),
                        new Binding(
                                ThematicRole.Attribute.IID,
                                List.of(new dev.everydaythings.graph.item.id.CompoundKey.Sememe(NotationVocabulary.Precedence.IID)),
                                Literal.ofInteger(precedence)),
                        new Binding(
                                ThematicRole.Attribute.IID,
                                List.of(new dev.everydaythings.graph.item.id.CompoundKey.Sememe(NotationVocabulary.Associativity.IID)),
                                BindingTarget.iid(NotationVocabulary.Left.IID))));
        ContentID lexCid = lib.persist(lexBody);
        op.commit(lib, List.of(
                new Binding(Manifest.ENDORSES, BindingTarget.ref(lexCid))));
    }

    @Test
    @DisplayName("unary postfix: FACTORIAL(5) renders as '5!' (no space after operand for symbolic postfix)")
    void unaryPostfixSimple() {
        Librarian lib = Librarian.inMemory();
        ItemID factorialIid = ItemID.fromString("test.predicate:factorial");
        wirePostfix(lib, factorialIid, "!", 30L);

        FrameMap framemap = unaryFrameMap(factorialIid, Literal.ofInteger(5));
        FrameMap rendered = new Language(Language.IID, lib).render(framemap, ParseParams.defaults());
        assertThat(rendered.text()).isEqualTo("5!");
    }

    @Test
    @DisplayName("unary postfix on lower-precedence inner: FACTORIAL(ADD{5,3}) renders as '(5 + 3)!'")
    void unaryPostfixLowerPrecedenceInnerNeedsParens() {
        Librarian lib = Librarian.inMemory();
        lib.bootstrap();
        ItemID factorialIid = ItemID.fromString("test.predicate:factorial");
        wirePostfix(lib, factorialIid, "!", 30L);

        // FACTORIAL { THEME → ADD{5,3} } — Factorial prec 30 > Add prec 10, inner needs parens.
        ContentID addCid = persistBinary(lib, Add.IID, 5, 3);
        FrameMap framemap = unaryFrameMap(factorialIid, BindingTarget.ref(addCid));

        FrameMap rendered = new Language(Language.IID, lib).render(framemap, ParseParams.defaults());
        assertThat(rendered.text()).isEqualTo("(5 + 3)!");
    }

    @Test
    @DisplayName("unary postfix chained: FACTORIAL(FACTORIAL(5)) renders as '5!!' (left-assoc, same prec)")
    void unaryPostfixChained() {
        Librarian lib = Librarian.inMemory();
        ItemID factorialIid = ItemID.fromString("test.predicate:factorial");
        wirePostfix(lib, factorialIid, "!", 30L);

        ContentID innerFactCid = persistUnary(lib, factorialIid, Literal.ofInteger(5));
        FrameMap framemap = unaryFrameMap(factorialIid, BindingTarget.ref(innerFactCid));

        FrameMap rendered = new Language(Language.IID, lib).render(framemap, ParseParams.defaults());
        assertThat(rendered.text()).isEqualTo("5!!");
    }
}
