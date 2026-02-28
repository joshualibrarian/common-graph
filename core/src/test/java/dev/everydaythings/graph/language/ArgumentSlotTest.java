package dev.everydaythings.graph.language;

import com.upokecenter.cbor.CBOREncodeOptions;
import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.id.ItemID;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ArgumentSlot} CBOR round-trip persistence.
 */
class ArgumentSlotTest {

    @Test
    void roundTrip_optionalWithoutTypeConstraint() {
        var slot = ArgumentSlot.optional(ThematicRole.THEME, "what to create");

        byte[] bytes = slot.encodeBinary(Canonical.Scope.RECORD);
        var decoded = Canonical.decodeBinary(bytes, ArgumentSlot.class, Canonical.Scope.RECORD);

        assertThat(decoded.role()).isEqualTo(ThematicRole.THEME);
        assertThat(decoded.required()).isFalse();
        assertThat(decoded.typeConstraint()).isNull();
        assertThat(decoded.descriptions()).containsEntry("en", "what to create");
    }

    @Test
    void roundTrip_requiredWithTypeConstraint() {
        ItemID typeId = ItemID.fromString("cg:type/chess");
        var slot = ArgumentSlot.required(ThematicRole.PATIENT, typeId, "the piece to capture");

        byte[] bytes = slot.encodeBinary(Canonical.Scope.RECORD);
        var decoded = Canonical.decodeBinary(bytes, ArgumentSlot.class, Canonical.Scope.RECORD);

        assertThat(decoded.role()).isEqualTo(ThematicRole.PATIENT);
        assertThat(decoded.required()).isTrue();
        assertThat(decoded.typeConstraint()).isEqualTo(typeId);
        assertThat(decoded.descriptions()).containsEntry("en", "the piece to capture");
    }

    @Test
    void roundTrip_listOfSlots() {
        var slots = List.of(
                ArgumentSlot.required(ThematicRole.THEME, "what"),
                ArgumentSlot.optional(ThematicRole.TARGET, "where")
        );

        // Encode as CBOR array (same path as @ContentField List encoding)
        CBORObject cbor = Canonical.encodeValue(slots, Canonical.Scope.RECORD);
        byte[] bytes = cbor.EncodeToBytes(CBOREncodeOptions.DefaultCtap2Canonical);

        // Decode back — simulate the decoding path used by bindFieldsFromTable
        CBORObject decoded = CBORObject.DecodeFromBytes(bytes);
        assertThat(decoded.size()).isEqualTo(2);

        // Decode each element as ArgumentSlot
        List<ArgumentSlot> result = new ArrayList<>();
        for (int i = 0; i < decoded.size(); i++) {
            result.add(Canonical.decodeBinary(
                    decoded.get(i).EncodeToBytes(CBOREncodeOptions.DefaultCtap2Canonical),
                    ArgumentSlot.class, Canonical.Scope.RECORD));
        }

        assertThat(result).hasSize(2);
        assertThat(result.get(0).role()).isEqualTo(ThematicRole.THEME);
        assertThat(result.get(0).required()).isTrue();
        assertThat(result.get(1).role()).isEqualTo(ThematicRole.TARGET);
        assertThat(result.get(1).required()).isFalse();
    }

    @Test
    void roundTrip_emptyList() {
        List<ArgumentSlot> slots = List.of();

        CBORObject cbor = Canonical.encodeValue(slots, Canonical.Scope.RECORD);
        byte[] bytes = cbor.EncodeToBytes(CBOREncodeOptions.DefaultCtap2Canonical);

        CBORObject decoded = CBORObject.DecodeFromBytes(bytes);
        assertThat(decoded.size()).isEqualTo(0);
    }

    @Test
    void equals_sameValues() {
        var a = ArgumentSlot.optional(ThematicRole.THEME, "what");
        var b = ArgumentSlot.optional(ThematicRole.THEME, "what");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void equals_differentRole() {
        var a = ArgumentSlot.optional(ThematicRole.THEME, "what");
        var b = ArgumentSlot.optional(ThematicRole.TARGET, "what");
        assertThat(a).isNotEqualTo(b);
    }
}
