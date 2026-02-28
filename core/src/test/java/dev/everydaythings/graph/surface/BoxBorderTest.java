package dev.everydaythings.graph.surface;

import dev.everydaythings.graph.ui.scene.BoxBorder;
import dev.everydaythings.graph.ui.scene.BoxBorder.BorderSide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for BoxBorder CSS shorthand parsing, per-side construction,
 * resolution with overrides, and the builder pattern.
 */
@DisplayName("BoxBorder")
class BoxBorderTest {

    // ==================================================================================
    // BorderSide Parsing
    // ==================================================================================

    @Nested
    @DisplayName("BorderSide.parse")
    class BorderSideParsing {

        @Test
        @DisplayName("parses full shorthand: width style color")
        void parsesFullShorthand() {
            BorderSide side = BorderSide.parse("1px solid blue");

            assertThat(side.width()).isEqualTo("1px");
            assertThat(side.style()).isEqualTo("solid");
            assertThat(side.color()).isEqualTo("blue");
        }

        @Test
        @DisplayName("parses width and style without color")
        void parsesWidthAndStyle() {
            BorderSide side = BorderSide.parse("2px dashed");

            assertThat(side.width()).isEqualTo("2px");
            assertThat(side.style()).isEqualTo("dashed");
            assertThat(side.color()).isNull();
        }

        @Test
        @DisplayName("parses style only, defaults width to 1px")
        void parsesStyleOnly() {
            BorderSide side = BorderSide.parse("solid");

            assertThat(side.style()).isEqualTo("solid");
            assertThat(side.width()).isEqualTo("1px");
            assertThat(side.color()).isNull();
        }

        @Test
        @DisplayName("parses 'none' to NONE")
        void parsesNoneKeyword() {
            BorderSide side = BorderSide.parse("none");

            assertThat(side).isEqualTo(BorderSide.NONE);
            assertThat(side.style()).isEqualTo("none");
            assertThat(side.width()).isEqualTo("0");
        }

        @Test
        @DisplayName("parses 'NONE' case-insensitively")
        void parsesNoneCaseInsensitive() {
            BorderSide side = BorderSide.parse("NONE");

            assertThat(side).isEqualTo(BorderSide.NONE);
        }

        @Test
        @DisplayName("null input returns NONE")
        void nullReturnsNone() {
            assertThat(BorderSide.parse(null)).isEqualTo(BorderSide.NONE);
        }

        @Test
        @DisplayName("blank input returns NONE")
        void blankReturnsNone() {
            assertThat(BorderSide.parse("   ")).isEqualTo(BorderSide.NONE);
        }

        @Test
        @DisplayName("empty input returns NONE")
        void emptyReturnsNone() {
            assertThat(BorderSide.parse("")).isEqualTo(BorderSide.NONE);
        }

        @Test
        @DisplayName("parses double style")
        void parsesDoubleStyle() {
            BorderSide side = BorderSide.parse("1px double red");

            assertThat(side.style()).isEqualTo("double");
            assertThat(side.width()).isEqualTo("1px");
            assertThat(side.color()).isEqualTo("red");
        }

        @Test
        @DisplayName("parses dotted style")
        void parsesDottedStyle() {
            BorderSide side = BorderSide.parse("dotted");

            assertThat(side.style()).isEqualTo("dotted");
        }

        @Test
        @DisplayName("tokens in any order: color first")
        void tokensInAnyOrder() {
            BorderSide side = BorderSide.parse("red 2px dashed");

            assertThat(side.color()).isEqualTo("red");
            assertThat(side.width()).isEqualTo("2px");
            assertThat(side.style()).isEqualTo("dashed");
        }

        @Test
        @DisplayName("parses decimal width like 0.5em")
        void parsesDecimalWidth() {
            BorderSide side = BorderSide.parse("0.5em solid");

            assertThat(side.width()).isEqualTo("0.5em");
            assertThat(side.style()).isEqualTo("solid");
        }

        @Test
        @DisplayName("parses ch unit")
        void parsesChUnit() {
            BorderSide side = BorderSide.parse("2ch solid green");

            assertThat(side.width()).isEqualTo("2ch");
            assertThat(side.style()).isEqualTo("solid");
            assertThat(side.color()).isEqualTo("green");
        }

        @Test
        @DisplayName("parses hex color")
        void parsesHexColor() {
            BorderSide side = BorderSide.parse("1px solid #333");

            assertThat(side.color()).isEqualTo("#333");
        }

        // --- multi-unit width parsing ---

        @Test
        @DisplayName("parses em width")
        void parsesEmWidth() {
            BorderSide side = BorderSide.parse("1em solid blue");

            assertThat(side.width()).isEqualTo("1em");
            assertThat(side.style()).isEqualTo("solid");
            assertThat(side.color()).isEqualTo("blue");
        }

        @Test
        @DisplayName("parses rem width")
        void parsesRemWidth() {
            BorderSide side = BorderSide.parse("0.5rem dashed red");

            assertThat(side.width()).isEqualTo("0.5rem");
            assertThat(side.style()).isEqualTo("dashed");
            assertThat(side.color()).isEqualTo("red");
        }

        @Test
        @DisplayName("parses ln width")
        void parsesLnWidth() {
            BorderSide side = BorderSide.parse("1ln solid green");

            assertThat(side.width()).isEqualTo("1ln");
            assertThat(side.style()).isEqualTo("solid");
            assertThat(side.color()).isEqualTo("green");
        }

        @Test
        @DisplayName("parses fractional ch width")
        void parsesFractionalChWidth() {
            BorderSide side = BorderSide.parse("0.1ch solid");

            assertThat(side.width()).isEqualTo("0.1ch");
            assertThat(side.style()).isEqualTo("solid");
        }

        @Test
        @DisplayName("parses fractional em width")
        void parsesFractionalEmWidth() {
            BorderSide side = BorderSide.parse("0.25em dashed cyan");

            assertThat(side.width()).isEqualTo("0.25em");
            assertThat(side.style()).isEqualTo("dashed");
            assertThat(side.color()).isEqualTo("cyan");
        }
    }

    // ==================================================================================
    // BorderSide.isVisible
    // ==================================================================================

    @Nested
    @DisplayName("BorderSide.isVisible")
    class BorderSideVisibility {

        @Test
        @DisplayName("NONE is not visible")
        void noneIsNotVisible() {
            assertThat(BorderSide.NONE.isVisible()).isFalse();
        }

        @Test
        @DisplayName("solid 1px is visible")
        void solidIsVisible() {
            BorderSide side = BorderSide.parse("1px solid blue");
            assertThat(side.isVisible()).isTrue();
        }

        @Test
        @DisplayName("style='none' is not visible even with width")
        void styleNoneIsNotVisible() {
            BorderSide side = new BorderSide("none", "1px", "blue");
            assertThat(side.isVisible()).isFalse();
        }

        @Test
        @DisplayName("width='0' is not visible even with style")
        void widthZeroIsNotVisible() {
            BorderSide side = new BorderSide("solid", "0", "blue");
            assertThat(side.isVisible()).isFalse();
        }

        @Test
        @DisplayName("null style is not visible")
        void nullStyleIsNotVisible() {
            BorderSide side = new BorderSide(null, "1px", "blue");
            assertThat(side.isVisible()).isFalse();
        }
    }

    // ==================================================================================
    // BoxBorder.parse
    // ==================================================================================

    @Nested
    @DisplayName("BoxBorder.parse")
    class BoxBorderParsing {

        @Test
        @DisplayName("parse shorthand sets all sides the same")
        void parseShorthandAllSides() {
            BoxBorder border = BoxBorder.parse("1px solid blue");

            assertThat(border.top().width()).isEqualTo("1px");
            assertThat(border.top().style()).isEqualTo("solid");
            assertThat(border.top().color()).isEqualTo("blue");

            // All sides should be identical
            assertThat(border.right()).isEqualTo(border.top());
            assertThat(border.bottom()).isEqualTo(border.top());
            assertThat(border.left()).isEqualTo(border.top());
        }

        @Test
        @DisplayName("parse 'none' returns NONE")
        void parseNoneReturnsNone() {
            assertThat(BoxBorder.parse("none")).isEqualTo(BoxBorder.NONE);
        }

        @Test
        @DisplayName("parse null returns NONE")
        void parseNullReturnsNone() {
            assertThat(BoxBorder.parse(null)).isEqualTo(BoxBorder.NONE);
        }

        @Test
        @DisplayName("parse blank returns NONE")
        void parseBlankReturnsNone() {
            assertThat(BoxBorder.parse("  ")).isEqualTo(BoxBorder.NONE);
        }

        @Test
        @DisplayName("parse with radius")
        void parseWithRadius() {
            BoxBorder border = BoxBorder.parse("1px solid", "4px");

            assertThat(border.radius()).isEqualTo("4px");
            assertThat(border.hasRadius()).isTrue();
            assertThat(border.top().style()).isEqualTo("solid");
        }

        @Test
        @DisplayName("parse without radius has radius 'none'")
        void parseWithoutRadius() {
            BoxBorder border = BoxBorder.parse("1px solid");

            assertThat(border.radius()).isEqualTo("none");
            assertThat(border.hasRadius()).isFalse();
        }
    }

    // ==================================================================================
    // BoxBorder.of (per-side)
    // ==================================================================================

    @Nested
    @DisplayName("BoxBorder.of (per-side)")
    class BoxBorderPerSide {

        @Test
        @DisplayName("different sides parse independently")
        void differentSidesIndependent() {
            BoxBorder border = BoxBorder.of(
                    "2px solid red",
                    "1px dashed blue",
                    "3px double green",
                    "1px dotted yellow",
                    "4px"
            );

            assertThat(border.top().width()).isEqualTo("2px");
            assertThat(border.top().style()).isEqualTo("solid");
            assertThat(border.top().color()).isEqualTo("red");

            assertThat(border.right().width()).isEqualTo("1px");
            assertThat(border.right().style()).isEqualTo("dashed");
            assertThat(border.right().color()).isEqualTo("blue");

            assertThat(border.bottom().width()).isEqualTo("3px");
            assertThat(border.bottom().style()).isEqualTo("double");
            assertThat(border.bottom().color()).isEqualTo("green");

            assertThat(border.left().width()).isEqualTo("1px");
            assertThat(border.left().style()).isEqualTo("dotted");
            assertThat(border.left().color()).isEqualTo("yellow");

            assertThat(border.radius()).isEqualTo("4px");
        }

        @Test
        @DisplayName("null sides default to NONE")
        void nullSidesDefaultNone() {
            BoxBorder border = BoxBorder.of("1px solid", null, null, null, null);

            assertThat(border.top().isVisible()).isTrue();
            assertThat(border.right()).isEqualTo(BorderSide.NONE);
            assertThat(border.bottom()).isEqualTo(BorderSide.NONE);
            assertThat(border.left()).isEqualTo(BorderSide.NONE);
            assertThat(border.radius()).isEqualTo("none");
        }

        @Test
        @DisplayName("all null sides and radius")
        void allNull() {
            BoxBorder border = BoxBorder.of(null, null, null, null, null);

            assertThat(border.top()).isEqualTo(BorderSide.NONE);
            assertThat(border.right()).isEqualTo(BorderSide.NONE);
            assertThat(border.bottom()).isEqualTo(BorderSide.NONE);
            assertThat(border.left()).isEqualTo(BorderSide.NONE);
            assertThat(border.radius()).isEqualTo("none");
        }
    }

    // ==================================================================================
    // BoxBorder.resolve
    // ==================================================================================

    @Nested
    @DisplayName("BoxBorder.resolve")
    class BoxBorderResolve {

        @Test
        @DisplayName("'all' is base for all sides")
        void allIsBase() {
            BoxBorder border = BoxBorder.resolve(
                    "1px solid blue",   // all
                    null, null, null, null,  // per-side
                    null, null, null,   // width/style/color
                    null                // radius
            );

            assertThat(border.top().width()).isEqualTo("1px");
            assertThat(border.top().style()).isEqualTo("solid");
            assertThat(border.top().color()).isEqualTo("blue");
            assertThat(border.right()).isEqualTo(border.top());
            assertThat(border.bottom()).isEqualTo(border.top());
            assertThat(border.left()).isEqualTo(border.top());
        }

        @Test
        @DisplayName("per-side overrides 'all'")
        void perSideOverridesAll() {
            BoxBorder border = BoxBorder.resolve(
                    "1px solid blue",       // all
                    "2px dashed red",       // top override
                    null,                   // right (inherits all)
                    "3px double green",     // bottom override
                    null,                   // left (inherits all)
                    null, null, null,       // width/style/color
                    null                    // radius
            );

            // Top overridden
            assertThat(border.top().width()).isEqualTo("2px");
            assertThat(border.top().style()).isEqualTo("dashed");
            assertThat(border.top().color()).isEqualTo("red");

            // Right inherits from all
            assertThat(border.right().width()).isEqualTo("1px");
            assertThat(border.right().style()).isEqualTo("solid");
            assertThat(border.right().color()).isEqualTo("blue");

            // Bottom overridden
            assertThat(border.bottom().width()).isEqualTo("3px");
            assertThat(border.bottom().style()).isEqualTo("double");
            assertThat(border.bottom().color()).isEqualTo("green");

            // Left inherits from all
            assertThat(border.left()).isEqualTo(border.right());
        }

        @Test
        @DisplayName("width/style/color shorthands apply to base")
        void widthStyleColorShorthands() {
            BoxBorder border = BoxBorder.resolve(
                    null, null, null, null, null,   // no all or per-side
                    "2px", "dashed", "red",         // width/style/color
                    null                             // radius
            );

            assertThat(border.top().width()).isEqualTo("2px");
            assertThat(border.top().style()).isEqualTo("dashed");
            assertThat(border.top().color()).isEqualTo("red");
            assertThat(border.right()).isEqualTo(border.top());
        }

        @Test
        @DisplayName("width/style/color modify 'all' base")
        void widthStyleColorModifyAll() {
            BoxBorder border = BoxBorder.resolve(
                    "1px solid blue",               // all
                    null, null, null, null,          // per-side
                    "3px", null, "green",            // override width and color
                    null                             // radius
            );

            // Width and color overridden, style kept from 'all'
            assertThat(border.top().width()).isEqualTo("3px");
            assertThat(border.top().style()).isEqualTo("solid");
            assertThat(border.top().color()).isEqualTo("green");
        }

        @Test
        @DisplayName("radius is set when provided")
        void radiusIsSet() {
            BoxBorder border = BoxBorder.resolve(
                    "1px solid", null, null, null, null,
                    null, null, null,
                    "8px"
            );

            assertThat(border.radius()).isEqualTo("8px");
            assertThat(border.hasRadius()).isTrue();
        }

        @Test
        @DisplayName("radius defaults to 'none' when not provided")
        void radiusDefaultsNone() {
            BoxBorder border = BoxBorder.resolve(
                    "1px solid", null, null, null, null,
                    null, null, null,
                    null
            );

            assertThat(border.radius()).isEqualTo("none");
            assertThat(border.hasRadius()).isFalse();
        }
    }

    // ==================================================================================
    // BoxBorder.builder
    // ==================================================================================

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("builder with all() sets all sides")
        void builderAll() {
            BoxBorder border = BoxBorder.builder()
                    .all("1px solid blue")
                    .build();

            assertThat(border.top().width()).isEqualTo("1px");
            assertThat(border.top().style()).isEqualTo("solid");
            assertThat(border.top().color()).isEqualTo("blue");
            assertThat(border.right()).isEqualTo(border.top());
            assertThat(border.bottom()).isEqualTo(border.top());
            assertThat(border.left()).isEqualTo(border.top());
        }

        @Test
        @DisplayName("builder with per-side overrides")
        void builderPerSideOverrides() {
            BoxBorder border = BoxBorder.builder()
                    .all("1px solid")
                    .top("2px solid red")
                    .radius("4px")
                    .build();

            assertThat(border.top().width()).isEqualTo("2px");
            assertThat(border.top().color()).isEqualTo("red");

            // Other sides inherit from all
            assertThat(border.right().width()).isEqualTo("1px");
            assertThat(border.left().width()).isEqualTo("1px");
            assertThat(border.bottom().width()).isEqualTo("1px");

            assertThat(border.radius()).isEqualTo("4px");
            assertThat(border.hasRadius()).isTrue();
        }

        @Test
        @DisplayName("builder with width/style/color shorthands")
        void builderWidthStyleColor() {
            BoxBorder border = BoxBorder.builder()
                    .width("3px")
                    .style("dashed")
                    .color("cyan")
                    .build();

            assertThat(border.top().width()).isEqualTo("3px");
            assertThat(border.top().style()).isEqualTo("dashed");
            assertThat(border.top().color()).isEqualTo("cyan");
        }

        @Test
        @DisplayName("builder with all four sides different")
        void builderFourSides() {
            BoxBorder border = BoxBorder.builder()
                    .top("1px solid red")
                    .right("2px dashed blue")
                    .bottom("3px double green")
                    .left("1px dotted yellow")
                    .build();

            assertThat(border.top().color()).isEqualTo("red");
            assertThat(border.right().style()).isEqualTo("dashed");
            assertThat(border.bottom().style()).isEqualTo("double");
            assertThat(border.left().style()).isEqualTo("dotted");
        }

        @Test
        @DisplayName("builder defaults radius to 'none'")
        void builderDefaultsRadiusNone() {
            BoxBorder border = BoxBorder.builder()
                    .all("1px solid")
                    .build();

            assertThat(border.radius()).isEqualTo("none");
            assertThat(border.hasRadius()).isFalse();
        }
    }

    // ==================================================================================
    // BoxBorder.isVisible / hasRadius
    // ==================================================================================

    @Nested
    @DisplayName("Visibility and Radius")
    class VisibilityAndRadius {

        @Test
        @DisplayName("NONE is not visible")
        void noneIsNotVisible() {
            assertThat(BoxBorder.NONE.isVisible()).isFalse();
        }

        @Test
        @DisplayName("parsed border is visible")
        void parsedBorderIsVisible() {
            BoxBorder border = BoxBorder.parse("1px solid blue");
            assertThat(border.isVisible()).isTrue();
        }

        @Test
        @DisplayName("border with at least one visible side is visible")
        void oneVisibleSideIsVisible() {
            BoxBorder border = BoxBorder.of(
                    "1px solid", null, null, null, null
            );
            assertThat(border.isVisible()).isTrue();
        }

        @Test
        @DisplayName("border with all NONE sides is not visible")
        void allNoneSidesNotVisible() {
            BoxBorder border = BoxBorder.of(null, null, null, null, null);
            assertThat(border.isVisible()).isFalse();
        }

        @Test
        @DisplayName("hasRadius with '4px' returns true")
        void hasRadiusWithValue() {
            BoxBorder border = BoxBorder.parse("1px solid", "4px");
            assertThat(border.hasRadius()).isTrue();
        }

        @Test
        @DisplayName("hasRadius with 'none' returns false")
        void hasRadiusWithNone() {
            BoxBorder border = BoxBorder.parse("1px solid", "none");
            assertThat(border.hasRadius()).isFalse();
        }

        @Test
        @DisplayName("hasRadius with '0' returns false")
        void hasRadiusWithZero() {
            BoxBorder border = BoxBorder.parse("1px solid", "0");
            assertThat(border.hasRadius()).isFalse();
        }

        @Test
        @DisplayName("hasRadius with null returns false")
        void hasRadiusWithNull() {
            BoxBorder border = new BoxBorder(
                    BorderSide.parse("1px solid"),
                    BorderSide.parse("1px solid"),
                    BorderSide.parse("1px solid"),
                    BorderSide.parse("1px solid"),
                    null
            );
            assertThat(border.hasRadius()).isFalse();
        }
    }
}
