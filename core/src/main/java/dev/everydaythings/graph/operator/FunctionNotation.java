package dev.everydaythings.graph.operator;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.BindingTarget;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.DatumRef;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.text.AnchorTable.TokenAnchor;
import dev.everydaythings.graph.text.FrameMap;
import dev.everydaythings.graph.text.FrameMap.BindingMap;
import dev.everydaythings.graph.text.FrameMap.Part;
import dev.everydaythings.graph.text.FrameMapTarget;
import dev.everydaythings.graph.text.GroupVocabulary;
import dev.everydaythings.graph.text.ParseContext;
import dev.everydaythings.graph.text.ParseEngine;
import dev.everydaythings.graph.text.ParseParams;
import dev.everydaythings.graph.text.TextSpan;
import dev.everydaythings.graph.text.TokenLattice.TokenSpan;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Function notation — the {@link Language} that recognizes function-call shapes
 * like {@code sum(5, 3)} or {@code between(0, 255, 128)} and renders them in
 * the same form.
 *
 * <p>Sibling of {@link OperatorNotation}: same underlying operator vocabulary,
 * different surface form. The {@code @add} sememe carries multiple lexemes —
 * one tagged for OperatorNotation ({@code "+"}, infix), one tagged for
 * FunctionNotation ({@code "sum"}, named function), each tagged for relevant
 * natural languages ({@code "add"}, English verb). Each Language consumes the
 * lexemes tagged for its notation.
 *
 * <h2>Surface form</h2>
 * <pre>
 *   sum(5, 3)        → ADD { THEME → 5, GOAL → 3 }
 *   between(0, 9, 5) → BETWEEN { SOURCE → 0, GOAL → 9, THEME → 5 }
 * </pre>
 *
 * <p>V1 scope: binary calls map arg0 → THEME, arg1 → GOAL. Unary maps arg0 →
 * THEME. Variadic and per-predicate positional declarations are deferred.
 */
@Seed.Item(key = FunctionNotation.KEY)
@Seed.Embodies(key = FunctionNotation.KEY)
public class FunctionNotation extends Language {

    /** Canonical key for the function-notation Language. */
    public static final String KEY = "cg.lang:function-notation";

    /** Seed/siloed constructor (no librarian). */
    public FunctionNotation() {
        super(ItemRef.iid(KEY));
    }

    /** Runtime constructor — bound to a librarian. */
    public FunctionNotation(Librarian librarian) {
        super(ItemRef.iid(KEY), librarian);
    }

    /** Runtime constructor invoked by {@link dev.everydaythings.graph.item.SeedProcessor}
     *  during bootstrap hydration; matches the {@code (ItemRef, Librarian)} contract
     *  enforced for any class carrying {@link Seed.Embodies}. */
    public FunctionNotation(ItemRef iid, Librarian librarian) {
        super(iid, librarian);
    }

    // ==================================================================================
    // Parse — recognize {@code name(args)} shape and emit the operator frame.
    // ==================================================================================

    /**
     * Build a function-call parse contribution. For each anchor of {@code self}
     * whose token text matches the operator's FunctionNotation lexeme name, check
     * that the immediately following token is an open-group {@code (}, find the
     * matching close, recursively parse the bracketed args (comma-separated), and
     * emit a frame with positional bindings.
     *
     * <p>Instance method (rather than static) so the arg-resolution helpers can
     * call {@code this.recognizeOperand} for locale-aware literal recognition.
     */
    public FrameMap parseAnchor(Operator self, ParseContext ctx) {
        Optional<String> nameOpt = lookupFunctionName(self);
        if (nameOpt.isEmpty()) return FrameMap.empty();
        String functionName = nameOpt.get();

        Optional<TokenAnchor> selfAnchor = ctx.anchors().tokenAnchors().stream()
                .filter(ta -> ta.participant().iid().equals(self.iid()))
                .findFirst();
        if (selfAnchor.isEmpty() || selfAnchor.get().spans().isEmpty()) {
            return FrameMap.empty();
        }

        for (TextSpan span : selfAnchor.get().spans()) {
            int nameIdx = Language.indexOfTokenSpan(ctx.tokens(), span);
            if (nameIdx < 0) continue;
            if (!functionName.equals(ctx.tokens().get(nameIdx).surfaceText())) continue;
            if (nameIdx + 1 >= ctx.tokens().size()) continue;
            TokenSpan next = ctx.tokens().get(nameIdx + 1);
            if (!Language.isOpenGroup(next)) continue;
            int closeIdx = Language.findMatchingClose(ctx.tokens(), nameIdx + 1);
            if (closeIdx < 0) continue;
            return buildCallFrame(self, ctx, nameIdx, nameIdx + 1, closeIdx);
        }
        return FrameMap.empty();
    }

    /**
     * Build a function-call FrameMap. The call's text-span covers from {@code name}
     * through {@code )}; each parsed arg becomes a positional binding (arg0 →
     * THEME, arg1 → GOAL, additional args dropped in V1).
     */
    private FrameMap buildCallFrame(Operator self, ParseContext ctx,
                                    int nameIdx, int openIdx, int closeIdx) {
        TextSpan callSpan = new TextSpan(
                ctx.tokens().get(nameIdx).span().start(),
                ctx.tokens().get(closeIdx).span().end());

        List<Operand> args = parseArgs(ctx, openIdx, closeIdx);

        BigDecimal predicateConfidence = new BigDecimal("0.9000");
        BigDecimal bindingConfidence = new BigDecimal("0.8500");

        List<BindingMap> bindings = new ArrayList<>();
        if (args.size() >= 1) {
            BindingMap themeBinding = makeBinding(args.get(0),
                    ItemRef.iid(ThematicRole.Theme.KEY), bindingConfidence);
            if (themeBinding != null) bindings.add(themeBinding);
        }
        if (args.size() >= 2) {
            BindingMap goalBinding = makeBinding(args.get(1),
                    ItemRef.iid(ThematicRole.Goal.KEY), bindingConfidence);
            if (goalBinding != null) bindings.add(goalBinding);
        }
        // V1: args beyond 2 dropped. Per-predicate positional declarations land later.

        return new FrameMap(
                null,
                new Part<>(ItemRef.of(self.iid()), predicateConfidence, List.of(callSpan)),
                bindings,
                List.of());
    }

    /**
     * Split tokens between {@code openIdx} (the opening paren) and {@code closeIdx}
     * (the closing paren) into per-arg Operands, comma-separated. Each arg-chunk
     * is recursively parsed via the same engine; single-token args use direct
     * literal/ref extraction.
     */
    private List<Operand> parseArgs(ParseContext ctx, int openIdx, int closeIdx) {
        List<Operand> args = new ArrayList<>();
        int chunkStart = openIdx + 1;
        for (int i = openIdx + 1; i < closeIdx; i++) {
            if (isComma(ctx.tokens().get(i))) {
                Operand arg = parseArgChunk(ctx, chunkStart, i);
                if (arg != null) args.add(arg);
                chunkStart = i + 1;
            }
        }
        Operand last = parseArgChunk(ctx, chunkStart, closeIdx);
        if (last != null) args.add(last);
        return args;
    }

    /** True if the token resolves to the comma sememe. */
    private static boolean isComma(TokenSpan token) {
        if (token == null) return false;
        return token.postings().stream()
                .anyMatch(p -> ItemRef.iid(GroupVocabulary.Comma.KEY).equals(p.target()));
    }

    /**
     * Parse one arg-chunk (tokens at {@code [start, end)}) into an Operand. A
     * single literal/ref token becomes that target directly; multi-token chunks
     * are recursively parsed via the engine.
     */
    private Operand parseArgChunk(ParseContext ctx, int start, int end) {
        if (start >= end) return null;
        if (end - start == 1) {
            TokenSpan t = ctx.tokens().get(start);
            Object target = recognizeOperand(t).orElse(null);
            if (target == null) return null;
            return new Operand(target, List.of(t.span()));
        }
        // Multi-token arg: recursively parse the substring.
        TextSpan chunkSpan = new TextSpan(
                ctx.tokens().get(start).span().start(),
                ctx.tokens().get(end - 1).span().end());
        String text = ctx.draft() != null ? ctx.draft().text() : null;
        if (text == null) return null;
        if (chunkSpan.start() < 0 || chunkSpan.end() > text.length()
                || chunkSpan.start() > chunkSpan.end()) return null;
        String bracketed = text.substring(chunkSpan.start(), chunkSpan.end());
        FrameMap subFrame = ParseEngine.run(ctx.orchestrator(), bracketed, ParseParams.defaults());
        return new Operand(new FrameMapTarget(subFrame), List.of(chunkSpan));
    }

    /** Build a role binding from a resolved arg-operand, or null if absent. */
    private static BindingMap makeBinding(Operand op, ItemRef role, BigDecimal confidence) {
        if (op == null) return null;
        return new BindingMap(
                new Part<>(ItemRef.of(role), confidence, List.of()),
                List.of(),
                new Part<>(op.target(), confidence, op.spans()));
    }

    /** Either a literal/ref operand or a sub-FrameMap from a multi-token arg. */
    private record Operand(Object target, List<TextSpan> spans) {}

    // ==================================================================================
    // Render — emit {@code name(args)} from a frame whose predicate carries a
    // FunctionNotation lexeme.
    // ==================================================================================

    @Override
    public FrameMap render(FrameMap framemap, ParseParams params) {
        if (framemap == null || framemap.predicate() == null
                || framemap.predicate().value() == null
                || librarian() == null) {
            return framemap;
        }
        Optional<String> rendered = renderCall(framemap.predicate().value().iid(),
                operandTargetsByRole(framemap));
        return rendered.isPresent() ? framemap.withText(rendered.get()) : framemap;
    }

    /** Look up the predicate's FunctionNotation name and assemble {@code name(args)}. */
    private Optional<String> renderCall(ItemRef predicateIid, List<Object> targets) {
        Optional<Item> predItem = librarian().fetchItem(predicateIid);
        if (predItem.isEmpty()) return Optional.empty();
        Optional<String> nameOpt = lookupFunctionName(predItem.get());
        if (nameOpt.isEmpty()) return Optional.empty();

        StringBuilder out = new StringBuilder(nameOpt.get()).append('(');
        for (int i = 0; i < targets.size(); i++) {
            if (i > 0) out.append(", ");
            String t = renderTarget(targets.get(i));
            if (t == null) return Optional.empty();
            out.append(t);
        }
        out.append(')');
        return Optional.of(out.toString());
    }

    /**
     * Render one arg. A {@link BindingTarget.RefTarget} pointing at a stored
     * frame body is fetched and recursed via {@link #renderCall}; other targets
     * (literals, IID refs) format via {@link Object#toString}.
     */
    private String renderTarget(Object target) {
        if (target instanceof BindingTarget.RefTarget rt) {
            DatumRef cid = rt.asDatumId();
            Optional<Frame> innerFrame = librarian().fetchFrame(cid);
            if (innerFrame.isEmpty()) return renderOperandFallback(target);
            Body inner = innerFrame.get().body();
            if (!(inner.head() instanceof ItemRef ref)) return renderOperandFallback(target);
            List<Object> innerTargets = operandTargetsByRole(inner);
            return renderCall(ref.iid(), innerTargets).orElseGet(() -> renderOperandFallback(target));
        }
        return renderOperandFallback(target);
    }

    private static String renderOperandFallback(Object target) {
        if (target == null) return null;
        if (target instanceof String s) return s;
        return target.toString();
    }

    // ==================================================================================
    // FunctionNotation-lexeme lookup
    // ==================================================================================

    /**
     * Find the operator's FunctionNotation lexeme — the Lexeme frame whose VALUE
     * binding carries {@code FunctionNotation.KEY} as a qualifier — and return
     * its text.
     */
    private static Optional<String> lookupFunctionName(Item item) {
        return item.endorsedFramesByPredicate(ItemRef.iid(LexicalVocabulary.Lexeme.KEY))
                .map(FunctionNotation::readFunctionName)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    private static Optional<String> readFunctionName(Frame lexemeFrame) {
        ItemRef valueRole = ItemRef.iid(ThematicRole.Value.KEY);
        ItemRef fnLang = ItemRef.iid(KEY);
        return lexemeFrame.bindings()
                .filter(b -> valueRole.equals(b.role()))
                .filter(b -> hasQualifier(b, fnLang))
                .map(b -> b.target() instanceof String s ? s : null)
                .filter(java.util.Objects::nonNull)
                .findFirst();
    }

    /** True if the binding's qualifiers include a Sememe matching {@code id}. */
    private static boolean hasQualifier(Binding binding, ItemRef id) {
        for (CompoundKey.Qualifier q : binding.qualifiers()) {
            if (q instanceof CompoundKey.Sememe s && id.equals(s.id())) return true;
        }
        return false;
    }

    // ==================================================================================
    // Operand ordering — duplicate of OperatorNotation's THEME/GOAL convention,
    // applied to render-time arg extraction.
    // ==================================================================================

    private static List<Object> operandTargetsByRole(FrameMap framemap) {
        Object theme = null;
        Object goal = null;
        List<Object> others = new ArrayList<>();
        for (BindingMap bm : framemap.bindings()) {
            ItemRef role = bm.role().value() != null ? bm.role().value().iid() : null;
            Object target = bm.target().value();
            if (role == null) others.add(target);
            else if (theme == null && ItemRef.iid(ThematicRole.Theme.KEY).equals(role)) theme = target;
            else if (goal == null && ItemRef.iid(ThematicRole.Goal.KEY).equals(role))   goal = target;
            else others.add(target);
        }
        List<Object> ordered = new ArrayList<>();
        if (theme != null) ordered.add(theme);
        if (goal != null) ordered.add(goal);
        ordered.addAll(others);
        return ordered;
    }

    private static List<Object> operandTargetsByRole(Body body) {
        Object theme = null;
        Object goal = null;
        List<Object> others = new ArrayList<>();
        for (Binding b : body.bindings()) {
            ItemRef role = b.roleIid();
            Object target = b.target();
            if (theme == null && ItemRef.iid(ThematicRole.Theme.KEY).equals(role)) theme = target;
            else if (goal == null && ItemRef.iid(ThematicRole.Goal.KEY).equals(role))   goal = target;
            else others.add(target);
        }
        List<Object> ordered = new ArrayList<>();
        if (theme != null) ordered.add(theme);
        if (goal != null) ordered.add(goal);
        ordered.addAll(others);
        return ordered;
    }
}
