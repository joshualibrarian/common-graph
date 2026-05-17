# Values

A **value** in Common Graph is a body whose head names a value-typed archetype. Color, Quantity, Length, Mass, Point, and similar typed scalars are values; they encode a structured value the way a number encodes a count or a UUID encodes an identity. Values are immutable, content-addressed, and stand-alone — they have no IIDs, no lineages, no need to be items. They *are* their data.

This document defines the Value meta-archetype, its descendants, and how typed value bodies serve as the system's first-class data types — quantities with units, colors with channels, points with coordinates, anything whose structure is more than a primitive but less than an item.

This document assumes familiarity with [the datum primitive](datum.md), [items](item.md), [types](types.md), and [the reference scheme](ref-scheme.md).

## The Value archetype

`@value` (head: `@archetype`) is the meta-archetype for typed values. Its instances are *archetypes whose own instances are typed value bodies*. Color extends Value; Quantity extends Value; Length extends Quantity extends Value. The hierarchy lives in head pointers, the same way every other archetypal chain does.

```
@archetype
  └── @value
        ├── @color
        ├── @quantity
        │     ├── @length
        │     ├── @mass
        │     ├── @time
        │     ├── @electric-current
        │     ├── @temperature
        │     ├── @amount
        │     └── @luminous-intensity
        ├── @point
        ├── @bool
        ├── @numeric
        └── …
```

Each Value archetype carries its own schema (`!`-bindings declaring the components instances should hold) and its own gloss / lexeme frames. Concrete value bodies fill in the slots.

## Body-shaped values

The dominant kind. A body-shaped value has a head naming its archetype and bindings naming its components. The bindings' targets are typically literals (numbers, strings) or other value bodies (nested structures).

```
{@color, [
  @R → 255,
  @G → 0,
  @B → 0
]}

{@quantity, [
  @VALUE → 5,
  @UNIT → @meter
]}

{@point, [
  @X → 3,
  @Y → 4
]}
```

Three different value archetypes; three different schemas; three independent shapes; same overall structure (head + bindings, same as every other body in the system).

Two body-shaped values with identical bindings have identical content — same ContentID, same DatumID, deduplicated in storage by birthright. They're interchangeable wherever they appear. Equality is byte equality, automatically.

## Primitive-shaped values

Some Value archetypes have *no* body. Their instances are wire literals — a Java `Boolean`, a `Long`, a `BigDecimal` — and the archetype exists so that other archetypes can declare `?bool` or `?numeric` as a target pattern.

```
@bool      ; instances are wire booleans (true / false)
@numeric   ; instances are wire numbers (Long, BigInteger, BigDecimal, Rational)
```

A Bool body would be redundant — booleans are just booleans, and wrapping them in a body adds bytes without adding meaning. The Bool archetype exists because the type system needs to refer to "the type of boolean values" in schema declarations and operator return-type specifications.

The same applies to Numeric: numbers don't need a body to be self-describing, but the system needs a name for "the type of numbers" for use in `!THEME → ?numeric` schema declarations.

Body-shaped and primitive-shaped values coexist. Each Value archetype declares its instance shape; the system handles both uniformly through the same Value meta-archetype.

## Schemas on Value archetypes

A Value archetype's manifest carries `!`-bindings declaring the slots its instances should fill, exactly as any other archetype does:

```
@color's manifest:
  head: @archetype
  bindings:
    @ITEM_ID → <color-iid>
    !R → ?numeric
    !G → ?numeric
    !B → ?numeric
```

```
@quantity's manifest:
  head: @archetype
  bindings:
    @ITEM_ID → <quantity-iid>
    !VALUE → ?numeric
    !UNIT → ?unit
```

Instance value bodies fill in those slots with concrete bindings. The system can validate a candidate value against its archetype's schema — same machinery as for any other archetype.

The `!`-bindings constrain target *shapes*, not target values. A Color's R can be any number; a Quantity's UNIT can be any unit; range constraints (clamping to 0-255 for color channels, dimensional constraints on Quantity sub-archetypes) live in domain-specific logic, not in the basic schema declarations.

## Quantity and the SI dimensional types

Quantity is the generic typed scalar: magnitude plus unit. The seven SI base dimensions get their own subarchetypes for first-class typing:

| Archetype | Quantity of | Base unit |
|---|---|---|
| `@length` | distance | meter |
| `@mass` | mass | kilogram |
| `@time` | duration | second |
| `@electric-current` | current | ampere |
| `@temperature` | temperature | kelvin |
| `@amount` | amount of substance | mole |
| `@luminous-intensity` | luminous intensity | candela |

Each sub-archetype's schema inherits Quantity's `!VALUE` and `!UNIT` declarations and may further constrain the UNIT to its dimension's units. A Length body's UNIT must be a length-unit (meter, foot, parsec, …); a Mass body's UNIT must be a mass-unit (kilogram, pound, solar mass, …). The constraint is expressed in the archetype's schema; runtime validation walks the archetype chain.

```
{@length, [
  @VALUE → 5,
  @UNIT → @meter
]}

{@mass, [
  @VALUE → 70,
  @UNIT → @kilogram
]}

{@time, [
  @VALUE → 3,
  @UNIT → @hour
]}
```

Same structural shape; different archetypes; different dimensional semantics. Operators that work on quantities (Add, Multiply, comparison) dispatch on the dimension and reject mismatches (`@length + @mass` is a type error).

**Derived dimensions emerge by composition.** Velocity is length divided by time; energy is mass times length-squared divided by time-squared. The system doesn't enumerate every derived dimension as its own archetype — it computes derived dimensions from the bases when operators combine quantities. A velocity quantity is `{@quantity, [@VALUE → 100, @UNIT → @kilometers-per-hour]}`, and the kilometers-per-hour unit's dimensional analysis says "length / time."

The seven SI base dimensions are first-class because they're foundational; everything else falls out by combination.

## Color and the presentation values

Color is the canonical value example outside of Quantity. Its instances carry RGB(A) component bindings:

```
{@color, [
  @R → 255,
  @G → 153,
  @B → 0
]}
```

Color's archetype carries schemas, glosses, and named-color sub-archetypes (`@red`, `@orange`, `@chartreuse`, ...) — common colors that get their own typed identities for convenience, each with a manifest specifying its standard component values.

Beyond Color, the presentation system uses an array of value archetypes for the typed slots its scenes work with: borders, font sizes, easing curves, animation timings. The Layout, Typography, Spatial, and Visual vocabularies all build on Color, Length, Time, and the dimensional values for their typed bindings.

## No IID, no lineage

A value body has no item identity. Two color bodies with identical RGB hash to the same ContentID and are interchangeable. They don't accumulate history; they don't have manifests; they're not the target of `@`-references in the item sense.

References to values typically appear *inline* — the value body sits in the binding directly:

```
{@harvest-record, [
  @AGENT → @alice,
  @THEME → @tomatoes,
  @RESULT:[QUANTITY, WEIGHT] → {@mass, [@VALUE → 5, @UNIT → @kilogram]}
]}
```

The mass quantity is the binding's target, inline, no separate identity. References by `~` (ContentID) are valid for value bodies as well — useful when the same value is shared across many frames and you want byte-level deduplication referenced explicitly.

Value bodies *are* their data. Their identity is their content; their content is their bytes; their bytes hash to a stable ContentID. That's all the identity they need.

## Why values are first-class

Earlier systems treat typed scalars as primitives bolted on top of a string-or-int data model. CSS has `rgba()` and `5px`; SQL has `NUMERIC(10, 2)` and `INTERVAL`; programming languages have unit-aware libraries grafted on top of doubles. Each is a special case.

Common Graph treats typed values as bodies — the same structural primitive as everything else. A Color is a body. A Length is a body. A custom value type (Rational, Polynomial, a domain-specific currency) is a body. The system reasons about them uniformly because they're all the same shape.

New value types are vocabulary additions, not language extensions. Introducing a new value archetype means minting a new sememe whose head is `@value`, declaring its schema, endorsing lexemes and glosses for it. No new wire format, no new parser, no special case in the codec. The encoding, the canonical walker, the dispatch — all work on the new archetype the same way they work on the existing ones.

This is the architectural payoff of one structural primitive. The cost is one body per typed scalar; the benefit is unbounded extensibility.

## Worked examples

**A red color.**

```
{@color, [
  @R → 255,
  @G → 0,
  @B → 0
]}
```

Three concrete bindings, integer literals. Same body shape as Color's schema, just with concrete values where the schema had `!R → ?numeric`.

**A 5-meter length.**

```
{@length, [
  @VALUE → 5,
  @UNIT → @meter
]}
```

VALUE is a literal numeric; UNIT is a sememe reference to the meter unit. The Length archetype's schema declared `!VALUE → ?numeric` and `!UNIT → ?unit` (constrained to length-dimensioned units).

**A point in 2D.**

```
{@point, [
  @X → 3,
  @Y → 4
]}
```

Point is a value archetype with X and Y component slots. Other coordinate systems (polar, 3D) get their own archetypes with their own component bindings.

**A schema example, side-by-side.**

```
Color schema (archetype manifest):
  {@archetype, [
    @ITEM_ID → <color-iid>,
    !R → ?numeric,
    !G → ?numeric,
    !B → ?numeric
  ]}

Color instance (a value body):
  {@color, [
    @R → 255,
    @G → 0,
    @B → 0
  ]}
```

Same head reference, parallel structure, `!` versus `@` distinguishing schema slots from concrete values.

## Relations

- [`datum.md`](datum.md) — the structural primitive value bodies are built from.
- [`item.md`](item.md) — items vs. values; what makes a body an item.
- [`types.md`](types.md) — the meta-archetype tree; where Value sits.
- [`sememes.md`](sememes.md) — sememes used as roles, units, named colors.
- [`ref-scheme.md`](ref-scheme.md) — how value bodies appear inline or are referenced by `~`.
