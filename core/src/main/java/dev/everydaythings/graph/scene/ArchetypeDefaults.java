package dev.everydaythings.graph.scene;

/**
 * Terminal default scenes formerly attached to {@code CoreVocabulary.Archetype}
 * as inner classes.  Pulled out so Archetype-the-seed has no scene
 * dependency (and can move to :annotations as a pure declaration).
 *
 * <p>These three classes are currently not wired to Archetype — the
 * enclosing-class convention that previously linked them is gone.  A
 * record-based attachment mechanism will reconnect them in scene/ later;
 * tracked separately.  Until then, archetypes with no own-scene
 * declaration will not have a terminal cascade fallback.
 */
public final class ArchetypeDefaults {

    private ArchetypeDefaults() {}

    /**
     * Terminal default Scene.  Loud-but-rendering placeholder ("Common
     * Graph item") signaling a missing more-specific declaration up the
     * archetype chain.
     */
    @Scene.Text(schemaRole = SceneVocabulary.Scene.KEY)
    public static class DefaultScene {
        @Scene.Property(role = SceneVocabulary.Text.KEY)
        static String text = "Common Graph item";
    }

    /**
     * Terminal default Handle scene — compact / glanceable form for
     * lists, chains, breadcrumbs, swarm dots.
     */
    @Scene.Text(schemaRole = SceneVocabulary.Scene.KEY,
                qualifiers = {SceneVocabulary.Handle.KEY})
    public static class DefaultHandle {
        @Scene.Property(role = SceneVocabulary.Text.KEY)
        static String text = "[item]";
    }

    /**
     * Terminal default Aura scene — per-item overlay framework.  Empty
     * container by default; activation and content is the session's job
     * at render time.
     */
    @Scene.Container(schemaRole = SceneVocabulary.Scene.KEY,
                     qualifiers = {SceneVocabulary.Aura.KEY})
    public static class DefaultAura {
    }
}
