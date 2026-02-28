package dev.everydaythings.graph.game.spades;

import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.SceneSchema;
import dev.everydaythings.graph.ui.scene.View;

public class SpadesSurface extends SceneSchema<SpadesGame> {

    public SpadesSurface() {}

    public static SpadesSurface from(SpadesGame game) {
        SpadesSurface surface = new SpadesSurface();
        surface.value(game);
        return surface;
    }

    public View toView() {
        return View.of(this);
    }

    @Scene.Container(direction = Scene.Direction.VERTICAL, padding = "0.5em", gap = "0.5em",
            style = {"spades"})
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

        @Scene.Container(direction = Scene.Direction.HORIZONTAL, gap = "0.75em",
                padding = "0.35em 0.5em", background = "#1E1E2C", cornerRadius = "0.25em")
        static class Teams {
            @Scene.Repeat(bind = "teamRows")
            @Scene.Text(bind = "$item")
            static class Team {}
        }

        @Scene.If("value.hasCurrentTrick")
        @Scene.Container(direction = Scene.Direction.VERTICAL, gap = "0.2em",
                padding = "0.35em 0.5em", background = "#2A2A3E", cornerRadius = "0.25em")
        static class Trick {
            @Scene.Text(content = "Current Trick", style = {"heading"})
            static class Heading {}

            @Scene.Repeat(bind = "currentTrickRows")
            @Scene.Text(bind = "$item")
            static class Card {}
        }

        @Scene.Container(direction = Scene.Direction.VERTICAL, gap = "0.2em",
                padding = "0.35em 0.5em", background = "#1E1E2C", cornerRadius = "0.25em")
        static class Seats {
            @Scene.Text(content = "Players", style = {"heading"})
            static class Heading {}

            @Scene.Repeat(bind = "seatRows")
            @Scene.Text(bind = "$item")
            static class Seat {}
        }
    }
}
