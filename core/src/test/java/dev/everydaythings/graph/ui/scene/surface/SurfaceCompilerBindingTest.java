package dev.everydaythings.graph.ui.scene.surface;

import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.ui.scene.BoxBorder;
import dev.everydaythings.graph.ui.scene.SceneCompiler;
import dev.everydaythings.graph.ui.scene.surface.primitive.ContainerSurface;
import dev.everydaythings.graph.ui.scene.surface.primitive.ImageSurface;
import dev.everydaythings.graph.ui.scene.surface.primitive.TextSurface;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the dynamic binding infrastructure in SceneCompiler:
 * <ul>
 *   <li>BindingResolver property path resolution</li>
 *   <li>Map key access in the binding engine</li>
 *   <li>renderWithBindings() template resolution</li>
 *   <li>visibleBind truthiness checks</li>
 * </ul>
 */
class SurfaceCompilerBindingTest {

    // ==================================================================================
    // Test Data
    // ==================================================================================

    /** Simple value object with fluent accessors for binding tests. */
    static class GameState {
        private final String status;
        private final Map<String, PieceInfo> pieces;

        GameState(String status, Map<String, PieceInfo> pieces) {
            this.status = status;
            this.pieces = pieces;
        }

        public String status() { return status; }
        public Map<String, PieceInfo> pieces() { return pieces; }
    }

    static class PieceInfo {
        private final String symbol;
        private final String imageKey;

        PieceInfo(String symbol, String imageKey) {
            this.symbol = symbol;
            this.imageKey = imageKey;
        }

        public String symbol() { return symbol; }
        public String imageKey() { return imageKey; }
    }

    /** Root value object simulating a Chess component. */
    static class MockChess {
        private final GameState state;
        private final String statusLabel;

        MockChess(GameState state, String statusLabel) {
            this.state = state;
            this.statusLabel = statusLabel;
        }

        public GameState state() { return state; }
        public String statusLabel() { return statusLabel; }
    }

    // ==================================================================================
    // BindingResolver Tests
    // ==================================================================================

    @Test
    void resolver_valueReturnsRoot() {
        var resolver = new SceneCompiler.BindingResolver("hello");
        assertThat(resolver.resolve("value")).isEqualTo("hello");
    }

    @Test
    void resolver_valueProperty_resolvesFluentAccessor() {
        var chess = new MockChess(null, "WHITE to move");
        var resolver = new SceneCompiler.BindingResolver(chess);

        assertThat(resolver.resolve("value.statusLabel")).isEqualTo("WHITE to move");
    }

    @Test
    void resolver_shorthand_treatedAsValueProperty() {
        var chess = new MockChess(null, "BLACK to move");
        var resolver = new SceneCompiler.BindingResolver(chess);

        assertThat(resolver.resolve("statusLabel")).isEqualTo("BLACK to move");
    }

    @Test
    void resolver_nestedPropertyPath() {
        var pieces = Map.of("a1", new PieceInfo("♜", "chess/pieces/b_rook.svg"));
        var state = new GameState("active", pieces);
        var chess = new MockChess(state, "");
        var resolver = new SceneCompiler.BindingResolver(chess);

        assertThat(resolver.resolve("state.status")).isEqualTo("active");
    }

    @Test
    void resolver_mapKeyAccess() {
        var pieces = Map.of("a1", new PieceInfo("♜", "chess/pieces/b_rook.svg"));
        var state = new GameState("active", pieces);
        var chess = new MockChess(state, "");
        var resolver = new SceneCompiler.BindingResolver(chess);

        // state.pieces returns the Map, then "a1" accesses the key
        Object result = resolver.resolve("state.pieces.a1");
        assertThat(result).isInstanceOf(PieceInfo.class);
        assertThat(((PieceInfo) result).symbol()).isEqualTo("♜");
    }

    @Test
    void resolver_deepMapPath_accessesPropertyOnMapValue() {
        var pieces = Map.of("a1", new PieceInfo("♜", "chess/pieces/b_rook.svg"));
        var state = new GameState("active", pieces);
        var chess = new MockChess(state, "");
        var resolver = new SceneCompiler.BindingResolver(chess);

        // Full path: state.pieces.a1.symbol
        Object result = resolver.resolve("state.pieces.a1.symbol");
        assertThat(result).isEqualTo("♜");
    }

    @Test
    void resolver_missingMapKey_returnsNull() {
        var pieces = Map.of("a1", new PieceInfo("♜", "chess/pieces/b_rook.svg"));
        var state = new GameState("active", pieces);
        var chess = new MockChess(state, "");
        var resolver = new SceneCompiler.BindingResolver(chess);

        assertThat(resolver.resolve("state.pieces.e4")).isNull();
    }

    @Test
    void resolver_nullPath_returnsNull() {
        var resolver = new SceneCompiler.BindingResolver("anything");
        assertThat(resolver.resolve(null)).isNull();
        assertThat(resolver.resolve("")).isNull();
    }

    @Test
    void resolver_nullIntermediateValue_returnsNull() {
        var chess = new MockChess(null, "label");
        var resolver = new SceneCompiler.BindingResolver(chess);

        // state is null, so state.pieces should return null gracefully
        assertThat(resolver.resolve("state.pieces.a1")).isNull();
    }

    // ==================================================================================
    // Truthiness Tests
    // ==================================================================================

    @Test
    void isTruthy_nullIsFalse() {
        var resolver = new SceneCompiler.BindingResolver(null);
        assertThat(resolver.isTruthy(null)).isFalse();
    }

    @Test
    void isTruthy_booleanValues() {
        var resolver = new SceneCompiler.BindingResolver(null);
        assertThat(resolver.isTruthy(true)).isTrue();
        assertThat(resolver.isTruthy(false)).isFalse();
    }

    @Test
    void isTruthy_strings() {
        var resolver = new SceneCompiler.BindingResolver(null);
        assertThat(resolver.isTruthy("hello")).isTrue();
        assertThat(resolver.isTruthy("")).isFalse();
    }

    @Test
    void isTruthy_numbers() {
        var resolver = new SceneCompiler.BindingResolver(null);
        assertThat(resolver.isTruthy(42)).isTrue();
        assertThat(resolver.isTruthy(0)).isFalse();
        assertThat(resolver.isTruthy(0.0)).isFalse();
    }

    @Test
    void isTruthy_collections() {
        var resolver = new SceneCompiler.BindingResolver(null);
        assertThat(resolver.isTruthy(List.of("a"))).isTrue();
        assertThat(resolver.isTruthy(List.of())).isFalse();
    }

    @Test
    void isTruthy_optionals() {
        var resolver = new SceneCompiler.BindingResolver(null);
        assertThat(resolver.isTruthy(Optional.of("x"))).isTrue();
        assertThat(resolver.isTruthy(Optional.empty())).isFalse();
    }

    @Test
    void isTruthy_objectIsTrue() {
        var resolver = new SceneCompiler.BindingResolver(null);
        assertThat(resolver.isTruthy(new PieceInfo("♜", ""))).isTrue();
    }

    // ==================================================================================
    // renderWithBindings Tests
    // ==================================================================================

    @Test
    void renderWithBindings_textWithBind_resolvesContent() {
        TextSurface text = TextSurface.of("placeholder").bind("statusLabel");
        var chess = new MockChess(null, "WHITE to move");
        RecordingRenderer recorder = new RecordingRenderer();

        SceneCompiler.renderWithBindings(text, chess, recorder);

        assertThat(recorder.textCalls).hasSize(1);
        assertThat(recorder.textCalls.getFirst()).isEqualTo("WHITE to move");
    }

    @Test
    void renderWithBindings_textWithoutBind_rendersNormally() {
        TextSurface text = TextSurface.of("static text");
        RecordingRenderer recorder = new RecordingRenderer();

        SceneCompiler.renderWithBindings(text, new Object(), recorder);

        assertThat(recorder.textCalls).hasSize(1);
        assertThat(recorder.textCalls.getFirst()).isEqualTo("static text");
    }

    @Test
    void renderWithBindings_imageWithBind_resolvesAlt() {
        ImageSurface img = ImageSurface.of("").bind("state.pieces.a1.symbol");
        var pieces = Map.of("a1", new PieceInfo("♜", "chess/pieces/b_rook.svg"));
        var state = new GameState("active", pieces);
        var chess = new MockChess(state, "");
        RecordingRenderer recorder = new RecordingRenderer();

        SceneCompiler.renderWithBindings(img, chess, recorder);

        assertThat(recorder.imageCalls).hasSize(1);
        assertThat(recorder.imageCalls.getFirst().alt).isEqualTo("♜");
    }

    @Test
    void renderWithBindings_imageWithBind_resolvesImageKey() {
        ImageSurface img = ImageSurface.of("").bind("state.pieces.a1");
        var pieces = Map.of("a1", new PieceInfo("♜", "chess/pieces/b_rook.svg"));
        var state = new GameState("active", pieces);
        var chess = new MockChess(state, "");
        RecordingRenderer recorder = new RecordingRenderer();

        SceneCompiler.renderWithBindings(img, chess, recorder);

        assertThat(recorder.imageCalls).hasSize(1);
        // When binding resolves to an object, imageKey property is used for resource
        assertThat(recorder.imageCalls.getFirst().resource).isEqualTo("chess/pieces/b_rook.svg");
    }

    @Test
    void renderWithBindings_visibleBind_hidesWhenFalsy() {
        TextSurface text = TextSurface.of("should be hidden");
        text.visibleWhen("state.pieces.e4"); // empty square
        var pieces = Map.of("a1", new PieceInfo("♜", ""));
        var state = new GameState("active", pieces);
        var chess = new MockChess(state, "");
        RecordingRenderer recorder = new RecordingRenderer();

        SceneCompiler.renderWithBindings(text, chess, recorder);

        // e4 is not in the map → null → falsy → hidden
        assertThat(recorder.textCalls).isEmpty();
    }

    @Test
    void renderWithBindings_visibleBind_showsWhenTruthy() {
        TextSurface text = TextSurface.of("visible").bind("state.pieces.a1.symbol");
        text.visibleWhen("state.pieces.a1"); // occupied square
        var pieces = Map.of("a1", new PieceInfo("♜", ""));
        var state = new GameState("active", pieces);
        var chess = new MockChess(state, "");
        RecordingRenderer recorder = new RecordingRenderer();

        SceneCompiler.renderWithBindings(text, chess, recorder);

        assertThat(recorder.textCalls).hasSize(1);
        assertThat(recorder.textCalls.getFirst()).isEqualTo("♜");
    }

    @Test
    void renderWithBindings_containerRecursesIntoChildren() {
        ContainerSurface container = ContainerSurface.vertical();
        container.id("board");
        container.add(TextSurface.of("").bind("statusLabel"));
        container.add(TextSurface.of("static footer"));

        var chess = new MockChess(null, "BLACK to move");
        RecordingRenderer recorder = new RecordingRenderer();

        SceneCompiler.renderWithBindings(container, chess, recorder);

        assertThat(recorder.boxBegins).isEqualTo(1);
        assertThat(recorder.boxEnds).isEqualTo(1);
        assertThat(recorder.textCalls).hasSize(2);
        assertThat(recorder.textCalls.get(0)).isEqualTo("BLACK to move");
        assertThat(recorder.textCalls.get(1)).isEqualTo("static footer");
    }

    @Test
    void renderWithBindings_visibleBindOnContainer_skipsEntireSubtree() {
        ContainerSurface container = ContainerSurface.vertical();
        container.visibleWhen("state.pieces.e4"); // will be falsy
        container.add(TextSurface.of("child text"));

        var pieces = Map.of("a1", new PieceInfo("♜", ""));
        var state = new GameState("active", pieces);
        var chess = new MockChess(state, "");
        RecordingRenderer recorder = new RecordingRenderer();

        SceneCompiler.renderWithBindings(container, chess, recorder);

        // Container hidden → no box or children rendered
        assertThat(recorder.boxBegins).isEqualTo(0);
        assertThat(recorder.textCalls).isEmpty();
    }

    @Test
    void renderWithBindings_nullRoot_doesNothing() {
        RecordingRenderer recorder = new RecordingRenderer();
        SceneCompiler.renderWithBindings(null, new Object(), recorder);

        assertThat(recorder.textCalls).isEmpty();
        assertThat(recorder.imageCalls).isEmpty();
        assertThat(recorder.boxBegins).isEqualTo(0);
    }

    @Test
    void renderWithBindings_mixedStaticAndBound() {
        // Simulates a chess board row with one occupied and one empty square
        ContainerSurface row = ContainerSurface.horizontal();

        // Square a1 — occupied (rook)
        ContainerSurface a1 = ContainerSurface.vertical();
        a1.id("a1");
        ImageSurface a1Piece = ImageSurface.of("").bind("state.pieces.a1.symbol");
        a1Piece.visibleWhen("state.pieces.a1");
        a1.add(a1Piece);
        row.add(a1);

        // Square b1 — empty
        ContainerSurface b1 = ContainerSurface.vertical();
        b1.id("b1");
        ImageSurface b1Piece = ImageSurface.of("").bind("state.pieces.b1.symbol");
        b1Piece.visibleWhen("state.pieces.b1");
        b1.add(b1Piece);
        row.add(b1);

        var pieces = Map.of("a1", new PieceInfo("♜", "chess/pieces/b_rook.svg"));
        var state = new GameState("active", pieces);
        var chess = new MockChess(state, "");
        RecordingRenderer recorder = new RecordingRenderer();

        SceneCompiler.renderWithBindings(row, chess, recorder);

        // Row container + 2 square containers
        assertThat(recorder.boxBegins).isEqualTo(3);
        assertThat(recorder.boxEnds).isEqualTo(3);
        // Only a1's piece renders (b1 is empty → visibleBind is falsy)
        assertThat(recorder.imageCalls).hasSize(1);
        assertThat(recorder.imageCalls.getFirst().alt).isEqualTo("♜");
    }

    // ==================================================================================
    // Recording Renderer (test helper)
    // ==================================================================================

    static class ImageCall {
        final String alt;
        final String resource;

        ImageCall(String alt, String resource) {
            this.alt = alt;
            this.resource = resource;
        }
    }

    static class RecordingRenderer implements SurfaceRenderer {
        final List<String> textCalls = new ArrayList<>();
        final List<ImageCall> imageCalls = new ArrayList<>();
        int boxBegins = 0;
        int boxEnds = 0;
        final List<String> ids = new ArrayList<>();

        @Override
        public void text(String content, List<String> styles) {
            textCalls.add(content);
        }

        @Override
        public void formattedText(String content, String format, List<String> styles) {
            textCalls.add(content);
        }

        @Override
        public void image(String alt, ContentID image, ContentID solid, String resource,
                          String size, String fit, List<String> styles) {
            imageCalls.add(new ImageCall(alt, resource));
        }

        @Override
        public void image(String alt, ContentID image, ContentID solid, String size,
                          String fit, List<String> styles) {
            imageCalls.add(new ImageCall(alt, null));
        }

        @Override
        public void beginBox(dev.everydaythings.graph.ui.scene.Scene.Direction direction, List<String> styles) {
            boxBegins++;
        }

        @Override
        public void endBox() {
            boxEnds++;
        }

        @Override
        public void type(String type) {}

        @Override
        public void id(String id) {
            ids.add(id);
        }

        @Override
        public void editable(boolean editable) {}

        @Override
        public void event(String on, String action, String target) {}
    }
}
