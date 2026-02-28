package dev.everydaythings.graph.item.action;

/**
 * Specification for an action/verb parameter.
 *
 * <p>Extracted from method annotations ({@code @Item.Param}) during class scanning.
 * Used by {@link dev.everydaythings.graph.item.VerbSpec},
 * {@link dev.everydaythings.graph.item.VerbEntry}, and
 * {@link dev.everydaythings.graph.item.VerbInvoker}.
 */
@lombok.Value
public class ParamSpec {
    @lombok.NonNull String name;
    @lombok.NonNull Class<?> type;
    String doc;
    boolean required;
    String defaultValue;
    /** Thematic role name (e.g., "THEME", "TARGET"). Null if not specified. */
    String role;
}
