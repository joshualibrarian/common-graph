package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.item.Factory;

import dev.everydaythings.graph.item.Type;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.DisplayInfo;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.ui.scene.SceneCompiler;
import dev.everydaythings.graph.ui.scene.ViewNode;
import dev.everydaythings.graph.value.Color;

/**
 * Component that stores a compiled scene template (ViewNode tree) and display
 * metadata on an Item.
 *
 * <p>This is the unified presentation component for type items. It holds both:
 * <ul>
 *   <li>The <b>surface template</b> (a compiled ViewNode tree from {@code @Scene} annotations)</li>
 *   <li>The <b>display metadata</b> (glyph, color, shape, typeName, icon paths)</li>
 * </ul>
 *
 * <p>Resolution order:
 * <ol>
 *   <li>Check if item has its own SurfaceTemplateComponent &rarr; use it</li>
 *   <li>Look up item's type &rarr; get type's SurfaceTemplateComponent</li>
 *   <li>Fall back to live annotation compilation</li>
 * </ol>
 *
 * @see SceneCompiler
 * @see ViewNode
 */
@Type(value = SurfaceTemplateComponent.KEY, glyph = "\uD83D\uDDBC")
public class SurfaceTemplateComponent implements Canonical {

    public static final String KEY = "cg:type/surface-template";
    public static final FrameKey HANDLE = FrameKey.literal("surface");

    // ==================================================================================
    // Surface Template
    // ==================================================================================

    @Canonical.Canon(order = 0)
    private ViewNode root;

    // ==================================================================================
    // Display Metadata (formerly DisplayComponent)
    // ==================================================================================

    /** The glyph (emoji/icon) for display. */
    private String glyph;

    /** RGB color packed as int (0xRRGGBB). */
    private int color;

    /** Shape kind: "sphere", "cube", or "disc". */
    private String shape;

    /** Optional type name (e.g., "Librarian"). */
    private String typeName;

    /** Optional 2D icon path. */
    private String iconPath2D;

    /** Optional 3D model path. */
    private String iconPath3D;

    // ==================================================================================
    // Constructors
    // ==================================================================================

    public SurfaceTemplateComponent() {}

    public SurfaceTemplateComponent(ViewNode root) {
        this.root = root;
    }

    // ==================================================================================
    // Factory Methods
    // ==================================================================================

    /**
     * Compile a scene template from a class with @Scene annotations.
     *
     * @param clazz The annotated class (SceneSchema/SurfaceSchema subclass or model class)
     * @return The compiled template, or null if the class has no compilable scene
     */
    public static SurfaceTemplateComponent compile(Class<?> clazz) {
        if (!SceneCompiler.canCompile(clazz)) return null;
        ViewNode compiled = SceneCompiler.getCompiled(clazz);
        if (compiled == null) return null;
        return new SurfaceTemplateComponent(compiled);
    }

    /**
     * Create a SurfaceTemplateComponent with display fields from a @Type annotation.
     */
    public static SurfaceTemplateComponent fromType(Type annotation) {
        SurfaceTemplateComponent stc = new SurfaceTemplateComponent();
        stc.glyph = annotation.glyph().isEmpty() ? "\uD83D\uDCE6" : annotation.glyph();
        stc.color = annotation.color();
        stc.shape = annotation.shape().isEmpty() ? "sphere" : annotation.shape();
        if (!annotation.icon().isEmpty()) {
            stc.iconPath2D = annotation.icon();
        }
        return stc;
    }

    /**
     * Create a SurfaceTemplateComponent with display fields from a @Value.Type annotation.
     */
    public static SurfaceTemplateComponent fromValueType(dev.everydaythings.graph.value.Value.Type annotation) {
        SurfaceTemplateComponent stc = new SurfaceTemplateComponent();
        stc.glyph = annotation.glyph().isEmpty() ? "\uD83D\uDCE6" : annotation.glyph();
        stc.color = annotation.color();
        stc.shape = annotation.shape().isEmpty() ? "sphere" : annotation.shape();
        return stc;
    }

    // ==================================================================================
    // Root Accessors
    // ==================================================================================

    public ViewNode root() {
        return root;
    }

    public SurfaceTemplateComponent root(ViewNode root) {
        this.root = root;
        return this;
    }

    // ==================================================================================
    // Display Accessors
    // ==================================================================================

    public String glyph() {
        return glyph;
    }

    public SurfaceTemplateComponent glyph(String glyph) {
        this.glyph = glyph;
        return this;
    }

    public int color() {
        return color;
    }

    public SurfaceTemplateComponent color(int color) {
        this.color = color;
        return this;
    }

    public String shape() {
        return shape;
    }

    public SurfaceTemplateComponent shape(String shape) {
        this.shape = shape;
        return this;
    }

    public String typeName() {
        return typeName;
    }

    public SurfaceTemplateComponent typeName(String typeName) {
        this.typeName = typeName;
        return this;
    }

    public String iconPath2D() {
        return iconPath2D;
    }

    public SurfaceTemplateComponent iconPath2D(String path) {
        this.iconPath2D = path;
        return this;
    }

    public String iconPath3D() {
        return iconPath3D;
    }

    public SurfaceTemplateComponent iconPath3D(String path) {
        this.iconPath3D = path;
        return this;
    }

    // ==================================================================================
    // Conversion
    // ==================================================================================

    /**
     * Convert to a DisplayInfo for rendering.
     *
     * @param name The instance name to use
     * @return DisplayInfo ready for rendering
     */
    public DisplayInfo toDisplayInfo(String name) {
        return DisplayInfo.builder()
                .name(name)
                .typeName(typeName)
                .color(Color.fromPacked(color))
                .iconText(glyph)
                .shape(parseShape(shape))
                .iconPath2D(iconPath2D)
                .iconPath3D(iconPath3D)
                .build();
    }

    /**
     * Convert to a DisplayInfo using this component's data as defaults.
     */
    public DisplayInfo toDisplayInfo() {
        return toDisplayInfo(null);
    }

    private static DisplayInfo.Shape parseShape(String shape) {
        if (shape == null) return DisplayInfo.Shape.SPHERE;
        return switch (shape.toLowerCase()) {
            case "cube" -> DisplayInfo.Shape.CUBE;
            case "disc" -> DisplayInfo.Shape.DISC;
            default -> DisplayInfo.Shape.SPHERE;
        };
    }

    /**
     * Get the color as a CG Color.
     */
    public Color toColor() {
        return Color.fromPacked(color);
    }

    // ==================================================================================
    // Display Implementation
    // ==================================================================================

    public String displayToken() {
        return typeName != null ? typeName : "Surface Template";
    }

    public String emoji() {
        return "\uD83D\uDDBC";
    }

    // ==================================================================================
    // Canonical Encoding
    // ==================================================================================

    @Override
    public CBORObject toCborTree(Scope scope) {
        CBORObject obj = CBORObject.NewMap();
        if (root != null) {
            obj.set("root", root.toCborTree(scope));
        }
        // Display fields
        if (glyph != null) {
            obj.set("glyph", CBORObject.FromObject(glyph));
        }
        if (color != 0) {
            obj.set("color", CBORObject.FromObject(color));
        }
        if (shape != null) {
            obj.set("shape", CBORObject.FromObject(shape));
        }
        if (typeName != null) {
            obj.set("typeName", CBORObject.FromObject(typeName));
        }
        if (iconPath2D != null) {
            obj.set("iconPath2D", CBORObject.FromObject(iconPath2D));
        }
        if (iconPath3D != null) {
            obj.set("iconPath3D", CBORObject.FromObject(iconPath3D));
        }
        return obj;
    }

    @Factory
    public static SurfaceTemplateComponent fromCborTree(CBORObject obj) {
        SurfaceTemplateComponent template = new SurfaceTemplateComponent();
        if (obj.ContainsKey("root")) {
            template.root = Canonical.fromCborTree(
                    obj.get("root"), ViewNode.class, Canonical.Scope.RECORD);
        }
        // Display fields
        if (obj.ContainsKey("glyph")) {
            template.glyph = obj.get("glyph").AsString();
        }
        if (obj.ContainsKey("color")) {
            template.color = obj.get("color").AsInt32();
        }
        if (obj.ContainsKey("shape")) {
            template.shape = obj.get("shape").AsString();
        }
        if (obj.ContainsKey("typeName")) {
            template.typeName = obj.get("typeName").AsString();
        }
        if (obj.ContainsKey("iconPath2D")) {
            template.iconPath2D = obj.get("iconPath2D").AsString();
        }
        if (obj.ContainsKey("iconPath3D")) {
            template.iconPath3D = obj.get("iconPath3D").AsString();
        }
        return template;
    }
}
