package dev.everydaythings.graph.ui.scene.surface.primitive;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Canonical.Canon;
import dev.everydaythings.graph.item.Tick;
import dev.everydaythings.graph.item.Type;
import dev.everydaythings.graph.ui.scene.Transition;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.Scene.Direction;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.List;

/**
 * Clock widget — visual definition and ephemeral time display.
 *
 * <p>The canonical identity is the clock's visual definition (analog vs digital).
 * Time values (hour, minute, second) are transient runtime state — set before
 * each render by the clock's source (wall clock, chess timer, etc.).
 *
 * <p>Provides computed angles for hour, minute, and second hands,
 * plus digital time display. Angles are in degrees with 0 = 12 o'clock.
 *
 * <p>Uses container queries for responsive layout:
 * <ul>
 *   <li>Wide containers (≥ 30ch): full analog clock face with ticks, hands, and circle</li>
 *   <li>Narrow containers (&lt; 30ch): compact digital time display</li>
 * </ul>
 */
@Getter
@NoArgsConstructor
@Canonical.Canonization
@Type(value = "cg:type/clock", glyph = "🕐")
@Scene.Container(direction = Direction.STACK, style = "clock", width = "100%", height = "100%")
@Scene.Shape(type = "circle", fill = "#1E1E2E", stroke = "#CDD6F4", strokeWidth = "1%")
public class ClockFace implements Canonical {

    // --- Canonical: visual definition ---

    @Canon(order = 0)
    private boolean analog;

    // --- Transient: current time (set before render, never serialized) ---

    private transient int hour;
    private transient int minute;
    private transient int second;

    /** Create a clock face with the given display mode and initial time. */
    public ClockFace(int hour, int minute, int second, boolean analog) {
        this.analog = analog;
        this.hour = hour;
        this.minute = minute;
        this.second = second;
    }

    /** Create a clock face definition (analog or digital). Time is set separately. */
    public ClockFace(boolean analog) {
        this.analog = analog;
    }

    /** Set the displayed time. Called before each render by the time source. */
    public void setTime(int hour, int minute, int second) {
        this.hour = hour;
        this.minute = minute;
        this.second = second;
    }

    // --- Context menu — toggle between analog and digital ---

    @Scene.ContextMenu(label = "Digital Mode", action = "toggleMode",
            when = "value.analog", icon = "\uD83D\uDD22")
    @Scene.ContextMenu(label = "Analog Mode", action = "toggleMode",
            when = "!value.analog", icon = "\uD83D\uDD50")
    static class Menu {}

    // --- Tick marks (12 hour positions) — analog only ---

    @Scene.Query("width >= 30ch")
    @Scene.Repeat(bind = "value.tickAngles")
    @Scene.Container(direction = Direction.VERTICAL, rotation = "bind:$item",
            transformOrigin = "bottom", height = "50%",
            padding = "2% 0 0 0", style = {"tick", "align-center"}, depth = "3mm")
    @Scene.Shape(type = "rectangle", fill = "#CDD6F4", width = "0.6%", height = "10%")
    static class Tick {}

    // --- Center dot — analog only ---

    @Scene.Query("width >= 30ch")
    @Scene.Container(style = {"align-center", "justify-center"}, depth = "15mm")
    @Scene.Shape(type = "circle", fill = "#CDD6F4", size = "3%")
    static class CenterDot {}

    // --- Digital overlay (shown in analog digital mode when wide enough) ---

    @Scene.Query("width >= 30ch")
    @Scene.Container(style = {"align-center", "justify-center"}, depth = "15mm")
    @Scene.If("value.digital")
    static class DigitalOverlay {
        @Scene.Text(bind = "value.digitalTime", style = {"clock-digital", "heading", "monospace"})
        static class Time {}
    }

    // --- Compact digital fallback (when container is too narrow for analog) ---

    @Scene.Query("width < 30ch")
    @Scene.Container(style = {"align-center", "justify-center"})
    static class CompactDigital {
        @Scene.Text(bind = "value.digitalTime", style = {"clock-digital", "heading", "monospace"})
        static class Time {}
    }

    // ==================================================================================
    // Factory methods
    // ==================================================================================

    /** Create a ClockFace — defaults to current time, analog mode. */
    public static ClockFace create() {
        return now(true);
    }

    /** Create a ClockFace for the current time. */
    public static ClockFace now(boolean analog) {
        LocalTime t = LocalTime.now();
        return new ClockFace(t.getHour(), t.getMinute(), t.getSecond(), analog);
    }

    /** Create a ClockFace for the current time in analog mode. */
    public static ClockFace now() {
        return now(true);
    }

    /** Update this clock to the current time. */
    @dev.everydaythings.graph.item.Tick(interval = 1000)
    public void tick() {
        LocalTime t = LocalTime.now();
        this.hour = t.getHour();
        this.minute = t.getMinute();
        this.second = t.getSecond();
    }

    /** Toggle analog/digital mode. */
    public void toggleMode() {
        this.analog = !this.analog;
    }

    // ==================================================================================
    // Computed properties (bound by surface expressions)
    // ==================================================================================

    /** Hour hand angle in degrees (0 = 12 o'clock, clockwise). */
    @Scene.Query("width >= 30ch")
    @Scene.Container(direction = Direction.VERTICAL, rotation = "bind:value.hourAngle",
            transformOrigin = "bottom", height = "50%",
            style = {"hand", "align-center", "justify-end"}, depth = "6mm")
    @Scene.Shape(type = "rectangle", fill = "#CDD6F4", width = "2.4%", height = "64%")
    @Transition(property = "rotation", duration = 0.5, easing = "ease-out")
    public double hourAngle() {
        return (hour % 12 + minute / 60.0) * 30.0;
    }

    /** Minute hand angle in degrees. */
    @Scene.Query("width >= 30ch")
    @Scene.Container(direction = Direction.VERTICAL, rotation = "bind:value.minuteAngle",
            transformOrigin = "bottom", height = "50%",
            style = {"hand", "align-center", "justify-end"}, depth = "9mm")
    @Scene.Shape(type = "rectangle", fill = "#CDD6F4", width = "1.6%", height = "80%")
    @Transition(property = "rotation", duration = 0.3, easing = "ease-out")
    public double minuteAngle() {
        return (minute + second / 60.0) * 6.0;
    }

    /** Second hand angle in degrees. */
    @Scene.Query("width >= 30ch")
    @Scene.Container(direction = Direction.VERTICAL, rotation = "bind:value.secondAngle",
            transformOrigin = "bottom", height = "50%",
            style = {"hand", "align-center", "justify-end"}, depth = "12mm")
    @Scene.Shape(type = "rectangle", fill = "#F38BA8", width = "0.6%", height = "86%")
    @Transition(property = "rotation", duration = 0.15, easing = "linear")
    public double secondAngle() {
        return second * 6.0;
    }

    /** Whether to show digital overlay. */
    public boolean digital() {
        return !analog;
    }

    /** Formatted digital time string in plain numerals for reliable small-size rendering. */
    public String digitalTime() {
        if (hour > 0) {
            return String.format("%d:%02d:%02d", hour, minute, second);
        }
        return String.format("%d:%02d", minute, second);
    }

    /** Tick mark angles for the 12 hour positions (0, 30, 60, ... 330). */
    public List<Integer> tickAngles() {
        return List.of(0, 30, 60, 90, 120, 150, 180, 210, 240, 270, 300, 330);
    }
}
