package dev.everydaythings.graph.ui.scene;

import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.value.Unit;

import java.util.HashMap;
import java.util.Map;

/**
 * Conversion tables for mapping Unit items to renderer-specific measurements.
 *
 * <p>Keyed by {@link ItemID} (not Unit references) so that Units resolved from the
 * graph match even if they're different object instances than the seed statics.
 *
 * <p>Three conversion dimensions:
 * <ul>
 *   <li>{@code columnsPerUnit} — TUI horizontal (character columns)</li>
 *   <li>{@code rowsPerUnit} — TUI vertical (terminal rows)</li>
 *   <li>{@code pixelsPerUnit} — FX/GUI pixels</li>
 * </ul>
 *
 * <p>All lookups throw if the unit is not found — the seed vocabulary guarantees
 * all standard units exist. A missing unit means something fundamental is broken.
 */
public class RenderMetrics {

    private final Map<ItemID, Double> columnsPerUnit;
    private final Map<ItemID, Double> rowsPerUnit;
    private final Map<ItemID, Double> pixelsPerUnit;

    private RenderMetrics(Map<ItemID, Double> columnsPerUnit,
                          Map<ItemID, Double> rowsPerUnit,
                          Map<ItemID, Double> pixelsPerUnit) {
        this.columnsPerUnit = Map.copyOf(columnsPerUnit);
        this.rowsPerUnit = Map.copyOf(rowsPerUnit);
        this.pixelsPerUnit = Map.copyOf(pixelsPerUnit);
    }

    /**
     * Default TUI metrics for monospace terminal rendering.
     *
     * <p>Column mapping: 1ch=1col, 1em=2col, 1px=1/8col, 1ln=1col
     * <p>Row mapping: 1ln=1row, 1em=1row, 1ch=0.5row, 1px=1/16row
     * <p>Pixel mapping: compressed for TUI weight mapping (1em=4px, 1ch=2px).
     * In a terminal, one character cell is already a coarse visual unit, so
     * relative units must map to the LIGHT/HEAVY range of
     * {@link dev.everydaythings.graph.ui.text.BoxDrawing#pixelsToWeight} rather than
     * the block-character range. This ensures "0.5em solid" renders as ━┃ (heavy)
     * instead of ▊ (3/4 block).
     */
    public static final RenderMetrics TUI_DEFAULT = builder()
            // Columns (horizontal)
            .column(Unit.CharacterWidth.SEED, 1.0)    // 1ch = 1 column
            .column(Unit.Em.SEED, 2.0)                 // 1em = 2 columns
            .column(Unit.Rem.SEED, 2.0)                // 1rem = 2 columns
            .column(Unit.Pixel.SEED, 0.125)            // 1px = 1/8 column
            .column(Unit.LineHeight.SEED, 1.0)        // 1ln = 1 column
            .column(Unit.Percent.SEED, 1.0)            // 1% = 1 column (identity pass-through)
            // Rows (vertical)
            .row(Unit.LineHeight.SEED, 1.0)           // 1ln = 1 row
            .row(Unit.Em.SEED, 1.0)                    // 1em = 1 row
            .row(Unit.Rem.SEED, 1.0)                   // 1rem = 1 row
            .row(Unit.CharacterWidth.SEED, 0.5)       // 1ch = 0.5 rows
            .row(Unit.Pixel.SEED, 0.0625)              // 1px = 1/16 row
            .row(Unit.Percent.SEED, 1.0)               // 1% = 1 row (identity pass-through)
            // Pixels — scaled so character fractions map to block-character fractions.
            // With 1em=10px, the pixelsToWeight thresholds align so that:
            //   0.125em → HEAVY, 0.25em → BLOCK_1_4, 0.5em → BLOCK_1_2, 1em → FULL
            // This uses the full Unicode block character range (▏▎▍▌▋▊▉█).
            .pixel(Unit.Pixel.SEED, 1.0)               // 1px = 1px (unchanged)
            .pixel(Unit.Em.SEED, 10.0)                 // 1em = 10px (0.5em→BLOCK_1_2)
            .pixel(Unit.CharacterWidth.SEED, 10.0)    // 1ch = 10px (0.2ch→BLOCK_1_8)
            .pixel(Unit.Rem.SEED, 10.0)                // 1rem = 10px
            .pixel(Unit.LineHeight.SEED, 10.0)        // 1ln = 10px
            .pixel(Unit.Percent.SEED, 1.0)             // 1% = 1px (identity pass-through)
            .build();

    /**
     * Create GUI pixel metrics from measured font values.
     *
     * <p>Renderers call this with their actual font measurements so that
     * {@code em}, {@code ch}, and {@code ln} units resolve to real values.
     *
     * @param emPx  base font size in pixels (1em)
     * @param chPx  measured width of "0" in pixels (1ch)
     * @param lnPx  measured line height in pixels (1ln)
     * @param remPx root font size in pixels (1rem)
     */
    public static RenderMetrics gui(double emPx, double chPx, double lnPx, double remPx) {
        return builder()
                .pixel(Unit.Pixel.SEED, 1.0)
                .pixel(Unit.Em.SEED, emPx)
                .pixel(Unit.CharacterWidth.SEED, chPx)
                .pixel(Unit.Rem.SEED, remPx)
                .pixel(Unit.LineHeight.SEED, lnPx)
                .pixel(Unit.Percent.SEED, 1.0)
                // Column/row mappings (unchanged — TUI grid semantics)
                .column(Unit.CharacterWidth.SEED, 1.0)
                .column(Unit.Em.SEED, 2.0)
                .column(Unit.Rem.SEED, 2.0)
                .column(Unit.Pixel.SEED, 0.125)
                .column(Unit.LineHeight.SEED, 1.0)
                .column(Unit.Percent.SEED, 1.0)
                .row(Unit.LineHeight.SEED, 1.0)
                .row(Unit.Em.SEED, 1.0)
                .row(Unit.Rem.SEED, 1.0)
                .row(Unit.CharacterWidth.SEED, 0.5)
                .row(Unit.Pixel.SEED, 0.0625)
                .row(Unit.Percent.SEED, 1.0)
                .build();
    }

    /**
     * Default FX/GUI metrics.
     *
     * <p>Pixel mapping uses standard font-size-relative values (fontSize=16px).
     * Column/row mappings match TUI for consistency.
     */
    public static final RenderMetrics FX_DEFAULT = gui(16.0, 8.8, 19.2, 16.0);

    /**
     * Default Skia metrics — same pixel mappings as FX.
     */
    public static final RenderMetrics SKIA_DEFAULT = FX_DEFAULT;

    // ==================== Conversions ====================

    /**
     * Convert a value to TUI character columns.
     *
     * @throws IllegalArgumentException if no column conversion exists for this unit
     */
    public double toColumns(Unit unit, double value) {
        Double factor = columnsPerUnit.get(unit.iid());
        if (factor == null) {
            throw new IllegalArgumentException("No column conversion for unit: " + unit);
        }
        return value * factor;
    }

    /**
     * Convert a value to TUI rows.
     *
     * @throws IllegalArgumentException if no row conversion exists for this unit
     */
    public double toRows(Unit unit, double value) {
        Double factor = rowsPerUnit.get(unit.iid());
        if (factor == null) {
            throw new IllegalArgumentException("No row conversion for unit: " + unit);
        }
        return value * factor;
    }

    /**
     * Convert a value to pixels.
     *
     * @throws IllegalArgumentException if no pixel conversion exists for this unit
     */
    public double toPixels(Unit unit, double value) {
        Double factor = pixelsPerUnit.get(unit.iid());
        if (factor == null) {
            throw new IllegalArgumentException("No pixel conversion for unit: " + unit);
        }
        return value * factor;
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Map<ItemID, Double> columns = new HashMap<>();
        private final Map<ItemID, Double> rows = new HashMap<>();
        private final Map<ItemID, Double> pixels = new HashMap<>();

        public Builder column(Unit unit, double factor) {
            columns.put(unit.iid(), factor);
            return this;
        }

        public Builder row(Unit unit, double factor) {
            rows.put(unit.iid(), factor);
            return this;
        }

        public Builder pixel(Unit unit, double factor) {
            pixels.put(unit.iid(), factor);
            return this;
        }

        public RenderMetrics build() {
            return new RenderMetrics(columns, rows, pixels);
        }
    }
}
