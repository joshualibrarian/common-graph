package dev.everydaythings.graph.item;

import com.upokecenter.cbor.CBOREncodeOptions;
import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Hash;
import dev.everydaythings.graph.item.component.FrameEntry;
import dev.everydaythings.graph.item.component.FrameTable;
import dev.everydaythings.graph.item.component.Components;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.HandleID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.relation.Relation;
import dev.everydaythings.graph.language.Role;
import dev.everydaythings.graph.value.Value;
import dev.everydaythings.graph.value.address.AddressSpace;
import lombok.Getter;
import lombok.NonNull;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Cached schema for an Item class.
 *
 * <p>ItemSchema is the result of scanning an Item class for annotations.
 * It's computed once per class and cached by {@link ItemScanner}.
 *
 * <p><b>When used:</b>
 * <ul>
 *   <li><b>CREATION</b>: Schema drives initial population of vocabulary</li>
 *   <li><b>COMMIT</b>: Schema provides field accessors for encoding</li>
 *   <li><b>HYDRATION</b>: Schema provides field-to-handle mapping for binding</li>
 * </ul>
 *
 * <p>The schema contains:
 * <ul>
 *   <li>Frame field specs from @Item.Frame</li>
 *   <li>Item-level verbs from @Verb on methods</li>
 *   <li>Component verbs from @Verb on component classes</li>
 * </ul>
 */
@Getter
public class ItemSchema {

    /** The Item class this schema describes. */
    @NonNull private final Class<? extends Item> itemClass;

    /** All frame fields (endorsed + unendorsed). */
    private final List<FrameFieldSpec> frameFields;

    /** All @Verb methods on the item class. */
    private final List<VerbSpec> verbSpecs;

    /** Verbs per component handle (handle -> list of verbs). */
    private final Map<String, List<VerbSpec>> componentVerbs;

    public ItemSchema(
            @NonNull Class<? extends Item> itemClass,
            List<FrameFieldSpec> frameFields,
            List<VerbSpec> verbSpecs,
            Map<String, List<VerbSpec>> componentVerbs) {
        this.itemClass = itemClass;
        this.frameFields = frameFields != null ? List.copyOf(frameFields) : List.of();
        this.verbSpecs = verbSpecs != null ? List.copyOf(verbSpecs) : List.of();
        this.componentVerbs = componentVerbs != null ? Map.copyOf(componentVerbs) : Map.of();
    }

    // ==================================================================================
    // Vocabulary Population
    // ==================================================================================

    /**
     * Populate the Vocabulary from this schema.
     *
     * <p>Adds all item-level verbs and all component verbs discovered
     * during scanning.
     *
     * @param vocab The vocabulary to populate
     * @param owner The owning item (for setting owner on verb entries)
     */
    public void populateVocabulary(Vocabulary vocab, Item owner) {
        // Add item-level verbs
        for (VerbSpec spec : verbSpecs) {
            vocab.add(VerbEntry.itemVerb(spec, owner));
        }

        // Add component verbs
        for (Map.Entry<String, List<VerbSpec>> entry : componentVerbs.entrySet()) {
            String componentHandle = entry.getKey();
            Object component = owner.component(componentHandle);
            if (component != null) {
                for (VerbSpec spec : entry.getValue()) {
                    vocab.add(VerbEntry.componentVerb(spec, componentHandle, component));
                }
            }
        }
    }

    // ==================================================================================
    // Endorsed Frame Fields (Components)
    // ==================================================================================

    /** Get all endorsed frame fields. */
    public List<FrameFieldSpec> endorsedFrameFields() {
        return frameFields.stream().filter(FrameFieldSpec::endorsed).collect(Collectors.toList());
    }

    /** Get all unendorsed frame fields. */
    public List<FrameFieldSpec> unendorsedFrameFields() {
        return frameFields.stream().filter(f -> !f.endorsed()).collect(Collectors.toList());
    }

    /** Check if this schema has any endorsed (component) fields. */
    public boolean hasComponentFields() {
        return frameFields.stream().anyMatch(FrameFieldSpec::endorsed);
    }

    /** Check if this schema has any unendorsed (relation) fields. */
    public boolean hasRelationFields() {
        return frameFields.stream().anyMatch(f -> !f.endorsed());
    }

    /** Check if this schema has any frame fields. */
    public boolean hasFrameFields() {
        return !frameFields.isEmpty();
    }

    // ==================================================================================
    // Field Binding (Hydration)
    // ==================================================================================

    /**
     * Bind endorsed frame fields from loaded data during hydration.
     *
     * <p>For each endorsed frame field, looks up the live instance in the
     * FrameTable and injects it into the field.
     *
     * @param item  The item to bind fields on
     * @param table The content table with live instances
     */
    public void bindFieldsFromTable(Item item, FrameTable table) {
        for (FrameFieldSpec spec : frameFields) {
            if (!spec.endorsed()) continue;
            HandleID handle = spec.handle();
            table.getLive(handle, spec.fieldType())
                    .ifPresent(value -> spec.setValue(item, value));
        }
    }

    /**
     * Get a frame field spec by handle string.
     */
    public FrameFieldSpec getFrameField(String handle) {
        HandleID hid = HandleID.of(handle);
        return frameFields.stream()
                .filter(spec -> spec.handle().equals(hid))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get a frame field spec by handle ID.
     */
    public FrameFieldSpec getFrameField(HandleID handle) {
        return frameFields.stream()
                .filter(spec -> spec.handle().equals(handle))
                .findFirst()
                .orElse(null);
    }

    // ==================================================================================
    // Relation Population (into FrameTable)
    // ==================================================================================

    /**
     * Populate the FrameTable with relation entries from unendorsed frame fields.
     *
     * <p>This is the display-time path (not commit-time). Relations are built and
     * stored as live instances in the FrameTable with computed CIDs.
     *
     * @param table The frame table to populate
     * @param item  The item to read field values from
     */
    @SuppressWarnings("deprecation")
    public void populateRelationEntries(FrameTable table, Item item) {
        table.removeRelationEntries();

        for (FrameFieldSpec spec : frameFields) {
            if (spec.endorsed()) continue;
            Object value = spec.getValue(item);
            if (value == null) continue;

            if (spec.isIterable()) {
                for (Object element : (Iterable<?>) value) {
                    Relation relation = buildRelation(item, spec, element);
                    if (relation != null) {
                        addRelationEntry(table, relation);
                    }
                }
            } else {
                Relation relation = buildRelation(item, spec, value);
                if (relation != null) {
                    addRelationEntry(table, relation);
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void addRelationEntry(FrameTable table, Relation relation) {
        byte[] bytes = relation.encodeBinary(Canonical.Scope.RECORD);
        ContentID cid = ContentID.of(bytes);
        FrameEntry entry = FrameEntry.forRelation(relation.predicate(), cid, true);
        table.add(entry);
        table.setLive(entry.handle(), relation);
    }

    @SuppressWarnings("deprecation")
    private Relation buildRelation(Item item, FrameFieldSpec spec, Object target) {
        if (target == null) return null;

        Relation.Target targetValue;
        if (target instanceof ItemID itemId) {
            targetValue = Relation.iid(itemId);
        } else if (target instanceof Item targetItem) {
            targetValue = Relation.iid(targetItem.iid());
        } else if (target instanceof AddressSpace.ParsedAddress addr) {
            targetValue = Literal.ofText(addr.canonical());
        } else if (target instanceof Value value) {
            targetValue = Literal.of(value);
        } else if (target instanceof String str) {
            targetValue = Literal.ofText(str);
        } else if (target instanceof Number num) {
            if (num instanceof Float || num instanceof Double) {
                return null;
            }
            targetValue = Literal.ofInteger(num.longValue());
        } else {
            targetValue = Literal.ofText(target.toString());
        }

        return Relation.builder()
                .predicate(spec.predicate())
                .bind(Role.THEME.iid(), Relation.iid(item.iid()))
                .bind(Role.TARGET.iid(), targetValue)
                .build();
    }

    // ==================================================================================
    // Field Binding (Commit-time)
    // ==================================================================================

    /**
     * Bind all endorsed frame fields for commit — encode values and add to content table.
     *
     * @param item         The item to read field values from
     * @param contentTable The content table to add entries to
     * @param storePayload Function to store payload bytes
     */
    public void bindComponentFieldsForCommit(Item item, FrameTable contentTable, Consumer<byte[]> storePayload) {
        for (FrameFieldSpec spec : frameFields) {
            if (!spec.endorsed()) continue;
            Object value = spec.getValue(item);
            if (value == null) continue;

            FrameEntry entry = encodeFrameField(spec, value, storePayload);
            if (entry != null) {
                contentTable.add(entry);
                contentTable.setLive(spec.handle(), spec.handleKey(), value);
            }
        }
    }

    private FrameEntry encodeFrameField(FrameFieldSpec spec, Object value, Consumer<byte[]> storePayload) {
        HandleID handle = spec.handle();
        String alias = spec.handleKey();

        // For types with @Type annotation
        if (value.getClass().isAnnotationPresent(Type.class)) {
            ItemID typeId = Components.typeId(value.getClass());
            boolean isLocalOnly = spec.localOnly();

            if (isLocalOnly) {
                return FrameEntry.builder()
                        .handle(handle).alias(alias)
                        .type(typeId).identity(spec.identity()).build();
            }

            if (value instanceof Canonical canonical) {
                byte[] bytes = canonical.encodeBinary(Canonical.Scope.RECORD);
                ContentID cid = new ContentID(Hash.DEFAULT.digest(bytes), Hash.DEFAULT);
                if (storePayload != null) storePayload.accept(bytes);
                return FrameEntry.builder()
                        .handle(handle).alias(alias)
                        .type(typeId).identity(spec.identity())
                        .payload(FrameEntry.EntryPayload.builder().snapshotCid(cid).build())
                        .build();
            }

            byte[] bytes = Components.encode(value);
            ContentID cid = new ContentID(Hash.DEFAULT.digest(bytes), Hash.DEFAULT);
            if (storePayload != null) storePayload.accept(bytes);
            return FrameEntry.builder()
                    .handle(handle).alias(alias)
                    .type(typeId).identity(spec.identity())
                    .payload(FrameEntry.EntryPayload.builder().snapshotCid(cid).build())
                    .build();
        }

        // For Canonical types without @Type
        if (value instanceof Canonical canonical) {
            byte[] bytes = canonical.encodeBinary(Canonical.Scope.RECORD);
            ContentID cid = new ContentID(Hash.DEFAULT.digest(bytes), Hash.DEFAULT);
            if (storePayload != null) storePayload.accept(bytes);
            ItemID typeId = deriveTypeId(spec.fieldType());
            return FrameEntry.builder()
                    .handle(handle).alias(alias)
                    .type(typeId).identity(spec.identity())
                    .payload(FrameEntry.EntryPayload.builder().snapshotCid(cid).build())
                    .build();
        }

        // For simple serializable types
        if (isSimpleSerializableType(value)) {
            byte[] bytes = encodeSimpleValue(value);
            ContentID cid = new ContentID(Hash.DEFAULT.digest(bytes), Hash.DEFAULT);
            if (storePayload != null) storePayload.accept(bytes);
            ItemID typeId = deriveTypeId(spec.fieldType());
            return FrameEntry.builder()
                    .handle(handle).alias(alias)
                    .type(typeId).identity(spec.identity())
                    .payload(FrameEntry.EntryPayload.builder().snapshotCid(cid).build())
                    .build();
        }

        throw new IllegalStateException("Cannot encode field with handle '" + handle +
                "': value is not a supported type (@Type component, Canonical, or simple serializable)");
    }

    /**
     * Bind unendorsed frame fields for commit — create relations, store them, add as entries.
     *
     * @param item           The item to read field values from
     * @param componentTable The frame table to add relation entries to
     * @param storePayload   Function to store payload bytes and return CID
     * @param storeRelation  Function to store relations (indexing)
     */
    @SuppressWarnings("deprecation")
    public void bindRelationFieldsForCommit(Item item, FrameTable componentTable,
                                            java.util.function.Function<byte[], ContentID> storePayload,
                                            Consumer<Relation> storeRelation) {
        componentTable.removeRelationEntries();

        for (FrameFieldSpec spec : frameFields) {
            if (spec.endorsed()) continue;
            Object value = spec.getValue(item);
            if (value == null) continue;

            if (spec.isIterable()) {
                for (Object element : (Iterable<?>) value) {
                    bindSingleRelation(item, spec, element, componentTable, storePayload, storeRelation);
                }
            } else {
                bindSingleRelation(item, spec, value, componentTable, storePayload, storeRelation);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void bindSingleRelation(Item item, FrameFieldSpec spec, Object object,
                                    FrameTable componentTable,
                                    java.util.function.Function<byte[], ContentID> storePayload,
                                    Consumer<Relation> storeRelation) {
        Relation relation = buildRelation(item, spec, object);
        if (relation == null) return;

        if (storeRelation != null) {
            storeRelation.accept(relation);
        }

        byte[] bytes = relation.encodeBinary(Canonical.Scope.RECORD);
        ContentID cid = (storePayload != null) ? storePayload.apply(bytes) : ContentID.of(bytes);

        FrameEntry entry = FrameEntry.forRelation(relation.predicate(), cid, true);
        componentTable.add(entry);
        componentTable.setLive(entry.handle(), relation);
    }

    // ==================================================================================
    // Encoding Helpers
    // ==================================================================================

    /** Derive a type ID from a Java class. */
    public static ItemID deriveTypeId(Class<?> type) {
        return ItemID.fromString("cg.type:" + type.getSimpleName().toLowerCase());
    }

    /** Check if a value is a simple serializable type. */
    public static boolean isSimpleSerializableType(Object value) {
        return value instanceof CharSequence
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Enum<?>
                || value instanceof Map<?, ?>
                || value instanceof Collection<?>
                || value.getClass().isArray();
    }

    /** Encode a simple value to CBOR bytes. */
    public static byte[] encodeSimpleValue(Object value) {
        CBORObject cbor = Canonical.encodeValue(value, Canonical.Scope.RECORD);
        return cbor.EncodeToBytes(CBOREncodeOptions.DefaultCtap2Canonical);
    }

    /** Decode a simple value from CBOR bytes. */
    public static Object decodeSimpleValue(Field field, byte[] bytes) {
        try {
            CBORObject cbor = CBORObject.DecodeFromBytes(bytes);
            return Canonical.decodeIntoType(field.getGenericType(), field.getType(), cbor, Canonical.Scope.RECORD);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "ItemSchema[" + itemClass.getSimpleName() +
                ", frames=" + frameFields.size() +
                ", verbs=" + verbSpecs.size() + "]";
    }
}
