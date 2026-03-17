package dev.everydaythings.graph.library;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.Frame;
import dev.everydaythings.graph.frame.PresentationConfig;
import dev.everydaythings.graph.frame.SurfaceTemplateComponent;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.Implements;
import dev.everydaythings.graph.item.Type;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.SceneCompiler;
import dev.everydaythings.graph.ui.scene.SceneSchema;
import dev.everydaythings.graph.ui.scene.ViewNode;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;
import dev.everydaythings.graph.frame.FrameBody;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.language.LexicalVocabulary;
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
import java.util.Map;
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
     * unit resolution ("ch" &rarr; Unit.CharacterWidth.SEED) and other seed-based
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

            // 1. @Item.Seed static fields on @Implements classes
            for (ClassInfo classInfo : scanResult.getClassesWithAnnotation(Implements.class)) {
                Class<?> clazz = classInfo.loadClass();
                if (!Item.class.isAssignableFrom(clazz)) continue;
                collectSeedItemsWithTokens(clazz, result);
            }

            // 1b. @Item.Seed static fields on @Type classes without @Implements
            for (ClassInfo classInfo : scanResult.getClassesWithAnnotation(Type.class)) {
                Class<?> clazz = classInfo.loadClass();
                if (clazz.isAnnotationPresent(Implements.class)) continue;
                if (!Item.class.isAssignableFrom(clazz)) continue;
                collectSeedItemsWithTokens(clazz, result);
            }

            // 2. @Item.Seed static fields on non-identity classes (e.g. GameVocabulary)
            for (ClassInfo classInfo : scanResult.getClassesWithFieldAnnotation(Item.Seed.class)) {
                Class<?> clazz = classInfo.loadClass();
                if (hasIdentity(clazz)) continue; // already handled above
                collectSeedItemsWithTokens(clazz, result);
            }

            // 3. @Implements/@Type classes → type seed items (concrete or abstract)
            for (ClassInfo classInfo : scanResult.getClassesWithAnnotation(Implements.class)) {
                Class<?> clazz = classInfo.loadClass();
                if (!Item.class.isAssignableFrom(clazz)) continue;

                String key = readKey(clazz);
                if (key == null || key.isBlank()) continue;

                try {
                    if (Modifier.isAbstract(clazz.getModifiers())) {
                        String name = extractReadableName(key);
                        Sememe ns = new Sememe(key)
                                .gloss("en", name)
                                .word(PartOfSpeech.NOUN, GrammaticalFeature.Lemma.SEED, "en", name.toLowerCase());
                        if (ns.extractTokens().findAny().isPresent()) {
                            result.add(ns);
                        }
                    } else {
                        Class<? extends Item> itemClass = (Class<? extends Item>) clazz;
                        Constructor<? extends Item> ctor = itemClass.getDeclaredConstructor(ItemID.class);
                        ctor.setAccessible(true);
                        ItemID typeId = ItemID.fromString(key);
                        Item typeSeed = ctor.newInstance(typeId);
                        if (typeSeed.extractTokens().findAny().isPresent()) {
                            result.add(typeSeed);
                        }
                    }
                } catch (Exception e) {
                    // Skip types without seed constructor
                }
            }

            // 4. @Implements/@Type non-Item classes → sememe seed items
            for (ClassInfo classInfo : scanResult.getClassesWithAnnotation(Implements.class)) {
                Class<?> clazz = classInfo.loadClass();
                if (Item.class.isAssignableFrom(clazz)) continue;
                if (Modifier.isAbstract(clazz.getModifiers())) continue;

                String key = readKey(clazz);
                if (key == null || key.isBlank()) continue;

                String name = extractReadableName(key);
                Sememe ns = new Sememe(key)
                        .gloss("en", name)
                        .word(PartOfSpeech.NOUN, GrammaticalFeature.Lemma.SEED, "en", name.toLowerCase());
                if (ns.extractTokens().findAny().isPresent()) {
                    result.add(ns);
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

            // 1. ALL @Item.Seed fields across the full classpath — these define concepts
            for (ClassInfo classInfo : scanResult.getClassesWithFieldAnnotation(Item.Seed.class)) {
                Class<?> clazz = classInfo.loadClass();
                scanForSeedItems(clazz);
            }

            // 2. @Implements classes → IMPLEMENTED_BY + display (no Sememe creation)
            for (ClassInfo classInfo : scanResult.getClassesWithAnnotation(Implements.class)) {
                Class<?> clazz = classInfo.loadClass();
                registerImplementation(clazz);
            }

            // 3. @Value.Type classes (unchanged)
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

    /**
     * Register a class as an implementation of a concept.
     *
     * <p>Creates IMPLEMENTED_BY relation and attaches display metadata from @Type.
     * Does NOT create Sememe seeds — those come from @Item.Seed fields in TypeSeed
     * inner classes, scanned independently by scanForSeedItems().
     */
    private void registerImplementation(Class<?> clazz) {
        String key = readKey(clazz);
        if (key == null || key.isBlank()) return;
        if (Modifier.isAbstract(clazz.getModifiers())) return;

        ItemID typeId = ItemID.fromString(key);

        // Find the seed item by IID — it was stored earlier in the seed scan
        Item seedItem = seedItems.stream()
                .filter(i -> typeId.equals(i.iid()))
                .findFirst()
                .orElse(null);

        // Attach display metadata if @Type present
        Type annotation = clazz.getAnnotation(Type.class);
        if (annotation != null && seedItem != null) {
            attachTypePresentation(seedItem, clazz, annotation, key);
        }

        // Store manifest first (with presentation but without IMPLEMENTED_BY).
        // storeItem() calls generateSeedManifest() which rebuilds the EndorsementsTable
        // from @Item.Frame fields, so any manually added frames would be lost.
        if (seedItem != null) {
            storeItem(seedItem);
        }

        // Create IMPLEMENTED_BY relation and attach to seed item AFTER storeItem().
        // This ensures the in-memory cached instance carries the frame at runtime,
        // since generateSeedManifest()'s scanAndBindFields() won't overwrite it again.
        FrameBody implBy = createImplementedByRelation(typeId, clazz, seedItem);
        storeFrameBody(implBy);
        storeFrameBody(createTitleRelation(typeId, key));
    }

    private void registerValueType(Class<? extends dev.everydaythings.graph.value.Value> type) {
        var annotation = type.getAnnotation(dev.everydaythings.graph.value.Value.Type.class);
        if (annotation == null || annotation.value().isBlank()) return;

        ItemID typeId = ItemID.fromString(annotation.value());

        // Value types may not have seed items, just create IMPLEMENTED_BY relation
        storeFrameBody(FrameBody.of(
                CoreVocabulary.ImplementedBy.SEED.iid(),
                typeId,
                Map.of(ThematicRole.Goal.SEED.iid(), Literal.ofJavaClass(type))));
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
                    storeFrameBody(createInstanceOfRelation(item));

                    String key = extractKeyFromItem(item);
                    if (key != null) {
                        storeFrameBody(createTitleRelation(item.iid(), key));
                    }
                }
            } catch (IllegalAccessException e) {
                // Skip inaccessible fields
            }
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
    private void attachTypePresentation(Item typeItem, Class<?> typeClass, Type annotation, String key) {
        // Start with display fields from @Type annotation
        SurfaceTemplateComponent stc = SurfaceTemplateComponent.fromType(annotation);
        stc.typeName(extractReadableName(key));

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

        // Phase 5: also store PresentationConfig in a PRESENTATION frame on the type item.
        // This feeds the three-level cascade (instance → type → sememe).
        PresentationConfig presConfig = PresentationConfig.of(
                annotation.glyph(), annotation.color(), annotation.shape());
        byte[] presBytes = presConfig.encodeBinary(Canonical.Scope.RECORD);
        Literal presLiteral = new Literal(Literal.TYPE_CBOR, presBytes);

        FrameKey presKey = FrameKey.of(ThematicRole.Presentation.SEED.iid());
        FrameBody presBody = new FrameBody(ThematicRole.Presentation.SEED.iid(),
                List.of(new Binding(ThematicRole.Topic.SEED.iid(), presLiteral)));
        Frame presFrame = new Frame(presKey, ThematicRole.Presentation.SEED.iid(),
                presBody, presBody.hash(), false);
        typeItem.frames().add(presFrame);
    }

    /**
     * Attach a component to an item's content table.
     *
     * <p>Encodes the component to CBOR, computes a ContentID, and stores
     * the entry with a snapshot CID so it survives manifest generation.
     */
    private void attachComponent(Item item, FrameKey key, String alias, Object component) {
        var contentTable = item.frames();
        if (contentTable != null) {
            // Encode and compute CID upfront so the entry has a snapshot
            byte[] bytes = ((Canonical) component).encodeBinary(Canonical.Scope.RECORD);
            ContentID cid = ContentID.of(bytes);

            Frame frame = Frame.snapshot(key,
                    ItemID.fromString(SurfaceTemplateComponent.KEY), cid, false);

            contentTable.add(frame);
            contentTable.setLive(key, component);
            logger.debug("Attached surface template to {}", item.displayToken());
        }
    }

    /**
     * Attach SememeGloss components for each language gloss on a seed sememe.
     */
    private void attachGlosses(Sememe sememe) {
        var glosses = sememe.glosses();
        if (glosses == null || glosses.isEmpty()) return;

        var contentTable = sememe.frames();
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
            FrameKey key = FrameKey.literal(handleKey);

            byte[] bytes = gloss.encodeBinary(Canonical.Scope.RECORD);
            ContentID cid = ContentID.of(bytes);

            Frame ce = Frame.snapshot(key,
                    ItemID.fromString(SememeGloss.KEY), cid, false);

            contentTable.add(ce);
            contentTable.setLive(key, gloss);
        }
    }

    // ==================================================================================
    // Relation Creation
    // ==================================================================================

    private FrameBody createImplementedByRelation(ItemID typeId, Class<?> implementingClass, Item item) {
        FrameBody body = FrameBody.of(
                CoreVocabulary.ImplementedBy.SEED.iid(),
                typeId,
                Map.of(ThematicRole.Goal.SEED.iid(), Literal.ofJavaClass(implementingClass)));

        // Add to item's endorsements table as a bare frame
        if (item != null) {
            byte[] bytes = body.encodeBinary(Canonical.Scope.RECORD);
            ContentID cid = ContentID.of(bytes);
            Frame frame = Frame.forFrameBody(body.predicate(), cid, true, "implementedBy");
            item.frames().add(frame);
            item.frames().setLive(frame.frameKey(), body);
        }

        return body;
    }

    private FrameBody createTitleRelation(ItemID itemId, String key) {
        return FrameBody.of(
                CoreVocabulary.Title.SEED.iid(),
                itemId,
                Map.of(ThematicRole.Goal.SEED.iid(), Literal.ofText(key)));
    }

    private FrameBody createInstanceOfRelation(Item item) {
        String key = readKey(item.getClass());
        if (key == null || key.isBlank()) return null;

        ItemID typeId = ItemID.fromString(key);
        ItemID instanceId = item.iid();

        // Don't create relation if instance IS the type
        if (instanceId.equals(typeId)) return null;

        return FrameBody.of(
                LexicalVocabulary.InstanceOf.SEED.iid(),
                instanceId,
                Map.of(ThematicRole.Goal.SEED.iid(), BindingTarget.iid(typeId)));
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
            for (Frame frame : manifest.components()) {
                if (frame.body().hasContent()) {
                    // Try @ContentField-based encoding first
                    byte[] content = item.encodeComponentValue(frame.frameKey());

                    // Fall back to live value in content table (for manually-attached components)
                    if (content == null && item.frames() != null) {
                        Object live = item.frames().getLive(frame.frameKey()).orElse(null);
                        if (live instanceof Canonical c) {
                            content = c.encodeBinary(Canonical.Scope.RECORD);
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

    private void storeFrameBody(FrameBody body) {
        if (body == null) return;
        try {
            byte[] record = body.encodeBinary(Canonical.Scope.RECORD);
            store.persistContent(record, tx);
        } catch (Exception e) {
            logger.warn("Failed to store frame body: {}", e.getMessage());
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

    /**
     * Read the canonical key from @Implements (preferred) or @Type (fallback).
     */
    private static String readKey(Class<?> clazz) {
        Implements impl = clazz.getAnnotation(Implements.class);
        return impl != null ? impl.value() : null;
    }

    /**
     * Check if a class has identity metadata (@Implements or @Type).
     */
    private static boolean hasIdentity(Class<?> clazz) {
        return clazz.isAnnotationPresent(Implements.class) || clazz.isAnnotationPresent(Type.class);
    }

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
