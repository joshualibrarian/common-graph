package dev.everydaythings.graph.value.address;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.Type;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Librarian;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Java class address space: fully qualified class names.
 *
 * <p>Format: {@code package.name.ClassName} or {@code package.name.Outer$Inner}
 *
 * <p>Authority model: Java package namespace conventions.
 * Resolution loads the class via the classloader.
 *
 * <p>Examples:
 * <ul>
 *   <li>{@code java.lang.String}</li>
 *   <li>{@code dev.everydaythings.graph.value.Dimension}</li>
 *   <li>{@code com.example.Outer$Inner}</li>
 * </ul>
 *
 * <p>Used in relations to reference Java implementations:
 * <pre>{@code
 * (cg:type/dimension) —[implementedBy]→ "dev.everydaythings.graph.value.Dimension"
 * }</pre>
 */
@Type(value = JavaClass.KEY, glyph = "☕")
public class JavaClass extends AddressSpace {

    // ==================================================================================
    // TYPE DEFINITION
    // ==================================================================================

    public static final String KEY = "cg.address:java-class";


    /** The address space item - use for predicates and type references. */
    public static final JavaClass ITEM = new JavaClass(KEY, "Java Class");

    // ==================================================================================
    // SYNTAX
    // ==================================================================================

    // Java FQCN: package parts (lowercase by convention) + class name
    // Supports inner classes with $ separator
    private static final Pattern PATTERN = Pattern.compile(
            "^([a-z_][a-z0-9_]*(\\.[a-z_][a-z0-9_]*)*\\.)?[A-Z_$][A-Za-z0-9_$]*(\\$[A-Za-z0-9_$]+)*$"
    );

    // ==================================================================================
    // CONSTRUCTORS
    // ==================================================================================

    /**
     * Type seed constructor - creates a minimal JavaClass for use as type seed.
     */
    @SuppressWarnings("unused")  // Used via reflection by SeedStore
    protected JavaClass(ItemID typeId) {
        super(typeId);
    }

    private JavaClass(String key, String name) {
        super(key, name);
    }

    /**
     * Hydration constructor - reconstructs a JavaClass from a stored manifest.
     */
    @SuppressWarnings("unused")  // Used via reflection for hydration
    private JavaClass(Librarian librarian, Manifest manifest) {
        super(librarian, manifest);
    }

    // ==================================================================================
    // ADDRESS SPACE IMPLEMENTATION
    // ==================================================================================

    @Override
    public Pattern syntaxPattern() {
        return PATTERN;
    }

    @Override
    public Optional<ParsedAddress> parse(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String trimmed = raw.trim();
        if (!PATTERN.matcher(trimmed).matches()) return Optional.empty();
        return Optional.of(new Parsed(trimmed));
    }

    @Override
    public int score(String raw) {
        if (raw == null || raw.isBlank()) return -1;
        if (!PATTERN.matcher(raw.trim()).matches()) return -1;
        // Score based on package depth (more dots = more confident it's a FQCN)
        int dots = (int) raw.chars().filter(ch -> ch == '.').count();
        return 5 + dots * 3;
    }

    // ==================================================================================
    // PARSED ADDRESS
    // ==================================================================================

    /**
     * A parsed Java class address.
     */
    public record Parsed(String className) implements ParsedAddress {

        public Parsed {
            Objects.requireNonNull(className, "className");
        }

        @Override
        public AddressSpace space() {
            return ITEM;
        }

        @Override
        public String raw() {
            return className;
        }

        @Override
        public String canonical() {
            return className; // Already canonical
        }

        /**
         * Get the simple class name (without package).
         */
        public String simpleName() {
            int lastDot = className.lastIndexOf('.');
            String name = lastDot < 0 ? className : className.substring(lastDot + 1);
            // Handle inner classes: Outer$Inner -> Inner
            int dollar = name.lastIndexOf('$');
            return dollar < 0 ? name : name.substring(dollar + 1);
        }

        /**
         * Get the package name.
         */
        public String packageName() {
            int lastDot = className.lastIndexOf('.');
            return lastDot < 0 ? "" : className.substring(0, lastDot);
        }

        /**
         * Check if this is an inner class.
         */
        public boolean isInnerClass() {
            return className.contains("$");
        }

        /**
         * Attempt to load the class.
         *
         * @return The loaded class, or empty if not found
         */
        public Optional<Class<?>> loadClass() {
            try {
                return Optional.of(Class.forName(className, false,
                        Thread.currentThread().getContextClassLoader()));
            } catch (ClassNotFoundException e) {
                return Optional.empty();
            }
        }

        /**
         * Load the class, throwing if not found.
         */
        public Class<?> loadClassOrThrow() {
            return loadClass().orElseThrow(() ->
                    new IllegalStateException("Class not found: " + className));
        }
    }

    // ==================================================================================
    // CONVENIENCE FACTORY
    // ==================================================================================

    /**
     * Create a parsed address from a Class object.
     */
    public static Parsed of(Class<?> clazz) {
        return new Parsed(clazz.getName());
    }
}
