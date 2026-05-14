package dev.everydaythings.graph.canonical;

import dev.everydaythings.graph.canonical.CgTag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level annotation controlling CG-CBOR encoding.
 *
 * <p>Specifies how the class encodes to CBOR:
 * <ul>
 *   <li>{@link #classType()} - ARRAY (ordered fields) or MAP (named fields)</li>
 *   <li>{@link #tag()} - CG-CBOR tag for type discrimination (see {@link CgTag})</li>
 * </ul>
 *
 * <p>Keys are always sorted deterministically (CTAP2 canonical form).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Canonization {
    Canonical.ClassCollectionType classType() default Canonical.ClassCollectionType.ARRAY;

    /**
     * CG-CBOR tag for this type.
     *
     * <p>When set to a value other than {@link CgTag#NONE}, the encoded CBOR
     * will be wrapped in this tag. On decode, the tag is used to determine
     * which concrete class to instantiate for polymorphic types.
     *
     * @return the CG-CBOR tag number, or {@link CgTag#NONE} for no tag
     */
    int tag() default CgTag.NONE;
}
