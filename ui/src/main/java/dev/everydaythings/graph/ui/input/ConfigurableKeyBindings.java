package dev.everydaythings.graph.ui.input;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * User-configurable key bindings with fallback to defaults.
 *
 * <p>Allows users to override specific bindings while falling back
 * to the default bindings for everything else.
 *
 * <p>Usage:
 * <pre>{@code
 * ConfigurableKeyBindings bindings = ConfigurableKeyBindings.create()
 *     .bind(KeyChord.alt(SpecialKey.UP), NavAction.TREE_UP)
 *     .bind(KeyChord.ctrl(SpecialKey.UP), NavAction.ITEM_PREV)  // platform-specific override
 *     .build();
 * }</pre>
 */
public class ConfigurableKeyBindings implements KeyBindings {

    private final Platform platform;
    private final KeyBindings fallback;
    private final Map<KeyChord, BiFunction<FocusContext, Boolean, Optional<NavAction>>> overrides;

    private ConfigurableKeyBindings(
            Platform platform,
            KeyBindings fallback,
            Map<KeyChord, BiFunction<FocusContext, Boolean, Optional<NavAction>>> overrides) {
        this.platform = platform;
        this.fallback = fallback;
        this.overrides = Map.copyOf(overrides);
    }

    /**
     * Create a builder for configurable bindings.
     */
    public static Builder create() {
        return new Builder();
    }

    /**
     * Create a builder starting from the default bindings for the current platform.
     */
    public static Builder fromDefaults() {
        return new Builder().withFallback(DefaultKeyBindings.forCurrentPlatform());
    }

    /**
     * Create a builder starting from specific default bindings.
     */
    public static Builder fromDefaults(KeyBindings defaults) {
        return new Builder().withFallback(defaults);
    }

    @Override
    public Platform platform() {
        return platform;
    }

    @Override
    public Optional<NavAction> resolve(KeyChord chord, FocusContext context) {
        return resolve(chord, context, false);
    }

    @Override
    public Optional<NavAction> resolve(KeyChord chord, FocusContext context, boolean promptHasText) {
        // Check overrides first
        var override = overrides.get(chord);
        if (override != null) {
            Optional<NavAction> result = override.apply(context, promptHasText);
            if (result.isPresent()) {
                return result;
            }
        }

        // Fall back to default bindings
        return fallback.resolve(chord, context, promptHasText);
    }

    /**
     * Builder for configurable key bindings.
     */
    public static class Builder {
        private Platform platform = Platform.current();
        private KeyBindings fallback = new DefaultKeyBindings();
        private final Map<KeyChord, BiFunction<FocusContext, Boolean, Optional<NavAction>>> overrides = new HashMap<>();

        /**
         * Set the platform for these bindings.
         */
        public Builder forPlatform(Platform platform) {
            this.platform = platform;
            this.fallback = new DefaultKeyBindings(platform);
            return this;
        }

        /**
         * Set a custom fallback (instead of DefaultKeyBindings).
         */
        public Builder withFallback(KeyBindings fallback) {
            this.fallback = fallback;
            return this;
        }

        /**
         * Bind a key chord to an action (context-independent).
         */
        public Builder bind(KeyChord chord, NavAction action) {
            overrides.put(chord, (ctx, hasText) -> Optional.of(action));
            return this;
        }

        /**
         * Bind a key chord to an action only in a specific context.
         */
        public Builder bind(KeyChord chord, FocusContext requiredContext, NavAction action) {
            overrides.put(chord, (ctx, hasText) ->
                    ctx == requiredContext ? Optional.of(action) : Optional.empty());
            return this;
        }

        /**
         * Bind a key chord with full control over resolution.
         */
        public Builder bind(KeyChord chord, BiFunction<FocusContext, Boolean, Optional<NavAction>> resolver) {
            overrides.put(chord, resolver);
            return this;
        }

        /**
         * Unbind a key chord (make it do nothing, don't fall through to defaults).
         */
        public Builder unbind(KeyChord chord) {
            overrides.put(chord, (ctx, hasText) -> Optional.empty());
            return this;
        }

        /**
         * Build the configured bindings.
         */
        public ConfigurableKeyBindings build() {
            return new ConfigurableKeyBindings(platform, fallback, overrides);
        }
    }
}
