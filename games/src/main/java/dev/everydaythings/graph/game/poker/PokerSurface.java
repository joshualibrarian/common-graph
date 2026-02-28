package dev.everydaythings.graph.game.poker;

import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.SceneSchema;
import dev.everydaythings.graph.ui.scene.View;

public class PokerSurface extends SceneSchema<PokerGame> {

    public PokerSurface() {}

    public static PokerSurface from(PokerGame game) {
        PokerSurface surface = new PokerSurface();
        surface.value(game);
        return surface;
    }

    public View toView() {
        return View.of(this);
    }

    @Scene.Container(direction = Scene.Direction.VERTICAL, padding = "0.5em", gap = "0.5em",
            style = {"poker"})
    static class Root {

        @Scene.Text(bind = "statusText", style = {"heading"})
        static class Status {}

        @Scene.Container(direction = Scene.Direction.VERTICAL, gap = "0.2em",
                padding = "0.35em 0.5em", background = "#2A2A3E", cornerRadius = "0.25em")
        static class Meta {
            @Scene.Repeat(bind = "metaRows")
            @Scene.Text(bind = "$item")
            static class Row {}
        }

        @Scene.Container(direction = Scene.Direction.HORIZONTAL, gap = "0.4em",
                padding = "0.35em 0.5em", background = "#1E1E2C", cornerRadius = "0.25em")
        static class Community {
            @Scene.Text(content = "Board:", style = {"heading"})
            static class Heading {}

            @Scene.If("value.hasCommunityCards")
            @Scene.Repeat(bind = "communityRows")
            @Scene.Text(bind = "$item")
            static class Card {}

            @Scene.If("!value.hasCommunityCards")
            @Scene.Text(content = "(no community cards yet)")
            static class Empty {}
        }

        @Scene.Container(direction = Scene.Direction.VERTICAL, gap = "0.2em",
                padding = "0.35em 0.5em", background = "#1E1E2C", cornerRadius = "0.25em")
        static class Players {
            @Scene.Text(content = "Players", style = {"heading"})
            static class Heading {}

            @Scene.Repeat(bind = "playerRows")
            @Scene.Text(bind = "$item")
            static class Row {}
        }
    }
}
