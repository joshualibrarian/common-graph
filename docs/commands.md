# base command vocabulary

Frame predicates the system runs on.  Some the user types; some the
system invokes internally; many are both (lookup fires as you type AND
as a deliberate query).  Each dispatches to the entity that owns the
capability: [librarian] for the store and indexes, [signer] for keys,
[session] for the view projection and the auth table.


# item lifecycle
create              [librarian]   mint an instance of an archetype
delete              [librarian]   remove an item from local storage
view                [session]     open an ITEM_VIEW on an item
close               [session]     close an open view


# search and discovery
lookup              [librarian]   token lookup (implicit while typing; also explicit)


# identity & keys
authenticate        [session]     sign a user into a session
sign-out            [session]     end a user's session
rotate              [signer]      rotate a key-track (current → next)
delegate            [signer]      grant authority to another signer
revoke              [signer]      withdraw an attestation / delegation


# body transforms (produce Opaque content)
encrypt             [signer]      → Opaque.Encrypted   (AGENT-routed)
decrypt             [signer]      ← Opaque.Encrypted
elide               [librarian]   → Opaque.Redacted
compress            [librarian]   → Opaque.Compressed
decompress          [librarian]   ← Opaque.Compressed


# communication
send                [librarian]   transmit X to a recipient (librarian owns the outbound channel;
                                  the signer's keys do the signing, but the librarian is the
                                  only entity with network reach)


# system-internal (rarely typed; here for completeness)
construct           [archetype]   post-create hook (initial state)
inception           [signer]      mint a key-track (the first key)
display-layout      [session]     place a display within the session's coord space


# open questions

- modify / update   — CG mutates by re-committing manifests.  Does this earn a
                      verb of its own, or is it always "create a new version"?

- inspect / describe — read state explicitly.  Today implicit via fetch.  Worth
                       a verb when the user types "describe X" expecting a
                       prose answer rather than a navigation?

- materialize       — store bytes locally / pin from a peer.  The act-kind
                      exists in `RecordVocabulary`; the unification sweep
                      should give it a command form (parallel to
                      encrypt/elide/compress).

- subscribe / unsubscribe — watching for changes to an item or topic.  Pairs
                            with the SUBSCRIBE design in
                            [[project_lifecycle_model]].

- forward / reply   — composites of `send`, or distinct verbs with their own
                      shape?

- attest            — sign for another's assertion (signer-to-signer trust
                      assertions).  Distinct from inception/rotation/delegate
                      because the body being signed isn't your own.

- connect / disconnect — peer management at the network layer.  Probably
                         system-internal but might surface ("connect to
                         alice.example") for explicit handshakes.


# notes

- There's no hard line between "commands" and other predicates.  Anything that's
  a predicate can be the head of a frame body someone types.  This list is the
  *curated common set* — verbs people actually invoke or the system routinely
  emits — not a closed type.

- Lexical relation predicates (hypernym/hyponym/lexeme/gloss/…) and the schema
  predicate (implements) are mostly *generated* by lexicon imports and
  app-developer vocabulary rollouts, but they're declarative and a user
  perfectly well *could* type one ("X is hypernym of Y", "this word has this
  gloss in English") to extend the lexicon by hand.  Uncommon, not impossible.

- The input-event predicates (key-press, pointer-down, focus, blur, …) and the
  operator sememes (add, multiply, transform, …) are different — input events
  are emitted by devices and aren't sensibly typed; operator sememes are
  evaluation targets, not commands.

- Some entries above are already wired; others are aspirational.  As of
  2026-05-29: create / delete / lookup / view / close / encrypt / decrypt /
  elide / compress / construct / inception / rotation / delegation / revocation
  exist as seeded predicates with handlers.  authenticate / sign-out exist as
  Java methods on Session but not yet as CG predicates.  send / subscribe /
  decompress / modify / materialize / attest / forward / reply are not yet
  predicates.
