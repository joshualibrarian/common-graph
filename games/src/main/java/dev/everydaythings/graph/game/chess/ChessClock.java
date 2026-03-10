package dev.everydaythings.graph.game.chess;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Canonical.Canon;
import dev.everydaythings.graph.item.component.Tick;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.Scene.Direction;
import dev.everydaythings.graph.ui.scene.surface.primitive.ClockFace;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Chess clock with two independent countdown timers and Fischer increment.
 *
 * <p>Holds time remaining for both sides, tracks which clock is running,
 * and provides {@link ClockFace} instances for visual embedding. The clock
 * starts paused — call {@link #start()} after the first move.
 *
 * <p>In 2D, renders via {@code @Scene.Container} with nested {@code ClockFaces}
 * and {@code Indicators} classes. In 3D, the box body has clock faces on the
 * front face and toggle indicators on the top face via {@code @Scene.Face}.
 *
 * @see ChessGame
 * @see ClockFace
 */
@Getter
@NoArgsConstructor
@Canonical.Canonization
@Scene.Container(direction = Direction.HORIZONTAL, gap = "0.25em",
        padding = "0.3em", width = "100%", height = "100%")
@Scene.Shape(type = "rectangle", fill = "#2A2A3E", stroke = "#6272A4",
        strokeWidth = "1px", cornerRadius = "0.45em", depth = "1cm")
@Scene.Body(shape = "box", width = "24cm", height = "7cm", depth = "9cm", color = 0x2A2A3E)
public class ChessClock implements Canonical {

    @Canon(order = 0) private int whiteMinutes;
    @Canon(order = 1) private int whiteSeconds;
    @Canon(order = 2) private int blackMinutes;
    @Canon(order = 3) private int blackSeconds;
    @Canon(order = 4) private boolean whiteActive;
    @Canon(order = 5) private boolean running;
    @Canon(order = 6, setting = true) private int incrementSeconds;

    // ==================================================================================
    // 2D scene layout — vertical stack of two timer sections
    // ==================================================================================

    /** Left side of the clock body: two timer faces stacked. */
    @Scene.Query("width >= 34ch")
    @Scene.Container(direction = Direction.VERTICAL, width = "86%",
            gap = "0.08em", style = {"align-center", "justify-center", "fill"})
    static class FrontFace2D {
        @Scene.Container(direction = Direction.HORIZONTAL, width = "100%", height = "49%",
                style = {"fill", "justify-center"}, align = "center")
        static class BlackRow {
            @Scene.Container(width = "100%", height = "100%")
            static class BlackFaceBox {
                @Scene.Embed(bind = "value.blackFace")
                static class Face {}
            }
        }

        @Scene.Container(direction = Direction.HORIZONTAL, width = "100%", height = "49%",
                style = {"fill", "justify-center"}, align = "center")
        static class WhiteRow {
            @Scene.Container(width = "100%", height = "100%")
            static class WhiteFaceBox {
                @Scene.Embed(bind = "value.whiteFace")
                static class Face {}
            }
        }
    }

    /** Compact 2D readout when the clock panel is too narrow for analog faces. */
    @Scene.Query("width < 34ch")
    @Scene.Container(direction = Direction.VERTICAL, width = "86%",
            gap = "0.08em", style = {"fill", "align-center", "justify-center"})
    static class CompactFrontFace2D {
        @Scene.Container(direction = Direction.HORIZONTAL, width = "100%", height = "49%",
                style = {"fill", "justify-center"}, align = "center")
        static class BlackRow {
            @Scene.Text(bind = "value.blackTimeLabel", style = {"heading", "monospace"})
            static class Time {}
        }

        @Scene.Container(direction = Direction.HORIZONTAL, width = "100%", height = "49%",
                style = {"fill", "justify-center"}, align = "center")
        static class WhiteRow {
            @Scene.Text(bind = "value.whiteTimeLabel", style = {"heading", "monospace"})
            static class Time {}
        }
    }

    /** Right side in 2D: unfolded top face panel with the two press buttons. */
    @Scene.Container(direction = Direction.VERTICAL, width = "13%",
            style = {"fill", "align-center", "justify-center"})
    @Scene.Shape(type = "rectangle", fill = "#23263A", stroke = "#6272A4",
            strokeWidth = "1px", cornerRadius = "0.35em", depth = "5mm")
    static class TopFace2D {
        @Scene.Container(direction = Direction.VERTICAL, gap = "0.12em",
                width = "100%", height = "100%", style = {"fill", "align-center", "justify-center"})
        static class Buttons {
            @Scene.Container(direction = Direction.HORIZONTAL, width = "100%", height = "48%",
                    style = {"fill", "justify-center"}, align = "center")
            static class BlackRow {
                @Scene.Container(direction = Direction.HORIZONTAL, width = "96%", aspectRatio = "2.4/1",
                        shape = "rectangle", cornerRadius = "0.35em",
                        background = "bind:value.blackButtonColor",
                        style = {"align-center", "justify-center"})
                @Scene.On(event = "click", action = "switchSide")
                static class BlackButton {}
            }

            @Scene.Container(direction = Direction.HORIZONTAL, width = "100%", height = "48%",
                    style = {"fill", "justify-center"}, align = "center")
            static class WhiteRow {
                @Scene.Container(direction = Direction.HORIZONTAL, width = "96%", aspectRatio = "2.4/1",
                        shape = "rectangle", cornerRadius = "0.35em",
                        background = "bind:value.whiteButtonColor",
                        style = {"align-center", "justify-center"})
                @Scene.On(event = "click", action = "switchSide")
                static class WhiteButton {}
            }
        }
    }

    // ==================================================================================
    // 3D body face panels (compiled independently by SpatialCompiler)
    // ==================================================================================

    /** Side face toward the board: two embedded ClockFaces stacked. */
    @Scene.Face(value = "front", ppm = 2048)
    @Scene.Container(direction = Direction.HORIZONTAL, gap = "0.05em",
            width = "100%", height = "100%", padding = "4%",
            style = {"align-center", "justify-center"})
    @Scene.Shape(type = "rectangle", fill = "#23263A", stroke = "#6272A4",
            strokeWidth = "1px")
    static class ClockFaces {
        @Scene.Container(width = "34%", height = "90%",
                style = {"align-center", "justify-center"})
        static class BlackClockSlot {
            @Scene.Embed(bind = "value.blackFace")
            static class BlackClock {
            }
        }

        @Scene.Container(width = "34%", height = "90%",
                style = {"align-center", "justify-center"})
        static class WhiteClockSlot {
            @Scene.Embed(bind = "value.whiteFace")
            static class WhiteClock {}
        }
    }

    /** Top face: physical button slabs. */
    @Scene.Face(value = "top", ppm = 2048)
    @Scene.Container(direction = Direction.VERTICAL,
            width = "100%", height = "100%", style = {"align-center", "justify-center"})
    static class Indicators {
        @Scene.Container(direction = Direction.HORIZONTAL, gap = "0.75em",
                width = "100%", height = "100%", style = {"align-center", "justify-center"})
        static class ToggleRow {
            @Scene.Shape(type = "rectangle", fill = "bind:value.blackButtonColor",
                    width = "34%", height = "36%", cornerRadius = "0.35em", depth = "8mm")
            @Scene.On(event = "click", action = "switchSide")
            static class BlackToggle {}

            @Scene.Shape(type = "rectangle", fill = "bind:value.whiteButtonColor",
                    width = "34%", height = "36%", cornerRadius = "0.35em", depth = "8mm")
            @Scene.On(event = "click", action = "switchSide")
            static class WhiteToggle {}
        }
    }

    private ChessClock(int minutes, int increment) {
        this.whiteMinutes = minutes;
        this.whiteSeconds = 0;
        this.blackMinutes = minutes;
        this.blackSeconds = 0;
        this.whiteActive = true;
        this.running = false;
        this.incrementSeconds = increment;
    }

    /**
     * Create a chess clock with the given time control.
     *
     * @param minutes   Starting minutes per side
     * @param increment Fischer increment in seconds per move
     */
    public static ChessClock create(int minutes, int increment) {
        return new ChessClock(minutes, increment);
    }

    // ==================================================================================
    // Clock faces (for embedding in scenes)
    // ==================================================================================

    /**
     * ClockFace for white's remaining time.
     * Uses minute hand for minutes and second hand for seconds.
     */
    public ClockFace whiteFace() {
        return new ClockFace(0, whiteMinutes, whiteSeconds, true);
    }

    /**
     * ClockFace for black's remaining time.
     */
    public ClockFace blackFace() {
        return new ClockFace(0, blackMinutes, blackSeconds, true);
    }

    // ==================================================================================
    // Indicator colors (for binding expressions in @Scene annotations)
    // ==================================================================================

    /** Green when black is active, muted when inactive. */
    public String blackIndicatorColor() {
        return !whiteActive ? "#50FA7B" : "#6272A4";
    }

    /** Green when white is active, muted when inactive. */
    public String whiteIndicatorColor() {
        return whiteActive ? "#50FA7B" : "#6272A4";
    }

    /** Top button color for black side (active side looks pressed/highlighted). */
    public String blackButtonColor() {
        return !whiteActive ? "#4A8F66" : "#3B3F59";
    }

    /** Top button color for white side (active side looks pressed/highlighted). */
    public String whiteButtonColor() {
        return whiteActive ? "#4A8F66" : "#3B3F59";
    }

    // ==================================================================================
    // State queries
    // ==================================================================================

    public boolean whiteActive() { return whiteActive; }
    public boolean blackActive() { return !whiteActive; }

    /**
     * Whether either player has run out of time.
     */
    public boolean flagged() {
        return (whiteMinutes == 0 && whiteSeconds == 0)
            || (blackMinutes == 0 && blackSeconds == 0);
    }

    /**
     * Formatted time label for white (e.g., "5:00").
     */
    public String whiteTimeLabel() {
        return formatTime(whiteMinutes, whiteSeconds);
    }

    /**
     * Formatted time label for black (e.g., "5:00").
     */
    public String blackTimeLabel() {
        return formatTime(blackMinutes, blackSeconds);
    }

    private static String formatTime(int minutes, int seconds) {
        return String.format("%d:%02d", minutes, seconds);
    }

    // ==================================================================================
    // Actions
    // ==================================================================================

    /**
     * Switch the active clock after a move is made.
     * Adds Fischer increment to the player who just moved.
     */
    public void switchSide() {
        if (whiteActive) {
            addSeconds(true, incrementSeconds);
        } else {
            addSeconds(false, incrementSeconds);
        }
        whiteActive = !whiteActive;
    }

    public void start() { running = true; }
    public void stop() { running = false; }

    /**
     * Tick — counts down the active player's time by one second.
     * Called every second by the tick registry.
     */
    @Tick(interval = 1000)
    public void tick() {
        if (!running || flagged()) return;
        if (whiteActive) {
            decrementOne(true);
        } else {
            decrementOne(false);
        }
    }

    // ==================================================================================
    // Internal time manipulation
    // ==================================================================================

    private void decrementOne(boolean white) {
        if (white) {
            if (whiteSeconds > 0) {
                whiteSeconds--;
            } else if (whiteMinutes > 0) {
                whiteMinutes--;
                whiteSeconds = 59;
            }
        } else {
            if (blackSeconds > 0) {
                blackSeconds--;
            } else if (blackMinutes > 0) {
                blackMinutes--;
                blackSeconds = 59;
            }
        }
    }

    private void addSeconds(boolean white, int secs) {
        if (secs <= 0) return;
        if (white) {
            whiteSeconds += secs;
            whiteMinutes += whiteSeconds / 60;
            whiteSeconds %= 60;
        } else {
            blackSeconds += secs;
            blackMinutes += blackSeconds / 60;
            blackSeconds %= 60;
        }
    }
}
