package dev.everydaythings.graph.network.message;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.VersionID;

import java.util.List;
import java.util.Objects;

/**
 * REQUEST - "I want something"
 *
 * <p>The universal request primitive. Covers:
 * <ul>
 *   <li>Item by IID (latest version)</li>
 *   <li>Item by IID@VID (specific version)</li>
 *   <li>Content by CID</li>
 *   <li>Relations matching filters</li>
 * </ul>
 *
 * <p>Multiple targets can be requested in a single message for efficiency.
 */
public record Request(
        long requestId,           // for correlating responses
        List<Target> targets
) implements ProtocolMessage, Canonical {

    public static final String TYPE = "request";

    public Request {
        Objects.requireNonNull(targets, "targets");
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("at least one target required");
        }
    }

    /**
     * Request a single item by IID (latest version).
     */
    public static Request item(long requestId, ItemID iid) {
        return new Request(requestId, List.of(new Target.Item(iid, null)));
    }

    /**
     * Request a specific version of an item.
     */
    public static Request itemVersion(long requestId, ItemID iid, VersionID vid) {
        return new Request(requestId, List.of(new Target.Item(iid, vid)));
    }

    /**
     * Request content by CID.
     */
    public static Request content(long requestId, ContentID cid) {
        return new Request(requestId, List.of(new Target.Content(cid)));
    }

    /**
     * Request relations with filters.
     */
    public static Request relations(long requestId, ItemID subject, ItemID predicate, ItemID object) {
        return new Request(requestId, List.of(new Target.Relations(subject, predicate, object, false)));
    }

    /**
     * Subscribe to relation updates (streaming).
     */
    public static Request subscribe(long requestId, ItemID subject, ItemID predicate, ItemID object) {
        return new Request(requestId, List.of(new Target.Relations(subject, predicate, object, true)));
    }

    @Override
    public CBORObject toCborTree(Scope scope) {
        CBORObject obj = CBORObject.NewMap();
        obj.set("id", CBORObject.FromInt64(requestId));
        CBORObject arr = CBORObject.NewArray();
        for (Target t : targets) {
            arr.Add(t.toCbor());
        }
        obj.set("targets", arr);
        return obj;
    }

    public byte[] encode() {
        CBORObject envelope = CBORObject.NewMap();
        envelope.set("type", CBORObject.FromString(TYPE));
        envelope.set("payload", toCborTree(Scope.RECORD));
        return envelope.EncodeToBytes();
    }

    public static Request fromCbor(CBORObject obj) {
        long requestId = obj.get("id").AsInt64Value();
        List<Target> targets = obj.get("targets").getValues().stream()
                .map(Target::fromCbor)
                .toList();
        return new Request(requestId, targets);
    }

    /**
     * What we're requesting.
     */
    public sealed interface Target {

        CBORObject toCbor();

        static Target fromCbor(CBORObject obj) {
            String kind = obj.get("kind").AsString();
            return switch (kind) {
                case "item" -> Item.fromCbor(obj);
                case "content" -> Content.fromCbor(obj);
                case "relations" -> Relations.fromCbor(obj);
                default -> throw new IllegalArgumentException("Unknown target kind: " + kind);
            };
        }

        /**
         * Request an item (optionally at a specific version).
         */
        record Item(ItemID iid, VersionID vid) implements Target {
            @Override
            public CBORObject toCbor() {
                CBORObject obj = CBORObject.NewMap();
                obj.set("kind", CBORObject.FromString("item"));
                obj.set("iid", CBORObject.FromByteArray(iid.encodeBinary()));
                if (vid != null) {
                    obj.set("vid", CBORObject.FromByteArray(vid.encodeBinary()));
                }
                return obj;
            }

            static Item fromCbor(CBORObject obj) {
                ItemID iid = new ItemID(obj.get("iid").GetByteString());
                VersionID vid = obj.ContainsKey("vid")
                        ? new VersionID(obj.get("vid").GetByteString())
                        : null;
                return new Item(iid, vid);
            }
        }

        /**
         * Request content by CID.
         */
        record Content(ContentID cid) implements Target {
            @Override
            public CBORObject toCbor() {
                CBORObject obj = CBORObject.NewMap();
                obj.set("kind", CBORObject.FromString("content"));
                obj.set("cid", CBORObject.FromByteArray(cid.encodeBinary()));
                return obj;
            }

            static Content fromCbor(CBORObject obj) {
                ContentID cid = new ContentID(obj.get("cid").GetByteString());
                return new Content(cid);
            }
        }

        /**
         * Request relations matching filters (any null = wildcard).
         * If subscribe=true, this is a subscription that streams updates.
         */
        record Relations(ItemID subject, ItemID predicate, ItemID object, boolean subscribe) implements Target {
            @Override
            public CBORObject toCbor() {
                CBORObject obj = CBORObject.NewMap();
                obj.set("kind", CBORObject.FromString("relations"));
                if (subject != null) obj.set("s", CBORObject.FromByteArray(subject.encodeBinary()));
                if (predicate != null) obj.set("p", CBORObject.FromByteArray(predicate.encodeBinary()));
                if (object != null) obj.set("o", CBORObject.FromByteArray(object.encodeBinary()));
                obj.set("subscribe", CBORObject.FromBool(subscribe));
                return obj;
            }

            static Relations fromCbor(CBORObject obj) {
                ItemID s = obj.ContainsKey("s") ? new ItemID(obj.get("s").GetByteString()) : null;
                ItemID p = obj.ContainsKey("p") ? new ItemID(obj.get("p").GetByteString()) : null;
                ItemID o = obj.ContainsKey("o") ? new ItemID(obj.get("o").GetByteString()) : null;
                boolean subscribe = obj.get("subscribe").AsBoolean();
                return new Relations(s, p, o, subscribe);
            }
        }
    }
}
