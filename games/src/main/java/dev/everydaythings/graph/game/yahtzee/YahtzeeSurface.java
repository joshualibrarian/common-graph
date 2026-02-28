package dev.everydaythings.graph.game.yahtzee;

import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.SceneSchema;
import dev.everydaythings.graph.ui.scene.View;

/**
 * 2D scene for Yahtzee — dice tray, roll button, and scorecard.
 *
 * <p>Layout:
 * <pre>
 * ╔══════════════════════════════════╗
 * ║  Player 1's turn — roll (3)     ║  Status
 * ║  P1: 42 | P2: 38               ║  Score bar
 * ╠══════════════════════════════════╣
 * ║  [⚃] [⚁] [⚅*] [⚂] [⚄*]       ║  Dice (held = highlighted)
 * ║         [Roll Again (2)]        ║  Roll button
 * ╠══════════════════════════════════╣
 * ║  Ones .............. [ 3] [  ] ║
 * ║  Twos .............. [  ] [ 8] ║  Scorecard rows
 * ║  ...                            ║  (potential scores shown
 * ║  Full House ........ [25] [  ] ║   for available categories)
 * ║  Yahtzee ........... [50] [  ] ║
 * ║──────────────────────────────── ║
 * ║  Total: 142                     ║
 * ╚══════════════════════════════════╝
 * </pre>
 *
 * @see YahtzeeGame
 */
public class YahtzeeSurface extends SceneSchema<YahtzeeGame> {

    public YahtzeeSurface() {}

    public static YahtzeeSurface from(YahtzeeGame game) {
        YahtzeeSurface surface = new YahtzeeSurface();
        surface.value(game);
        return surface;
    }

    public View toView() {
        return View.of(this);
    }

    // ==================================================================================
    // Scene Structure
    // ==================================================================================

    @Scene.Container(direction = Scene.Direction.VERTICAL, padding = "0.5em", gap = "0.5em",
                     style = {"yahtzee"})
    static class Root {

        /** Turn status heading. */
        @Scene.Text(bind = "statusText", style = {"heading"})
        static class Status {}

        /** Player score bar — shows all players' totals. */
        @Scene.If("value.seatedCount")
        @Scene.Container(direction = Scene.Direction.HORIZONTAL, gap = "1em",
                         style = {"score-bar"}, padding = "0.25em 0.5em")
        static class ScoreBar {
            @Scene.Repeat(bind = "playerScoreLabels")
            @Scene.Text(bind = "$item", style = {"score-label"})
            static class PlayerScore {}
        }

        /** Dice tray — 5 dice in a row. */
        @Scene.Container(direction = Scene.Direction.HORIZONTAL, gap = "0.5em",
                         style = {"dice-tray"}, padding = "0.5em",
                         align = "center")
        static class DiceTray {
            @Scene.Repeat(bind = "diceViews")
            @Scene.Container(direction = Scene.Direction.STACK,
                             width = "2.5em", height = "2.5em",
                             style = {"die"}, cornerRadius = "0.3em",
                             background = "#FAFAFA", align = "center")
            @Scene.State(when = "$item.held", style = {"die-held"})
            @Scene.On(event = "click", action = "keep", target = "$index")
            static class DieSlot {

                @Scene.Container(direction = Scene.Direction.VERTICAL,
                                 gap = "0.15em", align = "center",
                                 width = "100%", height = "100%",
                                 padding = "0.3em")
                static class PipGrid {

                    // Top row: TL TC TR
                    @Scene.Container(direction = Scene.Direction.HORIZONTAL,
                                     gap = "0.15em")
                    static class TopRow {
                        @Scene.Container(width = "0.6em", height = "0.6em",
                                         direction = Scene.Direction.STACK,
                                         align = "center")
                        static class SlotTL {
                            @Scene.If("$item.hasTL")
                            @Scene.Shape(type = "circle",
                                         width = "0.5em", height = "0.5em",
                                         fill = "#1A1A1A")
                            static class Pip {}
                        }

                        @Scene.Container(width = "0.6em", height = "0.6em",
                                         direction = Scene.Direction.STACK,
                                         align = "center")
                        static class SlotTC {
                            @Scene.If("$item.hasTC")
                            @Scene.Shape(type = "circle",
                                         width = "0.5em", height = "0.5em",
                                         fill = "#1A1A1A")
                            static class Pip {}
                        }

                        @Scene.Container(width = "0.6em", height = "0.6em",
                                         direction = Scene.Direction.STACK,
                                         align = "center")
                        static class SlotTR {
                            @Scene.If("$item.hasTR")
                            @Scene.Shape(type = "circle",
                                         width = "0.5em", height = "0.5em",
                                         fill = "#1A1A1A")
                            static class Pip {}
                        }
                    }

                    // Middle row: ML MC MR
                    @Scene.Container(direction = Scene.Direction.HORIZONTAL,
                                     gap = "0.15em")
                    static class MiddleRow {
                        @Scene.Container(width = "0.6em", height = "0.6em",
                                         direction = Scene.Direction.STACK,
                                         align = "center")
                        static class SlotML {
                            @Scene.If("$item.hasML")
                            @Scene.Shape(type = "circle",
                                         width = "0.5em", height = "0.5em",
                                         fill = "#1A1A1A")
                            static class Pip {}
                        }

                        @Scene.Container(width = "0.6em", height = "0.6em",
                                         direction = Scene.Direction.STACK,
                                         align = "center")
                        static class SlotMC {
                            @Scene.If("$item.hasMC")
                            @Scene.Shape(type = "circle",
                                         width = "0.5em", height = "0.5em",
                                         fill = "#1A1A1A")
                            static class Pip {}
                        }

                        @Scene.Container(width = "0.6em", height = "0.6em",
                                         direction = Scene.Direction.STACK,
                                         align = "center")
                        static class SlotMR {
                            @Scene.If("$item.hasMR")
                            @Scene.Shape(type = "circle",
                                         width = "0.5em", height = "0.5em",
                                         fill = "#1A1A1A")
                            static class Pip {}
                        }
                    }

                    // Bottom row: BL BC BR
                    @Scene.Container(direction = Scene.Direction.HORIZONTAL,
                                     gap = "0.15em")
                    static class BottomRow {
                        @Scene.Container(width = "0.6em", height = "0.6em",
                                         direction = Scene.Direction.STACK,
                                         align = "center")
                        static class SlotBL {
                            @Scene.If("$item.hasBL")
                            @Scene.Shape(type = "circle",
                                         width = "0.5em", height = "0.5em",
                                         fill = "#1A1A1A")
                            static class Pip {}
                        }

                        @Scene.Container(width = "0.6em", height = "0.6em",
                                         direction = Scene.Direction.STACK,
                                         align = "center")
                        static class SlotBC {
                            @Scene.If("$item.hasBC")
                            @Scene.Shape(type = "circle",
                                         width = "0.5em", height = "0.5em",
                                         fill = "#1A1A1A")
                            static class Pip {}
                        }

                        @Scene.Container(width = "0.6em", height = "0.6em",
                                         direction = Scene.Direction.STACK,
                                         align = "center")
                        static class SlotBR {
                            @Scene.If("$item.hasBR")
                            @Scene.Shape(type = "circle",
                                         width = "0.5em", height = "0.5em",
                                         fill = "#1A1A1A")
                            static class Pip {}
                        }
                    }
                }
            }
        }

        /** Roll button — shown when rolling is possible. */
        @Scene.If("value.canRoll")
        @Scene.Container(direction = Scene.Direction.HORIZONTAL, align = "center",
                         padding = "0.25em 0")
        static class RollRow {
            @Scene.Container(style = {"button", "roll-button"}, padding = "0.5em 1.5em",
                             cornerRadius = "0.25em", background = "#4CAF50",
                             direction = Scene.Direction.HORIZONTAL)
            @Scene.On(event = "click", action = "roll")
            static class RollButton {
                @Scene.Text(bind = "rollLabel", style = {"button-text"})
                static class Label {}
            }
        }

        /** Scorecard — category rows with scores and potentials. */
        @Scene.Container(direction = Scene.Direction.VERTICAL, gap = "0.125em",
                         style = {"scorecard"}, padding = "0.5em",
                         background = "#2A2A3E", cornerRadius = "0.25em")
        static class Scorecard {

            @Scene.Text(content = "Scorecard", style = {"scorecard-title"})
            static class Title {}

            @Scene.Repeat(bind = "scorecardRows")
            @Scene.Container(direction = Scene.Direction.HORIZONTAL, gap = "0.5em",
                             padding = "0.125em 0.25em", style = {"scorecard-row"})
            @Scene.State(when = "$item.available", style = {"row-available"})
            @Scene.State(when = "$item.scored", style = {"row-scored"})
            @Scene.State(when = "$item.separator", style = {"row-separator"})
            @Scene.On(event = "click", action = "score", target = "$item.categoryKey",
                      when = "$item.available")
            static class Row {

                /** Category name. */
                @Scene.Text(bind = "$item.label", style = {"category-name"})
                static class Name {}

                /** Scored value (filled in after scoring). */
                @Scene.If("$item.scored")
                @Scene.Text(bind = "$item.scoredValue", style = {"scored-value"})
                static class Scored {}

                /** Potential value (shown for available categories). */
                @Scene.If("$item.available")
                @Scene.Text(bind = "$item.potentialValue", style = {"potential-value"})
                static class Potential {}
            }
        }
    }
}
