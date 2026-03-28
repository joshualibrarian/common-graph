package dev.everydaythings.graph.library;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.Frame;
import dev.everydaythings.graph.frame.FrameBody;
import dev.everydaythings.graph.frame.ItemFrame;
import dev.everydaythings.graph.item.Implements;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.value.Value;
import lombok.extern.log4j.Log4j2;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates fully-formed seed Items from {@code @ItemSeed}-annotated classes.
 *
 * <p>Given a class with {@code @ItemSeed}, this factory:
 * <ol>
 *   <li>Instantiates the class (must be a Sememe subclass with a no-arg constructor,
 *       or falls back to creating a plain Sememe from the canonical key)</li>
 *   <li>Scans static {@code @ItemFrame} fields → creates frames (glosses, symbols, lexemes)</li>
 *   <li>Scans {@code @Implements} → creates IMPLEMENTED_BY frame</li>
 *   <li>Processes {@code @ItemSeed} slots</li>
 * </ol>
 *
 * <p>The result is a fully-formed Item ready for storage. No post-store
 * bolt-on steps needed.
 *
 * <p>No IID validation is performed — referenced items may not exist yet
 * during bootstrap. They will exist by the time the bootstrap completes.
 */
@Log4j2
public final class SeedItemFactory {

    private SeedItemFactory() {}

    /**
     * Create a fully-formed seed Item from a {@code @ItemSeed}-annotated class.
     *
     * @param clazz the annotated class
     * @return the fully-formed item with all frames, or null if the class can't be processed
     */
    public static Item create(Class<?> clazz) {
        ItemSeed seedAnn = clazz.getAnnotation(ItemSeed.class);
        if (seedAnn == null) return null;

        String key = seedAnn.key();
        if (key == null || key.isBlank()) return null;

        // 1. Instantiate the seed item
        Sememe item = instantiate(clazz, key);
        if (item == null) return null;

        // 2. Scan static @ItemFrame fields → create frames (includes EXPECTS)
        scanStaticFrameFields(clazz, item);
        // 3. Scan @Implements → create IMPLEMENTED_BY frame
        scanImplements(clazz, item);

        return item;
    }

    // ==================================================================================
    // Instantiation
    // ==================================================================================

    private static Sememe instantiate(Class<?> clazz, String key) {
        // If the class IS a Sememe subclass, instantiate it directly
        if (Sememe.class.isAssignableFrom(clazz) && clazz != Sememe.class) {
            try {
                var ctor = clazz.getDeclaredConstructor();
                ctor.setAccessible(true);
                return (Sememe) ctor.newInstance();
            } catch (Exception e) {
                logger.debug("Could not instantiate {}, falling back to Sememe: {}",
                        clazz.getSimpleName(), e.getMessage());
            }
        }

        // Fall back to plain Sememe
        return new Sememe(key);
    }

    // ==================================================================================
    // Frame scanning
    // ==================================================================================

    /**
     * Scan static @ItemFrame fields on the class and create frames on the item.
     *
     * <p>Only scans the LEAF class — static fields on parent classes belong to
     * the parent's type item, not this one.
     */
    private static void scanStaticFrameFields(Class<?> clazz, Item item) {
        for (Field field : clazz.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;

            ItemFrame ann = field.getAnnotation(ItemFrame.class);
            if (ann == null) continue;
            if (ann.predicate().isEmpty()) continue;

            try {
                field.setAccessible(true);
                Object value = field.get(null);
                if (value == null) continue;

                createFrame(item, ann, value);
            } catch (Exception e) {
                logger.warn("Failed to process @ItemFrame field {}.{}: {}",
                        clazz.getSimpleName(), field.getName(), e.getMessage());
            }
        }
    }

    /**
     * Create a frame on the item from an @ItemFrame annotation and field value.
     */
    private static void createFrame(Item item, ItemFrame ann, Object value) {
        ItemID head = ItemID.fromString(ann.predicate());

        // Resolve bindings from classAs / fieldAs
        ItemID selfRole = ItemID.fromString(ann.classAs().role());
        ItemID valueRole = ItemID.fromString(ann.fieldAs().role());

        List<FrameKey.FrameToken> qualifiers = new ArrayList<>();
        for (String q : ann.fieldAs().qualifiers()) {
            qualifiers.add(new FrameKey.Sememe(ItemID.fromString(q)));
        }

        // Build bindings: self binding + one or more value bindings
        List<Binding> bindings = new ArrayList<>();
        bindings.add(new Binding(selfRole, BindingTarget.iid(item.iid())));

        // Handle array/list values → multiple bindings with the same key
        List<Object> values = flattenValue(value);
        for (Object v : values) {
            BindingTarget target = encodeValue(v);
            if (target != null) {
                bindings.add(Binding.qualified(valueRole, qualifiers, target, true, false));
            }
        }
        if (bindings.size() < 2) return; // no value bindings created

        FrameBody body = new FrameBody(head, bindings);
        // Derive FrameKey from body for lookup
        FrameKey frameKey = body.selector();

        // Create Frame and add to item
        byte[] bytes = body.encodeBinary(Canonical.Scope.RECORD);
        ContentID cid = ContentID.of(bytes);
        Frame frame = new Frame(frameKey, head, body, cid, true);
        item.frames().add(frame);
        item.frames().setLive(frameKey, body);
    }

    /**
     * Scan @Implements annotation and create IMPLEMENTED_BY frame.
     *
     * <p>The IMPLEMENTED_BY frame binds the item (THEME) to its Java class (GOAL).
     * This replaces the old SeedVocabulary.registerImplementation() post-store step.
     */
    private static void scanImplements(Class<?> clazz, Item item) {
        Implements ann = clazz.getAnnotation(Implements.class);
        if (ann == null) return;

        String implKey = ann.value();
        if (implKey == null || implKey.isBlank()) return;

        // The IMPLEMENTED_BY frame: THEME = this item, GOAL = Java class name
        ItemID predicate = CoreVocabulary.ImplementedBy.IID;
        Literal classLiteral = Literal.ofJavaClass(clazz);

        List<Binding> bindings = List.of(
                new Binding(ThematicRole.Theme.IID, BindingTarget.iid(item.iid())),
                new Binding(ThematicRole.Goal.IID, classLiteral));
        FrameBody body = new FrameBody(predicate, bindings);

        byte[] bytes = body.encodeBinary(Canonical.Scope.RECORD);
        ContentID cid = ContentID.of(bytes);
        Frame frame = Frame.forFrameBody(predicate, cid, true, "implementedBy");
        frame.setBody(body);
        item.frames().add(frame);
        item.frames().setLive(frame.frameKey(), body);
    }

    // ==================================================================================
    // Value handling
    // ==================================================================================

    /**
     * Flatten a field value into a list of individual values.
     * Arrays and Lists produce multiple elements; scalars produce a singleton.
     */
    private static List<Object> flattenValue(Object value) {
        if (value instanceof Object[] arr) {
            return List.of(arr);
        }
        if (value instanceof java.util.Collection<?> coll) {
            return new ArrayList<>(coll);
        }
        return List.of(value);
    }

    /**
     * Encode a field value as a BindingTarget for frame storage.
     */
    private static BindingTarget encodeValue(Object value) {
        if (value instanceof String s) {
            byte[] bytes = com.upokecenter.cbor.CBORObject.FromString(s).EncodeToBytes();
            return new Literal(Literal.TYPE_TEXT, bytes);
        }
        if (value instanceof Long l) {
            byte[] bytes = com.upokecenter.cbor.CBORObject.FromObject(l).EncodeToBytes();
            return new Literal(Literal.TYPE_INTEGER, bytes);
        }
        if (value instanceof Integer i) {
            byte[] bytes = com.upokecenter.cbor.CBORObject.FromObject(i).EncodeToBytes();
            return new Literal(Literal.TYPE_INTEGER, bytes);
        }
        if (value instanceof ItemID iid) {
            return BindingTarget.iid(iid);
        }
        if (value instanceof Value v) {
            return Literal.of(v);
        }
        if (value instanceof Canonical c) {
            byte[] bytes = c.encodeBinary(Canonical.Scope.RECORD);
            return new Literal(Literal.TYPE_CBOR, bytes);
        }
        // Fallback: stringify
        if (value != null) {
            byte[] bytes = com.upokecenter.cbor.CBORObject.FromString(value.toString()).EncodeToBytes();
            return new Literal(Literal.TYPE_TEXT, bytes);
        }
        return null;
    }
}
