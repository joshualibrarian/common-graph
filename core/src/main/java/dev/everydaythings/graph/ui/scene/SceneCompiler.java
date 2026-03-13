package dev.everydaythings.graph.ui.scene;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.id.HashID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.ui.scene.surface.ChipsSurface;
import dev.everydaythings.graph.ui.scene.surface.HandleSurface;
import dev.everydaythings.graph.ui.scene.surface.ListSurface;
import dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;
import dev.everydaythings.graph.ui.scene.surface.layout.ConstraintSurface;
import dev.everydaythings.graph.ui.scene.surface.layout.FlexSurface;
import dev.everydaythings.graph.ui.scene.surface.primitive.ContainerSurface;
import dev.everydaythings.graph.ui.scene.surface.primitive.ImageSurface;
import dev.everydaythings.graph.ui.scene.surface.primitive.TextSurface;
import dev.everydaythings.graph.ui.scene.surface.tree.TreeSurface;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.*;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Unified compiler for @Scene annotations.
 *
 * <p>SceneCompiler handles the unified {@code @Scene.*} annotation namespace,
 * covering both 2D structural primitives (Container, Text, Image, Shape) and
 * 3D scene elements (Body, Face, Transform, Light, Audio, Environment, Camera).
 *
 * <p>This compiler handles the same annotations in different contexts:
 *
 * <h2>1. Schema Classes (extends SurfaceSchema or SceneSchema)</h2>
 * <p>Nested static classes define the visual structure template:
 * <pre>{@code
 * @Scene.Container(direction = Direction.VERTICAL)
 * class PersonScene extends SceneSchema<Person> {
 *
 *     @Scene.Text(bind = "value.name", style = "heading")
 *     static class Name {}
 *
 *     @Scene.Text(bind = "value.email", style = "muted")
 *     static class Email {}
 * }
 * }</pre>
 *
 * <h2>2. Model Classes (extends SceneModel)</h2>
 * <p>Methods with annotations provide dynamic content for layout regions:
 * <pre>{@code
 * @Scene(as = ConstraintSurface.class)
 * class ItemModel extends SceneModel {
 *
 *     @Scene(id = "header")
 *     @ConstraintSurface.Layout(top = "0", height = "fit")
 *     public SurfaceSchema header() { return ...; }
 * }
 * }</pre>
 *
 * <h2>3. Data Models (plain POJOs)</h2>
 * <p>Annotations on fields describe how to render the data:
 * <pre>{@code
 * class Person {
 *     @Scene.Text(style = "heading") String name;
 *     @Scene.Text(style = "muted") String email;
 * }
 *
 * // Compile to view:
 * View view = SceneCompiler.compile(person);
 * }</pre>
 *
 * <h2>3D Annotations</h2>
 * <p>3D annotations are emitted as calls to {@link SceneRenderer} methods:
 * <ul>
 *   <li>{@code @Scene.Body} &rarr; {@link SceneRenderer#body} or {@link SceneRenderer#meshBody}</li>
 *   <li>{@code @Scene.Face} &rarr; {@link SceneRenderer#beginFace}/{@link SceneRenderer#endFace}</li>
 *   <li>{@code @Scene.Transform} &rarr; {@link SceneRenderer#pushTransform}/{@link SceneRenderer#popTransform}</li>
 *   <li>{@code @Scene.Light} &rarr; {@link SceneRenderer#light}</li>
 *   <li>{@code @Scene.Audio} &rarr; {@link SceneRenderer#audio3d}</li>
 *   <li>{@code @Scene.Environment} &rarr; {@link SceneRenderer#environment}</li>
 *   <li>{@code @Scene.Camera} &rarr; {@link SceneRenderer#camera}</li>
 *   <li>{@code depth} attribute on Container/Shape &rarr; {@link SceneRenderer#depth}</li>
 * </ul>
 *
 * @see Scene
 * @see SceneRenderer
 * @see SceneSchema
 * @see SurfaceSchema
 * @see SceneModel
 */
public final class SceneCompiler {

    private SceneCompiler() {} // Static utility

    /** Cache of compiled structures per class. */
    private static final Map<Class<?>, ViewNode> STRUCTURE_CACHE = new ConcurrentHashMap<>();

    /**
     * Thread-local item resolver for enriching ItemID fields during compilation.
     *
     * <p>Set via {@link #compile(Object, SceneMode, Function)}
     * and read by {@link #valueToScene} to resolve ItemIDs into HandleSurfaces.
     */
    private static final ThreadLocal<Function<
            ItemID,
            Optional<Item>>> ITEM_RESOLVER = new ThreadLocal<>();

    /** Recursion guard for default value rendering of object graphs. */
    private static final ThreadLocal<Set<Object>> DEFAULT_RENDER_GUARD =
            ThreadLocal.withInitial(() -> Collections.newSetFromMap(new IdentityHashMap<>()));

    // ==================================================================================
    // Public API
    // ==================================================================================

    /**
     * Check if a class can be compiled (has @Scene.* annotations in any position).
     */
    public static boolean canCompile(Class<?> clazz) {
        // Check for structural annotations
        if (hasStructuralAnnotation(clazz)) return true;

        // Check nested classes
        for (Class<?> nested : clazz.getDeclaredClasses()) {
            if (hasStructuralAnnotation(nested)) return true;
        }

        // Check for @Scene on methods (SceneModel pattern)
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.isAnnotationPresent(Scene.class)) return true;
        }

        // Check for @Scene on fields (data model pattern)
        for (Field f : clazz.getDeclaredFields()) {
            if (f.isAnnotationPresent(Scene.class)) return true;
            if (hasStructuralAnnotation(f)) return true;
        }

        return false;
    }

    /**
     * Compile and render an object to output.
     *
     * <p>Dispatches based on class type:
     * <ul>
     *   <li>SurfaceSchema/SceneSchema &rarr; render from nested static class structure</li>
     *   <li>SceneModel &rarr; render by calling annotated methods</li>
     *   <li>Other &rarr; render from annotated fields</li>
     * </ul>
     */
    public static void render(Object obj, SurfaceRenderer out) {
        if (obj == null) return;

        if (obj instanceof SurfaceSchema<?> schema) {
            renderFromStructure(schema, out);
        } else if (obj instanceof SceneModel model) {
            renderFromMethods(model, out);
        } else {
            renderFromFields(obj, out);
        }
    }

    /**
     * Get the compiled structure for a class (cached).
     */
    public static ViewNode getCompiled(Class<?> clazz) {
        return STRUCTURE_CACHE.computeIfAbsent(clazz, SceneCompiler::compileStructure);
    }

    /**
     * Clear the compilation cache.
     */
    public static void clearCache() {
        STRUCTURE_CACHE.clear();
    }

    /**
     * Resolve the model value from an object, checking for live() and model() methods.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>{@code live()} &mdash; real-time snapshot (if return type matches)</li>
     *   <li>{@code model()} &mdash; stored model (if return type matches)</li>
     *   <li>The source itself (if it IS the model type)</li>
     *   <li>The source as fallback</li>
     * </ol>
     */
    public static Object resolveModelValue(Object source, Class<?> modelClass) {
        try {
            Method live = source.getClass().getMethod("live");
            if (modelClass.isAssignableFrom(live.getReturnType())) {
                return live.invoke(source);
            }
        } catch (Exception ignored) {}

        try {
            Method model = source.getClass().getMethod("model");
            if (modelClass.isAssignableFrom(model.getReturnType())) {
                return model.invoke(source);
            }
        } catch (Exception ignored) {}

        if (modelClass.isInstance(source)) return source;
        return source;
    }

    /**
     * Compile any annotated object to a View.
     */
    public static View compile(Object data) {
        return compile(data, SceneMode.FULL);
    }

    /**
     * Compile with an item resolver for enriching ItemID fields into HandleSurfaces.
     */
    public static View compile(Object data, SceneMode mode,
                               Function<ItemID,
                                       Optional<Item>> resolver) {
        ITEM_RESOLVER.set(resolver);
        try {
            return compile(data, mode);
        } finally {
            ITEM_RESOLVER.remove();
        }
    }

    /**
     * Compile any annotated object to a View with the specified mode.
     */
    public static View compile(Object data, SceneMode mode) {
        if (data == null) {
            return View.empty();
        }

        Class<?> clazz = data.getClass();

        // Check for type-level @Scene(as = ...) with annotated methods
        // This handles both SceneModel subclasses and plain classes with method annotations
        Scene typeScene = findTypeScene(clazz);
        if (typeScene != null && typeScene.as() != SceneSchema.class) {
            // Check if this class has annotated methods (layout model pattern)
            if (hasAnnotatedMethods(clazz)) {
                SurfaceSchema<?> schema = compileFromMethods(data);
                if (schema != null) {
                    return View.of(schema).mode(mode);
                }
            }

            Class<?> asClass = typeScene.as();
            if (SurfaceSchema.class.isAssignableFrom(asClass)) {
                // Fall back to instantiating the schema directly
                @SuppressWarnings("unchecked")
                SurfaceSchema<?> schema = instantiateSchema(
                        (Class<? extends SurfaceSchema>) asClass, data, mode);
                if (schema != null) {
                    return View.of(schema).mode(mode);
                }
            } else if (canCompile(asClass)) {
                // as points to a model class with surface annotations (not a SurfaceSchema)
                Object modelValue = resolveModelValue(data, asClass);
                SurfaceSchema<Object> wrapper = new SurfaceSchema<>() {};
                wrapper.value(modelValue);
                wrapper.structureClass(asClass);
                return View.of(wrapper).mode(mode);
            }
        }

        // Compile based on class type
        if (data instanceof SurfaceSchema<?> schema) {
            return View.of(schema).mode(mode);
        } else if (data instanceof SceneModel model) {
            return View.of(model.toSurface()).mode(mode);
        } else if (has2DAnnotation(clazz)) {
            // Class itself has @Scene.Container/@Scene.Text/etc — compile from its own annotations
            SurfaceSchema<Object> wrapper = new SurfaceSchema<>() {};
            wrapper.value(data);
            wrapper.structureClass(clazz);
            return View.of(wrapper).mode(mode);
        } else {
            // Data model - compile fields
            return compileDataModel(data, mode);
        }
    }

    // ==================================================================================
    // Binding Resolution for Stored Templates
    // ==================================================================================

    /**
     * Render a stored surface template, resolving dynamic bindings against a live value.
     *
     * <p>This is the render path for CBOR-stored surface trees. The template was built
     * programmatically (or compiled from annotations) with {@code bind} expressions
     * on TextSurface, ImageSurface, and {@code visibleWhen} on any surface. At render
     * time, this method walks the tree, resolves those expressions against the provided
     * value, and emits rendering instructions.
     *
     * <p>For surfaces without bindings, falls through to their normal {@code render(out)}.
     *
     * @param root  The surface template (typically hydrated from CBOR)
     * @param value The live data to resolve bindings against
     * @param out   The renderer
     */
    public static void renderWithBindings(SurfaceSchema<?> root, Object value, SurfaceRenderer out) {
        if (root == null) return;
        BindingResolver resolver = new BindingResolver(value);
        renderNodeWithBindings(root, resolver, out);
    }

    /**
     * Recursively render a surface node, resolving bindings.
     */
    private static void renderNodeWithBindings(SurfaceSchema<?> node, BindingResolver resolver, SurfaceRenderer out) {
        // Check visibleBind — skip if falsy
        String visBind = node.visibleBind();
        if (visBind != null && !visBind.isEmpty()) {
            Object visValue = resolver.resolve(visBind);
            if (!resolver.isTruthy(visValue)) {
                return;  // Hidden by binding
            }
        } else if (!node.visible()) {
            return;  // Hidden by static flag
        }

        // Handle TextSurface with bind
        if (node instanceof TextSurface text) {
            String bind = text.bind();
            if (bind != null && !bind.isEmpty()) {
                Object resolved = resolver.resolve(bind);
                String content = resolved != null ? resolved.toString() : "";
                node.emitCommonProperties(out);
                String format = text.format();
                if (format != null && !format.isEmpty()) {
                    out.formattedText(content, format, node.style());
                } else {
                    out.text(content, node.style());
                }
                return;
            }
            // No bind — render normally
            text.render(out);
            return;
        }

        // Handle ImageSurface with bind
        if (node instanceof ImageSurface img) {
            String bind = img.bind();
            if (bind != null && !bind.isEmpty()) {
                Object resolved = resolver.resolve(bind);
                String alt;
                if (resolved != null && !(resolved instanceof String)) {
                    // Multi-fidelity object: prefer symbol() for text fallback
                    Object sym = resolver.getPropertyFromObject(resolved, "symbol");
                    alt = (sym instanceof String s && !s.isEmpty()) ? s : resolved.toString();
                } else {
                    alt = resolved != null ? resolved.toString() : "";
                }
                // Try to get resource from a deeper property if the resolved object has one
                String resource = img.resource();
                if (resource == null && resolved != null) {
                    Object resKey = resolver.getPropertyFromObject(resolved, "imageKey");
                    if (resKey instanceof String s && !s.isEmpty()) {
                        resource = s;
                    }
                }
                // Extract model hints for 3D rendering
                String modelKey = null;
                int modelColor = -1;
                if (resolved != null && !(resolved instanceof String)) {
                    Object mdl = resolver.getPropertyFromObject(resolved, "modelKey");
                    if (mdl instanceof String s && !s.isEmpty()) {
                        modelKey = s;
                    }
                    Object mc = resolver.getPropertyFromObject(resolved, "modelColor");
                    if (mc instanceof Number n) {
                        modelColor = n.intValue();
                    }
                }
                node.emitCommonProperties(out);
                if (modelKey != null) {
                    out.model(modelKey, modelColor);
                }
                out.image(alt, img.image(), img.solid(), resource, img.size(), img.fit(), node.style());
                return;
            }
            img.render(out);
            return;
        }

        // Handle ContainerSurface — recurse into children with visual properties
        if (node instanceof ContainerSurface container) {
            node.emitCommonProperties(out);
            if (container.gap() != null && !container.gap().isEmpty()) {
                out.gap(container.gap());
            }
            BoxBorder border = container.boxBorder();
            boolean hasVisualProps = (border != null && border.isVisible())
                    || (container.boxBackground() != null && !container.boxBackground().isEmpty())
                    || (container.boxWidth() != null && !container.boxWidth().isEmpty())
                    || (container.boxHeight() != null && !container.boxHeight().isEmpty())
                    || (container.padding() != null && !container.padding().isEmpty());
            if (hasVisualProps) {
                out.beginBox(container.direction(), node.style(),
                        border != null ? border : BoxBorder.NONE,
                        container.boxBackground() != null ? container.boxBackground() : "",
                        container.boxWidth() != null ? container.boxWidth() : "",
                        container.boxHeight() != null ? container.boxHeight() : "",
                        container.padding() != null ? container.padding() : "");
            } else {
                out.beginBox(container.direction(), node.style());
            }
            for (SurfaceSchema<?> child : container.children()) {
                renderNodeWithBindings(child, resolver, out);
            }
            out.endBox();
            return;
        }

        // All other surface types — render normally (no bindings)
        node.render(out);
    }

    /**
     * Binding resolver that evaluates property path expressions against a root value.
     *
     * <p>Reuses the same resolution logic as {@link StructureRenderContext} but
     * operates independently of annotation-compiled structures. Designed for
     * resolving bindings on stored (CBOR-hydrated) surface templates.
     */
    public static class BindingResolver {
        private final Object value;

        public BindingResolver(Object value) {
            this.value = value;
        }

        /**
         * Resolve a binding expression to a value.
         *
         * <p>Supports:
         * <ul>
         *   <li>{@code "value"} &mdash; the root value itself</li>
         *   <li>{@code "value.foo.bar"} &mdash; property path from root value</li>
         *   <li>{@code "foo.bar"} &mdash; shorthand, treated as value.foo.bar</li>
         * </ul>
         */
        public Object resolve(String bindExpr) {
            if (bindExpr == null || bindExpr.isEmpty()) return null;

            if ("value".equals(bindExpr)) return value;

            if (bindExpr.startsWith("value.")) {
                return getProperty(value, bindExpr.substring(6));
            }

            // Shorthand — try as property of value
            return getProperty(value, bindExpr);
        }

        public Object getProperty(Object obj, String path) {
            if (obj == null || path == null || path.isEmpty()) return null;

            String[] parts = path.split("\\.");
            Object current = obj;

            for (String part : parts) {
                if (current == null) return null;
                current = getPropertyFromObject(current, part);
            }

            return current;
        }

        public Object getPropertyFromObject(Object obj, String propertyName) {
            if (obj == null) return null;
            Class<?> clazz = obj.getClass();

            // Fluent accessor: obj.propertyName()
            try {
                Method method = clazz.getMethod(propertyName);
                method.setAccessible(true);
                return method.invoke(obj);
            } catch (Exception ignored) {}

            // JavaBean getter: obj.getPropertyName()
            String getterName = "get" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
            try {
                Method method = clazz.getMethod(getterName);
                method.setAccessible(true);
                return method.invoke(obj);
            } catch (Exception ignored) {}

            // Boolean getter: obj.isPropertyName()
            String isName = "is" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
            try {
                Method method = clazz.getMethod(isName);
                method.setAccessible(true);
                return method.invoke(obj);
            } catch (Exception ignored) {}

            // Map key access
            if (obj instanceof Map<?, ?> map) {
                return map.get(propertyName);
            }

            return null;
        }

        public boolean isTruthy(Object val) {
            if (val == null) return false;
            if (val instanceof Boolean b) return b;
            if (val instanceof String s) return !s.isEmpty();
            if (val instanceof Number n) return n.doubleValue() != 0;
            if (val instanceof Collection<?> c) return !c.isEmpty();
            if (val instanceof Optional<?> o) return o.isPresent();
            return true;
        }
    }

    // ==================================================================================
    // Structure Compilation (SurfaceSchema / SceneSchema with nested static classes)
    // ==================================================================================

    /**
     * Render a schema using its nested static class structure.
     *
     * <p>When {@code structureClass} is set on the schema, compiles from that
     * class instead of the schema's own class. This allows model classes
     * (plain POJOs) to carry surface annotations directly.
     */
    private static void renderFromStructure(SurfaceSchema<?> schema, SurfaceRenderer out) {
        Class<?> cls = schema.structureClass() != null ? schema.structureClass() : schema.getClass();
        ViewNode root = getCompiled(cls);
        if (root != null) {
            StructureRenderContext ctx = new StructureRenderContext(schema);
            renderNode(root, ctx, out);
        }
    }

    /**
     * Compile nested static classes to a node tree.
     */
    private static ViewNode compileStructure(Class<?> sceneClass) {
        // Check if the class itself has structural annotations
        if (hasStructuralAnnotation(sceneClass)) {
            return compileClass(sceneClass);
        }

        // Fallback: find nested classes with structural annotations
        for (Class<?> nested : sceneClass.getDeclaredClasses()) {
            if (hasStructuralAnnotation(nested)) {
                return compileClass(nested);
            }
        }
        return null;
    }

    /**
     * Check if a class has any @Scene structural annotation (2D or 3D).
     */
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

    /**
     * Check if a class has 2D surface annotations (Container, Text, Image, Shape, Embed).
     * Classes with only 3D annotations (Body, Face, Transform, etc.) return false.
     */
    static boolean has2DAnnotation(Class<?> clazz) {
        return clazz.isAnnotationPresent(Scene.Container.class)
                || clazz.isAnnotationPresent(Scene.Text.class)
                || clazz.isAnnotationPresent(Scene.Image.class)
                || clazz.isAnnotationPresent(Scene.Shape.class)
                || clazz.isAnnotationPresent(Scene.Embed.class);
    }

    /**
     * Check if a field has any structural annotation.
     */
    private static boolean hasStructuralAnnotation(Field field) {
        return field.isAnnotationPresent(Scene.Container.class)
            || field.isAnnotationPresent(Scene.Text.class)
            || field.isAnnotationPresent(Scene.Image.class)
            || field.isAnnotationPresent(Scene.Shape.class);
    }

    private static boolean hasStructuralAnnotation(Method method) {
        return method.isAnnotationPresent(Scene.Container.class)
                || method.isAnnotationPresent(Scene.Text.class)
                || method.isAnnotationPresent(Scene.Image.class)
                || method.isAnnotationPresent(Scene.Shape.class)
                || method.isAnnotationPresent(Scene.Embed.class);
    }

    /**
     * Compile a class to a node, including children from nested classes and methods.
     */
    private static ViewNode compileClass(Class<?> clazz) {
        ViewNode node = compileElement(clazz, clazz.getSimpleName());
        if (node == null) return null;

        // Compile children from nested classes (reverse for source-order)
        Class<?>[] nested = clazz.getDeclaredClasses();
        List<Class<?>> nestedList = new ArrayList<>(Arrays.asList(nested));
        Collections.reverse(nestedList);
        for (Class<?> n : nestedList) {
            // Skip @Scene.Face classes — they are 3D face panels compiled
            // independently by SpatialCompiler, not 2D surface children
            if (n.isAnnotationPresent(Scene.Face.class)) continue;
            if (hasStructuralAnnotation(n)) {
                ViewNode child = compileClass(n);
                if (child != null) {
                    node.children.add(child);
                }
            } else {
                // Non-structural nested classes may carry @Scene.ContextMenu
                extractContextMenu(n, node);
            }
        }

        // Also extract context menu from the class itself (for type-level annotations)
        extractContextMenu(clazz, node);

        // Compile children from annotated methods (public, non-static)
        List<Method> annotatedMethods = new ArrayList<>();
        for (Method m : clazz.getDeclaredMethods()) {
            if (!Modifier.isStatic(m.getModifiers())
                    && Modifier.isPublic(m.getModifiers())
                    && hasStructuralAnnotation(m)) {
                annotatedMethods.add(m);
            }
        }
        annotatedMethods.sort(Comparator.comparing(Method::getName));
        for (Method m : annotatedMethods) {
            ViewNode child = compileElement(m, m.getName());
            if (child != null) {
                node.children.add(child);
            }
        }

        return node;
    }

    /**
     * Extract {@code @Scene.ContextMenu} annotations from a class onto a parent node.
     *
     * <p>Context menu items are collected from nested static classes that carry
     * {@code @Scene.ContextMenu} annotations (but no structural annotation).
     * They are also collected from the class itself if it has type-level
     * context menu annotations.
     */
    private static void extractContextMenu(Class<?> clazz, ViewNode target) {
        Scene.ContextMenu[] menus = clazz.getAnnotationsByType(Scene.ContextMenu.class);
        for (Scene.ContextMenu menu : menus) {
            target.contextMenu.add(new ContextMenuItem(
                    menu.label(),
                    menu.action(),
                    menu.target(),
                    menu.when(),
                    menu.icon(),
                    menu.group(),
                    menu.order()
            ));
        }
    }

    /**
     * Compile annotations from any AnnotatedElement into a ViewNode.
     *
     * <p>Reads all structural annotations (Container, Text, Shape, Border,
     * State, On, Bind, Repeat, Transition, If, plus 3D: Body, Face,
     * Transform, Light, Audio, Environment, Camera) from the element and
     * builds a ViewNode. Does NOT recurse into children.
     *
     * @param element     the annotated element (Class or Method)
     * @param elementName the name to use for transition element IDs
     * @return the compiled ViewNode, or null if no structural annotation found
     */
    private static ViewNode compileElement(AnnotatedElement element, String elementName) {
        ViewNode node = new ViewNode();

        // Conditional visibility
        Scene.If sceneIf = element.getAnnotation(Scene.If.class);
        if (sceneIf != null) {
            node.visibilityCondition = sceneIf.value();
        }

        // Container query
        Scene.Query query = element.getAnnotation(Scene.Query.class);
        if (query != null) {
            node.queryCondition = query.value();
        }

        // Get structural annotations
        Scene.Container container = element.getAnnotation(Scene.Container.class);
        Scene.Text sceneText = element.getAnnotation(Scene.Text.class);
        Scene.Image sceneImage = element.getAnnotation(Scene.Image.class);
        Scene.Shape shape = element.getAnnotation(Scene.Shape.class);
        Scene.Embed embed = element.getAnnotation(Scene.Embed.class);

        // 3D annotations
        Scene.Body body = element.getAnnotation(Scene.Body.class);
        Scene.Face face = element.getAnnotation(Scene.Face.class);
        Scene.Transform transform = element.getAnnotation(Scene.Transform.class);
        Scene.Light light = element.getAnnotation(Scene.Light.class);
        Scene.Audio audio = element.getAnnotation(Scene.Audio.class);
        Scene.Environment env = element.getAnnotation(Scene.Environment.class);
        Scene.Camera camera = element.getAnnotation(Scene.Camera.class);

        // Determine node type — 2D primitives take precedence over 3D-only
        if (embed != null) {
            node.type = ViewNode.NodeType.EMBED;
            node.embedBind = embed.bind();
        } else if (container != null) {
            node.type = ViewNode.NodeType.CONTAINER;
            node.direction = container.direction();
            node.styles = new ArrayList<>(Arrays.asList(container.style()));
            node.background = container.background();
            node.padding = container.padding();
            node.gap = container.gap();
            node.size = container.size();
            node.align = container.align();
            node.rotation = container.rotation();
            node.transformOrigin = container.transformOrigin();
            node.nodeId = container.id();
            node.depth = container.depth();
            node.aspectRatio = container.aspectRatio();
            node.overflow = container.overflow();
            node.fontFamily = container.fontFamily();
            node.textFontSize = container.fontSize();

            // Handle shape (shorthand or @Scene.Shape)
            if (!container.shape().isEmpty()) {
                node.shapeType = container.shape();
                node.cornerRadius = container.cornerRadius();
            } else if (shape != null) {
                boolean hasExplicitSize = !shape.width().isEmpty()
                        || !shape.height().isEmpty() || !shape.size().isEmpty();
                if (hasExplicitSize) {
                    // Sized shape on container → child node
                    ViewNode shapeChild = new ViewNode();
                    shapeChild.type = ViewNode.NodeType.SHAPE;
                    shapeChild.shapeType = shape.type();
                    shapeChild.cornerRadius = shape.cornerRadius();
                    shapeChild.shapeFill = shape.fill();
                    shapeChild.shapeStroke = shape.stroke();
                    shapeChild.shapeStrokeWidth = shape.strokeWidth();
                    shapeChild.shapePath = shape.d();
                    shapeChild.styles = new ArrayList<>(Arrays.asList(shape.style()));
                    shapeChild.depth = shape.depth();
                    resolveShapeSize(shapeChild, shape);
                    node.children.add(shapeChild);
                } else {
                    // Unsized shape → background shape
                    node.shapeType = shape.type();
                    node.cornerRadius = shape.cornerRadius();
                    node.shapeFill = shape.fill();
                    node.shapeStroke = shape.stroke();
                    node.shapeStrokeWidth = shape.strokeWidth();
                    node.styles.addAll(Arrays.asList(shape.style()));
                }
            }

            // Container width/height
            node.width = container.width();
            node.height = container.height();
        } else if (sceneText != null) {
            node.type = ViewNode.NodeType.TEXT;
            node.textContent = sceneText.content();
            node.textBind = sceneText.bind();
            node.textFormat = sceneText.format();
            node.textFontSize = sceneText.fontSize();
            node.fontFamily = sceneText.fontFamily();
            node.styles = new ArrayList<>(Arrays.asList(sceneText.style()));
        } else if (sceneImage != null) {
            node.type = ViewNode.NodeType.IMAGE;
            node.imageAlt = sceneImage.alt();
            node.imageBind = sceneImage.bind();
            node.imageSize = sceneImage.size();
            node.imageFit = sceneImage.fit();
            node.imageModelResource = sceneImage.modelResource();
            node.imageModelColor = sceneImage.modelColor();
            node.styles = new ArrayList<>(Arrays.asList(sceneImage.style()));
        } else if (shape != null) {
            node.type = ViewNode.NodeType.SHAPE;
            node.shapeType = shape.type();
            node.cornerRadius = shape.cornerRadius();
            node.shapeFill = shape.fill();
            node.shapeStroke = shape.stroke();
            node.shapeStrokeWidth = shape.strokeWidth();
            node.shapePath = shape.d();
            node.rotation = shape.rotation();
            node.transformOrigin = shape.transformOrigin();
            node.depth = shape.depth();
            resolveShapeSize(node, shape);
            node.styles = new ArrayList<>(Arrays.asList(shape.style()));
            // Shapes can carry 3D transforms — read @Scene.Transform if present
            if (transform != null) {
                node.transformX = transform.x();
                node.transformY = transform.y();
                node.transformZ = transform.z();
                node.transformYaw = transform.yaw();
                node.transformPitch = transform.pitch();
                node.transformRoll = transform.roll();
                node.transformAxisX = transform.axisX();
                node.transformAxisY = transform.axisY();
                node.transformAxisZ = transform.axisZ();
                node.transformAngle = transform.angle();
                node.transformScaleX = transform.scaleX();
                node.transformScaleY = transform.scaleY();
                node.transformScaleZ = transform.scaleZ();
            }
        } else if (body != null) {
            node.type = ViewNode.NodeType.BODY;
            node.bodyShape = body.shape();
            node.bodyWidth = body.width();
            node.bodyHeight = body.height();
            node.bodyDepth = body.depth();
            node.bodyRadius = body.radius();
            node.bodyMesh = body.mesh();
            node.bodyColor = body.color();
            node.bodyOpacity = body.opacity();
            node.bodyShading = body.shading();
        } else if (face != null) {
            // Face wraps its children in beginFace/endFace
            node.type = ViewNode.NodeType.FACE;
            node.faceName = face.value();
            node.facePpm = face.ppm();
        } else if (transform != null) {
            node.type = ViewNode.NodeType.TRANSFORM;
            node.transformX = transform.x();
            node.transformY = transform.y();
            node.transformZ = transform.z();
            node.transformYaw = transform.yaw();
            node.transformPitch = transform.pitch();
            node.transformRoll = transform.roll();
            node.transformAxisX = transform.axisX();
            node.transformAxisY = transform.axisY();
            node.transformAxisZ = transform.axisZ();
            node.transformAngle = transform.angle();
            node.transformScaleX = transform.scaleX();
            node.transformScaleY = transform.scaleY();
            node.transformScaleZ = transform.scaleZ();
        } else if (light != null) {
            node.type = ViewNode.NodeType.LIGHT;
            node.lightType = light.type();
            node.lightColor = light.color();
            node.lightIntensity = light.intensity();
            node.lightX = light.x();
            node.lightY = light.y();
            node.lightZ = light.z();
            node.lightDirX = light.dirX();
            node.lightDirY = light.dirY();
            node.lightDirZ = light.dirZ();
        } else if (audio != null) {
            node.type = ViewNode.NodeType.AUDIO_3D;
            node.audioSrc = audio.src();
            node.audioX = audio.x();
            node.audioY = audio.y();
            node.audioZ = audio.z();
            node.audioVolume = audio.volume();
            node.audioPitch = audio.pitch();
            node.audioLoop = audio.loop();
            node.audioSpatial = audio.spatial();
            node.audioRefDistance = audio.refDistance();
            node.audioMaxDistance = audio.maxDistance();
            node.audioAutoplay = audio.autoplay();
        } else if (env != null) {
            node.type = ViewNode.NodeType.ENVIRONMENT;
            node.envBackground = env.background();
            node.envAmbient = env.ambient();
            node.envFogNear = env.fogNear();
            node.envFogFar = env.fogFar();
            node.envFogColor = env.fogColor();
        } else if (camera != null) {
            node.type = ViewNode.NodeType.CAMERA;
            node.cameraProjection = camera.projection();
            node.cameraFov = camera.fov();
            node.cameraNear = camera.near();
            node.cameraFar = camera.far();
            node.cameraX = camera.x();
            node.cameraY = camera.y();
            node.cameraZ = camera.z();
            node.cameraTargetX = camera.targetX();
            node.cameraTargetY = camera.targetY();
            node.cameraTargetZ = camera.targetZ();
        } else {
            return null;
        }

        // Extract @Scene.Border (applies to ANY node type)
        Scene.Border borderAnn = element.getAnnotation(Scene.Border.class);
        if (borderAnn != null) {
            node.border = borderAnn.all();
            node.borderTop = borderAnn.top();
            node.borderRight = borderAnn.right();
            node.borderBottom = borderAnn.bottom();
            node.borderLeft = borderAnn.left();
            node.borderWidth = borderAnn.width();
            node.borderStyle = borderAnn.style();
            node.borderColor = borderAnn.color();
            node.borderRadius = borderAnn.radius();
        }

        // Compile state mappings
        Scene.State[] sceneStates = element.getAnnotationsByType(Scene.State.class);
        for (Scene.State state : sceneStates) {
            node.stateStyles.add(new ViewNode.StateStyle(state.when(), Arrays.asList(state.style())));
        }

        // Compile event handlers
        Scene.On[] sceneEvents = element.getAnnotationsByType(Scene.On.class);
        for (Scene.On event : sceneEvents) {
            node.events.add(new ViewNode.EventHandler(event.event(), event.action(), event.target(), event.when()));
        }

        // Compile binding
        Scene.Bind sceneBind = element.getAnnotation(Scene.Bind.class);
        if (sceneBind != null) {
            node.bindPath = sceneBind.value();
        }

        // Compile repeat
        Scene.Repeat repeat = element.getAnnotation(Scene.Repeat.class);
        if (repeat != null) {
            node.repeatBind = repeat.bind();
            node.repeatItemVar = repeat.itemVar();
            node.repeatIndexVar = repeat.indexVar();
            node.repeatColumns = repeat.columns();
            if (repeat.as() != SceneSchema.class) {
                @SuppressWarnings("unchecked")
                Class<? extends SurfaceSchema> repeatAsClass =
                        (Class<? extends SurfaceSchema>) repeat.as();
                node.repeatAs = repeatAsClass;
                node.repeatAsKey = repeat.as().getName();
            }
        }

        // Compile placement metadata
        Scene.Place place = element.getAnnotation(Scene.Place.class);
        if (place != null) {
            node.placeIn = place.in();
            node.placeAnchor = place.anchor();
            node.placeTop = place.top();
            node.placeBottom = place.bottom();
            node.placeLeft = place.left();
            node.placeRight = place.right();
            node.placeTopTo = place.topTo();
            node.placeBottomTo = place.bottomTo();
            node.placeLeftTo = place.leftTo();
            node.placeRightTo = place.rightTo();
            node.placeWidth = place.width();
            node.placeHeight = place.height();
            node.placeMinWidth = place.minWidth();
            node.placeMinHeight = place.minHeight();
            node.placeMaxWidth = place.maxWidth();
            node.placeMaxHeight = place.maxHeight();
            node.placeAlignX = place.alignX();
            node.placeAlignY = place.alignY();
            node.placeZIndex = place.zIndex();
            node.placeOverflow = place.overflow();
        }

        // If element has @Scene.Body as a type-level annotation on a Container,
        // compile it as supplementary data (body describes the 3D shape; container
        // describes the 2D structure on that body)
        if (node.type == ViewNode.NodeType.CONTAINER && body != null) {
            node.bodyShape = body.shape();
            node.bodyWidth = body.width();
            node.bodyHeight = body.height();
            node.bodyDepth = body.depth();
            node.bodyRadius = body.radius();
            node.bodyMesh = body.mesh();
            node.bodyColor = body.color();
            node.bodyOpacity = body.opacity();
            node.bodyShading = body.shading();
        }

        // Compile transitions
        List<TransitionSpec> specs = TransitionSpec.fromElement(element);
        if (!specs.isEmpty()) {
            node.transitions = specs;
            node.elementName = elementName;
        }

        return node;
    }

    /**
     * Resolve shape size from @Scene.Shape annotation.
     */
    private static void resolveShapeSize(ViewNode node, Scene.Shape shape) {
        if (!shape.size().isEmpty()) {
            node.shapeWidth = shape.size();
            node.shapeHeight = shape.size();
        }
        if (!shape.width().isEmpty()) {
            node.shapeWidth = shape.width();
        }
        if (!shape.height().isEmpty()) {
            node.shapeHeight = shape.height();
        }
    }

    /** Check if a shape node carries non-default 3D transform data. */
    private static boolean nodeHasTransform3D(ViewNode node) {
        return !"0".equals(node.transformX) || !"0".equals(node.transformY) || !"0".equals(node.transformZ)
                || node.transformYaw != 0 || node.transformPitch != 0 || node.transformRoll != 0
                || node.transformAngle != 0
                || node.transformScaleX != 1 || node.transformScaleY != 1 || node.transformScaleZ != 1;
    }

    // ==================================================================================
    // Rendering
    // ==================================================================================

    /**
     * Render a compiled node to output.
     */
    private static void renderNode(ViewNode node, StructureRenderContext ctx, SurfaceRenderer out) {
        // Handle @Repeat — iterate over collection
        if (node.isRepeat()) {
            renderRepeat(node, ctx, out);
            return;
        }

        // Check visibility condition
        if (node.visibilityCondition != null && !ctx.evaluate(node.visibilityCondition)) {
            return;
        }

        // Check container query
        boolean hasQuery = node.queryCondition != null && !node.queryCondition.isEmpty();
        if (hasQuery) {
            boolean matches = out.beginQuery(node.queryCondition);
            if (!matches) {
                out.endQuery();
                return;
            }
        }
        try {

        // Build effective styles
        List<String> effectiveStyles = new ArrayList<>(node.styles);
        for (ViewNode.StateStyle ss : node.stateStyles) {
            if (ctx.evaluate(ss.condition)) {
                effectiveStyles.addAll(ss.styles);
            }
        }

        // Emit events
        for (ViewNode.EventHandler eh : node.events) {
            if (eh.condition.isEmpty() || ctx.evaluate(eh.condition)) {
                String target = ctx.resolveBinding(eh.target);
                out.event(eh.eventType, eh.action, target);
            }
        }

        // Emit transition specs and auto-generate element ID for animated nodes
        if (!node.transitions.isEmpty()) {
            if (node.elementName != null) {
                out.id(node.elementName);
            }
            out.transitions(node.transitions);
        }

        // Resolve border
        BoxBorder boxBorder = resolveBorder(node);
        boolean hasBorder = boxBorder.isVisible();
        boolean hasVisualProps = hasBorder
                || !node.background.isEmpty()
                || !node.width.isEmpty()
                || !node.height.isEmpty()
                || !node.padding.isEmpty();

        // Emit node ID
        if (node.nodeId != null && !node.nodeId.isEmpty()) {
            String resolvedId;
            if (node.nodeId.startsWith("bind:")) {
                resolvedId = ctx.resolveBinding(node.nodeId.substring("bind:".length()));
            } else {
                resolvedId = node.nodeId;
            }
            if (!resolvedId.isEmpty()) {
                out.id(resolvedId);
            }
        }

        // Render based on type
        switch (node.type) {
            case CONTAINER -> {
                if (node.fontFamily != null && !node.fontFamily.isEmpty()) {
                    out.textFontFamily(node.fontFamily);
                }
                if (node.textFontSize != null && !node.textFontSize.isEmpty()) {
                    out.textFontSize(node.textFontSize);
                }
                if (node.hasShape()) {
                    if (!node.shapeWidth.isEmpty() || !node.shapeHeight.isEmpty()) {
                        out.shapeSize(node.shapeWidth, node.shapeHeight);
                    }
                    out.shape(node.shapeType, node.cornerRadius, node.shapeFill,
                            node.shapeStroke, node.shapeStrokeWidth, node.shapePath);
                }
                if (node.gap != null && !node.gap.isEmpty()) {
                    out.gap(node.gap);
                }
                // Emit elevation hint (3D renderers create slabs) — legacy path
                if (node.elevation != null && !node.elevation.isEmpty()) {
                    out.elevation(elevationToMeters(node.elevation), node.elevationSolid);
                }
                // Emit depth as elevation hint (all renderers) + 3D depth
                if (node.depth != null && !node.depth.isEmpty()) {
                    double meters = elevationToMeters(node.depth);
                    if (meters != 0.0) {
                        out.elevation(meters, true);
                        if (out instanceof SceneRenderer sr) {
                            sr.depth(meters, true);
                        }
                    }
                }
                // Aspect ratio
                if (node.aspectRatio != null && !node.aspectRatio.isEmpty()) {
                    out.aspectRatio(node.aspectRatio);
                }
                // Overflow
                if (node.overflow != null && !"visible".equals(node.overflow)) {
                    out.overflow(node.overflow);
                    // When overflow is scroll/auto and height is set, also emit maxHeight
                    if (("scroll".equals(node.overflow) || "auto".equals(node.overflow))
                            && node.height != null && !node.height.isEmpty()) {
                        out.maxHeight(node.height);
                    }
                }
                // Cross-axis alignment as style class
                if (node.align != null && !node.align.isEmpty()) {
                    effectiveStyles.add("align-" + node.align);
                }
                // Transform-origin
                if (node.transformOrigin != null && !node.transformOrigin.isEmpty()) {
                    float[] origin = resolveTransformOrigin(node.transformOrigin, ctx);
                    if (origin[0] != 0.5f || origin[1] != 0.5f) {
                        out.transformOrigin(origin[0], origin[1]);
                    }
                }
                // Rotation
                if (node.rotation != null && !node.rotation.isEmpty()) {
                    double degrees = resolveRotation(node.rotation, ctx);
                    if (degrees != 0.0) {
                        out.rotation(degrees);
                    }
                }
                if (hasVisualProps) {
                    out.beginBox(node.direction, effectiveStyles,
                            boxBorder, node.background,
                            node.width, node.height, node.padding);
                } else {
                    out.beginBox(node.direction, effectiveStyles);
                }
                for (ViewNode child : node.children) {
                    renderNode(child, ctx, out);
                }
                out.endBox();
            }
            case TEXT -> {
                if (hasBorder) {
                    out.beginBox(Scene.Direction.HORIZONTAL, Collections.emptyList(),
                            boxBorder, node.background,
                            node.width, node.height, node.padding);
                }
                if (node.fontFamily != null && !node.fontFamily.isEmpty()) {
                    out.textFontFamily(node.fontFamily);
                }
                if (node.textFontSize != null && !node.textFontSize.isEmpty()) {
                    out.textFontSize(node.textFontSize);
                }
                String content = resolveText(node, ctx);
                if (!"plain".equals(node.textFormat) && !node.textFormat.isEmpty()) {
                    out.formattedText(content, node.textFormat, effectiveStyles);
                } else {
                    out.text(content, effectiveStyles);
                }
                if (hasBorder) {
                    out.endBox();
                }
            }
            case IMAGE -> {
                if (hasBorder) {
                    out.beginBox(Scene.Direction.HORIZONTAL, Collections.emptyList(),
                            boxBorder, node.background,
                            node.width, node.height, node.padding);
                }
                String alt;
                String resource = null;
                String modelKey = null;
                int modelColor = -1;

                if (!node.imageBind.isEmpty()) {
                    Object resolved = ctx.resolveValue(node.imageBind);
                    if (resolved != null && !(resolved instanceof String)) {
                        Object sym = ctx.resolveValue(node.imageBind + ".symbol");
                        alt = sym != null ? sym.toString() : resolved.toString();
                        Object img = ctx.resolveValue(node.imageBind + ".imageKey");
                        if (img != null && !img.toString().isEmpty()) {
                            resource = img.toString();
                        }
                        Object mdl = ctx.resolveValue(node.imageBind + ".modelKey");
                        if (mdl != null && !mdl.toString().isEmpty()) {
                            modelKey = mdl.toString();
                        }
                        Object mc = ctx.resolveValue(node.imageBind + ".modelColor");
                        if (mc instanceof Number n) {
                            modelColor = n.intValue();
                        }
                    } else {
                        alt = resolved != null ? resolved.toString() : "";
                    }
                } else {
                    alt = node.imageAlt;
                }

                // Static model resource from annotation
                if (modelKey == null && !node.imageModelResource.isEmpty()) {
                    modelKey = node.imageModelResource;
                    modelColor = node.imageModelColor;
                }

                if (modelKey != null) {
                    out.model(modelKey, modelColor);
                }
                out.image(alt, null, null, resource, node.imageSize, node.imageFit, effectiveStyles);
                if (hasBorder) {
                    out.endBox();
                }
            }
            case SHAPE -> {
                // 3D transform on shape — wrap with pushTransform/popTransform
                boolean hasTransform3D = nodeHasTransform3D(node);
                if (hasTransform3D && out instanceof SceneRenderer sr) {
                    double tx = parseDimension(node.transformX);
                    double ty = parseDimension(node.transformY);
                    double tz = parseDimension(node.transformZ);
                    double[] quat = computeQuaternion(
                            node.transformYaw, node.transformPitch, node.transformRoll,
                            node.transformAxisX, node.transformAxisY, node.transformAxisZ,
                            node.transformAngle);
                    sr.pushTransform(tx, ty, tz,
                            quat[0], quat[1], quat[2], quat[3],
                            node.transformScaleX, node.transformScaleY, node.transformScaleZ);
                }
                if (node.transformOrigin != null && !node.transformOrigin.isEmpty()) {
                    float[] origin = resolveTransformOrigin(node.transformOrigin, ctx);
                    if (origin[0] != 0.5f || origin[1] != 0.5f) {
                        out.transformOrigin(origin[0], origin[1]);
                    }
                }
                if (node.rotation != null && !node.rotation.isEmpty()) {
                    double degrees = resolveRotation(node.rotation, ctx);
                    if (degrees != 0.0) {
                        out.rotation(degrees);
                    }
                }
                // Emit depth as elevation hint (all renderers) + 3D depth
                if (node.depth != null && !node.depth.isEmpty()) {
                    double meters = elevationToMeters(node.depth);
                    if (meters != 0.0) {
                        out.elevation(meters, true);
                        if (out instanceof SceneRenderer sr) {
                            sr.depth(meters, true);
                        }
                    }
                }
                if (!node.shapeWidth.isEmpty() || !node.shapeHeight.isEmpty()) {
                    out.shapeSize(node.shapeWidth, node.shapeHeight);
                }
                out.shape(node.shapeType, node.cornerRadius, node.shapeFill,
                        node.shapeStroke, node.shapeStrokeWidth, node.shapePath);
                if (hasTransform3D && out instanceof SceneRenderer sr) {
                    sr.popTransform();
                }
            }
            case EMBED -> {
                Object embedded = ctx.resolveValue(node.embedBind);
                if (embedded instanceof SceneSchema<?> scene) {
                    scene.render(out);
                } else if (embedded instanceof SurfaceSchema<?> surface) {
                    surface.render(out);
                } else if (embedded != null && canCompile(embedded.getClass())) {
                    SurfaceSchema<Object> wrapper = new SurfaceSchema<>() {};
                    wrapper.value(embedded);
                    wrapper.structureClass(embedded.getClass());
                    wrapper.render(out);
                }
            }

            // ===== 3D Node Types =====

            case BODY -> {
                if (out instanceof SceneRenderer sr) {
                    if (!node.bodyMesh.isEmpty()) {
                        sr.meshBody(node.bodyMesh, node.bodyColor, node.bodyOpacity,
                                node.bodyShading, effectiveStyles);
                    } else if (!node.bodyShape.isEmpty()) {
                        double w = parseDimension(node.bodyWidth);
                        double h = parseDimension(node.bodyHeight);
                        double d = parseDimension(node.bodyDepth);
                        sr.body(node.bodyShape, w, h, d, node.bodyColor, node.bodyOpacity,
                                node.bodyShading, effectiveStyles);
                    }
                }
                for (ViewNode child : node.children) {
                    renderNode(child, ctx, out);
                }
            }
            case FACE -> {
                if (out instanceof SceneRenderer sr) sr.beginFace(node.faceName, node.facePpm);
                for (ViewNode child : node.children) {
                    renderNode(child, ctx, out);
                }
                if (out instanceof SceneRenderer sr) sr.endFace();
            }
            case TRANSFORM -> {
                if (out instanceof SceneRenderer sr) {
                    double x = parseDimension(node.transformX);
                    double y = parseDimension(node.transformY);
                    double z = parseDimension(node.transformZ);
                    double[] quat = computeQuaternion(
                            node.transformYaw, node.transformPitch, node.transformRoll,
                            node.transformAxisX, node.transformAxisY, node.transformAxisZ,
                            node.transformAngle);
                    sr.pushTransform(x, y, z,
                            quat[0], quat[1], quat[2], quat[3],
                            node.transformScaleX, node.transformScaleY, node.transformScaleZ);
                }
                for (ViewNode child : node.children) {
                    renderNode(child, ctx, out);
                }
                if (out instanceof SceneRenderer sr) {
                    sr.popTransform();
                }
            }
            case LIGHT -> {
                if (out instanceof SceneRenderer sr) {
                    double lx = parseDimension(node.lightX);
                    double ly = parseDimension(node.lightY);
                    double lz = parseDimension(node.lightZ);
                    sr.light(node.lightType, node.lightColor, node.lightIntensity,
                            lx, ly, lz, node.lightDirX, node.lightDirY, node.lightDirZ);
                }
            }
            case AUDIO_3D -> {
                if (out instanceof SceneRenderer sr) {
                    double ax = parseDimension(node.audioX);
                    double ay = parseDimension(node.audioY);
                    double az = parseDimension(node.audioZ);
                    double refDist = parseDimension(node.audioRefDistance);
                    double maxDist = parseDimension(node.audioMaxDistance);
                    sr.audio3d(node.audioSrc, ax, ay, az,
                            node.audioVolume, node.audioPitch, node.audioLoop,
                            node.audioSpatial, refDist, maxDist, node.audioAutoplay);
                }
            }
            case ENVIRONMENT -> {
                if (out instanceof SceneRenderer sr) {
                    double fogNear = node.envFogNear.isEmpty() ? -1 : parseDimension(node.envFogNear);
                    double fogFar = node.envFogFar.isEmpty() ? -1 : parseDimension(node.envFogFar);
                    sr.environment(node.envBackground, node.envAmbient,
                            fogNear, fogFar, node.envFogColor);
                }
            }
            case CAMERA -> {
                if (out instanceof SceneRenderer sr) {
                    double nearVal = parseDimension(node.cameraNear);
                    double farVal = parseDimension(node.cameraFar);
                    double cx = parseDimension(node.cameraX);
                    double cy = parseDimension(node.cameraY);
                    double cz = parseDimension(node.cameraZ);
                    double tx = parseDimension(node.cameraTargetX);
                    double ty = parseDimension(node.cameraTargetY);
                    double tz = parseDimension(node.cameraTargetZ);
                    sr.camera(node.cameraProjection, node.cameraFov, nearVal, farVal,
                            cx, cy, cz, tx, ty, tz);
                }
            }
        }

        } finally {
            if (hasQuery) {
                out.endQuery();
            }
        }
    }

    /**
     * Render a repeat node by iterating over its collection.
     */
    private static void renderRepeat(ViewNode node, StructureRenderContext ctx, SurfaceRenderer out) {
        Object collection = ctx.resolveValue(node.repeatBind);
        if (collection == null) return;

        Iterable<?> items;
        if (collection instanceof Iterable<?> iterable) {
            items = iterable;
        } else if (collection.getClass().isArray()) {
            items = Arrays.asList((Object[]) collection);
        } else {
            items = List.of(collection);
        }

        // Clone node without repeat to render each item
        ViewNode itemNode = cloneWithoutRepeat(node);

        // Resolve columns for grid wrapping
        int columns = resolveColumns(node.repeatColumns, ctx);

        int index = 0;
        boolean inRow = false;
        for (Object item : items) {
            // Grid wrapping: start a new horizontal row every N items
            if (columns > 0 && index % columns == 0) {
                if (inRow) out.endBox();
                if (node.gap != null && !node.gap.isEmpty()) {
                    out.gap(node.gap);
                }
                out.beginBox(Scene.Direction.HORIZONTAL, List.of());
                inRow = true;
            }

            StructureRenderContext itemCtx = ctx.withItem(item, index, node.repeatItemVar, node.repeatIndexVar);

            if (node.repeatAs != null) {
                try {
                    SurfaceSchema<?> itemScene = node.repeatAs.getDeclaredConstructor().newInstance();
                    if (item != null) {
                        try {
                            var valueMethod = SceneSchema.class.getMethod("value", Object.class);
                            valueMethod.invoke(itemScene, item);
                        } catch (Exception ignored) {}
                    }
                    itemScene.render(out);
                } catch (Exception e) {
                    renderNode(itemNode, itemCtx, out);
                }
            } else {
                renderNode(itemNode, itemCtx, out);
            }

            index++;
        }

        // Close the last row if grid wrapping was active
        if (inRow) out.endBox();
    }

    /**
     * Resolve a columns spec to an int. Supports literal ints and "bind:..." expressions.
     */
    private static int resolveColumns(String spec, StructureRenderContext ctx) {
        if (spec == null || spec.isEmpty()) return 0;
        if (spec.startsWith("bind:")) {
            Object val = ctx.resolveValue(spec.substring("bind:".length()));
            if (val instanceof Number n) return n.intValue();
            if (val instanceof String s) {
                try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
            }
            return 0;
        }
        try { return Integer.parseInt(spec); } catch (NumberFormatException ignored) {}
        return 0;
    }

    /**
     * Clone a node without repeat properties (for iterating).
     */
    private static ViewNode cloneWithoutRepeat(ViewNode src) {
        ViewNode dst = new ViewNode();
        dst.type = src.type;
        dst.visibilityCondition = src.visibilityCondition;
        dst.direction = src.direction;
        dst.cornerRadius = src.cornerRadius;
        dst.background = src.background;
        dst.padding = src.padding;
        dst.gap = src.gap;
        dst.size = src.size;
        dst.align = src.align;
        dst.rotation = src.rotation;
        dst.transformOrigin = src.transformOrigin;
        dst.nodeId = src.nodeId;
        dst.shapeType = src.shapeType;
        dst.shapeFill = src.shapeFill;
        dst.shapeStroke = src.shapeStroke;
        dst.shapeStrokeWidth = src.shapeStrokeWidth;
        dst.shapePath = src.shapePath;
        dst.shapeWidth = src.shapeWidth;
        dst.shapeHeight = src.shapeHeight;
        dst.textContent = src.textContent;
        dst.textBind = src.textBind;
        dst.textFormat = src.textFormat;
        dst.textFontSize = src.textFontSize;
        dst.fontFamily = src.fontFamily;
        dst.imageAlt = src.imageAlt;
        dst.imageBind = src.imageBind;
        dst.imageSize = src.imageSize;
        dst.imageFit = src.imageFit;
        dst.imageModelResource = src.imageModelResource;
        dst.imageModelColor = src.imageModelColor;
        dst.bindPath = src.bindPath;
        dst.embedBind = src.embedBind;
        dst.border = src.border;
        dst.borderTop = src.borderTop;
        dst.borderRight = src.borderRight;
        dst.borderBottom = src.borderBottom;
        dst.borderLeft = src.borderLeft;
        dst.borderWidth = src.borderWidth;
        dst.borderStyle = src.borderStyle;
        dst.borderColor = src.borderColor;
        dst.borderRadius = src.borderRadius;
        dst.width = src.width;
        dst.height = src.height;
        dst.depth = src.depth;
        dst.elevation = src.elevation;
        dst.elevationSolid = src.elevationSolid;
        dst.queryCondition = src.queryCondition;
        dst.aspectRatio = src.aspectRatio;
        dst.placeIn = src.placeIn;
        dst.placeAnchor = src.placeAnchor;
        dst.transitions = src.transitions;
        dst.elementName = src.elementName;
        dst.placeTop = src.placeTop;
        dst.placeBottom = src.placeBottom;
        dst.placeLeft = src.placeLeft;
        dst.placeRight = src.placeRight;
        dst.placeTopTo = src.placeTopTo;
        dst.placeBottomTo = src.placeBottomTo;
        dst.placeLeftTo = src.placeLeftTo;
        dst.placeRightTo = src.placeRightTo;
        dst.placeWidth = src.placeWidth;
        dst.placeHeight = src.placeHeight;
        dst.placeMinWidth = src.placeMinWidth;
        dst.placeMinHeight = src.placeMinHeight;
        dst.placeMaxWidth = src.placeMaxWidth;
        dst.placeMaxHeight = src.placeMaxHeight;
        dst.placeAlignX = src.placeAlignX;
        dst.placeAlignY = src.placeAlignY;
        dst.placeZIndex = src.placeZIndex;
        dst.placeOverflow = src.placeOverflow;
        dst.styles = new ArrayList<>(src.styles);
        dst.stateStyles = new ArrayList<>(src.stateStyles);
        dst.events = new ArrayList<>(src.events);
        dst.children = new ArrayList<>(src.children);
        // Don't copy repeat properties
        return dst;
    }

    // ==================================================================================
    // Method Compilation (SceneModel with annotated methods)
    // ==================================================================================

    /**
     * Render a SceneModel by calling annotated methods.
     */
    private static void renderFromMethods(SceneModel<?> model, SurfaceRenderer out) {
        SurfaceSchema<?> compiled = compileFromMethods(model);
        if (compiled != null) {
            compiled.render(out);
        }
    }

    /**
     * Compile a SceneModel from annotated methods.
     */
    private static SurfaceSchema<?> compileFromMethods(Object model) {
        if (model == null) {
            return ContainerSurface.vertical();
        }

        Class<?> clazz = model.getClass();

        // Check class-level @Scene for layout type
        Scene classScene = findClassScene(clazz);
        Class<?> layoutType = classScene != null
                ? classScene.as()
                : SceneSchema.class;

        // Dispatch to appropriate compiler based on layout type
        if (layoutType == ConstraintSurface.class) {
            return compileConstraints(model, clazz);
        } else if (layoutType == FlexSurface.class) {
            return compileFlex(model, clazz);
        } else {
            return compileDefaultLayout(model, clazz);
        }
    }

    private static SurfaceSchema<?> compileConstraints(Object model, Class<?> clazz) {
        ConstraintSurface container = new ConstraintSurface();
        container.style("constraint-layout");

        // Apply class-level @Scene.Border to the outer constraint container
        Scene.Border classBorder = clazz.getAnnotation(Scene.Border.class);
        if (classBorder != null) {
            BoxBorder outerBorder = BoxBorder.resolve(
                    classBorder.all(), classBorder.top(), classBorder.right(),
                    classBorder.bottom(), classBorder.left(),
                    classBorder.width(), classBorder.style(), classBorder.color(),
                    classBorder.radius());
            if (outerBorder != null && outerBorder.isVisible()) {
                container.border(outerBorder);
            }
        }

        // Sort elements by constraint position: top="0" first, bottom="0" last
        List<LayoutElement> elements = scanElements(model, clazz,
                Scene.Constraint.class, Scene.Place.class);
        elements.sort(Comparator.<LayoutElement>comparingInt(e -> {
            ConstraintSurface.ConstraintValues c = resolvePlacement(e);
            if (c == null) return 10;
            if ("0".equals(c.top())) return 0;
            if ("0".equals(c.bottom())) return 20;
            if ("0".equals(c.left()) && c.leftTo().isEmpty()) return 10;
            if (!c.leftTo().isEmpty()) return 11;
            return 10;
        }));

        for (LayoutElement element : elements) {
            SurfaceSchema<?> content = element.invoke();
            if (content == null) continue;

            BoxBorder border = resolveBorderAnnotation(element);

            String id = element.id();
            ConstraintSurface.ConstraintValues placement = resolvePlacement(element);
            if (placement != null) {
                String placedId = resolvePlacementId(element);
                if (id.isEmpty()) {
                    id = placedId;
                }
                container.add(id, content, placement, border);
            }
        }

        return container;
    }

    private static SurfaceSchema<?> compileFlex(Object model, Class<?> clazz) {
        FlexSurface container = FlexSurface.column();
        container.style("flex-layout");

        Scene.FlexContainer flexConfig = clazz.getAnnotation(Scene.FlexContainer.class);
        if (flexConfig != null) {
            container.container(flexConfig);
        }

        List<LayoutElement> elements = scanElements(model, clazz, Scene.Flex.class);
        elements.sort(Comparator.comparingInt(e -> {
            Scene.Flex flex = e.getAnnotation(Scene.Flex.class);
            return flex != null ? flex.order() : 0;
        }));

        for (LayoutElement element : elements) {
            SurfaceSchema<?> content = element.invoke();
            if (content == null) continue;

            applyBorderAnnotation(content, element);

            Scene.Flex flex = element.getAnnotation(Scene.Flex.class);
            if (flex != null) {
                container.add(content, flex);
            } else {
                container.add(content);
            }
        }

        return container;
    }

    private static SurfaceSchema<?> compileDefaultLayout(Object model, Class<?> clazz) {
        ContainerSurface container = ContainerSurface.vertical();

        for (LayoutElement element : scanElements(model, clazz)) {
            SurfaceSchema<?> content = element.invoke();
            if (content != null) {
                applyBorderAnnotation(content, element);
                container.add(content);
            }
        }

        return container;
    }

    @SafeVarargs
    private static List<LayoutElement> scanElements(Object model, Class<?> clazz,
                                                     Class<? extends Annotation>... layoutAnnotations) {
        List<LayoutElement> elements = new ArrayList<>();

        // Scan methods
        for (Method method : getAllMethods(clazz)) {
            Scene scene = method.getAnnotation(Scene.class);
            if (scene == null) continue;
            if (!scene.visible()) continue;

            if (layoutAnnotations.length > 0 && !hasAnyAnnotation(method, layoutAnnotations)) {
                continue;
            }

            elements.add(new MethodElement(model, method, scene));
        }

        // Scan fields
        for (Field field : getAllFields(clazz)) {
            Scene scene = field.getAnnotation(Scene.class);
            if (scene == null) continue;
            if (!scene.visible()) continue;

            if (layoutAnnotations.length > 0 && !hasAnyAnnotation(field, layoutAnnotations)) {
                continue;
            }

            elements.add(new FieldElement(model, field, scene));
        }

        return elements;
    }

    /**
     * Normalize @Scene.Constraint and @Scene.Place into one placement model.
     */
    private static ConstraintSurface.ConstraintValues resolvePlacement(LayoutElement element) {
        Scene.Constraint constraint = element.getAnnotation(Scene.Constraint.class);
        Scene.Place place = element.getAnnotation(Scene.Place.class);
        if (constraint == null && place == null) {
            return null;
        }
        if (constraint == null) {
            return ConstraintSurface.ConstraintValues.from(place);
        }
        if (place == null) {
            return ConstraintSurface.ConstraintValues.from(constraint);
        }

        return new ConstraintSurface.ConstraintValues(
                firstNonEmpty(place.top(), constraint.top()),
                firstNonEmpty(place.bottom(), constraint.bottom()),
                firstNonEmpty(place.left(), constraint.left()),
                firstNonEmpty(place.right(), constraint.right()),
                firstNonEmpty(place.topTo(), constraint.topTo()),
                firstNonEmpty(place.bottomTo(), constraint.bottomTo()),
                firstNonEmpty(place.leftTo(), constraint.leftTo()),
                firstNonEmpty(place.rightTo(), constraint.rightTo()),
                firstNonEmpty(place.width(), constraint.width()),
                firstNonEmpty(place.height(), constraint.height()),
                firstNonEmpty(place.minWidth(), constraint.minWidth()),
                firstNonEmpty(place.minHeight(), constraint.minHeight()),
                firstNonEmpty(place.maxWidth(), constraint.maxWidth()),
                firstNonEmpty(place.maxHeight(), constraint.maxHeight()),
                firstNonEmpty(place.alignX(), constraint.alignX()),
                firstNonEmpty(place.alignY(), constraint.alignY()),
                place.zIndex() != 0 ? place.zIndex() : constraint.zIndex(),
                firstNonDefault(place.overflow(), "visible", constraint.overflow())
        );
    }

    private static String resolvePlacementId(LayoutElement element) {
        Scene.Place place = element.getAnnotation(Scene.Place.class);
        if (place != null && !place.id().isEmpty()) {
            return place.id();
        }
        Scene.Constraint constraint = element.getAnnotation(Scene.Constraint.class);
        if (constraint != null && !constraint.id().isEmpty()) {
            return constraint.id();
        }
        return "";
    }

    private static String firstNonEmpty(String preferred, String fallback) {
        return preferred != null && !preferred.isEmpty() ? preferred : fallback;
    }

    private static String firstNonDefault(String preferred, String defaultValue, String fallback) {
        return preferred != null && !preferred.equals(defaultValue) ? preferred : fallback;
    }

    @SafeVarargs
    private static boolean hasAnyAnnotation(Method method, Class<? extends Annotation>... annotations) {
        for (Class<? extends Annotation> annotation : annotations) {
            if (annotation != null && method.isAnnotationPresent(annotation)) {
                return true;
            }
        }
        return false;
    }

    @SafeVarargs
    private static boolean hasAnyAnnotation(Field field, Class<? extends Annotation>... annotations) {
        for (Class<? extends Annotation> annotation : annotations) {
            if (annotation != null && field.isAnnotationPresent(annotation)) {
                return true;
            }
        }
        return false;
    }

    // ==================================================================================
    // Field Compilation (Data models with annotated fields)
    // ==================================================================================

    /**
     * Render a data object from its annotated fields.
     */
    private static void renderFromFields(Object data, SurfaceRenderer out) {
        View view = compileDataModel(data, SceneMode.FULL);
        if (view.root() != null) {
            view.root().render(out);
        }
    }

    /**
     * Compile a data model to a View.
     */
    private static View compileDataModel(Object data, SceneMode mode) {
        if (data == null) {
            return View.empty();
        }

        Class<?> clazz = data.getClass();
        FieldMetadata metadata = scanFields(clazz);

        // Primitive/simple objects (String, Number, Map, List, etc.) may not have
        // annotated fields. Also fall through if all fields are hidden.
        boolean hasVisibleFields = metadata.fields().stream().anyMatch(FieldInfo::visible);
        if (metadata.fields().isEmpty() || !hasVisibleFields) {
            SurfaceSchema<?> surface = defaultValueToScene(data, mode);
            return surface != null ? View.of(surface).mode(mode) : View.empty();
        }

        return switch (mode) {
            case FULL -> compileFullView(data, metadata);
            case COMPACT -> compileCompactView(data, metadata);
            case CHIP -> compileChipView(data, metadata);
            case PREVIEW -> compilePreviewView(data, metadata);
        };
    }

    private static View compileFullView(Object data, FieldMetadata metadata) {
        ContainerSurface root = ContainerSurface.vertical();
        root.gap("0.25em");

        String typeName = data.getClass().getSimpleName();
        root.add(TextSurface.of(typeName).style("heading"));

        Map<String, List<FieldInfo>> byRegion = new LinkedHashMap<>();
        for (FieldInfo field : metadata.fields()) {
            String region = field.region().isEmpty() ? "main" : field.region();
            byRegion.computeIfAbsent(region, k -> new ArrayList<>()).add(field);
        }

        List<FieldInfo> mainFields = byRegion.getOrDefault("main", metadata.fields());
        for (FieldInfo field : mainFields) {
            if (!field.visible()) continue;
            SurfaceSchema<?> valueSurface = compileFieldToScene(data, field);
            if (valueSurface == null) continue;

            ContainerSurface row = ContainerSurface.horizontal();
            row.gap("0.5em");
            row.add(TextSurface.of(field.label() + ":").style("muted"));
            row.add(valueSurface);
            root.add(row);
        }

        return View.of(root).mode(SceneMode.FULL);
    }

    private static View compileCompactView(Object data, FieldMetadata metadata) {
        ContainerSurface root = ContainerSurface.horizontal().style("compact");

        FieldInfo primary = metadata.fields().stream()
            .filter(f -> Arrays.asList(f.style()).contains("heading"))
            .findFirst()
            .orElse(metadata.fields().isEmpty() ? null : metadata.fields().get(0));

        if (primary != null) {
            Object value = getFieldValue(data, primary.field());
            if (value != null) {
                root.add(TextSurface.of(String.valueOf(value)).style("compact-title"));
            }
        }

        return View.of(root).mode(SceneMode.COMPACT);
    }

    private static View compileChipView(Object data, FieldMetadata metadata) {
        FieldInfo primary = metadata.fields().stream()
            .filter(f -> Arrays.asList(f.style()).contains("heading"))
            .findFirst()
            .orElse(metadata.fields().isEmpty() ? null : metadata.fields().get(0));

        String label = "";
        if (primary != null) {
            Object value = getFieldValue(data, primary.field());
            if (value != null) {
                label = String.valueOf(value);
            }
        }

        return View.of(TextSurface.of(label).style("chip")).mode(SceneMode.CHIP);
    }

    private static View compilePreviewView(Object data, FieldMetadata metadata) {
        ContainerSurface root = ContainerSurface.vertical().style("preview");

        int count = 0;
        for (FieldInfo field : metadata.fields()) {
            if (!field.visible() || count >= 3) continue;

            SurfaceSchema<?> surface = compileFieldToScene(data, field);
            if (surface != null) {
                root.add(surface);
                count++;
            }
        }

        return View.of(root).mode(SceneMode.PREVIEW);
    }

    private static List<SurfaceSchema<?>> compileFieldsToScenes(Object data, List<FieldInfo> fields) {
        List<SurfaceSchema<?>> surfaces = new ArrayList<>();
        for (FieldInfo field : fields) {
            if (!field.visible()) continue;
            SurfaceSchema<?> surface = compileFieldToScene(data, field);
            if (surface != null) {
                surfaces.add(surface);
            }
        }
        return surfaces;
    }

    private static SurfaceSchema<?> compileFieldToScene(Object data, FieldInfo field) {
        Object value = getFieldValue(data, field.field());
        if (value == null) {
            return null;
        }

        SurfaceSchema<?> surface = valueToScene(value, field);

        if (field.id() != null && !field.id().isEmpty()) {
            surface.id(field.id());
        }
        if (field.style().length > 0) {
            surface.style(field.style());
        }
        if (field.label() != null && !field.label().isEmpty()
                && !(surface instanceof HandleSurface)) {
            surface.label(field.label());
        }
        surface.editable(field.editable());

        return surface;
    }

    private static SurfaceSchema<?> valueToScene(Object value, FieldInfo field) {
        // Check if a specific surface pattern is requested
        Class<?> asType = field.as();
        if (asType != null && asType != SurfaceSchema.class && asType != SceneSchema.class) {
            if (SurfaceSchema.class.isAssignableFrom(asType)) {
                @SuppressWarnings("unchecked")
                Class<? extends SurfaceSchema> schemaType = (Class<? extends SurfaceSchema>) asType;
                return instantiateScene(schemaType, value, field);
            } else if (canCompile(asType)) {
                SurfaceSchema<Object> wrapper = new SurfaceSchema<>() {};
                wrapper.value(value);
                wrapper.structureClass(asType);
                return wrapper;
            }
        }

        // Default surface based on type
        if (value instanceof String s) {
            return TextSurface.of(s).format(field.format());
        }
        if (value instanceof List<?> list) {
            return compileList(list, field);
        }
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                return TextSurface.of("(empty)").style("muted");
            }
            ContainerSurface container = ContainerSurface.vertical();
            container.gap("0.2em");
            for (var entry : map.entrySet()) {
                ContainerSurface row = ContainerSurface.horizontal();
                row.gap("0.5em");
                row.add(valueToScene(entry.getKey(), field));
                row.add(TextSurface.of("\u2192").style("muted"));
                row.add(valueToScene(entry.getValue(), field));
                container.add(row);
            }
            return container;
        }
        if (value instanceof Boolean b) {
            return TextSurface.of(b.toString()).style("boolean");
        }
        if (value instanceof Enum<?> e) {
            return TextSurface.of(e.name()).style("enum");
        }
        if (value instanceof Number n) {
            return TextSurface.of(n.toString()).style("number");
        }
        if (value instanceof byte[] bytes) {
            String hex = HexFormat.of().formatHex(bytes);
            if (hex.length() > 64) {
                hex = hex.substring(0, 64) + "\u2026";
            }
            return TextSurface.of(hex).format("code");
        }
        if (value instanceof ItemID iid) {
            return resolveItemHandle(iid);
        }
        if (value instanceof HashID id) {
            return TextSurface.of(id.encodeText()).format("code");
        }
        if (value instanceof BindingTarget.IidTarget iidTarget) {
            return resolveItemHandle(iidTarget.iid());
        }
        if (value instanceof Literal literal) {
            return TextSurface.of(literal.toString()).format("code");
        }
        if (value instanceof Canonical c) {
            return compile(c, field.mode()).root();
        }
        if (value instanceof Instant instant) {
            return TextSurface.of(instant.toString()).style("timestamp");
        }

        return TextSurface.of(String.valueOf(value));
    }

    /**
     * Default value rendering when no annotated field metadata exists.
     */
    private static SurfaceSchema<?> defaultValueToScene(Object value, SceneMode mode) {
        if (value == null) return TextSurface.of("(null)").style("muted");
        Set<Object> guard = DEFAULT_RENDER_GUARD.get();
        if (!guard.add(value)) {
            return TextSurface.of(String.valueOf(value)).style("muted");
        }
        try {
        if (value instanceof String s) return TextSurface.of(s);
        if (value instanceof Number n) return TextSurface.of(n.toString()).style("number");
        if (value instanceof Boolean b) return TextSurface.of(b.toString()).style("boolean");
        if (value instanceof Enum<?> e) return TextSurface.of(e.name()).style("enum");
        if (value instanceof ItemID iid) return resolveItemHandle(iid);
        if (value instanceof HashID id) {
            return TextSurface.of(id.encodeText()).format("code");
        }
        if (value instanceof BindingTarget.IidTarget iidTarget) {
            return resolveItemHandle(iidTarget.iid());
        }
        if (value instanceof Literal literal) {
            return TextSurface.of(literal.toString()).format("code");
        }
        if (value instanceof Instant instant) {
            return TextSurface.of(instant.toString()).style("timestamp");
        }
        if (value instanceof List<?> list) {
            ListSurface listSurface = new ListSurface().itemMode(mode);
            for (Object item : list) {
                SurfaceSchema<?> elem = defaultValueToScene(item, mode);
                if (elem != null) listSurface.add(elem);
            }
            return listSurface;
        }
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) return TextSurface.of("(empty)").style("muted");
            ContainerSurface container = ContainerSurface.vertical();
            container.gap("0.2em");
            for (var entry : map.entrySet()) {
                ContainerSurface row = ContainerSurface.horizontal();
                row.gap("0.5em");
                row.add(defaultValueToScene(entry.getKey(), mode));
                row.add(TextSurface.of("\u2192").style("muted"));
                row.add(defaultValueToScene(entry.getValue(), mode));
                container.add(row);
            }
            return container;
        }
        if (value instanceof Canonical c) {
            // Prevent compile -> defaultValueToScene -> compile recursion for Canonicals
            // that do not expose annotated fields (e.g., Hash/ID wrappers).
            FieldMetadata metadata = scanFields(value.getClass());
            boolean anyVisible = metadata.fields().stream().anyMatch(FieldInfo::visible);
            if (metadata.fields().isEmpty() || !anyVisible) {
                return TextSurface.of(String.valueOf(value)).format("code");
            }
            View v = compile(c, mode);
            return v != null ? v.root() : null;
        }
        return TextSurface.of(String.valueOf(value));
        } finally {
            guard.remove(value);
            if (guard.isEmpty()) {
                DEFAULT_RENDER_GUARD.remove();
            }
        }
    }

    /**
     * Resolve an ItemID to a HandleSurface if an item resolver is available.
     * Falls back to encoded text if no resolver or item not found.
     *
     * <p>Name resolution priority:
     * <ol>
     *   <li>Instance name from displayInfo (content fields: name, title, label)</li>
     *   <li>displayToken &mdash; Sememes return their canonical key part (e.g., "title")</li>
     *   <li>Type name from SurfaceTemplateComponent (e.g., "NounSememe")</li>
     * </ol>
     */
    private static SurfaceSchema<?> resolveItemHandle(ItemID iid) {
        if (iid == null) return TextSurface.of("(none)").style("muted");

        var resolver = ITEM_RESOLVER.get();
        if (resolver != null) {
            var item = resolver.apply(iid);
            if (item.isPresent()) {
                var i = item.get();
                var info = i.displayInfo();
                String className = i.getClass().getSimpleName();

                // 1. Try instance name from displayInfo (content fields)
                String label = info.name();

                // 2. If name is just the Java class name, try displayToken
                if (label == null || label.isBlank() || label.equals(className)) {
                    String token = i.displayToken();
                    if (token != null && !token.equals(className)) {
                        label = token;
                    }
                }

                // 3. Final fallback: type name from SurfaceTemplateComponent
                if (label == null || label.isBlank() || label.equals(className)) {
                    label = info.typeName();
                }

                return HandleSurface.of(info.effectiveIconText(), label);
            }
        }

        // Fallback: truncated encoded ID
        String encoded = iid.encodeText();
        if (encoded.length() > 32) encoded = encoded.substring(0, 31) + "\u2026";
        return TextSurface.of(encoded).format("code");
    }

    private static SurfaceSchema<?> compileList(List<?> list, FieldInfo field) {
        ListSurface listSurface = new ListSurface().itemMode(field.mode());

        for (Object item : list) {
            if (item instanceof Canonical c) {
                View itemView = compile(c, field.mode());
                if (itemView.root() != null) {
                    listSurface.add(itemView.root());
                }
            } else {
                listSurface.add(TextSurface.of(String.valueOf(item)));
            }
        }

        return listSurface;
    }

    private static SurfaceSchema<?> instantiateScene(Class<? extends SurfaceSchema> type, Object value, FieldInfo field) {
        try {
            if (type == ChipsSurface.class && value instanceof List<?> list) {
                ChipsSurface chips = new ChipsSurface();
                for (Object item : list) {
                    chips.add(String.valueOf(item));
                }
                return chips;
            }

            if (type == TreeSurface.class) {
                return new TreeSurface();
            }

            return valueToScene(value, new FieldInfo(field.field(), null));
        } catch (Exception e) {
            return TextSurface.of(String.valueOf(value));
        }
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    /**
     * Check if a class has methods annotated with @Scene.
     */
    private static boolean hasAnnotatedMethods(Class<?> clazz) {
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.isAnnotationPresent(Scene.class)) {
                return true;
            }
        }
        return false;
    }

    private static Scene findTypeScene(Class<?> clazz) {
        while (clazz != null && clazz != Object.class) {
            Scene scene = clazz.getAnnotation(Scene.class);
            if (scene != null && scene.as() != SceneSchema.class) {
                return scene;
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    private static Scene findClassScene(Class<?> clazz) {
        while (clazz != null && clazz != Object.class) {
            Scene scene = clazz.getAnnotation(Scene.class);
            if (scene != null) {
                return scene;
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static SurfaceSchema<?> instantiateSchema(Class<? extends SurfaceSchema> schemaClass,
                                                       Object object, SceneMode mode) {
        try {
            try {
                var method = schemaClass.getMethod("from", object.getClass(), SceneMode.class);
                return (SurfaceSchema<?>) method.invoke(null, object, mode);
            } catch (NoSuchMethodException e) {
                // Continue
            }

            if (object instanceof Item) {
                try {
                    var method = schemaClass.getMethod("from",
                            Item.class, SceneMode.class);
                    return (SurfaceSchema<?>) method.invoke(null, object, mode);
                } catch (NoSuchMethodException e) {
                    // Continue
                }
            }

            @SuppressWarnings("unchecked")
            SurfaceSchema<Object> schema = (SurfaceSchema<Object>)
                    schemaClass.getDeclaredConstructor().newInstance();
            schema.value(object);
            return schema;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Resolve a BoxBorder from compiled node border properties.
     */
    private static BoxBorder resolveBorder(ViewNode node) {
        boolean hasBorderProps = !node.border.isEmpty()
                || !node.borderTop.isEmpty() || !node.borderRight.isEmpty()
                || !node.borderBottom.isEmpty() || !node.borderLeft.isEmpty()
                || !node.borderWidth.isEmpty() || !node.borderStyle.isEmpty()
                || !node.borderColor.isEmpty() || !node.borderRadius.isEmpty();

        if (!hasBorderProps) return BoxBorder.NONE;

        return BoxBorder.resolve(
                node.border, node.borderTop, node.borderRight,
                node.borderBottom, node.borderLeft,
                node.borderWidth, node.borderStyle, node.borderColor,
                node.borderRadius);
    }

    /**
     * Resolve {@code @Scene.Border} from a method/field annotation to a BoxBorder.
     *
     * @return the resolved border, or null if no border annotation present
     */
    private static BoxBorder resolveBorderAnnotation(LayoutElement element) {
        Scene.Border borderAnn = element.getAnnotation(Scene.Border.class);
        if (borderAnn == null) return null;

        boolean hasBorderProps = !borderAnn.all().isEmpty()
                || !borderAnn.top().isEmpty() || !borderAnn.right().isEmpty()
                || !borderAnn.bottom().isEmpty() || !borderAnn.left().isEmpty()
                || !borderAnn.width().isEmpty() || !borderAnn.style().isEmpty()
                || !borderAnn.color().isEmpty() || !borderAnn.radius().isEmpty();

        if (!hasBorderProps) return null;

        return BoxBorder.resolve(
                borderAnn.all(), borderAnn.top(), borderAnn.right(),
                borderAnn.bottom(), borderAnn.left(),
                borderAnn.width(), borderAnn.style(), borderAnn.color(),
                borderAnn.radius());
    }

    /**
     * Apply {@code @Scene.Border} from a method/field annotation to the content surface.
     */
    private static void applyBorderAnnotation(SurfaceSchema<?> content, LayoutElement element) {
        BoxBorder border = resolveBorderAnnotation(element);
        if (border != null) {
            content.border(border);
        }
    }

    private static String resolveText(ViewNode node, StructureRenderContext ctx) {
        if (!node.textBind.isEmpty()) {
            return ctx.resolveBinding(node.textBind);
        }
        return node.textContent;
    }

    /**
     * Resolve a rotation value — static number or "bind:value.X" expression.
     */
    private static double resolveRotation(String rotation, StructureRenderContext ctx) {
        if (rotation == null || rotation.isEmpty()) return 0.0;

        if (rotation.startsWith("bind:")) {
            Object resolved = ctx.resolveValue(rotation.substring(5));
            if (resolved instanceof Number n) return n.doubleValue();
            if (resolved instanceof String s) {
                try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 0.0; }
            }
            return 0.0;
        }

        try {
            return Double.parseDouble(rotation);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * Resolve transform-origin spec to fractional coordinates.
     */
    private static float[] resolveTransformOrigin(String spec, StructureRenderContext ctx) {
        if (spec == null || spec.isEmpty()) return new float[]{0.5f, 0.5f};

        if (spec.startsWith("bind:")) {
            Object resolved = ctx.resolveValue(spec.substring(5));
            if (resolved instanceof String s) {
                spec = s;
            } else {
                return new float[]{0.5f, 0.5f};
            }
        }

        spec = spec.trim().toLowerCase();

        return switch (spec) {
            case "center" -> new float[]{0.5f, 0.5f};
            case "top" -> new float[]{0.5f, 0.0f};
            case "bottom" -> new float[]{0.5f, 1.0f};
            case "left" -> new float[]{0.0f, 0.5f};
            case "right" -> new float[]{1.0f, 0.5f};
            case "top left", "left top" -> new float[]{0.0f, 0.0f};
            case "top right", "right top" -> new float[]{1.0f, 0.0f};
            case "bottom left", "left bottom" -> new float[]{0.0f, 1.0f};
            case "bottom right", "right bottom" -> new float[]{1.0f, 1.0f};
            default -> parsePercentagePair(spec);
        };
    }

    private static float[] parsePercentagePair(String spec) {
        String[] parts = spec.split("\\s+");
        if (parts.length == 2) {
            float x = parseOriginComponent(parts[0], 0.5f);
            float y = parseOriginComponent(parts[1], 0.5f);
            return new float[]{x, y};
        }
        if (parts.length == 1) {
            float v = parseOriginComponent(parts[0], 0.5f);
            return new float[]{v, v};
        }
        return new float[]{0.5f, 0.5f};
    }

    private static float parseOriginComponent(String part, float fallback) {
        part = part.trim();
        return switch (part) {
            case "left", "top" -> 0.0f;
            case "center" -> 0.5f;
            case "right", "bottom" -> 1.0f;
            default -> {
                if (part.endsWith("%")) {
                    try {
                        yield Float.parseFloat(part.substring(0, part.length() - 1)) / 100f;
                    } catch (NumberFormatException e) {
                        yield fallback;
                    }
                }
                yield fallback;
            }
        };
    }

    /**
     * Convert a dimension string to meters.
     *
     * <p>Handles common physical units: mm, cm, m, km. Bare numbers default to meters.
     *
     * @param spec dimension string (e.g., "1m", "50cm", "3mm", "0")
     * @return value in meters, or 0 if null/empty/unparseable
     */
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

    /**
     * Parse a dimension string (e.g., "1m", "50cm", "0") to meters.
     */
    static double parseDimension(String spec) {
        return elevationToMeters(spec);
    }

    /**
     * Compute a quaternion from euler angles or axis+angle.
     *
     * <p>If any euler angle (yaw/pitch/roll) is non-zero, uses euler.
     * If angle is non-zero and no euler angles are set, uses axis+angle.
     * Otherwise returns identity quaternion.
     *
     * @return [qx, qy, qz, qw]
     */
    static double[] computeQuaternion(double yaw, double pitch, double roll,
                                       double axisX, double axisY, double axisZ,
                                       double angle) {
        // Euler angles
        if (yaw != 0 || pitch != 0 || roll != 0) {
            return eulerToQuaternion(yaw, pitch, roll);
        }

        // Axis+angle
        if (angle != 0) {
            return axisAngleToQuaternion(axisX, axisY, axisZ, angle);
        }

        // Identity
        return new double[]{0, 0, 0, 1};
    }

    /**
     * Convert euler angles (degrees) to quaternion.
     * Convention: yaw around Y, pitch around X, roll around Z.
     */
    private static double[] eulerToQuaternion(double yaw, double pitch, double roll) {
        double cy = Math.cos(Math.toRadians(yaw) * 0.5);
        double sy = Math.sin(Math.toRadians(yaw) * 0.5);
        double cp = Math.cos(Math.toRadians(pitch) * 0.5);
        double sp = Math.sin(Math.toRadians(pitch) * 0.5);
        double cr = Math.cos(Math.toRadians(roll) * 0.5);
        double sr = Math.sin(Math.toRadians(roll) * 0.5);

        double qw = cr * cp * cy + sr * sp * sy;
        double qx = sr * cp * cy - cr * sp * sy;
        double qy = cr * sp * cy + sr * cp * sy;
        double qz = cr * cp * sy - sr * sp * cy;

        return new double[]{qx, qy, qz, qw};
    }

    /**
     * Convert axis+angle (degrees) to quaternion.
     */
    private static double[] axisAngleToQuaternion(double ax, double ay, double az, double angleDeg) {
        double len = Math.sqrt(ax * ax + ay * ay + az * az);
        if (len < 1e-10) return new double[]{0, 0, 0, 1};

        ax /= len;
        ay /= len;
        az /= len;

        double halfAngle = Math.toRadians(angleDeg) * 0.5;
        double s = Math.sin(halfAngle);

        return new double[]{ax * s, ay * s, az * s, Math.cos(halfAngle)};
    }

    private static Object getFieldValue(Object data, Field field) {
        try {
            field.setAccessible(true);
            return field.get(data);
        } catch (Exception e) {
            return null;
        }
    }

    private static FieldMetadata scanFields(Class<?> clazz) {
        List<FieldInfo> fields = new ArrayList<>();

        for (Field field : getAllFields(clazz)) {
            Scene scene = field.getAnnotation(Scene.class);
            Canonical.Canon canon = field.getAnnotation(Canonical.Canon.class);

            if (scene != null || canon != null) {
                fields.add(new FieldInfo(field, scene));
            }
        }

        fields.sort(Comparator.comparingInt(FieldInfo::order));

        return new FieldMetadata(fields);
    }

    private static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }
        return fields;
    }

    private static List<Method> getAllMethods(Class<?> clazz) {
        List<Method> methods = new ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            methods.addAll(Arrays.asList(clazz.getDeclaredMethods()));
            clazz = clazz.getSuperclass();
        }
        return methods;
    }

    // ==================================================================================
    // Supporting Inner Types
    // ==================================================================================

    /** Field metadata container. */
    private static class FieldMetadata {
        private final List<FieldInfo> fields;

        FieldMetadata(List<FieldInfo> fields) {
            this.fields = fields;
        }

        List<FieldInfo> fields() { return fields; }
    }

    /** Field info for data model compilation. */
    static class FieldInfo {
        private final Field field;
        private final Scene scene;

        FieldInfo(Field field, Scene scene) {
            this.field = field;
            this.scene = scene;
        }

        Field field() { return field; }

        int order() {
            if (scene != null && scene.order() >= 0) return scene.order();
            Canonical.Canon canon = field.getAnnotation(Canonical.Canon.class);
            if (canon != null) return canon.order();
            return Integer.MAX_VALUE;
        }

        Class<?> as() {
            return scene != null ? scene.as() : SceneSchema.class;
        }

        SceneMode mode() {
            return scene != null ? scene.mode() : SceneMode.FULL;
        }

        String format() {
            return scene != null ? scene.format() : "";
        }

        String[] style() {
            return scene != null ? scene.classes() : new String[0];
        }

        String id() {
            return scene != null ? scene.id() : "";
        }

        String label() {
            if (scene != null && !scene.label().isEmpty()) return scene.label();
            return toTitleCase(field.getName());
        }

        String region() {
            return scene != null ? scene.region() : "";
        }

        boolean visible() {
            return scene == null || scene.visible();
        }

        boolean editable() {
            return scene != null && scene.editable();
        }

        private static String toTitleCase(String s) {
            if (s == null || s.isEmpty()) return s;
            StringBuilder result = new StringBuilder();
            result.append(Character.toUpperCase(s.charAt(0)));
            for (int i = 1; i < s.length(); i++) {
                char c = s.charAt(i);
                if (Character.isUpperCase(c)) result.append(' ');
                result.append(c);
            }
            return result.toString();
        }
    }

    /** Layout element abstraction for method/field compilation. */
    interface LayoutElement {
        String id();
        SurfaceSchema<?> invoke();
        <T extends Annotation> T getAnnotation(Class<T> type);
    }

    private static class MethodElement implements LayoutElement {
        private final Object model;
        private final Method method;
        private final Scene scene;

        MethodElement(Object model, Method method, Scene scene) {
            this.model = model;
            this.method = method;
            this.scene = scene;
        }

        @Override
        public String id() {
            return scene.id().isEmpty() ? method.getName() : scene.id();
        }

        @Override
        public SurfaceSchema<?> invoke() {
            try {
                method.setAccessible(true);
                Object result = method.invoke(model);
                if (result instanceof SurfaceSchema<?> ss) return ss;
                if (result != null) {
                    System.err.println("[SceneCompiler] Method " + method.getName()
                            + " returned " + result.getClass().getSimpleName()
                            + " (not SurfaceSchema), ignoring");
                }
            } catch (Exception e) {
                System.err.println("[SceneCompiler] Method " + method.getName()
                        + " threw: " + e.getCause());
                e.getCause().printStackTrace(System.err);
            }
            return null;
        }

        @Override
        public <T extends Annotation> T getAnnotation(Class<T> type) {
            return method.getAnnotation(type);
        }
    }

    private static class FieldElement implements LayoutElement {
        private final Object model;
        private final Field field;
        private final Scene scene;

        FieldElement(Object model, Field field, Scene scene) {
            this.model = model;
            this.field = field;
            this.scene = scene;
        }

        @Override
        public String id() {
            return scene.id().isEmpty() ? field.getName() : scene.id();
        }

        @Override
        public SurfaceSchema<?> invoke() {
            try {
                field.setAccessible(true);
                Object value = field.get(model);
                if (value instanceof SurfaceSchema<?> ss) return ss;
            } catch (Exception e) {
                // Ignore
            }
            return null;
        }

        @Override
        public <T extends Annotation> T getAnnotation(Class<T> type) {
            return field.getAnnotation(type);
        }
    }

    // ==================================================================================
    // Structure Render Context
    // ==================================================================================

    /**
     * Context for resolving bindings and evaluating conditions during rendering.
     */
    private static class StructureRenderContext {
        private final SurfaceSchema<?> schema;
        private final Object value;
        private final Object item;
        private final int index;
        private final String itemVar;
        private final String indexVar;

        StructureRenderContext(SurfaceSchema<?> schema) {
            this.schema = schema;
            this.value = schema.value();
            this.item = null;
            this.index = -1;
            this.itemVar = "item";
            this.indexVar = "index";
        }

        private StructureRenderContext(SurfaceSchema<?> schema, Object value,
                                       Object item, int index,
                                       String itemVar, String indexVar) {
            this.schema = schema;
            this.value = value;
            this.item = item;
            this.index = index;
            this.itemVar = itemVar;
            this.indexVar = indexVar;
        }

        StructureRenderContext withItem(Object item, int index, String itemVar, String indexVar) {
            return new StructureRenderContext(schema, value, item, index, itemVar, indexVar);
        }

        boolean evaluate(String condition) {
            if (condition == null || condition.isEmpty()) return true;

            boolean negate = condition.startsWith("!");
            String expr = negate ? condition.substring(1) : condition;

            boolean result = evaluatePositive(expr);
            return negate ? !result : result;
        }

        private boolean evaluatePositive(String expr) {
            // Equality comparisons
            if (expr.contains(" == ")) {
                String[] parts = expr.split(" == ", 2);
                Object left = resolveValue(parts[0].trim());
                Object right = resolveValue(parts[1].trim());
                return Objects.equals(left, right);
            }
            if (expr.contains(" != ")) {
                String[] parts = expr.split(" != ", 2);
                Object left = resolveValue(parts[0].trim());
                Object right = resolveValue(parts[1].trim());
                return !Objects.equals(left, right);
            }

            // $ variable check
            if (expr.startsWith("$")) {
                String prop = expr.substring(1);
                return switch (prop) {
                    case "editable" -> schema.editable();
                    case "visible" -> schema.visible();
                    case "tabbable" -> Boolean.TRUE.equals(schema.tabbable());
                    case "label" -> schema.label() != null && !schema.label().isEmpty();
                    case "id" -> schema.id() != null && !schema.id().isEmpty();
                    default -> {
                        Object resolved = resolveValue(expr);
                        yield isTruthy(resolved);
                    }
                };
            }

            if ("value".equals(expr)) return isTruthy(value);

            if (expr.startsWith("value.")) {
                Object propValue = getProperty(value, expr.substring(6));
                return isTruthy(propValue);
            }

            Object propValue = getProperty(schema, expr);
            if (propValue != null) return isTruthy(propValue);

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

            // Handle $ variables
            if (bindExpr.startsWith("$")) {
                String varName = bindExpr.substring(1);
                int dotIdx = varName.indexOf('.');
                String base = dotIdx > 0 ? varName.substring(0, dotIdx) : varName;
                String rest = dotIdx > 0 ? varName.substring(dotIdx + 1) : "";

                Object baseValue = switch (base) {
                    case "label" -> schema.label();
                    case "id" -> schema.id();
                    default -> {
                        if (base.equals(itemVar) || base.equals("item")) {
                            yield item;
                        } else if (base.equals(indexVar) || base.equals("index")) {
                            yield index;
                        }
                        yield null;
                    }
                };

                if (baseValue != null && !rest.isEmpty()) {
                    return getProperty(baseValue, rest);
                }
                return baseValue;
            }

            if ("value".equals(bindExpr)) return value;
            if (bindExpr.startsWith("value.")) {
                return getProperty(value, bindExpr.substring(6));
            }

            // Direct property access on schema
            Object schemaProp = getProperty(schema, bindExpr);
            if (schemaProp != null) return schemaProp;

            // Direct property access on value
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

            // Fluent accessor
            try {
                Method method = clazz.getMethod(propertyName);
                return method.invoke(obj);
            } catch (Exception ignored) {}

            // JavaBean getter
            String getterName = "get" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
            try {
                Method method = clazz.getMethod(getterName);
                return method.invoke(obj);
            } catch (Exception ignored) {}

            // Boolean getter
            String isName = "is" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
            try {
                Method method = clazz.getMethod(isName);
                return method.invoke(obj);
            } catch (Exception ignored) {}

            // Map key access
            if (obj instanceof Map<?, ?> map) {
                return map.get(propertyName);
            }

            return null;
        }
    }
}
