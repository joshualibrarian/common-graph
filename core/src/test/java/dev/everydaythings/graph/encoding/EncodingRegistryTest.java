package dev.everydaythings.graph.encoding;

import dev.everydaythings.graph.canonical.Node;
import dev.everydaythings.graph.ref.ItemRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EncodingRegistry — codec lookup by IID")
class EncodingRegistryTest {

    @Nested
    @DisplayName("Default registry")
    class DefaultRegistry {

        @Test
        @DisplayName("Contains CG-CBOR-v1")
        void containsCgCbor() {
            EncodingRegistry r = EncodingRegistry.defaultRegistry();
            Optional<Encoding> cgcbor = r.get(ItemRef.iid(Encoding.CgCborV1.KEY));
            assertThat(cgcbor).isPresent();
            assertThat(cgcbor.get()).isInstanceOf(CgCbor.class);
        }

        @Test
        @DisplayName("Reports its size")
        void reportsSize() {
            EncodingRegistry r = EncodingRegistry.defaultRegistry();
            assertThat(r.size()).isEqualTo(1);
            assertThat(r.known()).contains(ItemRef.iid(Encoding.CgCborV1.KEY));
        }
    }

    @Nested
    @DisplayName("Register / lookup")
    class RegisterLookup {

        @Test
        @DisplayName("Empty registry returns empty for any IID")
        void emptyReturnsEmpty() {
            EncodingRegistry r = new EncodingRegistry();
            assertThat(r.get(ItemRef.iid(Encoding.CgCborV1.KEY))).isEmpty();
            assertThat(r.size()).isZero();
        }

        @Test
        @DisplayName("Registered codec is retrievable by its IID")
        void registeredIsRetrievable() {
            EncodingRegistry r = new EncodingRegistry();
            r.register(CgCbor.codec());
            assertThat(r.get(ItemRef.iid(Encoding.CgCborV1.KEY))).contains(CgCbor.codec());
        }

        @Test
        @DisplayName("Unknown IID returns empty")
        void unknownReturnsEmpty() {
            EncodingRegistry r = EncodingRegistry.defaultRegistry();
            assertThat(r.get(ItemRef.iid("cg.encoding:not-a-real-codec"))).isEmpty();
        }

        @Test
        @DisplayName("Null IID returns empty (defensive)")
        void nullReturnsEmpty() {
            EncodingRegistry r = EncodingRegistry.defaultRegistry();
            assertThat(r.get(null)).isEmpty();
        }

        @Test
        @DisplayName("Re-registering the same IID replaces the previous instance")
        void rereqisterReplaces() {
            EncodingRegistry r = new EncodingRegistry();
            Encoding first  = stubCodec(ItemRef.iid(Encoding.CgCborV1.KEY));
            Encoding second = stubCodec(ItemRef.iid(Encoding.CgCborV1.KEY));
            r.register(first);
            r.register(second);
            assertThat(r.get(ItemRef.iid(Encoding.CgCborV1.KEY))).contains(second);
            assertThat(r.size()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("register rejects null")
        void registerRejectsNull() {
            EncodingRegistry r = new EncodingRegistry();
            assertThatNullPointerException().isThrownBy(() -> r.register(null));
        }

    }

    // ==================================================================================
    // Test helpers
    // ==================================================================================

    private static Encoding stubCodec(ItemRef iid) {
        return new Encoding() {
            @Override public ItemRef encoding() { return iid; }
            @Override public byte formatCode() { return 0x00; }
            @Override public byte[] encode(Object value)        { return new byte[0]; }
            @Override public Object decode(byte[] bytes)        { return null; }
            @Override public String encodeText(Object value)    { return ""; }
            @Override public Object decodeText(String text)     { return null; }
            @Override public Node walk(Object value)            { return null; }
            @Override public Node walk(byte[] bytes)            { return null; }
            @Override public String prettyPrint(Object value)   { return ""; }
            @Override public boolean isValid(byte[] bytes)      { return true; }
            @Override public Optional<Object> decodeOne(InputStream in) { return Optional.empty(); }
        };
    }
}
