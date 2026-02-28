package dev.everydaythings.graph.game.dice;

import dev.everydaythings.graph.game.Piece;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.Scene.Direction;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * A single die with a current face value.
 *
 * <p>Reusable across dice games — Yahtzee, craps, backgammon, Liar's Dice,
 * Farkle, etc. Implements {@link Piece} for multi-fidelity rendering.
 *
 * <p>Standard d6 die faces use Unicode die symbols (U+2680–U+2685: ⚀⚁⚂⚃⚄⚅).
 *
 * <h3>Rendering tiers</h3>
 * <ul>
 *   <li>Text: Unicode die face character ({@link #symbol()})</li>
 *   <li>2D: Pip dot layout via Scene annotations</li>
 *   <li>3D: Cube body with pip dots on top face ({@code @Scene.Body})</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * Die die = Die.d6(4);        // face value 4
 * die.symbol();               // "⚃"
 * die.faceValue();            // 4
 *
 * Die blank = Die.blank();    // unrolled die
 * blank.faceValue();          // 0
 * blank.symbol();             // "⬜"
 * }</pre>
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode
@Scene.Body(shape = "box", width = "16mm", height = "16mm", depth = "16mm",
            color = 0xFAFAFA, shading = "lit")
@Scene.Container(direction = Direction.STACK, width = "2.5em", height = "2.5em",
                 style = {"die"}, cornerRadius = "0.3em", background = "#FAFAFA")
@Scene.State(when = "value.held", style = {"die-held"})
public class Die implements Piece {

    // ==================================================================================
    // Scene: 3D Face Rendering
    // ==================================================================================

    /** Top face of the 3D cube — shows current pip pattern. */
    @Scene.Face("top")
    @Scene.Container(direction = Direction.VERTICAL, background = "#FAFAFA",
                     width = "100%", height = "100%", gap = "0.15em",
                     align = "center", padding = "0.3em")
    static class TopFace {
        @Scene.Container(direction = Direction.HORIZONTAL, gap = "0.15em")
        static class Row0 {
            @Scene.Container(width = "0.6em", height = "0.6em",
                             direction = Direction.STACK, align = "center")
            static class S0 {
                @Scene.If("value.hasTL")
                @Scene.Shape(type = "circle", width = "0.5em", height = "0.5em",
                             fill = "#1A1A1A")
                static class P {}
            }
            @Scene.Container(width = "0.6em", height = "0.6em",
                             direction = Direction.STACK, align = "center")
            static class S1 {
                @Scene.If("value.hasTC")
                @Scene.Shape(type = "circle", width = "0.5em", height = "0.5em",
                             fill = "#1A1A1A")
                static class P {}
            }
            @Scene.Container(width = "0.6em", height = "0.6em",
                             direction = Direction.STACK, align = "center")
            static class S2 {
                @Scene.If("value.hasTR")
                @Scene.Shape(type = "circle", width = "0.5em", height = "0.5em",
                             fill = "#1A1A1A")
                static class P {}
            }
        }
        @Scene.Container(direction = Direction.HORIZONTAL, gap = "0.15em")
        static class Row1 {
            @Scene.Container(width = "0.6em", height = "0.6em",
                             direction = Direction.STACK, align = "center")
            static class S0 {
                @Scene.If("value.hasML")
                @Scene.Shape(type = "circle", width = "0.5em", height = "0.5em",
                             fill = "#1A1A1A")
                static class P {}
            }
            @Scene.Container(width = "0.6em", height = "0.6em",
                             direction = Direction.STACK, align = "center")
            static class S1 {
                @Scene.If("value.hasMC")
                @Scene.Shape(type = "circle", width = "0.5em", height = "0.5em",
                             fill = "#1A1A1A")
                static class P {}
            }
            @Scene.Container(width = "0.6em", height = "0.6em",
                             direction = Direction.STACK, align = "center")
            static class S2 {
                @Scene.If("value.hasMR")
                @Scene.Shape(type = "circle", width = "0.5em", height = "0.5em",
                             fill = "#1A1A1A")
                static class P {}
            }
        }
        @Scene.Container(direction = Direction.HORIZONTAL, gap = "0.15em")
        static class Row2 {
            @Scene.Container(width = "0.6em", height = "0.6em",
                             direction = Direction.STACK, align = "center")
            static class S0 {
                @Scene.If("value.hasBL")
                @Scene.Shape(type = "circle", width = "0.5em", height = "0.5em",
                             fill = "#1A1A1A")
                static class P {}
            }
            @Scene.Container(width = "0.6em", height = "0.6em",
                             direction = Direction.STACK, align = "center")
            static class S1 {
                @Scene.If("value.hasBC")
                @Scene.Shape(type = "circle", width = "0.5em", height = "0.5em",
                             fill = "#1A1A1A")
                static class P {}
            }
            @Scene.Container(width = "0.6em", height = "0.6em",
                             direction = Direction.STACK, align = "center")
            static class S2 {
                @Scene.If("value.hasBR")
                @Scene.Shape(type = "circle", width = "0.5em", height = "0.5em",
                             fill = "#1A1A1A")
                static class P {}
            }
        }
    }

    // ==================================================================================
    // Scene: 2D Pip Rendering
    // ==================================================================================

    /** 2D die face — 3×3 pip grid with conditional circles. */
    @Scene.Container(direction = Direction.VERTICAL, gap = "0.15em",
                     align = "center", width = "100%", height = "100%",
                     padding = "0.3em")
    static class PipGrid {

        // Top row: TL TC TR
        @Scene.Container(direction = Direction.HORIZONTAL, gap = "0.15em")
        static class TopRow {
            @Scene.Container(width = "0.6em", height = "0.6em",
                             direction = Direction.STACK, align = "center")
            static class SlotTL {
                @Scene.If("value.hasTL")
                @Scene.Shape(type = "circle", width = "0.5em", height = "0.5em",
                             fill = "#1A1A1A")
                static class Pip {}
            }

            @Scene.Container(width = "0.6em", height = "0.6em",
                             direction = Direction.STACK, align = "center")
            static class SlotTC {
                @Scene.If("value.hasTC")
                @Scene.Shape(type = "circle", width = "0.5em", height = "0.5em",
                             fill = "#1A1A1A")
                static class Pip {}
            }

            @Scene.Container(width = "0.6em", height = "0.6em",
                             direction = Direction.STACK, align = "center")
            static class SlotTR {
                @Scene.If("value.hasTR")
                @Scene.Shape(type = "circle", width = "0.5em", height = "0.5em",
                             fill = "#1A1A1A")
                static class Pip {}
            }
        }

        // Middle row: ML MC MR
        @Scene.Container(direction = Direction.HORIZONTAL, gap = "0.15em")
        static class MiddleRow {
            @Scene.Container(width = "0.6em", height = "0.6em",
                             direction = Direction.STACK, align = "center")
            static class SlotML {
                @Scene.If("value.hasML")
                @Scene.Shape(type = "circle", width = "0.5em", height = "0.5em",
                             fill = "#1A1A1A")
                static class Pip {}
            }

            @Scene.Container(width = "0.6em", height = "0.6em",
                             direction = Direction.STACK, align = "center")
            static class SlotMC {
                @Scene.If("value.hasMC")
                @Scene.Shape(type = "circle", width = "0.5em", height = "0.5em",
                             fill = "#1A1A1A")
                static class Pip {}
            }

            @Scene.Container(width = "0.6em", height = "0.6em",
                             direction = Direction.STACK, align = "center")
            static class SlotMR {
                @Scene.If("value.hasMR")
                @Scene.Shape(type = "circle", width = "0.5em", height = "0.5em",
                             fill = "#1A1A1A")
                static class Pip {}
            }
        }

        // Bottom row: BL BC BR
        @Scene.Container(direction = Direction.HORIZONTAL, gap = "0.15em")
        static class BottomRow {
            @Scene.Container(width = "0.6em", height = "0.6em",
                             direction = Direction.STACK, align = "center")
            static class SlotBL {
                @Scene.If("value.hasBL")
                @Scene.Shape(type = "circle", width = "0.5em", height = "0.5em",
                             fill = "#1A1A1A")
                static class Pip {}
            }

            @Scene.Container(width = "0.6em", height = "0.6em",
                             direction = Direction.STACK, align = "center")
            static class SlotBC {
                @Scene.If("value.hasBC")
                @Scene.Shape(type = "circle", width = "0.5em", height = "0.5em",
                             fill = "#1A1A1A")
                static class Pip {}
            }

            @Scene.Container(width = "0.6em", height = "0.6em",
                             direction = Direction.STACK, align = "center")
            static class SlotBR {
                @Scene.If("value.hasBR")
                @Scene.Shape(type = "circle", width = "0.5em", height = "0.5em",
                             fill = "#1A1A1A")
                static class Pip {}
            }
        }
    }

    // ==================================================================================
    // Pip Grid
    // ==================================================================================

    /**
     * Standard die pip patterns as a 3×3 grid.
     *
     * <p>Grid positions: TL TC TR / ML MC MR / BL BC BR (indices 0-8).
     *
     * <pre>
     * 1: MC
     * 2: TR, BL
     * 3: TR, MC, BL
     * 4: TL, TR, BL, BR
     * 5: TL, TR, MC, BL, BR
     * 6: TL, TR, ML, MR, BL, BR
     * </pre>
     */
    private static final boolean[][] PIP_GRID = {
            // face 0 (blank): no pips
            {false, false, false, false, false, false, false, false, false},
            // face 1: MC
            {false, false, false, false, true, false, false, false, false},
            // face 2: TR, BL
            {false, false, true, false, false, false, true, false, false},
            // face 3: TR, MC, BL
            {false, false, true, false, true, false, true, false, false},
            // face 4: TL, TR, BL, BR
            {true, false, true, false, false, false, true, false, true},
            // face 5: TL, TR, MC, BL, BR
            {true, false, true, false, true, false, true, false, true},
            // face 6: TL, TR, ML, MR, BL, BR
            {true, false, true, true, false, true, true, false, true},
    };

    public boolean hasTL() { return pip(0); }
    public boolean hasTC() { return pip(1); }
    public boolean hasTR() { return pip(2); }
    public boolean hasML() { return pip(3); }
    public boolean hasMC() { return pip(4); }
    public boolean hasMR() { return pip(5); }
    public boolean hasBL() { return pip(6); }
    public boolean hasBC() { return pip(7); }
    public boolean hasBR() { return pip(8); }

    private boolean pip(int index) {
        return faceValue >= 1 && faceValue <= 6 && PIP_GRID[faceValue][index];
    }

    // ==================================================================================
    // Constants
    // ==================================================================================

    /** Unicode die face characters (index 0-6; 0 = blank/unrolled). */
    private static final String[] DIE_FACES = {
            "\u2B1C",  // 0 = blank (unrolled)
            "\u2680",  // ⚀ = 1
            "\u2681",  // ⚁ = 2
            "\u2682",  // ⚂ = 3
            "\u2683",  // ⚃ = 4
            "\u2684",  // ⚄ = 5
            "\u2685",  // ⚅ = 6
    };

    /** Standard die dimensions: 16mm per side. */
    public static final double STANDARD_SIZE_MM = 16.0;

    // ==================================================================================
    // Fields
    // ==================================================================================

    /** Number of sides (e.g., 6 for standard die). */
    private final int sides;

    /** Current face value (1-sides), or 0 if unrolled. */
    private final int faceValue;

    /** Whether this die is being held (not re-rolled). */
    private final boolean held;

    // ==================================================================================
    // Constructors and Factories
    // ==================================================================================

    /**
     * Create a die with a specific face value.
     *
     * @param sides     number of sides
     * @param faceValue current face value (0 = unrolled, 1-sides = rolled)
     * @param held      whether this die is held
     */
    public Die(int sides, int faceValue, boolean held) {
        if (sides < 2) throw new IllegalArgumentException("Die must have at least 2 sides");
        if (faceValue < 0 || faceValue > sides) {
            throw new IllegalArgumentException(
                    "Face value " + faceValue + " out of range for d" + sides);
        }
        this.sides = sides;
        this.faceValue = faceValue;
        this.held = held;
    }

    /** Standard d6 with a face value. */
    public static Die d6(int faceValue) {
        return new Die(6, faceValue, false);
    }

    /** Standard d6 with a face value and held state. */
    public static Die d6(int faceValue, boolean held) {
        return new Die(6, faceValue, held);
    }

    /** Unrolled d6. */
    public static Die blank() {
        return new Die(6, 0, false);
    }

    /** Create a new die with the same sides but a different face value. */
    public Die withFace(int newFaceValue) {
        return new Die(sides, newFaceValue, held);
    }

    /** Create a new die with the same face but different held state. */
    public Die withHeld(boolean newHeld) {
        return new Die(sides, faceValue, newHeld);
    }

    // ==================================================================================
    // Queries
    // ==================================================================================

    /** Whether this die has been rolled (face value > 0). */
    public boolean isRolled() {
        return faceValue > 0;
    }

    /** Whether this is a standard 6-sided die. */
    public boolean isD6() {
        return sides == 6;
    }

    /**
     * ARGB pip color. Standard dice: black pips on white.
     * Held dice could use a tint in the renderer.
     */
    public int pipColor() {
        return 0xFF1A1A1A;
    }

    /**
     * ARGB body color. White for free, amber-tinted for held.
     */
    public int bodyColor() {
        return held ? 0xFFFFF3E0 : 0xFFFAFAFA;
    }

    // ==================================================================================
    // Piece Implementation
    // ==================================================================================

    @Override
    public String symbol() {
        if (isD6() && faceValue >= 0 && faceValue <= 6) {
            return DIE_FACES[faceValue];
        }
        if (faceValue == 0) return "\u2B1C";
        return String.valueOf(faceValue);
    }

    @Override
    public String imageKey() {
        return "";
    }

    @Override
    public String modelKey() {
        return "dice/d" + sides + ".glb";
    }

    @Override
    public String colorCategory() {
        return held ? "held" : "free";
    }

    @Override
    public int modelColor() {
        return bodyColor();
    }

    @Override
    public String toString() {
        String s = symbol();
        if (held) s += "*";
        return s;
    }
}
