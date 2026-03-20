package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.frame.ItemFrame;
import dev.everydaythings.graph.item.Factory;
import dev.everydaythings.graph.item.Implements;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.ItemSeed;


import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.DisplayInfo;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.language.SememeGloss;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.ui.scene.SceneCompiler;
import dev.everydaythings.graph.ui.scene.ViewNode;
import dev.everydaythings.graph.value.Color;
import dev.everydaythings.graph.language.CoreVocabulary;

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
@Implements(SurfaceTemplateComponent.KEY)
@ItemSeed(key = SurfaceTemplateComponent.KEY)
public class SurfaceTemplateComponent implements Canonical {

    public static final String KEY = "cg.sememe:surface-template";

    @ItemFrame(predicate = SememeGloss.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
    static final String seedGloss = "display template for an item type";

    @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY, fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String seedNoun = "surface-template";

    public static final FrameKey HANDLE = FrameKey.of(ItemID.fromString(KEY));

    // ==================================================================================
    // Surface Template
    // ==================================================================================

    @Canonical.Canon(order = 0)
    private ViewNode root;

    // ==================================================================================
    // Display Metadata — DEPRECATED (Phase 7)
    //
    // These fields are superseded by PresentationConfig in the presentation cascade.
    // @Type glyph/color/shape now flow through PresentationConfig stored on seed items.
    // Kept for backward compat with existing serialized STCs and non-seed items.
    // ==================================================================================

    /** @deprecated Use PresentationConfig cascade via Item.resolvedPresentation(). */
    @Deprecated
    private String glyph;

    /** @deprecated Use PresentationConfig cascade (PRIMARY palette token). */
    @Deprecated
    private int color;

    /** @deprecated Use PresentationConfig cascade (shape field). */
    @Deprecated
    private String shape;

    /** @deprecated Use Item.findTypeName(). */
    @Deprecated
    private String typeName;

    /** @deprecated Use PresentationConfig or @Scene.Body(image=...). */
    @Deprecated
    private String iconPath2D;

    /** @deprecated Use @Scene.Body(mesh=...). */
    @Deprecated
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
     * Create a SurfaceTemplateComponent from an @Implements annotation.
     */
    public static SurfaceTemplateComponent fromImplements(Implements annotation) {
        SurfaceTemplateComponent stc = new SurfaceTemplateComponent();
        stc.glyph = "📦".isEmpty() ? "\uD83D\uDCE6" : "📦";
        stc.color = 0x78788C;
        stc.shape = "sphere";
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
