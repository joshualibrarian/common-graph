package dev.everydaythings.graph.ui.scene.surface.item;

import dev.everydaythings.graph.dispatch.ParamSpec;
import dev.everydaythings.graph.dispatch.VerbEntry;
import dev.everydaythings.graph.dispatch.Vocabulary;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Declarative help surface — shows the vocabulary scope stack for a context.
 *
 * <p>Renders the full scope chain: item → session → system. Each scope
 * section shows available verbs and local tokens. Built entirely from
 * {@code @Scene.*} annotations — no procedural rendering.
 *
 * <p>Toggled by F1. Shows in the detail panel without affecting the tree.
 */
@Getter
@Scene.Container(direction = Scene.Direction.VERTICAL, style = {"help-surface"}, gap = "0.5em")
@Scene.Rule(match = ".scope-title", fontWeight = "bold")
@Scene.Rule(match = ".verb-name", fontWeight = "bold", fontFamily = "monospace")
@Scene.Rule(match = ".verb-doc", opacity = "dim")
@Scene.Rule(match = ".param-name", fontFamily = "monospace")
@Scene.Rule(match = ".param-type", opacity = "dim")
@Scene.Rule(match = ".token-name", fontFamily = "monospace")
@Scene.Rule(match = ".token-target", opacity = "dim")
@Scene.Rule(match = ".empty", opacity = "dim")
public class HelpSurface extends SurfaceSchema<Void> {

    // ==================================================================================
    // Declarative Structure
    // ==================================================================================

    /** Title */
    @Scene.Text(bind = "'Help (F1 to dismiss)'", style = {"heading", "small"})
    static class Title {}

    /** Scope sections */
    @Scene.Repeat(bind = "scopes")
    static class ScopeSection {

        /** Scope name header */
        @Scene.Text(bind = "$item.scopeName", style = {"scope-title"})
        static class ScopeName {}

        /** Verbs in this scope */
        @Scene.Container(direction = Scene.Direction.VERTICAL, gap = "0.125em")
        @Scene.If("$item.hasVerbs")
        static class Verbs {
            @Scene.Repeat(bind = "$item.verbs")
            static class VerbRow {
                @Scene.Container(direction = Scene.Direction.HORIZONTAL, gap = "0.5em")
                static class Row {
                    @Scene.Text(bind = "$item.name", style = {"verb-name"})
                    static class Name {}

                    @Scene.Text(bind = "$item.doc", style = {"verb-doc"})
                    @Scene.If("$item.doc != null")
                    static class Doc {}
                }

                /** Parameters */
                @Scene.Container(direction = Scene.Direction.VERTICAL, style = {"params"})
                @Scene.If("$item.hasParams")
                static class Params {
                    @Scene.Repeat(bind = "$item.params")
                    static class ParamRow {
                        @Scene.Container(direction = Scene.Direction.HORIZONTAL, gap = "0.25em")
                        static class Row {
                            @Scene.Text(bind = "'  '")
                            static class Indent {}

                            @Scene.Text(bind = "$item.name", style = {"param-name"})
                            static class Name {}

                            @Scene.Text(bind = "$item.typeLabel", style = {"param-type"})
                            static class Type {}

                            @Scene.Text(bind = "'(required)'", style = {"param-type"})
                            @Scene.If("$item.required")
                            static class Required {}
                        }
                    }
                }
            }
        }

        /** Local tokens in this scope */
        @Scene.Container(direction = Scene.Direction.VERTICAL, gap = "0.0625em")
        @Scene.If("$item.hasTokens")
        static class Tokens {
            @Scene.Text(bind = "'Local tokens:'", style = {"scope-title", "small"})
            static class Header {}

            @Scene.Repeat(bind = "$item.tokens")
            static class TokenRow {
                @Scene.Container(direction = Scene.Direction.HORIZONTAL, gap = "0.5em")
                static class Row {
                    @Scene.Text(bind = "$item.token", style = {"token-name"})
                    static class Name {}

                    @Scene.Text(bind = "'→'", style = {"muted"})
                    static class Arrow {}

                    @Scene.Text(bind = "$item.targetLabel", style = {"token-target"})
                    static class Target {}
                }
            }
        }

        /** Empty scope placeholder */
        @Scene.Text(bind = "'  (none)'", style = {"empty"})
        @Scene.If("$item.empty")
        static class Empty {}
    }

    // ==================================================================================
    // Data Model
    // ==================================================================================

    private List<ScopeData> scopes;

    public HelpSurface() {}

    /**
     * Build the help surface from a context item and its scope chain.
     *
     * @param contextItem the currently selected item (drives scope)
     * @param session     the session item (if available)
     * @param librarian   the librarian (system-level vocabulary)
     */
    public static HelpSurface of(Item contextItem, Item session, Librarian librarian) {
        HelpSurface surface = new HelpSurface();
        surface.scopes = new ArrayList<>();

        // Item scope
        if (contextItem != null) {
            surface.scopes.add(ScopeData.fromItem(
                    contextItem.displayToken(), contextItem.vocabulary()));
        }

        // Session scope
        if (session != null && session != contextItem) {
            surface.scopes.add(ScopeData.fromItem(
                    "Session", session.vocabulary()));
        }

        // System scope (librarian)
        if (librarian != null) {
            surface.scopes.add(ScopeData.fromItem(
                    "System", librarian.vocabulary()));
        }

        return surface;
    }

    // ==================================================================================
    // Data Records
    // ==================================================================================

    @Getter
    public static class ScopeData {
        private String scopeName;
        private boolean hasVerbs;
        private boolean hasTokens;
        private boolean empty;
        private List<VerbData> verbs;
        private List<TokenData> tokens;

        static ScopeData fromItem(String name, Vocabulary vocab) {
            ScopeData data = new ScopeData();
            data.scopeName = name;
            data.verbs = new ArrayList<>();
            data.tokens = new ArrayList<>();

            if (vocab != null) {
                for (VerbEntry verb : vocab) {
                    data.verbs.add(VerbData.from(verb));
                }

                data.tokens = vocab.prefixMatch("").stream()
                        .map(TokenData::from)
                        .toList();
            }

            data.hasVerbs = !data.verbs.isEmpty();
            data.hasTokens = !data.tokens.isEmpty();
            data.empty = !data.hasVerbs && !data.hasTokens;

            return data;
        }
    }

    @Getter
    public static class VerbData {
        private String name;
        private String doc;
        private boolean hasParams;
        private List<ParamData> params;

        static VerbData from(VerbEntry verb) {
            VerbData data = new VerbData();

            String methodName = verb.methodName();
            data.name = methodName.startsWith("action")
                    ? methodName.substring(6, 7).toLowerCase() + methodName.substring(7)
                    : methodName;

            data.doc = verb.doc();

            data.params = new ArrayList<>();
            if (verb.params() != null) {
                for (ParamSpec param : verb.params()) {
                    data.params.add(ParamData.from(param));
                }
            }
            data.hasParams = !data.params.isEmpty();

            return data;
        }
    }

    @Getter
    public static class ParamData {
        private String name;
        private String typeLabel;
        private boolean required;

        static ParamData from(ParamSpec param) {
            ParamData data = new ParamData();
            data.name = param.name();
            data.typeLabel = ": " + param.type().getSimpleName();
            data.required = param.required();
            return data;
        }
    }

    @Getter
    public static class TokenData {
        private String token;
        private String targetLabel;

        static TokenData from(Posting posting) {
            TokenData data = new TokenData();
            data.token = posting.token();
            data.targetLabel = posting.target() != null
                    ? posting.target().displayAtWidth(16) : "—";
            return data;
        }
    }
}
