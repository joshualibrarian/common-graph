package dev.everydaythings.graph.language;

/**
 * Thematic roles (theta roles) for prepositions.
 *
 * <p>When a preposition Sememe carries a thematic role, it tells the evaluator
 * what semantic function its object serves in the expression. For example,
 * "create chess <b>on</b> myItem" — the preposition "on" has role TARGET,
 * meaning its object (myItem) is where the result should go.
 *
 * <p>This is data-driven: each preposition Sememe declares its role, so the
 * evaluator doesn't hardcode preposition semantics. New prepositions with
 * new roles can be added as seed vocabulary without changing dispatch logic.
 */
public enum ThematicRole {
    /** The doer of the action (usually implicit: the signer). */
    AGENT,
    /** The entity affected or produced by the action. */
    THEME,
    /** The entity undergoing a change of state. */
    PATIENT,
    /** Where the result goes — "on", "to", "into". */
    TARGET,
    /** Where something comes from — "from". */
    SOURCE,
    /** Tool or method used — "with", "using". */
    INSTRUMENT,
    /** Where something is — "at". */
    LOCATION,
    /** Who benefits — "for". */
    RECIPIENT,
    /** Reason — "because", "due to". */
    CAUSE,
    /** Companion or participant — "between", "with (person)". */
    COMITATIVE,
    /** Designation or label — "named", "called". */
    NAME
}
