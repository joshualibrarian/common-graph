package dev.everydaythings.graph.library;

import dev.everydaythings.graph.canonical.DatumWalker;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.id.HashID;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.id.SchemaRef;

import java.util.ArrayList;
import java.util.List;

/**
 * Schema validation: given a candidate body and its archetype's manifest,
 * verify the candidate satisfies the archetype's EXPECTS declarations.
 *
 * <p>The archetype manifest carries {@code !}-roled bindings (SchemaRef in
 * role position) that name expected bindings on instances.  For each such
 * declaration, this walker:
 * <ol>
 *   <li>Confirms the candidate has a binding with the corresponding
 *       (literal-roled) IID.</li>
 *   <li>(Future) Matches the candidate's target against the schema's target
 *       pattern — e.g., {@code BETWEEN { 0, 255 }} applied to the candidate's
 *       R value.  Requires the matcher orchestrator; not yet wired.</li>
 * </ol>
 *
 * <p>Presence-only validation is what's wired in this first cut.  The walker
 * extends {@link DatumWalker} for traversal mechanics; the override of
 * {@link #walkBinding} captures the binding-level context.
 *
 * <p>Usage:
 * <pre>{@code
 *   ValidationResult r = SchemaWalker.validate(candidateBody, archetypeManifestBody);
 *   if (!r.isValid()) { ... }
 * }</pre>
 */
public final class SchemaWalker extends DatumWalker {

    private final Body candidate;
    private final List<ValidationResult.Issue> issues = new ArrayList<>();

    private SchemaWalker(Body candidate) {
        this.candidate = candidate;
    }

    /**
     * Validate {@code candidate} against the archetype manifest body's
     * schema declarations.
     */
    public static ValidationResult validate(Body candidate, Body archetypeManifest) {
        SchemaWalker walker = new SchemaWalker(candidate);
        walker.walk(archetypeManifest);
        return new ValidationResult(List.copyOf(walker.issues));
    }

    @Override
    protected void walkBinding(Binding schemaBinding) {
        // Only !-roled bindings on the schema body are expectation declarations.
        // Literal-roled bindings (ITEM_ID, ENDORSES, etc.) are the archetype's
        // own structural metadata, not constraints on instances.
        if (!(schemaBinding.role() instanceof SchemaRef sr)) {
            return;
        }

        // The schema role's underlying IID is the role we expect on the candidate.
        ItemRef expectedRole = sr.iid();
        // Bindings only — Opaque entries in the candidate are invisible to
        // role-presence checks (we can't see the hidden role).  Conservative
        // outcome: if the candidate has only an Opaque where the expected
        // binding should be, validation reports the binding missing.  Future:
        // surface "validation incomplete due to opacity" as a distinct
        // outcome when callers can act on it.
        boolean present = candidate.bindings().stream()
                .anyMatch(b -> isSameRole(b.role(), expectedRole));
        if (!present) {
            issues.add(new ValidationResult.Issue(
                    ValidationResult.Issue.Kind.MISSING_BINDING,
                    "Candidate missing binding with role " + expectedRole.encodeText()));
        }
        // Future: check schemaBinding.target() as a pattern against the
        // candidate's matching binding's target.  Requires matcher orchestrator.
    }

    /** Whether the candidate's role refers to the same IID as the schema-declared role. */
    private static boolean isSameRole(HashID candidateRole, ItemRef expected) {
        if (candidateRole instanceof ItemRef ir) {
            return expected.equals(ir);
        }
        // SchemaRef or TypeRef candidate roles wouldn't appear on an instance
        // body — they're for archetype/query bodies.  If they do appear,
        // compare by underlying IID bytes.
        return java.util.Arrays.equals(candidateRole.multihash(), expected.multihash());
    }
}
