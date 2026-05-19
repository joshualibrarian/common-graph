package dev.everydaythings.graph.language;

import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.text.TextSpan;
import dev.everydaythings.graph.text.TokenLattice;
import dev.everydaythings.graph.text.TokenLattice.TokenSpan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the universal literal-recognition contract on {@link Language},
 * exercised through the English subclass.
 *
 * <p>Demonstrates the override pattern: English inherits locale-aware number
 * parsing from the base (ICU NumberFormat for {@link com.ibm.icu.util.ULocale#ENGLISH}),
 * and overrides {@code recognizeBoolean} to add {@code true}/{@code false}.
 *
 * <p>This is the foundation that every parse helper builds on — once
 * notations are migrated to the same path, this contract handles all literal
 * recognition across the codebase.
 */
class EnglishLiteralTest {

    private English english;

    @BeforeEach
    void setUp() {
        Librarian librarian = Librarian.inMemory();
        librarian.bootstrap();
        Optional<Item> englishItem = librarian.fetchItem(ItemRef.iid(Language.English.KEY));
        assertThat(englishItem).as("English seeded into bootstrap").isPresent();
        english = (English) englishItem.get();
    }

    @Test
    @DisplayName("integer literal \"5\" → 5L")
    void integerLiteral() {
        assertThat(english.recognizeLiteral(literal("5"))).contains(5L);
    }

    @Test
    @DisplayName("decimal literal \"3.14\" → BigDecimal (no IEEE float)")
    void decimalLiteral() {
        Object value = english.recognizeLiteral(literal("3.14")).orElseThrow();
        assertThat(value).isInstanceOf(BigDecimal.class);
        assertThat((BigDecimal) value).isEqualByComparingTo(new BigDecimal("3.14"));
    }

    @Test
    @DisplayName("English-locale thousands separator: \"10,000\" → 10000L")
    void englishThousandsSeparator() {
        // English locale uses comma as thousands separator — ICU handles this.
        // German would parse "10,000" as decimal 10.0; we'll see that distinction
        // once German lands.
        assertThat(english.recognizeLiteral(literal("10,000"))).contains(10000L);
    }

    @Test
    @DisplayName("negative integer \"-5\" → -5L")
    void negativeInteger() {
        assertThat(english.recognizeLiteral(literal("-5"))).contains(-5L);
    }

    @Test
    @DisplayName("boolean \"true\" → Boolean.TRUE (English override)")
    void booleanTrue() {
        assertThat(english.recognizeLiteral(literal("true"))).contains(Boolean.TRUE);
    }

    @Test
    @DisplayName("boolean \"false\" → Boolean.FALSE (English override)")
    void booleanFalse() {
        assertThat(english.recognizeLiteral(literal("false"))).contains(Boolean.FALSE);
    }

    @Test
    @DisplayName("boolean is case-insensitive: \"TRUE\" → Boolean.TRUE")
    void booleanCaseInsensitive() {
        assertThat(english.recognizeLiteral(literal("TRUE"))).contains(Boolean.TRUE);
        assertThat(english.recognizeLiteral(literal("True"))).contains(Boolean.TRUE);
        assertThat(english.recognizeLiteral(literal("False"))).contains(Boolean.FALSE);
    }

    @Test
    @DisplayName("string literal \"\\\"hello\\\"\" → \"hello\" (quotes stripped)")
    void stringLiteral() {
        assertThat(english.recognizeLiteral(literal("\"hello\""))).contains("hello");
    }

    @Test
    @DisplayName("single-quoted string \"'world'\" → \"world\"")
    void singleQuotedString() {
        assertThat(english.recognizeLiteral(literal("'world'"))).contains("world");
    }

    @Test
    @DisplayName("unrecognized literal returns empty")
    void unrecognized() {
        assertThat(english.recognizeLiteral(literal("xyz"))).isEmpty();
    }

    @Test
    @DisplayName("non-LITERAL token returns empty")
    void nonLiteralToken() {
        // Same surface text "5", but kind=WORD — recognizeLiteral should reject.
        TokenSpan word = new TokenSpan(new TextSpan(0, 1), "5",
                List.of(), TokenLattice.Kind.WORD, BigDecimal.ONE);
        assertThat(english.recognizeLiteral(word)).isEmpty();
    }

    @Test
    @DisplayName("renderLiteral round-trips integers, decimals, booleans, strings")
    void renderLiteralRoundTrip() {
        // Numbers: locale-formatted via ICU. English's ULocale.ENGLISH gives "."
        // for decimal — should produce a form recognizeLiteral can re-accept.
        assertThat(english.renderLiteral(5L)).isEqualTo("5");
        assertThat(english.renderLiteral(new BigDecimal("3.14"))).isEqualTo("3.14");
        assertThat(english.renderLiteral(Boolean.TRUE)).isEqualTo("true");
        assertThat(english.renderLiteral(Boolean.FALSE)).isEqualTo("false");
        assertThat(english.renderLiteral("hello")).isEqualTo("\"hello\"");
    }

    /** Build a LITERAL-kind TokenSpan with the given surface text. */
    private static TokenSpan literal(String text) {
        return new TokenSpan(new TextSpan(0, text.length()), text,
                List.of(), TokenLattice.Kind.LITERAL, BigDecimal.ONE);
    }
}
