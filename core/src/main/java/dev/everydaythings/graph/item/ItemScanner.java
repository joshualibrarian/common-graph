package dev.everydaythings.graph.item;

import dev.everydaythings.graph.item.action.ActionContext;
import dev.everydaythings.graph.item.action.ParamSpec;
import dev.everydaythings.graph.item.component.Components;
import dev.everydaythings.graph.item.component.Param;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.component.Verb;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.id.HandleID;
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

        Set<String> handles = new HashSet<>(); // Track for uniqueness
        Set<String> paths = new HashSet<>();   // Track for uniqueness
        Set<ItemID> seenVerbSememes = new HashSet<>(); // Track verb sememe IDs for child-wins precedence

        // Walk class hierarchy (child first → parent)
        for (Class<?> c = itemClass; c != null && c != Object.class; c = c.getSuperclass()) {
            // Scan fields for @Item.Frame
            for (Field field : c.getDeclaredFields()) {
                Item.Frame frame = field.getAnnotation(Item.Frame.class);
                if (frame != null) {
                    FrameFieldSpec frameSpec = extractFrameField(field, frame);

                    if (frameSpec.endorsed()) {
                        validateFrameField(frameSpec, handles, paths);

                        // Scan component class for @Verb methods
                        if (field.getType().isAnnotationPresent(Type.class)) {
                            List<VerbSpec> verbs = scanComponentVerbs(field.getType(), frameSpec.handleKey());
                            if (!verbs.isEmpty()) {
                                componentVerbs.put(frameSpec.handleKey(), verbs);
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
                    if (seenVerbSememes.add(sememeId)) {
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

    // ==================================================================================
    // Field Extraction
    // ==================================================================================

    /**
     * Extract a FrameFieldSpec from a field and @Frame annotation.
     */
    private static FrameFieldSpec extractFrameField(Field field, Item.Frame ann) {
        // Determine FrameKey and handleKey
        FrameKey frameKey;
        String handleKey;

        if (ann.key().length > 0) {
            // Semantic key from annotation
            ItemID[] tokens = new ItemID[ann.key().length];
            for (int i = 0; i < ann.key().length; i++) {
                tokens[i] = ItemID.fromString(ann.key()[i]);
            }
            frameKey = FrameKey.of(tokens);
            // HandleKey derived from first token for backward compat
            handleKey = ann.handle().isEmpty() ? ann.key()[0] : ann.handle();
        } else if (!ann.handle().isEmpty()) {
            // Literal key
            frameKey = FrameKey.literal(ann.handle());
            handleKey = ann.handle();
        } else {
            // Default to field name
            String name = field.getName();
            frameKey = FrameKey.literal(name);
            handleKey = name;
        }

        HandleID handle = HandleID.of(handleKey);

        // Determine type
        Class<?> fieldType = field.getType();
        ItemID type;
        if (fieldType.isAnnotationPresent(Type.class)) {
            type = Components.typeId(fieldType);
        } else {
            type = ItemID.fromString("cg:type/" + fieldType.getSimpleName().toLowerCase());
        }

        boolean localOnly = ann.localOnly();
        boolean stream = ann.stream();
        boolean snapshot = ann.snapshot() && !localOnly;
        boolean identity = ann.identity() && !localOnly;

        field.setAccessible(true);

        return new FrameFieldSpec(
                field, frameKey, handle, handleKey, type,
                ann.path(), snapshot, stream, localOnly, identity, ann.endorsed());
    }


    // ==================================================================================
    // Verb Extraction
    // ==================================================================================

    /**
     * Extract a VerbSpec from a method with @Verb.
     */
    private static VerbSpec extractMethodVerb(Method method, Verb ann) {
        method.setAccessible(true);

        ItemID sememeId = ItemID.fromString(ann.value());
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
                    ItemID sememeId = ItemID.fromString(ann.value());
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

    // ==================================================================================
    // Validation
    // ==================================================================================

    /**
     * Validate an endorsed frame field spec for uniqueness.
     */
    private static void validateFrameField(FrameFieldSpec spec, Set<String> handles, Set<String> paths) {
        String handleStr = spec.handle().toString();
        if (handles.contains(handleStr)) {
            throw new IllegalStateException(
                    "Duplicate frame handle '" + handleStr + "' on field " + spec.field().getName());
        }
        handles.add(handleStr);

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
