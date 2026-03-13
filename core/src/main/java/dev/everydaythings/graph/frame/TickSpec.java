package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.item.Tick;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

/**
 * Cached metadata for a {@link Tick @Tick} annotated method.
 *
 * <p>Wraps the reflective {@link Method} with a {@link MethodHandle} for
 * near-native invocation performance. Stores the configured interval.
 *
 * <p>Instances are created by {@link ComponentScanner} during class scanning
 * and cached for the lifetime of the JVM.
 */
public class TickSpec {

    private final Method method;
    private final MethodHandle handle;
    private final long intervalMs;

    public TickSpec(Method method, long intervalMs) {
        this.method = method;
        this.intervalMs = intervalMs;
        method.setAccessible(true);
        try {
            this.handle = MethodHandles.lookup().unreflect(method);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Cannot create MethodHandle for @Tick method " + method, e);
        }
    }

    /**
     * Invoke the tick method on the given target instance.
     */
    public void invoke(Object target) {
        try {
            handle.invoke(target);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to invoke @Tick method " + method.getName()
                    + " on " + target.getClass().getSimpleName(), e);
        }
    }

    public long intervalMs() {
        return intervalMs;
    }

    public String methodName() {
        return method.getName();
    }
}
