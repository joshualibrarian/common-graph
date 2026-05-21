package dev.everydaythings.graph.item;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.network.IpAddress;
import dev.everydaythings.graph.value.Value;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.Optional;

/**
 * BodyBinder — populates {@code @Seed.Property}-annotated instance
 * fields on a Java instance from a {@link Body}'s bindings.
 *
 * <p>The companion to the static {@code @Seed.Property} path in
 * {@link SeedProcessor}: where SeedProcessor walks static fields to
 * build seed manifests at bootstrap, BodyBinder walks instance fields
 * to populate per-instance values at construction.  Same annotation,
 * symmetric handling — static and instance both contribute to the
 * archetype's seed manifest; instance also binds at runtime.
 *
 * <p>Usage from a value class's constructor:
 *
 * <pre>
 * public final class ToolButton extends Container {
 *     {@literal @}Seed.Property(role = VisualVocabulary.Background.KEY)
 *     Color background;
 *
 *     {@literal @}Seed.Property(role = SpatialVocabulary.Width.KEY)
 *     Length width;
 *
 *     public ToolButton(Body body) {
 *         super(body);
 *         BodyBinder.bind(this, body);
 *     }
 * }
 * </pre>
 *
 * <p>After {@code BodyBinder.bind}, each {@code @Seed.Property} instance
 * field is either populated from the body's matching binding (when the
 * binding exists and the target converts to the field's type) or left at
 * its declared default (or null when no default was given).
 *
 * <h2>Type conversion</h2>
 *
 * <p>The body's binding target is {@code Object}; the field has a
 * specific declared type.  Conversion rules (in order):
 *
 * <ul>
 *   <li>If {@code target instanceof FieldType}, assign directly.</li>
 *   <li>{@code Long → int/short/byte/double/float} narrow/widen.</li>
 *   <li>{@code byte[] → IpAddress} via constructor.</li>
 *   <li>{@code Body → V extends Value} via the field-type's static
 *       {@code from(Body)} method if one exists.</li>
 *   <li>Otherwise: throw an error so the missing converter gets noticed.</li>
 * </ul>
 *
 * <p>Inheritance: instance-field walking includes superclass fields, so
 * a {@code Container extends SceneNode} instance picks up
 * {@code SceneNode}'s instance fields as well as its own.
 */
public final class BodyBinder {

    private BodyBinder() {}

    /**
     * Walk {@code instance.getClass()}'s {@code @Seed.Property} instance
     * fields (including inherited ones) and populate each from the
     * body's matching binding.
     */
    public static void bind(Object instance, Body body) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(body, "body");
        Class<?> cls = instance.getClass();
        while (cls != null && cls != Object.class) {
            for (Field field : cls.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                Seed.Property[] properties = field.getAnnotationsByType(Seed.Property.class);
                if (properties.length == 0) continue;
                for (Seed.Property property : properties) {
                    bindField(instance, field, property, body);
                }
            }
            cls = cls.getSuperclass();
        }
    }

    /**
     * Bind one field from the body's matching binding.  No-op when the
     * body has no binding for the role, or when the binding's target is
     * null.
     *
     * <p>Honors {@link Seed.Property#qualifiers()} — a property with
     * qualifiers matches a compound binding key (e.g.,
     * {@code role=SIGNING, qualifiers={CURRENT}} matches
     * {@code (SIGNING, CURRENT)}).
     */
    private static void bindField(Object instance, Field field,
                                  Seed.Property property, Body body) {
        if (property.role().isEmpty()) return;  // schemaRole / typeRole forms don't bind at runtime
        ItemRef role = ItemRef.iid(property.role());

        String[] qualifierKeys = property.qualifiers();
        dev.everydaythings.graph.ref.CompoundKey compoundKey;
        if (qualifierKeys.length == 0) {
            compoundKey = dev.everydaythings.graph.ref.CompoundKey.of(role);
        } else {
            Object[] qualifiers = new Object[qualifierKeys.length];
            for (int i = 0; i < qualifierKeys.length; i++) {
                qualifiers[i] = ItemRef.iid(qualifierKeys[i]);
            }
            compoundKey = dev.everydaythings.graph.ref.CompoundKey.of(role, qualifiers);
        }

        Optional<Binding> bindingOpt = body.binding(compoundKey);
        if (bindingOpt.isEmpty()) return;
        Object target = bindingOpt.get().target();
        if (target == null) return;

        Object converted = convert(target, field.getType(), field);
        if (converted == null) return;

        field.setAccessible(true);
        try {
            field.set(instance, converted);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(
                    "Cannot write @Seed.Property field "
                            + field.getDeclaringClass().getName() + "." + field.getName(), e);
        }
    }

    /**
     * Convert a binding target to the field's declared type.  Returns
     * null if no conversion path applies; throws if the path was wrong.
     */
    private static Object convert(Object target, Class<?> fieldType, Field field) {
        // BindingExempt types are vault-managed (or similar): hydrated in a
        // later pass by the owning subsystem with the runtime context they
        // need.  BodyBinder leaves them at their default value.
        if (BindingExempt.class.isAssignableFrom(fieldType)) return null;

        // Direct assignment.
        if (fieldType.isInstance(target)) return target;

        // Numeric widen/narrow from Long (the codec's canonical integer).
        if (target instanceof Long n) {
            if (fieldType == int.class || fieldType == Integer.class)   return n.intValue();
            if (fieldType == short.class || fieldType == Short.class)   return n.shortValue();
            if (fieldType == byte.class || fieldType == Byte.class)     return n.byteValue();
            if (fieldType == long.class)                                return n;
            if (fieldType == double.class || fieldType == Double.class) return n.doubleValue();
            if (fieldType == float.class || fieldType == Float.class)   return n.floatValue();
        }

        // Numeric widen from Double (the codec's canonical float).
        if (target instanceof Double d) {
            if (fieldType == float.class || fieldType == Float.class)   return d.floatValue();
            if (fieldType == double.class)                              return d;
        }

        // Boolean.
        if (target instanceof Boolean b && (fieldType == boolean.class || fieldType == Boolean.class)) {
            return b;
        }

        // byte[] → IpAddress.
        if (target instanceof byte[] bytes && fieldType == IpAddress.class) {
            return new IpAddress(bytes);
        }

        // Body → V extends Value via static from(Body).
        if (target instanceof Body bodyTarget && Value.class.isAssignableFrom(fieldType)) {
            return invokeFromBody(fieldType, bodyTarget, field);
        }

        // Deferred Variable reference: an ItemRef where the field type
        // can't hold one is the unresolved-Variable case (the binding
        // target is a Variable's IID, awaiting substitution by the
        // Presenter).  Leave the field null; the underlying bindings
        // list still carries the reference for downstream stages.
        if (target instanceof dev.everydaythings.graph.ref.ItemRef) {
            return null;
        }

        throw new IllegalStateException(
                "BodyBinder: no converter for target type "
                        + target.getClass().getName() + " → field type "
                        + fieldType.getName()
                        + " (field " + field.getDeclaringClass().getName()
                        + "." + field.getName() + ")");
    }

    /** Call {@code fieldType.from(Body)} via reflection. */
    private static Object invokeFromBody(Class<?> fieldType, Body bodyTarget, Field field) {
        try {
            Method from = fieldType.getMethod("from", Body.class);
            return from.invoke(null, bodyTarget);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    "BodyBinder: field type " + fieldType.getName()
                            + " has no static from(Body) method (field "
                            + field.getDeclaringClass().getName() + "." + field.getName() + ")", e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "BodyBinder: failed invoking " + fieldType.getName() + ".from(Body) for field "
                            + field.getDeclaringClass().getName() + "." + field.getName(), e);
        }
    }
}
