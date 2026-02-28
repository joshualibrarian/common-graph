package dev.everydaythings.graph.item;

import dev.everydaythings.graph.item.action.ParamSpec;
import dev.everydaythings.graph.item.id.ItemID;
import lombok.Getter;
import lombok.Setter;

import java.lang.reflect.Method;
import java.util.List;

/**
 * A runtime verb entry in a {@link Vocabulary}.
 *
 * <p>VerbEntry represents a verb that can be invoked on an Item or Component,
 * using Sememe IDs for language-agnostic dispatch.
 *
 * <p>This is the runtime version of {@link VerbSpec} - it includes the
 * target instance that the method should be invoked on.
 */
@Getter
public class VerbEntry {

    private final ItemID sememeId;
    private final Method method;
    private final String doc;
    private final List<ParamSpec> params;
    private final VerbSpec.VerbSource source;
    private final String componentHandle; // null for item verbs
    @Setter private Object target; // Item or component

    public VerbEntry(
            ItemID sememeId,
            Method method,
            String doc,
            List<ParamSpec> params,
            VerbSpec.VerbSource source,
            String componentHandle,
            Object target
    ) {
        this.sememeId = sememeId;
        this.method = method;
        this.doc = doc;
        this.params = params != null ? List.copyOf(params) : List.of();
        this.source = source;
        this.componentHandle = componentHandle;
        this.target = target;
    }

    /**
     * Create a VerbEntry for an item-level verb.
     */
    public static VerbEntry itemVerb(VerbSpec spec, Item owner) {
        return new VerbEntry(
                spec.sememeId(),
                spec.method(),
                spec.doc(),
                spec.params(),
                VerbSpec.VerbSource.ITEM,
                null,
                owner
        );
    }

    /**
     * Create a VerbEntry for a component-level verb.
     */
    public static VerbEntry componentVerb(VerbSpec spec, String componentHandle, Object component) {
        return new VerbEntry(
                spec.sememeId(),
                spec.method(),
                spec.doc(),
                spec.params(),
                VerbSpec.VerbSource.COMPONENT,
                componentHandle,
                component
        );
    }

    /**
     * Get the canonical key from the sememe ID.
     */
    public String sememeKey() {
        return sememeId.encodeText();
    }

    /**
     * Get the method name for display/debugging.
     */
    public String methodName() {
        return method.getName();
    }

    @Override
    public String toString() {
        return "VerbEntry[" + sememeKey() + " -> " + methodName() + "()]";
    }
}
