package dev.everydaythings.graph.item.component;

import dev.everydaythings.graph.item.DisplayInfo;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.action.ActionContext;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.user.Signer;
import dev.everydaythings.graph.language.VerbSememe;
import dev.everydaythings.graph.runtime.Librarian;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * A ComponentType defines a type of component that can appear in Items.
 *
 * <p>ComponentTypes are Items, enabling:
 * <ul>
 *   <li>Type discovery via graph queries (all component types)</li>
 *   <li>Type metadata storage (name, description, implementing class)</li>
 *   <li>Third-party component packs</li>
 * </ul>
 *
 * <p>Component classes declare their type via {@code @Type("cg:type/xxx")}
 * which references a ComponentType seed item.
 *
 * <p>Similar to {@link dev.everydaythings.graph.value.ValueType} but for components.
 *
 * @see Component
 * @see Type
 */
@Log4j2
@Type(value = ComponentType.KEY, glyph = "🏗️")
public class ComponentType extends Item {

    // ==================================================================================
    // TYPE DEFINITION
    // ==================================================================================

    public static final String KEY = "cg:type/component-type";

    // ==================================================================================
    // STATIC TYPE NAME REGISTRY
    // ==================================================================================

    /**
     * Maps ItemID → readable type name for all registered component types.
     *
     * <p>This is needed because:
     * <ol>
     *   <li>ItemID is a hash of the original type key (e.g., "cg:type/expression")</li>
     *   <li>After hydration from storage, the canonicalKey field is null</li>
     *   <li>We can't reverse the hash to get the original key</li>
     * </ol>
     *
     * <p>SeedStore populates this registry when creating ComponentType seeds.
     */
    private static final Map<ItemID, String> TYPE_NAME_REGISTRY =
            new ConcurrentHashMap<>();

    /**
     * Register a type name for an ItemID.
     */
    public static void registerTypeName(ItemID iid, String name) {
        TYPE_NAME_REGISTRY.put(iid, name);
    }

    /**
     * Look up a registered type name by ItemID.
     */
    public static String getRegisteredTypeName(ItemID iid) {
        return TYPE_NAME_REGISTRY.get(iid);
    }

    // ==================================================================================
    // INSTANCE FIELDS
    // ==================================================================================

    /** The canonical type key (e.g., "cg:type/expression") */
    @Getter
    @Frame(handle = "key")
    private String canonicalKey;

    /** Human-readable name */
    @Getter
    @Frame
    private String name;

    /** The implementing Java class name (for debugging/introspection) */
    @Getter
    @Frame(handle = "impl")
    private String implementingClass;

    // ==================================================================================
    // CONSTRUCTORS
    // ==================================================================================

    /**
     * Create a seed component type (deterministic IID from key).
     */
    public ComponentType(String canonicalKey, String name, Class<?> implClass) {
        super(ItemID.fromString(canonicalKey));
        this.canonicalKey = canonicalKey;
        this.name = name;
        this.implementingClass = implClass != null ? implClass.getName() : null;
    }

    /**
     * Create a component type with a librarian (for runtime creation).
     */
    public ComponentType(Librarian librarian, String canonicalKey, String name,
                         Class<?> implClass) {
        super(librarian, ItemID.fromString(canonicalKey));
        this.canonicalKey = canonicalKey;
        this.name = name;
        this.implementingClass = implClass != null ? implClass.getName() : null;
    }

    /**
     * Protected constructor for type ID seed creation.
     * Used when creating a seed from just the type annotation.
     */
    protected ComponentType(ItemID typeId) {
        super(typeId);
        this.canonicalKey = null;
        this.name = null;
        this.implementingClass = null;
    }

    /**
     * Hydration constructor - reconstructs a ComponentType from a stored manifest.
     *
     * <p>Fields are bound via reflection in the base class hydrate() method.
     */
    @SuppressWarnings("unused")  // Used via reflection for hydration
    protected ComponentType(Librarian librarian, Manifest manifest) {
        super(librarian, manifest);
        // Fields are set by bindFieldsFromTable() via reflection during super() call
        // Do NOT assign values here - it would overwrite what hydration set!
    }

    /**
     * Create and commit a component type.
     */
    public static ComponentType create(Librarian librarian, Signer signer,
                                       String canonicalKey, String name,
                                       Class<?> implClass) {
        ComponentType componentType = new ComponentType(librarian, canonicalKey, name, implClass);
        componentType.commit(signer);
        return componentType;
    }

    // ==================================================================================
    // VERBS
    // ==================================================================================

    /**
     * Create a new instance of this component type.
     *
     * <p>This is the CREATE verb on a ComponentType. When the user types
     * "create chess", Eval dispatches CREATE on the Chess ComponentType,
     * which instantiates a new Chess component via its static create() factory
     * or no-arg constructor.
     *
     * <p>This shadows {@link Item#actionNew(ActionContext)} since both use the
     * same Sememe key. The verb system dispatches by Sememe ID, and the subclass
     * verb takes precedence.
     *
     * @param ctx The action context
     * @return A new component instance
     */
    @Verb(value = VerbSememe.Create.KEY, doc = "Create a new instance of this component type")
    public Object createComponent(
            ActionContext ctx,
            @Param(value = "name", required = false, role = "NAME") String name,
            @Param(value = "players", required = false, role = "COMITATIVE") List players,
            @Param(value = "source", required = false, role = "SOURCE") String source) {

        Class<?> implClass = resolveClass()
                .orElseThrow(() -> new IllegalStateException(
                        "No implementing class for component type: " + displayToken()));

        Map<String, Object> params = new LinkedHashMap<>();
        if (name != null) params.put("name", name);
        if (players != null) params.put("players", players);
        if (source != null) params.put("source", source);

        return params.isEmpty()
                ? instantiateComponent(implClass)
                : instantiateWithParams(implClass, params);
    }

    /**
     * Resolve the implementing Java class for this component type.
     *
     * @return The class, or empty if not resolvable
     */
    public Optional<Class<?>> resolveClass() {
        if (implementingClass == null || implementingClass.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Class.forName(implementingClass));
        } catch (ClassNotFoundException e) {
            logger.debug("Could not resolve component class '{}': {}", implementingClass, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Instantiate a component from its class.
     *
     * <p>Tries in order:
     * <ol>
     *   <li>Static {@code create()} factory method (preferred — handles component-specific setup)</li>
     *   <li>No-arg constructor</li>
     * </ol>
     */
    public static Object instantiateComponent(Class<?> compClass) {
        // Try static create() factory first
        try {
            Method createMethod = compClass.getDeclaredMethod("create");
            if (Modifier.isStatic(createMethod.getModifiers())) {
                createMethod.setAccessible(true);
                return createMethod.invoke(null);
            }
        } catch (NoSuchMethodException ignored) {
            // No create() method, try constructor
        } catch (Exception e) {
            logger.debug("create() factory failed for {}, trying constructor: {}", compClass.getSimpleName(), e.getMessage());
        }

        // Fall back to no-arg constructor
        try {
            var ctor = compClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Cannot instantiate component " + compClass.getSimpleName()
                            + ": no create() factory or no-arg constructor", e);
        }
    }

    /**
     * Instantiate a component with parameters.
     *
     * <p>Tries in order:
     * <ol>
     *   <li>Static {@code create(Map)} factory method (preferred — handles params)</li>
     *   <li>Falls back to {@link #instantiateComponent(Class)} (ignores params)</li>
     * </ol>
     */
    @SuppressWarnings("unchecked")
    public static Object instantiateWithParams(Class<?> compClass, Map<String, Object> params) {
        // Try static create(Map) factory
        try {
            Method createMethod = compClass.getDeclaredMethod("create", Map.class);
            if (Modifier.isStatic(createMethod.getModifiers())) {
                createMethod.setAccessible(true);
                return createMethod.invoke(null, params);
            }
        } catch (NoSuchMethodException ignored) {
            // No create(Map) method, fall back
        } catch (Exception e) {
            logger.debug("create(Map) factory failed for {}, falling back: {}",
                    compClass.getSimpleName(), e.getMessage());
        }

        // Fall back to parameterless instantiation
        return instantiateComponent(compClass);
    }

    // ==================================================================================
    // DISPLAY INFO
    // ==================================================================================

    @Override
    protected String findTypeName() {
        String displayName = resolveDisplayName();
        if (displayName != null && !displayName.equals("Component")) return displayName;
        return super.findTypeName();
    }

    @Override
    public DisplayInfo displayInfo() {
        // Delegate to parent's resolution, override name with resolved display name
        DisplayInfo base = super.displayInfo();
        String displayName = resolveDisplayName();
        return base.withName(displayName);
    }

    /**
     * Resolve the display name for this component type.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Instance name field (if hydration worked)</li>
     *   <li>Extract from canonicalKey field</li>
     *   <li>Look up in static registry (populated by SeedStore)</li>
     *   <li>"Component" as last resort</li>
     * </ol>
     */
    private String resolveDisplayName() {
        // Try instance name first
        if (name != null && !name.isBlank()) {
            return name;
        }

        // Try canonicalKey
        String extracted = extractNameFromKey(canonicalKey);
        if (extracted != null) {
            return extracted;
        }

        // Try static registry (populated by SeedStore during bootstrap)
        if (iid() != null) {
            String registered = getRegisteredTypeName(iid());
            if (registered != null) {
                return registered;
            }
        }

        return "Component";
    }

    /**
     * Extract a readable name from a type key string.
     */
    private String extractNameFromKey(String key) {
        if (key == null || key.isBlank()) return null;

        // For keys like "cg:type/expression", extract "expression" and capitalize
        int lastSlash = key.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < key.length() - 1) {
            String shortName = key.substring(lastSlash + 1);
            // Handle hyphenated names like "rocks-item-store" -> "RocksItemStore"
            return capitalizeTypeName(shortName);
        }

        // For keys like "cg.value:decimal", extract after colon
        int lastColon = key.lastIndexOf(':');
        if (lastColon >= 0 && lastColon < key.length() - 1) {
            String shortName = key.substring(lastColon + 1);
            return capitalizeTypeName(shortName);
        }

        return null;
    }

    /**
     * Capitalize a type name, handling hyphens.
     */
    private String capitalizeTypeName(String name) {
        if (name == null || name.isEmpty()) return name;

        // Handle hyphenated names: "rocks-item-store" -> "RocksItemStore"
        if (name.contains("-")) {
            StringBuilder sb = new StringBuilder();
            for (String part : name.split("-")) {
                if (!part.isEmpty()) {
                    sb.append(Character.toUpperCase(part.charAt(0)));
                    if (part.length() > 1) {
                        sb.append(part.substring(1));
                    }
                }
            }
            return sb.toString();
        }

        // Simple capitalize
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    @Override
    public String displayToken() {
        return displayInfo().displayName();
    }

    @Override
    public String displaySubtitle() {
        // Show implementing class if available
        if (implementingClass != null && !implementingClass.isBlank()) {
            int lastDot = implementingClass.lastIndexOf('.');
            if (lastDot >= 0) {
                return implementingClass.substring(lastDot + 1);
            }
            return implementingClass;
        }
        // Fallback to type key
        if (canonicalKey != null) {
            return canonicalKey;
        }
        if (iid() != null) {
            return iid().encodeText();
        }
        return "Component Type";
    }

    @Override
    public Stream<TokenEntry> extractTokens() {
        List<TokenEntry> tokens = new ArrayList<>();

        // Primary: the human-readable name
        if (name != null && !name.isBlank()) {
            tokens.add(new TokenEntry(name, 1.0f));
        }

        // Also index the canonical key (e.g., "cg:type/expression")
        if (canonicalKey != null && !canonicalKey.isBlank()) {
            tokens.add(new TokenEntry(canonicalKey, 0.9f));
            // And the short name part
            int slashIdx = canonicalKey.lastIndexOf('/');
            if (slashIdx >= 0 && slashIdx < canonicalKey.length() - 1) {
                String shortName = canonicalKey.substring(slashIdx + 1);
                if (!shortName.equalsIgnoreCase(name)) {
                    tokens.add(new TokenEntry(shortName, 0.8f));
                }
            }
        }

        return tokens.stream();
    }

    @Override
    public String toString() {
        return displayInfo().displayName();
    }
}
