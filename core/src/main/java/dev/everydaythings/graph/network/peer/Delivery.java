package dev.everydaythings.graph.network.peer;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Canonical.CgTag;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.frame.FrameBody;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.network.ProtocolMessage;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * DELIVERY — "Here's something" [Tag 12].
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
        long requestId,
        List<Payload> payloads
) implements PeerMessage, Canonical {

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
    public int tag() {
        return CgTag.DELIVERY;
    }

    @Override
    public CBORObject toCbor() {
        return toCborTree(Scope.RECORD);
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

    public static Delivery fromCbor(CBORObject obj) {
        long requestId = obj.get("id").AsInt64Value();
        List<Payload> payloads = obj.get("payloads").getValues().stream()
                .map(Payload::fromCbor)
                .toList();
        return new Delivery(requestId, payloads);
    }

    /**
     * What we're delivering. Subtypes distinguished by field presence.
     */
    public sealed interface Payload {

        CBORObject toCbor(Canonical.Scope scope);

        static Payload fromCbor(CBORObject obj) {
            // Field-presence discrimination (no "kind" string):
            // - has "manifest" → Item
            // - has "cid" + "data" → Content
            // - has "relations" → Relations
            // - has "notfound" → NotFound
            // - has "next" → Envelope
            if (obj.ContainsKey("manifest")) {
                return Item.fromCbor(obj);
            } else if (obj.ContainsKey("data")) {
                return Content.fromCbor(obj);
            } else if (obj.ContainsKey("relations")) {
                return Relations.fromCbor(obj);
            } else if (obj.ContainsKey("notfound")) {
                return NotFound.fromCbor(obj);
            } else if (obj.ContainsKey("next")) {
                return Envelope.fromCbor(obj);
            }
            throw new IllegalArgumentException("Unknown payload format");
        }

        /**
         * An item (manifest).
         */
        record Item(Manifest manifest) implements Payload {
            @Override
            public CBORObject toCbor(Canonical.Scope scope) {
                CBORObject obj = CBORObject.NewMap();
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
                obj.set("notfound", CBORObject.FromByteArray(iid.encodeBinary()));
                return obj;
            }

            static NotFound fromCbor(CBORObject obj) {
                ItemID iid = new ItemID(obj.get("notfound").GetByteString());
                return new NotFound(iid);
            }
        }

        /**
         * An envelope to be forwarded to another librarian.
         *
         * @param nextHop The librarian IID that should receive this
         * @param origin  The librarian IID that originally sent this
         * @param inner   Raw CBOR bytes of the wrapped ProtocolMessage
         */
        record Envelope(ItemID nextHop, ItemID origin, byte[] inner) implements Payload {
            @Override
            public CBORObject toCbor(Canonical.Scope scope) {
                CBORObject obj = CBORObject.NewMap();
                obj.set("next", CBORObject.FromByteArray(nextHop.encodeBinary()));
                obj.set("origin", CBORObject.FromByteArray(origin.encodeBinary()));
                obj.set("inner", CBORObject.FromByteArray(inner));
                return obj;
            }

            static Envelope fromCbor(CBORObject obj) {
                ItemID nextHop = new ItemID(obj.get("next").GetByteString());
                ItemID origin = new ItemID(obj.get("origin").GetByteString());
                byte[] inner = obj.get("inner").GetByteString();
                return new Envelope(nextHop, origin, inner);
            }
        }
    }
}
