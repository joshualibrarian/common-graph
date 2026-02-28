package dev.everydaythings.graph.ui.text;

import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.lang.UProperty;
import dev.everydaythings.graph.ui.scene.RenderContext;
import dev.everydaythings.graph.ui.scene.BoxBorder;
import dev.everydaythings.graph.ui.scene.SizeValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Unicode box-drawing character resolution and box wrapping.
 *
 * <p>Maps border specifications (style + width) to the appropriate Unicode
 * characters from the Box Drawing block (U+2500–U+257F) and Block Elements
 * block (U+2580–U+259F).
 *
 * <h2>Weight Spectrum</h2>
 * <p>Border width maps to a continuous spectrum of visual weights:
 * <pre>
 * LIGHT  → line drawing thin:  ─ │ ┌ ┐ └ ┘
 * HEAVY  → line drawing thick: ━ ┃ ┏ ┓ ┗ ┛
 * DOUBLE → double lines:       ═ ║ ╔ ╗ ╚ ╝
 * BLOCK  → fractional blocks:  ▏▎▍▌▋▊▉█ (vertical) / ▔▁▂▃▄▅▆▇█ (horizontal)
 * </pre>
 *
 * <h2>Per-Side Transitions</h2>
 * <p>When adjacent sides have different weights, Unicode provides transition
 * characters at corners (e.g., ┍ for light-top/heavy-left). This class
 * handles the full transition matrix.
 */
public class BoxDrawing {

    /**
     * Weight tiers for TUI border rendering.
     * Continuous spectrum from hairline to full cell.
     */
    public enum Weight {
        NONE,
        LIGHT,          // ─ │ — line drawing, thin
        HEAVY,          // ━ ┃ — line drawing, thick
        DOUBLE,         // ═ ║ — double lines
        BLOCK_1_8,      // ▏ ▔ — 1/8 block
        BLOCK_1_4,      // ▎ ▂ — 1/4 block
        BLOCK_3_8,      // ▍ ▃ — 3/8 block
        BLOCK_1_2,      // ▌ ▄ — 1/2 block
        BLOCK_5_8,      // ▋ ▅ — 5/8 block
        BLOCK_3_4,      // ▊ ▆ — 3/4 block
        BLOCK_7_8,      // ▉ ▇ — 7/8 block
        FULL;           // █   — full block

        public boolean isBlock() { return ordinal() >= BLOCK_1_8.ordinal(); }
        public boolean isLine()  { return this == LIGHT || this == HEAVY || this == DOUBLE; }
    }

    public enum Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    // ==================== Horizontal Characters ====================

    /**
     * Get horizontal line character for a border side (top or bottom edge).
     */
    public static char horizontalChar(String style, Weight weight) {
        if (weight == Weight.NONE) return ' ';
        if (weight.isBlock()) return '█'; // blocks use full block for horizontal runs
        if ("double".equals(style)) return '═';
        return switch (weight) {
            case LIGHT -> "dashed".equals(style) ? '╌' : '─';
            case HEAVY -> "dashed".equals(style) ? '╍' : '━';
            default -> '─';
        };
    }

    /**
     * Get the top-edge block character for the given weight.
     * Top blocks grow downward: ▔ → █
     */
    public static char topBlockChar(Weight weight) {
        return switch (weight) {
            case BLOCK_1_8 -> '▔';
            case BLOCK_1_4, BLOCK_3_8, BLOCK_1_2 -> '▀'; // upper half block
            case BLOCK_5_8, BLOCK_3_4, BLOCK_7_8, FULL -> '█';
            default -> ' ';
        };
    }

    /**
     * Get the bottom-edge block character for the given weight.
     * Bottom blocks grow upward: ▁ → █
     */
    public static char bottomBlockChar(Weight weight) {
        return switch (weight) {
            case BLOCK_1_8 -> '▁';
            case BLOCK_1_4 -> '▂';
            case BLOCK_3_8 -> '▃';
            case BLOCK_1_2 -> '▄';
            case BLOCK_5_8 -> '▅';
            case BLOCK_3_4 -> '▆';
            case BLOCK_7_8 -> '▇';
            case FULL -> '█';
            default -> ' ';
        };
    }

    // ==================== Vertical Characters ====================

    /**
     * Get vertical line character for a border side (left or right edge).
     */
    public static char verticalChar(String style, Weight weight) {
        if (weight == Weight.NONE) return ' ';
        if (weight.isBlock()) return leftBlockChar(weight);
        if ("double".equals(style)) return '║';
        return switch (weight) {
            case LIGHT -> "dashed".equals(style) ? '╎' : '│';
            case HEAVY -> "dashed".equals(style) ? '╏' : '┃';
            default -> '│';
        };
    }

    /**
     * Get the left-side block character for the given weight.
     * Left blocks: ▏▎▍▌▋▊▉█
     */
    public static char leftBlockChar(Weight weight) {
        return switch (weight) {
            case BLOCK_1_8 -> '▏';
            case BLOCK_1_4 -> '▎';
            case BLOCK_3_8 -> '▍';
            case BLOCK_1_2 -> '▌';
            case BLOCK_5_8 -> '▋';
            case BLOCK_3_4 -> '▊';
            case BLOCK_7_8 -> '▉';
            case FULL -> '█';
            default -> ' ';
        };
    }

    /**
     * Get the right-side block character for the given weight.
     * Right blocks: ▕ (1/8 only), ▐ (half), █ (full)
     */
    public static char rightBlockChar(Weight weight) {
        return switch (weight) {
            case BLOCK_1_8 -> '▕';
            case BLOCK_1_4 -> '▕'; // closest available
            case BLOCK_3_8, BLOCK_1_2 -> '▐';
            case BLOCK_5_8, BLOCK_3_4, BLOCK_7_8, FULL -> '█';
            default -> ' ';
        };
    }

    // ==================== Corner Resolution ====================

    /**
     * Resolve corner character where two sides meet.
     *
     * <p>For line-drawing weights (LIGHT/HEAVY/DOUBLE), uses Unicode's
     * transition characters. For block weights, uses the full block.
     *
     * @param hWeight weight of the horizontal side (top for TOP_*, bottom for BOTTOM_*)
     * @param vWeight weight of the vertical side (left for *_LEFT, right for *_RIGHT)
     * @param hStyle  style of the horizontal side
     * @param vStyle  style of the vertical side
     * @param corner  which corner
     * @param rounded whether to use rounded corners (light lines only)
     * @return the corner character
     */
    public static char resolveCorner(Weight hWeight, Weight vWeight,
                                     String hStyle, String vStyle,
                                     Corner corner, boolean rounded) {
        // If either side is NONE, use half-line or nothing
        if (hWeight == Weight.NONE && vWeight == Weight.NONE) return ' ';

        // Block weights → use light line-drawing corners for clean proportions
        // (full block '█' at corners looks oversized next to thin block edges like ▏▎)
        if (hWeight.isBlock() || vWeight.isBlock()) {
            return resolveLineCorner(Weight.LIGHT, Weight.LIGHT,
                    "solid", "solid", corner, rounded);
        }

        // Both are line-drawing weights — use the transition tables
        return resolveLineCorner(hWeight, vWeight, hStyle, vStyle, corner, rounded);
    }

    /**
     * Resolve a line-drawing corner character.
     */
    private static char resolveLineCorner(Weight hWeight, Weight vWeight,
                                          String hStyle, String vStyle,
                                          Corner corner, boolean rounded) {
        // Handle double style
        boolean hDouble = "double".equals(hStyle);
        boolean vDouble = "double".equals(vStyle);

        // Normalize to effective weight for double
        Weight hEff = hDouble ? Weight.DOUBLE : hWeight;
        Weight vEff = vDouble ? Weight.DOUBLE : vWeight;

        // Rounded corners (only for light lines, no double)
        if (rounded && hEff == Weight.LIGHT && vEff == Weight.LIGHT) {
            return switch (corner) {
                case TOP_LEFT -> '╭';
                case TOP_RIGHT -> '╮';
                case BOTTOM_LEFT -> '╰';
                case BOTTOM_RIGHT -> '╯';
            };
        }

        return switch (corner) {
            case TOP_LEFT -> topLeftCorner(hEff, vEff);
            case TOP_RIGHT -> topRightCorner(hEff, vEff);
            case BOTTOM_LEFT -> bottomLeftCorner(hEff, vEff);
            case BOTTOM_RIGHT -> bottomRightCorner(hEff, vEff);
        };
    }

    // ---- Top-Left Corner (horizontal goes right, vertical goes down) ----

    private static char topLeftCorner(Weight h, Weight v) {
        if (h == Weight.NONE) {
            return switch (v) {
                case LIGHT -> '╷';
                case HEAVY -> '╻';
                case DOUBLE -> '║';
                default -> ' ';
            };
        }
        if (v == Weight.NONE) {
            return switch (h) {
                case LIGHT -> '╶';
                case HEAVY -> '╺';
                case DOUBLE -> '═';
                default -> ' ';
            };
        }
        return switch (h) {
            case LIGHT -> switch (v) {
                case LIGHT -> '┌';
                case HEAVY -> '┎';
                case DOUBLE -> '╓';
                default -> '┌';
            };
            case HEAVY -> switch (v) {
                case LIGHT -> '┍';
                case HEAVY -> '┏';
                case DOUBLE -> '┏'; // no exact match, fallback
                default -> '┏';
            };
            case DOUBLE -> switch (v) {
                case LIGHT -> '╒';
                case HEAVY -> '╒'; // no exact match, fallback
                case DOUBLE -> '╔';
                default -> '╔';
            };
            default -> '┌';
        };
    }

    // ---- Top-Right Corner (horizontal goes left, vertical goes down) ----

    private static char topRightCorner(Weight h, Weight v) {
        if (h == Weight.NONE) {
            return switch (v) {
                case LIGHT -> '╷';
                case HEAVY -> '╻';
                case DOUBLE -> '║';
                default -> ' ';
            };
        }
        if (v == Weight.NONE) {
            return switch (h) {
                case LIGHT -> '╴';
                case HEAVY -> '╸';
                case DOUBLE -> '═';
                default -> ' ';
            };
        }
        return switch (h) {
            case LIGHT -> switch (v) {
                case LIGHT -> '┐';
                case HEAVY -> '┒';
                case DOUBLE -> '╖';
                default -> '┐';
            };
            case HEAVY -> switch (v) {
                case LIGHT -> '┑';
                case HEAVY -> '┓';
                case DOUBLE -> '┓'; // fallback
                default -> '┓';
            };
            case DOUBLE -> switch (v) {
                case LIGHT -> '╕';
                case HEAVY -> '╕'; // fallback
                case DOUBLE -> '╗';
                default -> '╗';
            };
            default -> '┐';
        };
    }

    // ---- Bottom-Left Corner (horizontal goes right, vertical goes up) ----

    private static char bottomLeftCorner(Weight h, Weight v) {
        if (h == Weight.NONE) {
            return switch (v) {
                case LIGHT -> '╵';
                case HEAVY -> '╹';
                case DOUBLE -> '║';
                default -> ' ';
            };
        }
        if (v == Weight.NONE) {
            return switch (h) {
                case LIGHT -> '╶';
                case HEAVY -> '╺';
                case DOUBLE -> '═';
                default -> ' ';
            };
        }
        return switch (h) {
            case LIGHT -> switch (v) {
                case LIGHT -> '└';
                case HEAVY -> '┖';
                case DOUBLE -> '╙';
                default -> '└';
            };
            case HEAVY -> switch (v) {
                case LIGHT -> '┕';
                case HEAVY -> '┗';
                case DOUBLE -> '┗'; // fallback
                default -> '┗';
            };
            case DOUBLE -> switch (v) {
                case LIGHT -> '╘';
                case HEAVY -> '╘'; // fallback
                case DOUBLE -> '╚';
                default -> '╚';
            };
            default -> '└';
        };
    }

    // ---- Bottom-Right Corner (horizontal goes left, vertical goes up) ----

    private static char bottomRightCorner(Weight h, Weight v) {
        if (h == Weight.NONE) {
            return switch (v) {
                case LIGHT -> '╵';
                case HEAVY -> '╹';
                case DOUBLE -> '║';
                default -> ' ';
            };
        }
        if (v == Weight.NONE) {
            return switch (h) {
                case LIGHT -> '╴';
                case HEAVY -> '╸';
                case DOUBLE -> '═';
                default -> ' ';
            };
        }
        return switch (h) {
            case LIGHT -> switch (v) {
                case LIGHT -> '┘';
                case HEAVY -> '┚';
                case DOUBLE -> '╜';
                default -> '┘';
            };
            case HEAVY -> switch (v) {
                case LIGHT -> '┙';
                case HEAVY -> '┛';
                case DOUBLE -> '┛'; // fallback
                default -> '┛';
            };
            case DOUBLE -> switch (v) {
                case LIGHT -> '╛';
                case HEAVY -> '╛'; // fallback
                case DOUBLE -> '╝';
                default -> '╝';
            };
            default -> '┘';
        };
    }

    // ==================== Box Wrapping ====================

    /**
     * Resolve a BorderSide to its TUI weight.
     *
     * @param side the border side specification
     * @param ctx  render context with unit resolver and metrics
     */
    public static Weight weightOf(BoxBorder.BorderSide side, RenderContext ctx) {
        if (!side.isVisible()) return Weight.NONE;
        if ("double".equals(side.style())) return Weight.DOUBLE;
        SizeValue sv = SizeValue.parse(side.width());
        if (sv == null) return Weight.BLOCK_1_8;
        return pixelsToWeight(sv.toPixels(ctx));
    }

    /**
     * Map a pixel size to a TUI border weight tier.
     *
     * <p>Uses block characters exclusively — the continuous spectrum from ▏ (1/8)
     * to █ (full) provides better proportional accuracy than mixing line-drawing
     * and block characters. With TUI_DEFAULT metrics (1em = 10px), each 1/8
     * character fraction maps exactly to its corresponding block character:
     * <pre>
     * 0.125em = 1.25px → BLOCK_1_8 (▏)
     * 0.25em  = 2.5px  → BLOCK_1_4 (▎)
     * 0.375em = 3.75px → BLOCK_3_8 (▍)
     * 0.5em   = 5.0px  → BLOCK_1_2 (▌)
     * 0.625em = 6.25px → BLOCK_5_8 (▋)
     * 0.75em  = 7.5px  → BLOCK_3_4 (▊)
     * 0.875em = 8.75px → BLOCK_7_8 (▉)
     * 1em     = 10px   → FULL      (█)
     * </pre>
     */
    public static Weight pixelsToWeight(double px) {
        if (px <= 0) return Weight.NONE;
        if (px <= 1.25) return Weight.BLOCK_1_8;
        if (px <= 2.5) return Weight.BLOCK_1_4;
        if (px <= 3.75) return Weight.BLOCK_3_8;
        if (px <= 5.0) return Weight.BLOCK_1_2;
        if (px <= 6.25) return Weight.BLOCK_5_8;
        if (px <= 7.5) return Weight.BLOCK_3_4;
        if (px <= 8.75) return Weight.BLOCK_7_8;
        return Weight.FULL;
    }

    /**
     * Wrap content lines in a border.
     *
     * @param contentLines content already rendered (may contain ANSI codes)
     * @param border       per-side border specification
     * @param innerWidth   content width in chars, or -1 for auto (max line width)
     * @param padTop       padding rows above content
     * @param padRight     padding columns right of content
     * @param padBottom    padding rows below content
     * @param padLeft      padding columns left of content
     * @param rounded      whether to use rounded corners
     * @param ctx          render context for unit resolution
     * @return lines of the bordered box (including border rows)
     */
    public static List<String> wrapInBorder(List<String> contentLines,
                                            BoxBorder border,
                                            int innerWidth,
                                            int padTop, int padRight,
                                            int padBottom, int padLeft,
                                            boolean rounded,
                                            RenderContext ctx) {
        return wrapInBorder(contentLines, border, innerWidth,
                padTop, padRight, padBottom, padLeft, rounded, ctx, null);
    }

    /**
     * Wrap content lines in border characters with optional ANSI border color.
     *
     * <p>When {@code borderColor} is non-null, each border character gets its own
     * color code + reset, preventing content ANSI resets from killing the border color.
     */
    public static List<String> wrapInBorder(List<String> contentLines,
                                            BoxBorder border,
                                            int innerWidth,
                                            int padTop, int padRight,
                                            int padBottom, int padLeft,
                                            boolean rounded,
                                            RenderContext ctx,
                                            String borderColor) {
        // Determine weights for each side
        Weight topW = weightOf(border.top(), ctx);
        Weight rightW = weightOf(border.right(), ctx);
        Weight bottomW = weightOf(border.bottom(), ctx);
        Weight leftW = weightOf(border.left(), ctx);

        // Calculate content width
        int maxContentWidth = 0;
        for (String line : contentLines) {
            int w = stripAnsiLength(line);
            if (w > maxContentWidth) maxContentWidth = w;
        }
        int contentWidth = innerWidth > 0 ? innerWidth : maxContentWidth;
        int paddedWidth = padLeft + contentWidth + padRight;

        // Get border characters
        String topStyle = border.top().style();
        String rightStyle = border.right().style();
        String bottomStyle = border.bottom().style();
        String leftStyle = border.left().style();

        // Determine if borders take up character cells
        boolean hasTop = topW != Weight.NONE;
        boolean hasRight = rightW != Weight.NONE;
        boolean hasBottom = bottomW != Weight.NONE;
        boolean hasLeft = leftW != Weight.NONE;

        int leftCols = hasLeft ? 1 : 0;
        int rightCols = hasRight ? 1 : 0;

        // ANSI helpers — wrap border characters with color if provided
        String bc = borderColor != null ? borderColor : "";
        String br = borderColor != null ? "\u001B[0m" : "";

        List<String> result = new ArrayList<>();

        // Top border row
        if (hasTop) {
            StringBuilder topRow = new StringBuilder();
            if (bc.length() > 0) topRow.append(bc);
            if (hasLeft) {
                topRow.append(resolveCorner(topW, leftW, topStyle, leftStyle,
                        Corner.TOP_LEFT, rounded));
            }
            char topChar = topW.isBlock() ? topBlockChar(topW)
                    : horizontalChar(topStyle, topW);
            topRow.append(String.valueOf(topChar).repeat(paddedWidth));
            if (hasRight) {
                topRow.append(resolveCorner(topW, rightW, topStyle, rightStyle,
                        Corner.TOP_RIGHT, rounded));
            }
            if (br.length() > 0) topRow.append(br);
            result.add(topRow.toString());
        }

        // Top padding rows
        for (int i = 0; i < padTop; i++) {
            result.add(buildContentRow("", paddedWidth, contentWidth, padLeft, padRight,
                    leftW, rightW, leftStyle, rightStyle, hasLeft, hasRight, bc, br));
        }

        // Content rows
        for (String line : contentLines) {
            result.add(buildContentRow(line, paddedWidth, contentWidth, padLeft, padRight,
                    leftW, rightW, leftStyle, rightStyle, hasLeft, hasRight, bc, br));
        }

        // Bottom padding rows
        for (int i = 0; i < padBottom; i++) {
            result.add(buildContentRow("", paddedWidth, contentWidth, padLeft, padRight,
                    leftW, rightW, leftStyle, rightStyle, hasLeft, hasRight, bc, br));
        }

        // Bottom border row
        if (hasBottom) {
            StringBuilder bottomRow = new StringBuilder();
            if (bc.length() > 0) bottomRow.append(bc);
            if (hasLeft) {
                bottomRow.append(resolveCorner(bottomW, leftW, bottomStyle, leftStyle,
                        Corner.BOTTOM_LEFT, rounded));
            }
            char botChar = bottomW.isBlock() ? bottomBlockChar(bottomW)
                    : horizontalChar(bottomStyle, bottomW);
            bottomRow.append(String.valueOf(botChar).repeat(paddedWidth));
            if (hasRight) {
                bottomRow.append(resolveCorner(bottomW, rightW, bottomStyle, rightStyle,
                        Corner.BOTTOM_RIGHT, rounded));
            }
            if (br.length() > 0) bottomRow.append(br);
            result.add(bottomRow.toString());
        }

        return result;
    }

    /**
     * Build a single content row with left/right borders and padding (no border color).
     */
    private static String buildContentRow(String content, int paddedWidth,
                                          int contentWidth,
                                          int padLeft, int padRight,
                                          Weight leftW, Weight rightW,
                                          String leftStyle, String rightStyle,
                                          boolean hasLeft, boolean hasRight) {
        return buildContentRow(content, paddedWidth, contentWidth,
                padLeft, padRight, leftW, rightW, leftStyle, rightStyle,
                hasLeft, hasRight, "", "");
    }

    /**
     * Build a single content row with left/right borders, padding, and optional border color.
     *
     * <p>When {@code bc}/{@code br} are non-empty, border characters get their own
     * color codes so content ANSI resets don't kill the border color.
     */
    private static String buildContentRow(String content, int paddedWidth,
                                          int contentWidth,
                                          int padLeft, int padRight,
                                          Weight leftW, Weight rightW,
                                          String leftStyle, String rightStyle,
                                          boolean hasLeft, boolean hasRight,
                                          String bc, String br) {
        StringBuilder row = new StringBuilder();

        // Left border (with color)
        if (hasLeft) {
            if (bc.length() > 0) row.append(bc);
            row.append(leftW.isBlock() ? leftBlockChar(leftW)
                    : verticalChar(leftStyle, leftW));
            if (br.length() > 0) row.append(br);
        }

        // Left padding
        row.append(" ".repeat(padLeft));

        // Content (truncate with ellipsis if too wide, pad if too narrow)
        int contentLen = stripAnsiLength(content);
        if (contentLen > contentWidth && contentWidth > 1) {
            row.append(truncateAnsi(content, contentWidth - 1));
            row.append("…");
        } else {
            row.append(content);
            if (contentLen < contentWidth) {
                row.append(" ".repeat(contentWidth - contentLen));
            }
        }

        // Right padding
        row.append(" ".repeat(padRight));

        // Right border (with color)
        if (hasRight) {
            if (bc.length() > 0) row.append(bc);
            row.append(rightW.isBlock() ? rightBlockChar(rightW)
                    : verticalChar(rightStyle, rightW));
            if (br.length() > 0) row.append(br);
        }

        return row.toString();
    }

    /**
     * Calculate the terminal display width of a string, stripping ANSI escape codes
     * and accounting for wide characters (East Asian, emoji).
     *
     * <p>Uses ICU4J to determine character widths:
     * <ul>
     *   <li>East Asian Wide/Fullwidth characters → 2 columns</li>
     *   <li>Characters with Emoji_Presentation → 2 columns</li>
     *   <li>Combining marks, zero-width chars, variation selectors (incl. VS16) → 0 columns</li>
     *   <li>Everything else → 1 column</li>
     * </ul>
     *
     * <p>Note: VS16 (U+FE0F) is treated as zero-width. VTE-based terminals do not
     * honor VS16 for BMP emoji characters (e.g., ♟️ renders as 1 column, not 2).
     */
    static int stripAnsiLength(String s) {
        if (s == null || s.isEmpty()) return 0;
        int width = 0;
        boolean inEscape = false;
        int len = s.length();

        for (int i = 0; i < len; ) {
            char c = s.charAt(i);

            // Skip ANSI escape sequences
            if (c == '\u001B') {
                inEscape = true;
                i++;
                continue;
            }
            if (inEscape) {
                if (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z') {
                    inEscape = false;
                }
                i++;
                continue;
            }

            int cp = Character.codePointAt(s, i);
            int cpLen = Character.charCount(cp);
            width += codePointWidth(cp);
            i += cpLen;
        }
        return width;
    }

    /**
     * Determine the terminal display width of a single Unicode code point.
     */
    static int codePointWidth(int cp) {
        // Zero-width characters
        int type = Character.getType(cp);
        if (type == Character.NON_SPACING_MARK
                || type == Character.ENCLOSING_MARK
                || type == Character.FORMAT) {
            return 0;
        }
        // Variation selectors and zero-width special characters
        if ((cp >= 0xFE00 && cp <= 0xFE0F)          // variation selectors
                || (cp >= 0xE0100 && cp <= 0xE01EF)  // variation selectors supplement
                || cp == 0x200B   // zero-width space
                || cp == 0x200C   // zero-width non-joiner
                || cp == 0x200D   // zero-width joiner
                || cp == 0xFEFF) { // BOM / zero-width no-break space
            return 0;
        }

        // East Asian Wide or Fullwidth → 2 columns
        int eaw = UCharacter.getIntPropertyValue(cp, UProperty.EAST_ASIAN_WIDTH);
        if (eaw == UCharacter.EastAsianWidth.WIDE
                || eaw == UCharacter.EastAsianWidth.FULLWIDTH) {
            return 2;
        }

        // Emoji with default emoji presentation → 2 columns
        // This covers supplementary-plane emoji (🔒 🔑 📋 etc.) that terminals
        // always render wide. Characters with only Emoji=true but NOT
        // Emoji_Presentation (like ♟ ▶ ▼) stay at 1 column — VTE-based
        // terminals render these as narrow text glyphs.
        if (UCharacter.hasBinaryProperty(cp, UProperty.EMOJI_PRESENTATION)) {
            return 2;
        }

        return 1;
    }

    /**
     * Truncate an ANSI-coded string to a visible terminal width.
     *
     * <p>Walks the string by code point, tracking visible width using
     * {@link #codePointWidth(int)} while passing through ANSI escape
     * sequences without counting them.
     *
     * @param s        the string (may contain ANSI escape codes)
     * @param maxWidth maximum visible column width
     * @return truncated string with ANSI reset appended if needed
     */
    static String truncateAnsi(String s, int maxWidth) {
        if (s == null || s.isEmpty() || maxWidth <= 0) return "";

        StringBuilder result = new StringBuilder();
        int visibleLen = 0;
        boolean inEscape = false;
        boolean hadAnsi = false;
        int len = s.length();

        for (int i = 0; i < len; ) {
            char c = s.charAt(i);

            if (c == '\u001B') {
                inEscape = true;
                hadAnsi = true;
                result.append(c);
                i++;
                continue;
            }

            if (inEscape) {
                result.append(c);
                if (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z') {
                    inEscape = false;
                }
                i++;
                continue;
            }

            int cp = Character.codePointAt(s, i);
            int cpLen = Character.charCount(cp);
            int charWidth = codePointWidth(cp);

            if (visibleLen + charWidth > maxWidth) break;
            for (int j = i; j < i + cpLen; j++) {
                result.append(s.charAt(j));
            }
            visibleLen += charWidth;
            i += cpLen;
        }

        if (hadAnsi) {
            result.append("\u001B[0m");
        }

        return result.toString();
    }
}
