package dev.everydaythings.graph.library;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.component.ComponentType;
import dev.everydaythings.graph.item.component.FrameEntry;
import dev.everydaythings.graph.item.component.Components;
import dev.everydaythings.graph.item.component.SurfaceTemplateComponent;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.id.HandleID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.SceneCompiler;
import dev.everydaythings.graph.ui.scene.SceneSchema;
import dev.everydaythings.graph.ui.scene.ViewNode;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;
import dev.everydaythings.graph.item.relation.Relation;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.Role;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.language.SememeGloss;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import lombok.extern.log4j.Log4j2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Bootstraps an ItemStore with seed vocabulary from the classpath.
 *
 * <p>Scans the classpath for:
 * <ul>
 *   <li>{@code @Type} classes - registered with IMPLEMENTED_BY relations</li>
 *   <li>{@code @Item.Seed} fields - stored as seed items with manifests</li>
 *   <li>{@code @Value.Type} classes - registered with IMPLEMENTED_BY relations</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * ItemStore store = MapDBItemStore.memory();
 * SeedVocabulary.bootstrap(store);
 * // store now contains all seed items, types, and relations
 * }</pre>
 */
@Log4j2
public final class SeedVocabulary {

    private static final String BASE_PACKAGE = "dev.everydaythings.graph";

    private final ItemStore store;
    private WriteTransaction tx;
    private final List<Item> seedItems = new ArrayList<>();

    private SeedVocabulary(ItemStore store, WriteTransaction tx) {
        this.store = store;
        this.tx = tx;
    }

    /**
     * Bootstrap the given ItemStore with seed vocabulary.
     *
     * <p>Scans the classpath for types and seed items, then populates the store
     * with manifests, relations, and content. When this method returns, the store
     * contains all seed data and this class can be discarded.
     *
     * @param store The ItemStore to populate
     */
    public static List<Item> bootstrap(ItemStore store) {
        Objects.requireNonNull(store, "store");
        logger.info("Bootstrapping vocabulary - scanning classpath for types and seeds");

        SeedVocabulary vocab = new SeedVocabulary(store, null);
        store.runInWriteTransaction(tx -> {
            vocab.tx = tx;
            vocab.scan();
        });

        logger.info("Vocabulary bootstrap complete");
        return Collections.unmodifiableList(vocab.seedItems);
    }

    /**
     * Collect all seed Items that provide tokens for the TokenDictionary.
     *
     * <p>Scans the classpath for {@code @Item.Seed} fields and returns
     * those whose {@link Item#extractTokens()} produces entries. Also scans
     * for {@code @Type} annotated classes. This enables
     * unit resolution ("ch" &rarr; Unit.CHARACTER_WIDTH) and other seed-based
     * lookups through the graph.
     *
     * @return seed Items with extractable tokens
     */
    @SuppressWarnings("unchecked")
    public static List<Item> seedItemsWithTokens() {
        List<Item> result = new ArrayList<>();
        try (ScanResult scanResult = new ClassGraph()
                .acceptPackages(BASE_PACKAGE)
                .enableClassInfo()
                .enableAnnotationInfo()
                .enableFieldInfo()
                .ignoreFieldVisibility()
                .scan()) {

            // 1. @Item.Seed static fields on @Type classes
            for (ClassInfo classInfo : scanResult.getClassesWithAnnotation(Type.class)) {
                Class<?> clazz = classInfo.loadClass();
                if (!Item.class.isAssignableFrom(clazz)) continue;
                collectSeedItemsWithTokens(clazz, result);
            }

            // 2. @Item.Seed static fields on non-@Type classes (e.g. GameVocabulary)
            for (ClassInfo classInfo : scanResult.getClassesWithFieldAnnotation(Item.Seed.class)) {
                Class<?> clazz = classInfo.loadClass();
                if (clazz.isAnnotationPresent(Type.class)) continue; // already handled above
                collectSeedItemsWithTokens(clazz, result);
            }

            // 3. @Type classes → type seed items (concrete or abstract)
            for (ClassInfo classInfo : scanResult.getClassesWithAnnotation(Type.class)) {
                Class<?> clazz = classInfo.loadClass();
                if (!Item.class.isAssignableFrom(clazz)) continue;

                Type ann = clazz.getAnnotation(Type.class);
                if (ann == null || ann.value().isBlank()) continue;

                try {
                    if (Modifier.isAbstract(clazz.getModifiers())) {
                        // Abstract classes: derive tokens statically from @Type annotation
                        String key = ann.value();
                        String name = extractReadableName(key);
                        ComponentType ct = new ComponentType(key, name, clazz);
                        if (ct.extractTokens().findAny().isPresent()) {
                            result.add(ct);
                        }
                    } else {
                        // Concrete classes: instantiate via seed constructor
                        Class<? extends Item> itemClass = (Class<? extends Item>) clazz;
                        Constructor<? extends Item> ctor = itemClass.getDeclaredConstructor(ItemID.class);
                        ctor.setAccessible(true);
                        ItemID typeId = ItemID.fromString(ann.value());
                        Item typeSeed = ctor.newInstance(typeId);
                        if (typeSeed.extractTokens().findAny().isPresent()) {
                            result.add(typeSeed);
                        }
                    }
                } catch (Exception e) {
                    // Skip types without seed constructor
                }
            }

            // 4. @Type classes (non-Item) → ComponentType seed items
            for (ClassInfo classInfo : scanResult.getClassesWithAnnotation(Type.class)) {
                Class<?> clazz = classInfo.loadClass();
                if (Item.class.isAssignableFrom(clazz)) continue;
                if (Modifier.isAbstract(clazz.getModifiers())) continue;

                Type ann = clazz.getAnnotation(Type.class);
                if (ann == null || ann.value().isBlank()) continue;

                String key = ann.value();
                String name = extractReadableName(key);
                ComponentType ct = new ComponentType(key, name, clazz);
                if (ct.extractTokens().findAny().isPresent()) {
                    result.add(ct);
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private void scan() {
        // Seed English FIRST — it must exist before other seeds
        // register their English tokens scoped to cg:language/eng
        seedEnglish();

        try (ScanResult scanResult = new ClassGraph()
                .acceptPackages(BASE_PACKAGE)
                .enableClassInfo()
                .enableAnnotationInfo()
                .enableFieldInfo()
                .ignoreFieldVisibility()
                .scan()) {

            // Find all @Type classes (items and components unified)
            for (ClassInfo classInfo : scanResult.getClassesWithAnnotation(Type.class)) {
                Class<?> clazz = classInfo.loadClass();
                if (Item.class.isAssignableFrom(clazz)) {
                    Class<? extends Item> itemClass = (Class<? extends Item>) clazz;
                    registerItemType(itemClass);
                    scanForSeedItems(clazz);
                } else {
                    registerComponentType(clazz);
                }
            }

            // Find non-@Type classes with @Item.Seed fields (e.g. GameVocabulary)
            for (ClassInfo classInfo : scanResult.getClassesWithFieldAnnotation(Item.Seed.class)) {
                Class<?> clazz = classInfo.loadClass();
                if (clazz.isAnnotationPresent(Type.class)) continue; // already handled above
                scanForSeedItems(clazz);
            }

            // Find all @Value.Type classes
            for (ClassInfo classInfo : scanResult.getClassesWithAnnotation(
                    dev.everydaythings.graph.value.Value.Type.class)) {
                Class<?> clazz = classInfo.loadClass();
                if (dev.everydaythings.graph.value.Value.class.isAssignableFrom(clazz)) {
                    registerValueType((Class<? extends dev.everydaythings.graph.value.Value>) clazz);
                }
            }
        }
    }

    // ==================================================================================
    // Language Seeding
    // ==================================================================================

    /**
     * Seed the English Language item.
     *
     * <p>Only English is seeded at bootstrap because it's needed to scope
     * seed tokens. All other languages are created during the English import
     * (their names are English words — "French", "Japanese", etc.).
     */
    private void seedEnglish() {
        Language english = new Language(Language.ENGLISH, "eng");
        if (storeItem(english)) {
            seedItems.add(english);
            logger.info("Seeded English Language item: {}", Language.ENGLISH);
        }
    }

    /**
     * Load ISO 639-3 codes and English names from the bundled resource file.
     *
     * <p>Each entry is a {@code String[2]} of {@code [code, englishName]}.
     * Used by the English import to create Language items for all languages.
     */
    public static List<String[]> loadLanguageCodes() {
        List<String[]> result = new ArrayList<>();
        try (InputStream is = SeedVocabulary.class.getResourceAsStream("/iso-639-3.tsv")) {
            if (is == null) {
                logger.error("Missing resource: /iso-639-3.tsv");
                return result;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("#") || line.isBlank()) continue;
                    String[] parts = line.split("\t", 2);
                    if (parts.length == 2 && parts[0].length() == 3) {
                        result.add(parts);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Failed to load language codes: {}", e.getMessage());
        }
        return result;
    }

    // ==================================================================================
    // Type Registration
    // ==================================================================================

    private void registerItemType(Class<? extends Item> type) {
        Type annotation = type.getAnnotation(Type.class);
        if (annotation == null || annotation.value().isBlank()) return;

        String key = annotation.value();
        ItemID typeId = ItemID.fromString(key);

        Item typeSeed;
        if (Modifier.isAbstract(type.getModifiers())) {
            // Abstract classes: create a ComponentType seed with static metadata
            String name = extractReadableName(key);
            ComponentType.registerTypeName(typeId, name);
            typeSeed = new ComponentType(key, name, type);
        } else {
            // Concrete classes: instantiate via seed constructor
            typeSeed = createTypeSeed(type, typeId);
            if (typeSeed == null) return;
        }

        // Attach unified presentation component (display metadata + surface template)
        attachTypePresentation(typeSeed, type, annotation);

        // Store manifest and content - only create relations if storage succeeds
        boolean stored = storeItem(typeSeed);
        if (!stored) {
            logger.warn("Skipping relations for {} since item storage failed", key);
            return;
        }

        seedItems.add(typeSeed);

        // Create relations
        storeRelation(createTitleRelation(typeId, key));
        storeRelation(createImplementedByRelation(typeId, type, typeSeed));
    }

    private void registerComponentType(Class<?> type) {
        Type annotation = type.getAnnotation(Type.class);
        if (annotation == null || annotation.value().isBlank()) return;

        String key = annotation.value();
        ItemID typeId = ItemID.fromString(key);

        // Skip abstract classes
        if (Modifier.isAbstract(type.getModifiers())) return;

        // Create ComponentType seed Item
        String name = extractReadableName(key);
        ComponentType.registerTypeName(typeId, name);
        ComponentType componentType = new ComponentType(key, name, type);

        // Attach unified presentation component (display metadata + surface template)
        attachTypePresentation(componentType, type, annotation);

        // Store manifest and content
        boolean stored = storeItem(componentType);
        if (stored) {
            seedItems.add(componentType);
        }

        // Create relations
        storeRelation(createImplementedByRelation(typeId, type, componentType));
        storeRelation(createTitleRelation(typeId, key));

        // HYPERNYM relation: this type is-a-kind-of ComponentType
        storeRelation(Relation.builder()
                .predicate(Sememe.HYPERNYM.iid())
                .bind(Role.THEME.iid(), Relation.iid(typeId))
                .bind(Role.TARGET.iid(), Relation.iid(ItemID.fromString(ComponentType.KEY)))
                .build());
    }

    private void registerValueType(Class<? extends dev.everydaythings.graph.value.Value> type) {
        var annotation = type.getAnnotation(dev.everydaythings.graph.value.Value.Type.class);
        if (annotation == null || annotation.value().isBlank()) return;

        ItemID typeId = ItemID.fromString(annotation.value());

        // Value types may not have seed items, just create IMPLEMENTED_BY relation
        storeRelation(Relation.builder()
                .predicate(Sememe.IMPLEMENTED_BY.iid())
                .bind(Role.THEME.iid(), Relation.iid(typeId))
                .bind(Role.TARGET.iid(), Literal.ofJavaClass(type))
                .build());
    }

    // ==================================================================================
    // Seed Item Scanning
    // ==================================================================================

    private void scanForSeedItems(Class<?> clazz) {
        for (Field field : clazz.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            if (!Modifier.isFinal(field.getModifiers())) continue;
            if (!Item.class.isAssignableFrom(field.getType())) continue;
            if (!field.isAnnotationPresent(Item.Seed.class)) continue;

            try {
                field.setAccessible(true);
                Item item = (Item) field.get(null);
                if (item != null) {
                    // Attach SememeGloss components before storing
                    if (item instanceof Sememe sememe) {
                        attachGlosses(sememe);
                    }

                    boolean stored = storeItem(item);
                    if (stored) {
                        seedItems.add(item);
                    }
                    storeRelation(createInstanceOfRelation(item));

                    String key = extractKeyFromItem(item);
                    if (key != null) {
                        storeRelation(createTitleRelation(item.iid(), key));
                    }
                }
            } catch (IllegalAccessException e) {
                // Skip inaccessible fields
            }
        }
    }

    // ==================================================================================
    // Item Creation
    // ==================================================================================

    private Item createTypeSeed(Class<? extends Item> type, ItemID typeId) {
        try {
            Constructor<? extends Item> ctor = type.getDeclaredConstructor(ItemID.class);
            ctor.setAccessible(true);
            Item result = ctor.newInstance(typeId);
            logger.info("Created type seed for {}: {}", type.getSimpleName(), typeId.encodeText());
            return result;
        } catch (NoSuchMethodException e) {
            logger.debug("No (ItemID) constructor for {}", type.getName());
            return null;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            logger.error("Failed to instantiate type seed for {}: {}", type.getName(), e.getMessage(), e);
            return null;
        }
    }

    // ==================================================================================
    // Unified Presentation Attachment
    // ==================================================================================

    /**
     * Attach a unified SurfaceTemplateComponent with both display metadata and
     * surface template to a type item.
     *
     * <p>This replaces the former two-component pattern (DisplayComponent + SurfaceTemplateComponent)
     * with a single component at handle "surface".
     */
    private void attachTypePresentation(Item typeItem, Class<?> typeClass, Type annotation) {
        // Start with display fields from @Type annotation
        SurfaceTemplateComponent stc = SurfaceTemplateComponent.fromType(annotation);
        stc.typeName(extractReadableName(annotation.value()));

        // Compile surface template from @Scene annotations (if present)
        Class<?> surfaceClass = null;
        Scene sceneAnn = typeClass.getAnnotation(Scene.class);
        if (sceneAnn != null && sceneAnn.as() != SceneSchema.class) {
            surfaceClass = sceneAnn.as();
        }
        Class<?> target = surfaceClass != null ? surfaceClass : typeClass;
        if (SceneCompiler.canCompile(target)) {
            ViewNode compiled = SceneCompiler.getCompiled(target);
            if (compiled != null) {
                stc.root(compiled);
            }
        }

        attachComponent(typeItem, SurfaceTemplateComponent.HANDLE, "surface", stc);
    }

    /**
     * Attach a component to an item's content table.
     *
     * <p>Encodes the component to CBOR, computes a ContentID, and stores
     * the entry with a snapshot CID so it survives manifest generation.
     */
    private void attachComponent(Item item, HandleID handle, String alias, Object component) {
        var contentTable = item.content();
        if (contentTable != null) {
            // Encode and compute CID upfront so the entry has a snapshot
            byte[] bytes = ((Canonical) component).encodeBinary(Canonical.Scope.RECORD);
            ContentID cid = ContentID.of(bytes);

            FrameEntry entry = FrameEntry.builder()
                    .handle(handle)
                    .type(ItemID.fromString(SurfaceTemplateComponent.KEY))
                    .identity(false)
                    .payload(FrameEntry.EntryPayload.builder().snapshotCid(cid).build())
                    .build();

            contentTable.add(entry);
            contentTable.setLive(handle, alias, component);
            logger.debug("Attached surface template to {}", item.displayToken());
        }
    }

    /**
     * Attach SememeGloss components for each language gloss on a seed sememe.
     */
    private void attachGlosses(Sememe sememe) {
        var glosses = sememe.glosses();
        if (glosses == null || glosses.isEmpty()) return;

        var contentTable = sememe.content();
        if (contentTable == null) return;

        for (var entry : glosses.entrySet()) {
            String langCode = entry.getKey();
            String text = entry.getValue();
            if (text == null || text.isBlank()) continue;

            // Map 2-letter codes to 3-letter for consistency
            String iso3 = langCode.equals("en") ? "eng" : langCode;
            ItemID langIid = Language.iidFor(iso3);
            SememeGloss gloss = new SememeGloss(langIid, text);

            String handleKey = SememeGloss.handleKeyFor(iso3);
            HandleID handle = HandleID.of(handleKey);

            byte[] bytes = gloss.encodeBinary(Canonical.Scope.RECORD);
            ContentID cid = ContentID.of(bytes);

            FrameEntry ce = FrameEntry.builder()
                    .handle(handle)
                    .type(ItemID.fromString(SememeGloss.KEY))
                    .identity(false)
                    .payload(FrameEntry.EntryPayload.builder().snapshotCid(cid).build())
                    .build();

            contentTable.add(ce);
            contentTable.setLive(handle, handleKey, gloss);
        }
    }

    // ==================================================================================
    // Relation Creation
    // ==================================================================================

    private Relation createImplementedByRelation(ItemID typeId, Class<?> implementingClass, Item item) {
        Relation relation = Relation.builder()
                .predicate(Sememe.IMPLEMENTED_BY.iid())
                .bind(Role.THEME.iid(), Relation.iid(typeId))
                .bind(Role.TARGET.iid(), Literal.ofJavaClass(implementingClass))
                .build();

        // Add to item's component table as a relation entry
        if (item != null) {
            byte[] bytes = relation.encodeBinary(Canonical.Scope.RECORD);
            ContentID cid = ContentID.of(bytes);
            FrameEntry entry = FrameEntry.forRelation(relation.predicate(), cid, true);
            item.content().add(entry);
            item.content().setLive(entry.handle(), relation);
        }

        return relation;
    }

    private Relation createTitleRelation(ItemID itemId, String key) {
        return Relation.builder()
                .predicate(Sememe.TITLE.iid())
                .bind(Role.THEME.iid(), Relation.iid(itemId))
                .bind(Role.TARGET.iid(), Literal.ofText(key))
                .build();
    }

    private Relation createInstanceOfRelation(Item item) {
        Type typeAnnotation = item.getClass().getAnnotation(Type.class);
        if (typeAnnotation == null || typeAnnotation.value().isBlank()) return null;

        ItemID typeId = ItemID.fromString(typeAnnotation.value());
        ItemID instanceId = item.iid();

        // Don't create relation if instance IS the type
        if (instanceId.equals(typeId)) return null;

        return Relation.builder()
                .predicate(Sememe.INSTANCE_OF.iid())
                .bind(Role.THEME.iid(), Relation.iid(instanceId))
                .bind(Role.TARGET.iid(), Relation.iid(typeId))
                .build();
    }

    // ==================================================================================
    // Store Operations
    // ==================================================================================

    private boolean storeItem(Item item) {
        try {
            var manifest = item.generateSeedManifest();
            byte[] record = manifest.encodeBinary(Canonical.Scope.RECORD);
            store.persistManifest(item.iid(), record, tx);

            // Store component content
            for (FrameEntry entry : manifest.components()) {
                if (entry.payload().snapshotCid() != null) {
                    // Try @ContentField-based encoding first
                    byte[] content = item.encodeComponentValue(entry.handle());

                    // Fall back to live value in content table (for manually-attached components)
                    if (content == null && item.content() != null) {
                        Object live = item.content().getLive(entry.handle()).orElse(null);
                        if (live instanceof Canonical c) {
                            content = c.encodeBinary(Canonical.Scope.RECORD);
                        } else if (live != null) {
                            content = Components.encode(live);
                        }
                    }

                    if (content != null) {
                        store.persistContent(content, tx);
                    }
                }
            }
            return true;
        } catch (Exception e) {
            logger.error("Failed to store item {} ({}): {}", item.iid(), item.getClass().getSimpleName(), e.getMessage(), e);
            return false;
        }
    }

    private void storeRelation(Relation relation) {
        if (relation == null) return;
        try {
            byte[] record = relation.encodeBinary(Canonical.Scope.RECORD);
            store.persistContent(record, tx);
        } catch (Exception e) {
            logger.warn("Failed to store relation: {}", e.getMessage());
        }
    }

    // ==================================================================================
    // Seed Field Collection
    // ==================================================================================

    private static void collectSeedItemsWithTokens(Class<?> clazz, List<Item> result) {
        for (Field field : clazz.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            if (!Modifier.isFinal(field.getModifiers())) continue;
            if (!Item.class.isAssignableFrom(field.getType())) continue;
            if (!field.isAnnotationPresent(Item.Seed.class)) continue;

            try {
                field.setAccessible(true);
                Item item = (Item) field.get(null);
                if (item != null && item.extractTokens().findAny().isPresent()) {
                    result.add(item);
                }
            } catch (IllegalAccessException e) {
                // Skip inaccessible fields
            }
        }
    }

    // ==================================================================================
    // Utility Methods
    // ==================================================================================

    private static String extractReadableName(String typeKey) {
        String shortName = extractShortName(typeKey);
        if (shortName == null) return typeKey;
        return shortName.substring(0, 1).toUpperCase() + shortName.substring(1);
    }

    private static String extractShortName(String key) {
        if (key == null) return null;
        int lastSlash = key.lastIndexOf('/');
        int lastColon = key.lastIndexOf(':');
        int lastSep = Math.max(lastSlash, lastColon);
        if (lastSep >= 0 && lastSep < key.length() - 1) {
            return key.substring(lastSep + 1);
        }
        return null;
    }

    private static String extractKeyFromItem(Item item) {
        if (item instanceof Sememe sememe) {
            return sememe.canonicalKey();
        }
        return null;
    }
}
