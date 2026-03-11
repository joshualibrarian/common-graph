package dev.everydaythings.graph.item.id;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Canonical;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FrameKeyTest {

    static final ItemID TITLE = ItemID.fromString("cg:pred/title");
    static final ItemID GLOSS = ItemID.fromString("cg:pred/gloss");
    static final ItemID ENG = ItemID.fromString("cg:language/eng");
    static final ItemID CHAT = ItemID.fromString("cg:pred/chat");
    static final ItemID CONTENT = ItemID.fromString("cg:pred/content");

    @Nested
    @DisplayName("Factory methods")
    class Factories {

        @Test
        @DisplayName("single sememe key")
        void singleSememe() {
            FrameKey key = FrameKey.of(TITLE);

            assertThat(key.size()).isEqualTo(1);
            assertThat(key.isSemantic()).isTrue();
            assertThat(key.isLiteral()).isFalse();
            assertThat(key.headSememe()).isEqualTo(TITLE);
            assertThat(key.qualifiers()).isEmpty();
        }

        @Test
        @DisplayName("compound sememe key")
        void compoundSememe() {
            FrameKey key = FrameKey.of(GLOSS, ENG);

            assertThat(key.size()).isEqualTo(2);
            assertThat(key.isSemantic()).isTrue();
            assertThat(key.headSememe()).isEqualTo(GLOSS);
            assertThat(key.qualifiers()).hasSize(1);
        }

        @Test
        @DisplayName("single literal key")
        void singleLiteral() {
            FrameKey key = FrameKey.literal("vault");

            assertThat(key.size()).isEqualTo(1);
            assertThat(key.isLiteral()).isTrue();
            assertThat(key.isSemantic()).isFalse();
            assertThat(key.literalValue()).isEqualTo("vault");
            assertThat(key.headSememe()).isNull();
        }

        @Test
        @DisplayName("mixed key — sememe head with literal qualifier")
        void mixedKey() {
            FrameKey key = FrameKey.mixed(CHAT, "tavern");

            assertThat(key.size()).isEqualTo(2);
            assertThat(key.isSemantic()).isFalse();
            assertThat(key.isLiteral()).isFalse();
            assertThat(key.headSememe()).isEqualTo(CHAT);
            assertThat(key.qualifiers()).hasSize(1);
            assertThat(key.qualifiers().getFirst()).isInstanceOf(FrameKey.Literal.class);
        }

        @Test
        @DisplayName("fromHandle produces literal key")
        void fromHandle() {
            FrameKey key = FrameKey.fromHandle("vault");

            assertThat(key.isLiteral()).isTrue();
            assertThat(key.literalValue()).isEqualTo("vault");
        }

        @Test
        @DisplayName("rejects empty token list")
        void rejectsEmpty() {
            assertThatThrownBy(() -> FrameKey.of(new ItemID[0]))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects blank literal")
        void rejectsBlankLiteral() {
            assertThatThrownBy(() -> FrameKey.literal("  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("HandleID compatibility")
    class HandleCompat {

        @Test
        @DisplayName("literal key produces same HandleID as HandleID.of()")
        void literalHandleMatchesOriginal() {
            FrameKey key = FrameKey.literal("vault");
            HandleID fromKey = key.toHandleID();
            HandleID direct = HandleID.of("vault");

            assertThat(fromKey).isEqualTo(direct);
        }

        @Test
        @DisplayName("toHandleString returns literal value for literal keys")
        void handleStringForLiteral() {
            assertThat(FrameKey.literal("vault").toHandleString()).isEqualTo("vault");
        }

        @Test
        @DisplayName("toHandleString is deterministic for semantic keys")
        void handleStringDeterministic() {
            FrameKey key1 = FrameKey.of(GLOSS, ENG);
            FrameKey key2 = FrameKey.of(GLOSS, ENG);

            assertThat(key1.toHandleString()).isEqualTo(key2.toHandleString());
            assertThat(key1.toHandleID()).isEqualTo(key2.toHandleID());
        }
    }

    @Nested
    @DisplayName("Display")
    class Display {

        @Test
        @DisplayName("single sememe displays in parens")
        void singleSememeDisplay() {
            FrameKey key = FrameKey.of(TITLE);
            assertThat(key.displayText()).startsWith("(");
            assertThat(key.displayText()).endsWith(")");
            // Sememe display text is derived from ItemID — not the original string
            assertThat(key.displayText()).isNotBlank();
        }

        @Test
        @DisplayName("literal key displays quoted")
        void literalDisplay() {
            FrameKey key = FrameKey.literal("x");
            assertThat(key.displayText()).isEqualTo("(\"x\")");
        }

        @Test
        @DisplayName("compound key shows comma-separated tokens")
        void compoundDisplay() {
            FrameKey key = FrameKey.of(GLOSS, ENG);
            String display = key.displayText();
            assertThat(display).startsWith("(");
            assertThat(display).endsWith(")");
            assertThat(display).contains(", ");
        }

        @Test
        @DisplayName("mixed key shows literal quoted")
        void mixedDisplay() {
            FrameKey key = FrameKey.mixed(CHAT, "tavern");
            String display = key.displayText();
            assertThat(display).contains("\"tavern\"");
        }
    }

    @Nested
    @DisplayName("CBOR round-trip")
    class CborRoundTrip {

        @Test
        @DisplayName("single sememe key survives encode/decode")
        void singleSememe() {
            FrameKey original = FrameKey.of(TITLE);
            CBORObject cbor = original.toCborTree(Canonical.Scope.BODY);
            FrameKey decoded = FrameKey.fromCborTree(cbor);

            assertThat(decoded).isEqualTo(original);
            assertThat(decoded.headSememe()).isEqualTo(TITLE);
        }

        @Test
        @DisplayName("compound sememe key survives encode/decode")
        void compoundSememe() {
            FrameKey original = FrameKey.of(GLOSS, ENG);
            CBORObject cbor = original.toCborTree(Canonical.Scope.BODY);
            FrameKey decoded = FrameKey.fromCborTree(cbor);

            assertThat(decoded).isEqualTo(original);
            assertThat(decoded.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("literal key survives encode/decode")
        void literal() {
            FrameKey original = FrameKey.literal("vault");
            CBORObject cbor = original.toCborTree(Canonical.Scope.BODY);
            FrameKey decoded = FrameKey.fromCborTree(cbor);

            assertThat(decoded).isEqualTo(original);
            assertThat(decoded.literalValue()).isEqualTo("vault");
        }

        @Test
        @DisplayName("mixed key survives encode/decode")
        void mixed() {
            FrameKey original = FrameKey.mixed(CHAT, "tavern");
            CBORObject cbor = original.toCborTree(Canonical.Scope.BODY);
            FrameKey decoded = FrameKey.fromCborTree(cbor);

            assertThat(decoded).isEqualTo(original);
        }

        @Test
        @DisplayName("binary round-trip via encodeBinary/decodeBinary")
        void binaryRoundTrip() {
            FrameKey original = FrameKey.of(GLOSS, ENG);
            byte[] bytes = original.encodeBinary(Canonical.Scope.BODY);
            FrameKey decoded = FrameKey.fromCborTree(CBORObject.DecodeFromBytes(bytes));

            assertThat(decoded).isEqualTo(original);
        }
    }

    @Nested
    @DisplayName("Equality and comparison")
    class EqualityAndComparison {

        @Test
        @DisplayName("same tokens produce equal keys")
        void equalKeys() {
            FrameKey a = FrameKey.of(GLOSS, ENG);
            FrameKey b = FrameKey.of(GLOSS, ENG);

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("different tokens produce unequal keys")
        void unequalKeys() {
            FrameKey a = FrameKey.of(TITLE);
            FrameKey b = FrameKey.of(CONTENT);

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("sememe key not equal to literal key")
        void sememeNotEqualToLiteral() {
            FrameKey sememe = FrameKey.of(TITLE);
            FrameKey literal = FrameKey.literal("title");

            assertThat(sememe).isNotEqualTo(literal);
        }

        @Test
        @DisplayName("comparison is consistent")
        void comparisonConsistent() {
            FrameKey a = FrameKey.of(TITLE);
            FrameKey b = FrameKey.of(GLOSS, ENG);

            int cmp = a.compareTo(b);
            assertThat(b.compareTo(a)).isEqualTo(-cmp);
        }

        @Test
        @DisplayName("shorter key sorts before longer with same prefix")
        void shorterSortsFirst() {
            FrameKey shorter = FrameKey.of(GLOSS);
            FrameKey longer = FrameKey.of(GLOSS, ENG);

            assertThat(shorter.compareTo(longer)).isLessThan(0);
        }
    }

    @Nested
    @DisplayName("FrameEntry integration")
    class EntryIntegration {

        @Test
        @DisplayName("entry with explicit frameKey returns it")
        void explicitFrameKey() {
            FrameKey key = FrameKey.of(TITLE);
            dev.everydaythings.graph.item.component.FrameEntry entry =
                    dev.everydaythings.graph.item.component.FrameEntry.builder()
                            .handle(HandleID.of("title"))
                            .type(ItemID.fromString("cg:type/text"))
                            .frameKey(key)
                            .identity(true)
                            .build();

            assertThat(entry.frameKey()).isEqualTo(key);
        }

        @Test
        @DisplayName("entry with aliasRef derives semantic frameKey")
        void derivedFromAliasRef() {
            dev.everydaythings.graph.item.component.FrameEntry entry =
                    dev.everydaythings.graph.item.component.FrameEntry.builder()
                            .handle(HandleID.of("title"))
                            .aliasRef(TITLE)
                            .type(ItemID.fromString("cg:type/text"))
                            .identity(true)
                            .build();

            FrameKey derived = entry.frameKey();
            assertThat(derived.isSemantic()).isTrue();
            assertThat(derived.headSememe()).isEqualTo(TITLE);
        }

        @Test
        @DisplayName("entry with alias derives literal frameKey")
        void derivedFromAlias() {
            dev.everydaythings.graph.item.component.FrameEntry entry =
                    dev.everydaythings.graph.item.component.FrameEntry.builder()
                            .handle(HandleID.of("vault"))
                            .alias("vault")
                            .type(ItemID.fromString("cg:type/vault"))
                            .identity(true)
                            .build();

            FrameKey derived = entry.frameKey();
            assertThat(derived.isLiteral()).isTrue();
            assertThat(derived.literalValue()).isEqualTo("vault");
        }
    }
}
