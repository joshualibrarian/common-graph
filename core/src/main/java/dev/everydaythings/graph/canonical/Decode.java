package dev.everydaythings.graph.canonical;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a static factory method that reconstructs a leaf value from a single
 * wire-form input — the inverse of {@link Encode @Encode}.
 *
 * <p>Dispatch is by parameter type. Each class may carry multiple
 * {@code @Decode} methods, one per wire form:
 *
 * <ul>
 *   <li>{@code @Decode static T fromBinary(byte[] bytes)} — reconstructs from
 *       canonical binary form.</li>
 *   <li>{@code @Decode static T fromText(String text)} — reconstructs from
 *       canonical text form.</li>
 * </ul>
 *
 * <p>The annotated method must be {@code static} and take exactly one
 * parameter. Pair with {@link Encode @Encode} instance methods on the same
 * class to support round-tripping.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Decode {
}
