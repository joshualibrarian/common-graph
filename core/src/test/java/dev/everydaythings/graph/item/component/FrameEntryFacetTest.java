package dev.everydaythings.graph.item.component;

import dev.everydaythings.graph.item.id.HandleID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.mount.Mount;
import dev.everydaythings.graph.policy.PolicySet;
import dev.everydaythings.graph.ui.scene.ViewNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FrameEntry facets")
class FrameEntryFacetTest {

    private static final ItemID TYPE = ItemID.fromString("cg:type/test");

    @Test
    @DisplayName("legacy mount wiring populates presentation layout mounts")
    void presentationLayoutContainsMounts() {
        FrameEntry entry = FrameEntry.builder()
                .handle(HandleID.of("docs"))
                .type(TYPE)
                .identity(true)
                .build();

        entry.addMount(new Mount.PathMount("/documents"));

        assertThat(entry.mounts()).hasSize(1);
        assertThat(entry.presentation().layout().mounts()).hasSize(1);
        assertThat(entry.presentation().layout().mounts().get(0))
                .isInstanceOf(Mount.PathMount.class);
    }

    @Test
    @DisplayName("policy lives in config facet")
    void policyInConfigFacet() {
        FrameEntry entry = FrameEntry.builder()
                .handle(HandleID.of("policy"))
                .type(TYPE)
                .identity(true)
                .build();

        PolicySet policy = PolicySet.defaultsDenyAll();
        entry.setPolicy(policy);

        assertThat(entry.policy()).isSameAs(policy);
        assertThat(entry.config().policy()).isSameAs(policy);
    }

    @Test
    @DisplayName("scene override lives in presentation skin")
    void sceneOverrideInPresentationSkin() {
        FrameEntry entry = FrameEntry.builder()
                .handle(HandleID.of("scene"))
                .type(TYPE)
                .identity(true)
                .build();

        ViewNode override = new ViewNode();
        entry.setSceneOverride(override);

        assertThat(entry.sceneOverride()).isSameAs(override);
        assertThat(entry.presentation().skin().sceneOverride()).isSameAs(override);
    }
}
