package dev.everydaythings.graph.editing;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Canonical.Canon;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;
import dev.everydaythings.graph.ui.text.CliSurfaceRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Canonical Editor")
class CanonicalEditorTest {

    // ==================================================================================
    // Test Canonical type
    // ==================================================================================

    enum TestLayout { VERTICAL, HORIZONTAL, GRID }

    static class TestConfig implements Canonical {
        @Canon(order = 0) String title = "Default";
        @Canon(order = 1) boolean darkMode = false;
        @Canon(order = 2) int fontSize = 14;
        @Canon(order = 3) TestLayout layout = TestLayout.VERTICAL;
        @Canon(order = 4) List<String> tags = new ArrayList<>();
    }

    // ==================================================================================
    // EditModel get/set
    // ==================================================================================

    @Nested
    @DisplayName("EditModel get/set")
    class GetSet {

        @Test
        @DisplayName("get returns current field values")
        void getReturnsValues() {
            TestConfig config = new TestConfig();
            EditModel model = new EditModel(config);

            assertThat(model.get("title")).isEqualTo("Default");
            assertThat(model.get("darkMode")).isEqualTo(false);
            assertThat(model.get("fontSize")).isEqualTo(14);
            assertThat(model.get("layout")).isEqualTo(TestLayout.VERTICAL);
        }

        @Test
        @DisplayName("set updates field value")
        void setUpdatesValue() {
            TestConfig config = new TestConfig();
            EditModel model = new EditModel(config);

            model.set("title", "New Title");
            assertThat(model.get("title")).isEqualTo("New Title");
            assertThat(config.title).isEqualTo("New Title");
        }

        @Test
        @DisplayName("get returns null for unknown field")
        void getUnknownReturnsNull() {
            TestConfig config = new TestConfig();
            EditModel model = new EditModel(config);

            assertThat(model.get("nonexistent")).isNull();
        }

        @Test
        @DisplayName("target returns the wrapped Canonical")
        void targetReturnsWrapped() {
            TestConfig config = new TestConfig();
            EditModel model = new EditModel(config);

            assertThat(model.target()).isSameAs(config);
        }
    }

    // ==================================================================================
    // Boolean toggle
    // ==================================================================================

    @Nested
    @DisplayName("Boolean toggle events")
    class BooleanToggle {

        @Test
        @DisplayName("toggle:darkMode flips false to true")
        void toggleFalseToTrue() {
            TestConfig config = new TestConfig();
            EditModel model = new EditModel(config);

            boolean handled = model.handleEvent("toggle:darkMode", "");

            assertThat(handled).isTrue();
            assertThat(config.darkMode).isTrue();
        }

        @Test
        @DisplayName("toggle:darkMode flips true to false")
        void toggleTrueToFalse() {
            TestConfig config = new TestConfig();
            config.darkMode = true;
            EditModel model = new EditModel(config);

            model.handleEvent("toggle:darkMode", "");

            assertThat(config.darkMode).isFalse();
        }

        @Test
        @DisplayName("toggle on non-boolean field is not handled")
        void toggleNonBoolean() {
            TestConfig config = new TestConfig();
            EditModel model = new EditModel(config);

            boolean handled = model.handleEvent("toggle:title", "");

            assertThat(handled).isFalse();
        }
    }

    // ==================================================================================
    // Enum select
    // ==================================================================================

    @Nested
    @DisplayName("Enum select events")
    class EnumSelect {

        @Test
        @DisplayName("select:layout changes enum value")
        void selectChangesEnum() {
            TestConfig config = new TestConfig();
            EditModel model = new EditModel(config);

            boolean handled = model.handleEvent("select:layout", "HORIZONTAL");

            assertThat(handled).isTrue();
            assertThat(config.layout).isEqualTo(TestLayout.HORIZONTAL);
        }

        @Test
        @DisplayName("select with invalid constant is not handled")
        void selectInvalidConstant() {
            TestConfig config = new TestConfig();
            EditModel model = new EditModel(config);

            boolean handled = model.handleEvent("select:layout", "NONEXISTENT");

            assertThat(handled).isFalse();
            assertThat(config.layout).isEqualTo(TestLayout.VERTICAL);
        }

        @Test
        @DisplayName("select on non-enum field is not handled")
        void selectNonEnum() {
            TestConfig config = new TestConfig();
            EditModel model = new EditModel(config);

            boolean handled = model.handleEvent("select:title", "something");

            assertThat(handled).isFalse();
        }
    }

    // ==================================================================================
    // String set
    // ==================================================================================

    @Nested
    @DisplayName("String set events")
    class StringSet {

        @Test
        @DisplayName("set:title changes string value")
        void setChangesString() {
            TestConfig config = new TestConfig();
            EditModel model = new EditModel(config);

            boolean handled = model.handleEvent("set:title", "Updated");

            assertThat(handled).isTrue();
            assertThat(config.title).isEqualTo("Updated");
        }

        @Test
        @DisplayName("set on non-string field is not handled")
        void setNonString() {
            TestConfig config = new TestConfig();
            EditModel model = new EditModel(config);

            boolean handled = model.handleEvent("set:fontSize", "20");

            assertThat(handled).isFalse();
        }
    }

    // ==================================================================================
    // Change notification
    // ==================================================================================

    @Nested
    @DisplayName("Change notifications")
    class ChangeNotification {

        @Test
        @DisplayName("onChange fires on set")
        void onChangeFiresOnSet() {
            TestConfig config = new TestConfig();
            EditModel model = new EditModel(config);
            AtomicInteger count = new AtomicInteger();
            model.onChange(count::incrementAndGet);

            model.set("title", "New");

            assertThat(count.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("onChange fires on toggle event")
        void onChangeFiresOnToggle() {
            TestConfig config = new TestConfig();
            EditModel model = new EditModel(config);
            AtomicInteger count = new AtomicInteger();
            model.onChange(count::incrementAndGet);

            model.handleEvent("toggle:darkMode", "");

            assertThat(count.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("onChange fires on select event")
        void onChangeFiresOnSelect() {
            TestConfig config = new TestConfig();
            EditModel model = new EditModel(config);
            AtomicInteger count = new AtomicInteger();
            model.onChange(count::incrementAndGet);

            model.handleEvent("select:layout", "GRID");

            assertThat(count.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("onChange does not fire on unhandled event")
        void onChangeNotFiresOnUnhandled() {
            TestConfig config = new TestConfig();
            EditModel model = new EditModel(config);
            AtomicInteger count = new AtomicInteger();
            model.onChange(count::incrementAndGet);

            model.handleEvent("unknown:field", "");

            assertThat(count.get()).isEqualTo(0);
        }

        @Test
        @DisplayName("batch coalesces multiple changes")
        void batchCoalesces() {
            TestConfig config = new TestConfig();
            EditModel model = new EditModel(config);
            AtomicInteger count = new AtomicInteger();
            model.onChange(count::incrementAndGet);

            model.batch(() -> {
                model.set("title", "Batch1");
                model.set("title", "Batch2");
                model.handleEvent("toggle:darkMode", "");
            });

            assertThat(count.get()).isEqualTo(1);
            assertThat(config.title).isEqualTo("Batch2");
            assertThat(config.darkMode).isTrue();
        }
    }

    // ==================================================================================
    // Surface rendering
    // ==================================================================================

    @Nested
    @DisplayName("Surface rendering")
    class SurfaceRendering {

        @Test
        @DisplayName("toSurface produces CanonicalEditorSurface")
        void toSurfaceProducesEditor() {
            TestConfig config = new TestConfig();
            EditModel model = new EditModel(config);

            SurfaceSchema surface = model.toSurface();

            assertThat(surface).isInstanceOf(CanonicalEditorSurface.class);
        }

        @Test
        @DisplayName("render produces output with field labels")
        void renderProducesOutput() {
            TestConfig config = new TestConfig();
            config.title = "Hello World";
            EditModel model = new EditModel(config);

            SurfaceSchema surface = model.toSurface();
            CliSurfaceRenderer renderer = new CliSurfaceRenderer();
            surface.render(renderer);
            String output = renderer.result();

            assertThat(output).contains("Title");
            assertThat(output).contains("Dark Mode");
            assertThat(output).contains("Font Size");
            assertThat(output).contains("Layout");
            assertThat(output).contains("Tags");
        }

        @Test
        @DisplayName("render shows field values")
        void renderShowsValues() {
            TestConfig config = new TestConfig();
            config.title = "Test Title";
            config.fontSize = 20;
            EditModel model = new EditModel(config);

            SurfaceSchema surface = model.toSurface();
            CliSurfaceRenderer renderer = new CliSurfaceRenderer();
            surface.render(renderer);
            String output = renderer.result();

            assertThat(output).contains("Test Title");
            assertThat(output).contains("20");
        }

        @Test
        @DisplayName("render shows enum options")
        void renderShowsEnumOptions() {
            TestConfig config = new TestConfig();
            EditModel model = new EditModel(config);

            SurfaceSchema surface = model.toSurface();
            CliSurfaceRenderer renderer = new CliSurfaceRenderer();
            surface.render(renderer);
            String output = renderer.result();

            assertThat(output).contains("VERTICAL");
            assertThat(output).contains("HORIZONTAL");
            assertThat(output).contains("GRID");
        }

        @Test
        @DisplayName("render shows collection count")
        void renderShowsCollectionCount() {
            TestConfig config = new TestConfig();
            config.tags.add("a");
            config.tags.add("b");
            EditModel model = new EditModel(config);

            SurfaceSchema surface = model.toSurface();
            CliSurfaceRenderer renderer = new CliSurfaceRenderer();
            surface.render(renderer);
            String output = renderer.result();

            assertThat(output).contains("[2 items]");
        }

        @Test
        @DisplayName("re-render after mutation shows updated values")
        void reRenderAfterMutation() {
            TestConfig config = new TestConfig();
            EditModel model = new EditModel(config);

            // Initial render
            CliSurfaceRenderer r1 = new CliSurfaceRenderer();
            model.toSurface().render(r1);
            assertThat(r1.result()).contains("Default");

            // Mutate and re-render
            model.set("title", "Changed");
            CliSurfaceRenderer r2 = new CliSurfaceRenderer();
            model.toSurface().render(r2);
            assertThat(r2.result()).contains("Changed");
        }
    }

    // ==================================================================================
    // CBOR Round-trip
    // ==================================================================================

    @Nested
    @DisplayName("CBOR round-trip")
    class CborRoundTrip {

        @Test
        @DisplayName("mutated config survives encode/decode")
        void mutatedConfigSurvivesRoundTrip() {
            TestConfig config = new TestConfig();
            EditModel model = new EditModel(config);

            // Mutate via events
            model.handleEvent("set:title", "Round Trip");
            model.handleEvent("toggle:darkMode", "");
            model.handleEvent("select:layout", "GRID");

            // Encode
            byte[] bytes = config.encodeBinary(Canonical.Scope.RECORD);
            assertThat(bytes).isNotEmpty();

            // Decode
            TestConfig decoded = Canonical.decodeBinary(bytes, TestConfig.class, Canonical.Scope.RECORD);
            assertThat(decoded.title).isEqualTo("Round Trip");
            assertThat(decoded.darkMode).isTrue();
            assertThat(decoded.layout).isEqualTo(TestLayout.GRID);
        }
    }

    // ==================================================================================
    // Malformed events
    // ==================================================================================

    @Nested
    @DisplayName("Malformed events")
    class MalformedEvents {

        @Test
        @DisplayName("action without colon is not handled")
        void noColon() {
            TestConfig config = new TestConfig();
            EditModel model = new EditModel(config);

            boolean handled = model.handleEvent("toggle", "");

            assertThat(handled).isFalse();
        }

        @Test
        @DisplayName("unknown field is not handled")
        void unknownField() {
            TestConfig config = new TestConfig();
            EditModel model = new EditModel(config);

            boolean handled = model.handleEvent("toggle:unknown", "");

            assertThat(handled).isFalse();
        }

        @Test
        @DisplayName("unknown operation is not handled")
        void unknownOp() {
            TestConfig config = new TestConfig();
            EditModel model = new EditModel(config);

            boolean handled = model.handleEvent("delete:title", "");

            assertThat(handled).isFalse();
        }
    }
}
