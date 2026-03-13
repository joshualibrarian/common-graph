package dev.everydaythings.graph.dispatch;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.ThematicRole;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Invokes verbs on items and components.
 *
 * <p>VerbInvoker handles verb method invocation for the Vocabulary/Verb
 * dispatch system. Works with {@link VerbEntry} instances that use
 * Sememe IDs for language-agnostic identification.
 *
 * <p>Usage:
 * <pre>{@code
 * VerbInvoker invoker = new VerbInvoker();
 *
 * // Invoke a verb
 * ActionResult result = invoker.invoke(verbEntry, context, args);
 *
 * // Invoke with string arguments (CLI)
 * ActionResult result = invoker.invokeWithStrings(verbEntry, context, stringArgs);
 * }</pre>
 */
public class VerbInvoker {

    /**
     * Invoke a verb.
     *
     * @param verb The verb entry to invoke
     * @param ctx The action context
     * @param args The arguments (excluding ActionContext)
     * @return The invocation result
     */
    public ActionResult invoke(
            VerbEntry verb,
            ActionContext ctx,
            Object... args) {

        Objects.requireNonNull(verb, "verb");
        Objects.requireNonNull(ctx, "ctx");

        try {
            // Get target object from verb entry
            Object target = verb.target();
            if (target == null) {
                return ActionResult.failure(
                        new IllegalStateException("Verb has no target: " + verb.sememeKey()));
            }

            // Build argument array
            Object[] invokeArgs = buildInvokeArgs(verb, ctx, args);

            // Invoke
            Method method = verb.method();
            Object result = method.invoke(target, invokeArgs);

            return ActionResult.success(result);

        } catch (InvocationTargetException e) {
            // Unwrap the actual exception
            return ActionResult.failure(e.getCause() != null ? e.getCause() : e);
        } catch (Exception e) {
            return ActionResult.failure(e);
        }
    }

    /**
     * Invoke a verb with string arguments (for CLI).
     *
     * <p>Converts string arguments to the expected parameter types.
     *
     * @param verb The verb entry to invoke
     * @param ctx The action context
     * @param stringArgs The string arguments
     * @return The invocation result
     */
    public ActionResult invokeWithStrings(
            VerbEntry verb,
            ActionContext ctx,
            List<String> stringArgs) {

        Objects.requireNonNull(verb, "verb");
        Objects.requireNonNull(stringArgs, "stringArgs");

        try {
            // Convert string args to typed args
            Object[] typedArgs = convertArgs(verb, stringArgs);
            return invoke(verb, ctx, typedArgs);
        } catch (Exception e) {
            return ActionResult.failure(e);
        }
    }

    /**
     * Invoke a verb with typed bindings from a SemanticFrame.
     *
     * <p>Matches frame bindings to method parameters by ThematicRole,
     * with positional fallback for overflow values. This is the
     * preferred dispatch path for frame-based evaluation.
     *
     * <p>Matching order for each ParamSpec:
     * <ol>
     *   <li>Role match: if param has a role and bindings contains that role</li>
     *   <li>Positional fallback: consume next value from overflow list</li>
     *   <li>Default value: if optional with defaultValue</li>
     *   <li>Null: if optional with no default</li>
     * </ol>
     *
     * @param verb     The verb entry to invoke
     * @param ctx      The action context
     * @param bindings Thematic role to value (Items or literals)
     * @param overflow Positional overflow values (unmatched literals, etc.)
     * @return The invocation result
     */
    public ActionResult invokeWithBindings(
            VerbEntry verb,
            ActionContext ctx,
            Map<ItemID, Object> bindings,
            List<Object> overflow) {

        Objects.requireNonNull(verb, "verb");
        Objects.requireNonNull(ctx, "ctx");

        try {
            List<ParamSpec> params = verb.params();
            Object[] typedArgs = new Object[params.size()];
            int overflowIndex = 0;

            for (int i = 0; i < params.size(); i++) {
                ParamSpec param = params.get(i);

                // 1. Role match — convert param role name to ItemID via ThematicRole bridge
                Object matched = null;
                boolean found = false;
                if (param.role() != null) {
                    ItemID roleId = null;
                    try {
                        roleId = ThematicRole.fromName(param.role()).iid();
                    } catch (IllegalArgumentException e) {
                        // unknown role string — ignore
                    }
                    if (roleId != null && bindings.containsKey(roleId)) {
                        matched = bindings.get(roleId);
                        found = true;
                    }
                }

                // 2. Positional fallback
                if (!found && overflowIndex < overflow.size()) {
                    matched = overflow.get(overflowIndex++);
                    found = true;
                }

                // 3. Default value
                if (!found && !param.required() && param.defaultValue() != null) {
                    typedArgs[i] = convertArg(param.defaultValue(), param.type());
                    continue;
                }

                // 4. Null (optional, no default)
                if (!found && !param.required()) {
                    typedArgs[i] = null;
                    continue;
                }

                if (!found) {
                    throw new IllegalArgumentException("Missing required argument: " + param.name());
                }

                // Coerce matched value to target type
                typedArgs[i] = coerce(matched, param.type());
            }

            return invoke(verb, ctx, typedArgs);
        } catch (Exception e) {
            return ActionResult.failure(e);
        }
    }

    /**
     * Coerce a value to the target type with intelligent conversion.
     *
     * <ul>
     *   <li>Already the right type → pass through</li>
     *   <li>List passthrough when target is List</li>
     *   <li>Single value → List.of(value) when target is List</li>
     *   <li>Item → ItemID (extract .iid()), or Item → String (.displayToken())</li>
     *   <li>String → Path, enum, primitives (reuse convertArg logic)</li>
     *   <li>Number coercion (Integer → long, etc.)</li>
     * </ul>
     */
    private Object coerce(Object value, Class<?> targetType) {
        if (value == null) return null;

        // Already the right type
        if (targetType.isInstance(value)) return value;

        // List coercion: target is List
        if (targetType == List.class) {
            if (value instanceof List<?> list) {
                return list;  // pass through — caller declared raw List, don't stringify
            }
            // Single value → singleton list
            return List.of(value);
        }

        // Item → ItemID
        if (value instanceof Item item && targetType == ItemID.class) {
            return item.iid();
        }

        // Item → String
        if (value instanceof Item item && targetType == String.class) {
            return item.displayToken();
        }

        // String → target type (reuse existing conversion)
        if (value instanceof String s) {
            // String → Path
            if (targetType == Path.class) {
                return Path.of(s);
            }
            return convertArg(s, targetType);
        }

        // Number coercion
        if (value instanceof Number num) {
            if (targetType == int.class || targetType == Integer.class) return num.intValue();
            if (targetType == long.class || targetType == Long.class) return num.longValue();
            if (targetType == double.class || targetType == Double.class) return num.doubleValue();
            if (targetType == float.class || targetType == Float.class) return num.floatValue();
            if (targetType == short.class || targetType == Short.class) return num.shortValue();
            if (targetType == byte.class || targetType == Byte.class) return num.byteValue();
            if (targetType == String.class) return num.toString();
        }

        // Fall back to toString → convertArg for anything else
        return convertArg(String.valueOf(value), targetType);
    }

    /**
     * Coerce each element of a list to its string representation.
     * Items are converted via displayToken(), strings pass through.
     */
    private List<String> coerceListElements(List<?> list) {
        List<String> result = new ArrayList<>(list.size());
        for (Object element : list) {
            result.add(coerceToString(element));
        }
        return result;
    }

    /**
     * Coerce a single value to its string representation.
     */
    private String coerceToString(Object value) {
        if (value instanceof Item item) return item.displayToken();
        if (value instanceof String s) return s;
        return String.valueOf(value);
    }

    /**
     * Build the argument array for method invocation.
     *
     * <p>Prepends ActionContext if the method expects it.
     */
    private Object[] buildInvokeArgs(VerbEntry verb, ActionContext ctx, Object[] args) {
        Method method = verb.method();
        Class<?>[] paramTypes = method.getParameterTypes();

        // Check if first param is ActionContext
        boolean hasContext = paramTypes.length > 0 &&
                ActionContext.class.isAssignableFrom(paramTypes[0]);

        if (hasContext) {
            // Prepend context
            Object[] result = new Object[args.length + 1];
            result[0] = ctx;
            System.arraycopy(args, 0, result, 1, args.length);
            return result;
        } else {
            return args;
        }
    }

    /**
     * Convert string arguments to typed arguments based on parameter specs.
     */
    private Object[] convertArgs(VerbEntry verb, List<String> stringArgs) {
        List<ParamSpec> params = verb.params();

        // Count required params
        int required = (int) params.stream().filter(ParamSpec::required).count();

        // Check argument count
        if (stringArgs.size() < required) {
            throw new IllegalArgumentException(
                    "Not enough arguments. Expected " + required + ", got " + stringArgs.size());
        }
        if (stringArgs.size() > params.size()) {
            throw new IllegalArgumentException(
                    "Too many arguments. Expected at most " + params.size() + ", got " + stringArgs.size());
        }

        Object[] result = new Object[params.size()];

        for (int i = 0; i < params.size(); i++) {
            ParamSpec param = params.get(i);

            if (i < stringArgs.size()) {
                // Convert provided argument
                result[i] = convertArg(stringArgs.get(i), param.type());
            } else if (!param.required() && param.defaultValue() != null) {
                // Use default value
                result[i] = convertArg(param.defaultValue(), param.type());
            } else if (!param.required()) {
                // Optional with no default - null
                result[i] = null;
            } else {
                throw new IllegalArgumentException("Missing required argument: " + param.name());
            }
        }

        return result;
    }

    /**
     * Convert a string value to a typed value.
     */
    private Object convertArg(String value, Class<?> type) {
        if (type == String.class) {
            return value;
        } else if (type == ItemID.class) {
            // IID text from Eval (e.g. "iid:baaq...") → parse directly
            return ItemID.parse(value);
        } else if (type == int.class || type == Integer.class) {
            return Integer.parseInt(value);
        } else if (type == long.class || type == Long.class) {
            return Long.parseLong(value);
        } else if (type == double.class || type == Double.class) {
            return Double.parseDouble(value);
        } else if (type == float.class || type == Float.class) {
            return Float.parseFloat(value);
        } else if (type == boolean.class || type == Boolean.class) {
            return Boolean.parseBoolean(value);
        } else if (type == byte.class || type == Byte.class) {
            return Byte.parseByte(value);
        } else if (type == short.class || type == Short.class) {
            return Short.parseShort(value);
        } else if (type.isEnum()) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object enumValue = Enum.valueOf((Class<Enum>) type, value);
            return enumValue;
        } else {
            // For complex types, try a static valueOf or fromString method
            try {
                var valueOf = type.getMethod("valueOf", String.class);
                return valueOf.invoke(null, value);
            } catch (NoSuchMethodException e1) {
                try {
                    var fromString = type.getMethod("fromString", String.class);
                    return fromString.invoke(null, value);
                } catch (NoSuchMethodException e2) {
                    throw new IllegalArgumentException(
                            "Cannot convert '" + value + "' to " + type.getSimpleName() +
                            ": no valueOf or fromString method");
                } catch (Exception e2) {
                    throw new IllegalArgumentException(
                            "Failed to convert '" + value + "' to " + type.getSimpleName(), e2);
                }
            } catch (Exception e1) {
                throw new IllegalArgumentException(
                        "Failed to convert '" + value + "' to " + type.getSimpleName(), e1);
            }
        }
    }
}
