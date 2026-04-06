package dev.everydaythings.graph.ui.scene;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.ui.scene.SceneNode.NodeType;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Function;

/**
 * Compiles @Scene annotations into SceneNode trees.
 *
 * <p>Reads the unified {@code @Scene.*} annotation namespace and produces
 * a SceneNode tree suitable for the rendering pipeline:
 * SceneResolver → ScenePresenter → ScenePainter.
 */
public class SceneCompiler {

    private SceneCompiler() {}

    // ==================================================================================
    // Public API
    // ==================================================================================

    /**
     * Compile {@code @Scene} annotations on {@code data} into a SceneNode tree.
     */
    public static SceneNode compile(Object data) {
        if (data == null) return null;

        Class<?> clazz = data.getClass();
        Class<?> compileFrom = clazz;
        Scene typeScene = findTypeScene(clazz);
        if (typeScene != null && typeScene.as() != void.class
                && canCompile(typeScene.as())) {
            compileFrom = typeScene.as();
        }

        if (!has2DAnnotation(compileFrom) && !compileFrom.isAnnotationPresent(Scene.Root.class)) {
            return null;
        }

        CompileContext ctx = new CompileContext(data);
        SceneNode root = containerFromClass(compileFrom);
        if (root == null) root = SceneNode.vertical();

        applyEvents(compileFrom, root);
        applyStyles(compileFrom, root);
        compileMembers(compileFrom, data, root, ctx);

        return root;
    }

    /** Alias for backward compatibility during migration. */
    public static SceneNode compileToSceneNode(Object data) {
        return compile(data);
    }

    /**
     * Compile an annotated object to a root + handle pair.
     */
    @lombok.Getter
    @lombok.experimental.Accessors(fluent = true)
    public static class CompiledScene {
        private SceneNode root;
        private SceneNode handle;

        public CompiledScene() {}

        public CompiledScene root(SceneNode root) {
            this.root = root;
            return this;
        }

        public CompiledScene handle(SceneNode handle) {
            this.handle = handle;
            return this;
        }

        public static CompiledScene of(SceneNode root, SceneNode handle) {
            return new CompiledScene().root(root).handle(handle);
        }
    }

    public static CompiledScene compileScene(Object data) {
        if (data == null) return null;
        SceneNode root = compile(data);

        SceneNode handle = null;
        Class<?> clazz = data.getClass();

        Scene.Root rootAnn = clazz.getAnnotation(Scene.Root.class);
        if (rootAnn != null && rootAnn.handle() != Void.class) {
            try {
                handle = compile(rootAnn.handle().getDeclaredConstructor().newInstance());
            } catch (Exception ignored) {}
        }

        if (handle == null) {
            for (Method m : clazz.getDeclaredMethods()) {
                if (m.isAnnotationPresent(Scene.Handle.class) && Modifier.isPublic(m.getModifiers())) {
                    try {
                        Object result = m.invoke(data);
                        if (result instanceof SceneNode sn) handle = sn;
                        else if (result != null) handle = compile(result);
                    } catch (Exception ignored) {}
                    break;
                }
            }
        }

        if (handle == null) {
            for (Class<?> inner : clazz.getDeclaredClasses()) {
                if (inner.isAnnotationPresent(Scene.Handle.class)) {
                    try {
                        handle = compile(inner.getDeclaredConstructor().newInstance());
                    } catch (Exception ignored) {}
                    break;
                }
            }
        }

        return CompiledScene.of(root, handle);
    }

    public static boolean canCompile(Class<?> clazz) {
        if (hasStructuralAnnotation(clazz)) return true;
        for (Class<?> nested : clazz.getDeclaredClasses()) {
            if (hasStructuralAnnotation(nested)) return true;
        }
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.isAnnotationPresent(Scene.class)) return true;
        }
        for (Field f : clazz.getDeclaredFields()) {
            if (f.isAnnotationPresent(Scene.class) || hasStructuralAnnotation(f)) return true;
        }
        return false;
    }

    public static boolean has2DAnnotation(Class<?> clazz) {
        return clazz.isAnnotationPresent(Scene.Container.class)
                || clazz.isAnnotationPresent(Scene.Text.Literal.class)
                || clazz.isAnnotationPresent(Scene.Text.Semantic.class)
                || clazz.isAnnotationPresent(Scene.Image.class)
                || clazz.isAnnotationPresent(Scene.Shape.class)
                || clazz.isAnnotationPresent(Scene.Embed.class)
                || clazz.isAnnotationPresent(Scene.Root.class)
                || clazz.isAnnotationPresent(Scene.Handle.class);
    }

    static boolean hasStructuralAnnotation(Class<?> clazz) {
        return has2DAnnotation(clazz)
                || clazz.isAnnotationPresent(Scene.Body.class)
                || clazz.isAnnotationPresent(Scene.Face.class)
                || clazz.isAnnotationPresent(Scene.Transform.class)
                || clazz.isAnnotationPresent(Scene.Light.class)
                || clazz.isAnnotationPresent(Scene.Audio.class)
                || clazz.isAnnotationPresent(Scene.Environment.class)
                || clazz.isAnnotationPresent(Scene.Camera.class);
    }

    public static Object resolveModelValue(Object source, Class<?> modelClass) {
        try {
            Method live = source.getClass().getMethod("live");
            if (modelClass.isAssignableFrom(live.getReturnType())) return live.invoke(source);
        } catch (Exception ignored) {}
        try {
            Method model = source.getClass().getMethod("model");
            if (modelClass.isAssignableFrom(model.getReturnType())) return model.invoke(source);
        } catch (Exception ignored) {}
        if (modelClass.isInstance(source)) return source;
        return source;
    }

    public static double elevationToMeters(String spec) {
        if (spec == null || spec.isEmpty() || "0".equals(spec)) return 0;
        SizeValue sv = SizeValue.parse(spec, "m");
        if (sv == null) return 0;
        return switch (sv.unit()) {
            case "mm" -> sv.value() / 1000.0;
            case "cm" -> sv.value() / 100.0;
            case "m" -> sv.value();
            case "km" -> sv.value() * 1000.0;
            default -> sv.value();
        };
    }

    static double parseDimension(String spec) { return elevationToMeters(spec); }

    // ==================================================================================
    // Container from class annotation
    // ==================================================================================

    private static SceneNode containerFromClass(Class<?> clazz) {
        Scene.Container ann = clazz.getAnnotation(Scene.Container.class);
        if (ann == null) return null;

        String layout = ann.direction() == Scene.Direction.HORIZONTAL ? "horizontal" : "vertical";
        SceneNode c = SceneNode.container(layout);
        if (!ann.id().isEmpty()) c.id(ann.id());
        if (!ann.gap().isEmpty()) c.gap(ann.gap());
        if (!ann.backgroundColor().isEmpty()) c.backgroundColor(ann.backgroundColor());
        if (!ann.padding().isEmpty()) c.padding(ann.padding());
        if (!ann.width().isEmpty()) c.width(ann.width());
        if (!ann.height().isEmpty()) c.height(ann.height());
        if (!ann.align().isEmpty()) c.align(ann.align());
        if (!"visible".equals(ann.overflow())) c.overflow(ann.overflow());
        if (ann.style().length > 0) c.classes(ann.style());
        if (!ann.fontSize().isEmpty()) c.fontSize(ann.fontSize());
        if (!ann.fontFamily().isEmpty()) c.fontFamily(ann.fontFamily());
        if (!ann.aspectRatio().isEmpty()) {
            try { c.aspectRatio(Float.parseFloat(ann.aspectRatio())); }
            catch (NumberFormatException ignored) {}
        }
        if (!ann.depth().isEmpty()) c.elevation(elevationToMeters(ann.depth()));
        return c;
    }

    // ==================================================================================
    // Member compilation
    // ==================================================================================

    private record OrderedMember(AnnotatedElement element, int order, int index) {}

    private static void compileMembers(Class<?> clazz, Object data, SceneNode parent,
                                        CompileContext ctx) {
        List<OrderedMember> members = new ArrayList<>();
        int idx = 0;

        Class<?>[] nested = clazz.getDeclaredClasses();
        List<Class<?>> nestedList = new ArrayList<>(Arrays.asList(nested));
        Collections.reverse(nestedList);
        for (Class<?> n : nestedList) {
            if (n.isAnnotationPresent(Scene.Face.class)) continue;
            if (n.isAnnotationPresent(Scene.Handle.class)) continue;
            if (hasStructuralAnnotation(n)) members.add(new OrderedMember(n, extractOrder(n), idx++));
        }

        for (Field f : clazz.getDeclaredFields()) {
            if (hasStructuralAnnotation(f)) members.add(new OrderedMember(f, extractOrder(f), idx++));
        }

        for (Method m : clazz.getDeclaredMethods()) {
            if (Modifier.isStatic(m.getModifiers()) || !Modifier.isPublic(m.getModifiers())) continue;
            if (!hasStructuralAnnotation(m)) continue;
            if (m.isAnnotationPresent(Scene.Handle.class)) continue;
            members.add(new OrderedMember(m, extractOrder(m), idx++));
        }

        members.sort((a, b) -> {
            if (a.order >= 0 && b.order >= 0) return Integer.compare(a.order, b.order);
            if (a.order >= 0) return -1;
            if (b.order >= 0) return 1;
            return Integer.compare(a.index, b.index);
        });

        for (OrderedMember member : members) {
            try {
                if (member.element instanceof Class<?> n) {
                    Scene.Repeat repeat = n.getAnnotation(Scene.Repeat.class);
                    if (repeat != null) {
                        compileRepeatInline(n, data, parent, ctx, repeat);
                    } else {
                        SceneNode child = compileMember(n, data, ctx);
                        if (child != null) parent.add(child);
                    }
                } else if (member.element instanceof Field f) {
                    f.setAccessible(true);
                    Object value = Modifier.isStatic(f.getModifiers()) ? f.get(null) : f.get(data);
                    SceneNode child = wrapMemberResult(f, value, ctx);
                    if (child != null) parent.add(child);
                } else if (member.element instanceof Method m) {
                    Object value = m.invoke(data);
                    SceneNode child = wrapMemberResult(m, value, ctx);
                    if (child != null) parent.add(child);
                }
            } catch (Exception ignored) {}
        }
    }

    private static int extractOrder(AnnotatedElement element) {
        Scene.Container c = element.getAnnotation(Scene.Container.class);
        if (c != null) return c.order();
        Scene.Text.Literal tl = element.getAnnotation(Scene.Text.Literal.class);
        if (tl != null) return tl.order();
        Scene.Text.Semantic ts = element.getAnnotation(Scene.Text.Semantic.class);
        if (ts != null) return ts.order();
        Scene.Image img = element.getAnnotation(Scene.Image.class);
        if (img != null) return img.order();
        Scene.Shape sh = element.getAnnotation(Scene.Shape.class);
        if (sh != null) return sh.order();
        Scene.Embed em = element.getAnnotation(Scene.Embed.class);
        if (em != null) return em.order();
        Scene.On on = element.getAnnotation(Scene.On.class);
        if (on != null) return on.order();
        Scene.Body bd = element.getAnnotation(Scene.Body.class);
        if (bd != null) return bd.order();
        return -1;
    }

    private static SceneNode compileMember(Class<?> clazz, Object data, CompileContext ctx) {
        Scene.If sceneIf = clazz.getAnnotation(Scene.If.class);
        if (sceneIf != null && !ctx.evaluate(sceneIf.value())) return null;

        if (clazz.isAnnotationPresent(Scene.Container.class)) {
            SceneNode c = containerFromClass(clazz);
            if (c == null) c = SceneNode.vertical();
            compileMembers(clazz, data, c, ctx);
            return c;
        }

        Scene.Text.Literal literal = clazz.getAnnotation(Scene.Text.Literal.class);
        if (literal != null) {
            String content = !literal.bind().isEmpty()
                    ? ctx.resolveBinding(literal.bind()) : literal.content();
            SceneNode t = SceneNode.ofText(content);
            if (literal.style().length > 0) t.classes(literal.style());
            if (!literal.fontSize().isEmpty()) t.fontSize(literal.fontSize());
            if (!literal.fontFamily().isEmpty()) t.fontFamily(literal.fontFamily());
            if (!literal.fontWeight().isEmpty()) t.fontWeight(literal.fontWeight());
            if (!literal.fontStyle().isEmpty()) t.fontStyle(literal.fontStyle());
            if (!literal.textDecoration().isEmpty()) t.textDecoration(literal.textDecoration());
            if (!literal.textAlign().isEmpty()) t.textAlign(literal.textAlign());
            if (!literal.lineHeight().isEmpty()) t.lineHeight(literal.lineHeight());
            if (!literal.letterSpacing().isEmpty()) t.letterSpacing(literal.letterSpacing());
            return t;
        }

        Scene.Text.Semantic semantic = clazz.getAnnotation(Scene.Text.Semantic.class);
        if (semantic != null) {
            List<SceneNode.SemanticToken> tokens = new ArrayList<>();
            for (Scene.Text.Token tok : semantic.value()) {
                tokens.add(new SceneNode.SemanticToken()
                        .sememe(ItemID.fromString(tok.sememe()))
                        .features(Arrays.stream(tok.features()).map(ItemID::fromString).toList()));
            }
            SceneNode t = tokens.isEmpty() ? SceneNode.ofText("") : SceneNode.ofTokens(tokens);
            if (semantic.style().length > 0) t.classes(semantic.style());
            return t;
        }

        Scene.Embed embed = clazz.getAnnotation(Scene.Embed.class);
        if (embed != null && !embed.bind().isEmpty()) {
            Object resolved = ctx.resolveValue(embed.bind());
            if (resolved instanceof SceneNode sn) return sn;
            if (resolved != null) return compile(resolved);
        }

        Scene.Image image = clazz.getAnnotation(Scene.Image.class);
        if (image != null) {
            if (!image.bind().isEmpty()) {
                Object resolved = ctx.resolveValue(image.bind());
                if (resolved != null) {
                    SceneNode body = bodyFromObject(resolved);
                    if (!image.size().isEmpty()) { body.width(image.size()); body.height(image.size()); }
                    return body;
                }
            }
            SceneNode body = SceneNode.ofGlyph(image.alt());
            if (!image.size().isEmpty()) { body.width(image.size()); body.height(image.size()); }
            return body;
        }

        return null;
    }

    private static void compileRepeatInline(Class<?> clazz, Object data, SceneNode parent,
                                             CompileContext ctx, Scene.Repeat repeat) {
        Object collection = ctx.resolveValue(repeat.bind());
        if (collection == null) return;

        Iterable<?> items;
        if (collection instanceof Iterable<?> it) items = it;
        else if (collection.getClass().isArray()) items = Arrays.asList((Object[]) collection);
        else items = List.of(collection);

        int index = 0;
        for (Object item : items) {
            CompileContext itemCtx = ctx.withItem(item, index, repeat.itemVar(), repeat.indexVar());

            Scene.If sceneIf = clazz.getAnnotation(Scene.If.class);
            if (sceneIf != null && !itemCtx.evaluate(sceneIf.value())) { index++; continue; }

            SceneNode c = containerFromClass(clazz);
            if (c == null) c = SceneNode.vertical();

            if (c.id() != null && c.id().startsWith("bind:")) {
                c.id(itemCtx.resolveBinding(c.id().substring(5)));
            }

            for (Scene.State state : clazz.getAnnotationsByType(Scene.State.class)) {
                if (itemCtx.evaluate(state.when())) {
                    List<String> classes = c.classes() != null
                            ? new ArrayList<>(c.classes()) : new ArrayList<>();
                    Collections.addAll(classes, state.style());
                    c.classes(classes.toArray(new String[0]));
                }
            }

            for (Scene.On on : clazz.getAnnotationsByType(Scene.On.class)) {
                String target = on.target().contains("$")
                        ? itemCtx.resolveBinding(on.target()) : on.target();
                c.on(on.event(), on.action(), target);
            }

            compileMembers(clazz, data, c, itemCtx);
            parent.add(c);
            index++;
        }
    }

    private static SceneNode wrapMemberResult(AnnotatedElement m, Object result,
                                               CompileContext ctx) {
        if (result == null) return null;

        SceneNode wrapped = null;

        Scene.Container containerAnn = m.getAnnotation(Scene.Container.class);
        if (containerAnn != null && result instanceof SceneNode node) {
            String layout = containerAnn.direction() == Scene.Direction.HORIZONTAL
                    ? "horizontal" : "vertical";
            SceneNode c = SceneNode.container(layout);
            if (!containerAnn.id().isEmpty()) c.id(containerAnn.id());
            if (!containerAnn.gap().isEmpty()) c.gap(containerAnn.gap());
            if (!containerAnn.backgroundColor().isEmpty()) c.backgroundColor(containerAnn.backgroundColor());
            if (!containerAnn.padding().isEmpty()) c.padding(containerAnn.padding());
            if (!containerAnn.width().isEmpty()) c.width(containerAnn.width());
            if (!containerAnn.height().isEmpty()) c.height(containerAnn.height());
            if (!"visible".equals(containerAnn.overflow())) c.overflow(containerAnn.overflow());
            if (containerAnn.style().length > 0) c.classes(containerAnn.style());
            if (!containerAnn.fontSize().isEmpty()) c.fontSize(containerAnn.fontSize());
            c.add(node);
            wrapped = c;
        }

        if (wrapped == null) {
            Scene.Text.Semantic semanticAnn = m.getAnnotation(Scene.Text.Semantic.class);
            if (semanticAnn != null) {
                SceneNode t;
                if (result instanceof List<?> list) {
                    List<SceneNode.SemanticToken> tokens = new ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof SceneNode.SemanticToken st) tokens.add(st);
                    }
                    t = SceneNode.ofTokens(tokens);
                } else if (result instanceof ItemID iid) {
                    t = SceneNode.ofSememe(iid);
                } else {
                    ItemID iid = extractItemId(result);
                    t = iid != null ? SceneNode.ofSememe(iid) : SceneNode.ofText(result.toString());
                }
                if (semanticAnn.style().length > 0) t.classes(semanticAnn.style());
                if (!semanticAnn.fontSize().isEmpty()) t.fontSize(semanticAnn.fontSize());
                if (!semanticAnn.fontFamily().isEmpty()) t.fontFamily(semanticAnn.fontFamily());
                if (!semanticAnn.fontWeight().isEmpty()) t.fontWeight(semanticAnn.fontWeight());
                if (!semanticAnn.fontStyle().isEmpty()) t.fontStyle(semanticAnn.fontStyle());
                if (!semanticAnn.textDecoration().isEmpty()) t.textDecoration(semanticAnn.textDecoration());
                if (!semanticAnn.textAlign().isEmpty()) t.textAlign(semanticAnn.textAlign());
                if (!semanticAnn.lineHeight().isEmpty()) t.lineHeight(semanticAnn.lineHeight());
                if (!semanticAnn.letterSpacing().isEmpty()) t.letterSpacing(semanticAnn.letterSpacing());
                wrapped = t;
            }
        }

        if (wrapped == null) {
            Scene.Text.Literal literalAnn = m.getAnnotation(Scene.Text.Literal.class);
            if (literalAnn != null) {
                SceneNode t = SceneNode.ofText(result.toString());
                if (!literalAnn.format().isEmpty()) t.format(ItemID.fromString(literalAnn.format()));
                if (literalAnn.style().length > 0) t.classes(literalAnn.style());
                if (!literalAnn.fontSize().isEmpty()) t.fontSize(literalAnn.fontSize());
                if (!literalAnn.fontFamily().isEmpty()) t.fontFamily(literalAnn.fontFamily());
                if (!literalAnn.fontWeight().isEmpty()) t.fontWeight(literalAnn.fontWeight());
                if (!literalAnn.fontStyle().isEmpty()) t.fontStyle(literalAnn.fontStyle());
                if (!literalAnn.textDecoration().isEmpty()) t.textDecoration(literalAnn.textDecoration());
                if (!literalAnn.textAlign().isEmpty()) t.textAlign(literalAnn.textAlign());
                if (!literalAnn.lineHeight().isEmpty()) t.lineHeight(literalAnn.lineHeight());
                if (!literalAnn.letterSpacing().isEmpty()) t.letterSpacing(literalAnn.letterSpacing());
                wrapped = t;
            }
        }

        if (wrapped == null) {
            Scene.Image imageAnn = m.getAnnotation(Scene.Image.class);
            if (imageAnn != null) {
                SceneNode b = SceneNode.ofGlyph(result.toString());
                if (imageAnn.style().length > 0) b.classes(imageAnn.style());
                wrapped = b;
            }
        }

        if (wrapped == null && m.isAnnotationPresent(Scene.Embed.class) && result instanceof SceneNode sn) {
            wrapped = sn;
        }

        if (wrapped == null && result instanceof SceneNode sn) {
            wrapped = sn;
        }

        if (wrapped != null) applyEvents(m, wrapped);
        return wrapped;
    }

    /**
     * Collect @Scene.Style annotations from the class hierarchy and apply
     * them to the root SceneNode as when-blocks. The SceneResolver will
     * match these against node classes/IDs during resolution.
     */
    private static void applyStyles(Class<?> clazz, SceneNode root) {
        // Collect from entire class hierarchy (supertypes first for proper cascade)
        List<Scene.Style> allStyles = new ArrayList<>();
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            Scene.Style[] styles = c.getAnnotationsByType(Scene.Style.class);
            // Prepend superclass styles so they're overridden by subclass
            allStyles.addAll(0, Arrays.asList(styles));
        }

        // Store on root node — the resolver applies them during tree walking
        for (Scene.Style style : allStyles) {
            String when = style.when();
            if (!style.color().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "foreground", style.color());
            if (!style.background().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "backgroundColor", style.background());
            if (!style.backgroundColor().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "backgroundColor", style.backgroundColor());
            if (!style.backgroundImage().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "backgroundImage", style.backgroundImage());
            if (!style.backgroundSize().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "backgroundSize", style.backgroundSize());
            if (!style.fontSize().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "fontSize", style.fontSize());
            if (!style.fontFamily().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "fontFamily", style.fontFamily());
            if (!style.fontWeight().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "fontWeight", style.fontWeight());
            if (!style.fontStyle().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "fontStyle", style.fontStyle());
            if (!style.textDecoration().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "textDecoration", style.textDecoration());
            if (!style.textAlign().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "textAlign", style.textAlign());
            if (!style.lineHeight().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "lineHeight", style.lineHeight());
            if (!style.letterSpacing().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "letterSpacing", style.letterSpacing());
            if (!style.textOverflow().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "textOverflow", style.textOverflow());
            if (!style.whiteSpace().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "whiteSpace", style.whiteSpace());
            if (!style.opacity().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "opacity", style.opacity());
            if (!style.padding().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "padding", style.padding());
            if (!style.border().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "border", style.border());
            if (!style.borderWidth().isEmpty()) compileBorderMultiValue(root, when, "Width", style.borderWidth());
            if (!style.borderStyle().isEmpty()) compileBorderMultiValue(root, when, "Style", style.borderStyle());
            if (!style.borderColor().isEmpty()) compileBorderMultiValue(root, when, "Color", style.borderColor());
            if (!style.borderTopWidth().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "borderTopWidth", style.borderTopWidth());
            if (!style.borderRightWidth().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "borderRightWidth", style.borderRightWidth());
            if (!style.borderBottomWidth().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "borderBottomWidth", style.borderBottomWidth());
            if (!style.borderLeftWidth().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "borderLeftWidth", style.borderLeftWidth());
            if (!style.borderTopStyle().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "borderTopStyle", style.borderTopStyle());
            if (!style.borderRightStyle().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "borderRightStyle", style.borderRightStyle());
            if (!style.borderBottomStyle().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "borderBottomStyle", style.borderBottomStyle());
            if (!style.borderLeftStyle().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "borderLeftStyle", style.borderLeftStyle());
            if (!style.borderTopColor().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "borderTopColor", style.borderTopColor());
            if (!style.borderRightColor().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "borderRightColor", style.borderRightColor());
            if (!style.borderBottomColor().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "borderBottomColor", style.borderBottomColor());
            if (!style.borderLeftColor().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "borderLeftColor", style.borderLeftColor());
            if (!style.transition().isEmpty()) compileTransitionShorthand(root, when, style.transition());
            if (!style.transitionProperty().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "transitionProperty", style.transitionProperty());
            if (!style.transitionDuration().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "transitionDuration", style.transitionDuration());
            if (!style.transitionEasing().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "transitionEasing", style.transitionEasing());
            if (!style.transitionDelay().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "transitionDelay", style.transitionDelay());
            if (!style.radius().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "corner", style.radius());
            if (!style.rotation().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "rotation", style.rotation());
            if (!style.rotationX().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "rotationX", style.rotationX());
            if (!style.rotationY().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "rotationY", style.rotationY());
            if (!style.rotationZ().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "rotationZ", style.rotationZ());
            if (!style.scale().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "scale", style.scale());
            if (!style.scaleX().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "scaleX", style.scaleX());
            if (!style.scaleY().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "scaleY", style.scaleY());
            if (!style.scaleZ().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "scaleZ", style.scaleZ());
            if (!style.transformOrigin().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "transformOrigin", style.transformOrigin());
            if (!style.anchorTop().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "anchorTop", style.anchorTop());
            if (!style.anchorRight().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "anchorRight", style.anchorRight());
            if (!style.anchorBottom().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "anchorBottom", style.anchorBottom());
            if (!style.anchorLeft().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "anchorLeft", style.anchorLeft());
            if (!style.animationDuration().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "animationDuration", style.animationDuration());
            if (!style.animationIterationCount().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "animationIterationCount", style.animationIterationCount());
            if (!style.animationDirection().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "animationDirection", style.animationDirection());
            if (!style.animationEasing().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "animationEasing", style.animationEasing());
            if (!style.animationDelay().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "animationDelay", style.animationDelay());
            if (!style.animationFillMode().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "animationFillMode", style.animationFillMode());
            if (!style.animationPlayState().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "animationPlayState", style.animationPlayState());
            if (!style.minWidth().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "minWidth", style.minWidth());
            if (!style.maxWidth().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "maxWidth", style.maxWidth());
            if (!style.minHeight().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "minHeight", style.minHeight());
            if (!style.maxHeight().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "maxHeight", style.maxHeight());
            if (!style.display().isEmpty()) root.when(when.isEmpty() ? "$always" : when, "visible", "hidden".equals(style.display()) ? "false" : "true");
        }
    }

    /**
     * Expand a CSS multi-value border property (1-4 values) to per-side when-blocks.
     * "2px" → all 4 sides. "2px 1px" → top/bottom=2px, right/left=1px. etc.
     */
    private static void compileBorderMultiValue(SceneNode root, String when, String suffix, String value) {
        String w = when.isEmpty() ? "$always" : when;
        String[] parts = value.trim().split("\\s+");
        String top, right, bottom, left;
        switch (parts.length) {
            case 1 -> { top = right = bottom = left = parts[0]; }
            case 2 -> { top = bottom = parts[0]; right = left = parts[1]; }
            case 3 -> { top = parts[0]; right = left = parts[1]; bottom = parts[2]; }
            default -> { top = parts[0]; right = parts[1]; bottom = parts[2]; left = parts[3]; }
        }
        root.when(w, "borderTop" + suffix, top);
        root.when(w, "borderRight" + suffix, right);
        root.when(w, "borderBottom" + suffix, bottom);
        root.when(w, "borderLeft" + suffix, left);
    }

    /**
     * Parse CSS transition shorthand: "property duration easing delay".
     * E.g., "background 0.3s ease-out 0.1s" or "all 0.3s ease-out".
     */
    private static void compileTransitionShorthand(SceneNode root, String when, String shorthand) {
        String w = when.isEmpty() ? "$always" : when;
        String[] parts = shorthand.trim().split("\\s+");
        if (parts.length >= 1) root.when(w, "transitionProperty", parts[0]);
        if (parts.length >= 2) root.when(w, "transitionDuration", parts[1]);
        if (parts.length >= 3) root.when(w, "transitionEasing", parts[2]);
        if (parts.length >= 4) root.when(w, "transitionDelay", parts[3]);
    }

    private static SceneNode bodyFromObject(Object obj) {
        String glyph = invokeStringGetter(obj, "symbol");
        String image = invokeStringGetter(obj, "imageKey");
        String model = invokeStringGetter(obj, "modelKey");

        if (glyph != null || image != null || model != null) {
            SceneNode body = SceneNode.body();
            body.glyph(glyph).image(image).model(model);
            try {
                Method m = obj.getClass().getMethod("modelColor");
                Object result = m.invoke(obj);
                if (result instanceof Integer color && color != -1) {
                    body.fill(String.format("#%06X", color & 0xFFFFFF));
                }
            } catch (Exception ignored) {}
            return body;
        }
        return SceneNode.ofGlyph(obj.toString());
    }

    private static void applyEvents(AnnotatedElement element, SceneNode target) {
        for (Scene.On on : element.getAnnotationsByType(Scene.On.class)) {
            target.on(on.event(), on.action(), on.target());
        }
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    private static boolean hasStructuralAnnotation(Field field) {
        return field.isAnnotationPresent(Scene.Container.class)
            || field.isAnnotationPresent(Scene.Text.Literal.class)
            || field.isAnnotationPresent(Scene.Text.Semantic.class)
            || field.isAnnotationPresent(Scene.Image.class)
            || field.isAnnotationPresent(Scene.Shape.class);
    }

    private static boolean hasStructuralAnnotation(Method method) {
        return method.isAnnotationPresent(Scene.Container.class)
                || method.isAnnotationPresent(Scene.Text.Literal.class)
                || method.isAnnotationPresent(Scene.Text.Semantic.class)
                || method.isAnnotationPresent(Scene.Image.class)
                || method.isAnnotationPresent(Scene.Shape.class)
                || method.isAnnotationPresent(Scene.Embed.class)
                || method.isAnnotationPresent(Scene.Handle.class);
    }

    private static Scene findTypeScene(Class<?> clazz) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            Scene scene = c.getAnnotation(Scene.class);
            if (scene != null) return scene;
        }
        return null;
    }

    private static String invokeStringGetter(Object obj, String methodName) {
        try {
            Method m = obj.getClass().getMethod(methodName);
            Object result = m.invoke(obj);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static ItemID extractItemId(Object obj) {
        try {
            Method iidMethod = obj.getClass().getMethod("iid");
            Object iid = iidMethod.invoke(obj);
            if (iid instanceof ItemID id) return id;
        } catch (Exception ignored) {}
        return null;
    }

    // ==================================================================================
    // Compile Context (binding resolution during compilation)
    // ==================================================================================

    private static class CompileContext {
        private final Object value;
        private final Object item;
        private final int index;
        private final String itemVar;
        private final String indexVar;

        CompileContext(Object value) {
            this.value = value;
            this.item = null;
            this.index = -1;
            this.itemVar = "item";
            this.indexVar = "index";
        }

        private CompileContext(Object value, Object item, int index,
                               String itemVar, String indexVar) {
            this.value = value;
            this.item = item;
            this.index = index;
            this.itemVar = itemVar;
            this.indexVar = indexVar;
        }

        CompileContext withItem(Object item, int index, String itemVar, String indexVar) {
            return new CompileContext(value, item, index, itemVar, indexVar);
        }

        boolean evaluate(String condition) {
            if (condition == null || condition.isEmpty()) return true;
            boolean negate = condition.startsWith("!");
            String expr = negate ? condition.substring(1) : condition;
            boolean result = evaluatePositive(expr);
            return negate ? !result : result;
        }

        private boolean evaluatePositive(String expr) {
            if (expr.contains(" == ")) {
                String[] parts = expr.split(" == ", 2);
                return Objects.equals(resolveValue(parts[0].trim()), resolveValue(parts[1].trim()));
            }
            if (expr.contains(" != ")) {
                String[] parts = expr.split(" != ", 2);
                return !Objects.equals(resolveValue(parts[0].trim()), resolveValue(parts[1].trim()));
            }
            if (expr.startsWith("$")) {
                Object resolved = resolveValue(expr);
                return isTruthy(resolved);
            }
            if ("value".equals(expr)) return isTruthy(value);
            if (expr.startsWith("value.")) return isTruthy(getProperty(value, expr.substring(6)));
            if (value != null) {
                Object valueProp = getProperty(value, expr);
                if (valueProp != null) return isTruthy(valueProp);
            }
            return false;
        }

        private boolean isTruthy(Object val) {
            if (val == null) return false;
            if (val instanceof Boolean b) return b;
            if (val instanceof String s) return !s.isEmpty();
            if (val instanceof Number n) return n.doubleValue() != 0;
            if (val instanceof Collection<?> c) return !c.isEmpty();
            return true;
        }

        String resolveBinding(String bindExpr) {
            if (bindExpr == null || bindExpr.isEmpty()) return "";
            Object resolved = resolveValue(bindExpr);
            return resolved != null ? resolved.toString() : "";
        }

        Object resolveValue(String bindExpr) {
            if (bindExpr == null || bindExpr.isEmpty()) return null;

            if (bindExpr.startsWith("$")) {
                String varName = bindExpr.substring(1);
                int dotIdx = varName.indexOf('.');
                String base = dotIdx > 0 ? varName.substring(0, dotIdx) : varName;
                String rest = dotIdx > 0 ? varName.substring(dotIdx + 1) : "";

                Object baseValue = null;
                if (base.equals(itemVar) || base.equals("item")) baseValue = item;
                else if (base.equals(indexVar) || base.equals("index")) baseValue = index;

                if (baseValue != null && !rest.isEmpty()) return getProperty(baseValue, rest);
                return baseValue;
            }

            if ("value".equals(bindExpr)) return value;
            if (bindExpr.startsWith("value.")) return getProperty(value, bindExpr.substring(6));
            if (value != null) return getProperty(value, bindExpr);
            return null;
        }

        private Object getProperty(Object obj, String path) {
            if (obj == null || path.isEmpty()) return null;
            String[] parts = path.split("\\.");
            Object current = obj;
            for (String part : parts) {
                if (current == null) return null;
                current = invokeGetter(current, part);
            }
            return current;
        }

        private Object invokeGetter(Object obj, String propertyName) {
            Class<?> clazz = obj.getClass();
            try { return clazz.getMethod(propertyName).invoke(obj); } catch (Exception ignored) {}
            String getter = "get" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
            try { return clazz.getMethod(getter).invoke(obj); } catch (Exception ignored) {}
            String is = "is" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
            try { return clazz.getMethod(is).invoke(obj); } catch (Exception ignored) {}
            if (obj instanceof Map<?, ?> map) return map.get(propertyName);
            return null;
        }
    }
}
