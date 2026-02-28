package dev.everydaythings.graph.ui.text;

import java.util.ArrayList;
import java.util.List;

/**
 * Character-cell rasterizer for drawing shapes in the terminal.
 *
 * <p>Provides drawing primitives that account for the typical terminal
 * aspect ratio (~2:1 — characters are roughly twice as tall as wide).
 *
 * <p>Uses the full Unicode repertoire for quality rendering:
 * <ul>
 *   <li>Braille patterns (U+2800–U+28FF) for smooth curves — 2×4 sub-pixel grid per cell</li>
 *   <li>Heavy box-drawing (━┃) for thick lines, light (─│╱╲) for thin lines</li>
 *   <li>Block elements (█▀▄) for filled shapes</li>
 *   <li>Rounded box-drawing (╭╮╰╯) for ellipse outlines</li>
 * </ul>
 */
public class CharGrid {

    private final char[][] cells;
    private final String[][] colors;
    private final int width;
    private final int height;

    /** Terminal aspect ratio — characters are ~2x taller than wide. */
    private static final double ASPECT = 2.0;

    /**
     * Braille sub-pixel grid per cell.
     *
     * <p>Each braille character encodes a 2×4 dot matrix. Dot positions:
     * <pre>
     *   ① ④      bit 0  bit 3
     *   ② ⑤  →   bit 1  bit 4
     *   ③ ⑥      bit 2  bit 5
     *   ⑦ ⑧      bit 6  bit 7
     * </pre>
     * The braille base is U+2800; each dot maps to a bit in the 8-bit offset.
     */
    private static final int BRAILLE_BASE = 0x2800;
    private static final int[] BRAILLE_DOT_BITS = {
            0x01, 0x02, 0x04, 0x40,   // left column:  rows 0-3
            0x08, 0x10, 0x20, 0x80    // right column: rows 0-3
    };

    /** Sub-pixel resolution: 2 dots wide per cell, 4 dots tall per cell. */
    private static final int SUB_W = 2;
    private static final int SUB_H = 4;

    public CharGrid(int width, int height) {
        this.width = Math.max(width, 1);
        this.height = Math.max(height, 1);
        this.cells = new char[this.height][this.width];
        this.colors = new String[this.height][this.width];
        for (int y = 0; y < this.height; y++) {
            for (int x = 0; x < this.width; x++) {
                cells[y][x] = ' ';
            }
        }
    }

    public int width() { return width; }
    public int height() { return height; }

    // ==================== Cell Operations ====================

    /**
     * Set a cell, with bounds checking.
     */
    public void set(int x, int y, char ch, String color) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            cells[y][x] = ch;
            colors[y][x] = color;
        }
    }

    /**
     * Set a cell only if it's currently a space (for layering).
     */
    public void setIfEmpty(int x, int y, char ch, String color) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            if (cells[y][x] == ' ') {
                cells[y][x] = ch;
                colors[y][x] = color;
            }
        }
    }

    /**
     * Get the character at a cell.
     */
    public char get(int x, int y) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            return cells[y][x];
        }
        return ' ';
    }

    // ==================== Braille Sub-Pixel ====================

    /**
     * Set a braille sub-pixel dot, merging with any existing braille pattern.
     *
     * @param subX sub-pixel x (0 to width*2-1)
     * @param subY sub-pixel y (0 to height*4-1)
     * @param color ANSI color code
     */
    public void setBrailleDot(int subX, int subY, String color) {
        int cellX = subX / SUB_W;
        int cellY = subY / SUB_H;
        if (cellX < 0 || cellX >= width || cellY < 0 || cellY >= height) return;

        int dotCol = subX % SUB_W;  // 0 = left, 1 = right
        int dotRow = subY % SUB_H;  // 0-3

        int bitIndex = dotCol * 4 + dotRow; // maps to BRAILLE_DOT_BITS index
        int bit = BRAILLE_DOT_BITS[bitIndex];

        // Read existing braille pattern and merge
        char existing = cells[cellY][cellX];
        int pattern;
        if (existing >= BRAILLE_BASE && existing < BRAILLE_BASE + 256) {
            pattern = existing - BRAILLE_BASE;
        } else if (existing == ' ') {
            pattern = 0;
        } else {
            // Non-braille character already here — don't overwrite
            return;
        }

        pattern |= bit;
        cells[cellY][cellX] = (char) (BRAILLE_BASE + pattern);
        colors[cellY][cellX] = color;
    }

    // ==================== Ellipse Drawing ====================

    /**
     * Draw an ellipse outline using braille sub-pixel rendering.
     *
     * <p>Samples the ellipse at many angles and plots each point as a
     * braille dot, giving ~2× horizontal and ~4× vertical resolution
     * compared to character-cell rendering.
     *
     * @param cx     center x (in cell coordinates)
     * @param cy     center y (in cell coordinates)
     * @param rx     horizontal radius (in cell columns)
     * @param ry     vertical radius (in cell rows)
     * @param color  ANSI color code (null for default)
     */
    public void drawEllipse(double cx, double cy, double rx, double ry, String color) {
        if (rx < 1 || ry < 1) return;

        // Convert cell-space center and radii to sub-pixel space
        double subCx = cx * SUB_W;
        double subCy = cy * SUB_H;
        double subRx = rx * SUB_W;
        double subRy = ry * SUB_H;

        // Sample densely for smooth curve
        int steps = Math.max(120, (int) (2 * Math.PI * Math.max(subRx, subRy)));
        for (int i = 0; i < steps; i++) {
            double angle = 2.0 * Math.PI * i / steps;
            int sx = (int) Math.round(subCx + subRx * Math.cos(angle));
            int sy = (int) Math.round(subCy + subRy * Math.sin(angle));
            setBrailleDot(sx, sy, color);
        }
    }

    /**
     * Fill an ellipse using scanline fill.
     *
     * @param cx     center x
     * @param cy     center y
     * @param rx     horizontal radius (in cell columns)
     * @param ry     vertical radius (in cell rows)
     * @param ch     fill character
     * @param color  ANSI color code
     */
    public void fillEllipse(double cx, double cy, double rx, double ry, char ch, String color) {
        int yMin = Math.max(0, (int) Math.floor(cy - ry));
        int yMax = Math.min(height - 1, (int) Math.ceil(cy + ry));

        for (int y = yMin; y <= yMax; y++) {
            double dy = y - cy;
            double discriminant = 1.0 - (dy * dy) / (ry * ry);
            if (discriminant < 0) continue;
            double xSpan = rx * Math.sqrt(discriminant);
            int xMin = Math.max(0, (int) Math.ceil(cx - xSpan));
            int xMax = Math.min(width - 1, (int) Math.floor(cx + xSpan));
            for (int x = xMin; x <= xMax; x++) {
                set(x, y, ch, color);
            }
        }
    }

    // ==================== Line Drawing ====================

    /**
     * Draw a line using Bresenham's algorithm with angle-aware Unicode characters.
     *
     * <p>Uses light box-drawing characters for thin lines: ─ │ ╱ ╲.
     *
     * @param x0    start x
     * @param y0    start y
     * @param x1    end x
     * @param y1    end y
     * @param color ANSI color code
     */
    public void drawLine(double x0, double y0, double x1, double y1, String color) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        char ch = lineChar(dx, dy, false);

        int ix0 = (int) Math.round(x0), iy0 = (int) Math.round(y0);
        int ix1 = (int) Math.round(x1), iy1 = (int) Math.round(y1);

        int adx = Math.abs(ix1 - ix0);
        int ady = Math.abs(iy1 - iy0);
        int sx = ix0 < ix1 ? 1 : -1;
        int sy = iy0 < iy1 ? 1 : -1;
        int err = adx - ady;

        while (true) {
            set(ix0, iy0, ch, color);
            if (ix0 == ix1 && iy0 == iy1) break;
            int e2 = 2 * err;
            if (e2 > -ady) { err -= ady; ix0 += sx; }
            if (e2 < adx) { err += adx; iy0 += sy; }
        }
    }

    /**
     * Draw a thick line using heavy box-drawing characters (━ ┃).
     *
     * <p>Heavy weight characters are visually heavier than light (─ │)
     * while staying one cell wide. Uses ╲ ╱ for diagonals since
     * heavy diagonal box-drawing doesn't exist in Unicode.
     *
     * @param x0    start x
     * @param y0    start y
     * @param x1    end x
     * @param y1    end y
     * @param color ANSI color code
     */
    public void drawThickLine(double x0, double y0, double x1, double y1, String color) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        char ch = lineChar(dx, dy, true);

        int ix0 = (int) Math.round(x0), iy0 = (int) Math.round(y0);
        int ix1 = (int) Math.round(x1), iy1 = (int) Math.round(y1);

        int adx = Math.abs(ix1 - ix0);
        int ady = Math.abs(iy1 - iy0);
        int sx = ix0 < ix1 ? 1 : -1;
        int sy = iy0 < iy1 ? 1 : -1;
        int err = adx - ady;

        while (true) {
            set(ix0, iy0, ch, color);
            if (ix0 == ix1 && iy0 == iy1) break;
            int e2 = 2 * err;
            if (e2 > -ady) { err -= ady; ix0 += sx; }
            if (e2 < adx) { err += adx; iy0 += sy; }
        }
    }

    /**
     * Choose a line character based on direction, with light/heavy weight.
     *
     * <p>Uses 8-octant selection with aspect-corrected angle.
     * Light: ─ │ ╱ ╲   Heavy: ━ ┃ ╱ ╲ (no heavy diagonals in Unicode)
     */
    private char lineChar(double dx, double dy, boolean heavy) {
        if (Math.abs(dx) < 0.001 && Math.abs(dy) < 0.001) return '·';

        double angle = Math.toDegrees(Math.atan2(dy * ASPECT, dx));
        if (angle < 0) angle += 360;

        if (angle <= 22.5 || angle > 337.5) return heavy ? '━' : '─';
        if (angle > 22.5 && angle <= 67.5) return '╲';
        if (angle > 67.5 && angle <= 112.5) return heavy ? '┃' : '│';
        if (angle > 112.5 && angle <= 157.5) return '╱';
        if (angle > 157.5 && angle <= 202.5) return heavy ? '━' : '─';
        if (angle > 202.5 && angle <= 247.5) return '╲';
        if (angle > 247.5 && angle <= 292.5) return heavy ? '┃' : '│';
        return '╱';
    }

    // ==================== Text Placement ====================

    /**
     * Place text centered at (cx, cy).
     */
    public void drawTextCentered(int cx, int cy, String text, String color) {
        int startX = cx - text.length() / 2;
        for (int i = 0; i < text.length(); i++) {
            set(startX + i, cy, text.charAt(i), color);
        }
    }

    /**
     * Place text at (x, y).
     */
    public void drawText(int x, int y, String text, String color) {
        for (int i = 0; i < text.length(); i++) {
            set(x + i, y, text.charAt(i), color);
        }
    }

    // ==================== Output ====================

    /**
     * Convert the grid to lines with embedded ANSI color codes.
     *
     * @return list of strings, one per row
     */
    public List<String> toLines() {
        List<String> result = new ArrayList<>(height);
        for (int y = 0; y < height; y++) {
            StringBuilder sb = new StringBuilder(width * 2);
            String currentColor = null;
            for (int x = 0; x < width; x++) {
                String cellColor = colors[y][x];
                if (!java.util.Objects.equals(cellColor, currentColor)) {
                    if (currentColor != null) {
                        sb.append("\u001B[0m");
                    }
                    if (cellColor != null) {
                        sb.append(cellColor);
                    }
                    currentColor = cellColor;
                }
                sb.append(cells[y][x]);
            }
            if (currentColor != null) {
                sb.append("\u001B[0m");
            }
            result.add(sb.toString());
        }
        return result;
    }

    /**
     * Convert to a plain string (no ANSI codes) for testing.
     */
    public String toPlainString() {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                sb.append(cells[y][x]);
            }
            if (y < height - 1) sb.append('\n');
        }
        return sb.toString();
    }
}
