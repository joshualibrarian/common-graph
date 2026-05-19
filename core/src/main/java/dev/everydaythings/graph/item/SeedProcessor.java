package dev.everydaythings.graph.item;


import dev.everydaythings.graph.*;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.id.CompoundKey;
import dev.everydaythings.graph.id.DatumRef;
import dev.everydaythings.graph.id.HashID;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.id.SchemaRef;
import dev.everydaythings.graph.id.TypeRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.SchemaVocabulary;

import dev.everydaythings.graph.runtime.RuntimeVocabulary;
import dev.everydaythings.graph.language.CiliId;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.CoreVocabulary;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Processes {@code @Seed} and {@code @Embodies} annotations at bootstrap, persisting
 * the resulting manifest bodies and IMPLEMENTS frames as unsigned data.
 *
 * <p>The processor is invoked by {@link Librarian#bootstrap()}. It scans the
 * classpath via ClassGraph, discovers every {@code @Seed} and {@code @Embodies}
 * class, and writes the corresponding bodies to local storage.
 *
 * <p>Persistence is unsigned — these are bootstrap-time records of identity and
 * implementation declarations, not signed assertions. When the seed vocabulary is
 * eventually published to the broader graph (with language data, glosses, etc.),
 * those publications carry signatures from the publishers.
 *
 * <p>Idempotency comes from content-addressing: re-running bootstrap on the same
 * classpath produces the same body bytes → same CIDs → no-op writes to OBJECTS.
 */
public final class SeedProcessor {

    private SeedProcessor() {}

    /**
     * Scan the classpath, process every {@code @Seed}, {@code @Embodies}, and
     * {@code @Mints} class, persist the resulting bodies (unsigned).
     */
    public static void bootstrap(Librarian librarian) {
        try (ScanResult result = new ClassGraph()
                .enableClassInfo()
                .enableAnnotationInfo()
                .scan()) {

            // Pass 1: @Seed.Item classes — produces each archetype's manifest body.
            // For single-level self-@Embodies (same key), the class literal lands
            // on the archetype manifest here. Two-level @Embodies is NOT consulted
            // here — its work is independent (pass 3).
            ClassInfoList seedClasses = result.getClassesWithAnnotation(Seed.Item.class.getName());
            for (ClassInfo classInfo : seedClasses) {
                Class<?> cls = classInfo.loadClass();
                processSeed(librarian, cls);
            }

            // Pass 2: @Seed.Mints classes — publishes IMPLEMENTS frames for
            // instance-class declarations (the data signal that drives CREATE/MINT).
            // Cross-validate that the target concept has EXPECTS endorsements —
            // declaring an instance class for a non-instantiable concept is a bug.
            ClassInfoList mintsClasses = result.getClassesWithAnnotation(Seed.Mints.class.getName());
            for (ClassInfo classInfo : mintsClasses) {
                Class<?> cls = classInfo.loadClass();
                Seed.Mints mints = cls.getAnnotation(Seed.Mints.class);
                ItemRef conceptIid = ItemRef.fromString(mints.key());
                requireExpects(librarian, conceptIid, cls.getName());
                processMints(librarian, cls);
            }

            // Pass 3: two-level @Seed.Embodies classes — mints each CodeItem manifest
            // plus the IMPLEMENTS frame linking it to the archetype it implements.
            // Independent of pass 1: the archetype's manifest is already written and
            // is NOT modified here. The connection is via the IMPLEMENTS frame body,
            // discoverable by index ("who implements archetype A?").
            ClassInfoList embodiesClasses = result.getClassesWithAnnotation(Seed.Embodies.class.getName());
            for (ClassInfo classInfo : embodiesClasses) {
                Class<?> cls = classInfo.loadClass();
                processEmbodies(librarian, cls);
            }

            // Validate: every @Seed.Embodies declaration must be consistent.
            //   Single-level (no archetype()): requires @Seed.Item(K) on the same class.
            //   Two-level (archetype=AK): the archetype K must exist as a real seed
            //     item somewhere in the classpath — but it does NOT have to be the
            //     same class. The annotations are orthogonal.
            for (ClassInfo classInfo : embodiesClasses) {
                Class<?> cls = classInfo.loadClass();
                Seed.Embodies embodies = cls.getAnnotation(Seed.Embodies.class);
                if (embodies.archetype().isEmpty()) {
                    Seed.Item seedItem = cls.getAnnotation(Seed.Item.class);
                    if (seedItem == null || !seedItem.key().equals(embodies.key())) {
                        throw new IllegalStateException(
                                "@Seed.Embodies(key=" + embodies.key()
                                        + ") on " + cls.getName()
                                        + " (single-level) requires @Seed.Item(\""
                                        + embodies.key() + "\") on the same class");
                    }
                } else if (librarian.library()
                        .manifestCidsForItem(ItemRef.fromString(embodies.archetype()))
                        .isEmpty()) {
                    throw new IllegalStateException(
                            "@Seed.Embodies(key=" + embodies.key()
                                    + ", archetype=" + embodies.archetype()
                                    + ") on " + cls.getName()
                                    + " — archetype \"" + embodies.archetype()
                                    + "\" has no @Seed.Item declaration on the classpath");
                }
            }
        }
    }

    // ==================================================================================
    // @Seed processing
    // ==================================================================================

    /**
     * Process one {@code @Seed.Item} class into its declarative manifest.
     *
     * <p>The manifest body IS the item — persisting the body is the act of minting
     * the item. {@code @Seed.Item} is solely responsible for the archetype manifest
     * (head + ITEM_ID + endorsements + explicit extras). It knows nothing of
     * implementations.
     *
     * <p>{@code @Seed.Embodies} on the same class is handled separately, in its own
     * pass, and produces its own items (a CodeItem manifest + a standalone IMPLEMENTS
     * frame linking the CodeItem to the archetype). The two annotations are
     * orthogonal — neither modifies the other's manifest.
     */
    private static void processSeed(Librarian librarian, Class<?> cls) {
        Seed.Item seedItem = cls.getAnnotation(Seed.Item.class);
        if (seedItem == null) return;

        List<Binding> bindings = new ArrayList<>();
        bindings.add(Binding.ref(Manifest.ITEM_ID, ItemRef.fromString(seedItem.key())));

        // Single-level @Seed.Embodies (same key as @Seed.Item) is a self-embodiment
        // shortcut: the archetype IS its own runtime form.  Pin the class literal
        // here, and also publish IMPLEMENTS → self so the universal
        // IMPLEMENTS-based dispatch reverse-lookup finds this item when frames
        // headed by it arrive.
        // Two-level @Seed.Embodies (with archetype=) is intentionally not consulted
        // here — its work happens in processEmbodies, producing a separate
        // CodeItem manifest with its own IMPLEMENTS → @archetype binding.
        Seed.Embodies embodies = cls.getAnnotation(Seed.Embodies.class);
        boolean singleLevel = embodies != null
                && embodies.archetype().isEmpty()
                && seedItem.key().equals(embodies.key());
        if (singleLevel) {
            validateRuntimeClass(cls, "@Embodies");
            bindings.add(Manifest.implementation(cls));
            bindings.add(Manifest.implementsArchetype(ItemRef.fromString(seedItem.key())));
        }

        for (DatumRef frameCid : buildEndorsedFrames(librarian, cls)) {
            bindings.add(new Binding(Manifest.ENDORSES, frameCid));
        }

        String itemContext = cls.getName() + " (@Seed.Item)";
        for (Seed.Binding extra : seedItem.bindings()) {
            bindings.add(buildExplicitBinding(extra, itemContext));
        }

        // @Seed.Property fields — additional manifest bindings declared field-level
        // rather than crammed into @Seed.Item.bindings.
        processSeedProperties(cls, bindings);

        // HANDLES bindings — one per @Seed.Handler method on the class.  The
        // archetype declares its API surface here; instances inherit.  This is
        // the contract: predicate frames flow to items whose archetype HANDLES
        // them.  CodeItem manifests (built in processEmbodies) carry only
        // IMPLEMENTS plus the language binding — they don't redeclare HANDLES.
        addHandlesBindings(cls, bindings);

        librarian.persist(Body.of(
                ItemRef.of(ItemRef.fromString(seedItem.head())),
                bindings));
    }

    /**
     * Walk {@link Seed.Handler @Seed.Handler}-annotated methods on the class
     * and append a {@code @HANDLES → @<predicate>} binding for each.  HANDLES
     * lives on the archetype manifest; the method name is not recorded here
     * (the new dispatch model uses {@code Item.receive(Frame)} with internal
     * routing, not externally-named methods).
     */
    private static void addHandlesBindings(Class<?> cls, List<Binding> bindings) {
        for (Method m : cls.getDeclaredMethods()) {
            Seed.Handler ann = m.getAnnotation(Seed.Handler.class);
            if (ann == null) continue;
            bindings.add(Manifest.handles(ItemRef.fromString(ann.predicate())));
        }
    }

    /**
     * Walk every {@code @Seed.Property}-annotated field on the class and
     * append the corresponding binding to {@code bindings}.
     *
     * <p>Two modes, distinguished by {@code static}/{@code instance}:
     *
     * <ul>
     *   <li><b>Static field</b> — the field's value supplies the binding
     *       target.  For {@code role}, the value is a literal target.  For
     *       {@code schemaRole} or {@code typeRole}, the value is the
     *       matcher / TypeRef.</li>
     *   <li><b>Instance field</b> — the field's <i>Java type</i> supplies
     *       an EXPECTS declaration.  The field's type is mapped to a CG
     *       value-archetype IID (Length → {@code Length.KEY}, Color →
     *       {@code Color.KEY}, etc.) and used as the TypeRef constraint
     *       on the role.  At instance construction, {@link BodyBinder}
     *       reads the body's matching binding and populates the field.
     *       Static and instance variants both contribute to the seed
     *       manifest; only the instance form additionally binds at
     *       runtime.</li>
     * </ul>
     */
    private static void processSeedProperties(Class<?> cls, List<Binding> bindings) {
        for (Field field : cls.getDeclaredFields()) {
            Seed.Property[] properties = field.getAnnotationsByType(Seed.Property.class);
            if (properties.length == 0) continue;

            if (Modifier.isStatic(field.getModifiers())) {
                processStaticSeedProperty(field, properties, cls, bindings);
            } else {
                processInstanceSeedProperty(field, properties, cls, bindings);
            }
        }
    }

    /** Process a static {@code @Seed.Property} field — value supplies the binding target. */
    private static void processStaticSeedProperty(Field field, Seed.Property[] properties,
                                                  Class<?> cls, List<Binding> bindings) {
        field.setAccessible(true);
        Object fieldValue;
        try {
            fieldValue = field.get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(
                    "Cannot read @Seed.Property field " + cls.getName() + "."
                            + field.getName(), e);
        }

        String context = cls.getName() + "." + field.getName() + " (@Seed.Property)";
        for (Seed.Property property : properties) {
            bindings.add(buildPropertyBinding(property, fieldValue, context));
        }
    }

    /**
     * Process an instance {@code @Seed.Property} field — the field's Java
     * type supplies an EXPECTS declaration (the constraint is a TypeRef
     * to the type's CG archetype).  The runtime population from a body's
     * matching binding is handled by {@link BodyBinder} at instance
     * construction, not here.
     */
    private static void processInstanceSeedProperty(Field field, Seed.Property[] properties,
                                                    Class<?> cls, List<Binding> bindings) {
        String context = cls.getName() + "." + field.getName() + " (@Seed.Property instance)";
        for (Seed.Property property : properties) {
            // Instance fields only contribute schema (EXPECTS) — they
            // can't have a compile-time literal target.
            if (!property.role().isEmpty()) {
                // role on an instance field is shorthand for "this slot has
                // this type" — synthesize a schemaRole EXPECTS with the
                // TypeRef derived from the field's Java type.
                ItemRef archetypeIid = archetypeForFieldType(field.getType(), context);
                if (archetypeIid == null) continue;  // type doesn't map to a CG archetype; skip EXPECTS
                bindings.add(new Binding(
                        SchemaRef.fromString(property.role()),
                        List.of(),
                        TypeRef.of(archetypeIid),
                        null));
            } else if (!property.schemaRole().isEmpty()) {
                throw new IllegalStateException(
                        "@Seed.Property(schemaRole=...) on instance field " + context
                                + " — schemaRole is a static-field-only declaration");
            } else if (!property.typeRole().isEmpty()) {
                throw new IllegalStateException(
                        "@Seed.Property(typeRole=...) on instance field " + context
                                + " — typeRole is a static-field-only declaration");
            }
        }
    }

    /**
     * Map a Java field type to a CG value-archetype IID for EXPECTS
     * derivation.  Returns null when the type doesn't map cleanly
     * (e.g., raw Java types like String, List).
     */
    private static ItemRef archetypeForFieldType(Class<?> fieldType, String context) {
        // Walk up the class hierarchy looking for a public static final String KEY
        // — CG value archetypes declare their canonical key this way.
        Class<?> c = fieldType;
        while (c != null && c != Object.class) {
            try {
                Field keyField = c.getField("KEY");
                if (Modifier.isStatic(keyField.getModifiers())
                        && Modifier.isFinal(keyField.getModifiers())
                        && keyField.getType() == String.class) {
                    String key = (String) keyField.get(null);
                    return ItemRef.fromString(key);
                }
            } catch (NoSuchFieldException ignored) {
                // Try superclass
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(
                        "Cannot read KEY field on " + c.getName() + " for " + context, e);
            }
            c = c.getSuperclass();
        }
        return null;
    }

    /**
     * Build a manifest binding from a {@code @Seed.Property} annotation plus the
     * underlying field's value. The field value supplies the binding's target.
     */
    private static Binding buildPropertyBinding(Seed.Property property,
                                                Object fieldValue, String context) {
        HashID role = resolveRole(property.role(), property.schemaRole(),
                property.typeRole(), "@Seed.Property", context);
        List<CompoundKey.Qualifier> qualifiers = qualifiersFromKeys(property.qualifiers());
        Long index = property.index().length > 0 ? property.index()[0] : null;

        if (fieldValue == null) {
            throw new IllegalStateException(
                    "@Seed.Property on " + context + " has a null field value");
        }
        Object target = fieldValueAsTarget(fieldValue, context);
        return new Binding(role, qualifiers, target, index);
    }

    /**
     * Resolve the binding role from the three mutually-exclusive annotation
     * parameters ({@code role}, {@code schemaRole}, {@code typeRole}).
     * Exactly one must be a non-empty canonical-key string; produces an
     * {@link ItemRef}, {@link SchemaRef}, or {@link TypeRef} respectively.
     */
    private static HashID resolveRole(String role, String schemaRole, String typeRole,
                                      String annotationName, String context) {
        boolean hasRole = role != null && !role.isEmpty();
        boolean hasSchema = schemaRole != null && !schemaRole.isEmpty();
        boolean hasType = typeRole != null && !typeRole.isEmpty();
        int count = (hasRole ? 1 : 0) + (hasSchema ? 1 : 0) + (hasType ? 1 : 0);
        if (count == 0) {
            throw new IllegalStateException(
                    annotationName + " on " + context
                            + " requires exactly one of role / schemaRole / typeRole");
        }
        if (count > 1) {
            throw new IllegalStateException(
                    annotationName + " on " + context
                            + " has multiple roles set; role / schemaRole / typeRole are mutually exclusive");
        }
        if (hasRole)   return ItemRef.fromString(role);
        if (hasSchema) return SchemaRef.fromString(schemaRole);
        return TypeRef.fromString(typeRole);
    }

    /**
     * Coerce a field value to a binding-target, the same mapping
     * {@link #targetsFromValue} uses for {@code @Seed.Frame} fields except that
     * it returns a single target (no array → multiple-bodies fan-out).
     */
    private static Object fieldValueAsTarget(Object value, String context) {
        if (value instanceof String s) return s;
        if (value instanceof HashID ref) return ref;       // ItemRef, TypeRef, SchemaRef, ContentRef, DatumRef
        if (value instanceof Body body) return body;       // inline nested-body target
        if (value instanceof Class<?> c) return c.getName();
        if (value instanceof byte[] bytes) return bytes;
        if (value instanceof Boolean b) return b;
        if (value instanceof Long l) return l;
        if (value instanceof Integer i) return (long) i.intValue();
        if (value instanceof Instant ins) return ins;
        throw new IllegalArgumentException(
                "Unsupported @Seed.Property field type " + value.getClass()
                        + " on " + context);
    }

    /**
     * Process one two-level {@code @Seed.Embodies} class into its CodeItem manifest.
     *
     * <p>The CodeItem manifest carries:
     * <ul>
     *   <li>{@code @ITEM_ID → <codeIid>} — the CodeItem's identity.</li>
     *   <li>{@code @IMPLEMENTS → @<archetype>} — direct binding declaring which
     *       archetype this code realizes.  Reverse-lookup ("who implements
     *       archetype A?") walks the FRAME_BY_TARGET-style index over IMPLEMENTS
     *       bindings.</li>
     *   <li>{@code @JAVA:[ClassName] → "<fqcn>"} (or analogous for other
     *       languages) — the actual runtime form.</li>
     *   <li>{@code @ENDORSES → #<handles-frame-cid>} — one per {@code @Handler}
     *       method on the class.  HANDLES frames retain their current
     *       INSTRUMENT-carrying shape until task #114 lands.</li>
     * </ul>
     *
     * <p>Single-level {@code @Seed.Embodies} (no {@code archetype()}) is handled
     * in {@link #processSeed} and short-circuits here.
     */
    private static void processEmbodies(Librarian librarian, Class<?> cls) {
        Seed.Embodies embodies = cls.getAnnotation(Seed.Embodies.class);
        if (embodies == null || embodies.archetype().isEmpty()) return;

        // No validateRuntimeClass — code-items can represent classes (like Librarian)
        // that aren't constructible via the standard (ItemRef, Librarian) contract.
        // The class literal on the CodeItem is metadata; hydration-contract
        // conformance is checked at instantiation time.
        ItemRef codeIid = ItemRef.fromString(embodies.key());
        ItemRef archetypeIid = ItemRef.fromString(embodies.archetype());

        // CodeItem manifest: head = Code, ITEM_ID + IMPLEMENTS → @archetype +
        // language/class binding + ENDORSES (each HANDLES frame).
        List<Binding> bindings = new ArrayList<>();
        bindings.add(Binding.ref(Manifest.ITEM_ID, codeIid));
        bindings.add(Manifest.implementsArchetype(archetypeIid));
        bindings.add(Manifest.implementation(cls));
        for (DatumRef handlesCid : buildHandlesFrames(librarian, cls)) {
            bindings.add(new Binding(Manifest.ENDORSES, handlesCid));
        }
        librarian.persist(Body.of(ItemRef.of(ItemRef.iid(RuntimeVocabulary.Code.KEY)), bindings));
    }

    /**
     * Build and persist one HANDLES frame body per {@link Seed.Handler}-annotated method
     * on the class, returning their DatumIDs (suitable as ENDORSES targets on a
     * CodeItem manifest).
     *
     * <p>Each body has shape {@code head=HANDLES, THEME→@predicate, INSTRUMENT→"<method>"}.
     */
    private static List<DatumRef> buildHandlesFrames(Librarian librarian, Class<?> cls) {
        List<DatumRef> cids = new ArrayList<>();
        for (Method m : cls.getDeclaredMethods()) {
            Seed.Handler ann = m.getAnnotation(Seed.Handler.class);
            if (ann == null) continue;
            Body body = Body.of(
                    ItemRef.of(ItemRef.iid(CoreVocabulary.Handles.KEY)),
                    List.of(
                            Binding.ref(ItemRef.iid(ThematicRole.Theme.KEY),
                                    ItemRef.fromString(ann.predicate())),
                            new Binding(ItemRef.iid(ThematicRole.Instrument.KEY),
                                    m.getName())));
            cids.add(librarian.persist(body));
        }
        return cids;
    }

    // ==================================================================================
    // @Mints processing
    // ==================================================================================

    private static void processMints(Librarian librarian, Class<?> cls) {
        Seed.Mints mints = cls.getAnnotation(Seed.Mints.class);
        if (mints == null) return;

        // Item-class mints obey the (ItemRef, Librarian) hydration contract.
        // Value-class mints (no Item extension) are accepted as-is; their
        // construction surface is static factories on the value-class itself.
        // Either way, the IMPLEMENTS frame published below is the same shape.
        if (Item.class.isAssignableFrom(cls)) {
            validateRuntimeClass(cls, "@Mints");
        }

        ItemRef conceptIid = ItemRef.fromString(mints.key());

        // IMPLEMENTS { THEME → conceptIid, AGENT:[Java, ClassName] → text(fqcn) }
        Body implementsBody = Body.of(
                ItemRef.of(ItemRef.iid(SchemaVocabulary.Implements.KEY)),
                List.of(
                        Binding.ref(ItemRef.iid(ThematicRole.Theme.KEY), conceptIid),
                        new Binding(
                                ItemRef.iid(ThematicRole.Agent.KEY),
                                List.of(
                                        new CompoundKey.Sememe(ItemRef.iid(RuntimeVocabulary.Java.KEY)),
                                        new CompoundKey.Sememe(ItemRef.iid(RuntimeVocabulary.ClassName.KEY))),
                                cls.getName())));
        librarian.persist(implementsBody);
    }

    /**
     * Verify that the concept K identified by {@code conceptIid} has at least one
     * EXPECTS endorsement on its seed manifest. Throws if not — a {@code @Mints(K)}
     * declaration on a class for which K isn't instantiable (no EXPECTS) is a
     * programming error.
     *
     * <p>The check walks: K's seed manifest → ENDORSES bindings → fetched body's
     * head matches EXPECTS sememe.
     */
    private static void requireExpects(Librarian librarian, ItemRef conceptIid, String mintsClassName) {
        // The seed manifest must already be persisted (pass 1 ran first).
        List<DatumRef> manifestCids = librarian.library().manifestCidsForItem(conceptIid);
        if (manifestCids.isEmpty()) {
            throw new IllegalStateException(
                    "@Mints(\"" + conceptIid + "\") on " + mintsClassName
                            + " — no seed manifest found for the concept; declare @Seed for it");
        }
        for (DatumRef manifestCid : manifestCids) {
            Manifest manifest = librarian.fetchManifest(manifestCid).orElse(null);
            if (manifest == null) continue;

            // Any binding on the manifest body whose role is a SchemaRef is
            // an EXPECTS declaration.  One such binding is enough to satisfy
            // "instantiable concept."
            for (Binding b : manifest.body().bindings()) {
                if (b.role() instanceof SchemaRef) {
                    return;
                }
            }
        }
        throw new IllegalStateException(
                "@Seed.Mints(\"" + conceptIid + "\") on " + mintsClassName
                        + " — concept has no EXPECTS declarations on its manifest; "
                        + "add @Seed.Property(schemaRole = ...) fields to the seed class");
    }

    /**
     * Validate that a class meets the runtime-form contract: extends Item and has
     * a public {@code (ItemRef, Librarian)} constructor. Used for both
     * {@code @Seed.Embodies} (when paired with {@code @Seed.Item}) and {@code @Seed.Mints}.
     */
    private static void validateRuntimeClass(Class<?> cls, String annotationName) {
        if (!Item.class.isAssignableFrom(cls)) {
            throw new IllegalStateException(
                    annotationName + " class " + cls.getName() + " must extend Item");
        }
        try {
            cls.getConstructor(ItemRef.class, Librarian.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    annotationName + " class " + cls.getName()
                            + " must have a public (ItemRef, Librarian) constructor", e);
        }
    }

    // ==================================================================================
    // @Seed.Frame processing
    // ==================================================================================

    /**
     * Process all {@code @Seed.Frame}-annotated fields on {@code cls}, persist the
     * generated frame bodies, and return the CIDs of those marked for endorsement
     * ({@code endorse=true}, the default).
     *
     * <p>Bodies with {@code endorse=false} are still persisted (and auto-indexed by the
     * TokenDictionary if they carry text targets) but are not included in the returned
     * list — they don't appear in the seed manifest's ENDORSES.
     */
    private static List<DatumRef> buildEndorsedFrames(Librarian librarian, Class<?> cls) {
        Seed.Item seedItem = cls.getAnnotation(Seed.Item.class);
        ItemRef seedIid = seedItem != null ? ItemRef.fromString(seedItem.key()) : null;

        List<DatumRef> endorsedCids = new ArrayList<>();
        // Track field-level CILI declarations so we can detect double-declaration
        // (both class-level and field-level on the same seed).
        String fieldCili = null;
        for (Field field : cls.getDeclaredFields()) {
            // @Seed.Cili field — value comes from the field's String content.
            Seed.Cili ciliAnnotation = field.getAnnotation(Seed.Cili.class);
            if (ciliAnnotation != null) {
                if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) {
                    throw new IllegalStateException(
                            "@Seed.Cili field " + cls.getName() + "." + field.getName()
                                    + " must be a static String");
                }
                if (!ciliAnnotation.value().isEmpty()) {
                    throw new IllegalStateException(
                            "@Seed.Cili on field " + cls.getName() + "." + field.getName()
                                    + " must have empty value() — the field's content"
                                    + " is the source; use class-level @Seed.Cili(\"...\")"
                                    + " when you want to set the value on the annotation");
                }
                if (fieldCili != null) {
                    throw new IllegalStateException(
                            "Multiple @Seed.Cili fields on " + cls.getName()
                                    + " — a seed has exactly one CILI id");
                }
                field.setAccessible(true);
                try {
                    fieldCili = (String) field.get(null);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(
                            "Cannot read @Seed.Cili field " + cls.getName() + "." + field.getName(), e);
                }
            }

            Seed.Frame[] frames = field.getAnnotationsByType(Seed.Frame.class);
            if (frames.length == 0) continue;
            if (!Modifier.isStatic(field.getModifiers())) {
                throw new IllegalStateException(
                        "@Seed.Frame field " + cls.getName() + "." + field.getName()
                                + " must be static");
            }
            field.setAccessible(true);
            Object fieldValue;
            try {
                fieldValue = field.get(null);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(
                        "Cannot read @Seed.Frame field " + cls.getName() + "." + field.getName(), e);
            }
            if (fieldValue == null) continue;

            for (Seed.Frame frame : frames) {
                List<Body> frameBodies = buildFrameBodiesForBind(frame, fieldValue, cls, field, seedIid);
                for (Body body : frameBodies) {
                    DatumRef cid = librarian.persist(body);
                    if (frame.endorse()) {
                        endorsedCids.add(cid);
                    }
                }
            }
        }

        // Class-level shortcuts: @Seed.Gloss, @Seed.Lexeme, @Seed.Cili expand to
        // endorsed lexical-vocabulary frames, no backing field needed.
        if (seedIid != null) {
            for (Seed.Gloss gloss : cls.getAnnotationsByType(Seed.Gloss.class)) {
                Body body = buildGlossBody(gloss, seedIid);
                endorsedCids.add(librarian.persist(body));
            }
            for (Seed.Lexeme lexeme : cls.getAnnotationsByType(Seed.Lexeme.class)) {
                for (Body body : buildLexemeBodies(lexeme, seedIid, cls)) {
                    endorsedCids.add(librarian.persist(body));
                }
            }
            Seed.Cili classCili = cls.getAnnotation(Seed.Cili.class);
            String ciliText = resolveCili(cls, classCili, fieldCili);
            if (ciliText != null) {
                endorsedCids.add(librarian.persist(buildCiliBody(ciliText, seedIid)));
            }
        }

        return endorsedCids;
    }

    /**
     * Reconcile class-level vs field-level {@code @Seed.Cili} declarations and
     * return the single CILI id to use, or null if none.  Throws on:
     * <ul>
     *   <li>Class-level annotation present but {@code value} is empty.</li>
     *   <li>Both class-level and field-level CILI declared (ambiguous).</li>
     * </ul>
     */
    private static String resolveCili(Class<?> cls, Seed.Cili classCili, String fieldCili) {
        if (classCili != null && !classCili.value().isEmpty()) {
            if (fieldCili != null) {
                throw new IllegalStateException(
                        "Both class-level @Seed.Cili and field-level @Seed.Cili on "
                                + cls.getName() + " — pick one");
            }
            return classCili.value();
        }
        if (classCili != null && classCili.value().isEmpty()) {
            throw new IllegalStateException(
                    "Class-level @Seed.Cili on " + cls.getName() + " has empty value() —"
                            + " set the CILI id (e.g., @Seed.Cili(\"i24940\")) or use"
                            + " a field-level annotation");
        }
        return fieldCili;
    }

    /**
     * Build the Body for a CILI declaration.  Predicate is {@code cg.sememe:cili-id};
     * bindings are the back-link THEME → seed and {@code VALUE → "iN"}.
     */
    private static Body buildCiliBody(String ciliText, ItemRef seedIid) {
        List<Binding> bindings = List.of(
                new Binding(ItemRef.iid(ThematicRole.Theme.KEY), seedIid),
                new Binding(ItemRef.iid(ThematicRole.Value.KEY), ciliText));
        return Body.of(
                ItemRef.of(ItemRef.iid(CiliId.KEY)),
                bindings);
    }

    /**
     * Build the Body for a {@code @Seed.Gloss}.  Predicate is the gloss sememe;
     * bindings are the back-link THEME → seed and {@code VALUE[Language.English]
     * → "<text>"}.
     */
    private static Body buildGlossBody(Seed.Gloss gloss, ItemRef seedIid) {
        List<CompoundKey.Qualifier> englishQualifier = List.of(
                new CompoundKey.Sememe(ItemRef.iid(Language.English.KEY)));
        List<Binding> bindings = List.of(
                new Binding(ItemRef.iid(ThematicRole.Theme.KEY), seedIid),
                new Binding(ItemRef.iid(ThematicRole.Value.KEY),
                        englishQualifier,
                        gloss.english()));
        return Body.of(ItemRef.of(ItemRef.iid(LexicalVocabulary.Gloss.KEY)), bindings);
    }

    /**
     * Build the Body list for a {@code @Seed.Lexeme} — one Body per English
     * lemma supplied.  Each carries a back-link THEME and a
     * {@code VALUE[English, <pos>, <feature>] → "<lemma>"} binding.
     */
    private static List<Body> buildLexemeBodies(Seed.Lexeme lexeme, ItemRef seedIid,
                                                Class<?> declaringClass) {
        if (lexeme.pos().isEmpty()) {
            throw new IllegalStateException(
                    "@Seed.Lexeme on " + declaringClass.getName() + " missing pos="
                            + " — every lexeme must declare a part of speech");
        }
        String[] lemmas = lexeme.english();
        if (lemmas.length == 0) {
            throw new IllegalStateException(
                    "@Seed.Lexeme on " + declaringClass.getName() + " has no english lemmas"
                            + " — supply english = \"...\" or english = {\"a\", \"b\"}");
        }
        List<CompoundKey.Qualifier> qualifiers = List.of(
                new CompoundKey.Sememe(ItemRef.iid(Language.English.KEY)),
                new CompoundKey.Sememe(ItemRef.iid(lexeme.pos())),
                new CompoundKey.Sememe(ItemRef.iid(lexeme.feature())));
        List<Body> bodies = new ArrayList<>(lemmas.length);
        for (String lemma : lemmas) {
            List<Binding> bindings = List.of(
                    new Binding(ItemRef.iid(ThematicRole.Theme.KEY), seedIid),
                    new Binding(ItemRef.iid(ThematicRole.Value.KEY), qualifiers, lemma));
            bodies.add(Body.of(ItemRef.of(ItemRef.iid(LexicalVocabulary.Lexeme.KEY)), bindings));
        }
        return bodies;
    }

    /**
     * Given a {@code @Seed.Frame} annotation and a field value, build the frame bodies
     * that should be persisted.
     *
     * <p>Each generated body has up to (1 + 1 + N) bindings:
     * <ul>
     *   <li><b>classBinding</b> — back-link to the enclosing seed item. Default role
     *       {@link ThematicRole.Theme}. Suppressed if its role is empty or no seed is
     *       in scope. Target is implicitly the enclosing seed's IID.</li>
     *   <li><b>fieldBinding</b> — the field's value. Default role {@link ThematicRole.Value}.
     *       Suppressed if its role is empty. Target is derived from the field's runtime
     *       value (arrays produce one body per element, each carrying its own copy of
     *       classBinding and any extra {@code bindings[]} entries).</li>
     *   <li><b>bindings[]</b> — additional bindings with explicit literal or reference
     *       targets (text, integer, boolean, sememe ref). Each entry must specify
     *       exactly one target type.</li>
     * </ul>
     */
    private static List<Body> buildFrameBodiesForBind(Seed.Frame frame, Object fieldValue,
                                                      Class<?> declaringClass, Field field,
                                                      ItemRef seedIid) {
        ItemRef predicateIid = ItemRef.fromString(frame.predicate());

        Seed.Binding classAnnotation = frame.clazz();
        Seed.Binding fieldAnnotation = frame.field();

        ItemRef themeIid = frame.theme().isEmpty()
                ? seedIid
                : ItemRef.fromString(frame.theme());

        boolean hasClassBinding = !classAnnotation.role().isEmpty() && themeIid != null;
        boolean hasFieldBinding = !fieldAnnotation.role().isEmpty();

        Binding classBinding = hasClassBinding
                ? buildImplicitBinding(classAnnotation, themeIid)
                : null;

        // Pre-build the static extra bindings — same on every body produced.
        String fieldContext = declaringClass.getName() + "." + field.getName();
        List<Binding> extras = new ArrayList<>(frame.bindings().length);
        for (Seed.Binding extra : frame.bindings()) {
            extras.add(buildExplicitBinding(extra, fieldContext));
        }

        // Field-role binding suppressed: produce a single body with classBinding (if
        // present) + extras. The field's value is unused in this branch.
        if (!hasFieldBinding) {
            List<Binding> bindings = new ArrayList<>(1 + extras.size());
            if (classBinding != null) bindings.add(classBinding);
            bindings.addAll(extras);
            return List.of(Body.of(ItemRef.of(predicateIid), bindings));
        }

        // Field-role binding present: walk targets (arrays produce multiple bodies).
        List<Object> targets = targetsFromValue(fieldValue, declaringClass, field);
        List<Body> bodies = new ArrayList<>(targets.size());
        for (Object target : targets) {
            List<Binding> bindings = new ArrayList<>(2 + extras.size());
            if (classBinding != null) bindings.add(classBinding);
            bindings.add(buildImplicitBinding(fieldAnnotation, target));
            bindings.addAll(extras);
            bodies.add(Body.of(ItemRef.of(predicateIid), bindings));
        }
        return bodies;
    }

    /**
     * Build a data {@link Binding} for the {@code classBinding} or {@code fieldBinding}
     * slots. The target is supplied by the caller (the enclosing seed's IID for
     * classBinding, the field-derived target for fieldBinding); any {@code text},
     * {@code integer}, {@code bool}, or {@code ref} fields on the annotation are
     * ignored in these slots.
     */
    private static Binding buildImplicitBinding(Seed.Binding ann,
                                                Object target) {
        ItemRef role = ItemRef.fromString(ann.role());
        return new Binding(role, qualifiersFromAnnotation(ann), target);
    }

    /**
     * Build a data {@link Binding} for an entry in any explicit-target context
     * ({@code @Seed.Frame.bindings} or {@code @Seed.Item.bindings}). Exactly one
     * of {@code text}, {@code integer}, {@code bool}, {@code ref} must indicate
     * "set". The optional {@code index} flows into the resulting Binding's
     * ordinal slot.
     *
     * <p>{@code context} is a human-readable description of where this annotation
     * came from — used only in error messages.
     */
    private static Binding buildExplicitBinding(Seed.Binding ann, String context) {
        HashID role = resolveRole(ann.role(), ann.schemaRole(), ann.typeRole(),
                "@Seed.Binding", context);
        Object target = explicitTarget(ann, context);
        Long index = ann.index().length > 0 ? ann.index()[0] : null;
        return new Binding(role, qualifiersFromAnnotation(ann), target, index);
    }

    private static List<CompoundKey.Qualifier> qualifiersFromAnnotation(
            Seed.Binding ann) {
        return qualifiersFromKeys(ann.qualifiers());
    }

    private static List<CompoundKey.Qualifier> qualifiersFromKeys(String[] keys) {
        if (keys.length == 0) return List.of();
        List<CompoundKey.Qualifier> qualifiers = new ArrayList<>(keys.length);
        for (String key : keys) {
            qualifiers.add(new CompoundKey.Sememe(ItemRef.fromString(key)));
        }
        return qualifiers;
    }

    private static Object explicitTarget(Seed.Binding ann, String context) {
        boolean hasText = !ann.text().isEmpty();
        boolean hasInteger = ann.integer().length > 0;
        boolean hasBool = ann.bool().length > 0;
        boolean hasRef = !ann.ref().isEmpty();

        int set = (hasText ? 1 : 0) + (hasInteger ? 1 : 0) + (hasBool ? 1 : 0)
                + (hasRef ? 1 : 0);
        if (set != 1) {
            throw new IllegalStateException(
                    "@Seed.Binding entry on " + context
                            + " must specify exactly one of text/integer/bool/ref (found "
                            + set + ")");
        }
        if (hasText) return ann.text();
        if (hasInteger) return (long) (ann.integer()[0]);
        if (hasBool) return ann.bool()[0];
        return ItemRef.fromString(ann.ref());
    }

    @SuppressWarnings("unchecked")
    private static List<Object> targetsFromValue(Object value, Class<?> declaringClass, Field field) {
        if (value instanceof String s) {
            return List.of(s);
        }
        if (value instanceof String[] arr) {
            List<Object> ts = new ArrayList<>(arr.length);
            for (String s : arr) ts.add(s);
            return ts;
        }
        if (value instanceof ItemRef id) {
            return List.of(id);
        }
        if (value instanceof ItemRef[] ids) {
            List<Object> ts = new ArrayList<>(ids.length);
            for (ItemRef id : ids) ts.add(id);
            return ts;
        }
        if (value instanceof Class<?> c) {
            // Plain text target; the binding's qualifiers (declared on @Seed.Frame
            // or @Seed.Binding) carry the ClassName narrowing semantically.
            return List.of(c.getName());
        }
        if (value instanceof byte[] bytes) {
            return List.of(bytes);
        }
        if (value instanceof Boolean b) {
            return List.of(b);
        }
        if (value instanceof Long l) {
            return List.of((long) (l));
        }
        if (value instanceof Integer i) {
            return List.of((long) (i.longValue()));
        }
        if (value instanceof Instant instant) {
            return List.of(instant);
        }
        throw new IllegalArgumentException(
                "Unsupported @Seed.Frame field type: " + value.getClass()
                        + " on " + declaringClass.getName() + "." + field.getName());
    }
}
