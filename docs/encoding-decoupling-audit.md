# Encoding Decoupling Audit (2026-05-18)

Audit of the encoding-coupling violations in `datum/`, `id/`, and adjacent packages.  Joshua's framing of the principle:

> **The walker has to walk the structure and USE the encoder to extract the data correctly, but it can't know what encoding it is.**

The `datum/` package should be pure data structures.  Knowledge of CBOR (or any other wire format) belongs in `encoding/`.  Today that line is crossed in several places.

## What's right

The proper system exists and works:

- **`canonical.@Encode` / `canonical.@Decode`** — encoding-agnostic per-class annotations marking the wire form for "leaf" types.  `@Encode` methods return primitive shapes (`byte[]`, `String`); `@Decode` static methods take those same primitive shapes and reconstruct the typed value.  Used by `canonical.Leaves` for reflective dispatch and by `canonical.CanonWalker` for structural walking.  This is the right protocol.
- **`encoding.Encoding`** SPI — the polymorphic encoder interface.  Methods speak in `byte[]` / `Object` / `Node`, not in any codec's native types.
- **`encoding.CgCbor`** — concrete CG-CBOR-v1 implementation.  Legitimately full of `CBORObject` references; that's its job.

The contract those three define is: classes in `datum/`, `id/`, `value/` declare what they look like as leaves (via `@Encode`/`@Decode`) and as structures (via fields and the canonical walk).  Encoders ingest those declarations and emit/parse bytes.  No domain class should ever name a specific codec.

## What's wrong

### 1. `fromCborTree(CBORObject)` static methods in non-encoding packages

These methods import `com.upokecenter.cbor.CBORObject` directly and reach into CBOR internals to reconstruct typed values.  They couple their package to a specific codec library.

| File | Method |
|---|---|
| `datum/Body.java` | `fromCborTree(CBORObject)` + private `decodeEntries(CBORObject)` helper |
| `datum/Record.java` | `fromCborTree(CBORObject)` |
| `datum/Binding.java` | `fromCborTree(CBORObject)` |
| `datum/BindingTarget.java` | `fromCborTree(CBORObject)` (plus nested `RefTarget.fromCborTree`, `FrameTarget.fromCborTree`) |
| `datum/Opaque.java` | `fromCborTree(CBORObject)` + variants (`Redacted.fromCborTree`, `Compressed.fromCborTree`, `Encrypted.fromCborTree`) |
| `id/HashID.java` | `fromCborTree(CBORObject)` |

Joshua marked the architectural violation explicitly on `Body.fromCborTree`:

```java
@Factory    //TODO: this entire datum package shouldn't know about encoding or cbor.
            //  It should only see the abstraction of Encoder
```

And on `Datum.java` itself:

```java
TODO: we need a thorough going through of this whole package.  There's still
      lots of CBOR references in it and it should be encoding agnostic.
TODO: also the builders could use unification and improvement
```

### 2. `@Factory` annotation — misused

`canonical/Factory.java` was designed for a different purpose:

> Marks a static factory method for UI-driven creation discovery.
> When the UI needs to create an instance of a type, it scans for
> methods annotated with @Factory and presents them as options to the user.

The annotation has UI-shaped fields: `label`, `glyph`, `primary`, `order`, `doc`.  It's clearly meant for surfacing "create-me-a-Library" options to an end user, not for marking decode entry points.

But every usage in `datum/` is on a `fromCborTree(CBORObject)` method — being treated as "this is a decode factory the codec should dispatch to," with all the UI fields defaulted.  Joshua's TODO on the annotation itself:

```java
TODO: we need to reassess this
```

The annotation needs to either be **kept for UI purposes only** (and removed from all the `fromCborTree` sites, which shouldn't exist anyway), **repurposed for decode-dispatch** (with the UI fields stripped, which makes the annotation a near-duplicate of `@Decode`), or **deleted** (the UI factory-discovery concern can use a different mechanism, e.g., reflection over static methods returning the target type).

My read: the `@Factory` annotation is doing nothing useful in its current shape.  It should be deleted; the `@Decode` annotation already covers decode-dispatch.

### 3. `library/` byte stores hardcode CBOR shape detection

```java
// DataByteStore.java
CBORObject node = CBORObject.DecodeFromBytes(bytes);
int size = node.size();
if (size == 2) return Body.fromCborTree(node);
if (size == 3) return Record.fromCborTree(node);
```

A byte-store that calls `CBORObject.DecodeFromBytes` and switches on `node.size()` is hardcoded to CG-CBOR's specific shape (Body = 2-element array, Record = 3-element).  This violates the same principle: storage stores bytes; deserialization is the encoder's job.

The fix: `DataByteStore.fetchAndDecode(bytes)` should call `encoding.decode(bytes)` and let the Encoding return the right type.  No CBOR knowledge in the byte store.

Same pattern in `TokenIndexByteStore.java`.

### 4. Builders / convenience constructors are scattered

Per Joshua's TODO on `Datum.java`: "also the builders could use unification and improvement."

Today datum types have a mix of:
- Direct constructors (`new Body(head, entries)`)
- Static factories (`Body.of(head, entries)`)
- `fromCborTree(CBORObject)` (CBOR-coupled)
- Builder classes elsewhere (`BodyComposer.compose(...)`)

The builder surface deserves a single canonical pattern.  Probably: `new ClassName(...)` for the minimal case + a `ClassName.compose()` fluent builder for complex bindings.  Drop the redundant `of(...)` statics or align them with the builder.  Not urgent but accumulating drift.

## Architectural target

The clean shape:

```
canonical/
    @Encode, @Decode           — encoding-agnostic leaf protocol (working)
    CanonWalker, Leaves        — reflective walkers using @Encode/@Decode
    Factory.java               — DELETE (UI concern is separate; misused everywhere)

encoding/
    Encoding (SPI)             — encode(Object) → byte[], decode(byte[]) → Object,
                                  decodeOne(InputStream) → Optional<Object>
    CgCbor                     — concrete CG-CBOR-v1 codec
                                  - knows CBORObject, knows CG-CBOR tags
                                  - houses ALL the from-CBOR-tree dispatch
                                  - calls back into datum types via their
                                    public constructors

datum/
    Datum, Body, Record,        — pure POJOs
    Binding, BindingTarget,     - public constructors / accessors
    Opaque                      - @Encode/@Decode where applicable (leaf cases)
                                  - NO fromCborTree methods
                                  - NO CBORObject imports
                                  - NO @Factory

id/
    HashID, ItemRef, etc.       — same: pure POJOs + @Encode/@Decode

value/
    Color, Quantity, etc.       — same; Body.fromCborTree references in javadocs
                                   get rewritten or dropped

library/data/
    DataByteStore               — delegates to Encoding.decode for typed
                                   reconstruction; no CBOR knowledge
library/index/
    TokenIndexByteStore         — same
```

## Refactor plan

Three passes, each leaving the build green:

### Pass 1: extract from-CBOR dispatch into CgCbor

For each `fromCborTree(CBORObject)` method in `datum/`, `id/`:
- Move the method body into `CgCbor` (or a new `CgCbor.Decoders` helper class) as a private/package-private static method.
- The new home: a switch on `CBORObject` shape that dispatches to a typed constructor.  Most of the logic already exists in `CgCbor.fromCbor`; we're just centralizing the remaining cases.
- Remove the `fromCborTree` method + `CBORObject` import + `@Factory` annotation from the datum/id class.
- Update any direct callers (DataByteStore, TokenIndexByteStore) to call through `encoding.decode(bytes)` instead.

After Pass 1: `datum/`, `id/`, `value/` have zero `com.upokecenter.cbor` imports.

### Pass 2: delete `@Factory` annotation

- Remove all `@Factory` usages (residual ones after Pass 1, if any).
- Delete `canonical/Factory.java`.
- Document that UI factory-discovery (if/when it lands) uses a different mechanism — probably reflection over static methods returning the target type, or a new annotation purpose-built for UI without ambiguity with `@Decode`.

### Pass 3: byte-store / index decoupling

- `DataByteStore.decode(bytes)` calls `encoding.decode(bytes)`; the Encoding is provided at construction (probably already is).
- `TokenIndexByteStore` similarly.
- Confirm no CBOR-shape switching remains in `library/`.

### Pass 4 (optional, deferred): builder unification

Per Joshua's TODO on Datum.  Aligning `Body.of()` / `Body.compose()` / `new Body()` into a single canonical pattern.  Mechanical but touches many call sites.  Worth doing but not urgent.

## Estimated scope

- Pass 1: ~6 files in datum/, id/; centralize ~300 lines of CBOR dispatch into CgCbor; remove ~200 lines of duplicate decoding from datum/id classes.  ~half a day.
- Pass 2: ~10 @Factory annotation removals + delete Factory.java.  ~30 minutes.
- Pass 3: ~2 byte-store files, replace CBOR-shape switches with Encoding delegation.  ~1 hour.
- Pass 4: deferred.

Total: roughly a half-day of focused work for Passes 1-3.  Build stays green at each step.

## Recommendation

Do Passes 1-3 as one focused session.  The violation cuts across enough files that piecemeal cleanup would leave the codebase inconsistent in the middle.  Pass 4 (builder unification) can wait for a separate cleanup pass — it's orthogonal to the encoding-decoupling concern.
