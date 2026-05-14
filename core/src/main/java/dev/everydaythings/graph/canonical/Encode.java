package dev.everydaythings.graph.canonical;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an instance method that produces a leaf value's native wire form.
 *
 * <p>Used by codecs (CG-CBOR and friends) to encode "leaf" types — values
 * that carry their own self-contained binary or textual representation,
 * rather than being walked field-by-field as a structure. The presence of
 * any {@code @Encode} method on a class declares that class a leaf.
 *
 * <p>Dispatch is by return type. Each class may carry multiple {@code @Encode}
 * methods, one per wire form:
 *
 * <ul>
 *   <li>{@code @Encode byte[] encodeBinary()} — canonical binary form
 *       (used for hashing and for CBOR byte-string encoding).</li>
 *   <li>{@code @Encode String encodeText()} — canonical text form
 *       (used for multibase, display, and CBOR text-string encoding).</li>
 * </ul>
 *
 * <p>The {@code byte[]} form is the canonical hash form when both exist.
 * Pair with {@link Decode @Decode} static methods on the same class to
 * support round-tripping.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Encode {
}
