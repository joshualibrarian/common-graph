package dev.everydaythings.graph.canonical;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface Canon {
    int order() default -1;

    boolean isRecord() default true;

    boolean isBody() default true;

    /**
     * Marks this field as a user-configurable setting.
     *
     * <p>Setting fields appear in the config tree and are editable via
     * the canonical editor. Not every serialized field is a setting —
     * internal state fields (sequence counters, active flags, etc.)
     * should remain {@code false}.
     *
     * <p>The field's Java type drives the editor widget: enum → dropdown,
     * boolean → toggle, String → text input, numeric → number input.
     */
    boolean setting() default false;
}
