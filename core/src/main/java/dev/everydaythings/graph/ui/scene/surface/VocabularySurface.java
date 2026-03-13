package dev.everydaythings.graph.ui.scene.surface;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.dispatch.VerbAlias;
import dev.everydaythings.graph.dispatch.VerbEntry;
import dev.everydaythings.graph.dispatch.VerbSpec;
import dev.everydaythings.graph.dispatch.Vocabulary;
import dev.everydaythings.graph.dispatch.ParamSpec;
import dev.everydaythings.graph.ui.scene.surface.primitive.TextSpan;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Renders a {@link Vocabulary} as a help panel.
 *
 * <p>Shows available verbs grouped by source (item vs component),
 * with documentation and parameter info. Aliases are shown if present.
 */
@Canonical.Canonization
public class VocabularySurface extends SurfaceSchema<Vocabulary> {

    @Override
    public void render(SurfaceRenderer out) {
        emitCommonProperties(out);

        Vocabulary vocab = value();
        if (vocab == null || vocab.isEmpty()) {
            out.text("No verbs available.", List.of("muted"));
            return;
        }

        out.beginBox(Scene.Direction.VERTICAL, List.of("vocabulary-panel"));
        out.gap("0.25em");

        // Title
        out.text("Vocabulary", List.of("heading", "small"));

        // Item verbs
        List<VerbEntry> itemVerbs = vocab.itemVerbs().toList();
        if (!itemVerbs.isEmpty()) {
            renderVerbGroup(out, "Item Verbs", itemVerbs);
        }

        // Component verbs grouped by handle
        Map<String, List<VerbEntry>> byHandle = vocab.componentVerbs()
                .filter(v -> v.componentHandle() != null)
                .collect(Collectors.groupingBy(VerbEntry::componentHandle, LinkedHashMap::new, Collectors.toList()));

        for (var entry : byHandle.entrySet()) {
            renderVerbGroup(out, entry.getKey(), entry.getValue());
        }

        // Aliases
        List<VerbAlias> aliases = vocab.aliases();
        if (!aliases.isEmpty()) {
            out.beginBox(Scene.Direction.VERTICAL, List.of("vocabulary-section"));
            out.gap("0.125em");
            out.text("Aliases", List.of("subheading", "small"));
            for (VerbAlias alias : aliases) {
                out.richText(List.of(
                        new TextSpan(alias.token(), List.of("bold", "monospace")),
                        new TextSpan(" → ", List.of("muted")),
                        new TextSpan(alias.sememeId().encodeText(), List.of("monospace"))
                ), List.of("vocabulary-alias"));
            }
            out.endBox();
        }

        out.endBox();
    }

    private void renderVerbGroup(SurfaceRenderer out, String groupName, List<VerbEntry> verbs) {
        out.beginBox(Scene.Direction.VERTICAL, List.of("vocabulary-section"));
        out.gap("0.125em");
        out.text(groupName, List.of("subheading", "small"));

        for (VerbEntry verb : verbs) {
            out.beginBox(Scene.Direction.VERTICAL, List.of("vocabulary-entry"));

            // Derive display name from method name (actionNew → new, rename → rename)
            String methodName = verb.methodName();
            String shortName = methodName.startsWith("action")
                    ? methodName.substring(6, 7).toLowerCase() + methodName.substring(7)
                    : methodName;

            List<TextSpan> verbLine = new ArrayList<>();
            verbLine.add(new TextSpan(shortName, List.of("bold", "monospace")));
            if (verb.doc() != null && !verb.doc().isEmpty()) {
                verbLine.add(new TextSpan("  " + verb.doc(), List.of("muted")));
            }
            out.richText(verbLine, List.of("vocabulary-verb"));

            // Parameters
            List<ParamSpec> params = verb.params();
            if (!params.isEmpty()) {
                for (ParamSpec param : params) {
                    List<TextSpan> paramSpans = new ArrayList<>();
                    paramSpans.add(new TextSpan("  ", List.of()));
                    paramSpans.add(new TextSpan(param.name(), List.of("monospace")));
                    paramSpans.add(new TextSpan(" : " + param.type().getSimpleName(), List.of("muted")));
                    if (param.required()) {
                        paramSpans.add(new TextSpan(" (required)", List.of("bold", "small")));
                    }
                    if (param.doc() != null && !param.doc().isEmpty()) {
                        paramSpans.add(new TextSpan("  " + param.doc(), List.of("muted")));
                    }
                    out.richText(paramSpans, List.of("vocabulary-param"));
                }
            }

            out.endBox();
        }

        out.endBox();
    }
}
