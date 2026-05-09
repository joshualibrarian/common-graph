package dev.everydaythings.graph.item;

import dev.everydaythings.graph.*;
import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.Body;
import dev.everydaythings.graph.item.id.CompoundKey;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.ItemRef;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.semantics.Expects;
import dev.everydaythings.graph.semantics.Implements;
import dev.everydaythings.graph.semantics.Runtimes;
import dev.everydaythings.graph.semantics.ThematicRole;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;

import java.lang.reflect.Field;
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

            // Pass 1: @Seed classes — produces seed manifest bodies. @Embodies on the
            // same class (same key) is detected here and contributes IMPLEMENTATION.
            ClassInfoList seedClasses = result.getClassesWithAnnotation(Seed.Item.class.getName());
            for (ClassInfo classInfo : seedClasses) {
                Class<?> cls = classInfo.loadClass();
                processSeed(librarian, cls);
            }

            // Pass 2: @Mints classes — publishes IMPLEMENTS frames for instance-class
            // declarations. This is the data signal that drives CREATE/MINT.
            // Cross-validate that the target concept has EXPECTS endorsements (the data
            // signal of instantiability) — an instance class for a non-instantiable
            // concept is a programming error.
            ClassInfoList mintsClasses = result.getClassesWithAnnotation(Seed.Mints.class.getName());
            for (ClassInfo classInfo : mintsClasses) {
                Class<?> cls = classInfo.loadClass();
                Seed.Mints mints = cls.getAnnotation(Seed.Mints.class);
                ItemID conceptIid = ItemID.fromString(mints.key());
                requireExpects(librarian, conceptIid, cls.getName());
                processMints(librarian, cls);
            }

            // Validate: bare @Embodies (without a matching @Seed on the same class) is
            // an error — @Embodies's effect is the combination with @Seed.
            ClassInfoList embodiesClasses = result.getClassesWithAnnotation(Seed.Embodies.class.getName());
            for (ClassInfo classInfo : embodiesClasses) {
                Class<?> cls = classInfo.loadClass();
                Seed.Embodies embodies = cls.getAnnotation(Seed.Embodies.class);
                Seed.Item seedItem = cls.getAnnotation(Seed.Item.class);
                if (seedItem == null || !seedItem.key().equals(embodies.key())) {
                    throw new IllegalStateException(
                            "@Embodies(" + embodies.key() + ") on " + cls.getName()
                                    + " requires @Seed(\"" + embodies.key()
                                    + "\") on the same class");
                }
            }
        }
    }

    // ==================================================================================
    // @Seed processing
    // ==================================================================================

    private static void processSeed(Librarian librarian, Class<?> cls) {
        Seed.Item seedItem = cls.getAnnotation(Seed.Item.class);
        if (seedItem == null) return;

        ItemID iid = ItemID.fromString(seedItem.key());

        // Build endorsed frame bodies from @Seed.Frame-annotated static fields.
        List<ContentID> endorsedFrameCids = buildEndorsedFrames(librarian, cls);

        // Build the seed manifest body.
        List<Binding> manifestBindings = new ArrayList<>();
        manifestBindings.add(Binding.ref(Manifest.ITEM_ID, iid));

        // @Embodies(K) on the same class (same key) explicitly declares "I AM this
        // seed item." Add the IMPLEMENTATION binding so future hydration of the seed
        // item produces an instance of this class.
        Seed.Embodies selfEmbodies = cls.getAnnotation(Seed.Embodies.class);
        if (selfEmbodies != null && seedItem.key().equals(selfEmbodies.key())) {
            validateRuntimeClass(cls, "@Embodies");
            manifestBindings.add(Manifest.javaImplementation(cls));
        }

        for (ContentID frameCid : endorsedFrameCids) {
            manifestBindings.add(new Binding(
                    Manifest.ENDORSES,
                    BindingTarget.ref(frameCid)));
        }

        // Direct manifest-body bindings declared on @Seed.Item.bindings — for one-off
        // entries that don't warrant their own static field.
        String itemContext = cls.getName() + " (@Seed.Item)";
        for (Seed.Binding extra : seedItem.bindings()) {
            manifestBindings.add(buildExplicitBinding(extra, itemContext));
        }

        Body manifestBody = Body.of(
                ItemRef.of(ItemID.fromString(seedItem.head())),
                manifestBindings);
        librarian.persist(manifestBody);
    }

    // ==================================================================================
    // @Mints processing
    // ==================================================================================

    private static void processMints(Librarian librarian, Class<?> cls) {
        Seed.Mints mints = cls.getAnnotation(Seed.Mints.class);
        if (mints == null) return;

        validateRuntimeClass(cls, "@Mints");

        ItemID conceptIid = ItemID.fromString(mints.key());

        // IMPLEMENTS { THEME → conceptIid, AGENT[runtime=java] → Literal.ofJavaClass(cls) }
        Body implementsBody = Body.of(
                ItemRef.of(Implements.IID),
                List.of(
                        Binding.ref(ThematicRole.Theme.IID, conceptIid),
                        new Binding(
                                ThematicRole.Agent.IID,
                                List.of(new CompoundKey.Sememe(Runtimes.Java.IID)),
                                Literal.ofJavaClass(cls))));
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
    private static void requireExpects(Librarian librarian, ItemID conceptIid, String mintsClassName) {
        // The seed manifest must already be persisted (pass 1 ran first).
        List<ContentID> manifestCids = librarian.library().manifestCidsForItem(conceptIid);
        if (manifestCids.isEmpty()) {
            throw new IllegalStateException(
                    "@Mints(\"" + conceptIid + "\") on " + mintsClassName
                            + " — no seed manifest found for the concept; declare @Seed for it");
        }
        for (ContentID manifestCid : manifestCids) {
            Manifest manifest = librarian.fetchManifest(manifestCid).orElse(null);
            if (manifest == null) continue;
            for (Binding endorses : manifest.endorses()) {
                if (!(endorses.target() instanceof BindingTarget.RefTarget refTarget)) continue;
                ContentID frameCid = refTarget.asCid();
                Body endorsedBody = librarian.fetchFrame(frameCid)
                        .map(f -> f.body())
                        .orElse(null);
                if (endorsedBody == null) continue;
                if (endorsedBody.head() instanceof dev.everydaythings.graph.item.id.ItemRef ref
                        && Expects.IID.equals(ref.iid())) {
                    return;  // Found at least one EXPECTS endorsement; we're good.
                }
            }
        }
        throw new IllegalStateException(
                "@Seed.Mints(\"" + conceptIid + "\") on " + mintsClassName
                        + " — concept has no EXPECTS endorsements declaring its instance schema; "
                        + "add @Seed.Frame(predicate = Expects.KEY, ...) to the seed class");
    }

    /**
     * Validate that a class meets the runtime-form contract: extends Item and has
     * a public {@code (ItemID, Librarian)} constructor. Used for both
     * {@code @Seed.Embodies} (when paired with {@code @Seed.Item}) and {@code @Seed.Mints}.
     */
    private static void validateRuntimeClass(Class<?> cls, String annotationName) {
        if (!Item.class.isAssignableFrom(cls)) {
            throw new IllegalStateException(
                    annotationName + " class " + cls.getName() + " must extend Item");
        }
        try {
            cls.getConstructor(ItemID.class, Librarian.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    annotationName + " class " + cls.getName()
                            + " must have a public (ItemID, Librarian) constructor", e);
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
    private static List<ContentID> buildEndorsedFrames(Librarian librarian, Class<?> cls) {
        Seed.Item seedItem = cls.getAnnotation(Seed.Item.class);
        ItemID seedIid = seedItem != null ? ItemID.fromString(seedItem.key()) : null;

        List<ContentID> endorsedCids = new ArrayList<>();
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
                    ContentID cid = librarian.persist(body);
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
                                                      ItemID seedIid) {
        ItemID predicateIid = ItemID.fromString(frame.predicate());

        Seed.Binding classAnnotation = frame.clazz();
        Seed.Binding fieldAnnotation = frame.field();

        boolean hasClassBinding = !classAnnotation.role().isEmpty() && seedIid != null;
        boolean hasFieldBinding = !fieldAnnotation.role().isEmpty();

        Binding classBinding = hasClassBinding
                ? buildImplicitBinding(classAnnotation, BindingTarget.iid(seedIid))
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
        List<BindingTarget> targets = targetsFromValue(fieldValue, declaringClass, field);
        List<Body> bodies = new ArrayList<>(targets.size());
        for (BindingTarget target : targets) {
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
                                                BindingTarget target) {
        ItemID role = ItemID.fromString(ann.role());
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
        ItemID role = ItemID.fromString(ann.role());
        BindingTarget target = explicitTarget(ann, context);
        return new Binding(role, qualifiersFromAnnotation(ann), target);
    }

    private static List<CompoundKey.FrameToken> qualifiersFromAnnotation(
            Seed.Binding ann) {
        String[] keys = ann.qualifiers();
        if (keys.length == 0) return List.of();
        List<CompoundKey.FrameToken> qualifiers = new ArrayList<>(keys.length);
        for (String key : keys) {
            qualifiers.add(new CompoundKey.Sememe(ItemID.fromString(key)));
        }
        return qualifiers;
    }

    private static BindingTarget explicitTarget(Seed.Binding ann, String context) {
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
        if (hasText) return Literal.ofText(ann.text());
        if (hasInteger) return Literal.ofInteger(ann.integer()[0]);
        if (hasBool) return Literal.ofBoolean(ann.bool()[0]);
        return BindingTarget.iid(ItemID.fromString(ann.ref()));
    }

    @SuppressWarnings("unchecked")
    private static List<BindingTarget> targetsFromValue(Object value, Class<?> declaringClass, Field field) {
        if (value instanceof String s) {
            return List.of(Literal.ofText(s));
        }
        if (value instanceof String[] arr) {
            List<BindingTarget> ts = new ArrayList<>(arr.length);
            for (String s : arr) ts.add(Literal.ofText(s));
            return ts;
        }
        if (value instanceof ItemID id) {
            return List.of(BindingTarget.iid(id));
        }
        if (value instanceof ItemID[] ids) {
            List<BindingTarget> ts = new ArrayList<>(ids.length);
            for (ItemID id : ids) ts.add(BindingTarget.iid(id));
            return ts;
        }
        if (value instanceof Class<?> c) {
            return List.of(Literal.ofJavaClass(c));
        }
        if (value instanceof byte[] bytes) {
            return List.of(new Literal(Literal.TYPE_CBOR, bytes));
        }
        if (value instanceof Boolean b) {
            return List.of(Literal.ofBoolean(b));
        }
        if (value instanceof Long l) {
            return List.of(Literal.ofInteger(l));
        }
        if (value instanceof Integer i) {
            return List.of(Literal.ofInteger(i.longValue()));
        }
        if (value instanceof Instant instant) {
            return List.of(Literal.ofInstant(instant));
        }
        throw new IllegalArgumentException(
                "Unsupported @Seed.Frame field type: " + value.getClass()
                        + " on " + declaringClass.getName() + "." + field.getName());
    }
}
