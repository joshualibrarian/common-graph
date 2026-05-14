package dev.everydaythings.graph.canonical;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Field-level canonical-walk hint — declares this field's position within an
 * {@link Layout.Kind#ARRAY}-laid-out class.
 *
 * <p>The walker traverses fields in ascending {@code value()} order to produce
 * a deterministic Merkle structure. For {@link Layout.Kind#MAP}-laid-out
 * classes this annotation has no effect — fields are keyed by name and sorted
 * canonically.
 *
 * <p>This is a canonical-identity hint only — wire-format encoding tricks
 * belong to the codec, not here.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Order {
    /** Position in the ARRAY layout. Lower values come first. */
    int value() default -1;
}
