package dev.everydaythings.graph.library.index;

import dev.everydaythings.graph.ref.DatumRef;
import dev.everydaythings.graph.ref.ItemRef;
import java.math.BigDecimal;

import java.util.Objects;
import java.util.Set;

/**
 * A token-index lookup result — a single hit identifying a token, what it
 * refers to, the kind of source that produced it, and a ranking weight.
 *
 * <p>Postings are pure data, returned by {@link TokenIndexStore#lookup} and
 * surfaced through the librarian's token-lookup interface. All non-source
 * fields are derived from the source datum at lookup time.
 *
 * <p>Field semantics:
 * <ul>
 *   <li><b>token</b> — the normalized surface form (post-normalization,
 *       lowercase, NFC).</li>
 *   <li><b>target</b> — the item this token refers to. For a LEXEME, the
 *       sememe it names; for a TITLE, the titled item; for a NAME, the named
 *       entity. May be {@code null}.</li>
 *   <li><b>predicate</b> — the kind of source that produced this posting:
 *       LEXEME, TITLE, NAME, SYMBOL, etc. Equals the head of the source datum.</li>
 *   <li><b>scope</b> — the language scope (e.g., English). {@code null} for
 *       universal postings (symbols, language-neutral identifiers).</li>
 *   <li><b>features</b> — additional qualifier sememes (POS, lemma, plural,
 *       format). Interpretation depends on the predicate.</li>
 *   <li><b>weight</b> — ranking score; higher is more relevant.</li>
 *   <li><b>source</b> — semantic identity (DatumRef) of the datum that
 *       produced this posting. Lineage / dedup / cache-invalidation handle.</li>
 * </ul>
 *
 * <p>Stored as {@link BigDecimal} rather than {@code float} for the weight to
 * comply with CG-CBOR's prohibition on IEEE 754 floats, should the result
 * ever be encoded canonically.
 */
public record TokenPosting(
        String token,
        ItemRef target,
        ItemRef predicate,
        ItemRef scope,
        Set<ItemRef> features,
        BigDecimal weight,
        DatumRef source) {

    public TokenPosting {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(predicate, "predicate");
        Objects.requireNonNull(features, "features");
        Objects.requireNonNull(weight, "weight");
        Objects.requireNonNull(source, "source");
        // target and scope may be null
        features = Set.copyOf(features);
    }
}
