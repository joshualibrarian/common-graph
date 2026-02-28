package dev.everydaythings.graph.item.component;

import java.util.ArrayList;
import java.util.List;

/**
 * Runtime registry of active tickable component instances.
 *
 * <p>Bridges the gap between annotation scanning (in {@code :core}) and the
 * UI timer (in {@code :ui}). The session rebuilds this registry when the
 * context item changes, and the live timer calls {@link #tickAll()} each
 * cycle.
 *
 * <p>Thread safety: {@link #targets} is a volatile immutable list. The main
 * thread writes via {@link #rebuild}, the timer thread reads via
 * {@link #tickAll}. Each {@link TickTarget} tracks its own {@code lastTickMs}
 * which is only written by the single timer thread.
 */
public class TickRegistry {

    private volatile List<TickTarget> targets = List.of();

    /**
     * Rebuild from an item's live components.
     *
     * <p>Called on context change. Scans each live instance for
     * {@link Tick @Tick} methods via {@link ComponentScanner}.
     *
     * @param table the component table to scan
     */
    public void rebuild(ComponentTable table) {
        List<TickTarget> newTargets = new ArrayList<>();
        table.forEachLive(instance -> {
            ComponentScanner.ComponentMeta meta = ComponentScanner.metaFor(instance.getClass());
            for (TickSpec spec : meta.ticks()) {
                newTargets.add(new TickTarget(instance, spec));
            }
        });
        this.targets = List.copyOf(newTargets);
    }

    /**
     * Tick all due targets.
     *
     * @return true if any target was actually ticked (caller should repaint)
     */
    public boolean tickAll() {
        long now = System.currentTimeMillis();
        boolean anyTicked = false;
        for (TickTarget target : targets) {
            if (target.tickIfDue(now)) {
                anyTicked = true;
            }
        }
        return anyTicked;
    }

    /**
     * Clear all targets.
     */
    public void clear() {
        this.targets = List.of();
    }

    /**
     * Whether any tickable targets are registered.
     */
    public boolean hasTargets() {
        return !targets.isEmpty();
    }

    /**
     * A single tick target: one instance + one @Tick method.
     */
    private static class TickTarget {
        private final Object instance;
        private final TickSpec spec;
        private long lastTickMs;

        TickTarget(Object instance, TickSpec spec) {
            this.instance = instance;
            this.spec = spec;
            this.lastTickMs = 0; // tick immediately on first cycle
        }

        boolean tickIfDue(long nowMs) {
            if (nowMs - lastTickMs >= spec.intervalMs()) {
                spec.invoke(instance);
                lastTickMs = nowMs;
                return true;
            }
            return false;
        }
    }
}
