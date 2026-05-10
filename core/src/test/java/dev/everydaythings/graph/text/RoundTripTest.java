package dev.everydaythings.graph.text;

import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.Body;
import dev.everydaythings.graph.frame.Frame;
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
import dev.everydaythings.graph.value.Decimal;
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
        language = new Language(Language.IID, lib);
        orchestrator = new Item(ItemID.fromString("test.roundtrip-orchestrator"), lib);
    }

    @Test
    @DisplayName("round-trip 'Add{5,3}' ↔ '5 + 3'")
    void simpleBinary() {
        FrameMap original = binary(Add.IID, Literal.ofInteger(5), Literal.ofInteger(3));
        roundTrip(original);
    }

    @Test
    @DisplayName("round-trip 'Add{5, Multiply{3,2}}' ↔ '5 + 3 * 2' (precedence-implicit nesting)")
    void mixedPrecedenceImplicit() {
        ContentID multCid = persistBinary(Multiply.IID, 3, 2);
        FrameMap original = binary(Add.IID, Literal.ofInteger(5), BindingTarget.ref(multCid));
        roundTrip(original);
    }

    @Test
    @DisplayName("round-trip 'Multiply{Add{5,3}, 2}' ↔ '(5 + 3) * 2' (parens needed)")
    void parensNeeded() {
        ContentID addCid = persistBinary(Add.IID, 5, 3);
        FrameMap original = binary(Multiply.IID, BindingTarget.ref(addCid), Literal.ofInteger(2));
        roundTrip(original);
    }

    @Test
    @DisplayName("round-trip 'Power{2, Power{3,4}}' ↔ '2 ^ 3 ^ 4' (right-assoc)")
    void rightAssocChain() {
        ContentID innerPower = persistBinary(Power.IID, 3, 4);
        FrameMap original = binary(Power.IID, Literal.ofInteger(2), BindingTarget.ref(innerPower));
        roundTrip(original);
    }

    @Test
    @DisplayName("round-trip 'Subtract{Subtract{5,3}, 2}' ↔ '5 - 3 - 2' (left-assoc)")
    void leftAssocChain() {
        ContentID innerSub = persistBinary(Subtract.IID, 5, 3);
        FrameMap original = binary(Subtract.IID, BindingTarget.ref(innerSub), Literal.ofInteger(2));
        roundTrip(original);
    }

    @Test
    @DisplayName("round-trip 'Negate{5}' ↔ '-5'")
    void unaryPrefix() {
        FrameMap original = unary(Negate.IID, Literal.ofInteger(5));
        roundTrip(original);
    }

    @Test
    @DisplayName("round-trip 'Add{Negate{5}, 3}' ↔ '-5 + 3' (unary as binary operand)")
    void unaryAsBinaryOperand() {
        ContentID negCid = persistUnary(Negate.IID, Literal.ofInteger(5));
        FrameMap original = binary(Add.IID, BindingTarget.ref(negCid), Literal.ofInteger(3));
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
    private static FrameMap binary(ItemID predicate, BindingTarget left, BindingTarget right) {
        return new FrameMap(
                null,
                new Part<>(ItemRef.of(predicate), Decimal.parse("1.0"), List.of()),
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

    /** Build a unary FrameMap (predicate + THEME binding only). */
    private static FrameMap unary(ItemID predicate, BindingTarget operand) {
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

    /** Persist a binary frame body and return its CID — for using as a sub-frame target. */
    private ContentID persistBinary(ItemID predicate, long left, long right) {
        Body body = Body.of(
                ItemRef.of(predicate),
                List.of(
                        new Binding(ThematicRole.Theme.IID, Literal.ofInteger(left)),
                        new Binding(ThematicRole.Goal.IID, Literal.ofInteger(right))));
        return lib.persist(body);
    }

    /** Persist a unary frame body and return its CID. */
    private ContentID persistUnary(ItemID predicate, BindingTarget operand) {
        Body body = Body.of(
                ItemRef.of(predicate),
                List.of(new Binding(ThematicRole.Theme.IID, operand)));
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
        boolean first = true;
        for (BindingMap b : fm.bindings()) {
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
        boolean first = true;
        for (Binding b : body.bindings()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(b.role().compactDisplay()).append("=");
            sb.append(describeTarget(b.target()));
        }
        return sb.append("}").toString();
    }

    private String describeTarget(BindingTarget target) {
        if (target instanceof Literal lit) {
            if (Literal.TYPE_INTEGER.equals(lit.valueType())) return Long.toString(lit.asInteger());
            if (Literal.TYPE_TEXT.equals(lit.valueType())) return "\"" + lit.asText() + "\"";
            return lit.toString();
        }
        if (target instanceof FrameMapTarget fmt) {
            return describe(fmt.frameMap());
        }
        if (target instanceof BindingTarget.RefTarget rt) {
            Optional<Frame> frame = lib.fetchFrame(rt.asCid());
            return frame.map(f -> describeBody(f.body()))
                    .orElseGet(() -> "ref(" + rt.asCid().compactDisplay() + ")");
        }
        if (target instanceof BindingTarget.IidTarget iid) {
            return "iid(" + iid.iid().compactDisplay() + ")";
        }
        return target == null ? "<null>" : target.toString();
    }
}
