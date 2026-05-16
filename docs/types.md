# Types

Common Graph has one structural primitive (the datum) and a small set of meta-archetypes that establish the universal rules. Every body in the system is an instance of some archetype; every archetype is itself a body with its own archetype; the chain bottoms out at a self-referential root. The result is a type system that lives entirely as graph data — schemas, hierarchies, validations, all expressed in the same shape as the data they describe.

This document defines the meta-archetype tree, the roles items play within it, and how the system avoids the schema-versus-data split that plagues most data models.

This document assumes familiarity with [the datum primitive](datum.md), [items](item.md), [frames](frames.md), and [the reference scheme](ref-scheme.md).

## The meta-archetype tree

Body-shaped bodies all carry a head. The head names what kind of thing the body is. That head is itself an item, with its own head, and so on. The chain of heads is the type hierarchy, expressed as references rather than as a parallel construct.

A canonical slice of the tree:

```
Archetype                              (the root; self-referential)
  ├── Predicate                        (its instances are frames)
  │     ├── Add, Multiply, Authored, Move, …
  │
  ├── Item                             (its instances are addressable, versioned)
  │     ├── Document
  │     ├── Signer
  │     │     └── Librarian
  │     ├── ChessGame
  │     ├── Code                       (its instances carry runtime forms)
  │     │     └── …per-language code items
  │     └── …
  │
  ├── Value                            (its instances are typed value bodies)
  │     ├── Quantity
  │     │     ├── Length, Mass, Time, Temperature,
  │     │     │   ElectricCurrent, Amount, LuminousIntensity
  │     │     └── …
  │     ├── Color
  │     ├── Point
  │     └── …
  │
  └── Language                         (its instances are human/code languages)
        ├── English, German, Japanese, …
        ├── Clojure, Python, Lisp, …
```

Every item in the system fits somewhere in this tree. Every body's head is the archetype it instantiates; that archetype is itself an item, whose head is the meta-archetype it instantiates; and so on up to Archetype, which is its own head (the universal root).

## The roles items play

The taxonomy splits along *what their instances are*:

**Archetypes whose instances are items.** Most archetypes. ChessGame's instances are chess-game items; Document's instances are document items; each instance has its own IID and lineage of manifests. The archetype's schema describes what an instance's manifest looks like (`!`-prefixed bindings declaring expected slots, `@HANDLES` declaring the API surface).

**Archetypes whose instances are frames.** Predicates. Add's instances are Add-headed frames; Authored's instances are Authored-headed frames. Frames are not items — they have no IIDs, no lineages — they're free-standing semantic statements. The predicate's schema describes what a frame using it looks like.

**Archetypes whose instances are value bodies.** Color's instances are Color value bodies — `{@color, [@R → 255, @G → 0, @B → 0]}`. Length's instances are Length value bodies — `{@length, [@VALUE → 5, @UNIT → @meter]}`. Values are immutable data, like frames, but distinguished by purpose: they represent *typed quantities*, not relationship-assertions. They have no IIDs, no lineages, and no thematic-role bindings (their bindings are typed components, not participant slots).

**Archetypes whose instances are something else** — code items (instances of Code), language items (instances of Language). These are special-purpose archetypes where the instance category is itself meaningful.

The distinction is *purely about what an instance is for*, not about structural type. The same datum primitive describes them all; the head determines which role this particular body plays.

## What makes an item an item

An archetype's instances are items if and only if the archetype's schema declares an `!ITEM_ID` slot. This is the structural marker.

The Item meta-archetype's own schema includes `!ITEM_ID`. Any archetype that inherits from Item (directly or transitively) gets this slot. Any instance of such an archetype must carry an `@ITEM_ID → <iid>` binding in its manifest.

Predicates' schemas don't include `!ITEM_ID`. Value archetypes' schemas don't include `!ITEM_ID`. Code-item archetypes' schemas do (code items are items themselves, with their own identities and lineages). The presence of `!ITEM_ID` in an archetype's schema is the system's binary "is this archetype's instances item-like?" answer.

This is what makes the predicate/archetype split *usage-based, not structural*. There's no Predicate class versus an Archetype class in the runtime; there's only "this body has `!ITEM_ID` in its schema, and that one doesn't." Items inherit from Item (or one of its sub-archetypes); predicates inherit from Predicate; values inherit from Value. The category emerges from the meta-archetype chain, not from a separate categorical declaration.

## Schemas live on the archetype itself

Where many type systems require a parallel schema language — JSON Schema, IPLD Schema, Protobuf .proto files, XML XSDs — Common Graph puts schemas directly on the archetype's manifest as `!`-prefixed bindings. The archetype's schema IS its bindings; there's no separate schema file pointing at the type, no separate schema item being referenced.

```
@chess-game's manifest:
  head: @archetype
  bindings:
    @ITEM_ID → <chess-archetype-iid>
    !PLAYER:[WHITE] → ?user
    !PLAYER:[BLACK] → ?user
    !TURN → ?color
    @HANDLES → @move
    @HANDLES → @resign
```

This manifest is both *what the ChessGame archetype IS* and *what it expects of its instances*. The schema bindings (`!PLAYER`, `!TURN`) declare instance shape; the HANDLES bindings declare API surface; the ITEM_ID binding declares the archetype's own identity. One manifest, three concerns, all in the same data shape.

When validating an instance against its archetype, the runtime walks the archetype's `!`-bindings and confirms the instance has matching concrete bindings. Validation reports conformance; it doesn't reject non-conformance. An instance that doesn't match its archetype's schema is still data — just non-conforming data, flagged for whoever's looking at it.

## Inheritance through the head chain

Inheritance in Common Graph is the head chain. An archetype whose head is `@activity` inherits from Activity; an archetype whose head is `@game` (whose head is `@activity`) inherits transitively. The chain walks upward from any body to the meta-root.

Schemas accumulate down the chain. A sub-archetype's effective schema is the union of its own `!`-bindings, its parent archetype's `!`-bindings, and so on. ChessGame inherits Activity's schema slots; Activity inherits Item's `!ITEM_ID` slot; Item bottoms out at Archetype.

Overriding works the same way. A sub-archetype can re-declare a `!`-binding the parent already has, narrowing or constraining it; the sub-archetype's version takes precedence. New `!`-bindings on the sub-archetype are additive.

The HANDLES set inherits identically. ChessGame inherits Activity's HANDLES (whatever those are), adds its own MOVE / RESIGN / OFFER-DRAW. An instance of ChessGame responds to the union.

## Values vs entities

A useful distinction within the tree: **values** and **entities**.

A **value body** has its data inline. Color holds RGB components in its bindings; Length holds magnitude and unit; Point holds coordinates. The body *is* the data — no IID, no lineage, no separate state. Value bodies have content-addressed identity (same RGB values → same hash) and are immutable.

An **entity item** has identity outside its data. The chess-game item has an IID; its content (player bindings, turn state, accumulated moves) lives in its manifests' bindings, but those bindings can change across versions. The item *refers* to a sequence of data states; the item itself is the persistent thing.

Values and entities are structurally indistinguishable at the body level — both are head + bindings. They're distinguished by whether their archetype includes `!ITEM_ID` in its schema. Color does not (instances of Color are values, content-addressed, no versions). ChessGame does (instances of ChessGame are entities with IIDs).

This is the same continuant/occurrent distinction that splits manifests from frames, expressed in terms of value-versus-entity. Frames are occurrent — single semantic statements. Items are continuant — persisting subjects. Values are *neither* — they're abstract immutable data, like the number five or the color red.

## Quantity, with its dimensional subclasses

Quantity is the value archetype for scalar measurements. Its schema declares an amount and a unit:

```
@quantity's manifest:
  head: @archetype
  bindings:
    @ITEM_ID → <quantity-archetype-iid>
    !VALUE → ?number
    !UNIT → ?unit
```

A Quantity value body fills these slots concretely:

```
{@quantity, [
  @VALUE → 5,
  @UNIT → @meter
]}
```

The dimensional subarchetypes — Length, Mass, Time, Temperature, ElectricCurrent, Amount, LuminousIntensity — extend Quantity. Each is a sub-archetype whose head is Quantity and whose schema may further constrain the UNIT slot (Length expects a length-dimensioned unit; Mass expects a mass-dimensioned unit; etc.).

A Length value body specifies its archetype:

```
{@length, [
  @VALUE → 5,
  @UNIT → @meter
]}
```

The dimensional information lives in the head; the runtime can validate that the unit-target matches the expected dimension via the archetype's chain.

The seven SI base dimensions — length, mass, time, electric current, temperature, amount of substance, luminous intensity — get their own Quantity sub-archetypes for first-class typing. Derived dimensions (velocity, energy, etc.) emerge by composition.

## Worked example: the type chain for a chess move

A single chess move frame and the type chain that informs its interpretation:

```
The frame:
  {@move, [@AGENT → @alice, @THEME → @king-pawn, …]}

@move's archetype chain (head pointers walked upward):
  @move          (head: @predicate)
    @predicate   (head: @archetype)
      @archetype (head: @archetype — self-referential)

@move's schema (bindings on its own manifest):
  @ITEM_ID → <move-iid>
  !AGENT → ?player
  !THEME → ?piece
  !SOURCE → ?square
  !GOAL → ?square

The frame's targets and their type chains:
  @alice              (head: @signer → @item → @archetype)
  @king-pawn          (head: @chess-piece → @piece → @item → @archetype)
  …
```

Walking the chain answers all the system's type questions. *Is this frame valid?* — walk @move's `!`-bindings, check the frame has matches. *Is @alice the right kind of thing for AGENT?* — walk @alice's archetype chain looking for @player (the constraint in @move's schema). *What HANDLES applies to this MOVE frame?* — walk @move's relevant indexes finding items whose archetype's HANDLES set includes @move.

No type tables, no schema registry, no inheritance metadata stored separately. The graph itself encodes its own typing.

## Relations

- [`datum.md`](datum.md) — the single structural primitive.
- [`item.md`](item.md) — items as continuants, the role they play in the type hierarchy.
- [`frames.md`](frames.md) — predicates and their instances.
- [`manifest.md`](manifest.md) — where archetypes' schemas and HANDLES live.
- [`ref-scheme.md`](ref-scheme.md) — the `@` / `?` / `!` distinctions for references in schemas.
- [`api.md`](api.md) — HANDLES inheritance through the archetype chain.
- [`values.md`](values.md) — the Value subtree in detail, including Quantity and Color.
- [`sememes.md`](sememes.md) — the linguistic backbone the type tree is anchored to.
