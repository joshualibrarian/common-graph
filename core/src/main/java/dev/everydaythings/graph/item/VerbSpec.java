package dev.everydaythings.graph.item;

import dev.everydaythings.graph.item.action.ParamSpec;
import dev.everydaythings.graph.item.id.ItemID;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Specification for a verb method discovered via @Verb annotation.
 *
 * <p>VerbSpec is the compile-time metadata about a verb, stored in
 * {@link ItemSchema} after class scanning. At runtime, this is used
 * to populate {@link Vocabulary} with {@link VerbEntry} instances.
 *
 * <p>VerbSpec uses Sememe references for language-agnostic dispatch.
 *
 * @param sememeId  The Sememe ID that identifies this verb
 * @param method    The Java method to invoke
 * @param doc       Implementation-specific documentation
 * @param params    Parameter specifications (reuses ParamSpec)
 * @param source    Whether this is an item or component verb
 */
public record VerbSpec(
        ItemID sememeId,
        Method method,
        String doc,
        List<ParamSpec> params,
        VerbSource source
) {
    /**
     * Source of a verb: item class or component class.
     */
    public enum VerbSource {
        /** Verb declared on an Item class via @Verb */
        ITEM,
        /** Verb declared on a component class via @Verb */
        COMPONENT
    }

    /**
     * Create a VerbSpec for an item-level verb.
     */
    public static VerbSpec itemVerb(ItemID sememeId, Method method, String doc,
                                     List<ParamSpec> params) {
        return new VerbSpec(sememeId, method, doc, params, VerbSource.ITEM);
    }

    /**
     * Create a VerbSpec for a component-level verb.
     */
    public static VerbSpec componentVerb(ItemID sememeId, Method method, String doc,
                                          List<ParamSpec> params) {
        return new VerbSpec(sememeId, method, doc, params, VerbSource.COMPONENT);
    }

    /**
     * Get the method name for display/debugging.
     */
    public String methodName() {
        return method.getName();
    }

    /**
     * Get the canonical key from the sememe ID.
     */
    public String sememeKey() {
        return sememeId.encodeText();
    }
}
