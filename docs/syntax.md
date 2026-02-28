# Reference Syntax

Common Graph uses a consistent syntax for referencing items, versions, content, and relations.

## Item References

```
iid:<hex>                    # Item by identity
iid:<hex>@<vid>              # Specific version
iid:<hex>#<rid>              # Specific relation
iid:<hex>\<cid>              # Content block
iid:<hex>\<cid>[selector]    # Content fragment
```

## Examples

```
# Just the item
iid:a1b2c3d4e5f6...

# Specific version
iid:a1b2c3d4e5f6...@f9e8d7c6b5a4...

# A relation on the item
iid:a1b2c3d4e5f6...#1234abcd...

# A content block
iid:a1b2c3d4e5f6...\deadbeef...

# Bytes 100-200 of a content block
iid:a1b2c3d4e5f6...\deadbeef...[100-200]
```

## Selectors

Selectors address fragments within content:

| Selector | Format | Example |
|----------|--------|---------|
| Byte span | `[start-end]` | `[100-200]` |
| JSON path (future) | `[$.path.to.field]` | `[$.users[0].name]` |
| Time range (future) | `[t0-t1]` | `[00:30-01:45]` |

## Human-Readable Aliases

Human-readable names are **relations**, not magic namespaces:

```
user:Alice → hasAlias → literal("~alice") { scope: "local" }
```

Resolution:
1. Start from a known item (your host, a directory)
2. Follow alias relations
3. Apply trust policy to choose among conflicts

## Encoding Conventions

### Binary Keys

For database/index keys:

```
[FIELD32]                    # Fixed 32-byte field
(len|bytes)                  # Length-prefixed UTF-8
<sep>                        # Separator byte (0x1F)
BE/LE                        # Big/little endian
```

### Canonical CBOR

For content hashing:
- Deterministic field order (each field has a fixed order number)
- No floating point (use decimal: mantissa + scale)
- Maps sorted by key
- No duplicate keys

See [CG-CBOR](cg-cbor.md) for the full specification.

### Index Key Examples

```
# By subject
[SUBJECT_IID32]<sep>[PRED32]<sep>[VID32]

# By predicate
[PRED32]<sep>[SUBJECT_IID32]<sep>[VID32]

# Per-principal (private)
[PRINCIPAL32]<sep>[...rest...]
```
