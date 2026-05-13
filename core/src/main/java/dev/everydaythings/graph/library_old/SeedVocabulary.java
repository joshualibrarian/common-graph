package dev.everydaythings.graph.library_old;

import dev.everydaythings.graph.encoding.Canonical;
import dev.everydaythings.graph.item.ItemOld;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.frame.FrameOld;
import dev.everydaythings.graph.frame.FrameBodyOld;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.Implements;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.library.WriteTransaction;
import dev.everydaythings.graph.library_old.ItemStore;
import dev.everydaythings.graph.library_old.SeedItemFactory;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import lombok.extern.log4j.Log4j2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
 * <p>Scans the classpath for {@code @ItemSeed} classes and creates
 * fully-formed items via {@link SeedItemFactory}. Also registers
 * {@code @Implements} classes with IMPLEMENTED_BY relations.
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
    private final List<ItemOld> seedItems = new ArrayList<>();

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
    public static List<ItemOld> bootstrap(ItemStore store) {
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
                .enableMethodInfo()
                .ignoreFieldVisibility()
                .ignoreMethodVisibility()
                .scan()) {

            // 1. @ItemSeed-annotated classes — create fully-formed items via SeedItemFactory
            //    Creates proper typed instances (Operator.Add, Function.Sqrt, etc.)
            scanSeedAnnotations(scanResult);

            // 3. @Implements classes → IMPLEMENTED_BY + display
            for (ClassInfo classInfo : scanResult.getClassesWithAnnotation(Implements.class)) {
                Class<?> clazz = classInfo.loadClass();
                registerImplementation(clazz);
            }

            // 4. @Implements on Value classes — register IMPLEMENTED_BY for value types
            for (ClassInfo classInfo : scanResult.getClassesWithAnnotation(Implements.class)) {
                Class<?> clazz = classInfo.loadClass();
                if (dev.everydaythings.graph.value.Value.class.isAssignableFrom(clazz)
                        && !ItemOld.class.isAssignableFrom(clazz)) {
                    registerValueType((Class<? extends dev.everydaythings.graph.value.Value>) clazz);
                }
            }

            // Lexeme frames are now created by SeedItemFactory from @ItemFrame annotations
        }
    }

    /**
     * Scan for classes annotated with {@link ItemSeed} and create fully-formed
     * seed Items via {@link SeedItemFactory}.
     *
     * <p>Each @ItemSeed class produces one Item with all its frames (glosses,
     * symbols, lexemes, IMPLEMENTED_BY) already created. The item is then stored.
     */
    private void scanSeedAnnotations(ScanResult scanResult) {
        int count = 0;

        for (ClassInfo classInfo : scanResult.getClassesWithAnnotation(ItemSeed.class)) {
            Class<?> clazz = classInfo.loadClass();

            // Skip if already created (e.g., by the old @Item.Seed system)
            ItemSeed seedAnn = clazz.getAnnotation(ItemSeed.class);
            if (seedAnn == null) continue;
            ItemID seedId = ItemID.fromString(seedAnn.key());
            if (seedItems.stream().anyMatch(i -> seedId.equals(i.iid()))) continue;

            // Create the fully-formed seed item
            ItemOld item = SeedItemFactory.create(clazz);
            if (item == null) continue;

            // Store it
            if (storeItem(item)) {
                seedItems.add(item);
                count++;
            }

            // Store frame bodies for indexing
            if (item.frames() != null) {
                item.frames().forEachLive(FrameBodyOld.class, body -> storeFrameBody(body));
            }
        }

        if (count > 0) {
            logger.info("@ItemSeed scanner: {} seeds created via SeedItemFactory", count);
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
     * Does NOT create Sememe seeds — those come from @Item.Seed fields in @ItemSeed
     * inner classes, scanned independently by scanForSeedItems().
     */
    private void registerImplementation(Class<?> clazz) {
        String key = readKey(clazz);
        if (key == null || key.isBlank()) return;
        if (Modifier.isAbstract(clazz.getModifiers())) return;

        ItemID typeId = ItemID.fromString(key);

        // Find the seed item by IID — it was stored earlier in the seed scan
        ItemOld seedItem = seedItems.stream()
                .filter(i -> typeId.equals(i.iid()))
                .findFirst()
                .orElse(null);

        if (seedItem == null) {
            logger.warn("@Implements({}) on {} has no matching @ItemSeed — IID will be unresolvable",
                    key, clazz.getSimpleName());
        }

        // @Implements metadata noted — presentation is now CONFIG bindings on frames/items

        // Store manifest first (with presentation but without IMPLEMENTED_BY).
        // storeItem() calls generateSeedManifest() which rebuilds the EndorsementsTable
        // from @Item.Frame fields, so any manually added frames would be lost.
        if (seedItem != null) {
            storeItem(seedItem);
        }

        // Create IMPLEMENTED_BY relation and attach to seed item AFTER storeItem().
        // This ensures the in-memory cached instance carries the frame at runtime,
        // since generateSeedManifest()'s scanAndBindFields() won't overwrite it again.
        FrameBodyOld implBy = createImplementedByRelation(typeId, clazz, seedItem);
        storeFrameBody(implBy);
        storeFrameBody(createTitleRelation(typeId, key));
    }

    private void registerValueType(Class<? extends dev.everydaythings.graph.value.Value> type) {
        Implements annotation = type.getAnnotation(Implements.class);
        if (annotation == null || annotation.value().isBlank()) return;

        ItemID typeId = ItemID.fromString(annotation.value());

        // Value types may not have seed items, just create IMPLEMENTED_BY relation
        storeFrameBody(FrameBodyOld.of(
                CoreVocabulary.ImplementedBy.IID,
                typeId,
                Map.of(ThematicRole.Goal.IID, Literal.ofJavaClass(type))));
    }

    // ==================================================================================
    // Relation Creation
    // ==================================================================================

    private FrameBodyOld createImplementedByRelation(ItemID typeId, Class<?> implementingClass, ItemOld item) {
        FrameBodyOld body = FrameBodyOld.of(
                CoreVocabulary.ImplementedBy.IID,
                typeId,
                Map.of(ThematicRole.Goal.IID, Literal.ofJavaClass(implementingClass)));

        // Add to item's endorsements table
        if (item != null) {
            byte[] bytes = body.encodeBinary(Canonical.Scope.RECORD);
            ContentID cid = ContentID.of(bytes);
            FrameOld frame = FrameOld.forFrameBody(body.predicate(), cid, true, "implementedBy");
            frame.setBody(body);
            item.frames().add(frame);
            item.frames().setLive(frame.frameKey(), body);
        }

        return body;
    }

    private FrameBodyOld createTitleRelation(ItemID itemId, String key) {
        return FrameBodyOld.of(
                CoreVocabulary.Title.IID,
                itemId,
                Map.of(ThematicRole.Goal.IID, Literal.ofText(key)));
    }

    // ==================================================================================
    // Store Operations
    // ==================================================================================

    private boolean storeItem(ItemOld item) {
        try {
            var manifest = item.generateSeedManifest();
            byte[] record = manifest.encodeBinary(Canonical.Scope.RECORD);
            store.persistManifest(item.iid(), record, tx);

            // Store component content
            for (FrameOld frame : manifest.components()) {
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

    private void storeFrameBody(FrameBodyOld body) {
        if (body == null) return;
        try {
            byte[] record = body.encodeBinary(Canonical.Scope.RECORD);
            store.persistContent(record, tx);
        } catch (Exception e) {
            logger.warn("Failed to store frame body: {}", e.getMessage());
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
        return clazz.isAnnotationPresent(Implements.class);
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

}
