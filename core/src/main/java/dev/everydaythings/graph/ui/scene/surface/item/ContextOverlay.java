package dev.everydaythings.graph.ui.scene.surface.item;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.dispatch.VerbEntry;
import dev.everydaythings.graph.frame.FrameEntry;
import dev.everydaythings.graph.language.VerbSememe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Derived context overlay model for transient menus (vocabulary/settings).
 *
 * <p>Overlay content is generated from the current context item and is
 * non-persistent. Opening an overlay does not change navigation context.
 */
public final class ContextOverlay {

    public enum Kind {
        VOCABULARY,
        SETTINGS
    }

    public record Entry(
            String key,
            String title,
            String subtitle,
            String scope,
            int priority
    ) {}

    private final Kind kind;
    private final String contextLabel;
    private final List<Entry> entries;

    private ContextOverlay(Kind kind, String contextLabel, List<Entry> entries) {
        this.kind = kind;
        this.contextLabel = contextLabel;
        this.entries = entries != null ? List.copyOf(entries) : List.of();
    }

    public Kind kind() {
        return kind;
    }

    public String contextLabel() {
        return contextLabel;
    }

    public List<Entry> entries() {
        return entries;
    }

    public static ContextOverlay vocabulary(Item item) {
        if (item == null) return new ContextOverlay(Kind.VOCABULARY, "", List.of());

        List<Entry> out = new ArrayList<>();

        // Component nouns/keys (local component aliases/handles) — lower than verbs.
        for (FrameEntry entry : item.content()) {
            String key = entry.alias() != null && !entry.alias().isBlank()
                    ? entry.alias()
                    : entry.frameKey().toCanonicalString();
            out.add(new Entry(
                    key,
                    key,
                    "component",
                    "item",
                    150
            ));
        }

        // Verb vocabulary with proximity weighting.
        for (VerbEntry verb : item.vocabulary()) {
            String key = verb.sememeId().encodeText();
            String title = verb.methodName().startsWith("action")
                    ? decap(verb.methodName().substring(6))
                    : verb.methodName();
            String scope = verb.componentHandle() != null
                    ? "component:" + verb.componentHandle()
                    : "item";
            int priority = (verb.componentHandle() != null) ? 300 : 220;

            out.add(new Entry(
                    key,
                    title,
                    verb.doc() != null ? verb.doc() : "",
                    scope,
                    priority
            ));
        }

        out.sort(Comparator
                .comparingInt(Entry::priority).reversed()
                .thenComparing(Entry::title, String.CASE_INSENSITIVE_ORDER));

        return new ContextOverlay(Kind.VOCABULARY, item.displayToken(), out);
    }

    public static ContextOverlay settings(Item item) {
        if (item == null) return new ContextOverlay(Kind.SETTINGS, "", List.of());

        List<Entry> out = new ArrayList<>();

        for (VerbEntry verb : item.vocabulary()) {
            String sememe = verb.sememeId().encodeText();
            String method = verb.methodName();
            boolean looksLikeSettingVerb =
                    VerbSememe.Put.KEY.equals(sememe)
                            || VerbSememe.Edit.KEY.equals(sememe)
                            || method.startsWith("actionSet")
                            || method.startsWith("actionToggle")
                            || method.startsWith("actionConfigure");
            if (!looksLikeSettingVerb) continue;

            String scope = verb.componentHandle() != null
                    ? "component:" + verb.componentHandle()
                    : "item";
            int priority = (verb.componentHandle() != null) ? 320 : 240;
            String title = method.startsWith("action")
                    ? decap(method.substring(6))
                    : method;

            out.add(new Entry(
                    sememe,
                    title,
                    verb.doc() != null ? verb.doc() : "",
                    scope,
                    priority
            ));
        }

        out.sort(Comparator
                .comparingInt(Entry::priority).reversed()
                .thenComparing(Entry::title, String.CASE_INSENSITIVE_ORDER));

        return new ContextOverlay(Kind.SETTINGS, item.displayToken(), out);
    }

    private static String decap(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}

