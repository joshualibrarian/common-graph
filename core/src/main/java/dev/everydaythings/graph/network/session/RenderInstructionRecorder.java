package dev.everydaythings.graph.network.session;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.ui.scene.BoxBorder;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer;
import dev.everydaythings.graph.ui.scene.surface.primitive.TextSpan;

import java.util.List;

/**
 * A {@link SurfaceRenderer} that records all render calls as a CBOR array.
 *
 * <p>Each call becomes an instruction: {@code ["methodName", arg1, arg2, ...]}.
 * The full stream is an array of these instruction arrays, suitable for
 * replay by a remote renderer (e.g., the JS web client's {@code renderer.js}).
 *
 * <p>This is the bridge between CG's server-side scene compilation and
 * client-side rendering. The server compiles a View, renders it through
 * this recorder, and sends the instruction stream over the wire.
 *
 * <p>Usage:
 * <pre>
 * RenderInstructionRecorder recorder = new RenderInstructionRecorder();
 * view.root().render(recorder);
 * CBORObject instructions = recorder.toCbor();
 * </pre>
 */
public class RenderInstructionRecorder implements SurfaceRenderer {

    private final CBORObject instructions = CBORObject.NewArray();

    /**
     * Get the recorded instructions as a CBOR array.
     */
    public CBORObject toCbor() {
        return instructions;
    }

    private void emit(String method, Object... args) {
        CBORObject instr = CBORObject.NewArray();
        instr.Add(CBORObject.FromString(method));
        for (Object arg : args) {
            instr.Add(toCborValue(arg));
        }
        instructions.Add(instr);
    }

    @SuppressWarnings("unchecked")
    private CBORObject toCborValue(Object value) {
        if (value == null) return CBORObject.Null;
        if (value instanceof String s) return CBORObject.FromString(s);
        if (value instanceof Boolean b) return b ? CBORObject.True : CBORObject.False;
        if (value instanceof Integer i) return CBORObject.FromInt32(i);
        if (value instanceof Long l) return CBORObject.FromInt64(l);
        if (value instanceof Float f) return CBORObject.FromDouble(f.doubleValue());
        if (value instanceof Double d) return CBORObject.FromDouble(d);
        if (value instanceof Scene.Direction d) return CBORObject.FromString(d.name());
        if (value instanceof ContentID cid) return CBORObject.FromString(cid.encodeText());
        if (value instanceof BoxBorder b) return borderToCbor(b);
        if (value instanceof List<?> list) {
            CBORObject arr = CBORObject.NewArray();
            for (Object item : list) {
                if (item instanceof TextSpan span) {
                    CBORObject spanObj = CBORObject.NewMap();
                    spanObj.set("text", CBORObject.FromString(span.text()));
                    if (span.styles() != null && !span.styles().isEmpty()) {
                        CBORObject stylesArr = CBORObject.NewArray();
                        for (String s : span.styles()) stylesArr.Add(CBORObject.FromString(s));
                        spanObj.set("styles", stylesArr);
                    }
                    arr.Add(spanObj);
                } else {
                    arr.Add(toCborValue(item));
                }
            }
            return arr;
        }
        return CBORObject.FromString(value.toString());
    }

    private CBORObject borderToCbor(BoxBorder border) {
        if (border == null) return CBORObject.Null;
        CBORObject obj = CBORObject.NewMap();
        if (border.top() != null) obj.set("top", CBORObject.FromString(borderSideStr(border.top())));
        if (border.right() != null) obj.set("right", CBORObject.FromString(borderSideStr(border.right())));
        if (border.bottom() != null) obj.set("bottom", CBORObject.FromString(borderSideStr(border.bottom())));
        if (border.left() != null) obj.set("left", CBORObject.FromString(borderSideStr(border.left())));
        if (border.radius() != null) obj.set("radius", CBORObject.FromString(border.radius()));
        return obj;
    }

    private String borderSideStr(BoxBorder.BorderSide side) {
        if (side == null) return "";
        StringBuilder sb = new StringBuilder();
        if (side.width() != null) sb.append(side.width());
        if (side.style() != null) sb.append(' ').append(side.style());
        if (side.color() != null) sb.append(' ').append(side.color());
        return sb.toString().trim();
    }

    // ==================== Text ====================

    @Override
    public void text(String content, List<String> styles) {
        emit("text", content, styles);
    }

    @Override
    public void formattedText(String content, String format, List<String> styles) {
        emit("formattedText", content, format, styles);
    }

    @Override
    public void richText(List<TextSpan> spans, List<String> paragraphStyles) {
        emit("richText", spans, paragraphStyles);
    }

    // ==================== Image ====================

    @Override
    public void image(String alt, ContentID image, ContentID solid,
                      String size, String fit, List<String> styles) {
        emit("image", alt, image, solid, size, fit, styles);
    }

    @Override
    public void image(String alt, ContentID image, ContentID solid, String resource,
                      String size, String fit, List<String> styles) {
        emit("image", alt, resource, size, fit, styles);
    }

    // ==================== Container ====================

    @Override
    public void beginBox(Scene.Direction direction, List<String> styles) {
        emit("beginBox", direction, styles);
    }

    @Override
    public void beginBox(Scene.Direction direction, List<String> styles,
                         BoxBorder border, String background,
                         String width, String height, String padding) {
        emit("beginBox", direction, styles, border, background, width, height, padding);
    }

    @Override
    public void endBox() {
        emit("endBox");
    }

    // ==================== Metadata ====================

    @Override
    public void type(String type) {
        emit("type", type);
    }

    @Override
    public void id(String id) {
        emit("id", id);
    }

    @Override
    public void editable(boolean editable) {
        emit("editable", editable);
    }

    // ==================== Events ====================

    @Override
    public void event(String on, String action, String target) {
        emit("event", on, action, target);
    }

    // ==================== Audio ====================

    @Override
    public void audio(String src, double volume, boolean loop, List<String> styles) {
        emit("audio", src, volume, loop, styles);
    }

    // ==================== Layout hints ====================

    @Override
    public void gap(String gap) {
        emit("gap", gap);
    }

    @Override
    public void maxWidth(String maxWidth) {
        emit("maxWidth", maxWidth);
    }

    @Override
    public void maxHeight(String maxHeight) {
        emit("maxHeight", maxHeight);
    }

    @Override
    public void overflow(String overflow) {
        emit("overflow", overflow);
    }

    @Override
    public void aspectRatio(String ratio) {
        emit("aspectRatio", ratio);
    }

    // ==================== Text sizing ====================

    @Override
    public void textFontSize(String spec) {
        emit("fontSize", spec);
    }

    @Override
    public void textFontFamily(String family) {
        emit("fontFamily", family);
    }

    // ==================== Rotation ====================

    @Override
    public void rotation(double degrees) {
        emit("rotation", degrees);
    }

    @Override
    public void transformOrigin(float xFrac, float yFrac) {
        emit("transformOrigin", xFrac, yFrac);
    }

    // ==================== Shape ====================

    @Override
    public void shape(String type, String cornerRadius, String fill,
                      String stroke, String strokeWidth, String path) {
        emit("shape", type, cornerRadius, fill, stroke, strokeWidth, path);
    }

    @Override
    public void shapeSize(String width, String height) {
        emit("shapeSize", width, height);
    }

    // ==================== 3D hints (recorded but likely no-ops on web) ====================

    @Override
    public void elevation(double height, boolean solid) {
        emit("elevation", height, solid);
    }

    @Override
    public void model(String modelResource, int modelColor) {
        emit("model", modelResource, modelColor);
    }

    @Override
    public void tint(int argb) {
        emit("tint", argb);
    }
}
