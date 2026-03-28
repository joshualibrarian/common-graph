package dev.everydaythings.graph.item;

import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.Frame;
import dev.everydaythings.graph.frame.FrameBody;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.runtime.Librarian;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves dynamic handle labels for items by reading EXPECTS frames from
 * the item's type and disambiguating among visible siblings.
 *
 * <p>The algorithm:
 * <ol>
 *   <li>Get the item's type via {@code @Implements}</li>
 *   <li>Get the type item from the librarian cache</li>
 *   <li>Read EXPECTS frames on the type — ordered by declaration (= salience)</li>
 *   <li>For each expectation, find matching frames on the instance</li>
 *   <li>Extract non-self binding values that distinguish this item from siblings</li>
 *   <li>Return the minimal label that disambiguates</li>
 * </ol>
 *
 * <p>When no siblings are provided, returns the first available feature.
 * When siblings are provided, returns the minimal set that makes the item unique.
 *
 * @see CoreVocabulary.Expects
 */
public final class HandleResolver {

    private HandleResolver() {}

    /**
     * Resolve a distinguishing label for an item, given its visible siblings
     * of the same type.
     *
     * @param item     the item to label
     * @param siblings other items of the same type visible in the same context
     *                 (may be empty — produces a best-effort label)
     * @return a human-readable distinguishing label, or null if nothing useful found
     */
    public static String resolve(Item item, Collection<Item> siblings) {
        Librarian lib = item.itemLibrarian();
        if (lib == null) return null;

        // Get the type's EXPECTS frames (salience-ordered)
        List<Expectation> expectations = expectationsFor(item, lib);

        List<Feature> myFeatures;
        if (!expectations.isEmpty()) {
            // Type-guided: walk EXPECTS frames in salience order
            myFeatures = extractFeatures(item, expectations, lib);
        } else {
            // Universal fallback: scan item's own frames by role priority
            myFeatures = extractFallbackFeatures(item, lib);
        }

        if (myFeatures.isEmpty()) return null;

        // No siblings → return the first feature as label
        if (siblings == null || siblings.isEmpty()) {
            return formatFeatures(myFeatures, 1);
        }

        // Disambiguate: find the minimal feature set that makes this item unique
        return disambiguate(item, myFeatures, siblings, expectations, lib);
    }

    /**
     * Resolve a label for an item without sibling context.
     */
    public static String resolve(Item item) {
        return resolve(item, List.of());
    }

    // ==================================================================================
    // Expectation reading
    // ==================================================================================

    /**
     * An EXPECTS frame parsed into its components.
     */
    record Expectation(ItemID expectedPredicate, List<ItemID> constraints) {}

    /**
     * Read EXPECTS frames from an item's type, in declaration order (= salience).
     */
    static List<Expectation> expectationsFor(Item item, Librarian lib) {
        // Find the type IID
        ItemID typeId = typeIdOf(item);
        if (typeId == null) return List.of();

        // Get the type item from cache
        Optional<Item> typeItem = lib.get(typeId);
        if (typeItem.isEmpty()) return List.of();

        // Extract EXPECTS frames in iteration order
        List<Expectation> result = new ArrayList<>();
        for (Frame frame : typeItem.get().frames()) {
            if (frame.body() == null) continue;
            if (!CoreVocabulary.Expects.IID.equals(frame.body().predicate())) continue;

            Binding topicBinding = frame.body().getBindingByRole(ThematicRole.Topic.IID);
            if (topicBinding == null) continue;

            ItemID expectedPred = topicBinding.targetId();
            if (expectedPred == null) continue;

            // Qualifiers beyond the predicate itself are constraints
            // e.g., TOPIC:[PLAYER, WHITE] → qualifiers = [PLAYER, WHITE], constraints = [WHITE]
            List<ItemID> constraints = new ArrayList<>();
            List<ItemID> qualifierIds = topicBinding.key();
            // key() returns [role, qual1, qual2, ...] — skip role, skip predicate
            for (int i = 1; i < qualifierIds.size(); i++) {
                ItemID qid = qualifierIds.get(i);
                if (!qid.equals(expectedPred)) {
                    constraints.add(qid);
                }
            }

            result.add(new Expectation(expectedPred, constraints));
        }
        return result;
    }

    // ==================================================================================
    // Feature extraction
    // ==================================================================================

    /**
     * A distinguishing feature extracted from an item's frames.
     */
    record Feature(ItemID predicate, String displayValue) {}

    /**
     * Extract display features from an item by matching its frames against expectations.
     */
    static List<Feature> extractFeatures(Item item, List<Expectation> expectations, Librarian lib) {
        List<Feature> features = new ArrayList<>();

        for (Expectation exp : expectations) {
            // Find matching frame(s) on the instance
            for (Frame frame : item.frames()) {
                if (frame.body() == null) continue;
                if (!exp.expectedPredicate().equals(frame.body().predicate())) continue;

                // If there are constraints, verify the frame matches
                if (!exp.constraints().isEmpty() && !matchesConstraints(frame.body(), exp.constraints())) {
                    continue;
                }

                // Extract the first non-self binding value as a display feature
                String value = extractDisplayValue(frame.body(), item.iid(), lib);
                if (value != null) {
                    features.add(new Feature(exp.expectedPredicate(), value));
                    break; // one feature per expectation
                }
            }
        }
        return features;
    }

    /**
     * Check if a frame body's bindings contain ALL the constraint IIDs
     * (as qualifier values or target values).
     */
    private static boolean matchesConstraints(FrameBody body, List<ItemID> constraints) {
        for (ItemID constraint : constraints) {
            boolean found = false;
            for (Binding b : body.frameBindings()) {
                // Check qualifiers
                for (FrameKey.FrameToken q : b.qualifiers()) {
                    if (q instanceof FrameKey.Sememe s && s.id().equals(constraint)) {
                        found = true;
                        break;
                    }
                }
                if (found) break;
                // Check target
                if (constraint.equals(b.targetId())) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    /**
     * Extract a human-readable display value from a frame body.
     *
     * <p>Skips the home binding (self-reference) and returns the first
     * binding that has a resolvable display value.
     */
    private static String extractDisplayValue(FrameBody body, ItemID selfId, Librarian lib) {
        for (Binding b : body.frameBindings()) {
            // Skip self-references
            ItemID tid = b.targetId();
            if (tid != null && tid.equals(selfId)) continue;

            String value = extractBindingDisplayValue(b, lib);
            if (value != null) return value;
        }
        return null;
    }

    // ==================================================================================
    // Disambiguation
    // ==================================================================================

    /**
     * Find the minimal feature set that uniquely identifies this item among siblings.
     */
    private static String disambiguate(Item item, List<Feature> myFeatures,
                                        Collection<Item> siblings,
                                        List<Expectation> expectations, Librarian lib) {
        // Build feature lists for all siblings using the same extraction strategy
        Map<ItemID, List<Feature>> siblingFeatures = new LinkedHashMap<>();
        for (Item sib : siblings) {
            if (sib.iid().equals(item.iid())) continue; // skip self
            List<Feature> sf = !expectations.isEmpty()
                    ? extractFeatures(sib, expectations, lib)
                    : extractFallbackFeatures(sib, lib);
            siblingFeatures.put(sib.iid(), sf);
        }

        if (siblingFeatures.isEmpty()) {
            return formatFeatures(myFeatures, 1);
        }

        // Walk features in salience order, adding until unique
        for (int depth = 1; depth <= myFeatures.size(); depth++) {
            String myLabel = formatFeatures(myFeatures, depth);
            boolean unique = true;

            for (List<Feature> otherFeatures : siblingFeatures.values()) {
                String otherLabel = formatFeatures(otherFeatures, depth);
                if (myLabel.equals(otherLabel)) {
                    unique = false;
                    break;
                }
            }

            if (unique) return myLabel;
        }

        // All features match — fall back to full feature list
        return formatFeatures(myFeatures, myFeatures.size());
    }

    /**
     * Format the first N features as a display string.
     */
    private static String formatFeatures(List<Feature> features, int count) {
        if (features.isEmpty()) return null;
        int n = Math.min(count, features.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(", ");
            sb.append(features.get(i).displayValue());
        }
        return sb.toString();
    }

    // ==================================================================================
    // Universal fallback — no EXPECTS on type
    // ==================================================================================

    /**
     * Roles to scan in priority order when no EXPECTS frames exist.
     * People > names > time > location > status.
     */
    private static final ItemID[] FALLBACK_ROLE_PRIORITY = {
            ThematicRole.Name.IID,
            ThematicRole.Agent.IID,
            ThematicRole.Recipient.IID,
            ThematicRole.Partner.IID,
            ThematicRole.Time.IID,
            ThematicRole.Location.IID,
            ThematicRole.Result.IID,
            ThematicRole.Topic.IID,
            ThematicRole.Referent.IID,
            ThematicRole.Goal.IID,
    };

    /**
     * Predicates to skip in fallback — these are metadata/vocabulary frames,
     * not instance-identifying content.
     */
    private static final java.util.Set<ItemID> SKIP_PREDICATES = java.util.Set.of(
            CoreVocabulary.Expects.IID,
            CoreVocabulary.ImplementedBy.IID,
            ItemID.fromString(CoreVocabulary.Lexeme.KEY),
            ItemID.fromString("cg.predicate:gloss")
    );

    /**
     * Extract features from an item's frames using universal role priority.
     *
     * <p>Scans all frames on the item, skipping metadata predicates.
     * For each priority role, finds the first non-self binding value.
     */
    static List<Feature> extractFallbackFeatures(Item item, Librarian lib) {
        List<Feature> features = new ArrayList<>();
        ItemID selfId = item.iid();

        for (ItemID role : FALLBACK_ROLE_PRIORITY) {
            for (Frame frame : item.frames()) {
                if (frame.body() == null) continue;
                if (SKIP_PREDICATES.contains(frame.body().predicate())) continue;

                // Look for this role in the frame's bindings
                Binding binding = frame.body().getBindingByRole(role);
                if (binding == null) continue;

                // Skip self-references
                ItemID tid = binding.targetId();
                if (tid != null && tid.equals(selfId)) continue;

                // Extract display value
                String value = extractBindingDisplayValue(binding, lib);
                if (value != null) {
                    features.add(new Feature(frame.body().predicate(), value));
                    break; // one feature per role
                }
            }
            if (features.size() >= 3) break; // enough for disambiguation
        }
        return features;
    }

    /**
     * Extract a display value from a single binding.
     */
    private static String extractBindingDisplayValue(Binding binding, Librarian lib) {
        ItemID tid = binding.targetId();
        if (tid != null) {
            Optional<Item> resolved = lib.get(tid);
            if (resolved.isPresent()) {
                return resolved.get().displayToken();
            }
            return tid.displayAtWidth(12);
        }

        if (binding.target() instanceof Literal lit) {
            if (Literal.TYPE_TEXT.equals(lit.valueType())) {
                try {
                    String text = lit.asText();
                    if (text != null && !text.isBlank()) {
                        return text.length() > 30
                                ? text.substring(0, 27) + "..."
                                : text;
                    }
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    // ==================================================================================
    // Type resolution
    // ==================================================================================

    /**
     * Get the type IID for an item — from {@code @Implements} annotation.
     */
    static ItemID typeIdOf(Item item) {
        try {
            return Item.idOf(item.getClass());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
