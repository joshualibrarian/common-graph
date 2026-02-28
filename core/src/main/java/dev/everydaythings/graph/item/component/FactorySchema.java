package dev.everydaythings.graph.item.component;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Describes how to create instances of a type via factory methods.
 *
 * <p>Built by {@link CreationScanner} from scanning a class for {@link Factory}
 * annotated methods.
 *
 * @param type      The type this schema describes
 * @param factories Available factory methods, sorted by order
 */
public record FactorySchema(
    Class<?> type,
    List<FactoryOption> factories
) {
    /**
     * Get the primary factory (marked primary=true, or first if none marked).
     */
    public FactoryOption primary() {
        return factories.stream()
            .filter(FactoryOption::primary)
            .findFirst()
            .orElse(factories.isEmpty() ? null : factories.getFirst());
    }

    /**
     * Check if this type has any discoverable factories.
     */
    public boolean hasFactories() {
        return !factories.isEmpty();
    }

    /**
     * Describes a single factory method option.
     *
     * @param label   Human-readable label
     * @param doc     Description/tooltip
     * @param glyph   Icon/emoji
     * @param primary Whether this is the default option
     * @param order   Sort order (lower = earlier)
     * @param method  The actual method to invoke
     * @param params  Parameters with their metadata
     */
    public record FactoryOption(
        String label,
        String doc,
        String glyph,
        boolean primary,
        int order,
        Method method,
        List<ParamSchema> params
    ) implements Comparable<FactoryOption> {

        @Override
        public int compareTo(FactoryOption o) {
            return Integer.compare(this.order, o.order);
        }

        /**
         * Invoke this factory with the given arguments.
         *
         * @param args Arguments matching the parameter list
         * @return The created instance
         * @throws ReflectiveOperationException if invocation fails
         */
        public Object invoke(Object... args) throws ReflectiveOperationException {
            return method.invoke(null, args);
        }
    }

    /**
     * Describes a factory method parameter.
     *
     * @param name         Parameter name
     * @param label        Display label
     * @param doc          Documentation/tooltip
     * @param type         Java type of the parameter
     * @param required     Whether required
     * @param defaultValue Default value as string
     * @param showWhen     Visibility condition
     * @param picker       UI widget type
     * @param enumOptions  Enum constants if type is an enum
     */
    public record ParamSchema(
        String name,
        String label,
        String doc,
        Class<?> type,
        boolean required,
        String defaultValue,
        String showWhen,
        Picker picker,
        List<EnumOption> enumOptions
    ) {
        /**
         * Build from a method parameter.
         */
        public static ParamSchema fromParameter(Parameter param) {
            Param annotation = param.getAnnotation(Param.class);

            String name = param.getName();
            String label = name;
            String doc = "";
            boolean required = true;
            String defaultValue = "";
            String showWhen = "";
            Picker picker = Picker.AUTO;

            if (annotation != null) {
                label = annotation.label().isEmpty() ? name : annotation.label();
                doc = annotation.doc();
                required = annotation.required();
                defaultValue = annotation.defaultValue();
                showWhen = annotation.showWhen();
                picker = annotation.picker();
            }

            // Auto-detect picker based on type
            if (picker == Picker.AUTO) {
                picker = inferPicker(param.getType());
            }

            // Build enum options if applicable
            List<EnumOption> enumOptions = null;
            if (param.getType().isEnum()) {
                enumOptions = buildEnumOptions(param.getType());
            }

            return new ParamSchema(name, label, doc, param.getType(),
                required, defaultValue, showWhen, picker, enumOptions);
        }

        /**
         * Infer picker type from Java type.
         */
        private static Picker inferPicker(Class<?> type) {
            if (type.isEnum()) return Picker.DROPDOWN;
            if (type == Path.class || type == File.class) return Picker.DIRECTORY;
            if (type == boolean.class || type == Boolean.class) return Picker.BOOLEAN;
            if (Number.class.isAssignableFrom(type) || type.isPrimitive() && type != boolean.class) {
                return Picker.NUMBER;
            }
            return Picker.TEXT;
        }

        /**
         * Build enum options from an enum class.
         */
        @SuppressWarnings("unchecked")
        private static List<EnumOption> buildEnumOptions(Class<?> enumClass) {
            if (!enumClass.isEnum()) return List.of();

            return Arrays.stream(enumClass.getEnumConstants())
                .map(e -> {
                    Enum<?> enumConstant = (Enum<?>) e;
                    String name = enumConstant.name();
                    // Use enum name as label (could be enhanced with @Surface annotations later)
                    return new EnumOption(name, name, "");
                })
                .toList();
        }
    }

    /**
     * Represents an enum constant option for dropdowns.
     *
     * @param name  Enum constant name
     * @param label Display label
     * @param doc   Optional documentation
     */
    public record EnumOption(
        String name,
        String label,
        String doc
    ) {}
}
