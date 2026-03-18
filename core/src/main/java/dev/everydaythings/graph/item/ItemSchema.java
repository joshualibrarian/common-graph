package dev.everydaythings.graph.item;

import com.upokecenter.cbor.CBOREncodeOptions;
import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Hash;
import dev.everydaythings.graph.dispatch.VerbEntry;
import dev.everydaythings.graph.dispatch.VerbSpec;
import dev.everydaythings.graph.dispatch.Vocabulary;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.FrameConfig;
import dev.everydaythings.graph.frame.EndorsementsTable;
import dev.everydaythings.graph.frame.Frame;
import dev.everydaythings.graph.frame.FrameBody;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.language.ThematicRole;
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
     * EndorsementsTable and injects it into the field.
     *
     * @param item  The item to bind fields on
     * @param table The content table with live instances
     */
    public void bindFieldsFromTable(Item item, EndorsementsTable table) {
        for (FrameFieldSpec spec : frameFields) {
            if (!spec.endorsed()) continue;
            FrameKey key = spec.frameKey();
            table.getLive(key, spec.fieldType())
                    .ifPresent(value -> spec.setValue(item, value));
        }
    }


    /**
     * Get a frame field spec by FrameKey.
     */
    public FrameFieldSpec getFrameField(FrameKey key) {
        return frameFields.stream()
                .filter(spec -> spec.frameKey().equals(key))
                .findFirst()
                .orElse(null);
    }

    // ==================================================================================
    // Relation Population (into EndorsementsTable)
    // ==================================================================================

    /**
     * Populate the EndorsementsTable with bare frames from unendorsed frame fields.
     *
     * <p>This is the display-time path (not commit-time). Unendorsed frames are
     * built and stored as live instances in the EndorsementsTable with computed CIDs.
     *
     * @param table The endorsements table to populate
     * @param item  The item to read field values from
     */
    public void populateUnendorsedFrames(EndorsementsTable table, Item item) {
        table.removeBareFrames();

        for (FrameFieldSpec spec : frameFields) {
            if (spec.endorsed()) continue;
            Object value = spec.getValue(item);
            if (value == null) continue;

            if (spec.isIterable()) {
                for (Object element : (Iterable<?>) value) {
                    FrameBody body = buildFrameBody(item, spec, element);
                    if (body != null) {
                        addBareFrame(table, body, spec.predicateDisplayName());
                    }
                }
            } else {
                FrameBody body = buildFrameBody(item, spec, value);
                if (body != null) {
                    addBareFrame(table, body, spec.predicateDisplayName());
                }
            }
        }
    }

    private void addBareFrame(EndorsementsTable table, FrameBody body, String displayName) {
        byte[] bytes = body.encodeBinary(Canonical.Scope.RECORD);
        ContentID cid = ContentID.of(bytes);
        Frame frame = Frame.forFrameBody(body.predicate(), cid, true, displayName);
        frame.setBody(body);
        table.add(frame);
        table.setLive(frame.frameKey(), body);
    }

    private FrameBody buildFrameBody(Item item, FrameFieldSpec spec, Object target) {
        if (target == null) return null;

        BindingTarget targetValue;
        if (target instanceof ItemID itemId) {
            targetValue = BindingTarget.iid(itemId);
        } else if (target instanceof Item targetItem) {
            targetValue = BindingTarget.iid(targetItem.iid());
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

        return FrameBody.of(
                spec.predicate(),
                item.iid(),
                Map.of(ThematicRole.Goal.IID, targetValue));
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
    public void bindComponentFieldsForCommit(Item item, EndorsementsTable contentTable, Consumer<byte[]> storePayload) {
        bindComponentFieldsForCommit(item, contentTable, storePayload, null, null, id -> java.util.List.of());
    }

    /**
     * Bind all endorsed frame fields for commit with optional encryption.
     *
     * @param item              The item to read field values from
     * @param contentTable      The content table to add entries to
     * @param storePayload      Function to store payload bytes
     * @param encryptionContext  Encryption policy (null or {@code EncryptionContext.NONE} for no encryption)
     * @param keyResolver       Resolves a principal ItemID to their current encryption public keys
     */
    public void bindComponentFieldsForCommit(Item item, EndorsementsTable contentTable,
                                             Consumer<byte[]> storePayload,
                                             dev.everydaythings.graph.crypt.EncryptionContext encryptionContext,
                                             java.util.function.Function<ItemID, java.util.List<dev.everydaythings.graph.crypt.EncryptionPublicKey>> keyResolver) {
        bindComponentFieldsForCommit(item, contentTable, storePayload, encryptionContext, null, keyResolver);
    }

    /**
     * Bind all endorsed frame fields for commit with optional encryption and frame body storage.
     *
     * @param item              The item to read field values from
     * @param contentTable      The content table to add entries to
     * @param storePayload      Function to store payload bytes
     * @param encryptionContext  Encryption policy (null or {@code EncryptionContext.NONE} for no encryption)
     * @param storeFrameBody    Function to store FrameBody objects (for indexing); may be null
     * @param keyResolver       Resolves a principal ItemID to their current encryption public keys
     */
    public void bindComponentFieldsForCommit(Item item, EndorsementsTable contentTable,
                                             Consumer<byte[]> storePayload,
                                             dev.everydaythings.graph.crypt.EncryptionContext encryptionContext,
                                             Consumer<FrameBody> storeFrameBody,
                                             java.util.function.Function<ItemID, java.util.List<dev.everydaythings.graph.crypt.EncryptionPublicKey>> keyResolver) {
        for (FrameFieldSpec spec : frameFields) {
            if (!spec.endorsed()) continue;
            Object value = spec.getValue(item);
            if (value == null) continue;

            // TODO: Existing config (FrameConfig) migrating to CONFIG binding
            FrameConfig existingConfig = null;

            Frame frame = encodeFrameField(item, spec, value, storePayload, encryptionContext,
                    existingConfig, storeFrameBody, keyResolver);
            if (frame != null) {
                contentTable.add(frame);
                contentTable.setLive(spec.frameKey(), value);
            }
        }
    }

    private Frame encodeFrameField(Item item, FrameFieldSpec spec, Object value, Consumer<byte[]> storePayload,
                                    dev.everydaythings.graph.crypt.EncryptionContext encryptionContext,
                                    FrameConfig existingConfig,
                                    Consumer<FrameBody> storeFrameBody,
                                    java.util.function.Function<ItemID, java.util.List<dev.everydaythings.graph.crypt.EncryptionPublicKey>> keyResolver) {
        FrameKey key = spec.frameKey();
        String alias = spec.canonicalKeyString();

        // Determine effective encryption context: explicit parameter wins,
        // otherwise derive from existing frame's EncryptionPolicy
        dev.everydaythings.graph.crypt.EncryptionContext effectiveContext =
                resolveEncryptionContext(encryptionContext, existingConfig, keyResolver);

        // For types with @Type annotation
        if (value.getClass().isAnnotationPresent(Implements.class)) {
            ItemID typeId = Item.idOf(value.getClass());
            boolean isLocalOnly = spec.localOnly();

            if (isLocalOnly) {
                Frame frame = Frame.snapshot(key, typeId, null, spec.identity());
                // alias removed — display names resolved via TokenDictionary
                buildAndStoreComponentBody(item, spec, typeId, alias, null, null, existingConfig, frame, storeFrameBody);
                return frame;
            }

            byte[] bytes;
            if (value instanceof Canonical canonical) {
                bytes = canonical.encodeBinary(Canonical.Scope.RECORD);
            } else {
                throw new IllegalStateException(
                        "Non-Canonical @Type class " + value.getClass().getName()
                        + " cannot be encoded — implement Canonical");
            }
            return storeAndBuildFrame(item, key, alias, typeId, spec.identity(),
                    bytes, storePayload, effectiveContext, existingConfig, storeFrameBody);
        }

        // For Canonical types without @Type
        if (value instanceof Canonical canonical) {
            byte[] bytes = canonical.encodeBinary(Canonical.Scope.RECORD);
            ItemID typeId = deriveTypeId(spec.fieldType());
            return storeAndBuildFrame(item, key, alias, typeId, spec.identity(),
                    bytes, storePayload, effectiveContext, existingConfig, storeFrameBody);
        }

        // For simple serializable types
        if (isSimpleSerializableType(value)) {
            byte[] bytes = encodeSimpleValue(value);
            ItemID typeId = deriveTypeId(spec.fieldType());
            return storeAndBuildFrame(item, key, alias, typeId, spec.identity(),
                    bytes, storePayload, effectiveContext, existingConfig, storeFrameBody);
        }

        throw new IllegalStateException("Cannot encode frame with key " + key +
                ": value is not a supported type (@Type component, Canonical, or simple serializable)");
    }

    /**
     * Resolve the effective EncryptionContext for a frame.
     *
     * <p>If an explicit context was passed to commit(), it takes precedence.
     * Otherwise, if the existing frame entry has an EncryptionPolicy on its
     * config, we derive an EncryptionContext from it by resolving ItemID
     * recipients to EncryptionPublicKeys via the key resolver.
     *
     * <p>For {@code encryptToReaders} policies, the access policy's READ rules
     * are inspected to find subjects, which are then resolved to keys.
     */
    private dev.everydaythings.graph.crypt.EncryptionContext resolveEncryptionContext(
            dev.everydaythings.graph.crypt.EncryptionContext explicit,
            FrameConfig existingConfig,
            java.util.function.Function<ItemID, java.util.List<dev.everydaythings.graph.crypt.EncryptionPublicKey>> keyResolver) {
        // Explicit context always wins
        if (explicit != null && explicit != dev.everydaythings.graph.crypt.EncryptionContext.NONE) {
            return explicit;
        }
        // No per-frame policy — no encryption
        if (existingConfig == null || existingConfig.policy() == null) {
            return explicit; // null or NONE
        }
        var encryption = existingConfig.policy().encryption();
        if (encryption == null || !encryption.isEnabled()) {
            return explicit;
        }

        // Resolve recipients from the EncryptionPolicy
        java.util.List<dev.everydaythings.graph.crypt.EncryptionPublicKey> resolvedKeys;

        if (encryption.encryptToReaders()) {
            // Derive recipients from the access policy's READ rules
            resolvedKeys = resolveReadersToKeys(existingConfig.policy(), keyResolver);
        } else if (encryption.hasExplicitRecipients()) {
            // Resolve explicit ItemID recipients to keys
            resolvedKeys = encryption.recipients().stream()
                    .flatMap(id -> keyResolver.apply(id).stream())
                    .collect(java.util.stream.Collectors.toList());
        } else {
            // Enabled but no recipients specified — nothing to encrypt to
            return explicit;
        }

        if (resolvedKeys.isEmpty()) {
            return explicit; // Can't encrypt without recipients
        }

        return dev.everydaythings.graph.crypt.EncryptionContext.fromEncryptionPolicy(encryption, resolvedKeys);
    }

    /**
     * Resolve the access policy's READ subjects to encryption keys.
     *
     * <p>Inspects all ALLOW READ rules in the access policy, extracts subject
     * identifiers, and resolves those that are ItemIDs to encryption public keys.
     * Handles special subjects: "owner" is skipped (would need item context),
     * "any" is skipped (can't encrypt to everyone), others are tried as ItemIDs.
     */
    private java.util.List<dev.everydaythings.graph.crypt.EncryptionPublicKey> resolveReadersToKeys(
            dev.everydaythings.graph.policy.PolicySet policy,
            java.util.function.Function<ItemID, java.util.List<dev.everydaythings.graph.crypt.EncryptionPublicKey>> keyResolver) {
        if (policy.access() == null || !policy.access().hasRules()) {
            return java.util.List.of();
        }

        java.util.List<dev.everydaythings.graph.crypt.EncryptionPublicKey> keys = new java.util.ArrayList<>();
        for (var rule : policy.access().rules()) {
            if (rule.effect() != dev.everydaythings.graph.policy.PolicySet.AccessPolicy.Effect.ALLOW) continue;
            if (rule.action() != dev.everydaythings.graph.policy.PolicySet.AccessPolicy.Action.READ) continue;
            if (rule.subject() == null || rule.subject().who() == null) continue;

            String who = rule.subject().who();
            // Skip non-resolvable subjects
            if ("any".equals(who) || "participants".equals(who) || "hosts".equals(who)) continue;

            // Try to parse as an ItemID (iid:... format) and resolve
            try {
                ItemID subjectId = ItemID.parse(who);
                keys.addAll(keyResolver.apply(subjectId));
            } catch (Exception e) {
                // Not a valid ItemID — skip (could be "owner" or other symbolic ref)
            }
        }
        return keys;
    }

    /**
     * Store encoded bytes (optionally encrypting) and build a {@link Frame}.
     *
     * <p>Also builds a {@link FrameBody} for the component frame, computes its
     * body hash, and stores it via {@code storeFrameBody} if provided.
     *
     * <p>The plaintext CID ({@code snapshotCid}) is always computed from the plaintext bytes
     * and used for VID stability. If encryption is enabled for this frame, the bytes are
     * encrypted into a Tag 10 envelope, stored under the envelope's CID, and the
     * {@code encryptedCid} is set on the entry.
     *
     * <p>If {@code existingConfig} is non-null, it is carried forward to the new frame
     * so that per-frame config (policy, settings) survives across commits.
     */
    private Frame storeAndBuildFrame(Item item, FrameKey key, String alias, ItemID typeId, boolean identity,
                                     byte[] plaintextBytes, Consumer<byte[]> storePayload,
                                     dev.everydaythings.graph.crypt.EncryptionContext encryptionContext,
                                     FrameConfig existingConfig,
                                     Consumer<FrameBody> storeFrameBody) {

        ContentID snapshotCid = new ContentID(Hash.DEFAULT.digest(plaintextBytes), Hash.DEFAULT);
        ContentID encryptedCid = null;

        boolean shouldEncrypt = encryptionContext != null
                && encryptionContext != dev.everydaythings.graph.crypt.EncryptionContext.NONE
                && encryptionContext.shouldEncrypt(key);

        if (shouldEncrypt) {
            var recipients = encryptionContext.recipients(key);
            if (!recipients.isEmpty()) {
                // Encrypt the plaintext into a Tag 10 envelope
                var envelope = dev.everydaythings.graph.crypt.EnvelopeOps.encryptAnonymous(
                        plaintextBytes, Hash.DEFAULT.digest(plaintextBytes), null,
                        encryptionContext.aeadAlgorithm(), recipients);
                byte[] envelopeBytes = envelope.encodeBinary(Canonical.Scope.RECORD);
                encryptedCid = new ContentID(Hash.DEFAULT.digest(envelopeBytes), Hash.DEFAULT);

                // Store the encrypted envelope (not the plaintext)
                if (storePayload != null) storePayload.accept(envelopeBytes);
            } else {
                // No recipients — store cleartext
                if (storePayload != null) storePayload.accept(plaintextBytes);
            }
        } else {
            // No encryption — store cleartext
            if (storePayload != null) storePayload.accept(plaintextBytes);
        }

        Frame frame = Frame.snapshot(key, typeId, snapshotCid, identity);
        // alias removed — display names resolved via TokenDictionary

        // Carry forward existing config (policy, settings) from previous version
        if (existingConfig != null && existingConfig.policy() != null) {
            frame.setPolicy(existingConfig.policy());
        }

        // Build FrameBody for this component frame and set bodyHash
        buildAndStoreComponentBody(item, null, typeId, alias, snapshotCid, encryptedCid, existingConfig, frame, storeFrameBody);

        return frame;
    }

    /**
     * Build a {@link FrameBody} for a component frame, set its body hash on the entry,
     * and store it via the provided consumer.
     *
     * <p>The FrameBody carries the semantic assertion: predicate=type, theme=item,
     * with bindings for content (Topic), alias (Alias), encrypted envelope (Encrypted),
     * and config (Config).
     *
     * <p>The bodyHash stored on the entry is the CID of the RECORD-scope encoding
     * (which includes all bindings). This allows full reconstruction during hydration
     * from endorsements. The BODY-scope hash (identity only) can be computed lazily
     * when needed for identity comparison.
     */
    private void buildAndStoreComponentBody(Item item, FrameFieldSpec spec, ItemID typeId,
                                            String alias, ContentID snapshotCid,
                                            ContentID encryptedCid,
                                            FrameConfig existingConfig,
                                            Frame frame,
                                            Consumer<FrameBody> storeFrameBody) {
        java.util.List<dev.everydaythings.graph.frame.Binding> bindings = new java.util.ArrayList<>();

        ItemID topicId = ThematicRole.Topic.IID;

        // Content — Topic role (identity binding)
        if (snapshotCid != null) {
            bindings.add(new dev.everydaythings.graph.frame.Binding(
                    topicId, BindingTarget.ref(snapshotCid), true, false));
        }

        // Encrypted envelope — compound key (TOPIC, ENCRYPTED)
        if (encryptedCid != null) {
            bindings.add(dev.everydaythings.graph.frame.Binding.compound(
                    java.util.List.of(topicId, CoreVocabulary.Encrypted.IID),
                    BindingTarget.ref(encryptedCid), false, false));
        }

        // Config — Config role (non-identity, CBOR Literal)
        if (existingConfig != null && existingConfig.policy() != null) {
            byte[] configBytes = existingConfig.encodeBinary(Canonical.Scope.RECORD);
            bindings.add(dev.everydaythings.graph.frame.Binding.nonIdentity(
                    ThematicRole.Config.IID,
                    new Literal(Literal.TYPE_CBOR, configBytes)));
        }

        FrameBody body = new FrameBody(typeId, item.iid(), bindings);

        // Use RECORD-scope CID as bodyHash (includes all bindings for reconstruction)
        byte[] recordBytes = body.encodeBinary(Canonical.Scope.RECORD);
        ContentID recordCid = ContentID.of(recordBytes);
        frame.setBodyHash(recordCid);
        frame.setBody(body);

        if (storeFrameBody != null) {
            storeFrameBody.accept(body);
        }
    }

    /**
     * Bind unendorsed frame fields for commit — create frame bodies, store them, add as frames.
     *
     * @param item           The item to read field values from
     * @param table          The endorsements table to add frames to
     * @param storePayload   Function to store payload bytes and return CID
     * @param storeFrameBody Function to store frame bodies (indexing)
     */
    public void bindUnendorsedFramesForCommit(Item item, EndorsementsTable table,
                                              java.util.function.Function<byte[], ContentID> storePayload,
                                              Consumer<FrameBody> storeFrameBody) {
        table.removeBareFrames();

        for (FrameFieldSpec spec : frameFields) {
            if (spec.endorsed()) continue;
            Object value = spec.getValue(item);
            if (value == null) continue;

            if (spec.isIterable()) {
                for (Object element : (Iterable<?>) value) {
                    bindSingleUnendorsedFrame(item, spec, element, table, storePayload, storeFrameBody);
                }
            } else {
                bindSingleUnendorsedFrame(item, spec, value, table, storePayload, storeFrameBody);
            }
        }
    }

    private void bindSingleUnendorsedFrame(Item item, FrameFieldSpec spec, Object object,
                                           EndorsementsTable table,
                                           java.util.function.Function<byte[], ContentID> storePayload,
                                           Consumer<FrameBody> storeFrameBody) {
        FrameBody body = buildFrameBody(item, spec, object);
        if (body == null) return;

        if (storeFrameBody != null) {
            storeFrameBody.accept(body);
        }

        byte[] bytes = body.encodeBinary(Canonical.Scope.RECORD);
        ContentID cid = (storePayload != null) ? storePayload.apply(bytes) : ContentID.of(bytes);

        Frame frame = Frame.forFrameBody(body.predicate(), cid, true, spec.predicateDisplayName());
        table.add(frame);
        table.setLive(frame.frameKey(), body);
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
