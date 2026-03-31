package dev.everydaythings.graph.ui.scene.surface.item;

import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.Frame;
import dev.everydaythings.graph.frame.FrameBody;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.ui.scene.node.Container;
import dev.everydaythings.graph.ui.scene.node.Node;
import dev.everydaythings.graph.ui.scene.node.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Renders any CBORObject into a scene Node tree for inspection.
 *
 * <p>Type-aware: when the type of the data is known (e.g., FrameBody, Binding),
 * reads EXPECTS frames from the type's seed to label array positions with
 * meaningful field names instead of bare indices.
 *
 * <p>Supports all CG-CBOR encoding styles: arrays (with type-driven labels),
 * maps with string keys, and maps with sememe IID keys (resolved via librarian).
 * CG-CBOR tags (REF, VALUE, SIG, QTY) render with semantic awareness.
 */
public final class CborInspector {

    private CborInspector() {}

    /**
     * Render a Canonical object with type context.
     */
    public static Node render(Canonical canonical, Function<ItemID, String> resolver, ItemID typeId) {
        CBORObject cbor = canonical.toCborTree(Canonical.Scope.RECORD);
        List<FieldLabel> schema = typeId != null ? resolveSchema(typeId, resolver) : List.of();
        return renderValue(cbor, resolver, schema, 0);
    }

    /**
     * Render a Canonical object (type inferred if possible).
     */
    public static Node render(Canonical canonical, Function<ItemID, String> resolver) {
        ItemID typeId = inferType(canonical);
        return render(canonical, resolver, typeId);
    }

    /**
     * Render raw CBOR with optional type context.
     */
    public static Node render(CBORObject cbor, Function<ItemID, String> resolver, ItemID typeId) {
        if (cbor == null) return Text.of("null").classes("muted");
        List<FieldLabel> schema = typeId != null ? resolveSchema(typeId, resolver) : List.of();
        return renderValue(cbor, resolver, schema, 0);
    }

    /**
     * Render raw CBOR without type context.
     */
    public static Node render(CBORObject cbor, Function<ItemID, String> resolver) {
        return render(cbor, resolver, (ItemID) null);
    }

    // ==================================================================================
    // Schema resolution — EXPECTS frames → positional field labels
    // ==================================================================================

    record FieldLabel(String name, ItemID typeId) {}

    /**
     * Read EXPECTS frames from a type item to get array position labels.
     * Declaration order = position order.
     */
    private static List<FieldLabel> resolveSchema(ItemID typeId, Function<ItemID, String> resolver) {
        if (resolver == null) return List.of();

        // Well-known types — hardcoded for bootstrap (their seeds may not be loaded yet)
        if (FrameBody.TYPE_ID.equals(typeId)) {
            return List.of(
                    new FieldLabel("predicate", null),
                    new FieldLabel("bindings", Binding.IID),
                    new FieldLabel("config", Binding.IID));
        }
        if (Binding.IID.equals(typeId)) {
            return List.of(
                    new FieldLabel("role", null),
                    new FieldLabel("qualifiers", null),
                    new FieldLabel("target", null),
                    new FieldLabel("identity", null),
                    new FieldLabel("index", null));
        }

        // Generic: resolve from seed's EXPECTS frames
        // (Future: librarian.get(typeId) → read EXPECTS → extract TOPIC targets in order)
        return List.of();
    }

    private static ItemID inferType(Canonical canonical) {
        if (canonical instanceof FrameBody) return FrameBody.TYPE_ID;
        return null;
    }

    // ==================================================================================
    // Core rendering
    // ==================================================================================

    private static Node renderValue(CBORObject cbor, Function<ItemID, String> resolver,
                                     List<FieldLabel> schema, int depth) {
        if (cbor == null || cbor.isNull()) return Text.of("null").classes("muted");
        if (cbor.isUndefined()) return Text.of("undefined").classes("muted");

        if (cbor.isTagged()) return renderTagged(cbor, resolver, depth);

        CBORType type = cbor.getType();
        return switch (type) {
            case Map -> renderMap(cbor, resolver, depth);
            case Array -> renderArray(cbor, resolver, schema, depth);
            case TextString -> Text.of("\"" + truncate(cbor.AsString(), 200) + "\"").classes("literal");
            case Integer -> Text.of(cbor.ToObject(Long.class).toString()).classes("number");
            case Boolean -> Text.of(cbor.AsBoolean() ? "true" : "false").classes("keyword");
            case ByteString -> renderBytes(cbor.GetByteString(), resolver);
            case FloatingPoint -> Text.of(cbor.AsDouble() + "").classes("number");
            default -> Text.of(cbor.toString()).classes("muted");
        };
    }

    private static Node renderMap(CBORObject cbor, Function<ItemID, String> resolver, int depth) {
        Container container = Container.vertical().gap("0.25em");
        for (CBORObject key : cbor.getKeys()) {
            String label;
            if (key.getType() == CBORType.TextString) {
                label = key.AsString();
            } else if (key.getType() == CBORType.ByteString) {
                String resolved = tryResolveBytes(key.GetByteString(), resolver);
                label = resolved != null ? resolved : "0x" + hexPreview(key.GetByteString(), 6);
            } else {
                label = key.toString();
            }
            Container row = Container.horizontal().gap("0.5em");
            row.add(Text.of(label + ":").fontWeight("bold").classes("field-name"));
            row.add(renderValue(cbor.get(key), resolver, List.of(), depth + 1));
            container.add(row);
        }
        if (container.children().isEmpty()) return Text.of("{}").classes("muted");
        return container;
    }

    private static Node renderArray(CBORObject cbor, Function<ItemID, String> resolver,
                                     List<FieldLabel> schema, int depth) {
        if (cbor.size() == 0) return Text.of("[]").classes("muted");

        // Detect Literal pattern: [typeIID_bytes, payload_bytes] — render as typed value
        if (schema.isEmpty() && cbor.size() == 2
                && cbor.get(0).getType() == CBORType.ByteString
                && cbor.get(1).getType() == CBORType.ByteString) {
            Node typed = tryRenderLiteral(cbor, resolver);
            if (typed != null) return typed;
        }

        Container container = Container.vertical().gap("0.25em");
        for (int i = 0; i < cbor.size(); i++) {
            // Use schema field name if available, fall back to index
            String label;
            List<FieldLabel> childSchema = List.of();
            if (i < schema.size()) {
                label = schema.get(i).name();
                if (schema.get(i).typeId() != null) {
                    childSchema = resolveSchema(schema.get(i).typeId(), resolver);
                }
            } else {
                label = "[" + i + "]";
            }

            CBORObject element = cbor.get(i);

            // Array of typed elements (e.g., bindings) — each item gets its own block
            if (i < schema.size() && schema.get(i).typeId() != null
                    && element.getType() == CBORType.Array && !childSchema.isEmpty()) {
                Container section = Container.vertical().gap("0.1em");
                section.add(Text.of(label + ":").fontWeight("bold").classes("field-name"));
                Container items = Container.vertical().gap("0.5em").padding("0 0 0 1.5em");
                for (int j = 0; j < element.size(); j++) {
                    // Each binding/item in its own indented block
                    Container itemBlock = Container.vertical().gap("0.1em")
                            .padding("0.25em 0 0.25em 0");
                    itemBlock.add(renderValue(element.get(j), resolver, childSchema, depth + 2));
                    items.add(itemBlock);
                }
                section.add(items);
                container.add(section);
            } else {
                // Simple field — label and value on one line
                Container row = Container.horizontal().gap("0.5em");
                row.add(Text.of(label + ":").fontWeight("bold").classes("field-name"));

                // Binding "target" field: try Literal rendering (2-element array [typeId, payload])
                if ("target".equals(label) && element.getType() == CBORType.Array && element.size() == 2
                        && element.get(0).getType() == CBORType.ByteString) {
                    Node litNode = tryRenderLiteral(element, resolver);
                    row.add(litNode != null ? litNode : renderValue(element, resolver, List.of(), depth + 1));
                } else {
                    row.add(renderValue(element, resolver, List.of(), depth + 1));
                }
                container.add(row);
            }
        }
        return container;
    }

    // ==================================================================================
    // Tagged values — CG-CBOR semantic tags
    // ==================================================================================

    private static Node renderTagged(CBORObject cbor, Function<ItemID, String> resolver, int depth) {
        int tag = cbor.getMostOuterTag().ToInt32Checked();
        CBORObject inner = cbor.UntagOne();

        return switch (tag) {
            case Canonical.CgTag.REF -> renderRef(inner, resolver);
            case Canonical.CgTag.VALUE -> renderTypedValue(inner, resolver, depth);
            case Canonical.CgTag.SIG -> renderLabeled("sig", inner, resolver, depth);
            case Canonical.CgTag.QTY -> renderQuantity(inner, resolver);
            default -> renderLabeled("tag(" + tag + ")", inner, resolver, depth);
        };
    }

    private static Node renderRef(CBORObject inner, Function<ItemID, String> resolver) {
        if (inner.getType() == CBORType.ByteString) {
            return renderBytes(inner.GetByteString(), resolver);
        }
        return renderLabeled("ref", inner, resolver, 0);
    }

    private static Node renderTypedValue(CBORObject inner, Function<ItemID, String> resolver, int depth) {
        if (inner.getType() == CBORType.Array && inner.size() == 2) {
            CBORObject typeObj = inner.get(0);
            CBORObject value = inner.get(1);

            // Resolve the type IID
            ItemID typeId = null;
            String typeName = null;
            if (typeObj.getType() == CBORType.ByteString) {
                try { typeId = new ItemID(typeObj.GetByteString()); } catch (Exception ignored) {}
                typeName = tryResolveBytes(typeObj.GetByteString(), resolver);
            }
            if (typeName == null) typeName = "value";

            // Text values: decode bytes as UTF-8 string
            if (typeId != null && Literal.TYPE_TEXT.equals(typeId)
                    && value.getType() == CBORType.ByteString) {
                String text = new String(value.GetByteString(), java.nio.charset.StandardCharsets.UTF_8);
                return Text.of("\"" + truncate(text, 200) + "\"").classes("literal");
            }

            // Boolean values: show true/false
            if (typeId != null && Literal.TYPE_BOOLEAN.equals(typeId)) {
                return renderValue(value, resolver, List.of(), depth + 1);
            }

            // Instant values: format as human-readable date/time
            if (typeId != null && Literal.TYPE_INSTANT.equals(typeId)
                    && value.getType() == CBORType.Integer) {
                try {
                    java.time.Instant instant = java.time.Instant.ofEpochMilli(value.AsInt64Value());
                    String formatted = java.time.ZonedDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd h:mm:ss a"));
                    return Text.of(formatted).classes("literal");
                } catch (Exception ignored) {}
            }

            Container row = Container.horizontal().gap("0.25em");
            row.add(Text.of(typeName + ":").classes("muted"));
            row.add(renderValue(value, resolver, List.of(), depth + 1));
            return row;
        }
        return renderLabeled("value", inner, resolver, depth);
    }

    private static Node renderQuantity(CBORObject inner, Function<ItemID, String> resolver) {
        if (inner.getType() == CBORType.Array && inner.size() == 2) {
            String magnitude = inner.get(0).toString();
            String unit = null;
            CBORObject unitObj = inner.get(1);
            if (unitObj.getType() == CBORType.ByteString) {
                unit = tryResolveBytes(unitObj.GetByteString(), resolver);
            }
            return Text.of(magnitude + (unit != null ? " " + unit : "")).classes("number");
        }
        return renderLabeled("qty", inner, resolver, 0);
    }

    private static Node renderLabeled(String label, CBORObject inner,
                                       Function<ItemID, String> resolver, int depth) {
        Container row = Container.horizontal().gap("0.25em");
        row.add(Text.of(label + ":").classes("muted"));
        row.add(renderValue(inner, resolver, List.of(), depth + 1));
        return row;
    }

    // ==================================================================================
    // Literal detection — bare [typeIID, payload] arrays (no Tag 7)
    // ==================================================================================

    /**
     * Try to render a 2-element array as a Literal [typeIID, payload].
     * Returns null if it doesn't match the pattern.
     */
    private static Node tryRenderLiteral(CBORObject cbor, Function<ItemID, String> resolver) {
        try {
            byte[] typeBytes = cbor.get(0).GetByteString();
            ItemID typeId = new ItemID(typeBytes);
            CBORObject payloadElement = cbor.get(1);

            // Decode the payload — it may be CBOR-encoded bytes or a direct CBOR value
            CBORObject decoded;
            if (payloadElement.getType() == CBORType.ByteString) {
                decoded = CBORObject.DecodeFromBytes(payloadElement.GetByteString());
            } else {
                decoded = payloadElement;
            }

            // Text
            if (Literal.TYPE_TEXT.equals(typeId) && decoded.getType() == CBORType.TextString) {
                return Text.of("\"" + truncate(decoded.AsString(), 200) + "\"").classes("literal");
            }

            // Boolean
            if (Literal.TYPE_BOOLEAN.equals(typeId)) {
                return Text.of(decoded.AsBoolean() ? "true" : "false").classes("keyword");
            }

            // Integer
            if (Literal.TYPE_INTEGER.equals(typeId)) {
                return Text.of(decoded.toString()).classes("number");
            }

            // Instant — format as human-readable date/time
            if (Literal.TYPE_INSTANT.equals(typeId) && decoded.getType() == CBORType.Integer) {
                java.time.Instant instant = java.time.Instant.ofEpochMilli(decoded.AsInt64Value());
                String formatted = java.time.ZonedDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd h:mm:ss a"));
                return Text.of(formatted).classes("literal");
            }

            // Other type — show resolved type name + decoded CBOR value
            String typeName = tryResolveBytes(typeBytes, resolver);
            Container row = Container.horizontal().gap("0.25em");
            row.add(Text.of((typeName != null ? typeName : "value") + ":").classes("muted"));
            row.add(renderValue(decoded, resolver, List.of(), 0));
            return row;
        } catch (Exception ignored) {}
        return null;
    }

    // ==================================================================================
    // Byte string / ItemID resolution
    // ==================================================================================

    private static Node renderBytes(byte[] bytes, Function<ItemID, String> resolver) {
        String resolved = tryResolveBytes(bytes, resolver);
        if (resolved != null) {
            return Text.of(resolved).classes("reference");
        }
        return Text.of("0x" + hexPreview(bytes, 8)).classes("mono", "muted");
    }

    private static String tryResolveBytes(byte[] bytes, Function<ItemID, String> resolver) {
        if (bytes == null || bytes.length < 4 || resolver == null) return null;
        try {
            ItemID iid = new ItemID(bytes);
            String display = resolver.apply(iid);
            if (display != null) return display;
            return iid.fullDisplay();
        } catch (Exception e) {
            return null;
        }
    }

    private static String hexPreview(byte[] bytes, int maxBytes) {
        StringBuilder hex = new StringBuilder();
        int show = Math.min(bytes.length, maxBytes);
        for (int i = 0; i < show; i++) hex.append(String.format("%02x", bytes[i]));
        if (bytes.length > show) hex.append("\u2026");
        return hex.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 1) + "\u2026" : s;
    }
}
