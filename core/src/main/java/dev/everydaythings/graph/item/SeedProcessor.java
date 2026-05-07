package dev.everydaythings.graph.item;

import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.Body;
import dev.everydaythings.graph.item.id.CompoundKey;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.ItemRef;
import dev.everydaythings.graph.runtime.Librarian;
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

    /** Canonical key for the IMPLEMENTS predicate sememe. */
    private static final String IMPLEMENTS_KEY = "cg.sememe:implements";
    private static final ItemID IMPLEMENTS_IID = ItemID.fromString(IMPLEMENTS_KEY);

    /** Canonical key for the THEME role sememe. */
    private static final String THEME_KEY = "cg.role:theme";
    private static final ItemID THEME_IID = ItemID.fromString(THEME_KEY);

    /** Canonical key for the AGENT role sememe. */
    private static final String AGENT_KEY = "cg.role:agent";
    private static final ItemID AGENT_IID = ItemID.fromString(AGENT_KEY);

    /** Canonical key for the Java runtime sememe. */
    private static final String JAVA_RUNTIME_KEY = "cg.runtime:java";
    private static final ItemID JAVA_RUNTIME_IID = ItemID.fromString(JAVA_RUNTIME_KEY);

    /** Canonical key for the EXPECTS predicate sememe. */
    private static final String EXPECTS_KEY = "cg.sememe:expects";
    private static final ItemID EXPECTS_IID = ItemID.fromString(EXPECTS_KEY);

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
            ClassInfoList seedClasses = result.getClassesWithAnnotation(Seed.class.getName());
            for (ClassInfo classInfo : seedClasses) {
                Class<?> cls = classInfo.loadClass();
                processSeed(librarian, cls);
            }

            // Pass 2: @Mints classes — publishes IMPLEMENTS frames for instance-class
            // declarations. This is the data signal that drives CREATE/MINT.
            // Cross-validate that the target concept has EXPECTS endorsements (the data
            // signal of instantiability) — an instance class for a non-instantiable
            // concept is a programming error.
            ClassInfoList mintsClasses = result.getClassesWithAnnotation(Mints.class.getName());
            for (ClassInfo classInfo : mintsClasses) {
                Class<?> cls = classInfo.loadClass();
                Mints mints = cls.getAnnotation(Mints.class);
                ItemID conceptIid = ItemID.fromString(mints.key());
                requireExpects(librarian, conceptIid, cls.getName());
                processMints(librarian, cls);
            }

            // Validate: bare @Embodies (without a matching @Seed on the same class) is
            // an error — @Embodies's effect is the combination with @Seed.
            ClassInfoList embodiesClasses = result.getClassesWithAnnotation(Embodies.class.getName());
            for (ClassInfo classInfo : embodiesClasses) {
                Class<?> cls = classInfo.loadClass();
                Embodies embodies = cls.getAnnotation(Embodies.class);
                Seed seed = cls.getAnnotation(Seed.class);
                if (seed == null || !seed.key().equals(embodies.key())) {
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
        Seed seed = cls.getAnnotation(Seed.class);
        if (seed == null) return;

        ItemID iid = ItemID.fromString(seed.key());

        // Build endorsed frame bodies from @Bind-annotated static fields.
        List<ContentID> endorsedFrameCids = buildEndorsedFrames(librarian, cls);

        // Build the seed manifest body.
        List<Binding> manifestBindings = new ArrayList<>();
        manifestBindings.add(Binding.ref(Manifest.ITEM_ID, iid));

        // @Embodies(K) on the same class (same key) explicitly declares "I AM this
        // seed item." Add the IMPLEMENTATION binding so future hydration of the seed
        // item produces an instance of this class.
        Embodies selfEmbodies = cls.getAnnotation(Embodies.class);
        if (selfEmbodies != null && seed.key().equals(selfEmbodies.key())) {
            validateRuntimeClass(cls, "@Embodies");
            manifestBindings.add(Manifest.javaImplementation(cls));
        }

        for (ContentID frameCid : endorsedFrameCids) {
            manifestBindings.add(new Binding(
                    Manifest.ENDORSES,
                    BindingTarget.ref(frameCid)));
        }

        Body manifestBody = Body.of(
                ItemRef.of(Item.ARCHETYPE),  // generic archetype for now
                manifestBindings);
        librarian.persist(manifestBody);
    }

    // ==================================================================================
    // @Mints processing
    // ==================================================================================

    private static void processMints(Librarian librarian, Class<?> cls) {
        Mints mints = cls.getAnnotation(Mints.class);
        if (mints == null) return;

        validateRuntimeClass(cls, "@Mints");

        ItemID conceptIid = ItemID.fromString(mints.key());

        // IMPLEMENTS { THEME → conceptIid, AGENT[runtime=java] → Literal.ofJavaClass(cls) }
        Body implementsBody = Body.of(
                ItemRef.of(IMPLEMENTS_IID),
                List.of(
                        Binding.ref(THEME_IID, conceptIid),
                        new Binding(
                                AGENT_IID,
                                List.of(new CompoundKey.Sememe(JAVA_RUNTIME_IID)),
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
                        && EXPECTS_IID.equals(ref.iid())) {
                    return;  // Found at least one EXPECTS endorsement; we're good.
                }
            }
        }
        throw new IllegalStateException(
                "@Mints(\"" + conceptIid + "\") on " + mintsClassName
                        + " — concept has no EXPECTS endorsements declaring its instance schema; "
                        + "add @Bind(predicate = Expects.KEY, ...) to the seed class");
    }

    /**
     * Validate that a class meets the runtime-form contract: extends Item and has
     * a public {@code (ItemID, Librarian)} constructor. Used for both {@code @Embodies}
     * (when paired with {@code @Seed}) and {@code @Mints}.
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
    // @Bind processing
    // ==================================================================================

    private static List<ContentID> buildEndorsedFrames(Librarian librarian, Class<?> cls) {
        List<ContentID> cids = new ArrayList<>();
        for (Field field : cls.getDeclaredFields()) {
            Bind[] binds = field.getAnnotationsByType(Bind.class);
            if (binds.length == 0) continue;
            if (!Modifier.isStatic(field.getModifiers())) {
                throw new IllegalStateException(
                        "@Bind field " + cls.getName() + "." + field.getName()
                                + " must be static");
            }
            field.setAccessible(true);
            Object fieldValue;
            try {
                fieldValue = field.get(null);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(
                        "Cannot read @Bind field " + cls.getName() + "." + field.getName(), e);
            }
            if (fieldValue == null) continue;

            for (Bind bind : binds) {
                List<Body> frameBodies = buildFrameBodiesForBind(bind, fieldValue, cls, field);
                for (Body body : frameBodies) {
                    cids.add(librarian.persist(body));
                }
            }
        }
        return cids;
    }

    /**
     * Given a {@code @Bind} annotation and a field value, build the frame bodies
     * that should be persisted. Arrays produce one body per element; scalars
     * produce one body.
     */
    private static List<Body> buildFrameBodiesForBind(Bind bind, Object fieldValue,
                                                      Class<?> declaringClass, Field field) {
        ItemID predicateIid = ItemID.fromString(bind.predicate());
        ItemID roleIid = ItemID.fromString(bind.role());
        List<CompoundKey.FrameToken> qualifiers = new ArrayList<>(bind.qualifiers().length);
        for (String qualifierKey : bind.qualifiers()) {
            qualifiers.add(new CompoundKey.Sememe(ItemID.fromString(qualifierKey)));
        }

        List<BindingTarget> targets = targetsFromValue(fieldValue, declaringClass, field);
        List<Body> bodies = new ArrayList<>(targets.size());
        for (BindingTarget target : targets) {
            Binding binding = new Binding(roleIid, qualifiers, target);
            bodies.add(Body.of(ItemRef.of(predicateIid), List.of(binding)));
        }
        return bodies;
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
                "Unsupported @Bind field type: " + value.getClass()
                        + " on " + declaringClass.getName() + "." + field.getName());
    }
}
