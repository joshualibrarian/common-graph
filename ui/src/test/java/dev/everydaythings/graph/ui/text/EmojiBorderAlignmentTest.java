package dev.everydaythings.graph.ui.text;

import dev.everydaythings.graph.ui.scene.BoxBorder;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.surface.HandleSurface;
import dev.everydaythings.graph.ui.scene.surface.primitive.ContainerSurface;
import dev.everydaythings.graph.ui.scene.surface.tree.TreeSurface;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproduce the chess item border alignment issue.
 *
 * When a tree contains items with different emoji types (e.g., chess pawn ♟️),
 * the border on that line can be misaligned by 1 character.
 */
@DisplayName("Emoji Border Alignment")
class EmojiBorderAlignmentTest {

    private static final String ANSI_PATTERN = "\u001B\\[[0-9;]*m";

    private static String stripAnsi(String s) {
        return s.replaceAll(ANSI_PATTERN, "");
    }

    @Test
    @DisplayName("tree with chess emoji has aligned borders")
    void chessBorderAlignment() {
        // Build a tree like the real app: different emoji icons for each node
        TreeSurface tree = TreeSurface.of(List.of(
                TreeSurface.Node.of("vault", HandleSurface.of("\uD83D\uDD12", "vault"))
                        .expandable(true).expanded(true)
                        .addChild(TreeSurface.Node.of("chess",
                                HandleSurface.of("♟\uFE0F", "chess"))
                                .expandable(true))
                        .addChild(TreeSurface.Node.of("keys",
                                HandleSurface.of("\uD83D\uDD11", "keys")))
        ));

        // Render inside a bordered container (like the tree panel)
        ContainerSurface panel = ContainerSurface.vertical();
        panel.border("1px solid");
        // Manually render: panel wraps tree
        TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();

        // Render tree inside bordered panel
        renderer.beginBox(Scene.Direction.VERTICAL, List.of("panel"),
                BoxBorder.parse("1px solid"), null, null, null, null);
        tree.render(renderer);
        renderer.endBox();

        String output = renderer.result();
        String[] lines = output.split("\n");

        System.out.println("=== Full output ===");
        for (String line : lines) {
            String stripped = stripAnsi(line);
            int charLen = stripped.length();
            int dispWidth = BoxDrawing.stripAnsiLength(line);
            System.out.printf("charLen=%2d dispWidth=%2d text=\"%s\"%n",
                    charLen, dispWidth, stripped);
            // Print code points for diagnosis
            StringBuilder hex = new StringBuilder("  codepoints: ");
            for (int i = 0; i < stripped.length(); ) {
                int cp = stripped.codePointAt(i);
                hex.append(String.format("U+%04X(%s) ", cp,
                        Character.charCount(cp) > 1 ? "surr" : String.valueOf((char) cp)));
                i += Character.charCount(cp);
            }
            System.out.println(hex);
        }

        // Find content lines (those with left/right borders)
        // All content lines between top and bottom border should have the same display width
        int expectedWidth = -1;
        for (int i = 0; i < lines.length; i++) {
            String stripped = stripAnsi(lines[i]);
            int width = BoxDrawing.stripAnsiLength(lines[i]);
            if (i > 0 && i < lines.length - 1) {
                // Content line — should all be same width
                if (expectedWidth == -1) {
                    expectedWidth = width;
                }
                assertThat(width)
                        .as("Line %d should have same display width as line 1: \"%s\"",
                                i, stripped)
                        .isEqualTo(expectedWidth);
            }
        }
    }

    @Test
    @DisplayName("simple bordered box with chess emoji matches border")
    void simpleBorderedChessEmoji() {
        // Even simpler: just a horizontal row with emoji + text in a bordered box
        TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
        renderer.beginBox(Scene.Direction.VERTICAL, List.of("panel"),
                BoxBorder.parse("1px solid"), null, null, null, null);

        // Horizontal row 1: regular emoji
        renderer.beginBox(Scene.Direction.HORIZONTAL, List.of("row"));
        renderer.image("\uD83D\uDD12", null, null, null, null, List.of("icon"));
        renderer.text("vault", List.of("label"));
        renderer.endBox();

        // Horizontal row 2: chess pawn with VS16
        renderer.beginBox(Scene.Direction.HORIZONTAL, List.of("row"));
        renderer.image("♟\uFE0F", null, null, null, null, List.of("icon"));
        renderer.text("chess", List.of("label"));
        renderer.endBox();

        // Horizontal row 3: another regular emoji
        renderer.beginBox(Scene.Direction.HORIZONTAL, List.of("row"));
        renderer.image("\uD83D\uDD11", null, null, null, null, List.of("icon"));
        renderer.text("keys", List.of("label"));
        renderer.endBox();

        renderer.endBox();

        String output = renderer.result();
        String[] lines = output.split("\n");

        System.out.println("\n=== Simple bordered box output ===");
        for (String line : lines) {
            String stripped = stripAnsi(line);
            int charLen = stripped.length();
            int dispWidth = BoxDrawing.stripAnsiLength(line);
            System.out.printf("charLen=%2d dispWidth=%2d text=\"%s\"%n",
                    charLen, dispWidth, stripped);
        }

        // All lines should have same display width
        int expectedWidth = BoxDrawing.stripAnsiLength(lines[0]);
        for (int i = 1; i < lines.length; i++) {
            int width = BoxDrawing.stripAnsiLength(lines[i]);
            String stripped = stripAnsi(lines[i]);
            assertThat(width)
                    .as("Line %d should have same display width: \"%s\"", i, stripped)
                    .isEqualTo(expectedWidth);
        }
    }

    @Test
    @DisplayName("HandleSurface via SceneCompiler in bordered box")
    void handleSurfaceViaCompiler() {
        // Use HandleSurface.render() which goes through SceneCompiler (like real app)
        TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
        renderer.beginBox(Scene.Direction.VERTICAL, List.of("panel"),
                BoxBorder.parse("1px solid"), null, null, null, null);

        // Row 1: HandleSurface with lock emoji (with circle/bg like real app)
        renderer.beginBox(Scene.Direction.HORIZONTAL, List.of("node"));
        HandleSurface.of(
                dev.everydaythings.graph.ui.scene.surface.primitive.ImageSurface
                        .of("\uD83D\uDD12").size("medium").circle().backgroundColor("#3C3C4E"),
                "vault").render(renderer);
        renderer.endBox();

        // Row 2: HandleSurface with chess pawn (with circle/bg like real app)
        renderer.beginBox(Scene.Direction.HORIZONTAL, List.of("node"));
        HandleSurface.of(
                dev.everydaythings.graph.ui.scene.surface.primitive.ImageSurface
                        .of("♟\uFE0F").size("medium").circle().backgroundColor("#3C3C4E"),
                "chess").render(renderer);
        renderer.endBox();

        // Row 3: HandleSurface with key emoji (with circle/bg like real app)
        renderer.beginBox(Scene.Direction.HORIZONTAL, List.of("node"));
        HandleSurface.of(
                dev.everydaythings.graph.ui.scene.surface.primitive.ImageSurface
                        .of("\uD83D\uDD11").size("medium").circle().backgroundColor("#3C3C4E"),
                "keys").render(renderer);
        renderer.endBox();

        renderer.endBox();

        String output = renderer.result();
        String[] lines = output.split("\n");

        System.out.println("\n=== HandleSurface via SceneCompiler ===");
        for (String line : lines) {
            String stripped = stripAnsi(line);
            int charLen = stripped.length();
            int dispWidth = BoxDrawing.stripAnsiLength(line);
            System.out.printf("charLen=%2d dispWidth=%2d text=\"%s\"%n",
                    charLen, dispWidth, stripped);
        }

        // All lines should have same display width
        int expectedWidth = BoxDrawing.stripAnsiLength(lines[0]);
        for (int i = 1; i < lines.length; i++) {
            int width = BoxDrawing.stripAnsiLength(lines[i]);
            String stripped = stripAnsi(lines[i]);
            assertThat(width)
                    .as("Line %d should have same display width: \"%s\"", i, stripped)
                    .isEqualTo(expectedWidth);
        }
    }

    @Test
    @DisplayName("stripAnsiLength matches for different emoji types")
    void widthConsistency() {
        // Chess pawn with VS16 — VTE terminals render as 1 column (don't honor VS16)
        String chessPawn = "♟\uFE0F";
        assertThat(BoxDrawing.stripAnsiLength(chessPawn))
                .as("♟️ (U+265F + U+FE0F): VS16 is zero-width, pawn is 1 col")
                .isEqualTo(1);

        // Lock (supplementary, has Emoji_Presentation) → 2 columns
        String lock = "\uD83D\uDD12";
        assertThat(BoxDrawing.stripAnsiLength(lock)).isEqualTo(2);

        // Key (supplementary, has Emoji_Presentation) → 2 columns
        String key = "\uD83D\uDD11";
        assertThat(BoxDrawing.stripAnsiLength(key)).isEqualTo(2);

        // Full strings
        assertThat(BoxDrawing.stripAnsiLength(chessPawn + " chess"))
                .as("♟️ chess: 1 + 0 + 1 + 5 = 7")
                .isEqualTo(7);
        assertThat(BoxDrawing.stripAnsiLength(lock + " vault"))
                .as("🔒 vault: 2 + 1 + 5 = 8")
                .isEqualTo(8);
        assertThat(BoxDrawing.stripAnsiLength(key + " keys"))
                .as("🔑 keys: 2 + 1 + 4 = 7")
                .isEqualTo(7);
    }

    @Test
    @DisplayName("only Emoji_Presentation characters are 2 columns wide")
    void widthByEmojiPresentation() {
        // Characters with Emoji_Presentation=true → 2 columns
        assertThat(BoxDrawing.codePointWidth(0x1F512))
                .as("🔒 (U+1F512, Emoji_Presentation=true)")
                .isEqualTo(2);
        assertThat(BoxDrawing.codePointWidth(0x1F511))
                .as("🔑 (U+1F511, Emoji_Presentation=true)")
                .isEqualTo(2);

        // Characters with Emoji=true but NOT Emoji_Presentation → 1 column
        // VTE-based terminals render these as narrow text glyphs
        assertThat(BoxDrawing.codePointWidth(0x265F))
                .as("♟ (U+265F, Emoji=true, Emoji_Presentation=false)")
                .isEqualTo(1);
        assertThat(BoxDrawing.codePointWidth(0x25B6))
                .as("▶ (U+25B6, Emoji=true, Emoji_Presentation=false)")
                .isEqualTo(1);
        assertThat(BoxDrawing.codePointWidth(0x25C0))
                .as("◀ (U+25C0, Emoji=true, Emoji_Presentation=false)")
                .isEqualTo(1);
        assertThat(BoxDrawing.codePointWidth(0x25BC))
                .as("▼ (U+25BC, no Emoji)")
                .isEqualTo(1);

        // Box-drawing and block elements → 1 column
        assertThat(BoxDrawing.codePointWidth(0x2500)).as("─").isEqualTo(1);
        assertThat(BoxDrawing.codePointWidth(0x251C)).as("├").isEqualTo(1);
        assertThat(BoxDrawing.codePointWidth(0x258F)).as("▏").isEqualTo(1);

        // VS16 (U+FE0F) → 0 columns (variation selector)
        assertThat(BoxDrawing.codePointWidth(0xFE0F)).as("VS16").isEqualTo(0);
    }
}
