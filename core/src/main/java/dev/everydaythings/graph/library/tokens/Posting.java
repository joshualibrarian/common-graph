package dev.everydaythings.graph.library.tokens;

import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.value.Decimal;

import java.util.Objects;
import java.util.Set;

/**
 * A token-dictionary lookup result — a single hit identifying a token, what it
 * refers to, the kind of source that produced it, and a ranking weight.
 *
 * <p>Postings are pure data, returned by {@link TokenDictionary#lookup} and
 * upstream from the librarian's token-lookup interface. All fields are derived
 * from the source datum at lookup time; the index storage itself is minimal
 * (just CID + compound-key + weight bytes).
 *
 * <p>Field semantics:
 * <ul>
 *   <li><b>token</b> — the normalized surface form (post-normalization, lowercase, NFC).</li>
 *   <li><b>target</b> — the item this token refers to. For a LEXEME, the sememe
 *       it names; for a TITLE, the titled item; for a NAME, the named entity.
 *       May be {@code null} when no target role is present on the source.</li>
 *   <li><b>predicate</b> — the kind of source that produced this posting:
 *       LEXEME, TITLE, NAME, SYMBOL, etc. Equals the head of the source datum.</li>
 *   <li><b>scope</b> — the language scope (e.g., English). {@code null} for universal
 *       postings (symbols, language-neutral identifiers).</li>
 *   <li><b>features</b> — additional qualifier sememes (POS, lemma, plural, format).
 *       Interpretation depends on the predicate.</li>
 *   <li><b>weight</b> — ranking score; higher is more relevant.</li>
 *   <li><b>source</b> — CID of the datum (body or record) that produced this
 *       posting. Lineage / dedup / cache invalidation handle.</li>
 * </ul>
 *
 * <p>Stored as {@link Decimal} rather than {@code float} for the weight to comply
 * with CG-CBOR's prohibition on IEEE 754 floats, should the result ever be
 * encoded canonically.
 */
public record Posting(
        String token,
        ItemID target,
        ItemID predicate,
        ItemID scope,
        Set<ItemID> features,
        Decimal weight,
        ContentID source) {

    public Posting {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(predicate, "predicate");
        Objects.requireNonNull(features, "features");
        Objects.requireNonNull(weight, "weight");
        Objects.requireNonNull(source, "source");
        // target and scope may be null
        features = Set.copyOf(features);
    }
}
