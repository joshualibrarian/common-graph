package dev.everydaythings.graph.surface;

import dev.everydaythings.graph.ui.scene.surface.HandleSurface;
import dev.everydaythings.graph.ui.scene.surface.primitive.ImageSurface;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for HandleSurface factories, fields, and structure.
 */
@DisplayName("HandleSurface")
class HandleSurfaceTest {

    // ==================================================================================
    // Basic Factories
    // ==================================================================================

    @Nested
    @DisplayName("of() factory")
    class OfFactory {

        @Test
        @DisplayName("creates handle with glyph icon and label")
        void createsWithGlyphAndLabel() {
            HandleSurface h = HandleSurface.of("📁", "Documents");

            assertThat(h.icon()).isNotNull();
            assertThat(h.icon().alt()).isEqualTo("📁");
            assertThat(h.label()).isEqualTo("Documents");
            assertThat(h.hasBadges()).isFalse();
        }

        @Test
        @DisplayName("creates handle with ImageSurface icon and label")
        void createsWithImageAndLabel() {
            ImageSurface icon = ImageSurface.of("🔐");
            HandleSurface h = HandleSurface.of(icon, "Vault");

            assertThat(h.icon()).isSameAs(icon);
            assertThat(h.label()).isEqualTo("Vault");
        }

        @Test
        @DisplayName("ofLabel creates handle without icon")
        void ofLabelNoIcon() {
            HandleSurface h = HandleSurface.ofLabel("Untitled");

            assertThat(h.icon()).isNull();
            assertThat(h.label()).isEqualTo("Untitled");
        }
    }

    // ==================================================================================
    // Contextual Factories
    // ==================================================================================

    @Nested
    @DisplayName("contextual factories")
    class ContextualFactories {

        @Test
        @DisplayName("forNode adds node-handle style")
        void forNodeStyle() {
            HandleSurface h = HandleSurface.forNode("📄", "File");

            assertThat(h.icon().alt()).isEqualTo("📄");
            assertThat(h.label()).isEqualTo("File");
            assertThat(h.style()).contains("node-handle");
        }

        @Test
        @DisplayName("forHeader adds header-handle style")
        void forHeaderStyle() {
            HandleSurface h = HandleSurface.forHeader("📚", "Library");

            assertThat(h.icon().alt()).isEqualTo("📚");
            assertThat(h.label()).isEqualTo("Library");
            assertThat(h.style()).contains("header-handle");
        }

        @Test
        @DisplayName("forPrompt appends '> ' and adds prompt-handle style")
        void forPromptStyle() {
            HandleSurface h = HandleSurface.forPrompt("📚", "Library");

            assertThat(h.label()).isEqualTo("Library> ");
            assertThat(h.style()).contains("prompt-handle");
        }
    }

    // ==================================================================================
    // Subtitle
    // ==================================================================================

    @Nested
    @DisplayName("subtitle")
    class SubtitleTests {

        @Test
        @DisplayName("subtitle can be set via fluent setter")
        void subtitleSetter() {
            HandleSurface h = HandleSurface.of("🔐", "Vault")
                    .subtitle("3 keys");

            assertThat(h.subtitle()).isEqualTo("3 keys");
        }

        @Test
        @DisplayName("subtitle is null by default")
        void subtitleDefault() {
            HandleSurface h = HandleSurface.of("📁", "Docs");

            assertThat(h.subtitle()).isNull();
        }
    }

    // ==================================================================================
    // Badges
    // ==================================================================================

    @Nested
    @DisplayName("badges")
    class BadgeTests {

        @Test
        @DisplayName("hasBadges returns false when no badges")
        void noBadges() {
            HandleSurface h = HandleSurface.of("📁", "Docs");

            assertThat(h.hasBadges()).isFalse();
        }

        @Test
        @DisplayName("badge() adds a badge and hasBadges returns true")
        void addBadge() {
            HandleSurface h = HandleSurface.of("📁", "Docs")
                    .badge("✏️");

            assertThat(h.hasBadges()).isTrue();
            assertThat(h.badges()).hasSize(1);
        }

        @Test
        @DisplayName("multiple badges accumulate")
        void multipleBadges() {
            HandleSurface h = HandleSurface.of("📄", "README")
                    .badge("✏️")
                    .badge("🔒");

            assertThat(h.badges()).hasSize(2);
        }
    }

    // ==================================================================================
    // Fluent Chaining
    // ==================================================================================

    @Test
    @DisplayName("full fluent construction")
    void fluentChaining() {
        HandleSurface h = HandleSurface.of("📊", "Report")
                .subtitle("Q4 2025")
                .badge("🔴")
                .badge("📌");

        assertThat(h.icon().alt()).isEqualTo("📊");
        assertThat(h.label()).isEqualTo("Report");
        assertThat(h.subtitle()).isEqualTo("Q4 2025");
        assertThat(h.hasBadges()).isTrue();
        assertThat(h.badges()).hasSize(2);
    }
}
