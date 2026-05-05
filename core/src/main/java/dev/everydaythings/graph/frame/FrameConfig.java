package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.policy.PolicySet;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

/**
 * Per-frame configuration — settings and policy.
 *
 * <p>Long-term, config will be absorbed into the CONFIG binding on
 * {@link FrameBodyOld} and this class will be removed.
 */
@Getter
@Canonical.Canonization
public final class FrameConfig implements Canonical {

    @Canon(order = 0)
    private List<ScopedSetting> settings;

    @Canon(order = 1)
    private PolicySet policy;

    @Builder
    public FrameConfig(@Singular List<ScopedSetting> settings, PolicySet policy) {
        this.settings = settings == null ? List.of() : List.copyOf(settings);
        this.policy = policy;
    }

    public static FrameConfig empty() {
        return FrameConfig.builder().build();
    }

    @SuppressWarnings("unused")
    private FrameConfig() {}

    /**
     * A scoped configuration setting.
     */
    @Getter
    @Canonical.Canonization
    public static final class ScopedSetting implements Canonical {
        @Canon(order = 0) private String scopePath;
        @Canon(order = 1) private String key;
        @Canon(order = 2) private String value;

        @Builder
        public ScopedSetting(String scopePath, String key, String value) {
            this.scopePath = scopePath;
            this.key = key;
            this.value = value;
        }

        @SuppressWarnings("unused")
        private ScopedSetting() {}
    }
}
