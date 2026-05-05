package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.id.ItemID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FrameTarget — inline nested frames (Tag 23)")
class FrameOldTargetTest {

    static final ItemID ADD = ItemID.fromString("cg:op/add");
    static final ItemID MUL = ItemID.fromString("cg:op/mul");
    static final ItemID THEME = ItemID.fromString("cg.role:theme");
    static final ItemID INSTRUMENT = ItemID.fromString("cg.role:instrument");
    static final ItemID ITEM_A = ItemID.fromString("cg:test/a");
    static final ItemID ITEM_B = ItemID.fromString("cg:test/b");

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("wraps a FrameBody")
        void wrapsBody() {
            FrameBodyOld inner = new FrameBodyOld(ADD, ITEM_A);
            BindingTarget.FrameTarget target = new BindingTarget.FrameTarget(inner);
            assertThat(target.body()).isSameAs(inner);
            assertThat(target.body().predicate()).isEqualTo(ADD);
        }

        @Test
        @DisplayName("convenience factory on BindingTarget")
        void convenienceFactory() {
            FrameBodyOld inner = new FrameBodyOld(ADD, ITEM_A);
            BindingTarget target = BindingTarget.frame(inner);
            assertThat(target).isInstanceOf(BindingTarget.FrameTarget.class);
            assertThat(((BindingTarget.FrameTarget) target).body()).isEqualTo(inner);
        }

        @Test
        @DisplayName("Binding.frame() factory")
        void bindingFrameFactory() {
            FrameBodyOld inner = new FrameBodyOld(ADD, ITEM_A);
            Binding b = Binding.frame(THEME, inner);
            assertThat(b.target()).isInstanceOf(BindingTarget.FrameTarget.class);
            assertThat(b.role()).isEqualTo(THEME);
            assertThat(b.identity()).isTrue();
        }
    }

    @Nested
    @DisplayName("CBOR Round-trip")
    class CborRoundTrip {

        @Test
        @DisplayName("simple FrameTarget round-trips")
        void simpleRoundTrip() {
            FrameBodyOld inner = new FrameBodyOld(ADD, ITEM_A);
            BindingTarget.FrameTarget original = new BindingTarget.FrameTarget(inner);

            var cbor = original.toCborTree(Canonical.Scope.RECORD);
            assertThat(cbor.isTagged()).isTrue();
            assertThat(cbor.getMostInnerTag().ToInt32Checked()).isEqualTo(Canonical.CgTag.FRAME);

            BindingTarget decoded = BindingTarget.fromCborTree(cbor);
            assertThat(decoded).isInstanceOf(BindingTarget.FrameTarget.class);
            BindingTarget.FrameTarget decodedFrame = (BindingTarget.FrameTarget) decoded;
            assertThat(decodedFrame.body().predicate()).isEqualTo(ADD);
            assertThat(decodedFrame.body().homeId()).isEqualTo(ITEM_A);
        }

        @Test
        @DisplayName("nested FrameTarget round-trips (expression tree)")
        void nestedRoundTrip() {
            // ADD { THEME→a, INSTRUMENT→b }
            FrameBodyOld addBody = new FrameBodyOld(ADD, List.of(
                    new Binding(THEME, BindingTarget.iid(ITEM_A)),
                    new Binding(INSTRUMENT, BindingTarget.iid(ITEM_B))
            ));

            // MUL { THEME → ADD{...}, INSTRUMENT → b }
            FrameBodyOld mulBody = new FrameBodyOld(MUL, List.of(
                    Binding.frame(THEME, addBody),
                    new Binding(INSTRUMENT, BindingTarget.iid(ITEM_B))
            ));

            byte[] bytes = mulBody.encodeBinary(Canonical.Scope.RECORD);
            FrameBodyOld decoded = Canonical.decodeBinary(bytes, FrameBodyOld.class, Canonical.Scope.RECORD);

            assertThat(decoded.predicate()).isEqualTo(MUL);
            assertThat(decoded.frameBindings()).hasSize(2);

            // First binding should be the nested frame
            Binding themeBind = decoded.getBinding(THEME);
            assertThat(themeBind).isNotNull();
            assertThat(themeBind.target()).isInstanceOf(BindingTarget.FrameTarget.class);

            FrameBodyOld nestedAdd = ((BindingTarget.FrameTarget) themeBind.target()).body();
            assertThat(nestedAdd.predicate()).isEqualTo(ADD);
            assertThat(nestedAdd.frameBindings()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("equal FrameTargets are equal")
        void equalTargets() {
            FrameBodyOld body1 = new FrameBodyOld(ADD, ITEM_A);
            FrameBodyOld body2 = new FrameBodyOld(ADD, ITEM_A);
            var t1 = new BindingTarget.FrameTarget(body1);
            var t2 = new BindingTarget.FrameTarget(body2);
            assertThat(t1).isEqualTo(t2);
            assertThat(t1.hashCode()).isEqualTo(t2.hashCode());
        }

        @Test
        @DisplayName("different FrameTargets are not equal")
        void differentTargets() {
            var t1 = new BindingTarget.FrameTarget(new FrameBodyOld(ADD, ITEM_A));
            var t2 = new BindingTarget.FrameTarget(new FrameBodyOld(MUL, ITEM_A));
            assertThat(t1).isNotEqualTo(t2);
        }
    }

    @Nested
    @DisplayName("Display")
    class Display {

        @Test
        @DisplayName("toString contains Frame prefix")
        void toStringFormat() {
            FrameBodyOld inner = new FrameBodyOld(ADD, ITEM_A);
            var target = new BindingTarget.FrameTarget(inner);
            assertThat(target.toString()).startsWith("Frame(");
        }
    }
}
