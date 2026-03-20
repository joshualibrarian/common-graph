package dev.everydaythings.graph.item;

import dev.everydaythings.graph.frame.ItemFrame;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.dispatch.ActionContext;
import dev.everydaythings.graph.dispatch.ParamSpec;
import dev.everydaythings.graph.dispatch.VerbSpec;
import dev.everydaythings.graph.item.Param;
import dev.everydaythings.graph.item.Verb;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.id.ItemID;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified scanner for Item class metadata.
 *
 * <p>ItemScanner performs a single walk through an Item class hierarchy,
 * extracting all annotation-based metadata:
 * <ul>
 *   <li>@Item.Frame → FrameFieldSpec (endorsed + unendorsed)</li>
 *   <li>@Verb → VerbSpec (on methods and component classes)</li>
 * </ul>
 *
 * <p><b>Caching:</b> Scan results are cached per class. Call {@link #clearCache()}
 * only for testing or hot-reload scenarios.
 */
public final class ItemScanner {

    private ItemScanner() {} // Utility class

    // ==================================================================================
    // Cache
    // ==================================================================================

    /** Per-class schema cache. Thread-safe via ConcurrentHashMap. */
    private static final Map<Class<?>, ItemSchema> cache = new ConcurrentHashMap<>();

    /**
     * Get or compute the schema for an Item class.
     *
     * <p>Returns a cached schema if available, otherwise scans the class
     * and caches the result.
     *
     * @param itemClass The Item class to get schema for
     * @return The cached or computed schema
     */
    public static ItemSchema schemaFor(Class<? extends Item> itemClass) {
        Objects.requireNonNull(itemClass, "itemClass");
        return cache.computeIfAbsent(itemClass, ItemScanner::scan);
    }

    /**
     * Clear the schema cache.
     *
     * <p>Use only for testing or when classes are reloaded.
     */
    public static void clearCache() {
        cache.clear();
    }

    /**
     * Check if a schema is cached for the given class.
     */
    public static boolean isCached(Class<? extends Item> itemClass) {
        return cache.containsKey(itemClass);
    }

    /**
     * Get the number of cached schemas.
     */
    public static int cacheSize() {
        return cache.size();
    }

    // ==================================================================================
    // Scanning
    // ==================================================================================

    /**
     * Scan an Item class and build its schema.
     *
     * <p>This is the core scanning method. It walks the class hierarchy
     * once, collecting all annotation-based metadata.
     */
    @SuppressWarnings("unchecked")
    private static ItemSchema scan(Class<?> itemClass) {
        List<FrameFieldSpec> frameFields = new ArrayList<>();
        List<VerbSpec> verbSpecs = new ArrayList<>();
        Map<String, List<VerbSpec>> componentVerbs = new HashMap<>();

        Set<FrameKey> frameKeys = new HashSet<>(); // Track for uniqueness
        Set<String> paths = new HashSet<>();   // Track for uniqueness
        Set<ItemID> seenVerbIds = new HashSet<>(); // Track verb sememe IDs for child-wins precedence

        // Walk class hierarchy (child first → parent)
        for (Class<?> c = itemClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                // Skip static fields — type-level frames are handled by SeedVocabulary.
                // TODO: Unify type-level and instance-level frame scanning (see design-seed-pipeline-simplification.md)
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;

                ItemFrame itemFrame = field.getAnnotation(ItemFrame.class);
                if (itemFrame != null) {
                    FrameFieldSpec frameSpec = extractFrameField(field, itemFrame);

                    if (frameSpec.endorsed()) {
                        validateFrameField(frameSpec, frameKeys, paths);

                        // Scan component class for @Verb methods
                        if (field.getType().isAnnotationPresent(Implements.class)) {
                            String keyString = frameSpec.canonicalKeyString();
                            List<VerbSpec> verbs = scanComponentVerbs(field.getType(), keyString);
                            if (!verbs.isEmpty()) {
                                componentVerbs.put(keyString, verbs);
                            }
                        }
                    }

                    frameFields.add(frameSpec);
                }
            }

            // Scan methods for @Verb
            // Child methods are scanned first — skip parent verbs with same sememe ID
            for (Method method : c.getDeclaredMethods()) {
                Verb va = method.getAnnotation(Verb.class);
                if (va != null) {
                    ItemID sememeId = ItemID.fromString(va.value());
                    if (seenVerbIds.add(sememeId)) {
                        verbSpecs.add(extractMethodVerb(method, va));
                    }
                }
            }
        }

        return new ItemSchema(
                (Class<? extends Item>) itemClass,
                frameFields,
                verbSpecs,
                componentVerbs
        );
    }

    /**
     * Synthesize a FrameFieldSpec for IMPLEMENTED_BY from an @Implements annotation.
     *
     * <p>This is shorthand: @Implements("cg.op:add") on a class is equivalent to
     * having a static @ItemFrame field with key={ImplementedBy, JavaRuntime} and
     * value=the class name. The scanner creates the spec as if the field existed.
     */
    private static FrameFieldSpec synthesizeImplementedBy(Class<?> clazz, Implements ann) {
        String key = ann.value();
        if (key == null || key.isBlank()) return null;

        ItemID implPredicate = ItemID.fromString(CoreVocabulary.ImplementedBy.KEY);
        FrameKey frameKey = FrameKey.of(implPredicate);

        // The "type" of this frame is the ImplementedBy predicate itself
        ItemID type = implPredicate;

        // Self role = THEME (this item), value role = GOAL (the Java class name)
        ItemID selfRole = ThematicRole.Theme.IID;
        ItemID valueRole = ThematicRole.Goal.IID;

        // Create a synthetic field-less spec
        // We pass null for the field — the value is the class name, derived at commit time
        return new FrameFieldSpec(
                null, // no field — value is synthesized
                frameKey, type, selfRole, valueRole,
                "", true, false, false, true, true);
    }

    // ==================================================================================
    // Field Extraction
    // ==================================================================================

    /**
     * Extract a FrameFieldSpec from a field and @ItemFrame annotation.
     */
    private static FrameFieldSpec extractFrameField(Field field, ItemFrame ann) {
        if (ann.predicate().isEmpty()) {
            throw new IllegalStateException(
                    "@ItemFrame on " + field.getDeclaringClass().getSimpleName() + "." + field.getName()
                    + " must have a predicate");
        }

        ItemID head = ItemID.fromString(ann.predicate());
        ItemFrame.Bind fieldBind = ann.fieldAs();
        Object[] qualifiers = new Object[fieldBind.qualifiers().length];
        for (int i = 0; i < fieldBind.qualifiers().length; i++) {
            qualifiers[i] = ItemID.fromString(fieldBind.qualifiers()[i]);
        }
        FrameKey frameKey = FrameKey.of(head, qualifiers);

        Class<?> fieldType = field.getType();
        ItemID type;
        if (fieldType.isAnnotationPresent(Implements.class)) {
            type = Item.idOf(fieldType);
        } else {
            type = ItemID.fromString("cg.sememe:" + fieldType.getSimpleName().toLowerCase());
        }

        ItemID selfRole = ItemID.fromString(ann.classAs().role());
        ItemID valueRole = ItemID.fromString(ann.fieldAs().role());

        boolean localOnly = ann.localOnly();
        boolean stream = ann.stream();
        boolean snapshot = ann.snapshot() && !localOnly;
        boolean identity = ann.identity() && !localOnly;

        String mountPath = ann.endorsement().mounts().length > 0 ? ann.endorsement().mounts()[0] : "";
        boolean endorsed = ann.endorsement().value();

        field.setAccessible(true);

        return new FrameFieldSpec(
                field, frameKey, type, selfRole, valueRole,
                mountPath, snapshot, stream, localOnly, identity, endorsed);
    }

    // ==================================================================================
    // Verb Extraction
    // ==================================================================================

    /**
     * Extract a VerbSpec from a method with @Verb.
     */
    private static VerbSpec extractMethodVerb(Method method, Verb ann) {
        method.setAccessible(true);

        ItemID sememeId = ItemID.fromString(verbPredicate(ann));
        String doc = ann.doc();
        List<ParamSpec> params = extractParameters(method);

        return VerbSpec.itemVerb(sememeId, method, doc, params);
    }

    /**
     * Scan a component class for @Verb methods.
     *
     * <p>Verbs reference Sememes for language-agnostic dispatch.
     * Any class with {@code @Type} can declare verbs.
     *
     * @param componentClass The component class to scan
     * @param componentHandle The handle of the component instance
     * @return List of VerbSpec for discovered verbs
     */
    public static List<VerbSpec> scanComponentVerbs(
            Class<?> componentClass,
            String componentHandle) {

        List<VerbSpec> results = new ArrayList<>();

        for (Class<?> cls = componentClass;
             cls != null && cls != Object.class;
             cls = cls.getSuperclass()) {

            for (Method method : cls.getDeclaredMethods()) {
                Verb ann = method.getAnnotation(Verb.class);
                if (ann != null) {
                    method.setAccessible(true);
                    ItemID sememeId = ItemID.fromString(verbPredicate(ann));
                    String doc = ann.doc();
                    List<ParamSpec> params = extractParameters(method);
                    results.add(VerbSpec.componentVerb(sememeId, method, doc, params));
                }
            }
        }

        return results;
    }

    // ==================================================================================
    // Parameter Extraction
    // ==================================================================================

    /**
     * Extract parameter specifications from a method.
     */
    static List<ParamSpec> extractParameters(Method method) {
        List<ParamSpec> params = new ArrayList<>();

        Parameter[] methodParams = method.getParameters();
        int startIndex = 0;

        // Skip ActionContext if it's the first parameter
        if (methodParams.length > 0 && ActionContext.class.isAssignableFrom(methodParams[0].getType())) {
            startIndex = 1;
        }

        for (int i = startIndex; i < methodParams.length; i++) {
            Parameter param = methodParams[i];
            params.add(extractParamSpec(param));
        }

        return params;
    }

    /**
     * Extract a ParamSpec from a method parameter.
     */
    private static ParamSpec extractParamSpec(Parameter param) {
        Param ann = param.getAnnotation(Param.class);

        String name;
        String doc = "";
        boolean required = true;
        String defaultValue = null;
        String role = null;

        if (ann != null) {
            name = ann.value().isEmpty() ? param.getName() : ann.value();
            doc = ann.doc();
            required = ann.required();
            defaultValue = ann.defaultValue().isEmpty() ? null : ann.defaultValue();
            role = ann.role();
        } else {
            name = param.getName();
        }

        return new ParamSpec(name, param.getType(), doc, required, defaultValue, role);
    }

    /** Resolve verb predicate — new style (predicate) or legacy (value). */
    private static String verbPredicate(Verb ann) {
        return !ann.predicate().isEmpty() ? ann.predicate() : ann.value();
    }

    // ==================================================================================
    // Validation
    // ==================================================================================

    /**
     * Validate an endorsed frame field spec for uniqueness.
     */
    private static void validateFrameField(FrameFieldSpec spec, Set<FrameKey> frameKeys, Set<String> paths) {
        FrameKey key = spec.frameKey();
        if (frameKeys.contains(key)) {
            throw new IllegalStateException(
                    "Duplicate frame key " + key + " on field " + spec.field().getName());
        }
        frameKeys.add(key);

        if (spec.hasMountPath()) {
            if (paths.contains(spec.path())) {
                throw new IllegalStateException(
                        "Duplicate mount path '" + spec.path() + "' on field " + spec.field().getName());
            }
            paths.add(spec.path());
        }

        // localOnly requires path
        if (spec.localOnly() && !spec.hasMountPath()) {
            throw new IllegalStateException(
                    "@Frame on local-only field must specify path, " +
                    "but field '" + spec.field().getName() + "' has no path");
        }
    }
}
