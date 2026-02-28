package dev.everydaythings.graph;

import dev.everydaythings.graph.Canonical.Canon;
import dev.everydaythings.graph.CanonicalSchema.FieldSchema;
import dev.everydaythings.graph.ui.scene.Scene;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CanonicalSchema FieldSchema enrichment")
class CanonicalSchemaTest {

    // ==================================================================================
    // Test Canonical types
    // ==================================================================================

    enum TestDirection { VERTICAL, HORIZONTAL }

    static class TestConfig implements Canonical {
        @Canon(order = 0) String title = "Default";
        @Canon(order = 1) boolean darkMode = false;
        @Canon(order = 2) int fontSize = 14;
        @Canon(order = 3) TestDirection layout = TestDirection.VERTICAL;
        @Canon(order = 4) List<String> tags = new ArrayList<>();
        @Canon(order = 5) long timestamp = 0L;
        @Canon(order = 6) double opacity = 1.0;
    }

    static class NestedCanonical implements Canonical {
        @Canon(order = 0) String name = "child";
    }

    static class ParentConfig implements Canonical {
        @Canon(order = 0) NestedCanonical child = new NestedCanonical();
        @Canon(order = 1) List<NestedCanonical> children = new ArrayList<>();
    }

    // ==================================================================================
    // Display Name
    // ==================================================================================

    @Nested
    @DisplayName("toDisplayName")
    class ToDisplayName {

        @Test
        @DisplayName("converts camelCase to title case")
        void camelCase() {
            assertThat(CanonicalSchema.toDisplayName("baseColor")).isEqualTo("Base Color");
            assertThat(CanonicalSchema.toDisplayName("darkMode")).isEqualTo("Dark Mode");
            assertThat(CanonicalSchema.toDisplayName("fontSize")).isEqualTo("Font Size");
        }

        @Test
        @DisplayName("single character becomes uppercase")
        void singleChar() {
            assertThat(CanonicalSchema.toDisplayName("x")).isEqualTo("X");
        }

        @Test
        @DisplayName("already capitalized stays same")
        void alreadyCapitalized() {
            assertThat(CanonicalSchema.toDisplayName("Title")).isEqualTo("Title");
        }

        @Test
        @DisplayName("empty string returns empty")
        void empty() {
            assertThat(CanonicalSchema.toDisplayName("")).isEqualTo("");
            assertThat(CanonicalSchema.toDisplayName(null)).isEqualTo("");
        }
    }

    // ==================================================================================
    // Type Predicates
    // ==================================================================================

    @Nested
    @DisplayName("type predicates")
    class TypePredicates {

        @Test
        @DisplayName("isBoolean detects boolean fields")
        void isBoolean() {
            CanonicalSchema schema = CanonicalSchema.of(TestConfig.class);
            FieldSchema darkMode = findField(schema, "darkMode");
            FieldSchema title = findField(schema, "title");

            assertThat(darkMode.isBoolean()).isTrue();
            assertThat(title.isBoolean()).isFalse();
        }

        @Test
        @DisplayName("isString detects String fields")
        void isString() {
            CanonicalSchema schema = CanonicalSchema.of(TestConfig.class);
            FieldSchema title = findField(schema, "title");
            FieldSchema fontSize = findField(schema, "fontSize");

            assertThat(title.isString()).isTrue();
            assertThat(fontSize.isString()).isFalse();
        }

        @Test
        @DisplayName("isNumeric detects numeric fields")
        void isNumeric() {
            CanonicalSchema schema = CanonicalSchema.of(TestConfig.class);
            FieldSchema fontSize = findField(schema, "fontSize");
            FieldSchema timestamp = findField(schema, "timestamp");
            FieldSchema opacity = findField(schema, "opacity");
            FieldSchema title = findField(schema, "title");

            assertThat(fontSize.isNumeric()).isTrue();
            assertThat(timestamp.isNumeric()).isTrue();
            assertThat(opacity.isNumeric()).isTrue();
            assertThat(title.isNumeric()).isFalse();
        }

        @Test
        @DisplayName("isEnum detects enum fields")
        void isEnum() {
            CanonicalSchema schema = CanonicalSchema.of(TestConfig.class);
            FieldSchema layout = findField(schema, "layout");
            FieldSchema title = findField(schema, "title");

            assertThat(layout.isEnum()).isTrue();
            assertThat(title.isEnum()).isFalse();
        }

        @Test
        @DisplayName("isCanonical detects Canonical fields")
        void isCanonical() {
            CanonicalSchema schema = CanonicalSchema.of(ParentConfig.class);
            FieldSchema child = findField(schema, "child");

            assertThat(child.isCanonical()).isTrue();
        }

        @Test
        @DisplayName("isCollection detects List fields")
        void isCollection() {
            CanonicalSchema schema = CanonicalSchema.of(TestConfig.class);
            FieldSchema tags = findField(schema, "tags");
            FieldSchema title = findField(schema, "title");

            assertThat(tags.isCollection()).isTrue();
            assertThat(title.isCollection()).isFalse();
        }
    }

    // ==================================================================================
    // Enum Constants
    // ==================================================================================

    @Nested
    @DisplayName("enumConstants")
    class EnumConstants {

        @Test
        @DisplayName("returns enum values for enum field")
        void enumValues() {
            CanonicalSchema schema = CanonicalSchema.of(TestConfig.class);
            FieldSchema layout = findField(schema, "layout");

            Object[] constants = layout.enumConstants();
            assertThat(constants).isNotNull();
            assertThat(constants).hasSize(2);
            assertThat(constants[0]).isEqualTo(TestDirection.VERTICAL);
            assertThat(constants[1]).isEqualTo(TestDirection.HORIZONTAL);
        }

        @Test
        @DisplayName("returns null for non-enum field")
        void nonEnum() {
            CanonicalSchema schema = CanonicalSchema.of(TestConfig.class);
            FieldSchema title = findField(schema, "title");

            assertThat(title.enumConstants()).isNull();
        }
    }

    // ==================================================================================
    // Element Type
    // ==================================================================================

    @Nested
    @DisplayName("elementType")
    class ElementType {

        @Test
        @DisplayName("extracts String from List<String>")
        void listOfString() {
            CanonicalSchema schema = CanonicalSchema.of(TestConfig.class);
            FieldSchema tags = findField(schema, "tags");

            assertThat(tags.elementType()).isEqualTo(String.class);
        }

        @Test
        @DisplayName("extracts Canonical from List<Canonical>")
        void listOfCanonical() {
            CanonicalSchema schema = CanonicalSchema.of(ParentConfig.class);
            FieldSchema children = findField(schema, "children");

            assertThat(children.elementType()).isEqualTo(NestedCanonical.class);
        }

        @Test
        @DisplayName("returns Object for non-parameterized field")
        void nonParameterized() {
            CanonicalSchema schema = CanonicalSchema.of(TestConfig.class);
            FieldSchema title = findField(schema, "title");

            assertThat(title.elementType()).isEqualTo(Object.class);
        }
    }

    // ==================================================================================
    // Set Value
    // ==================================================================================

    @Nested
    @DisplayName("setValue")
    class SetValue {

        @Test
        @DisplayName("sets string field")
        void setString() {
            TestConfig config = new TestConfig();
            CanonicalSchema schema = CanonicalSchema.of(TestConfig.class);
            FieldSchema title = findField(schema, "title");

            title.setValue(config, "New Title");
            assertThat(config.title).isEqualTo("New Title");
        }

        @Test
        @DisplayName("sets boolean field")
        void setBoolean() {
            TestConfig config = new TestConfig();
            CanonicalSchema schema = CanonicalSchema.of(TestConfig.class);
            FieldSchema darkMode = findField(schema, "darkMode");

            darkMode.setValue(config, true);
            assertThat(config.darkMode).isTrue();
        }

        @Test
        @DisplayName("sets enum field")
        void setEnum() {
            TestConfig config = new TestConfig();
            CanonicalSchema schema = CanonicalSchema.of(TestConfig.class);
            FieldSchema layout = findField(schema, "layout");

            layout.setValue(config, TestDirection.HORIZONTAL);
            assertThat(config.layout).isEqualTo(TestDirection.HORIZONTAL);
        }

        @Test
        @DisplayName("sets int field")
        void setInt() {
            TestConfig config = new TestConfig();
            CanonicalSchema schema = CanonicalSchema.of(TestConfig.class);
            FieldSchema fontSize = findField(schema, "fontSize");

            fontSize.setValue(config, 20);
            assertThat(config.fontSize).isEqualTo(20);
        }
    }

    // ==================================================================================
    // Display Name on Fields
    // ==================================================================================

    @Nested
    @DisplayName("field display names")
    class FieldDisplayNames {

        @Test
        @DisplayName("fields carry computed display names")
        void fieldDisplayNames() {
            CanonicalSchema schema = CanonicalSchema.of(TestConfig.class);

            assertThat(findField(schema, "title").displayName()).isEqualTo("Title");
            assertThat(findField(schema, "darkMode").displayName()).isEqualTo("Dark Mode");
            assertThat(findField(schema, "fontSize").displayName()).isEqualTo("Font Size");
            assertThat(findField(schema, "layout").displayName()).isEqualTo("Layout");
            assertThat(findField(schema, "tags").displayName()).isEqualTo("Tags");
        }
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    private static FieldSchema findField(CanonicalSchema schema, String name) {
        return schema.fields().stream()
                .filter(f -> f.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No field: " + name));
    }
}
