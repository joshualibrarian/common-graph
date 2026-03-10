# Scripting

Items carry code. A chess game ships with the rules that enforce legal moves. A financial ledger ships with the formulas that compute balances. A chat room ships with the bot that greets new members. The code is a component — signed, versioned, content-addressed — just like any other part of the item. It runs in a sandboxed runtime engine on the host Librarian, governed by the same trust and policy mechanisms that govern everything else in the graph.

This is not a plugin system bolted onto a platform. There is no app store, no marketplace, no review board. Code distributes through the graph like any other content. Trust replaces curation. The signer's reputation, the item's policy, and the host's resource limits determine whether code runs — not a gatekeeper's approval.

## Progression

The scripting system exists on a continuum with what already works:

| Level | What | Mechanism |
|-------|------|-----------|
| **0. Java verbs** | `@Verb` annotations on compiled classes | Static dispatch, compile-time |
| **1. Expressions** | `ExpressionComponent`, vocabulary aliases | Eval pipeline, vocabulary contributions |
| **2. Scripts** | `ScriptComponent` evaluated by host runtime engines | Interpreted, hot-reloadable |
| **3. Live editing** | Edit script text, verb changes immediately | Inline editing from prompt or surface |
| **4. Distributed code** | Code Items fetched from graph, verified, sandboxed | Content-addressed, trust-gated |
| **5. Self-hosting** | Runtime engines as Items, capability discovery | Graph-native runtime management |

Level 0 exists today. Level 1 exists (ExpressionComponent, scripted expression aliases). Levels 2-5 are what this document specifies.

The boundary between levels is porous. A Level 1 expression alias and a Level 2 Groovy script both contribute verbs through the same EntryVocabulary mechanism. A Level 4 distributed WASM module and a Level 0 Java verb both dispatch through the same sememe-based vocabulary pipeline. The user sees verbs. The system doesn't care where the implementation came from.

---

## Script Component

A `ScriptComponent` is a component type like any other — `@Type` annotated, CBOR-serializable, holding source code and metadata:

```
ScriptComponent {
    language:   ItemID          # Runtime language (cg:language/groovy, cg:language/javascript, etc.)
    source:     string          # Source code text
    entryPoint: string?         # Entry function name (null = entire source is the body)
    bindings:   [BindingDecl]   # Declared input bindings
}
```

A `BindingDecl` declares what the script expects from its host item:

```
BindingDecl {
    name:   string      # Variable name exposed to the script
    source: BindSource  # Where the value comes from
}

BindSource = ITEM           # The owning Item
           | LIBRARIAN      # The host Librarian
           | COMPONENT(hid) # A sibling component by handle
           | CONTEXT        # The ActionContext (caller, params, etc.)
           | SETTING(key)   # A scoped setting on this component's entry
```

### Attachment

Scripts attach to items the same way any component does — via `@Item.ContentField` or dynamic addition:

```java
@Item.ContentField(handle = "greeting-script", path = "/scripts")
private ScriptComponent greetingScript;
```

Or dynamically at runtime: add a ScriptComponent to any item's component table, commit, and the new verbs are live.

### Relationship to ExpressionComponent

ExpressionComponent handles the CG expression language — vocabulary aliases, math, pattern queries. ScriptComponent handles general-purpose code in external languages. They serve different purposes:

| | ExpressionComponent | ScriptComponent |
|---|---|---|
| **Language** | CG expression language | Groovy, JavaScript, Python, Lua, WASM, Ruby |
| **Evaluation** | Eval pipeline | Runtime engine |
| **Typical use** | Aliases, formulas, queries | Complex logic, game rules, bots, integrations |
| **Trust level** | Low (constrained grammar) | Higher (general-purpose code requires sandboxing) |

Both contribute verbs through the same EntryVocabulary. Both dispatch through the same sememe pipeline. The distinction is implementation complexity, not architectural separation.

---

## Runtime Registry

The Librarian maintains a **RuntimeRegistry** — a mapping from language Items to available script engines:

```
RuntimeRegistry {
    engines:  Map<ItemID, RuntimeEngine>    # Language IID → engine instance
    limits:   ResourceLimits               # Global defaults for all engines

    resolve(languageId) → RuntimeEngine?
    available() → [ItemID]
}
```

A `RuntimeEngine` wraps a specific interpreter or compiler:

```
RuntimeEngine {
    languageId:   ItemID              # Which language this engine handles
    evaluate(source, bindings, sandbox) → Object
    canCompile() → boolean            # Ahead-of-time compilation support
    compile(source) → CompiledScript  # Pre-compiled form for repeated execution
}
```

### Supported Runtimes

| Priority | Language | Engine | Impedance | Use Case |
|----------|----------|--------|-----------|----------|
| 1 | **Groovy** | GroovyShell / GroovyClassLoader | Zero — calls CG Java classes directly | Power users, item authors, rapid prototyping |
| 2 | **JavaScript** | GraalJS (GraalVM polyglot) | Low — polyglot interop | Web-familiar scripting, JSON manipulation |
| 3 | **WASM** | Chicory (pure Java WASM interpreter) | High — binary interface | Distributed code, language-agnostic portable modules |
| 4 | **Python** | GraalPython (GraalVM polyglot) | Low — polyglot interop | Data processing, scientific computing |
| 5 | **Lua** | LuaJ | Medium — custom bindings | Game scripting, lightweight configuration |
| 6 | **Ruby** | JRuby | Low — JVM-native | Text processing, DSLs |

Groovy is first because it has zero impedance with the host. A Groovy script can call `item.iid()`, construct a `Relation`, or invoke `librarian.get()` as naturally as Java code. No marshaling, no binding layer, no FFI — it runs on the same JVM and sees the same classpath.

GraalVM polyglot (JavaScript, Python) provides a middle ground: sandboxed by default with controlled host access, but capable of interacting with CG objects through the polyglot API.

WASM (via Chicory) is the distributed code story. A WASM module is a portable binary — compiled from Rust, C, Go, or any language with a WASM target. It runs in a pure-Java interpreter with no JNI, no native dependencies, and hard resource limits. This is what makes "fetch code from the graph and run it safely" practical.

### Engine Lifecycle

Engines are initialized lazily on first use. The Librarian does not start a Python runtime unless someone hands it a Python script. Engine availability depends on classpath — if GraalJS isn't on the classpath, JavaScript scripts fail with a clear error, not a silent fallback.

---

## Vocabulary Integration

Scripts provide verbs through the same mechanism as everything else: **EntryVocabulary contributions** on their ComponentEntry.

### Declaring Verbs

A ScriptComponent declares which verbs it handles through VocabularyContributions on its entry:

```
VocabularyContribution {
    trigger: Sememe(cg.verb:greet)     # "I handle the greet verb"
    target:  Sememe(cg.verb:greet)     # Same sememe — capability registration
    scope:   "/"
}
```

When dispatch reaches this component, the ScriptComponent's source is evaluated with the verb's parameters bound into the script context.

### Script-Defined Verbs

A script can also define entirely new verbs — sememes that don't exist yet in the graph. The script author creates a new sememe (or references an existing one) and registers it as a vocabulary contribution:

```
VocabularyContribution {
    trigger: "analyze"                   # Literal token (becomes a proper noun on this item)
    target:  Sememe(cg.verb:analyze)     # New verb sememe
    scope:   "/"
}
```

From the user's perspective, typing "analyze" into the item's prompt dispatches to the script. The script-backed verb is indistinguishable from a Java-backed verb at the dispatch level.

### Dispatch Flow

```
User types "greet alice"
    |
Token resolution: "greet" → Sememe(cg.verb:greet), "alice" → Item(alice)
    |
Frame assembly: verb=greet, THEME=alice
    |
Vocabulary lookup (inner to outer):
    |
    +-- ScriptComponent has VocabularyContribution for cg.verb:greet
    |
    +-- ScriptVerbInvoker:
        1. Resolve RuntimeEngine from ScriptComponent.language
        2. Build bindings: item, librarian, context, params
        3. Apply sandbox from policy
        4. engine.evaluate(source, bindings, sandbox)
        5. Wrap result as ActionResult
```

The `ScriptVerbInvoker` is the bridge between the vocabulary system and the runtime engine. It implements the same `VerbInvoker` interface as the Java reflection-based invoker — the dispatch layer doesn't know the difference.

### Multiple Scripts, Multiple Verbs

An item can have multiple ScriptComponents, each handling different verbs. Or a single ScriptComponent can handle multiple verbs by registering multiple VocabularyContributions pointing to different entry points:

```
ScriptComponent {
    language: cg:language/groovy
    source: """
        def onGreet(who) { "Hello, ${who.displayToken()}!" }
        def onFarewell(who) { "Goodbye, ${who.displayToken()}!" }
    """
}

Entry vocabulary:
    { trigger: Sememe(cg.verb:greet),    entryPoint: "onGreet" }
    { trigger: Sememe(cg.verb:farewell), entryPoint: "onFarewell" }
```

---

## Sandboxing and Trust

Code from the graph is untrusted by default. The sandboxing model has three layers: runtime isolation, policy gating, and trust evaluation.

### Runtime Isolation

Each runtime engine provides its own sandboxing mechanism:

**GraalVM polyglot** (JavaScript, Python): The `Context` object controls everything:
- `allowIO(false)` — no filesystem access
- `allowHostAccess(HostAccess.NONE)` — no calling Java classes
- `allowCreateThread(false)` — no concurrency
- `allowNativeAccess(false)` — no JNI
- Host access is opt-in per class/method through a `HostAccess` policy

**Chicory (WASM)**: Pure computation by default. WASM modules have no ambient capabilities — they can only call functions explicitly provided as imports. The host controls every system call.

**Groovy**: The most permissive by default (same classpath as the host). Sandboxed via `CompilerConfiguration` with a `SecureASTCustomizer` that restricts allowed language features, imports, and method calls. Scripts from untrusted sources get a restricted configuration; scripts from the local user or highly trusted signers can get broader access.

**LuaJ**: Restricted standard library. Remove `os`, `io`, `debug` modules for untrusted scripts.

### Policy Gating

Before a script executes, the Librarian checks the item's `PolicySet`:

```
ScriptPolicy {
    allowedLanguages:  [ItemID]         # Which runtime languages are permitted
    maxCpuMs:          integer          # CPU time limit per invocation
    maxMemoryBytes:    integer          # Heap limit per invocation
    maxOutputBytes:    integer          # Output size limit
    hostAccess:        HostAccessLevel  # NONE, READONLY, RESTRICTED, FULL
    networkAccess:     boolean          # Can the script make network calls?
    allowedBindings:   [string]         # Which binding sources are permitted
}
```

`HostAccessLevel` controls what CG objects the script can see:

| Level | Description |
|-------|-------------|
| **NONE** | Pure computation only — no access to items, librarian, or graph |
| **READONLY** | Can read item state, query relations, but cannot modify anything |
| **RESTRICTED** | Can read and write through a controlled API surface (no raw store access) |
| **FULL** | Unrestricted access (local scripts, highly trusted signers only) |

### Trust Evaluation

The decision to run a script combines policy with trust:

```
1. Who signed this item (and its ScriptComponent)?
2. What is their trust score in my matrix? (see trust.md)
3. Does the item's PolicySet permit this script language?
4. Does the Librarian's own policy permit this trust level to run code?
5. Intersect: script gets the MINIMUM of all applicable limits
```

A script signed by the local user with full trust runs with broad permissions. A script from a stranger two hops away in the social graph runs with NONE host access and tight resource limits — or doesn't run at all, depending on policy.

This replaces the app store model. Instead of a central authority deciding what code is safe, every Librarian makes its own decision based on who signed the code, how much they trust that signer, and what policies they've configured. Communities can publish recommended trust policies (themselves Items) that members adopt — "trust scripts signed by members of this group" — creating decentralized curation without centralized control.

---

## Resource Metering

Scripts run under metered resource limits. Overages terminate execution immediately.

### Metered Resources

| Resource | Unit | Default Limit | Mechanism |
|----------|------|---------------|-----------|
| **CPU time** | milliseconds | 5,000 ms | GraalVM CPU time limit / thread interrupt |
| **Memory** | bytes | 64 MB | GraalVM heap limit / runtime tracking |
| **Output** | bytes | 1 MB | Stream wrapper with byte counter |
| **Invocations** | count per window | 100/minute | Token bucket on the Librarian |
| **Network** | bytes in+out | 0 (disabled by default) | Proxy layer if enabled |

### Reporting

Resource consumption is recorded per script, per signer, as part of the Librarian's service accounting:

```
ScriptUsageRecord {
    scriptCid:   ContentID      # Which script version ran
    signer:      ItemID         # Who signed it
    cpuMs:       integer        # CPU time consumed
    memoryBytes: integer        # Peak memory
    outputBytes: integer        # Output produced
    timestamp:   instant
    result:      OK | TIMEOUT | OOM | ERROR
}
```

These records feed into the trust matrix's **reciprocity** dimension (see [Trust](trust.md)). Items whose scripts consistently hit resource limits or produce errors erode their signer's service trust. Items whose scripts are lightweight and well-behaved build it.

### Burst and Sustained Limits

The Librarian tracks resource usage at two timescales:

- **Per-invocation**: Hard limits on a single script execution (the table above)
- **Per-window**: Aggregate limits over a rolling time window — prevents a script that stays under per-invocation limits from consuming all resources through rapid repeated invocation

A script that takes 4,999 ms once is fine. A script that takes 4,999 ms one hundred times per minute is not. The token bucket on the Librarian enforces the sustained limit.

---

## Inline Editing

Scripts are editable from both the prompt and the surface UI. This is the "everything is inline editable" principle applied to code.

### From the Prompt

```
alice@myitem> create groovy script
  --> Creates a ScriptComponent, attaches to current item
  --> Opens inline editor (or sets focus to the new script component)

alice@myitem/greeting-script> edit
  --> Opens script source in inline text editor
  --> Changes take effect on commit

alice@myitem/greeting-script> set language to javascript
  --> Changes the runtime language (re-evaluation on next dispatch)
```

The `create` verb handles script creation because ScriptComponent is a component type registered in the vocabulary. "create groovy script" resolves "groovy" to the Groovy language Item and "script" to the ScriptComponent type — standard frame assembly.

### From the Surface

ScriptComponent has a surface schema that renders an inline code editor:

- Source text in a monospace text area (editable when the item is in edit mode)
- Language selector (dropdown of available runtimes)
- Binding declarations (list of name + source pairs, editable)
- Vocabulary contributions (list of verb registrations, editable)
- Run/test button (evaluates the script with current bindings, shows result)
- Resource usage summary (last invocation's metered resources)

The surface is a standard `@Surface` declaration on the ScriptComponent class. The code editor widget is a TextSurface with `editable=true` and a monospace style class. No special-case rendering — the same surface pipeline that renders a document or a chat message renders a script editor.

### Hot Reload

When a script's source changes and the item is committed:

1. The ScriptComponent's new content gets a new CID
2. The item's vocabulary is rebuilt (standard hydration path)
3. Any VocabularyContributions from the script are re-registered
4. The next verb dispatch to this component uses the new source

There is no restart, no reload command, no server bounce. The item commits, the vocabulary rebuilds, and the new code is live. This is the same mechanism that makes any component change take effect on commit — scripts are not special.

### Settings as Configuration

ScriptComponents use the same `ScopedSetting` mechanism as any component entry for runtime configuration:

```
alice@myitem/greeting-script> set greeting-prefix to "Hey"
```

This writes a scoped setting on the ComponentEntry. The script reads it through the SETTING binding source. Settings are editable from both prompt and surface, versionable, and scoped (per-user, per-device, per-context). No config files. No environment variables. Settings on a component entry.

---

## Distributed Code: Code Items

The distributed code story is straightforward: code is content. Content is content-addressed. Content syncs through the graph. Therefore code syncs through the graph.

### Code Items

A **Code Item** is any item that carries ScriptComponents (or WASM modules) intended for others to use. It is not a special type — it is a regular item whose components happen to be executable:

```
Code Item: "CSV Analyzer"
    signer: alice
    components:
        ScriptComponent(groovy):  "def analyze(data) { ... }"
        ScriptComponent(wasm):    <compiled WASM binary>
    vocabulary:
        cg.verb:analyze → ScriptComponent(groovy)
    relations:
        IMPLEMENTED_IN → cg:language/groovy
        IMPLEMENTED_IN → cg:language/wasm
        PROVIDES_VERB  → cg.verb:analyze
```

### Discovery

Code Items are discoverable through normal graph queries:

```
alice@session> search items that provide analyze
    --> Queries: ? → PROVIDES_VERB → cg.verb:analyze
    --> Returns items whose ScriptComponents handle the analyze verb
```

The `PROVIDES_VERB` and `IMPLEMENTED_IN` predicates are indexed by the LibraryIndex like any other relation. Discovery fans out through the social graph — local first, then peers, then further (see [Network Architecture](network.md)).

### Fetch and Verify

When a Librarian encounters a Code Item from the graph:

```
1. Receive manifest (signed)
2. Verify signature against signer's public key
3. Check signer's trust score in local trust matrix
4. If trust >= threshold: fetch content (ScriptComponents)
5. Verify content hashes against manifest CIDs
6. Apply policy: which languages, what resource limits
7. Hydrate item — scripts become available for dispatch
```

Content-addressing guarantees integrity. The CID in the manifest is the hash of the script source. If a single byte changes, the CID changes, the VID changes, and the signature no longer verifies. Tampered code is cryptographically impossible — not just against policy, but against the data model.

### WASM as the Portable Format

WASM is the natural format for distributed code:

- **Language-agnostic**: Compiled from Rust, C, Go, AssemblyScript, or any WASM-targeting language
- **Sandboxed by design**: No ambient capabilities, no filesystem, no network — only explicit imports
- **Deterministic**: Same input produces same output (critical for verification)
- **Compact**: Binary format, content-addressed, deduplicatable
- **Checkable**: WASM validation is fast — a Librarian can verify module safety before execution

A Code Item can carry both a human-readable source (Groovy, JavaScript) and a compiled WASM binary. The source is for inspection and trust evaluation. The WASM binary is for execution on hosts that don't have the source language's runtime.

### Multi-Language Fallback

A Code Item can provide the same verb in multiple languages:

```
VocabularyContributions:
    { trigger: cg.verb:analyze, target: cg.verb:analyze, component: "analyzer-groovy" }
    { trigger: cg.verb:analyze, target: cg.verb:analyze, component: "analyzer-wasm" }
```

The Librarian selects the best available engine: if Groovy is available, use the Groovy source (faster, better interop). If not, fall back to the WASM binary (universal, but slower). The user doesn't see the difference — they type "analyze" and get a result.

---

## Runtimes as Items

Runtime engines are themselves Items in the graph. This closes the self-hosting loop: the system that runs code is itself managed by the system.

### Runtime Items

A runtime engine Item carries:

```
Runtime Item: "GraalJS Runtime"
    type: cg:type/runtime
    components:
        RuntimeMetadata:
            engineClass:    "dev.everydaythings.graph.scripting.GraalJsEngine"
            languageId:     cg:language/javascript
            version:        "24.1.0"
            capabilities:   [SANDBOX, POLYGLOT, COMPILE]
        ScriptPolicy:       # Default policy for scripts using this runtime
            maxCpuMs: 5000
            hostAccess: READONLY
    relations:
        IMPLEMENTS_LANGUAGE → cg:language/javascript
        REQUIRES_CLASSPATH  → "org.graalvm.js:js:24.1.0"
```

### Discovery and Installation

When a Librarian encounters a Code Item that requires a runtime it doesn't have:

```
1. Script language = cg:language/python
2. RuntimeRegistry has no engine for cg:language/python
3. Query graph: ? → IMPLEMENTS_LANGUAGE → cg:language/python
4. Find Runtime Item "GraalPython Runtime" from a trusted peer
5. Verify signer trust (runtime installation requires HIGH trust)
6. Fetch runtime metadata
7. If classpath dependency is available: register engine
8. If not: report missing dependency to user
```

Runtime installation is a high-trust operation — higher than running a script. The Librarian's policy can require explicit user approval for new runtimes, or it can auto-install from signers above a trust threshold.

### Capability Negotiation

When a Code Item arrives from the graph, the Librarian checks whether it can run:

```
Code Item requires: cg:language/wasm
Librarian has:      cg:language/groovy, cg:language/javascript, cg:language/wasm
    --> Can run. Use WASM engine.

Code Item requires: cg:language/python
Librarian has:      cg:language/groovy, cg:language/javascript
    --> Cannot run. Check for WASM fallback in the Code Item.
    --> If no fallback: report to user, query graph for Python runtime.
```

---

## Implementation Roadmap

### Phase 1: ScriptComponent + Groovy

The minimum viable scripting layer.

- Define `ScriptComponent` class (`@Type`, `Canonical`, `Component`)
- Define `RuntimeEngine` interface and `GroovyEngine` implementation
- Define `RuntimeRegistry` on Librarian
- Wire `ScriptVerbInvoker` into the dispatch pipeline
- Groovy scripts can define and handle verbs on items
- Basic sandbox via `SecureASTCustomizer`
- `create groovy script` works from the prompt

### Phase 2: GraalVM Polyglot + Sandboxing

Full sandboxing and JavaScript support.

- `GraalJsEngine` implementation using GraalVM polyglot `Context`
- `SandboxConfig` from `PolicySet` → GraalVM `Context.Builder` options
- Host access control (NONE/READONLY/RESTRICTED/FULL)
- CPU and memory limits via GraalVM resource limits
- `ScriptPolicy` on ComponentEntry and PolicySet

### Phase 3: Inline Editing + Surface

The live editing experience.

- `ScriptComponent` surface schema (code editor, language selector, bindings, test button)
- Prompt verbs: `create <language> script`, `edit`, `set language to`
- Hot reload on commit (vocabulary rebuild)
- Settings-based configuration for scripts
- Resource usage display in surface

### Phase 4: WASM via Chicory

The distributed code foundation.

- `ChicoryEngine` implementation (pure Java WASM interpreter)
- WASM module validation on fetch
- Import/export binding between WASM and CG objects
- Multi-language fallback (prefer native engine, fall back to WASM)
- Content-addressed WASM modules as component content

### Phase 5: Distributed Code + Trust Gating

Code Items flowing through the graph.

- `PROVIDES_VERB`, `IMPLEMENTED_IN`, `REQUIRES_RUNTIME` predicates
- Trust-gated script execution (trust matrix check before evaluation)
- Resource metering and usage records
- `ScriptUsageRecord` feeding into trust reciprocity
- Discovery queries for Code Items

### Phase 6: Runtimes as Items

Self-hosting runtimes.

- `RuntimeMetadata` component type
- Runtime discovery and installation through graph
- Capability negotiation (can this Librarian run this code?)
- Additional engines: GraalPython, LuaJ, JRuby
- Runtime version management through item versioning

### Dependency Graph

```
Phase 1 (ScriptComponent + Groovy)
    |
    +--→ Phase 2 (GraalVM + Sandboxing)
    |        |
    |        +--→ Phase 3 (Inline Editing)
    |
    +--→ Phase 4 (WASM via Chicory)
              |
              +--→ Phase 5 (Distributed Code)
                       |
                       +--→ Phase 6 (Runtimes as Items)
```

Phases 2 and 4 can proceed in parallel after Phase 1. Phase 3 depends on Phase 2 (needs sandboxing for the test button). Phase 5 depends on Phase 4 (WASM is the portable format for distribution). Phase 6 depends on Phase 5.

---

## Examples

### Create a Groovy Verb

```
alice@project> create groovy script
  --> ScriptComponent added to project item at handle "script-1"

alice@project/script-1> edit
  --> Opens inline editor

  def onSummarize(item) {
      def components = item.componentTable().entries()
      return "Item has ${components.size()} components: " +
             components.collect { it.handle().displayName() }.join(", ")
  }

alice@project/script-1> commit
  --> Script saved, vocabulary rebuilt

alice@project> summarize
  --> "Item has 4 components: readme, config, script-1, roster"
```

The verb "summarize" was registered as a VocabularyContribution when the script was committed. It dispatches to `onSummarize` through the standard pipeline.

### Edit a Script Inline

```
alice@project/script-1> edit
  --> Source opens in text widget

  # Change the greeting
  def onSummarize(item) {
      def n = item.componentTable().entries().size()
      return "This item contains ${n} component${n == 1 ? '' : 's'}."
  }

alice@project/script-1> commit
  --> New CID, new VID, vocabulary rebuilt

alice@project> summarize
  --> "This item contains 4 components."
```

No restart. No deploy. Edit, commit, dispatch.

### Distribute a WASM Module

```
# Bob publishes a markdown-to-html converter as a Code Item

bob@converter> describe
  Type: cg:type/item
  Signer: bob
  Components:
      converter.wasm    (WASM binary, 48 KB)
      converter.rs      (Rust source, for inspection)
  Vocabulary:
      convert: ScriptComponent(wasm)
  Relations:
      PROVIDES_VERB → cg.verb:convert
      IMPLEMENTED_IN → cg:language/wasm
      IMPLEMENTED_IN → cg:language/rust

# Alice discovers it through the graph

alice@session> search items that provide convert
  --> Found: "Markdown Converter" by bob (trust: 0.74)

alice@session> view markdown converter
  --> Fetches manifest, verifies bob's signature
  --> Trust 0.74 >= alice's threshold 0.5 for READONLY scripts
  --> Hydrates item, WASM module available

alice@notes> convert readme to html
  --> Dispatches convert verb
  --> Chicory engine loads converter.wasm
  --> Sandbox: no IO, no host access, 5s CPU limit, 64MB memory
  --> Returns HTML string
```

Alice never installed anything. She never approved a permissions dialog. Her trust policy and bob's reputation made the decision. If bob's trust score drops below her threshold, the converter stops working — automatically, without intervention.

---

## References

**Internal:**
- [Vocabulary](vocabulary.md) — Token resolution, dispatch, expression input
- [Components](components.md) — Component system, EntryVocabulary, lifecycle
- [Trust](trust.md) — Trust matrix, policy, signer verification
- [Network Architecture](network.md) — Discovery, routing, content distribution
- [Library](library.md) — Storage, content addressing

**Runtime engines:**
- [Groovy](https://groovy-lang.org/) — JVM scripting with zero-impedance Java interop
- [GraalVM Polyglot](https://www.graalvm.org/latest/reference-manual/polyglot-programming/) — Multi-language runtime with built-in sandboxing
- [Chicory](https://github.com/nicktomlin/chicory) — Pure Java WebAssembly interpreter
- [LuaJ](https://github.com/luaj/luaj) — Lua 5.3 for Java
- [JRuby](https://www.jruby.org/) — Ruby on the JVM

**Foundations:**
- [Miller 2006 — Robust Composition](references/Miller%202006%20-%20Robust%20Composition.pdf) — Capability-based security (the theoretical basis for sandboxing through explicit capabilities rather than ambient authority)
- [Dennis, Van Horn 1966 — Programming Semantics for Multiprogrammed Computations](references/Dennis%2C%20Van%20Horn%201966%20-%20Programming%20Semantics%20for%20Multiprogrammed%20Computations.pdf) — The origin of capability-based addressing
- [Szabo 1997 — Formalizing and Securing Relationships on Public Networks](references/Szabo%201997%20-%20Formalizing%20and%20Securing%20Relationships%20on%20Public%20Networks.pdf) — Smart contracts as formalized relationships (the pattern Code Items follow)
- [Kay 1993 — The Early History of Smalltalk](references/Kay%201993%20-%20The%20Early%20History%20of%20Smalltalk.pdf) — Objects that carry their own behavior (the intellectual ancestor of items carrying code)
