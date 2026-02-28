package dev.everydaythings.graph.surface.primitive;

import dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer;
import dev.everydaythings.graph.surface.harness.RenderResult;
import dev.everydaythings.graph.surface.harness.RendererProvider;
import dev.everydaythings.graph.surface.harness.assertions.CliAssertions;
import dev.everydaythings.graph.ui.scene.surface.primitive.ImageSurface;
import dev.everydaythings.graph.ui.text.CliSurfaceRenderer;
import dev.everydaythings.graph.ui.text.TuiSurfaceRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ImageSurface primitive across all renderers.
 *
 * <p>ImageSurface displays visual content with a text fallback chain:
 * solid (3D) → image (2D) → alt (text/emoji).
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Alt text rendering (the universal fallback)</li>
 *   <li>Emoji alt text</li>
 *   <li>Size options (small, medium, large, explicit)</li>
 *   <li>Fit options (contain, cover, fill, none)</li>
 * </ul>
 *
 * <p>Note: Image and solid ContentID tests are limited since text renderers
 * always fall back to alt text. Full image rendering is tested in FX-specific tests.
 */
@DisplayName("ImageSurface Primitive")
class ImagePrimitiveTest {

    // ==================================================================================
    // Alt Text (Universal Fallback)
    // ==================================================================================

    @Nested
    @DisplayName("Alt Text")
    class AltText {

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("renders alt text")
        void rendersAltText(String rendererName, SurfaceRenderer renderer) {
            ImageSurface image = ImageSurface.of("Folder icon");

            RenderResult result = RenderResult.capture(image, renderer);

            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("Folder icon");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("renders emoji alt text")
        void rendersEmojiAlt(String rendererName, SurfaceRenderer renderer) {
            ImageSurface image = ImageSurface.of("📁");

            RenderResult result = RenderResult.capture(image, renderer);

            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("📁");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("renders unicode glyph alt text")
        void rendersGlyphAlt(String rendererName, SurfaceRenderer renderer) {
            ImageSurface image = ImageSurface.of("▶");

            RenderResult result = RenderResult.capture(image, renderer);

            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("▶");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("renders descriptive alt text")
        void rendersDescriptiveAlt(String rendererName, SurfaceRenderer renderer) {
            ImageSurface image = ImageSurface.of("Photo of a sunset over the ocean");

            RenderResult result = RenderResult.capture(image, renderer);

            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("sunset");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("handles empty alt text")
        void handlesEmptyAlt(String rendererName, SurfaceRenderer renderer) {
            ImageSurface image = ImageSurface.of("");

            // Should not throw
            RenderResult result = RenderResult.capture(image, renderer);
            assertThat(result).isNotNull();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("handles null alt text")
        void handlesNullAlt(String rendererName, SurfaceRenderer renderer) {
            ImageSurface image = ImageSurface.of(null);

            // Should not throw
            RenderResult result = RenderResult.capture(image, renderer);
            assertThat(result).isNotNull();
        }
    }

    // ==================================================================================
    // Size Options
    // ==================================================================================

    @Nested
    @DisplayName("Size Options")
    class SizeOptions {

        @Test
        @DisplayName("small() sets size to small")
        void smallSetsSize() {
            ImageSurface image = ImageSurface.of("icon").small();

            assertThat(image.size()).isEqualTo("small");
        }

        @Test
        @DisplayName("medium() sets size to medium")
        void mediumSetsSize() {
            ImageSurface image = ImageSurface.of("icon").medium();

            assertThat(image.size()).isEqualTo("medium");
        }

        @Test
        @DisplayName("large() sets size to large")
        void largeSetsSize() {
            ImageSurface image = ImageSurface.of("icon").large();

            assertThat(image.size()).isEqualTo("large");
        }

        @Test
        @DisplayName("explicit size can be set")
        void explicitSizeCanBeSet() {
            ImageSurface image = ImageSurface.of("icon").size("48px");

            assertThat(image.size()).isEqualTo("48px");
        }

        @Test
        @DisplayName("percentage size can be set")
        void percentageSizeCanBeSet() {
            ImageSurface image = ImageSurface.of("icon").size("100%");

            assertThat(image.size()).isEqualTo("100%");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("renders with size set")
        void rendersWithSize(String rendererName, SurfaceRenderer renderer) {
            ImageSurface image = ImageSurface.of("📁").large();

            RenderResult result = RenderResult.capture(image, renderer);

            // Alt text should still render (size affects visual, not text)
            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("📁");
        }
    }

    // ==================================================================================
    // Fit Options
    // ==================================================================================

    @Nested
    @DisplayName("Fit Options")
    class FitOptions {

        @Test
        @DisplayName("contain fit can be set")
        void containFitCanBeSet() {
            ImageSurface image = ImageSurface.of("icon").fit("contain");

            assertThat(image.fit()).isEqualTo("contain");
        }

        @Test
        @DisplayName("cover fit can be set")
        void coverFitCanBeSet() {
            ImageSurface image = ImageSurface.of("icon").fit("cover");

            assertThat(image.fit()).isEqualTo("cover");
        }

        @Test
        @DisplayName("fill fit can be set")
        void fillFitCanBeSet() {
            ImageSurface image = ImageSurface.of("icon").fit("fill");

            assertThat(image.fit()).isEqualTo("fill");
        }

        @Test
        @DisplayName("none fit can be set")
        void noneFitCanBeSet() {
            ImageSurface image = ImageSurface.of("icon").fit("none");

            assertThat(image.fit()).isEqualTo("none");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("renders with fit set")
        void rendersWithFit(String rendererName, SurfaceRenderer renderer) {
            ImageSurface image = ImageSurface.of("🖼️").fit("cover");

            RenderResult result = RenderResult.capture(image, renderer);

            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("🖼️");
        }
    }

    // ==================================================================================
    // Image/Solid ContentID
    // ==================================================================================

    @Nested
    @DisplayName("Image and Solid")
    class ImageAndSolid {

        @Test
        @DisplayName("hasImage returns false when no image set")
        void hasImageFalseWhenNotSet() {
            ImageSurface image = ImageSurface.of("alt");

            assertThat(image.hasImage()).isFalse();
        }

        @Test
        @DisplayName("hasSolid returns false when no solid set")
        void hasSolidFalseWhenNotSet() {
            ImageSurface image = ImageSurface.of("alt");

            assertThat(image.hasSolid()).isFalse();
        }

        // TODO: Test with actual ContentID when content store is available
        // For now, text renderers always use alt text, so these tests
        // would just verify alt text rendering which is covered above.
    }

    // ==================================================================================
    // Fluent API
    // ==================================================================================

    @Nested
    @DisplayName("Fluent API")
    class FluentApi {

        @Test
        @DisplayName("fluent methods return same instance")
        void fluentMethodsReturnSameInstance() {
            ImageSurface image = ImageSurface.of("icon");

            assertThat(image.alt("new alt")).isSameAs(image);
            assertThat(image.size("large")).isSameAs(image);
            assertThat(image.fit("cover")).isSameAs(image);
            assertThat(image.small()).isSameAs(image);
            assertThat(image.medium()).isSameAs(image);
            assertThat(image.large()).isSameAs(image);
        }

        @Test
        @DisplayName("alt can be changed after creation")
        void altCanBeChanged() {
            ImageSurface image = ImageSurface.of("original").alt("changed");

            assertThat(image.alt()).isEqualTo("changed");
        }
    }

    // ==================================================================================
    // TUI-Specific
    // ==================================================================================

    @Nested
    @DisplayName("TUI Renderer Specific")
    class TuiSpecific {

        @Test
        @DisplayName("emoji alt renders in TUI")
        void emojiAltRendersInTui() {
            ImageSurface image = ImageSurface.of("🎮");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            image.render(renderer);
            String output = renderer.result();

            assertThat(output).contains("🎮");
        }
    }

    // ==================================================================================
    // CLI-Specific
    // ==================================================================================

    @Nested
    @DisplayName("CLI Renderer Specific")
    class CliSpecific {

        @Test
        @DisplayName("image output has no ANSI codes")
        void imageOutputNoAnsi() {
            ImageSurface image = ImageSurface.of("📁");

            CliSurfaceRenderer renderer = new CliSurfaceRenderer();
            image.render(renderer);
            String output = renderer.result();

            CliAssertions.assertNoAnsi(
                new RenderResult.TextResult(output, RendererProvider.RendererType.CLI));
        }

        @Test
        @DisplayName("plain text alt renders correctly")
        void plainTextAltRenders() {
            ImageSurface image = ImageSurface.of("User avatar");

            CliSurfaceRenderer renderer = new CliSurfaceRenderer();
            image.render(renderer);
            String output = renderer.result();

            assertThat(output).contains("User avatar");
        }
    }

    // ==================================================================================
    // Combined Options
    // ==================================================================================

    @Nested
    @DisplayName("Combined Options")
    class CombinedOptions {

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("renders with all options set")
        void rendersWithAllOptions(String rendererName, SurfaceRenderer renderer) {
            ImageSurface image = ImageSurface.of("🖼️")
                .size("large")
                .fit("cover");

            RenderResult result = RenderResult.capture(image, renderer);

            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("🖼️");
        }
    }
}
