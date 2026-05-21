package dev.everydaythings.graph.library;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.ref.SchemaRef;
import dev.everydaythings.graph.value.Color;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SchemaWalker validates a candidate body against an archetype manifest's
 * {@code !}-roled schema declarations.
 *
 * <p>First-cut presence check: each {@code !Role = ...} binding on the schema
 * implies the candidate must have a literal-roled binding for the same IID.
 * Target-shape validation (value ranges, type compatibility) lands later when
 * the matcher orchestrator is online.
 */
class SchemaWalkerTest {

    @Nested
    @DisplayName("Valid candidates")
    class Valid {

        @Test
        @DisplayName("All schema-declared bindings present on candidate")
        void allBindingsPresent() {
            Body schema = Body.of(ItemRef.iid(Color.KEY), List.of(
                    new Binding(SchemaRef.iid(Color.R.KEY), 0L),
                    new Binding(SchemaRef.iid(Color.G.KEY), 0L),
                    new Binding(SchemaRef.iid(Color.B.KEY), 0L)));
            Body candidate = Body.of(ItemRef.iid(Color.KEY), List.of(
                    new Binding(ItemRef.iid(Color.R.KEY), 200L),
                    new Binding(ItemRef.iid(Color.G.KEY), 50L),
                    new Binding(ItemRef.iid(Color.B.KEY), 100L)));
            ValidationResult r = SchemaWalker.validate(candidate, schema);
            assertThat(r.isValid()).isTrue();
            assertThat(r.issues()).isEmpty();
        }

        @Test
        @DisplayName("Candidate has extra bindings beyond what schema declares — still valid")
        void extraBindingsAllowed() {
            Body schema = Body.of(ItemRef.iid(Color.KEY), List.of(
                    new Binding(SchemaRef.iid(Color.R.KEY), 0L)));
            Body candidate = Body.of(ItemRef.iid(Color.KEY), List.of(
                    new Binding(ItemRef.iid(Color.R.KEY), 200L),
                    new Binding(ItemRef.iid(Color.G.KEY), 50L),
                    new Binding(ItemRef.iid(Color.B.KEY), 100L)));
            ValidationResult r = SchemaWalker.validate(candidate, schema);
            assertThat(r.isValid()).isTrue();
        }

        @Test
        @DisplayName("Schema with no !-roled bindings — trivially valid")
        void noSchemaDeclarations() {
            Body schema = Body.of(ItemRef.iid(Color.KEY), List.of());
            Body candidate = Body.of(ItemRef.iid(Color.KEY), List.of());
            ValidationResult r = SchemaWalker.validate(candidate, schema);
            assertThat(r.isValid()).isTrue();
        }

        @Test
        @DisplayName("Literal-roled bindings on schema body are ignored (not treated as expectations)")
        void literalRoledSchemaBindingsIgnored() {
            // A real archetype manifest carries metadata bindings (ITEM_ID, AUTHOR, etc.)
            // alongside !-roled schema declarations.  Only the !-roled ones are expectations.
            Body schema = Body.of(ItemRef.iid(Color.KEY), List.of(
                    new Binding(ItemRef.iid(Color.R.KEY), 0L)));  // literal role, not a schema declaration
            Body candidate = Body.of(ItemRef.iid(Color.KEY), List.of());
            ValidationResult r = SchemaWalker.validate(candidate, schema);
            assertThat(r.isValid()).isTrue();
        }
    }

    @Nested
    @DisplayName("Invalid candidates")
    class Invalid {

        @Test
        @DisplayName("Missing one expected binding")
        void missingOneBinding() {
            Body schema = Body.of(ItemRef.iid(Color.KEY), List.of(
                    new Binding(SchemaRef.iid(Color.R.KEY), 0L),
                    new Binding(SchemaRef.iid(Color.G.KEY), 0L),
                    new Binding(SchemaRef.iid(Color.B.KEY), 0L)));
            Body candidate = Body.of(ItemRef.iid(Color.KEY), List.of(
                    new Binding(ItemRef.iid(Color.R.KEY), 200L),
                    new Binding(ItemRef.iid(Color.G.KEY), 50L)));
            ValidationResult r = SchemaWalker.validate(candidate, schema);
            assertThat(r.isValid()).isFalse();
            assertThat(r.issues()).hasSize(1);
            assertThat(r.issues().get(0).kind())
                    .isEqualTo(ValidationResult.Issue.Kind.MISSING_BINDING);
        }

        @Test
        @DisplayName("Missing all expected bindings — one issue per missing binding")
        void missingAllBindings() {
            Body schema = Body.of(ItemRef.iid(Color.KEY), List.of(
                    new Binding(SchemaRef.iid(Color.R.KEY), 0L),
                    new Binding(SchemaRef.iid(Color.G.KEY), 0L),
                    new Binding(SchemaRef.iid(Color.B.KEY), 0L)));
            Body candidate = Body.of(ItemRef.iid(Color.KEY), List.of());
            ValidationResult r = SchemaWalker.validate(candidate, schema);
            assertThat(r.isValid()).isFalse();
            assertThat(r.issues()).hasSize(3);
            assertThat(r.issues()).allMatch(
                    i -> i.kind() == ValidationResult.Issue.Kind.MISSING_BINDING);
        }
    }
}
