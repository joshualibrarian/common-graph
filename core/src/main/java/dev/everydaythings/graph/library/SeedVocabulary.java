package dev.everydaythings.graph.library;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.Frame;
import dev.everydaythings.graph.frame.PresentationConfig;
import dev.everydaythings.graph.frame.SurfaceTemplateComponent;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.Implements;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.SceneCompiler;
import dev.everydaythings.graph.ui.scene.SceneSchema;
import dev.everydaythings.graph.ui.scene.ViewNode;
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
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
 *   <li>{@code @Implements} Value classes - registered with IMPLEMENTED_BY relations</li>
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
                                .word(PartOfSpeech.NOUN, new Sememe(GrammaticalFeature.Lemma.KEY), "en", name.toLowerCase());
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
                        .word(PartOfSpeech.NOUN, new Sememe(GrammaticalFeature.Lemma.KEY), "en", name.toLowerCase());
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
                .enableMethodInfo()
                .ignoreFieldVisibility()
                .ignoreMethodVisibility()
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

            // 3. @Implements on Value classes — register IMPLEMENTED_BY for value types
            for (ClassInfo classInfo : scanResult.getClassesWithAnnotation(Implements.class)) {
                Class<?> clazz = classInfo.loadClass();
                if (dev.everydaythings.graph.value.Value.class.isAssignableFrom(clazz)
                        && !Item.class.isAssignableFrom(clazz)) {
                    registerValueType((Class<? extends dev.everydaythings.graph.value.Value>) clazz);
                }
            }

            // 4. Create LEXEME frames on Language items from seed word declarations
            createLexemeFrames();

            // 5. NEW: Scan @Seed-annotated classes (runs alongside old @Item.Seed system)
            scanSeedAnnotations(scanResult);
        }
    }

    /**
     * Scan for classes annotated with {@link ItemSeed @Seed}
     * and create Sememe items with frames from annotated fields and methods.
     *
     * <p>This is the NEW bootstrap system that replaces @Item.Seed static fields.
     * During the migration period, both systems run — @Seed classes that already
     * have @Item.Seed fields will be skipped (the old system handles them).
     */
    private void scanSeedAnnotations(ScanResult scanResult) {
        ItemID lexemePredicate = ItemID.fromString(CoreVocabulary.Lexeme.KEY);
        int seedCount = 0;
        int frameCount = 0;
        int wordCount = 0;

        for (ClassInfo classInfo : scanResult.getClassesWithAnnotation(
                ItemSeed.class)) {
            Class<?> clazz = classInfo.loadClass();
            ItemSeed seedAnn = clazz.getAnnotation(
                    ItemSeed.class);
            if (seedAnn == null) continue;

            String key = seedAnn.key();
            if (key == null || key.isBlank()) continue;

            ItemID seedId = ItemID.fromString(key);

            // Skip if already handled by the old @Item.Seed system
            if (seedItems.stream().anyMatch(i -> seedId.equals(i.iid()))) {
                // But still process @Seed.Frame and @Seed.Word fields
                Item existing = seedItems.stream()
                        .filter(i -> seedId.equals(i.iid()))
                        .findFirst().orElse(null);
                if (existing != null) {
                    frameCount += processSeedFrameFields(clazz, existing);
                    wordCount += processSeedWordFields(clazz, seedId, lexemePredicate);
                }
                continue;
            }

            // Create new Sememe from @Seed annotation
            Sememe sememe = new Sememe(key);

            // Process slots
            for (String slotKey : seedAnn.slots()) {
                sememe.slot(slotKey);
            }

            // Process @Seed.Frame fields and methods
            frameCount += processSeedFrameFields(clazz, sememe);

            // Store the sememe
            if (storeItem(sememe)) {
                seedItems.add(sememe);
                seedCount++;
            }

            // Process @Seed.Word fields (LEXEME frames on Language items)
            wordCount += processSeedWordFields(clazz, seedId, lexemePredicate);
        }

        if (seedCount > 0 || frameCount > 0 || wordCount > 0) {
            logger.info("@Seed scanner: {} new seeds, {} frames, {} words", seedCount, frameCount, wordCount);
        }
    }

    /**
     * Process @Seed.Frame annotated fields and methods on a seed class.
     * Creates frames on the seed item.
     */
    private int processSeedFrameFields(Class<?> clazz, Item seedItem) {
        int count = 0;

        // Fields
        for (Field field : clazz.getDeclaredFields()) {
            ItemSeed.Frame frameAnn = field.getAnnotation(
                    ItemSeed.Frame.class);
            if (frameAnn == null) continue;

            try {
                field.setAccessible(true);
                Object value = field.get(null);
                if (value != null) {
                    addSeedFrame(seedItem, frameAnn.key(), value);
                    count++;
                }
            } catch (Exception e) {
                logger.warn("Failed to read @Seed.Frame field {}.{}: {}",
                        clazz.getSimpleName(), field.getName(), e.getMessage());
            }
        }

        // Methods
        for (java.lang.reflect.Method method : clazz.getDeclaredMethods()) {
            ItemSeed.Frame frameAnn = method.getAnnotation(
                    ItemSeed.Frame.class);
            if (frameAnn == null) continue;
            if (!Modifier.isStatic(method.getModifiers())) continue;

            try {
                method.setAccessible(true);
                Object value = method.invoke(null);
                if (value != null) {
                    addSeedFrame(seedItem, frameAnn.key(), value);
                    count++;
                }
            } catch (Exception e) {
                logger.warn("Failed to call @Seed.Frame method {}.{}(): {}",
                        clazz.getSimpleName(), method.getName(), e.getMessage());
            }
        }

        return count;
    }

    /**
     * Add a frame to a seed item from annotation data.
     */
    private void addSeedFrame(Item seedItem, String[] keyParts, Object value) {
        if (keyParts == null || keyParts.length == 0) return;

        // Build FrameKey from key parts
        ItemID head = ItemID.fromString(keyParts[0]);
        Object[] qualifiers = new Object[keyParts.length - 1];
        for (int i = 1; i < keyParts.length; i++) {
            qualifiers[i - 1] = ItemID.fromString(keyParts[i]);
        }
        FrameKey key = FrameKey.of(head, qualifiers);

        // Encode value to CBOR
        byte[] encoded;
        if (value instanceof String s) {
            encoded = com.upokecenter.cbor.CBORObject.FromString(s).EncodeToBytes();
        } else if (value instanceof Long l) {
            encoded = com.upokecenter.cbor.CBORObject.FromObject(l).EncodeToBytes();
        } else if (value instanceof Integer i) {
            encoded = com.upokecenter.cbor.CBORObject.FromObject(i).EncodeToBytes();
        } else if (value instanceof Canonical c) {
            encoded = c.encodeBinary(Canonical.Scope.RECORD);
        } else {
            logger.warn("Unsupported @Seed.Frame value type: {}", value.getClass().getSimpleName());
            return;
        }

        ContentID cid = ContentID.of(encoded);
        Literal literal = new Literal(Literal.TYPE_CBOR, encoded);
        FrameBody body = new FrameBody(head,
                List.of(new Binding(ThematicRole.Topic.IID, literal)));

        Frame frame = new Frame(key, head, body, body.hash(), false);
        seedItem.frames().add(frame);
    }

    /**
     * Process @Seed.Word annotated fields. Creates LEXEME frames on Language items.
     */
    private int processSeedWordFields(Class<?> clazz, ItemID sememeId, ItemID lexemePredicate) {
        int count = 0;

        // Group Language items by key for lookup
        Map<String, Item> languageItems = new HashMap<>();
        for (Item seed : seedItems) {
            if (seed instanceof Language lang && lang.languageCode() != null) {
                languageItems.put(lang.languageCode(), lang);
                // Map common 2-letter codes
                if ("eng".equals(lang.languageCode())) {
                    languageItems.put("en", lang);
                }
            }
        }

        for (Field field : clazz.getDeclaredFields()) {
            ItemSeed.Word wordAnn = field.getAnnotation(
                    ItemSeed.Word.class);
            if (wordAnn == null) continue;
            if (field.getType() != String.class) continue;

            try {
                field.setAccessible(true);
                String surface = (String) field.get(null);
                if (surface == null || surface.isBlank()) continue;

                // Resolve language from KEY
                String langKey = wordAnn.lang();
                // Try to find Language item by KEY match
                Item langItem = null;
                for (Item seed : seedItems) {
                    if (seed instanceof Language lang) {
                        String langItemKey = "cg:language/" + lang.languageCode();
                        if (langKey.equals(langItemKey) || langKey.equals(lang.languageCode())) {
                            langItem = seed;
                            break;
                        }
                    }
                }
                if (langItem == null) {
                    logger.debug("No Language item for key '{}', skipping word '{}'", langKey, surface);
                    continue;
                }

                // Build compound FrameKey: (LEXEME, sememeId, POS, features...)
                ItemID posId = ItemID.fromString(wordAnn.pos());
                Object[] qualifiers = new Object[2 + wordAnn.features().length];
                qualifiers[0] = sememeId;
                qualifiers[1] = posId;
                for (int i = 0; i < wordAnn.features().length; i++) {
                    qualifiers[i + 2] = ItemID.fromString(wordAnn.features()[i]);
                }
                FrameKey key = FrameKey.of(lexemePredicate, qualifiers);

                // Build frame body with THEME → surface form
                byte[] surfaceBytes = com.upokecenter.cbor.CBORObject.FromString(surface).EncodeToBytes();
                Literal surfaceLiteral = new Literal(Literal.TYPE_TEXT, surfaceBytes);
                FrameBody body = new FrameBody(lexemePredicate,
                        List.of(new Binding(ThematicRole.Theme.IID, surfaceLiteral)));

                Frame frame = new Frame(key, lexemePredicate, body, body.hash(), false);
                langItem.frames().add(frame);
                count++;
            } catch (Exception e) {
                logger.warn("Failed to process @Seed.Word field {}.{}: {}",
                        clazz.getSimpleName(), field.getName(), e.getMessage());
            }
        }

        return count;
    }

    /**
     * Create LEXEME frames on Language items from seed Sememe word declarations.
     *
     * <p>Iterates all seed items, collects their {@link Sememe.LexemeDeclaration}s,
     * and creates a LEXEME frame on the appropriate Language item for each one.
     */
    private void createLexemeFrames() {
        ItemID lexemePredicate = ItemID.fromString(CoreVocabulary.Lexeme.KEY);

        // Group declarations by language code → Language item
        Map<String, Item> languageItems = new HashMap<>();
        for (Item seed : seedItems) {
            if (seed instanceof Language lang && lang.languageCode() != null) {
                languageItems.put(lang.languageCode(), lang);
            }
        }

        // Map 2-letter codes to 3-letter
        languageItems.putIfAbsent("en", languageItems.get("eng"));

        int count = 0;
        for (Item seed : seedItems) {
            if (!(seed instanceof Sememe sememe)) continue;
            var declarations = sememe.lexemeDeclarations();
            if (declarations == null || declarations.isEmpty()) continue;

            for (Sememe.LexemeDeclaration decl : declarations) {
                // Resolve language
                String langCode = decl.lang().equals("en") ? "eng" : decl.lang();
                Item langItem = languageItems.get(langCode);
                if (langItem == null) {
                    logger.warn("No Language item for code '{}', skipping lexeme '{}'", langCode, decl.surface());
                    continue;
                }

                // Build compound FrameKey: (LEXEME, sememeId, POS, form)
                // form can be null due to circular clinit between Sememe and GrammaticalFeature
                ItemID formId = decl.form() != null ? decl.form().iid()
                        : ItemID.fromString(GrammaticalFeature.Lemma.KEY);
                FrameKey key = FrameKey.of(lexemePredicate, sememe.iid(), decl.pos(), formId);

                // Build frame body with THEME → surface form (CBOR-encoded text)
                byte[] surfaceBytes = com.upokecenter.cbor.CBORObject.FromString(decl.surface()).EncodeToBytes();
                Literal surfaceLiteral = new Literal(Literal.TYPE_TEXT, surfaceBytes);
                FrameBody body = new FrameBody(lexemePredicate,
                        List.of(new Binding(ThematicRole.Theme.IID, surfaceLiteral)));

                // Create and add frame to the Language item
                Frame frame = new Frame(key, lexemePredicate, body, body.hash(), false);
                langItem.frames().add(frame);
                count++;
            }
        }
        logger.info("Created {} LEXEME frames on Language items", count);
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
        Item seedItem = seedItems.stream()
                .filter(i -> typeId.equals(i.iid()))
                .findFirst()
                .orElse(null);

        // Attach display metadata from @Implements
        Implements impl = clazz.getAnnotation(Implements.class);
        if (impl != null && seedItem != null) {
            attachTypePresentation(seedItem, clazz, impl, key);
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
        Implements annotation = type.getAnnotation(Implements.class);
        if (annotation == null || annotation.value().isBlank()) return;

        ItemID typeId = ItemID.fromString(annotation.value());

        // Value types may not have seed items, just create IMPLEMENTED_BY relation
        storeFrameBody(FrameBody.of(
                CoreVocabulary.ImplementedBy.IID,
                typeId,
                Map.of(ThematicRole.Goal.IID, Literal.ofJavaClass(type))));
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
    private void attachTypePresentation(Item typeItem, Class<?> typeClass, Implements annotation, String key) {
        // Start with display fields from @Implements annotation
        SurfaceTemplateComponent stc = SurfaceTemplateComponent.fromImplements(annotation);
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
        PresentationConfig presConfig = PresentationConfig.of(
                "📦", 0x78788C, "sphere");
        byte[] presBytes = presConfig.encodeBinary(Canonical.Scope.RECORD);
        Literal presLiteral = new Literal(Literal.TYPE_CBOR, presBytes);

        FrameKey presKey = FrameKey.of(ThematicRole.Presentation.IID);
        FrameBody presBody = new FrameBody(ThematicRole.Presentation.IID,
                List.of(new Binding(ThematicRole.Topic.IID, presLiteral)));
        Frame presFrame = new Frame(presKey, ThematicRole.Presentation.IID,
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

            FrameKey key = FrameKey.of(ItemID.fromString(SememeGloss.KEY), iso3);

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
                CoreVocabulary.ImplementedBy.IID,
                typeId,
                Map.of(ThematicRole.Goal.IID, Literal.ofJavaClass(implementingClass)));

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
                CoreVocabulary.Title.IID,
                itemId,
                Map.of(ThematicRole.Goal.IID, Literal.ofText(key)));
    }

    private FrameBody createInstanceOfRelation(Item item) {
        String key = readKey(item.getClass());
        if (key == null || key.isBlank()) return null;

        ItemID typeId = ItemID.fromString(key);
        ItemID instanceId = item.iid();

        // Don't create relation if instance IS the type
        if (instanceId.equals(typeId)) return null;

        return FrameBody.of(
                LexicalVocabulary.InstanceOf.IID,
                instanceId,
                Map.of(ThematicRole.Goal.IID, BindingTarget.iid(typeId)));
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

    private static String extractKeyFromItem(Item item) {
        if (item instanceof Sememe sememe) {
            return sememe.canonicalKey();
        }
        return null;
    }
}
