package dev.everydaythings.graph.network.message;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.component.FrameBody;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * DELIVERY - "Here's something"
 *
 * <p>The universal delivery primitive. Covers:
 * <ul>
 *   <li>Items (manifests)</li>
 *   <li>Content (raw bytes)</li>
 *   <li>Relations</li>
 *   <li>Handshake ("here's my Librarian Item" / "here's my Principal")</li>
 * </ul>
 *
 * <p>Can be sent:
 * <ul>
 *   <li>In response to a Request (with matching requestId)</li>
 *   <li>Unsolicited (requestId = 0) - for handshake, gossip, push updates</li>
 * </ul>
 */
public record Delivery(
        long requestId,           // 0 = unsolicited, otherwise correlates to Request
        List<Payload> payloads
) implements ProtocolMessage, Canonical {

    public static final String TYPE = "delivery";

    public Delivery {
        Objects.requireNonNull(payloads, "payloads");
    }

    /**
     * Deliver an item (unsolicited - e.g., handshake).
     */
    public static Delivery item(Manifest manifest) {
        return new Delivery(0, List.of(new Payload.Item(manifest)));
    }

    /**
     * Deliver an item in response to a request.
     */
    public static Delivery item(long requestId, Manifest manifest) {
        return new Delivery(requestId, List.of(new Payload.Item(manifest)));
    }

    /**
     * Deliver multiple items (e.g., "here's my Librarian AND my Principal").
     */
    public static Delivery items(Manifest... manifests) {
        List<Payload> payloads = Arrays.stream(manifests)
                .map(Payload.Item::new)
                .map(p -> (Payload) p)
                .toList();
        return new Delivery(0, payloads);
    }

    /**
     * Deliver content in response to a request.
     */
    public static Delivery content(long requestId, ContentID cid, byte[] data) {
        return new Delivery(requestId, List.of(new Payload.Content(cid, data)));
    }

    /**
     * Deliver frame bodies (response or push update).
     */
    public static Delivery relations(long requestId, List<FrameBody> bodies) {
        return new Delivery(requestId, List.of(new Payload.Relations(bodies)));
    }

    /**
     * Indicate "not found" for a request.
     */
    public static Delivery notFound(long requestId, ItemID iid) {
        return new Delivery(requestId, List.of(new Payload.NotFound(iid)));
    }

    /**
     * Wrap a protocol message in an envelope for relay forwarding.
     *
     * @param nextHop     The librarian that should receive this
     * @param origin      The librarian that originally sent this
     * @param inner       The protocol message to relay
     */
    public static Delivery envelope(ItemID nextHop, ItemID origin, ProtocolMessage inner) {
        return new Delivery(0, List.of(new Payload.Envelope(nextHop, origin, inner.encode())));
    }

    @Override
    public CBORObject toCborTree(Scope scope) {
        CBORObject obj = CBORObject.NewMap();
        obj.set("id", CBORObject.FromInt64(requestId));
        CBORObject arr = CBORObject.NewArray();
        for (Payload p : payloads) {
            arr.Add(p.toCbor(scope));
        }
        obj.set("payloads", arr);
        return obj;
    }

    public byte[] encode() {
        CBORObject envelope = CBORObject.NewMap();
        envelope.set("type", CBORObject.FromString(TYPE));
        envelope.set("payload", toCborTree(Scope.RECORD));
        return envelope.EncodeToBytes();
    }

    public static Delivery fromCbor(CBORObject obj) {
        long requestId = obj.get("id").AsInt64Value();
        List<Payload> payloads = obj.get("payloads").getValues().stream()
                .map(Payload::fromCbor)
                .toList();
        return new Delivery(requestId, payloads);
    }

    /**
     * What we're delivering.
     */
    public sealed interface Payload {

        CBORObject toCbor(Canonical.Scope scope);

        static Payload fromCbor(CBORObject obj) {
            String kind = obj.get("kind").AsString();
            return switch (kind) {
                case "item" -> Item.fromCbor(obj);
                case "content" -> Content.fromCbor(obj);
                case "relations" -> Relations.fromCbor(obj);
                case "notfound" -> NotFound.fromCbor(obj);
                case "envelope" -> Envelope.fromCbor(obj);
                default -> throw new IllegalArgumentException("Unknown payload kind: " + kind);
            };
        }

        /**
         * An item (manifest).
         */
        record Item(Manifest manifest) implements Payload {
            @Override
            public CBORObject toCbor(Canonical.Scope scope) {
                CBORObject obj = CBORObject.NewMap();
                obj.set("kind", CBORObject.FromString("item"));
                obj.set("manifest", manifest.toCborTree(scope));
                return obj;
            }

            static Item fromCbor(CBORObject obj) {
                Manifest manifest = Canonical.fromCborTree(obj.get("manifest"), Manifest.class, Canonical.Scope.RECORD);
                return new Item(manifest);
            }
        }

        /**
         * Raw content bytes.
         */
        record Content(ContentID cid, byte[] data) implements Payload {
            @Override
            public CBORObject toCbor(Canonical.Scope scope) {
                CBORObject obj = CBORObject.NewMap();
                obj.set("kind", CBORObject.FromString("content"));
                obj.set("cid", CBORObject.FromByteArray(cid.encodeBinary()));
                obj.set("data", CBORObject.FromByteArray(data));
                return obj;
            }

            static Content fromCbor(CBORObject obj) {
                ContentID cid = new ContentID(obj.get("cid").GetByteString());
                byte[] data = obj.get("data").GetByteString();
                return new Content(cid, data);
            }
        }

        /**
         * Frame bodies.
         */
        record Relations(List<FrameBody> bodies) implements Payload {
            @Override
            public CBORObject toCbor(Canonical.Scope scope) {
                CBORObject obj = CBORObject.NewMap();
                obj.set("kind", CBORObject.FromString("relations"));
                CBORObject arr = CBORObject.NewArray();
                for (FrameBody body : bodies) {
                    arr.Add(body.toCborTree(scope));
                }
                obj.set("relations", arr);
                return obj;
            }

            static Relations fromCbor(CBORObject obj) {
                List<FrameBody> bodies = obj.get("relations").getValues().stream()
                        .map(node -> Canonical.fromCborTree(node, FrameBody.class, Canonical.Scope.RECORD))
                        .toList();
                return new Relations(bodies);
            }
        }

        /**
         * Not found response.
         */
        record NotFound(ItemID iid) implements Payload {
            @Override
            public CBORObject toCbor(Canonical.Scope scope) {
                CBORObject obj = CBORObject.NewMap();
                obj.set("kind", CBORObject.FromString("notfound"));
                obj.set("iid", CBORObject.FromByteArray(iid.encodeBinary()));
                return obj;
            }

            static NotFound fromCbor(CBORObject obj) {
                ItemID iid = new ItemID(obj.get("iid").GetByteString());
                return new NotFound(iid);
            }
        }

        /**
         * An envelope to be forwarded to another librarian.
         *
         * <p>The relay doesn't interpret the inner bytes — it just
         * looks up the nextHop in its active peers and forwards.
         *
         * @param nextHop The librarian IID that should receive this
         * @param origin  The librarian IID that originally sent this
         * @param inner   Raw CBOR bytes of the wrapped ProtocolMessage
         */
        record Envelope(ItemID nextHop, ItemID origin, byte[] inner) implements Payload {
            @Override
            public CBORObject toCbor(Canonical.Scope scope) {
                CBORObject obj = CBORObject.NewMap();
                obj.set("kind", CBORObject.FromString("envelope"));
                obj.set("nextHop", CBORObject.FromByteArray(nextHop.encodeBinary()));
                obj.set("origin", CBORObject.FromByteArray(origin.encodeBinary()));
                obj.set("inner", CBORObject.FromByteArray(inner));
                return obj;
            }

            static Envelope fromCbor(CBORObject obj) {
                ItemID nextHop = new ItemID(obj.get("nextHop").GetByteString());
                ItemID origin = new ItemID(obj.get("origin").GetByteString());
                byte[] inner = obj.get("inner").GetByteString();
                return new Envelope(nextHop, origin, inner);
            }
        }
    }
}
