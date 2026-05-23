package dev.everydaythings.graph.scene;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.ref.TypeRef;
import dev.everydaythings.graph.value.Value;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Builds scene-tree {@link Body} datums and style-frame Bodies from
 * {@link Scene}-annotated static class hierarchies on {@code @Seed.Item}
 * archetypes.  Returns them as record bindings the caller (typically
 * {@code SeedProcessor}) attaches to the archetype's signing record.
 *
 * <p>Two kinds of bindings come out:
 * <ul>
 *   <li><b>{@link SceneVocabulary.Scene Scene}</b> → scene-structure body —
 *       at most one per archetype.  Source: a {@code @Scene.Container} /
 *       {@code @Scene.Text} / {@code @Scene.Body} class (either on the
 *       {@code @Seed.Item} class itself or as a top-level static nested
 *       class).</li>
 *   <li><b>{@link SceneVocabulary.Style Style}</b> → style-frame body — zero
 *       or more per archetype, one per {@code @Scene.Style}-annotated
 *       top-level static nested class.  Indexed by declaration order (or
 *       {@code order=} override).</li>
 * </ul>
 *
 * <p>Both go directly on the record as their own top-level binding heads
 * ({@code Scene}, {@code Style}) — no {@code CONFIG[…]} wrapper.  The
 * cascade and resolver read them as plain role bindings.
 */
public final class SceneProcessor {

    private SceneProcessor() {}

    private static final ItemRef SCENE_ROLE = ItemRef.iid(SceneVocabulary.Scene.KEY);
    private static final ItemRef STYLE_ROLE = ItemRef.iid(SceneVocabulary.Style.KEY);
    private static final ItemRef PATTERN_ROLE = ItemRef.iid(SceneVocabulary.Pattern.KEY);
    private static final ItemRef CLASSES_ROLE = ItemRef.iid(SceneVocabulary.Classes.KEY);
    private static final ItemRef ID_ROLE = ItemRef.iid(SceneVocabulary.Id.KEY);
    private static final ItemRef CHILDREN_ROLE = ItemRef.iid(SceneVocabulary.Children.KEY);
    private static final ItemRef SCENE_NODE_ARCHETYPE = ItemRef.iid(SceneNode.KEY);

    /**
     * Walk {@code seedClass}'s scene declarations and produce the record
     * bindings they translate to: at most one {@code Scene} binding, zero or
     * more {@code Style} bindings.  Empty list when nothing is declared.
     */
    public static List<Binding> sceneRecordBindingsFor(Class<?> seedClass) {
        Objects.requireNonNull(seedClass, "seedClass");
        List<Binding> out = new ArrayList<>();
        findSceneRoot(seedClass).ifPresent(root ->
                out.add(Binding.qualified(SCENE_ROLE, List.of(), buildSceneBody(root))));
        collectStyleBindings(seedClass, out);
        return out;
    }

    /**
     * Find the class that should serve as the scene-structure root.  The
     * {@code @Seed.Item} class itself takes precedence; otherwise a single
     * top-level static nested class with a structure-type annotation
     * ({@link Scene.Container}, {@link Scene.Text}, {@link Scene.Body}) is
     * used.  Multiple top-level structure classes are an error (multi-scene
     * disambiguation is a follow-up concern).  {@link Scene.Style} classes
     * are NOT considered structure roots — they're handled separately.
     */
    private static Optional<Class<?>> findSceneRoot(Class<?> seedClass) {
        if (hasStructureAnnotation(seedClass)) {
            return Optional.of(seedClass);
        }
        List<Class<?>> nestedStructure = new ArrayList<>();
        for (Class<?> nested : seedClass.getDeclaredClasses()) {
            if (!Modifier.isStatic(nested.getModifiers())) continue;
            if (hasStructureAnnotation(nested)) {
                nestedStructure.add(nested);
            }
        }
        if (nestedStructure.isEmpty()) return Optional.empty();
        if (nestedStructure.size() > 1) {
            throw new IllegalStateException(
                    "Multiple top-level structure-typed nested classes inside "
                            + seedClass.getName()
                            + " — disambiguation is not yet supported. "
                            + "Found: " + nestedStructure);
        }
        return Optional.of(nestedStructure.get(0));
    }

    /**
     * Walk {@code seedClass}'s top-level static nested classes for
     * {@link Scene.Style} annotations; emit one {@code Style} record
     * binding per match.  Indices come from the annotation's {@code order=}
     * when set, or declaration order otherwise.
     */
    private static void collectStyleBindings(Class<?> seedClass, List<Binding> out) {
        long autoIndex = 0;
        for (Class<?> nested : seedClass.getDeclaredClasses()) {
            if (!Modifier.isStatic(nested.getModifiers())) continue;
            Scene.Style ann = nested.getAnnotation(Scene.Style.class);
            if (ann == null) continue;
            Body styleBody = buildStyleBody(nested, ann);
            long index = ann.order() == Long.MIN_VALUE ? autoIndex : ann.order();
            out.add(new Binding(CompoundKey.of(STYLE_ROLE), styleBody, index));
            autoIndex++;
        }
    }

    /** Build a {@link SceneStyle}-headed body for a {@code @Scene.Style} class. */
    private static Body buildStyleBody(Class<?> styleClass, Scene.Style ann) {
        List<Binding> bindings = new ArrayList<>();
        bindings.add(Binding.qualified(PATTERN_ROLE, List.of(),
                buildQueryPattern(ann, styleClass.getName())));
        appendPropertyBindings(styleClass, bindings);
        return Body.of(ItemRef.iid(SceneVocabulary.SceneStyle.KEY), bindings);
    }

    /**
     * Build the query-body referenced by a {@code @Scene.Style} annotation.
     * Head is a {@link TypeRef} (query mode); match-pattern bindings come from
     * the {@code matchClass} / {@code matchId} / {@code matchType} parameters.
     * Exactly one of those parameters must be set in this first slice; richer
     * queries land via the {@code SceneSelectorNotation} text form later.
     */
    private static Body buildQueryPattern(Scene.Style ann, String context) {
        boolean hasClass = !ann.matchClass().isEmpty();
        boolean hasId    = !ann.matchId().isEmpty();
        boolean hasType  = !ann.matchType().isEmpty();
        int set = (hasClass ? 1 : 0) + (hasId ? 1 : 0) + (hasType ? 1 : 0);
        if (set != 1) {
            throw new IllegalStateException(
                    "@Scene.Style on " + context + " must set exactly one of "
                            + "matchClass / matchId / matchType (got " + set + ").");
        }

        // Query head is the archetype the resolver returns: SceneNode (the
        // most-general scene node) unless matchType pins it more specifically.
        ItemRef matchedArchetype = hasType
                ? ItemRef.fromString(ann.matchType())
                : SCENE_NODE_ARCHETYPE;
        TypeRef queryHead = TypeRef.of(matchedArchetype);

        List<Binding> patternBindings = new ArrayList<>();
        if (hasClass) {
            patternBindings.add(Binding.literal(CLASSES_ROLE, ann.matchClass()));
        } else if (hasId) {
            patternBindings.add(Binding.literal(ID_ROLE, ann.matchId()));
        }
        // hasType only — no extra binding; the head IS the match.

        return Body.of(queryHead, patternBindings);
    }

    /**
     * Recursively build the scene-structure {@link Body} for a structure
     * class ({@code @Scene.Container} / {@code @Scene.Text} / {@code @Scene.Body}).
     */
    private static Body buildSceneBody(Class<?> nodeClass) {
        ItemRef head = sceneNodeHead(nodeClass);
        List<Binding> bindings = new ArrayList<>();
        appendPropertyBindings(nodeClass, bindings);
        appendChildBindings(nodeClass, bindings);
        return Body.of(head, bindings);
    }

    private static boolean hasStructureAnnotation(Class<?> cls) {
        return cls.isAnnotationPresent(Scene.Container.class)
                || cls.isAnnotationPresent(Scene.Text.class)
                || cls.isAnnotationPresent(Scene.Body.class);
    }

    private static ItemRef sceneNodeHead(Class<?> cls) {
        if (cls.isAnnotationPresent(Scene.Container.class)) return ItemRef.iid(SceneContainer.KEY);
        if (cls.isAnnotationPresent(Scene.Text.class))      return ItemRef.iid(SceneText.KEY);
        if (cls.isAnnotationPresent(Scene.Body.class))      return ItemRef.iid(SceneBody.KEY);
        throw new IllegalStateException(
                "No structure-type annotation on " + cls.getName());
    }

    private static void appendPropertyBindings(Class<?> cls, List<Binding> bindings) {
        for (Field field : cls.getDeclaredFields()) {
            Scene.Property prop = field.getAnnotation(Scene.Property.class);
            if (prop == null) continue;
            if (!Modifier.isStatic(field.getModifiers())) {
                throw new IllegalStateException(
                        "@Scene.Property requires a static field: "
                                + cls.getName() + "." + field.getName());
            }
            field.setAccessible(true);
            Object value;
            try {
                value = field.get(null);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(
                        "Cannot read @Scene.Property field "
                                + cls.getName() + "." + field.getName(), e);
            }
            if (value == null) {
                throw new IllegalStateException(
                        "@Scene.Property field " + cls.getName() + "." + field.getName()
                                + " is null — scene-property targets must be non-null at seed time");
            }
            bindings.add(buildPropertyBinding(prop, value,
                    cls.getName() + "." + field.getName()));
        }
    }

    private static Binding buildPropertyBinding(Scene.Property prop, Object fieldValue, String context) {
        ItemRef role = ItemRef.fromString(prop.role());
        List<CompoundKey.Qualifier> qualifiers = new ArrayList<>();
        for (String qKey : prop.qualifiers()) {
            qualifiers.add(new CompoundKey.Sememe(ItemRef.fromString(qKey)));
        }
        if (fieldValue instanceof ItemRef ref) {
            return qualifiers.isEmpty()
                    ? Binding.ref(role, ref)
                    : Binding.qualified(role, qualifiers, ref);
        }
        if (fieldValue instanceof TypeRef tref) {
            return qualifiers.isEmpty()
                    ? Binding.ref(role, tref)
                    : Binding.qualified(role, qualifiers, tref);
        }
        if (fieldValue instanceof Value v) {
            return Binding.qualified(role, qualifiers, v);
        }
        if (fieldValue instanceof String s) {
            return Binding.literal(role, s);
        }
        if (fieldValue instanceof Long || fieldValue instanceof Integer
                || fieldValue instanceof Boolean || fieldValue instanceof byte[]) {
            return Binding.literal(role, fieldValue);
        }
        throw new IllegalStateException(
                "Unsupported @Scene.Property field type at " + context
                        + ": " + fieldValue.getClass().getName());
    }

    private static void appendChildBindings(Class<?> cls, List<Binding> bindings) {
        Class<?>[] declared = cls.getDeclaredClasses();
        long autoIndex = 0;
        for (Class<?> child : declared) {
            if (!Modifier.isStatic(child.getModifiers())) continue;
            if (!hasStructureAnnotation(child)) continue;

            Body childBody = buildSceneBody(child);
            long index = explicitOrder(child).orElse(autoIndex);
            bindings.add(new Binding(
                    CompoundKey.of(CHILDREN_ROLE), childBody, index));
            autoIndex++;
        }
    }

    private static Optional<Long> explicitOrder(Class<?> child) {
        long o = orderFromAnnotation(child);
        return o == Long.MIN_VALUE ? Optional.empty() : Optional.of(o);
    }

    private static long orderFromAnnotation(Class<?> cls) {
        Scene.Container c = cls.getAnnotation(Scene.Container.class);
        if (c != null) return c.order();
        Scene.Text t = cls.getAnnotation(Scene.Text.class);
        if (t != null) return t.order();
        Scene.Body b = cls.getAnnotation(Scene.Body.class);
        if (b != null) return b.order();
        return Long.MIN_VALUE;
    }
}
