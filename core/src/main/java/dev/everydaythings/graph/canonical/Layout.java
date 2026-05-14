package dev.everydaythings.graph.canonical;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level canonical-walk hint — declares how a class lays out structurally
 * for Merkle hashing.
 *
 * <p>Two kinds:
 * <ul>
 *   <li>{@link Kind#ARRAY} — fields are walked in {@link Order} sequence; the
 *       hash position is determined by ordering. Default.</li>
 *   <li>{@link Kind#MAP} — fields are walked as a name-keyed multiset; the hash
 *       is sorted canonically by field name. Used for sparse / heterogeneous
 *       payloads (presentation tree nodes, theme overrides, etc.).</li>
 * </ul>
 *
 * <p>This annotation is purely about <i>canonical identity</i> — what shape the
 * Merkle walker sees. Wire-format concerns (CBOR tags, encoding tricks) belong
 * to the codec, which dispatches on Java type rather than reading this
 * annotation.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Layout {

    /** The layout kind — defaults to {@link Kind#ARRAY}. */
    Kind value() default Kind.ARRAY;

    /** Layout shapes recognised by the canonical walker. */
    enum Kind { ARRAY, MAP }
}
