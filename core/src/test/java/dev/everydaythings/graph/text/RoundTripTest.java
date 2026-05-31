package dev.everydaythings.graph.text;


import dev.everydaythings.graph.canonical.HashTree;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.ref.DatumRef;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.operator.OperatorNotation;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.ThematicRole;
import dev.everydaythings.graph.text.FrameMap.BindingMap;
import dev.everydaythings.graph.text.FrameMap.Part;
import java.math.BigDecimal;
import dev.everydaythings.graph.operator.math.Add;
import dev.everydaythings.graph.operator.math.Multiply;
import dev.everydaythings.graph.operator.math.Negate;
import dev.everydaythings.graph.operator.math.Power;
import dev.everydaythings.graph.operator.math.Subtract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip tests: build a FrameMap, render it to text, parse the text, and
 * verify the parsed result is structurally equivalent to the original.
 *
 * <p>"Structurally equivalent" means same predicate IIDs, same set of bindings
 * (by role), same target values — ignoring confidence and span metadata, and
 * normalizing across the {@link BindingTarget.RefTarget} (CID-addressed
 * sub-frame stored in the librarian) vs {@link FrameMapTarget} (in-flight
 * sub-FrameMap from parse) representations.
 *
 * <p>These tests validate the bidirectional pipeline: what render emits, parse
 * accepts; what parse produces, render reconstructs.
 */
class RoundTripTest {

    private Librarian lib;
    private Language language;
    private Item orchestrator;

    @BeforeEach
    void setUp() {
        lib = Librarian.inMemory();
        lib.bootstrap();
        language = new OperatorNotation(lib);
        orchestrator = new Item(ItemRef.fromString("test.roundtrip-orchestrator"), lib);
    }

    @Test
    @DisplayName("round-trip 'Add{5,3}' ↔ '5 + 3'")
    void simpleBinary() {
        FrameMap original = binary(ItemRef.iid(Add.KEY), (long) (5), (long) (3));
        roundTrip(original);
    }

    @Test
    @DisplayName("round-trip 'Add{5, Multiply{3,2}}' ↔ '5 + 3 * 2' (precedence-implicit nesting)")
    void mixedPrecedenceImplicit() {
        DatumRef multCid = persistBinary(ItemRef.iid(Multiply.KEY), 3, 2);
        FrameMap original = binary(ItemRef.iid(Add.KEY), (long) (5), multCid);
        roundTrip(original);
    }

    @Test
    @DisplayName("round-trip 'Multiply{Add{5,3}, 2}' ↔ '(5 + 3) * 2' (parens needed)")
    void parensNeeded() {
        DatumRef addCid = persistBinary(ItemRef.iid(Add.KEY), 5, 3);
        FrameMap original = binary(ItemRef.iid(Multiply.KEY), addCid, (long) (2));
        roundTrip(original);
    }

    @Test
    @DisplayName("round-trip 'Power{2, Power{3,4}}' ↔ '2 ^ 3 ^ 4' (right-assoc)")
    void rightAssocChain() {
        DatumRef innerPower = persistBinary(ItemRef.iid(Power.KEY), 3, 4);
        FrameMap original = binary(ItemRef.iid(Power.KEY), (long) (2), innerPower);
        roundTrip(original);
    }

    @Test
    @DisplayName("round-trip 'Subtract{Subtract{5,3}, 2}' ↔ '5 - 3 - 2' (left-assoc)")
    void leftAssocChain() {
        DatumRef innerSub = persistBinary(ItemRef.iid(Subtract.KEY), 5, 3);
        FrameMap original = binary(ItemRef.iid(Subtract.KEY), innerSub, (long) (2));
        roundTrip(original);
    }

    @Test
    @DisplayName("round-trip 'Negate{5}' ↔ '-5'")
    void unaryPrefix() {
        FrameMap original = unary(ItemRef.iid(Negate.KEY), (long) (5));
        roundTrip(original);
    }

    @Test
    @DisplayName("round-trip 'Add{Negate{5}, 3}' ↔ '-5 + 3' (unary as binary operand)")
    void unaryAsBinaryOperand() {
        DatumRef negCid = persistUnary(ItemRef.iid(Negate.KEY), (long) (5));
        FrameMap original = binary(ItemRef.iid(Add.KEY), negCid, (long) (3));
        roundTrip(original);
    }

    /**
     * Render the frame to text, parse the text back, and assert structural
     * equivalence between the original and the parsed result.
     */
    private void roundTrip(FrameMap original) {
        FrameMap rendered = language.render(original, ParseParams.defaults());
        assertThat(rendered.text()).as("render produced text").isNotNull();
        FrameMap parsed = orchestrator.parse(rendered.text(), ParseParams.defaults());
        assertThat(describe(parsed)).as("round-trip via '%s'", rendered.text())
                .isEqualTo(describe(original));
    }

    // ==================================================================================
    // FrameMap construction helpers
    // ==================================================================================

    /** Build a binary FrameMap (predicate + THEME + GOAL bindings). */
    private static FrameMap binary(ItemRef predicate, Object left, Object right) {
        return new FrameMap(
                null,
                new Part<>(ItemRef.of(predicate), new BigDecimal("1.0"), List.of()),
                List.of(
                        new BindingMap(
                                new Part<>(ItemRef.of(ItemRef.iid(ThematicRole.Theme.KEY)), new BigDecimal("1.0"), List.of()),
                                List.of(),
                                new Part<>(left, new BigDecimal("1.0"), List.of())),
                        new BindingMap(
                                new Part<>(ItemRef.of(ItemRef.iid(ThematicRole.Goal.KEY)), new BigDecimal("1.0"), List.of()),
                                List.of(),
                                new Part<>(right, new BigDecimal("1.0"), List.of()))),
                List.of());
    }

    /** Build a unary FrameMap (predicate + THEME binding only). */
    private static FrameMap unary(ItemRef predicate, Object operand) {
        return new FrameMap(
                null,
                new Part<>(ItemRef.of(predicate), new BigDecimal("1.0"), List.of()),
                List.of(
                        new BindingMap(
                                new Part<>(ItemRef.of(ItemRef.iid(ThematicRole.Theme.KEY)), new BigDecimal("1.0"), List.of()),
                                List.of(),
                                new Part<>(operand, new BigDecimal("1.0"), List.of()))),
                List.of());
    }

    /** Persist a binary frame body and return its CID — for using as a sub-frame target. */
    private DatumRef persistBinary(ItemRef predicate, long left, long right) {
        Body body = Body.of(
                ItemRef.of(predicate),
                List.of(
                        new Binding(ItemRef.iid(ThematicRole.Theme.KEY), (long) (left)),
                        new Binding(ItemRef.iid(ThematicRole.Goal.KEY), (long) (right))));
        return lib.persist(body);
    }

    /** Persist a unary frame body and return its CID. */
    private DatumRef persistUnary(ItemRef predicate, Object operand) {
        Body body = Body.of(
                ItemRef.of(predicate),
                List.of(new Binding(ItemRef.iid(ThematicRole.Theme.KEY), operand)));
        return lib.persist(body);
    }

    // ==================================================================================
    // Structural-equivalence describe()
    // ==================================================================================
    //
    // Same canonical form for equivalent structures, regardless of whether
    // sub-frames live in RefTargets (persisted bodies fetched via librarian) or
    // FrameMapTargets (in-flight from parse). Spans and confidences are ignored.

    private String describe(FrameMap fm) {
        if (fm == null || fm.predicate() == null) return "<empty>";
        StringBuilder sb = new StringBuilder();
        sb.append(fm.predicate().value().iid().compactDisplay()).append("{");
        // Canonical describe order: sort bindings by their role's structural
        // hash so a FrameMap (caller-ordered) and a Body (canonical-ordered)
        // describe identically when they hold the same content.
        List<BindingMap> sorted = new java.util.ArrayList<>(fm.bindings());
        sorted.sort(java.util.Comparator.comparing(
                b -> b.role().value().iid(),
                HashTree.CANONICAL));
        boolean first = true;
        for (BindingMap b : sorted) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(b.role().value().iid().compactDisplay()).append("=");
            sb.append(describeTarget(b.target().value()));
        }
        return sb.append("}").toString();
    }

    private String describeBody(Body body) {
        if (body == null || !(body.head() instanceof ItemRef ref)) return "<empty>";
        StringBuilder sb = new StringBuilder();
        sb.append(ref.iid().compactDisplay()).append("{");
        // Sort bindings by role-IID hash for stable describe output that matches
        // the FrameMap path above. Body's own canonical sort is by whole-binding
        // hash (different criterion); we re-sort here for test-comparison
        // consistency.
        List<Binding> sorted = new java.util.ArrayList<>(body.bindings());
        sorted.sort(java.util.Comparator.comparing(
                Binding::role,
                HashTree.CANONICAL));
        boolean first = true;
        for (Binding b : sorted) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(b.role().compactDisplay()).append("=");
            sb.append(describeTarget(b.target()));
        }
        return sb.append("}").toString();
    }

    private String describeTarget(Object target) {
        if (target instanceof Long n) return Long.toString(n);
        if (target instanceof String s) return "\"" + s + "\"";
        if (target instanceof FrameMapTarget fmt) {
            return describe(fmt.frameMap());
        }
        if (target instanceof DatumRef dr) {
            Optional<Frame> frame = lib.fetchFrame(dr);
            return frame.map(f -> describeBody(f.body()))
                    .orElseGet(() -> "ref(" + dr.compactDisplay() + ")");
        }
        if (target instanceof ItemRef ir && !ir.isPinned()) {
            return "ItemRef.iid(" + ir.compactDisplay() + ")";
        }
        return target == null ? "<null>" : target.toString();
    }
}
