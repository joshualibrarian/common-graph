package dev.everydaythings.graph.item.component.expression;

import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.relation.Relation;
import dev.everydaythings.graph.language.Sememe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FrameQuery")
class FrameQueryTest {

    static final ItemID AUTHOR = ItemID.fromString("cg:pred/author");
    static final ItemID TITLE = ItemID.fromString("cg:pred/title");
    static final ItemID THE_HOBBIT = ItemID.fromString("cg:book/the-hobbit");
    static final ItemID TOLKIEN = ItemID.fromString("cg:person/tolkien");
    static final ItemID TARGET_ROLE = ItemID.fromString("cg.role:target");
    static final ItemID ANY = Sememe.ANY.iid();
    static final ItemID WHAT = Sememe.WHAT.iid();

    @Test
    @DisplayName("of(predicate, theme) creates query with known predicate and theme")
    void ofFactory() {
        FrameQuery q = FrameQuery.of(AUTHOR, THE_HOBBIT);
        assertThat(q.predicate()).isEqualTo(AUTHOR);
        assertThat(q.theme()).isEqualTo(THE_HOBBIT);
        assertThat(q.bindings()).isEmpty();
    }

    @Test
    @DisplayName("about(theme) creates query with WHAT predicate")
    void aboutFactory() {
        FrameQuery q = FrameQuery.about(THE_HOBBIT);
        assertThat(q.predicate()).isEqualTo(WHAT);
        assertThat(q.theme()).isEqualTo(THE_HOBBIT);
    }

    @Test
    @DisplayName("withPredicate creates query with WHAT theme")
    void withPredicateFactory() {
        FrameQuery q = FrameQuery.withPredicate(AUTHOR);
        assertThat(q.predicate()).isEqualTo(AUTHOR);
        assertThat(q.theme()).isEqualTo(WHAT);
    }

    @Test
    @DisplayName("builder creates query with bindings")
    void builder() {
        FrameQuery q = FrameQuery.builder()
                .predicate(AUTHOR)
                .theme(WHAT)
                .binding(TARGET_ROLE, TOLKIEN)
                .build();

        assertThat(q.predicate()).isEqualTo(AUTHOR);
        assertThat(q.theme()).isEqualTo(WHAT);
        assertThat(q.bindings()).containsKey(TARGET_ROLE);
    }

    @SuppressWarnings("deprecation")
    @Test
    @DisplayName("fromPattern converts SPO pattern to frame query")
    void fromPattern() {
        PatternExpression pattern = PatternExpression.pattern(THE_HOBBIT, AUTHOR, WHAT);
        FrameQuery q = FrameQuery.fromPattern(pattern);

        assertThat(q.predicate()).isEqualTo(AUTHOR);
        assertThat(q.theme()).isEqualTo(THE_HOBBIT);
    }

    @Test
    @DisplayName("toExpressionString formats query readably")
    void expressionString() {
        FrameQuery q = FrameQuery.of(AUTHOR, THE_HOBBIT);
        String s = q.toExpressionString();
        assertThat(s).contains("theme:");
    }

    @Test
    @DisplayName("hasDependencies returns true")
    void hasDependencies() {
        assertThat(FrameQuery.of(AUTHOR, THE_HOBBIT).hasDependencies()).isTrue();
    }

    @Test
    @DisplayName("evaluate with no library returns empty list")
    void evaluateNoLibrary() {
        EvaluationContext ctx = new EvaluationContext(null, null);
        Object result = FrameQuery.of(AUTHOR, THE_HOBBIT).evaluate(ctx);
        assertThat(result).isInstanceOf(java.util.List.class);
        assertThat((java.util.List<?>) result).isEmpty();
    }
}
