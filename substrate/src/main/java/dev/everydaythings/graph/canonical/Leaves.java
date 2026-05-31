package dev.everydaythings.graph.canonical;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reflection cache for {@link Encode @Encode} / {@link Decode @Decode}
 * annotated methods on leaf value types.
 *
 * <p>Codecs ask: "given this class, what's the method that produces a
 * {@code byte[]}?" or "given this class, what's the static method that
 * accepts a {@code String}?" — and {@code Leaves} answers via cached
 * reflection.
 *
 * <p>A class is a "leaf" if it has at least one {@code @Encode} method
 * declared on itself or an ancestor. Leaves are encoded by invoking their
 * {@code @Encode} method and wrapping the result; structures (no
 * {@code @Encode}) are walked field-by-field.
 */
public final class Leaves {

    private Leaves() {}

    /**
     * One slot per (class, wire-form) pair. The wire-form key is the return
     * type of the {@code @Encode} method (e.g. {@code byte[].class},
     * {@code String.class}).
     */
    private static final Map<Class<?>, Map<Class<?>, Method>> ENCODE_CACHE =
            new ConcurrentHashMap<>();

    /**
     * One slot per (class, wire-form) pair. The wire-form key is the
     * parameter type of the {@code @Decode} method.
     */
    private static final Map<Class<?>, Map<Class<?>, Method>> DECODE_CACHE =
            new ConcurrentHashMap<>();

    /** True iff {@code clazz} declares any {@code @Encode} method. */
    public static boolean isLeaf(Class<?> clazz) {
        return !encodeMap(clazz).isEmpty();
    }

    /**
     * Find the {@code @Encode} method on {@code clazz} (or its ancestors)
     * whose return type matches {@code wireForm}. Returns {@code null} when
     * no such method exists.
     */
    public static Method findEncode(Class<?> clazz, Class<?> wireForm) {
        return encodeMap(clazz).get(wireForm);
    }

    /**
     * Find any {@code @Encode} method on {@code clazz}, preferring leaf wire
     * forms ({@code byte[]}, then {@code String}) and falling back to any
     * other return type. Returns {@code null} when the class has no
     * {@code @Encode} methods at all.
     */
    public static Method findAnyEncode(Class<?> clazz) {
        Map<Class<?>, Method> map = encodeMap(clazz);
        if (map.isEmpty()) return null;
        Method m = map.get(byte[].class);
        if (m != null) return m;
        m = map.get(String.class);
        if (m != null) return m;
        return map.values().iterator().next();
    }

    /**
     * Invoke an {@code @Encode} method and return its raw result (no cast).
     */
    public static Object invokeEncode(Object value, Method m) {
        try {
            return m.invoke(value);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException("@Encode failed on " + value.getClass().getName(), cause);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("@Encode inaccessible on " + value.getClass().getName(), e);
        }
    }

    /**
     * Find the {@code @Decode} static method on {@code clazz} (or its
     * ancestors) whose single parameter matches {@code wireForm}. Returns
     * {@code null} when no such method exists.
     */
    public static Method findDecode(Class<?> clazz, Class<?> wireForm) {
        return decodeMap(clazz).get(wireForm);
    }

    /**
     * Invoke a leaf's {@code @Encode} method, returning the encoded value
     * cast to {@code wireForm}. Throws if no matching encoder exists or if
     * the method itself throws.
     */
    @SuppressWarnings("unchecked")
    public static <T> T encode(Object value, Class<T> wireForm) {
        Method m = findEncode(value.getClass(), wireForm);
        if (m == null) {
            throw new IllegalArgumentException(
                    "No @Encode " + wireForm.getSimpleName() + " method on "
                            + value.getClass().getName());
        }
        try {
            return (T) m.invoke(value);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException("@Encode failed on " + value.getClass().getName(), cause);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("@Encode inaccessible on " + value.getClass().getName(), e);
        }
    }

    /**
     * Invoke the matching {@code @Decode} factory on {@code clazz}, passing
     * {@code wireValue}. Throws if no matching decoder exists or if the
     * method itself throws.
     */
    @SuppressWarnings("unchecked")
    public static <T> T decode(Class<T> clazz, Object wireValue) {
        Method m = findDecode(clazz, wireValue.getClass());
        if (m == null) {
            throw new IllegalArgumentException(
                    "No @Decode " + wireValue.getClass().getSimpleName()
                            + " method on " + clazz.getName());
        }
        try {
            return (T) m.invoke(null, wireValue);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException("@Decode failed on " + clazz.getName(), cause);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("@Decode inaccessible on " + clazz.getName(), e);
        }
    }

    private static Map<Class<?>, Method> encodeMap(Class<?> clazz) {
        return ENCODE_CACHE.computeIfAbsent(clazz, Leaves::scanEncode);
    }

    private static Map<Class<?>, Method> decodeMap(Class<?> clazz) {
        return DECODE_CACHE.computeIfAbsent(clazz, Leaves::scanDecode);
    }

    private static Map<Class<?>, Method> scanEncode(Class<?> clazz) {
        Map<Class<?>, Method> result = new HashMap<>();
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.isAnnotationPresent(Encode.class)) continue;
                if (Modifier.isStatic(m.getModifiers())) continue;
                if (m.getParameterCount() != 0) continue;
                Class<?> rt = m.getReturnType();
                if (rt == void.class) continue;
                // First match wins — subclass overrides superclass for the same wire form.
                result.putIfAbsent(rt, accessible(m));
            }
        }
        return result;
    }

    private static Map<Class<?>, Method> scanDecode(Class<?> clazz) {
        Map<Class<?>, Method> result = new HashMap<>();
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.isAnnotationPresent(Decode.class)) continue;
                if (!Modifier.isStatic(m.getModifiers())) continue;
                if (m.getParameterCount() != 1) continue;
                Class<?> pt = m.getParameterTypes()[0];
                result.putIfAbsent(pt, accessible(m));
            }
        }
        return result;
    }

    private static Method accessible(Method m) {
        if (!Modifier.isPublic(m.getModifiers())) m.setAccessible(true);
        return m;
    }
}
