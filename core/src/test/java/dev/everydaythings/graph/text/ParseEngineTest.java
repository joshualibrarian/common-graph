package dev.everydaythings.graph.text;

import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.Body;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.ItemRef;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.semantics.ThematicRole;
import dev.everydaythings.graph.text.AnchorTable.TokenAnchor;
import dev.everydaythings.graph.text.FrameMap.BindingMap;
import dev.everydaythings.graph.text.FrameMap.Part;
import dev.everydaythings.graph.text.TokenLattice.TokenSpan;
import dev.everydaythings.graph.value.Decimal;
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

    private static final ItemID ADD_IID = ItemID.fromString("test.predicate:add");
    private static final ItemID SYMBOL_PREDICATE = ItemID.fromString("test.predicate:symbol");

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
                        Binding.ref(ThematicRole.Theme.IID, ADD_IID),
                        new Binding(
                                ThematicRole.Value.IID,
                                List.of(),
                                Literal.ofText("+"))));
        ContentID bodyCid = lib.persist(body);
        assertThat(bodyCid).isNotNull();

        orchestrator = new Item(ItemID.fromString("test.orchestrator"), lib);
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

            BindingMap theme = findBinding(result, ThematicRole.Theme.IID);
            assertThat(theme).as("THEME binding present").isNotNull();
            assertThat(integerValue(theme)).isEqualTo(5L);

            BindingMap goal = findBinding(result, ThematicRole.Goal.IID);
            assertThat(goal).as("GOAL binding present").isNotNull();
            assertThat(integerValue(goal)).isEqualTo(3L);
        }

        @Test
        @DisplayName("whitespace around operator is tolerated: '5 + 3' parses identically")
        void whitespaceAroundOperator() {
            FrameMap result = orchestrator.parse("5 + 3", ParseParams.defaults());

            assertThat(result.predicate().value().iid()).isEqualTo(ADD_IID);
            assertThat(integerValue(findBinding(result, ThematicRole.Theme.IID))).isEqualTo(5L);
            assertThat(integerValue(findBinding(result, ThematicRole.Goal.IID))).isEqualTo(3L);
        }

        @Test
        @DisplayName("missing left operand: '+3' produces ADD with only GOAL filled")
        void leadingOperator() {
            FrameMap result = orchestrator.parse("+3", ParseParams.defaults());

            assertThat(result.predicate().value().iid()).isEqualTo(ADD_IID);
            assertThat(findBinding(result, ThematicRole.Theme.IID))
                    .as("THEME absent (no left operand)").isNull();
            assertThat(integerValue(findBinding(result, ThematicRole.Goal.IID))).isEqualTo(3L);
        }

        @Test
        @DisplayName("missing right operand: '5+' produces ADD with only THEME filled")
        void trailingOperator() {
            FrameMap result = orchestrator.parse("5+", ParseParams.defaults());

            assertThat(result.predicate().value().iid()).isEqualTo(ADD_IID);
            assertThat(integerValue(findBinding(result, ThematicRole.Theme.IID))).isEqualTo(5L);
            assertThat(findBinding(result, ThematicRole.Goal.IID))
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

    private static BindingMap findBinding(FrameMap fm, ItemID role) {
        for (BindingMap b : fm.bindings()) {
            if (b.role() != null && b.role().value() != null
                    && b.role().value().iid().equals(role)) {
                return b;
            }
        }
        return null;
    }

    private static long integerValue(BindingMap b) {
        return ((Literal) b.target().value()).asInteger();
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

        private static final Decimal PREDICATE_CONFIDENCE = Decimal.parse("0.95");
        private static final Decimal BINDING_CONFIDENCE = Decimal.parse("0.9");

        AddSememe(ItemID iid, Librarian librarian) {
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
            BindingMap themeBinding = makeBinding(left, ThematicRole.Theme.IID);
            if (themeBinding != null) bindings.add(themeBinding);
            BindingMap goalBinding = makeBinding(right, ThematicRole.Goal.IID);
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
        private static BindingMap makeBinding(TokenSpan token, ItemID role) {
            if (token == null) return null;
            BindingTarget target = tokenToBindingTarget(token);
            if (target == null) return null;
            return new BindingMap(
                    new Part<>(ItemRef.of(role), BINDING_CONFIDENCE, List.of()),
                    List.of(),
                    new Part<>(target, BINDING_CONFIDENCE, List.of(token.span())));
        }

        /** Convert a token to a BindingTarget. Integer literals → Literal.ofInteger; word with posting → IID ref. */
        private static BindingTarget tokenToBindingTarget(TokenSpan token) {
            if (token.kind() == TokenLattice.Kind.LITERAL) {
                try {
                    return Literal.ofInteger(Long.parseLong(token.surfaceText().trim()));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            if (!token.postings().isEmpty()) {
                return BindingTarget.iid(token.postings().get(0).target());
            }
            return null;
        }
    }
}
