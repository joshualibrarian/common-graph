package dev.everydaythings.graph.item;

import com.upokecenter.cbor.CBOREncodeOptions;
import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Hash;
import dev.everydaythings.graph.item.component.ComponentEntry;
import dev.everydaythings.graph.item.component.ComponentFieldSpec;
import dev.everydaythings.graph.item.component.ComponentTable;
import dev.everydaythings.graph.item.component.Components;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.HandleID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.relation.Relation;
import dev.everydaythings.graph.value.Value;
import dev.everydaythings.graph.value.address.AddressSpace;
import lombok.Getter;
import lombok.NonNull;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

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
 *   <li>Component field specs from @Item.ContentField</li>
 *   <li>Relation field specs from @Item.RelationField</li>
 *   <li>Item-level verbs from @Verb on methods</li>
 *   <li>Component verbs from @Verb on component classes</li>
 * </ul>
 */
@Getter
public class ItemSchema {

    /** The Item class this schema describes. */
    @NonNull private final Class<? extends Item> itemClass;

    /** All @Item.ContentField annotations. */
    private final List<ComponentFieldSpec> componentFields;

    /** All @Item.RelationField annotations. */
    private final List<RelationFieldSpec> relationFields;

    /** All @Verb methods on the item class. */
    private final List<VerbSpec> verbSpecs;

    /** Verbs per component handle (handle -> list of verbs). */
    private final Map<String, List<VerbSpec>> componentVerbs;

    public ItemSchema(
            @NonNull Class<? extends Item> itemClass,
            List<ComponentFieldSpec> componentFields,
            List<RelationFieldSpec> relationFields,
            List<VerbSpec> verbSpecs,
            Map<String, List<VerbSpec>> componentVerbs) {
        this.itemClass = itemClass;
        this.componentFields = componentFields != null ? List.copyOf(componentFields) : List.of();
        this.relationFields = relationFields != null ? List.copyOf(relationFields) : List.of();
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
    // Relation Population (into ComponentTable)
    // ==================================================================================

    /**
     * Populate the ComponentTable with relation entries from @RelationField values.
     *
     * <p>This is the display-time path (not commit-time). Relations are built and
     * stored as live instances in the ComponentTable with computed CIDs.
     * The DB storage (RELATION column) only happens during commit.
     *
     * @param table The component table to populate
     * @param item  The item to read field values from
     */
    public void populateRelationEntries(ComponentTable table, Item item) {
        // Clear existing relation entries before re-populating
        table.removeRelationEntries();

        for (RelationFieldSpec spec : relationFields) {
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

    /**
     * Add a single relation as a ComponentEntry with its live instance.
     */
    private void addRelationEntry(ComponentTable table, Relation relation) {
        byte[] bytes = relation.encodeBinary(Canonical.Scope.RECORD);
        ContentID cid = ContentID.of(bytes);
        ComponentEntry entry = ComponentEntry.forRelation(relation.predicate(), cid, true);
        table.add(entry);
        table.setLive(entry.handle(), relation);
    }

    /**
     * Build a Relation from a spec and object value.
     */
    private Relation buildRelation(Item item, RelationFieldSpec spec, Object object) {
        if (object == null) return null;

        Relation.RelationBuilder builder = Relation.builder()
                .subject(item.iid())
                .predicate(spec.predicate());

        // Set object based on type
        if (object instanceof ItemID itemId) {
            builder.object(Relation.iid(itemId));
        } else if (object instanceof Item targetItem) {
            builder.object(Relation.iid(targetItem.iid()));
        } else if (object instanceof AddressSpace.ParsedAddress addr) {
            builder.object(Literal.ofText(addr.canonical()));
        } else if (object instanceof Value value) {
            builder.object(Literal.of(value));
        } else if (object instanceof String str) {
            builder.object(Literal.ofText(str));
        } else if (object instanceof Number num) {
            // CG-CBOR forbids floats - only accept integer types
            if (num instanceof Float || num instanceof Double) {
                return null;
            }
            builder.object(Literal.ofInteger(num.longValue()));
        } else {
            builder.object(Literal.ofText(object.toString()));
        }

        return builder.build();
    }

    // ==================================================================================
    // Field Binding (Hydration)
    // ==================================================================================

    /**
     * Bind fields from loaded data during hydration.
     *
     * <p>For each component field spec, looks up the live instance in the
     * ComponentTable and injects it into the field.
     *
     * @param item  The item to bind fields on
     * @param table The content table with live instances
     */
    public void bindFieldsFromTable(Item item, ComponentTable table) {
        for (ComponentFieldSpec spec : componentFields) {
            HandleID handle = spec.handle();
            table.getLive(handle, spec.fieldType())
                    .ifPresent(value -> spec.setValue(item, value));
        }
    }

    /**
     * Get a component field spec by handle.
     *
     * @param handle The handle to look up
     * @return The spec, or null if not found
     */
    public ComponentFieldSpec getComponentField(String handle) {
        HandleID hid = HandleID.of(handle);
        return componentFields.stream()
                .filter(spec -> spec.handle().equals(hid))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get a component field spec by handle ID.
     *
     * @param handle The handle to look up
     * @return The spec, or null if not found
     */
    public ComponentFieldSpec getComponentField(HandleID handle) {
        return componentFields.stream()
                .filter(spec -> spec.handle().equals(handle))
                .findFirst()
                .orElse(null);
    }

    // ==================================================================================
    // Field Binding (Commit-time)
    // ==================================================================================

    /**
     * Bind all component fields for commit - encode values and add to content table.
     *
     * <p>For each @ContentField, reads the field value and:
     * <ul>
     *   <li>Local-only components: adds metadata entry only</li>
     *   <li>@Type-annotated components: encodes via Components.encode(), stores payload</li>
     *   <li>Canonical types: encodes via encodeBinary(), stores payload</li>
     *   <li>Simple types: encodes via CBOR, stores payload</li>
     * </ul>
     *
     * @param item         The item to read field values from
     * @param contentTable The content table to add entries to
     * @param storePayload Function to store payload bytes (e.g., librarian::storePayload)
     */
    public void bindComponentFieldsForCommit(Item item, ComponentTable contentTable, Consumer<byte[]> storePayload) {
        for (ComponentFieldSpec spec : componentFields) {
            Object value = spec.getValue(item);
            if (value == null) continue;

            ComponentEntry entry = encodeComponentField(spec, value, storePayload);
            if (entry != null) {
                contentTable.add(entry);
                // Preserve the live instance on the new entry so subsequent
                // lookups return the same object (not a lazy-decoded copy
                // that loses transient state like Dag's materialized maps).
                String alias = spec.alias().isEmpty() ? spec.handleKey() : spec.alias();
                contentTable.setLive(spec.handle(), alias, value);
            }
        }
    }

    /**
     * Encode a single component field value and return the ComponentEntry.
     */
    @SuppressWarnings("unchecked")
    private ComponentEntry encodeComponentField(ComponentFieldSpec spec, Object value, Consumer<byte[]> storePayload) {
        HandleID handle = spec.handle();
        String alias = spec.alias().isEmpty() ? spec.handleKey() : spec.alias();

        // For types with @Type annotation
        if (value.getClass().isAnnotationPresent(Type.class)) {
            ItemID typeId = Components.typeId(value.getClass());
            boolean isLocalOnly = spec.localOnly();

            if (isLocalOnly) {
                // Local-only: just metadata, no content storage
                return ComponentEntry.builder()
                        .handle(handle).alias(alias)
                        .type(typeId).identity(spec.identity()).build();
            }

            // Canonical types use encodeBinary (preferred over Components.encode)
            if (value instanceof Canonical canonical) {
                byte[] bytes = canonical.encodeBinary(Canonical.Scope.RECORD);
                ContentID cid = new ContentID(Hash.DEFAULT.digest(bytes), Hash.DEFAULT);
                if (storePayload != null) {
                    storePayload.accept(bytes);
                }
                return ComponentEntry.builder()
                        .handle(handle).alias(alias)
                        .type(typeId).identity(spec.identity())
                        .payload(ComponentEntry.EntryPayload.builder().snapshotCid(cid).build())
                        .build();
            }

            // Non-Canonical @Type components: encode via Components.encode()
            byte[] bytes = Components.encode(value);
            ContentID cid = new ContentID(Hash.DEFAULT.digest(bytes), Hash.DEFAULT);
            if (storePayload != null) {
                storePayload.accept(bytes);
            }
            return ComponentEntry.builder()
                    .handle(handle).alias(alias)
                    .type(typeId).identity(spec.identity())
                    .payload(ComponentEntry.EntryPayload.builder().snapshotCid(cid).build())
                    .build();
        }

        // For Canonical types without @Type
        if (value instanceof Canonical canonical) {
            byte[] bytes = canonical.encodeBinary(Canonical.Scope.RECORD);
            ContentID cid = new ContentID(Hash.DEFAULT.digest(bytes), Hash.DEFAULT);
            if (storePayload != null) {
                storePayload.accept(bytes);
            }
            ItemID typeId = deriveTypeId(spec.fieldType());
            return ComponentEntry.builder()
                    .handle(handle).alias(alias)
                    .type(typeId).identity(spec.identity())
                    .payload(ComponentEntry.EntryPayload.builder().snapshotCid(cid).build())
                    .build();
        }

        // For simple serializable types
        if (isSimpleSerializableType(value)) {
            byte[] bytes = encodeSimpleValue(value);
            ContentID cid = new ContentID(Hash.DEFAULT.digest(bytes), Hash.DEFAULT);
            if (storePayload != null) {
                storePayload.accept(bytes);
            }
            ItemID typeId = deriveTypeId(spec.fieldType());
            return ComponentEntry.builder()
                    .handle(handle).alias(alias)
                    .type(typeId).identity(spec.identity())
                    .payload(ComponentEntry.EntryPayload.builder().snapshotCid(cid).build())
                    .build();
        }

        throw new IllegalStateException("Cannot encode field with handle '" + handle +
                "': value is not a supported type (@Type component, Canonical, or simple serializable)");
    }

    /**
     * Bind relation fields for commit - create relations, store them, and add as ComponentEntries.
     *
     * <p>Each @RelationField value is built into a Relation, stored in the DB via
     * storeRelation (ItemStore.RELATION column + index fan-outs), and then added
     * as a ComponentEntry in the ComponentTable.
     *
     * @param item           The item to read field values from
     * @param componentTable The component table to add relation entries to
     * @param storePayload   Function to store payload bytes and return CID
     * @param storeRelation  Function to store canonical relations (DB RELATION column + indexing)
     */
    public void bindRelationFieldsForCommit(Item item, ComponentTable componentTable,
                                            java.util.function.Function<byte[], ContentID> storePayload,
                                            Consumer<Relation> storeRelation) {
        // Clear any existing relation entries before re-binding
        componentTable.removeRelationEntries();

        for (RelationFieldSpec spec : relationFields) {
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

    /**
     * Bind a single relation value — store in DB and add as ComponentEntry.
     */
    private void bindSingleRelation(Item item, RelationFieldSpec spec, Object object,
                                    ComponentTable componentTable,
                                    java.util.function.Function<byte[], ContentID> storePayload,
                                    Consumer<Relation> storeRelation) {
        Relation relation = buildRelation(item, spec, object);
        if (relation == null) return;

        // Store canonical relations in DB (RELATION column + index fan-outs)
        if (spec.canonical() && storeRelation != null) {
            storeRelation.accept(relation);
        }

        // Encode relation bytes and store as content payload
        byte[] bytes = relation.encodeBinary(Canonical.Scope.RECORD);
        ContentID cid = (storePayload != null) ? storePayload.apply(bytes) : ContentID.of(bytes);

        // Add as ComponentEntry in the component table
        // Relations are identity=true by default (they define the item's endorsed content)
        ComponentEntry entry = ComponentEntry.forRelation(relation.predicate(), cid, true);
        componentTable.add(entry);

        // Store the live Relation instance for runtime access
        componentTable.setLive(entry.handle(), relation);
    }

    // ==================================================================================
    // Encoding Helpers
    // ==================================================================================

    /**
     * Derive a type ID from a Java class.
     */
    public static ItemID deriveTypeId(Class<?> type) {
        return ItemID.fromString("cg.type:" + type.getSimpleName().toLowerCase());
    }

    /**
     * Check if a value is a simple serializable type.
     *
     * <p>Simple types include: String, Number, Boolean, Enum, Map, Collection, arrays.
     * These can be directly encoded to CBOR without implementing Canonical.
     */
    public static boolean isSimpleSerializableType(Object value) {
        return value instanceof CharSequence
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Enum<?>
                || value instanceof Map<?, ?>
                || value instanceof Collection<?>
                || value.getClass().isArray();
    }

    /**
     * Encode a simple value to CBOR bytes.
     *
     * <p>Uses the Canonical encoding infrastructure to produce deterministic CBOR.
     */
    public static byte[] encodeSimpleValue(Object value) {
        CBORObject cbor = Canonical.encodeValue(value, Canonical.Scope.RECORD);
        return cbor.EncodeToBytes(CBOREncodeOptions.DefaultCtap2Canonical);
    }

    /**
     * Decode a simple value from CBOR bytes.
     *
     * <p>Uses the field's declared type to guide decoding.
     *
     * @param field The field to decode into (for type information)
     * @param bytes The CBOR bytes to decode
     * @return The decoded value, or null if decoding fails
     */
    public static Object decodeSimpleValue(Field field, byte[] bytes) {
        try {
            CBORObject cbor = CBORObject.DecodeFromBytes(bytes);
            return Canonical.decodeIntoType(field.getGenericType(), field.getType(), cbor, Canonical.Scope.RECORD);
        } catch (Exception e) {
            // If CBOR decode fails, this wasn't a simple type - return null
            return null;
        }
    }

    // ==================================================================================
    // Statistics
    // ==================================================================================

    /**
     * Check if this schema has any component fields.
     */
    public boolean hasComponentFields() {
        return !componentFields.isEmpty();
    }

    /**
     * Check if this schema has any relation fields.
     */
    public boolean hasRelationFields() {
        return !relationFields.isEmpty();
    }

    @Override
    public String toString() {
        return "ItemSchema[" + itemClass.getSimpleName() +
                ", components=" + componentFields.size() +
                ", relations=" + relationFields.size() +
                ", verbs=" + verbSpecs.size() + "]";
    }
}
