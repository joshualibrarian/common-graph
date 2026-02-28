package dev.everydaythings.graph.ui.filament;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class FilamentSurfacePainterOrientationTest {

    @Test
    void canonicalTopLeftUvsMatchSkiaTextureConvention() {
        assertArrayEquals(new float[] {
                0f, 1f,
                1f, 1f,
                1f, 0f,
                0f, 0f
        }, FilamentSurfacePainter.canonicalQuadUvsTopLeft());
    }

    @Test
    void canonicalXzQuadIndicesDefineTopFacingWinding() {
        assertArrayEquals(new short[] {0, 1, 2, 0, 2, 3},
                FilamentSurfacePainter.canonicalXzQuadUpIndices());
    }

    @Test
    void texturedFallbackUsesTopFrontWinding() {
        assertArrayEquals(new short[] {0, 2, 1, 0, 3, 2},
                FilamentSurfacePainter.canonicalXzQuadTopFrontIndices());
    }
}
