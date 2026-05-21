package dev.everydaythings.graph.library;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.ref.SchemaRef;
import dev.everydaythings.graph.ref.TypeRef;
import dev.everydaythings.graph.value.Color;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QueryWalker recognizes the structural shape of a query.  Per docs/query.md,
 * a frame is a query iff it contains a TypeRef in any position (or, in the
 * future, a partially-applied Bool-returning operator — not yet implemented).
 *
 * <p>The walker is purely structural — it doesn't validate semantic
 * plausibility.  A TypeRef placed nonsensically (e.g., as the target of a
 * binding whose semantic role expects a primitive) is still flagged as a
 * query mechanically.  Such fixtures are intentionally not tested here
 * because they'd model patterns no real query would generate.
 */
class QueryWalkerTest {

    @Nested
    @DisplayName("Queries")
    class Queries {

        @Test
        @DisplayName("?Color — find any Color")
        void anyColor() {
            Body body = Body.of(TypeRef.iid(Color.KEY), List.of());
            assertThat(QueryWalker.isQuery(body)).isTrue();
        }

        @Test
        @DisplayName("?Color with literal constraints — find Colors matching R=200")
        void colorWithLiteralConstraint() {
            Body body = Body.of(TypeRef.iid(Color.KEY), List.of(
                    new Binding(ItemRef.iid(Color.R.KEY), 200L)));
            assertThat(QueryWalker.isQuery(body)).isTrue();
        }
    }

    @Nested
    @DisplayName("Non-queries")
    class NonQueries {

        @Test
        @DisplayName("A literal Color instance")
        void colorInstance() {
            Body body = Body.of(ItemRef.iid(Color.KEY), List.of(
                    new Binding(ItemRef.iid(Color.R.KEY), 200L),
                    new Binding(ItemRef.iid(Color.G.KEY), 50L),
                    new Binding(ItemRef.iid(Color.B.KEY), 100L)));
            assertThat(QueryWalker.isQuery(body)).isFalse();
        }

        @Test
        @DisplayName("An archetype manifest with schema-roled bindings (EXPECTS declarations)")
        void schemaDeclarations() {
            // Approximates Color's archetype manifest: literal head, !-roled bindings.
            // These are SCHEMA declarations, structurally distinct from queries.
            Body body = Body.of(ItemRef.iid(Color.KEY), List.of(
                    new Binding(SchemaRef.iid(Color.R.KEY), 0L),
                    new Binding(SchemaRef.iid(Color.G.KEY), 0L),
                    new Binding(SchemaRef.iid(Color.B.KEY), 0L)));
            assertThat(QueryWalker.isQuery(body)).isFalse();
        }
    }
}
