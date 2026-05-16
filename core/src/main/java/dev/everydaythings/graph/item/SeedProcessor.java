package dev.everydaythings.graph.item;

import dev.everydaythings.graph.*;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.id.CompoundKey;
import dev.everydaythings.graph.id.DatumRef;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.SchemaVocabulary;

import dev.everydaythings.graph.runtime.RuntimeVocabulary;
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
        // shortcut: the archetype IS its own runtime form, class literal pinned here.
        // Two-level @Seed.Embodies (with archetype=) is intentionally not consulted
        // here — its work happens in processEmbodies, leaving this manifest untouched.
        Seed.Embodies embodies = cls.getAnnotation(Seed.Embodies.class);
        boolean singleLevel = embodies != null
                && embodies.archetype().isEmpty()
                && seedItem.key().equals(embodies.key());
        if (singleLevel) {
            validateRuntimeClass(cls, "@Embodies");
            bindings.add(Manifest.implementation(cls));
        }

        for (DatumRef frameCid : buildEndorsedFrames(librarian, cls)) {
            bindings.add(new Binding(Manifest.ENDORSES, frameCid));
        }

        String itemContext = cls.getName() + " (@Seed.Item)";
        for (Seed.Binding extra : seedItem.bindings()) {
            bindings.add(buildExplicitBinding(extra, itemContext));
        }

        librarian.persist(Body.of(
                ItemRef.of(ItemRef.fromString(seedItem.head())),
                bindings));
    }

    /**
     * Process one two-level {@code @Seed.Embodies} class into its CodeItem manifest
     * plus the standalone IMPLEMENTS frame that links it to the archetype.
     *
     * <p>Two manifests are minted, both heads carry their own structural meaning:
     * <ul>
     *   <li><b>CodeItem manifest</b> — head = {@link RuntimeVocabulary.Code}, ITEM_ID =
     *       the CodeItem's key. Carries the class literal as IMPLEMENTATION (placeholder
     *       — the binding's role will move to a qualifier-bearing form in the Literal
     *       cleanup), endorses one HANDLES frame per {@code @Handler} method, and
     *       endorses the IMPLEMENTS frame that points at the archetype.</li>
     *   <li><b>IMPLEMENTS frame</b> — head = {@link Implements}, {@code THEME →
     *       @archetype, AGENT → @codeItem}. Independent frame body, indexable by
     *       archetype — that's the lookup path "who implements archetype A?"</li>
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

        // Mint the standalone IMPLEMENTS frame first — its DatumRef is what the
        // CodeItem manifest endorses (and what other archetype-implementation
        // lookups will index).
        Body implementsBody = Body.of(
                ItemRef.of(SchemaVocabulary.Implements.IID),
                List.of(
                        Binding.ref(ThematicRole.Theme.IID, archetypeIid),
                        Binding.ref(ThematicRole.Agent.IID, codeIid)));
        DatumRef implementsCid = librarian.persist(implementsBody);

        // CodeItem manifest: head = Code, ITEM_ID + class literal +
        // ENDORSES (the IMPLEMENTS frame + each HANDLES frame).
        List<Binding> bindings = new ArrayList<>();
        bindings.add(Binding.ref(Manifest.ITEM_ID, codeIid));
        bindings.add(Manifest.implementation(cls));
        bindings.add(new Binding(Manifest.ENDORSES, implementsCid));
        for (DatumRef handlesCid : buildHandlesFrames(librarian, cls)) {
            bindings.add(new Binding(Manifest.ENDORSES, handlesCid));
        }
        librarian.persist(Body.of(ItemRef.of(RuntimeVocabulary.Code.IID), bindings));
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
                    ItemRef.of(CoreVocabulary.Handles.IID),
                    List.of(
                            Binding.ref(ThematicRole.Theme.IID,
                                    ItemRef.fromString(ann.predicate())),
                            new Binding(ThematicRole.Instrument.IID,
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

        validateRuntimeClass(cls, "@Mints");

        ItemRef conceptIid = ItemRef.fromString(mints.key());

        // IMPLEMENTS { THEME → conceptIid, AGENT:[Java, ClassName] → text(fqcn) }
        Body implementsBody = Body.of(
                ItemRef.of(SchemaVocabulary.Implements.IID),
                List.of(
                        Binding.ref(ThematicRole.Theme.IID, conceptIid),
                        new Binding(
                                ThematicRole.Agent.IID,
                                List.of(
                                        new CompoundKey.Sememe(RuntimeVocabulary.Java.IID),
                                        new CompoundKey.Sememe(RuntimeVocabulary.ClassName.IID)),
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
            for (Binding endorses : manifest.endorses()) {
                if (!(endorses.target() instanceof DatumRef frameCid)) continue;
                Body endorsedBody = librarian.fetchFrame(frameCid)
                        .map(f -> f.body())
                        .orElse(null);
                if (endorsedBody == null) continue;
                if (endorsedBody.head() instanceof ItemRef ref
                        && SchemaVocabulary.Expects.IID.equals(ref.iid())) {
                    return;  // Found at least one EXPECTS endorsement; we're good.
                }
            }
        }
        throw new IllegalStateException(
                "@Seed.Mints(\"" + conceptIid + "\") on " + mintsClassName
                        + " — concept has no EXPECTS endorsements declaring its instance schema; "
                        + "add @Seed.Frame(predicate = SchemaVocabulary.Expects.KEY, ...) to the seed class");
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
        for (Field field : cls.getDeclaredFields()) {
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
        return endorsedCids;
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

        boolean hasClassBinding = !classAnnotation.role().isEmpty() && seedIid != null;
        boolean hasFieldBinding = !fieldAnnotation.role().isEmpty();

        Binding classBinding = hasClassBinding
                ? buildImplicitBinding(classAnnotation, seedIid)
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
     * ({@code @Seed.Frame.bindings} or {@code @Seed.Item.bindings}). Exactly one of
     * {@code text}, {@code integer}, {@code bool}, {@code ref} must indicate "set"
     * (non-empty string for text/ref, non-empty array for integer/bool).
     *
     * <p>{@code context} is a human-readable description of where this annotation
     * came from — used only in error messages.
     */
    private static Binding buildExplicitBinding(Seed.Binding ann, String context) {
        ItemRef role = ItemRef.fromString(ann.role());
        Object target = explicitTarget(ann, context);
        return new Binding(role, qualifiersFromAnnotation(ann), target);
    }

    private static List<CompoundKey.Qualifier> qualifiersFromAnnotation(
            Seed.Binding ann) {
        String[] keys = ann.qualifiers();
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

        int set = (hasText ? 1 : 0) + (hasInteger ? 1 : 0) + (hasBool ? 1 : 0) + (hasRef ? 1 : 0);
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
