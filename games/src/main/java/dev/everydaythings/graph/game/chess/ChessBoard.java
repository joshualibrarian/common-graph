package dev.everydaythings.graph.game.chess;

import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.Scene.Direction;

import java.util.List;

/**
 * Unified chess board: one class, all renderers.
 *
 * <p>Declares both 3D geometry ({@code @Scene.Body}) and 2D layout ({@code @Scene.Container}).
 * The rendering pipeline routes to the appropriate representation:
 * <ul>
 *   <li><b>Text/TUI</b>: unicode board via SurfaceRenderer</li>
 *   <li><b>2D (Skia)</b>: colored squares + piece images via LayoutEngine</li>
 *   <li><b>3D (Filament)</b>: {@code composeSurfaceOnBody()} → elevated boxes + GLB meshes</li>
 * </ul>
 *
 * <p>Embedded into {@link ChessGame} via {@code @Scene.Embed(bind = "value.chessBoard")}.
 *
 * @see ChessGame
 */
@Scene.Rule(match = ".square.light", background = "#F0D9B5")
@Scene.Rule(match = ".square.dark", background = "#B58863")
@Scene.Body(shape = "box", fontSize = "2.2cm", color = 0x8B4513)
@Scene.Container(id = "board-root", direction = Direction.VERTICAL, aspectRatio = "1")
public class ChessBoard {

    private final ChessGame game;

    public ChessBoard(ChessGame game) {
        this.game = game;
    }

    // Delegates for bind expressions
    public List<ChessGame.RankView> ranks() { return game.ranks(); }
    public List<String> fileLabels() { return game.fileLabels(); }

    // ==================================================================================
    // Surface Structure (2D board grid)
    // ==================================================================================

    @Scene.Container(direction = Direction.HORIZONTAL, style = "fill", width = "100%")
    static class TopBand {
        @Scene.Container(direction = Direction.VERTICAL, style = "fill")
        static class Left {}
        @Scene.Container(direction = Direction.VERTICAL, style = "fill")
        static class Center {}
        @Scene.Container(direction = Direction.VERTICAL, style = "fill")
        static class Right {}
    }

    @Scene.Repeat(bind = "value.ranks")
    @Scene.Container(direction = Direction.HORIZONTAL, style = "fill", width = "100%")
    static class Rank {

        @Scene.Container(direction = Direction.VERTICAL, style = {"fill", "align-center", "justify-center"})
        static class LeftLabel {
            @Scene.Text(bind = "$item.label", style = {"rank-label", "muted"})
            static class Text {}
        }

        @Scene.Repeat(bind = "$item.squares")
        @Scene.Container(id = "bind:$item.id", direction = Direction.VERTICAL,
                style = {"fill", "square", "justify-center"}, align = "center", depth = "1cm")
        @Scene.State(when = "$item.light", style = {"light"})
        @Scene.State(when = "!$item.light", style = {"dark"})
        @Scene.State(when = "$item.selected", style = {"selected"})
        @Scene.State(when = "$item.legalTarget", style = {"legal-target"})
        @Scene.On(event = "click", action = "select", target = "$item.id")
        static class Square {

            @Scene.If("$item.piece")
            @Scene.Image(bind = "$item.piece", size = "80%")
            static class PieceImg {}
        }

        @Scene.Container(direction = Direction.VERTICAL, style = "fill")
        static class RightGutter {}
    }

    @Scene.Container(direction = Direction.HORIZONTAL, style = "fill", width = "100%")
    static class FileLabels {

        @Scene.Container(direction = Direction.VERTICAL, style = "fill")
        static class LeftGutter {}

        @Scene.Repeat(bind = "value.fileLabels")
        @Scene.Container(direction = Direction.VERTICAL, style = {"fill", "align-center", "justify-center"})
        static class FileLabel {
            @Scene.Text(bind = "$item", style = {"file-label", "muted"})
            static class Text {}
        }

        @Scene.Container(direction = Direction.VERTICAL, style = "fill")
        static class RightGutter {}
    }
}
