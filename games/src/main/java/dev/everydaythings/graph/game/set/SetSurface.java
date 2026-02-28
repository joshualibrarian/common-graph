package dev.everydaythings.graph.game.set;

import dev.everydaythings.graph.ui.scene.View;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.SceneSchema;

/**
 * 2D scene for the Set card game via annotation-driven templates.
 *
 * <p>Uses {@code @Scene.Repeat} to iterate over the dynamic tableau
 * and player scores. Conditional sections ({@code @Scene.If}) show
 * the tableau only after the game starts, and scores only when players
 * are seated.
 *
 * @see SetGame
 * @see SetGame.CardView
 * @see SetGame.PlayerScore
 */
public class SetSurface extends SceneSchema<SetGame> {

    public SetSurface() {}

    public static SetSurface from(SetGame game) {
        SetSurface surface = new SetSurface();
        surface.value(game);
        return surface;
    }

    public View toView() {
        return View.of(this);
    }

    // ==================================================================================
    // Declarative UI
    // ==================================================================================

    @Scene.Container(direction = Scene.Direction.VERTICAL, style = {"set-game"})
    static class Root {

        /** Status heading: waiting, in progress, or game over. */
        @Scene.Text(bind = "describeStatus", style = {"heading"})
        static class Status {}

        /** Score bar — only shown when players are seated. */
        @Scene.If("value.seatedCount")
        @Scene.Container(direction = Scene.Direction.HORIZONTAL, style = {"scores"})
        static class ScoreBar {

            @Scene.Repeat(bind = "playerScores")
            @Scene.Text(bind = "$item.label", style = {"score"})
            static class Score {}
        }

        /** Tableau of face-up cards — only shown after game starts. */
        @Scene.If("value.isStarted")
        @Scene.Container(direction = Scene.Direction.HORIZONTAL, style = {"tableau", "wrap"})
        static class Tableau {

            @Scene.Repeat(bind = "tableauCards")
            @Scene.Container(direction = Scene.Direction.VERTICAL, style = {"card"})
            @Scene.On(event = "click", action = "select", target = "$item.ordinal")
            static class Card {

                /** Card symbol (e.g. ♦♦♦). */
                @Scene.Text(bind = "$item.symbol", style = {"card-symbol"})
                static class Symbol {}

                /** Card description (e.g. "3 solid red diamonds"). */
                @Scene.Text(bind = "$item.description", style = {"card-label"})
                static class Label {}
            }
        }

        /** Deck count — only shown after game starts. */
        @Scene.If("value.isStarted")
        @Scene.Text(bind = "deckLabel", style = {"deck-count"})
        static class DeckCount {}
    }
}
