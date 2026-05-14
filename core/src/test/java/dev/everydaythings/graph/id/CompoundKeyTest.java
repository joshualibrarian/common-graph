package dev.everydaythings.graph.id;

import dev.everydaythings.graph.canonical.Scope;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.canonical.Canonical;
import dev.everydaythings.graph.id.CompoundKey;
import dev.everydaythings.graph.id.ItemID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompoundKeyTest {

    static final ItemID TITLE = ItemID.fromString("cg:pred/title");
    static final ItemID GLOSS = ItemID.fromString("cg:pred/gloss");
    static final ItemID ENG = ItemID.fromString("cg:language/eng");
    static final ItemID CONTENT = ItemID.fromString("cg:pred/content");
    static final ItemID EXPRESSION = ItemID.fromString("cg:pred/expression");

    @Nested
    @DisplayName("Factory methods")
    class Factories {

        @Test
        @DisplayName("head only, no qualifiers")
        void headOnly() {
            CompoundKey key = CompoundKey.of(TITLE);

            assertThat(key.size()).isEqualTo(1);
            assertThat(key.head()).isEqualTo(TITLE);
            assertThat(key.headSememe()).isEqualTo(TITLE);
            assertThat(key.qualifiers()).isEmpty();
            assertThat(key.isSemantic()).isTrue();
        }

        @Test
        @DisplayName("head with sememe qualifier")
        void headWithSememeQualifier() {
            CompoundKey key = CompoundKey.of(GLOSS, ENG);

            assertThat(key.size()).isEqualTo(2);
            assertThat(key.head()).isEqualTo(GLOSS);
            assertThat(key.qualifiers()).hasSize(1);
            assertThat(key.qualifiers().get(0)).isInstanceOf(CompoundKey.Sememe.class);
            assertThat(((CompoundKey.Sememe) key.qualifiers().get(0)).id()).isEqualTo(ENG);
            assertThat(key.isSemantic()).isTrue();
        }

        @Test
        @DisplayName("head with literal qualifier")
        void headWithLiteralQualifier() {
            CompoundKey key = CompoundKey.of(EXPRESSION, "x");

            assertThat(key.size()).isEqualTo(2);
            assertThat(key.head()).isEqualTo(EXPRESSION);
            assertThat(key.qualifiers()).hasSize(1);
            assertThat(key.qualifiers().get(0)).isInstanceOf(CompoundKey.Literal.class);
            assertThat(((CompoundKey.Literal) key.qualifiers().get(0)).value()).isEqualTo("x");
            assertThat(key.isSemantic()).isFalse();
        }

        @Test
        @DisplayName("mixed sememe and literal qualifiers")
        void mixedQualifiers() {
            ItemID hostId = ItemID.fromString("cg:item/host-1");
            CompoundKey key = CompoundKey.of(EXPRESSION, hostId, "Display-0");

            assertThat(key.size()).isEqualTo(3);
            assertThat(key.qualifiers()).hasSize(2);
            assertThat(key.qualifiers().get(0)).isInstanceOf(CompoundKey.Sememe.class);
            assertThat(key.qualifiers().get(1)).isInstanceOf(CompoundKey.Literal.class);
            assertThat(key.isSemantic()).isFalse();
        }

        @Test
        @DisplayName("rejects null head")
        void rejectsNullHead() {
            assertThatThrownBy(() -> CompoundKey.of(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects unknown qualifier type")
        void rejectsUnknownQualifierType() {
            assertThatThrownBy(() -> CompoundKey.of(TITLE, 42))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("explicit qualifier list factory")
        void explicitQualifierList() {
            List<CompoundKey.FrameToken> qualifiers = List.of(
                    new CompoundKey.Sememe(ENG),
                    new CompoundKey.Literal("hello"));
            CompoundKey key = CompoundKey.of(GLOSS, qualifiers);

            assertThat(key.head()).isEqualTo(GLOSS);
            assertThat(key.qualifiers()).hasSize(2);
        }

        @Test
        @DisplayName("FrameToken can be passed directly as qualifier")
        void frameTokenDirectly() {
            CompoundKey.FrameToken sememe = new CompoundKey.Sememe(ENG);
            CompoundKey key = CompoundKey.of(GLOSS, sememe);

            assertThat(key.qualifiers()).hasSize(1);
            assertThat(key.qualifiers().get(0)).isSameAs(sememe);
        }
    }

    @Nested
    @DisplayName("Canonical string")
    class CanonicalString {

        @Test
        @DisplayName("toCanonicalString is deterministic")
        void canonicalStringDeterministic() {
            CompoundKey key1 = CompoundKey.of(GLOSS, ENG);
            CompoundKey key2 = CompoundKey.of(GLOSS, ENG);

            assertThat(key1.toCanonicalString()).isEqualTo(key2.toCanonicalString());
        }

        @Test
        @DisplayName("canonical string round-trips")
        void canonicalStringRoundTrip() {
            CompoundKey original = CompoundKey.of(EXPRESSION, "x");
            String canonical = original.toCanonicalString();
            CompoundKey decoded = CompoundKey.fromCanonicalString(canonical);

            assertThat(decoded).isEqualTo(original);
        }

        @Test
        @DisplayName("rejects empty canonical string")
        void rejectsEmptyCanonical() {
            assertThatThrownBy(() -> CompoundKey.fromCanonicalString(""))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> CompoundKey.fromCanonicalString(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Display")
    class Display {

        @Test
        @DisplayName("head only displays in parens")
        void headOnlyDisplay() {
            CompoundKey key = CompoundKey.of(TITLE);
            assertThat(key.displayText()).startsWith("(");
            assertThat(key.displayText()).endsWith(")");
            assertThat(key.displayText()).isNotBlank();
        }

        @Test
        @DisplayName("compound key shows comma-separated tokens")
        void compoundDisplay() {
            CompoundKey key = CompoundKey.of(GLOSS, ENG);
            String display = key.displayText();
            assertThat(display).startsWith("(");
            assertThat(display).endsWith(")");
            assertThat(display).contains(", ");
        }

        @Test
        @DisplayName("literal qualifier displays in quotes")
        void literalQualifierDisplay() {
            CompoundKey key = CompoundKey.of(EXPRESSION, "x");
            assertThat(key.displayText()).contains("\"x\"");
        }
    }

    @Nested
    @DisplayName("CBOR round-trip")
    class CborRoundTrip {

        @Test
        @DisplayName("head-only key survives encode/decode")
        void headOnly() {
            CompoundKey original = CompoundKey.of(TITLE);
            CBORObject cbor = original.toCborTree(Scope.BODY);
            CompoundKey decoded = CompoundKey.fromCborTree(cbor);

            assertThat(decoded).isEqualTo(original);
            assertThat(decoded.head()).isEqualTo(TITLE);
        }

        @Test
        @DisplayName("compound sememe key survives encode/decode")
        void compoundSememe() {
            CompoundKey original = CompoundKey.of(GLOSS, ENG);
            CBORObject cbor = original.toCborTree(Scope.BODY);
            CompoundKey decoded = CompoundKey.fromCborTree(cbor);

            assertThat(decoded).isEqualTo(original);
            assertThat(decoded.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("key with literal qualifier survives encode/decode")
        void withLiteralQualifier() {
            CompoundKey original = CompoundKey.of(EXPRESSION, "x");
            CBORObject cbor = original.toCborTree(Scope.BODY);
            CompoundKey decoded = CompoundKey.fromCborTree(cbor);

            assertThat(decoded).isEqualTo(original);
            assertThat(decoded.qualifiers().get(0))
                    .isInstanceOfSatisfying(CompoundKey.Literal.class,
                            l -> assertThat(l.value()).isEqualTo("x"));
        }

        @Test
        @DisplayName("mixed qualifiers survive encode/decode")
        void mixedQualifiers() {
            ItemID hostId = ItemID.fromString("cg:item/host-1");
            CompoundKey original = CompoundKey.of(EXPRESSION, hostId, "Display-0");
            CBORObject cbor = original.toCborTree(Scope.BODY);
            CompoundKey decoded = CompoundKey.fromCborTree(cbor);

            assertThat(decoded).isEqualTo(original);
            assertThat(decoded.size()).isEqualTo(3);
        }

        @Test
        @DisplayName("binary round-trip via encodeBinary")
        void binaryRoundTrip() {
            CompoundKey original = CompoundKey.of(GLOSS, ENG);
            byte[] bytes = original.encodeBinary(Scope.BODY);
            CompoundKey decoded = CompoundKey.fromCborTree(CBORObject.DecodeFromBytes(bytes));

            assertThat(decoded).isEqualTo(original);
        }

        @Test
        @DisplayName("rejects empty CBOR array")
        void rejectsEmptyArray() {
            CBORObject empty = CBORObject.NewArray();
            assertThatThrownBy(() -> CompoundKey.fromCborTree(empty))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Equality and comparison")
    class EqualityAndComparison {

        @Test
        @DisplayName("same head and qualifiers produce equal keys")
        void equalKeys() {
            CompoundKey a = CompoundKey.of(GLOSS, ENG);
            CompoundKey b = CompoundKey.of(GLOSS, ENG);

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("different heads produce unequal keys")
        void unequalKeys() {
            CompoundKey a = CompoundKey.of(TITLE);
            CompoundKey b = CompoundKey.of(CONTENT);

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("different qualifiers produce unequal keys")
        void differentQualifiers() {
            CompoundKey a = CompoundKey.of(GLOSS, ENG);
            CompoundKey b = CompoundKey.of(GLOSS, CONTENT);

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("comparison is consistent (anti-symmetric)")
        void comparisonConsistent() {
            CompoundKey a = CompoundKey.of(TITLE);
            CompoundKey b = CompoundKey.of(GLOSS, ENG);

            int cmp = a.compareTo(b);
            if (cmp != 0) {
                assertThat(Integer.signum(b.compareTo(a))).isEqualTo(-Integer.signum(cmp));
            }
        }

        @Test
        @DisplayName("shorter qualifier list sorts before longer when heads equal")
        void shorterSortsFirst() {
            CompoundKey shorter = CompoundKey.of(GLOSS);
            CompoundKey longer = CompoundKey.of(GLOSS, ENG);

            assertThat(shorter.compareTo(longer)).isLessThan(0);
        }
    }

    @Nested
    @DisplayName("Token list compatibility")
    class TokenListCompat {

        @Test
        @DisplayName("tokens() returns head as first element")
        void tokensIncludesHead() {
            CompoundKey key = CompoundKey.of(GLOSS, ENG);
            List<CompoundKey.FrameToken> all = key.tokens();

            assertThat(all).hasSize(2);
            assertThat(all.get(0)).isInstanceOfSatisfying(CompoundKey.Sememe.class,
                    s -> assertThat(s.id()).isEqualTo(GLOSS));
            assertThat(all.get(1)).isInstanceOfSatisfying(CompoundKey.Sememe.class,
                    s -> assertThat(s.id()).isEqualTo(ENG));
        }

        @Test
        @DisplayName("tokens() of head-only key has just the head")
        void tokensHeadOnly() {
            CompoundKey key = CompoundKey.of(TITLE);
            assertThat(key.tokens()).hasSize(1);
        }
    }
}
