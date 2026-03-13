package dev.everydaythings.graph.item;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Metadata for verb method parameters and factory method parameters.
 *
 * <p>Provides UI hints: labels, documentation, picker types, conditional visibility.
 *
 * <p>Usage on verb parameters:
 * <pre>{@code
 * @Verb(value = "cg.verb:greet", doc = "Greet someone")
 * public void greet(@Param(value = "name", doc = "Person to greet") String name) { ... }
 * }</pre>
 *
 * <p>Usage on factory parameters:
 * <pre>{@code
 * @Factory(label = "Custom", glyph = "⚙️")
 * public static Library custom(
 *     @Param(label = "Backend", doc = "Storage backend type") Backend backend,
 *     @Param(label = "Path", showWhen = "backend != SKIPLIST",
 *            picker = Picker.DIRECTORY) Path rootPath
 * ) { ... }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Param {
    /** Parameter name for display / verb param name. If empty, uses the actual parameter name. */
    String value() default "";

    /** Display label for factory parameters. */
    String label() default "";

    /** Documentation/tooltip text. */
    String doc() default "";

    /** Whether this parameter is required. Default true. */
    boolean required() default true;

    /** Default value as string (parsed based on parameter type). */
    String defaultValue() default "";

    /**
     * The thematic role this parameter fills in the verb's semantic frame.
     * Defaults to THEME (the primary entity affected by the action).
     */
    String role() default "THEME";

    /**
     * Condition for when to show this parameter (factory UI).
     * Simple expression referencing other parameter names.
     */
    String showWhen() default "";

    /** UI picker/widget type for this parameter. AUTO detects based on parameter type. */
    Picker picker() default Picker.AUTO;
}
