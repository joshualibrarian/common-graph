package dev.everydaythings.graph.ui.skia;

import dev.everydaythings.graph.ui.scene.surface.primitive.ContainerSurface;
import dev.everydaythings.graph.ui.scene.surface.primitive.ImageSurface;
import dev.everydaythings.graph.ui.scene.surface.primitive.TextSurface;

/**
 * Visual showcase: opens a Skia window displaying chess piece SVG assets.
 *
 * <p>Run with: {@code ./gradlew :ui:chessPieces}
 *
 * <p>Press Escape to close.
 */
public class ChessPieceShowcase {

    private static final String[] PIECE_NAMES = {"King", "Queen", "Rook", "Bishop", "Knight", "Pawn"};
    private static final String[] WHITE_SYMBOLS = {"♔", "♕", "♖", "♗", "♘", "♙"};
    private static final String[] BLACK_SYMBOLS = {"♚", "♛", "♜", "♝", "♞", "♟"};

    static ContainerSurface buildSurface() {
        ContainerSurface root = ContainerSurface.vertical().gap("1.5em");
        root.add(TextSurface.of("Chess Piece Showcase").style("heading"));

        // White pieces
        root.add(TextSurface.of("White Pieces (light maple wood)").style("bold"));
        ContainerSurface whiteRow = ContainerSurface.horizontal().gap("1.5em");
        for (int i = 0; i < 6; i++) {
            ContainerSurface cell = ContainerSurface.vertical().gap("0.25em");
            cell.add(ImageSurface.ofResource("chess/pieces/w_" + PIECE_NAMES[i].toLowerCase() + ".svg", WHITE_SYMBOLS[i])
                    .size("80px"));
            cell.add(TextSurface.of(PIECE_NAMES[i]).style("muted"));
            whiteRow.add(cell);
        }
        root.add(whiteRow);

        // Black pieces
        root.add(TextSurface.of("Black Pieces (black walnut wood)").style("bold"));
        ContainerSurface blackRow = ContainerSurface.horizontal().gap("1.5em");
        for (int i = 0; i < 6; i++) {
            ContainerSurface cell = ContainerSurface.vertical().gap("0.25em");
            cell.add(ImageSurface.ofResource("chess/pieces/b_" + PIECE_NAMES[i].toLowerCase() + ".svg", BLACK_SYMBOLS[i])
                    .size("80px"));
            cell.add(TextSurface.of(PIECE_NAMES[i]).style("muted"));
            blackRow.add(cell);
        }
        root.add(blackRow);

        root.add(TextSurface.of("SVGs: cburnett (GPLv2+) via Lichess  |  GLBs: Staunton STL (MIT) + ambientCG wood (CC0)").style("muted"));

        return root;
    }

    public static void main(String[] args) {
        ContainerSurface surface = buildSurface();

        // Build layout tree
        SkiaSurfaceRenderer renderer = new SkiaSurfaceRenderer();
        surface.render(renderer);
        LayoutNode.BoxNode layoutRoot = renderer.result();

        // Set up rendering pipeline
        FontCache fontCache = new FontCache(new dev.everydaythings.graph.ui.text.FontRegistry());
        LayoutEngine engine = new LayoutEngine(fontCache);
        SkiaPainter painter = new SkiaPainter(fontCache);

        // Open window
        SkiaWindow window = new SkiaWindow();
        window.init("Chess Pieces");

        window.onPaint(canvas -> {
            engine.layout(layoutRoot, window.width(), window.height());
            painter.paint(canvas, layoutRoot);
        });

        window.onKey((key, scancode, action, mods) -> {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && action == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
                org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose(window.handle(), true);
            }
        });

        window.runLoop();
        window.destroy();
    }
}
