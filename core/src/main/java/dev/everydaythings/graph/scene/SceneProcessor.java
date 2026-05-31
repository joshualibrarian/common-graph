package dev.everydaythings.graph.scene;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.HashID;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.ref.SchemaRef;
import dev.everydaythings.graph.ref.TypeRef;
import dev.everydaythings.graph.value.Value;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
     * bindings they translate to: one Scene-family binding per distinct
     * {@link CompoundKey} found (role HashID + qualifiers), zero or more
     * {@code Style} bindings.  Empty list when nothing is declared.
     *
     * <p>Each scene-structure root annotation may specify its attachment
     * role via one of {@code role} / {@code schemaRole} / {@code typeRole},
     * matching the trinity on {@code @Seed.Property} / {@code @Seed.Frame}:
     *
     * <ul>
     *   <li>{@code role = Scene.KEY} (or omitted entirely) — produces a
     *       concrete {@code Scene[qualifiers]} binding ({@code ItemRef}
     *       role).  The item's own scene.</li>
     *   <li>{@code schemaRole = Scene.KEY} — produces a {@code !Scene[qualifiers]}
     *       binding ({@code SchemaRef} role).  The template for INSTANCES
     *       of this item: the cascade walks instances upward and uses this
     *       to render them.  This is how ItemView attaches the chrome
     *       that frames present in.</li>
     *   <li>{@code typeRole = Scene.KEY} — produces a {@code ?Scene[qualifiers]}
     *       binding ({@code TypeRef} role).  Rare on scene-tree roots;
     *       included for symmetry.</li>
     * </ul>
     *
     * <p>Multiple roots with the SAME compound key (role + qualifiers) are
     * an error.  Multiple roots with distinct compound keys coexist as
     * separate bindings.
     */
    public static List<Binding> sceneRecordBindingsFor(Class<?> seedClass) {
        Objects.requireNonNull(seedClass, "seedClass");
        List<Binding> out = new ArrayList<>();
        Map<CompoundKey, Class<?>> roots = findSceneRoots(seedClass);
        for (Map.Entry<CompoundKey, Class<?>> entry : roots.entrySet()) {
            Body body = buildSceneBody(entry.getValue());
            out.add(new Binding(entry.getKey(), body, null));
        }
        collectStyleBindings(seedClass, out);
        return out;
    }

    /**
     * Find all scene-structure root classes on {@code seedClass}, keyed by
     * their compound key (role HashID + qualifiers).  Rules:
     *
     * <ul>
     *   <li>If the {@code @Seed.Item} class itself carries a structure
     *       annotation, it IS a root (with whatever role / qualifiers
     *       its annotation specifies; defaults to the concrete-Scene
     *       default-qualifiers compound key).</li>
     *   <li>A nested structure-annotated class with non-empty
     *       {@code qualifiers={}} OR an explicit
     *       {@code role}/{@code schemaRole}/{@code typeRole} is a separate
     *       root for its specific compound key.</li>
     *   <li>A nested structure-annotated class with no qualifiers and no
     *       explicit role is a <i>child</i> of the outer default root when
     *       one exists (handled by {@link #appendChildBindings}), OR the
     *       single default root when the outer class has no structure
     *       annotation.</li>
     * </ul>
     *
     * Two roots with the same compound key on the same archetype are an
     * error.  {@link Scene.Style} classes are NOT considered structure
     * roots — they're handled separately via {@link #collectStyleBindings}.
     */
    private static Map<CompoundKey, Class<?>> findSceneRoots(Class<?> seedClass) {
        Map<CompoundKey, Class<?>> result = new LinkedHashMap<>();

        boolean outerIsRoot = hasStructureAnnotation(seedClass);
        if (outerIsRoot) {
            putRoot(result, seedClass, compoundKeyOf(seedClass), seedClass.getName());
        }

        for (Class<?> nested : seedClass.getDeclaredClasses()) {
            if (!Modifier.isStatic(nested.getModifiers())) continue;
            if (!hasStructureAnnotation(nested)) continue;
            boolean explicitlyAddressed = !qualifiersOf(nested).isEmpty()
                    || hasExplicitRoleDeclaration(nested);
            if (explicitlyAddressed) {
                // Separate root: an explicit role or qualifier set marks
                // this nested class as not-a-child.
                putRoot(result, nested, compoundKeyOf(nested), seedClass.getName());
            } else if (!outerIsRoot) {
                // Unqualified + no explicit role + outer not a root → this
                // IS the single default root.  (If the outer were a root,
                // it'd be a child of outer, handled elsewhere.)
                putRoot(result, nested, compoundKeyOf(nested), seedClass.getName());
            }
        }

        return result;
    }

    private static void putRoot(Map<CompoundKey, Class<?>> result,
                                Class<?> root,
                                CompoundKey key,
                                String enclosing) {
        Class<?> previous = result.put(key, root);
        if (previous != null) {
            throw new IllegalStateException(
                    "Two scene-structure roots inside " + enclosing
                            + " share the same compound key " + key
                            + ": " + previous.getName() + " and " + root.getName()
                            + ".  Each (role + qualifiers) pair may have at most "
                            + "one root; differentiate via role/schemaRole/typeRole "
                            + "or qualifiers={...}.");
        }
    }

    /**
     * Resolve a structure-annotated class's compound key from the trinity
     * ({@code role} / {@code schemaRole} / {@code typeRole}) and its
     * {@code qualifiers}.  Default — when no role of any kind is declared —
     * is concrete {@link SceneVocabulary.Scene Scene}, matching historic
     * implicit own-scene behavior.
     */
    private static CompoundKey compoundKeyOf(Class<?> cls) {
        HashID role = roleHashIdOf(cls);
        List<String> qualifiers = qualifiersOf(cls);
        if (qualifiers.isEmpty()) {
            return CompoundKey.of(role);
        }
        Object[] qTokens = new Object[qualifiers.size()];
        for (int i = 0; i < qualifiers.size(); i++) {
            qTokens[i] = ItemRef.fromString(qualifiers.get(i));
        }
        return CompoundKey.of(role, qTokens);
    }

    private static HashID roleHashIdOf(Class<?> cls) {
        String role = roleOf(cls);
        String schemaRole = schemaRoleOf(cls);
        String typeRole = typeRoleOf(cls);
        int count = (role.isEmpty() ? 0 : 1)
                + (schemaRole.isEmpty() ? 0 : 1)
                + (typeRole.isEmpty() ? 0 : 1);
        if (count > 1) {
            throw new IllegalStateException(
                    "@Scene.* on " + cls.getName()
                            + " has multiple roles set; role / schemaRole / typeRole "
                            + "are mutually exclusive.");
        }
        if (!role.isEmpty()) return ItemRef.fromString(role);
        if (!schemaRole.isEmpty()) return SchemaRef.fromString(schemaRole);
        if (!typeRole.isEmpty()) return TypeRef.fromString(typeRole);
        // Implicit default — preserves historic behavior of declarations
        // that name no role explicitly: they attach as concrete Scene.
        return ItemRef.iid(SceneVocabulary.Scene.KEY);
    }

    private static boolean hasExplicitRoleDeclaration(Class<?> cls) {
        return !roleOf(cls).isEmpty()
                || !schemaRoleOf(cls).isEmpty()
                || !typeRoleOf(cls).isEmpty();
    }

    private static String roleOf(Class<?> cls) {
        Scene.Container c = cls.getAnnotation(Scene.Container.class);
        if (c != null) return c.role();
        Scene.Text t = cls.getAnnotation(Scene.Text.class);
        if (t != null) return t.role();
        Scene.Body b = cls.getAnnotation(Scene.Body.class);
        if (b != null) return b.role();
        return "";
    }

    private static String schemaRoleOf(Class<?> cls) {
        Scene.Container c = cls.getAnnotation(Scene.Container.class);
        if (c != null) return c.schemaRole();
        Scene.Text t = cls.getAnnotation(Scene.Text.class);
        if (t != null) return t.schemaRole();
        Scene.Body b = cls.getAnnotation(Scene.Body.class);
        if (b != null) return b.schemaRole();
        return "";
    }

    private static String typeRoleOf(Class<?> cls) {
        Scene.Container c = cls.getAnnotation(Scene.Container.class);
        if (c != null) return c.typeRole();
        Scene.Text t = cls.getAnnotation(Scene.Text.class);
        if (t != null) return t.typeRole();
        Scene.Body b = cls.getAnnotation(Scene.Body.class);
        if (b != null) return b.typeRole();
        return "";
    }

    /**
     * Read the structure annotation's {@code qualifiers()} parameter.
     * Returns an immutable list.  Defaults to empty when no qualifier is
     * declared.
     */
    private static List<String> qualifiersOf(Class<?> cls) {
        Scene.Container container = cls.getAnnotation(Scene.Container.class);
        if (container != null) return List.of(container.qualifiers());
        Scene.Text text = cls.getAnnotation(Scene.Text.class);
        if (text != null) return List.of(text.qualifiers());
        Scene.Body body = cls.getAnnotation(Scene.Body.class);
        if (body != null) return List.of(body.qualifiers());
        return List.of();
    }

    /** Turn string qualifier keys into CompoundKey qualifier tokens. */
    private static List<CompoundKey.Qualifier> qualifierTokens(List<String> keys) {
        if (keys.isEmpty()) return List.of();
        List<CompoundKey.Qualifier> out = new ArrayList<>(keys.size());
        for (String key : keys) {
            out.add(new CompoundKey.Sememe(ItemRef.fromString(key)));
        }
        return out;
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
            List<CompoundKey.Qualifier> qualifiers = qualifierTokens(List.of(ann.qualifiers()));
            out.add(new Binding(CompoundKey.of(STYLE_ROLE, qualifiers.toArray()), styleBody, index));
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

    private static final ItemRef TRANSFORM_KEY = ItemRef.fromString("cg.predicate:transform");
    private static final ItemRef THEME_ROLE = ItemRef.iid(
            dev.everydaythings.graph.ThematicRole.Theme.KEY);
    private static final ItemRef INSTRUMENT_ROLE = ItemRef.iid(
            dev.everydaythings.graph.ThematicRole.Instrument.KEY);

    private static void appendChildBindings(Class<?> cls, List<Binding> bindings) {
        Class<?>[] declared = cls.getDeclaredClasses();
        long autoIndex = 0;
        for (Class<?> child : declared) {
            if (!Modifier.isStatic(child.getModifiers())) continue;
            if (!hasStructureAnnotation(child)) continue;
            // Qualified children are alternate scene roots, not children of
            // this parent — they get their own Scene[qualifier] binding via
            // findSceneRoots, not a Children binding here.
            if (!qualifiersOf(child).isEmpty()) continue;

            Body childBody = buildSceneBody(child);
            // If the child is wrapped in @Scene.Repeat, replace its body with a
            // Transform-headed operator frame: THEME=?source, INSTRUMENT=the
            // child's body.  At resolve time, Transform expands the template
            // per source item; the resolver's collection-splat then produces
            // N sibling Children bindings here in place of this one.
            Scene.Repeat repeat = child.getAnnotation(Scene.Repeat.class);
            if (repeat != null) {
                childBody = wrapInTransform(childBody, repeat.source());
            }
            long index = explicitOrder(child).orElse(autoIndex);
            bindings.add(new Binding(
                    CompoundKey.of(CHILDREN_ROLE), childBody, index));
            autoIndex++;
        }
    }

    /**
     * Wrap a template body in a Transform-headed operator frame.  The Transform's
     * THEME is a {@code ?}-ref to the source key (resolved per render against
     * the context chain); its INSTRUMENT is the template body itself.
     */
    private static Body wrapInTransform(Body templateBody, String sourceKey) {
        return Body.of(
                TRANSFORM_KEY,
                List.of(
                        Binding.ref(THEME_ROLE, TypeRef.iid(sourceKey)),
                        Binding.qualified(INSTRUMENT_ROLE, List.of(), templateBody)));
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
