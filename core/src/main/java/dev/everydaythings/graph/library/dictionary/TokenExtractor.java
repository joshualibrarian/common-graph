package dev.everydaythings.graph.library.dictionary;

import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.Frame;
import dev.everydaythings.graph.frame.FrameBody;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.value.address.AddressSpace;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Extracts postings from various sources for token indexing.
 *
 * <p>This utility class handles extraction of postings from:
 * <ul>
 *   <li>Frame bodies (TITLE, NAME, DESCRIPTION)</li>
 *   <li>Manifests (component handles)</li>
 *   <li>Path mounts (highest weight — curated component names)</li>
 *   <li>Addresses (email, tilde paths)</li>
 * </ul>
 */
public final class TokenExtractor {

    private TokenExtractor() {}

    /**
     * Extract postings from a Frame's NAME binding, using the compound key
     * to derive scope and features.
     *
     * <p>The mapping:
     * <ul>
     *   <li>NAME binding text → token</li>
     *   <li>THEME binding IID → target</li>
     *   <li>First key qualifier → scope (null if no qualifiers = universal)</li>
     *   <li>Remaining key qualifiers → features</li>
     * </ul>
     *
     * @param frame the Frame (must have a live body)
     * @return a posting, or null if the frame has no NAME binding
     */
    public static Posting fromFrame(Frame frame) {
        if (frame == null) return null;
        List<Posting> postings = fromBody(frame.body());
        return postings.isEmpty() ? null : postings.getFirst();
    }

    /**
     * Extract postings from ALL NAME bindings in a FrameBody.
     *
     * <p>A frame may have multiple NAME bindings with the same compound key
     * (synonyms). Each produces a separate posting. The compound binding key
     * carries scope and features: {@code NAME:[ENGLISH, VERB, LEMMA] → "create"}
     * produces a posting with scope=ENGLISH, features={VERB, LEMMA}.
     *
     * @param body the FrameBody (self-contained assertion)
     * @return list of postings (may be empty)
     */
    public static List<Posting> fromBody(FrameBody body) {
        if (body == null) return List.of();

        // THEME binding → target item
        ItemID target = body.homeId();
        if (target == null) return List.of();

        // Find ALL NAME bindings — each produces a posting
        List<Posting> result = new ArrayList<>();
        for (Binding b : body.frameBindings()) {
            if (!ThematicRole.Name.IID.equals(b.role())) continue;
            if (!(b.target() instanceof Literal lit)) continue;

            String token = extractTextFromLiteral(lit);
            if (token == null || token.isBlank()) continue;

            // Derive scope and features from this binding's qualifiers
            List<FrameKey.FrameToken> quals = b.qualifiers();
            ItemID scope = null;
            Set<ItemID> features = Set.of();

            if (!quals.isEmpty()) {
                if (quals.getFirst() instanceof FrameKey.Sememe s) scope = s.id();
                if (quals.size() > 1) {
                    Set<ItemID> featureSet = new HashSet<>();
                    for (int i = 1; i < quals.size(); i++) {
                        if (quals.get(i) instanceof FrameKey.Sememe s) featureSet.add(s.id());
                    }
                    features = Set.copyOf(featureSet);
                }
            }

            result.add(new Posting(Posting.normalize(token), scope, target, 1.0f, features));
        }
        return result;
    }

    /**
     * Extract all NAME-binding postings from an item's frames.
     *
     * @param item the item with live Frame objects
     * @return list of postings (may be empty)
     */
    public static List<Posting> fromItemFrames(Item item) {
        if (item == null || item.frames() == null) return List.of();

        List<Posting> result = new ArrayList<>();
        for (Frame frame : item.frames()) {
            if (frame.body() != null) {
                result.addAll(fromBody(frame.body()));
            }
        }
        return result;
    }

    /**
     * Extract postings from a frame body using data-driven predicate indexing.
     *
     * <p>The predicate's own definition (its Sememe) declares whether its string
     * targets should be indexed, via the {@code indexWeight} field. The caller
     * provides a resolver that looks up the weight for a given predicate IID.
     *
     * <p>The theme is used as the scope for text postings (same role as subject
     * in the old Relation API).
     *
     * @param body the frame body to extract from
     * @param predicateWeightResolver resolves predicate IID → index weight (0 = don't index)
     * @return list of postings (may be empty)
     */
    public static List<Posting> fromFrameBody(
            FrameBody body,
            Function<ItemID, Float> predicateWeightResolver) {
        if (body == null) return List.of();

        float weight = predicateWeightResolver.apply(body.predicate());
        if (weight > 0) {
            return extractTextPostingFromBody(body, body.homeId(), weight);
        }
        return List.of();
    }

    /**
     * Extract postings from a manifest.
     *
     * <p>Note: Component handles in manifests are stored as FrameKeys,
     * not as the original string names. To index component handles by their
     * human-readable names, the original strings must be provided separately
     * when components are created. This method currently returns empty.
     *
     * @param manifest the manifest to extract from
     * @return list of postings (currently empty - see note above)
     */
    public static List<Posting> fromManifest(Manifest manifest) {
        // FrameKeys are semantic addresses - we can't recover the original
        // handle strings. Handle indexing needs to happen at frame creation
        // time when the original names are available.
        return List.of();
    }

    /**
     * Index a component handle by its human-readable name.
     *
     * <p>Call this when creating components, before the handle is hashed.
     * The posting is scoped locally to the item (scope = target).
     *
     * @param itemId the item containing the component
     * @param handleName the human-readable handle name
     * @return a posting for the handle, or null if invalid input
     */
    public static Posting fromComponentHandle(ItemID itemId, String handleName) {
        if (itemId == null || handleName == null || handleName.isBlank()) {
            return null;
        }

        // Local scope: scope = target
        return Posting.local(handleName, itemId);
    }

    /**
     * Weight for path-mounted tokens — highest priority in lookups.
     *
     * <p>Mount paths represent the curated, user-facing name for a component.
     * They should always appear first in token resolution, ahead of
     * content-derived tokens (1.0), display names (0.9), and type names (0.5).
     */
    public static final float PATH_MOUNT_WEIGHT = 1.5f;

    /**
     * Extract postings from a component's path mount.
     *
     * <p>When a component is mounted at a path (e.g., "/chess"), the leaf
     * segment becomes a high-weight token scoped to the owning item. This
     * ensures that when the item is context and a user types the mount name
     * (e.g., "chess"), it resolves first — ahead of component handles and
     * other local tokens.
     *
     * <p>Postings are item-scoped (not global) because mount paths are
     * navigation within a single item. Global lookups shouldn't return
     * random items' internal mount names.
     *
     * <p>For nested paths (e.g., "/documents/notes"), the leaf segment ("notes")
     * gets the full mount weight, and intermediate segments get a lower weight.
     *
     * @param itemId the item containing the mounted component
     * @param path the mount path (e.g., "/chess", "/documents/notes")
     * @return list of postings (may be empty if path is invalid)
     */
    public static List<Posting> fromPathMount(ItemID itemId, String path) {
        if (itemId == null || path == null || path.isBlank()) {
            return List.of();
        }

        // Strip leading slash and split into segments
        String clean = path.startsWith("/") ? path.substring(1) : path;
        if (clean.isEmpty()) return List.of();

        String[] segments = clean.split("/");
        if (segments.length == 0) return List.of();

        List<Posting> postings = new java.util.ArrayList<>();

        // Leaf segment gets full mount weight, scoped to the item
        String leaf = segments[segments.length - 1];
        if (!leaf.isBlank()) {
            postings.add(new Posting(Posting.normalize(leaf), itemId, itemId, PATH_MOUNT_WEIGHT));
        }

        // Intermediate segments get lower weight (still above normal content tokens)
        for (int i = 0; i < segments.length - 1; i++) {
            if (!segments[i].isBlank()) {
                postings.add(new Posting(Posting.normalize(segments[i]), itemId, itemId, 1.1f));
            }
        }

        return postings;
    }

    /**
     * Extract postings from a parsed address.
     *
     * <p>Address scope is determined by the address type:
     * <ul>
     *   <li>Tilde addresses (~host/path) - could be domain-scoped in future</li>
     *   <li>AtDomain addresses (@domain/path) - could be domain-scoped in future</li>
     *   <li>Others - global scope</li>
     * </ul>
     *
     * @param address the parsed address to extract from
     * @param targetItem the item this address points to
     * @return list of postings
     */
    public static List<Posting> fromAddress(AddressSpace.ParsedAddress address, ItemID targetItem) {
        if (address == null || targetItem == null) return List.of();

        String token = Posting.normalize(address.canonical());
        ItemID scope = determineAddressScope(address);

        return List.of(Posting.scoped(token, scope, targetItem));
    }

    // ==================================================================================
    // HELPERS
    // ==================================================================================

    private static List<Posting> extractTextPostingFromBody(FrameBody body, ItemID theme, float weight) {
        // GOAL binding is the equivalent of Relation.object()
        BindingTarget target = body.binding(ItemID.fromString("cg.role:goal"));
        if (target instanceof Literal literal) {
            String text = extractTextFromLiteral(literal);
            if (text != null && !text.isBlank()) {
                // Frame text (titles, descriptions) are scoped to the theme item.
                // They're proper nouns / item-specific facts, not language words.
                return List.of(Posting.scoped(text, theme, theme, weight));
            }
        }
        return List.of();
    }

    private static String extractTextFromLiteral(Literal literal) {
        if (literal == null) return null;
        // Check if this is a text-type literal
        if (Literal.TYPE_TEXT.equals(literal.valueType())) {
            try {
                return literal.asText();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Determine scope for an address.
     *
     * <p>For now, addresses are globally scoped. In the future, we might
     * want to scope them to domain items (e.g., iid:domain/tilde/hostname).
     */
    private static ItemID determineAddressScope(AddressSpace.ParsedAddress address) {
        // TODO: When we have domain items for namespaces, return them here
        // For now, all addresses are globally scoped
        return null;  // global
    }

    /**
     * Extract postings from a Sememe's tokens and symbols.
     *
     * <p>Tokens are language words — scoped to the given Language Item.
     * Symbols are language-neutral — universal scope (null).
     *
     * @param sememe   the Sememe to extract from
     * @param language the Language Item IID to scope word tokens to
     * @return list of postings
     */
    public static List<Posting> fromSememe(Sememe sememe, ItemID language) {
        if (sememe == null) return List.of();

        ItemID sememeId = sememe.iid();
        List<Posting> result = new java.util.ArrayList<>();

        // Symbols → universal postings (null scope)
        List<String> symbols = sememe.symbols();
        if (symbols != null) {
            for (String symbol : symbols) {
                if (symbol != null && !symbol.isBlank()) {
                    result.add(Posting.universal(Posting.normalize(symbol), sememeId));
                }
            }
        }

        // Tokens → language-scoped postings
        List<String> tokens = sememe.tokens();
        if (tokens != null) {
            for (String token : tokens) {
                if (token != null && !token.isBlank()) {
                    result.add(Posting.scoped(Posting.normalize(token), language, sememeId, 1.0f));
                }
            }
        }

        return result;
    }
}
