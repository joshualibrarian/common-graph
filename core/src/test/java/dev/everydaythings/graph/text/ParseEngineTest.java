package dev.everydaythings.graph.text;


import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.id.DatumRef;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.text.AnchorTable.TokenAnchor;
import dev.everydaythings.graph.text.FrameMap.BindingMap;
import dev.everydaythings.graph.text.FrameMap.Part;
import dev.everydaythings.graph.text.TokenLattice.TokenSpan;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end smoke tests for the consensus parse pipeline.
 *
 * <p>Each test sets up an in-memory librarian, registers a minimal active sememe
 * (here {@link AddSememe}), seeds the dictionary with its symbol, runs a parse
 * through the full {@code Item.parse(input, params)} → {@link ParseEngine#run}
 * path, and asserts on the resulting {@link FrameMap}.
 */
class ParseEngineTest {

    private static final ItemRef ADD_IID = ItemRef.fromString("test.predicate:add");
    private static final ItemRef SYMBOL_PREDICATE = ItemRef.fromString("test.predicate:symbol");

    private Librarian lib;
    private Item orchestrator;

    @BeforeEach
    void setUp() {
        lib = Librarian.inMemory();

        // Active ADD sememe registered with the librarian.
        AddSememe add = new AddSememe(ADD_IID, lib);
        lib.register(add);

        // Persist a body indexing "+" → ADD as THEME. Library auto-indexes text-target bindings.
        Body body = Body.of(
                ItemRef.of(SYMBOL_PREDICATE),
                List.of(
                        Binding.ref(ItemRef.iid(ThematicRole.Theme.KEY), ADD_IID),
                        new Binding(
                                ItemRef.iid(ThematicRole.Value.KEY),
                                List.of(),
                                "+")));
        DatumRef bodyCid = lib.persist(body);
        assertThat(bodyCid).isNotNull();

        orchestrator = new Item(ItemRef.fromString("test.orchestrator"), lib);
    }

    @Nested
    @DisplayName("simple binary addition")
    class BinaryAddition {

        @Test
        @DisplayName("'5+3' resolves to ADD with THEME=5, GOAL=3")
        void basic() {
            FrameMap result = orchestrator.parse("5+3", ParseParams.defaults());

            assertThat(result.text()).isEqualTo("5+3");
            assertThat(result.predicate()).isNotNull();
            assertThat(result.predicate().value().iid()).isEqualTo(ADD_IID);

            BindingMap theme = findBinding(result, ItemRef.iid(ThematicRole.Theme.KEY));
            assertThat(theme).as("THEME binding present").isNotNull();
            assertThat(integerValue(theme)).isEqualTo(5L);

            BindingMap goal = findBinding(result, ItemRef.iid(ThematicRole.Goal.KEY));
            assertThat(goal).as("GOAL binding present").isNotNull();
            assertThat(integerValue(goal)).isEqualTo(3L);
        }

        @Test
        @DisplayName("whitespace around operator is tolerated: '5 + 3' parses identically")
        void whitespaceAroundOperator() {
            FrameMap result = orchestrator.parse("5 + 3", ParseParams.defaults());

            assertThat(result.predicate().value().iid()).isEqualTo(ADD_IID);
            assertThat(integerValue(findBinding(result, ItemRef.iid(ThematicRole.Theme.KEY)))).isEqualTo(5L);
            assertThat(integerValue(findBinding(result, ItemRef.iid(ThematicRole.Goal.KEY)))).isEqualTo(3L);
        }

        @Test
        @DisplayName("missing left operand: '+3' produces ADD with only GOAL filled")
        void leadingOperator() {
            FrameMap result = orchestrator.parse("+3", ParseParams.defaults());

            assertThat(result.predicate().value().iid()).isEqualTo(ADD_IID);
            assertThat(findBinding(result, ItemRef.iid(ThematicRole.Theme.KEY)))
                    .as("THEME absent (no left operand)").isNull();
            assertThat(integerValue(findBinding(result, ItemRef.iid(ThematicRole.Goal.KEY)))).isEqualTo(3L);
        }

        @Test
        @DisplayName("missing right operand: '5+' produces ADD with only THEME filled")
        void trailingOperator() {
            FrameMap result = orchestrator.parse("5+", ParseParams.defaults());

            assertThat(result.predicate().value().iid()).isEqualTo(ADD_IID);
            assertThat(integerValue(findBinding(result, ItemRef.iid(ThematicRole.Theme.KEY)))).isEqualTo(5L);
            assertThat(findBinding(result, ItemRef.iid(ThematicRole.Goal.KEY)))
                    .as("GOAL absent (no right operand)").isNull();
        }

        @Test
        @DisplayName("operator alone: '+' produces ADD with no bindings")
        void operatorAlone() {
            FrameMap result = orchestrator.parse("+", ParseParams.defaults());

            assertThat(result.predicate().value().iid()).isEqualTo(ADD_IID);
            assertThat(result.bindings()).isEmpty();
        }
    }

    @Nested
    @DisplayName("PROBE: real seeded operators (bootstrapped Add/Subtract/Multiply/Negate)")
    class RealSeededOperators {

        private Item realOrchestrator;

        @BeforeEach
        void seedRealOps() {
            // Use a fresh lib (not the outer setUp's lib, which pre-registers a test
            // AddSememe at confidence 0.95 that would dominate over real seeded
            // operators' precedence-and-fitness-derived confidences).
            lib = Librarian.inMemory();
            lib.bootstrap();
            realOrchestrator = new Item(ItemRef.fromString("test.real-orchestrator"), lib);
        }

        private FrameMap parse(String input) {
            return realOrchestrator.parse(input, ParseParams.defaults());
        }

        /** Render a FrameMap as a debug string so failure messages show what we got. */
        private String describe(FrameMap fm) {
            StringBuilder sb = new StringBuilder();
            sb.append("predicate=");
            sb.append(fm.predicate() == null ? "null" : fm.predicate().value());
            sb.append(", bindings=[");
            for (int i = 0; i < fm.bindings().size(); i++) {
                if (i > 0) sb.append(", ");
                BindingMap b = fm.bindings().get(i);
                sb.append(b.role() == null ? "null-role" : b.role().value().iid());
                sb.append("→");
                sb.append(b.target() == null ? "null-target" : b.target().value());
            }
            sb.append("]");
            return sb.toString();
        }

        @Test
        @DisplayName("'5+3' → Add { THEME=5, GOAL=3 }")
        void simpleBinaryReal() {
            FrameMap result = parse("5+3");
            assertThat(result.predicate().value().iid())
                    .isEqualTo(ItemRef.iid(dev.everydaythings.graph.operator.math.Add.KEY));
            assertThat(integerValue(findBinding(result, ItemRef.iid(ThematicRole.Theme.KEY)))).isEqualTo(5L);
            assertThat(integerValue(findBinding(result, ItemRef.iid(ThematicRole.Goal.KEY)))).isEqualTo(3L);
        }

        @Test
        @DisplayName("'5 + 3 * 2' → Add { THEME=5, GOAL=Multiply{3,2} } — sub-frame nesting by precedence")
        void mixedPrecedence() {
            FrameMap result = parse("5 + 3 * 2");
            assertThat(result.predicate().value().iid())
                    .isEqualTo(ItemRef.iid(dev.everydaythings.graph.operator.math.Add.KEY));
            assertThat(integerValue(findBinding(result, ItemRef.iid(ThematicRole.Theme.KEY)))).isEqualTo(5L);

            // GOAL should be a FrameMapTarget wrapping the Multiply sub-frame.
            BindingMap goalBinding = findBinding(result, ItemRef.iid(ThematicRole.Goal.KEY));
            assertThat(goalBinding).as("GOAL binding present").isNotNull();
            assertThat(goalBinding.target().value())
                    .as("GOAL target is a FrameMapTarget (nested sub-expression)")
                    .isInstanceOf(FrameMapTarget.class);

            FrameMap nestedMultiply = ((FrameMapTarget) goalBinding.target().value()).frameMap();
            assertThat(nestedMultiply.predicate().value().iid())
                    .isEqualTo(ItemRef.iid(dev.everydaythings.graph.operator.math.Multiply.KEY));
            assertThat(integerValue(findBinding(nestedMultiply, ItemRef.iid(ThematicRole.Theme.KEY)))).isEqualTo(3L);
            assertThat(integerValue(findBinding(nestedMultiply, ItemRef.iid(ThematicRole.Goal.KEY)))).isEqualTo(2L);
        }

        @Test
        @DisplayName("'(5+3)*2' → Multiply { THEME=Add{5,3}, GOAL=2 } — explicit parens via OpenGroup/CloseGroup")
        void parens() {
            FrameMap result = parse("(5+3)*2");
            // OpenGroup / CloseGroup recognize "(" and ")" as text-layer markers.
            // Operator.parse's neighbor resolver detects them and recursively
            // parses the bracketed text into a FrameMapTarget — same artifact
            // precedence-driven nesting produces.
            assertThat(result.predicate().value().iid())
                    .isEqualTo(ItemRef.iid(dev.everydaythings.graph.operator.math.Multiply.KEY));

            BindingMap themeBinding = findBinding(result, ItemRef.iid(ThematicRole.Theme.KEY));
            assertThat(themeBinding).as("THEME binding present").isNotNull();
            assertThat(themeBinding.target().value())
                    .as("THEME target is a FrameMapTarget (parenthesized sub-expression)")
                    .isInstanceOf(FrameMapTarget.class);
            FrameMap nestedAdd = ((FrameMapTarget) themeBinding.target().value()).frameMap();
            assertThat(nestedAdd.predicate().value().iid())
                    .isEqualTo(ItemRef.iid(dev.everydaythings.graph.operator.math.Add.KEY));
            assertThat(integerValue(findBinding(nestedAdd, ItemRef.iid(ThematicRole.Theme.KEY)))).isEqualTo(5L);
            assertThat(integerValue(findBinding(nestedAdd, ItemRef.iid(ThematicRole.Goal.KEY)))).isEqualTo(3L);

            assertThat(integerValue(findBinding(result, ItemRef.iid(ThematicRole.Goal.KEY)))).isEqualTo(2L);
        }

        @Test
        @DisplayName("'-5' → Negate { THEME=5 } — context fitness disambiguates Negate (prefix) from Subtract (infix)")
        void unaryPrefix() {
            FrameMap result = parse("-5");
            // Both Negate (prefix) and Subtract (infix) match "-". Negate wins because
            // its prefix fitness (right operand present, no left) is 1.0, while Subtract's
            // infix fitness with no left operand is only 0.5.
            assertThat(result.predicate().value().iid())
                    .isEqualTo(ItemRef.iid(dev.everydaythings.graph.operator.math.Negate.KEY));
            assertThat(integerValue(findBinding(result, ItemRef.iid(ThematicRole.Theme.KEY)))).isEqualTo(5L);
            assertThat(findBinding(result, ItemRef.iid(ThematicRole.Goal.KEY)))
                    .as("unary Negate has no GOAL binding").isNull();
        }

        @Test
        @DisplayName("'2^3^4' → Power { THEME=2, GOAL=Power{3,4} } — right-associative chain")
        void rightAssocChain() {
            FrameMap result = parse("2^3^4");
            // Power is right-associative (precedence 30). Right-assoc means the
            // outer is the LEFTMOST anchor; inner is rightmost.
            assertThat(result.predicate().value().iid())
                    .isEqualTo(ItemRef.iid(dev.everydaythings.graph.operator.math.Power.KEY));
            assertThat(integerValue(findBinding(result, ItemRef.iid(ThematicRole.Theme.KEY)))).isEqualTo(2L);

            BindingMap goalBinding = findBinding(result, ItemRef.iid(ThematicRole.Goal.KEY));
            assertThat(goalBinding).as("GOAL binding present").isNotNull();
            assertThat(goalBinding.target().value())
                    .as("GOAL target is a FrameMapTarget (the right-side chain segment)")
                    .isInstanceOf(FrameMapTarget.class);
            FrameMap nestedPower = ((FrameMapTarget) goalBinding.target().value()).frameMap();
            assertThat(nestedPower.predicate().value().iid())
                    .isEqualTo(ItemRef.iid(dev.everydaythings.graph.operator.math.Power.KEY));
            assertThat(integerValue(findBinding(nestedPower, ItemRef.iid(ThematicRole.Theme.KEY)))).isEqualTo(3L);
            assertThat(integerValue(findBinding(nestedPower, ItemRef.iid(ThematicRole.Goal.KEY)))).isEqualTo(4L);
        }

        @Test
        @DisplayName("'(2^3)^4' → Power { THEME=Power{2,3}, GOAL=4 } — parens override right-assoc")
        void parensOverrideRightAssoc() {
            FrameMap result = parse("(2^3)^4");
            // Even though Power is right-associative, the parens force the inner
            // Power to be the LEFT operand of the outer Power. The inner '^' is
            // skipped at the outer level (it's inside parens) and handled by the
            // recursive parse of the bracketed text.
            assertThat(result.predicate().value().iid())
                    .isEqualTo(ItemRef.iid(dev.everydaythings.graph.operator.math.Power.KEY));

            BindingMap themeBinding = findBinding(result, ItemRef.iid(ThematicRole.Theme.KEY));
            assertThat(themeBinding.target().value())
                    .as("THEME target is a FrameMapTarget (parenthesized inner Power)")
                    .isInstanceOf(FrameMapTarget.class);
            FrameMap innerPower = ((FrameMapTarget) themeBinding.target().value()).frameMap();
            assertThat(innerPower.predicate().value().iid())
                    .isEqualTo(ItemRef.iid(dev.everydaythings.graph.operator.math.Power.KEY));
            assertThat(integerValue(findBinding(innerPower, ItemRef.iid(ThematicRole.Theme.KEY)))).isEqualTo(2L);
            assertThat(integerValue(findBinding(innerPower, ItemRef.iid(ThematicRole.Goal.KEY)))).isEqualTo(3L);

            assertThat(integerValue(findBinding(result, ItemRef.iid(ThematicRole.Goal.KEY)))).isEqualTo(4L);
        }

        @Test
        @DisplayName("'5 - 3 - 2 - 1' → deeply nested left-assoc chain (3 anchors)")
        void deepLeftAssocChain() {
            FrameMap result = parse("5 - 3 - 2 - 1");
            // ((5 - 3) - 2) - 1 — outermost is the rightmost '-'.
            assertThat(result.predicate().value().iid())
                    .isEqualTo(ItemRef.iid(dev.everydaythings.graph.operator.math.Subtract.KEY));
            assertThat(integerValue(findBinding(result, ItemRef.iid(ThematicRole.Goal.KEY)))).isEqualTo(1L);

            // THEME is (5 - 3) - 2
            FrameMap level2 = ((FrameMapTarget) findBinding(result, ItemRef.iid(ThematicRole.Theme.KEY)).target().value()).frameMap();
            assertThat(level2.predicate().value().iid())
                    .isEqualTo(ItemRef.iid(dev.everydaythings.graph.operator.math.Subtract.KEY));
            assertThat(integerValue(findBinding(level2, ItemRef.iid(ThematicRole.Goal.KEY)))).isEqualTo(2L);

            // level2's THEME is 5 - 3
            FrameMap level1 = ((FrameMapTarget) findBinding(level2, ItemRef.iid(ThematicRole.Theme.KEY)).target().value()).frameMap();
            assertThat(level1.predicate().value().iid())
                    .isEqualTo(ItemRef.iid(dev.everydaythings.graph.operator.math.Subtract.KEY));
            assertThat(integerValue(findBinding(level1, ItemRef.iid(ThematicRole.Theme.KEY)))).isEqualTo(5L);
            assertThat(integerValue(findBinding(level1, ItemRef.iid(ThematicRole.Goal.KEY)))).isEqualTo(3L);
        }

        @Test
        @DisplayName("'5 - 3 - 2' → Subtract { THEME=Subtract{5,3}, GOAL=2 } — multi-anchor chain (left-assoc)")
        void leftAssocChain() {
            FrameMap result = parse("5 - 3 - 2");
            // Left-associative: outer is the rightmost anchor, inner the leftmost.
            // Operator.parse iterates all its anchor spans and builds the nested
            // chain by accumulating left-to-right.
            assertThat(result.predicate().value().iid())
                    .isEqualTo(ItemRef.iid(dev.everydaythings.graph.operator.math.Subtract.KEY));

            BindingMap themeBinding = findBinding(result, ItemRef.iid(ThematicRole.Theme.KEY));
            assertThat(themeBinding).as("THEME binding present").isNotNull();
            assertThat(themeBinding.target().value())
                    .as("THEME target is a FrameMapTarget (the prior chain segment)")
                    .isInstanceOf(FrameMapTarget.class);
            FrameMap nestedSubtract = ((FrameMapTarget) themeBinding.target().value()).frameMap();
            assertThat(nestedSubtract.predicate().value().iid())
                    .isEqualTo(ItemRef.iid(dev.everydaythings.graph.operator.math.Subtract.KEY));
            assertThat(integerValue(findBinding(nestedSubtract, ItemRef.iid(ThematicRole.Theme.KEY)))).isEqualTo(5L);
            assertThat(integerValue(findBinding(nestedSubtract, ItemRef.iid(ThematicRole.Goal.KEY)))).isEqualTo(3L);

            assertThat(integerValue(findBinding(result, ItemRef.iid(ThematicRole.Goal.KEY)))).isEqualTo(2L);
        }
    }

    private static BindingMap findBinding(FrameMap fm, ItemRef role) {
        for (BindingMap b : fm.bindings()) {
            if (b.role() != null && b.role().value() != null
                    && b.role().value().iid().equals(role)) {
                return b;
            }
        }
        return null;
    }

    private static long integerValue(BindingMap b) {
        return (Long) b.target().value();
    }

    /**
     * Active sememe representing the ADD operator. Per the white paper, ADD models a
     * transfer: the left operand is the THEME (quantity in motion), the right operand
     * is the GOAL (destination). Per "add 5 to 3" → {@code ADD { THEME → 5, GOAL → 3 }}.
     *
     * <p>Implementation walks {@code ctx.tokens()} to find this sememe's anchor token
     * and its non-whitespace neighbors. Missing neighbors yield a partial frame
     * (binding simply absent) rather than an error — graceful degradation is the
     * v1 robustness story for underdetermined parses.
     */
    static class AddSememe extends Item {

        private static final BigDecimal PREDICATE_CONFIDENCE = new BigDecimal("0.95");
        private static final BigDecimal BINDING_CONFIDENCE = new BigDecimal("0.9");

        AddSememe(ItemRef iid, Librarian librarian) {
            super(iid, librarian);
        }

        @Override
        public FrameMap parse(ParseContext ctx) {
            Optional<TokenAnchor> selfAnchor = findSelfAnchor(ctx);
            if (selfAnchor.isEmpty() || selfAnchor.get().spans().isEmpty()) {
                return FrameMap.empty();
            }

            // v1: handle just the first occurrence of this operator in the input.
            TextSpan myAnchor = selfAnchor.get().spans().get(0);
            int myIdx = indexOfToken(ctx.tokens(), myAnchor);
            if (myIdx < 0) return FrameMap.empty();

            TokenSpan left = (myIdx > 0) ? ctx.tokens().get(myIdx - 1) : null;
            TokenSpan right = (myIdx < ctx.tokens().size() - 1)
                    ? ctx.tokens().get(myIdx + 1) : null;

            List<BindingMap> bindings = new ArrayList<>();
            BindingMap themeBinding = makeBinding(left, ItemRef.iid(ThematicRole.Theme.KEY));
            if (themeBinding != null) bindings.add(themeBinding);
            BindingMap goalBinding = makeBinding(right, ItemRef.iid(ThematicRole.Goal.KEY));
            if (goalBinding != null) bindings.add(goalBinding);

            return new FrameMap(
                    null,
                    new Part<>(ItemRef.of(this.iid()), PREDICATE_CONFIDENCE, List.of(myAnchor)),
                    bindings,
                    List.of());
        }

        private Optional<TokenAnchor> findSelfAnchor(ParseContext ctx) {
            return ctx.anchors().tokenAnchors().stream()
                    .filter(ta -> ta.participant().iid().equals(this.iid()))
                    .findFirst();
        }

        private static int indexOfToken(List<TokenSpan> tokens, TextSpan span) {
            for (int i = 0; i < tokens.size(); i++) {
                if (tokens.get(i).span().equals(span)) return i;
            }
            return -1;
        }

        /** Build a binding for the given role from a neighboring token, or null if no usable target. */
        private static BindingMap makeBinding(TokenSpan token, ItemRef role) {
            if (token == null) return null;
            Object target = tokenToBindingTarget(token);
            if (target == null) return null;
            return new BindingMap(
                    new Part<>(ItemRef.of(role), BINDING_CONFIDENCE, List.of()),
                    List.of(),
                    new Part<>(target, BINDING_CONFIDENCE, List.of(token.span())));
        }

        /** Convert a token to a BindingTarget. Integer literals → Literal.ofInteger; word with posting → IID ref. */
        private static Object tokenToBindingTarget(TokenSpan token) {
            if (token.kind() == TokenLattice.Kind.LITERAL) {
                try {
                    return (long) (Long.parseLong(token.surfaceText().trim()));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            if (!token.postings().isEmpty()) {
                return token.postings().get(0).target();
            }
            return null;
        }
    }
}
