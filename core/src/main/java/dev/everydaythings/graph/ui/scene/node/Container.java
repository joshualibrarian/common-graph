package dev.everydaythings.graph.ui.scene.node;

import dev.everydaythings.graph.Canonical;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * The structural primitive — has children and layout.
 *
 * <p>Everything that groups or arranges other nodes is a Container.
 * Buttons, lists, trees, grids, forms, panels — all containers
 * with different properties and children.
 *
 * <p>In 2D: renders as a laid-out box (flex, grid, stack).
 * In 3D: renders as a spatial group (children positioned in space).
 */
@Getter
@Accessors(fluent = true)
@Canonical.Canonization
public class Container extends Node {

    /** Layout mode for arranging children. */
    @Canon(order = 100)
    private String layout = "vertical";

    /** Gap between children. */
    @Canon(order = 101)
    private String gap;

    /** Cross-axis alignment. */
    @Canon(order = 102)
    private String align;

    /** Main-axis justification. */
    @Canon(order = 103)
    private String justify;

    /** Whether children wrap to next line. */
    @Canon(order = 104)
    private boolean wrap;

    /** Grid columns (when layout = "grid"). */
    @Canon(order = 105)
    private int columns;

    /** Grid rows (when layout = "grid"). */
    @Canon(order = 106)
    private int rows;

    /** Child nodes. */
    @Canon(order = 110)
    private List<Node> children;

    /** Repeat expression — generate children from a data collection. */
    @Canon(order = 111)
    private String repeat;

    /** Template for repeated children. */
    @Canon(order = 112)
    private Node childTemplate;

    // ==================================================================================
    // Constructors
    // ==================================================================================

    public Container() {}

    public Container(String layout) {
        this.layout = layout;
    }

    // ==================================================================================
    // Factories
    // ==================================================================================

    public static Container vertical() { return new Container("vertical"); }
    public static Container horizontal() { return new Container("horizontal"); }
    public static Container grid(int columns, int rows) {
        Container c = new Container("grid");
        c.columns = columns;
        c.rows = rows;
        return c;
    }
    public static Container stack() { return new Container("stack"); }

    // ==================================================================================
    // Child Management
    // ==================================================================================

    public Container add(Node child) {
        if (children == null) children = new ArrayList<>();
        children.add(child);
        return this;
    }

    public Container add(Node... nodes) {
        if (children == null) children = new ArrayList<>();
        for (Node n : nodes) children.add(n);
        return this;
    }

    // ==================================================================================
    // Fluent Setters
    // ==================================================================================

    public Container layout(String layout) { this.layout = layout; return this; }
    public Container gap(String gap) { this.gap = gap; return this; }
    public Container align(String align) { this.align = align; return this; }
    public Container justify(String justify) { this.justify = justify; return this; }
    public Container wrap(boolean wrap) { this.wrap = wrap; return this; }
    public Container columns(int c) { this.columns = c; return this; }
    public Container rows(int r) { this.rows = r; return this; }
    public Container repeat(String expr) { this.repeat = expr; return this; }
    public Container childTemplate(Node template) { this.childTemplate = template; return this; }
}
