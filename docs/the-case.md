# Below the Application, Above the Bytes: The Case for a Semantic Base Layer

**Joshua Chambers**
*March 2026*

---

## Abstract

Every layer of the computing stack stores data as opaque bytes, identified by location, interpreted only by the application that created it. No layer of infrastructure can answer the most basic question about any piece of data: *what does it mean?* This paper argues that the absence of a shared semantic layer is a foundational deficiency, not merely an inconvenience, and that decades of attempts to fix it by annotating existing layers have failed for structural reasons. I propose a new base layer built on a semantic primitive: the *frame*, a predicate-role structure drawn from Fillmore's frame semantics, grounded in the empirically validated semantic structures of WordNet, the Collaborative Interlingual Index (CILI), VerbNet, and ISO 24617-4. Together, frames and a shared meaning vocabulary produce a base layer where data is self-describing, queryable by meaning, and interoperable across languages, applications, and systems, without crawling, probabilistic parsing, or centralized indexing.

---

## 1. The Semantic Void

In 1945, Vannevar Bush described the central problem of information management: "The summation of human experience is being expanded at a prodigious rate, and the means we use for threading through the consequent maze to the momentarily important item is the same as was used in the days of square-rigged ships" (Bush, 1945). Eighty years later, the maze is incomparably larger, and the threading, while faster, is no less indirect.

A user saves a photograph. The filesystem records bytes at a path. The OS knows the file's size, its modification time, its location on disk. The image format encodes pixels and maybe some EXIF metadata: camera model, GPS coordinates, exposure settings. But no layer of the stack knows what the photograph *is*. Who is in it, what occasion it documents, how it relates to other photographs or to the people depicted. That knowledge exists only in the user's head, or in the proprietary database of whichever application the user happens to use.

This is a structural property of every layer in the stack:

- **The filesystem** sees bytes at paths. Whether those bytes are a novel, a spreadsheet, or a genome sequence is invisible to the layer that stores them.

- **The operating system** sees processes, file descriptors, and memory pages. It cannot distinguish a medical record from a restaurant menu.

- **The network layer** sees packets with source and destination addresses. HTTP adds content-type headers, which identify *format* (text/html, application/json), never *meaning*.

- **The database** sees rows and columns, or documents and collections. The schema is local to the application. Two databases storing information about the same person share no vocabulary for describing what they hold.

- **The web** sees pages at URLs. Search engines exist precisely because the web cannot answer "what is this about?" Third parties crawl billions of pages, guess at meaning from word frequency and link structure, and sell access to their proprietary guesses.

Each layer was designed for generality, and each achieves it by the same strategy: treat data as opaque and leave interpretation to the layer above. A defensible engineering choice. But the cumulative consequence is that meaning has no home in the architecture. Every application that needs to do anything with meaning must build its own semantic layer from scratch: its own schema, its own vocabulary, its own query logic, its own integration adapters.

The cost is immense but invisible, because it is pervasive. Every API integration is a bespoke translation between two systems that cannot describe their own contents to each other. Every search engine is a probabilistic compensation for the fact that data doesn't know what it means. Every data migration is an exercise in reconstructing meaning that was present in the creator's mind but was never captured by the infrastructure.

The key-value pair is perhaps the most fundamental pattern in computing. Configuration files, HTTP headers, database rows, JSON objects, environment variables. Yet because keys are application-defined strings, they are fractured beyond repair. One system's `author` is another's `creator`, another's `created_by`, another's `dc:creator`, another's `writtenBy`. They all mean the same thing. No layer of infrastructure knows this.

What is missing is not a better search engine or a smarter parser. What is missing is a *layer* where meaning is the fundamental unit of storage, identity, and retrieval.

---

## 2. Why It's Missing

The absence of a semantic layer reflects the world in which computing was built and the world in which it grew up.

When the foundational layers were laid down in the 1970s, nodes were disconnected and bytes were precious. Engineers were writing assembly write bits to disk. The byte-stream abstraction (everything is a file, a file is a sequence of bytes) was a practical triumph given the constraints. TCP/IP, HTTP, SQL: each subsequent layer solved the problem in front of it with the resources available. A semantic data model was not rejected; it was beyond the horizon.

**The object problem.** A semantic key cannot be a string. "Author," "creator," "created_by," and "writtenBy" are four labels for the same meaning, and nothing connects them. A semantic key must refer to a *meaning*: a stable, language-independent concept with a defined identity, a place in a hierarchy, cross-lingual equivalents, and participation in structured scenes. Building those objects requires decades of empirical research into how meaning is structured across human languages. The resources that make it tractable (WordNet's 120,000+ meanings, the CILI linking those meanings across languages, FrameNet's 1,200+ structured scenes, VerbNet's universal role inventory, and the cross-walks connecting them) are products of computational linguistics research that has only recently reached the maturity needed to serve as a practical foundation.

**Fragmentation.** Even as these linguistic resources matured, the software industry developed in a direction hostile to the kind of collaboration a semantic layer demands. A shared vocabulary of meaning is, by definition, a collective project. But the commercial landscape of the 1990s and 2000s was defined by proprietary lock-in. Every major platform held its data models close, because controlling the data model meant controlling the ecosystem. Interoperability was a competitive threat. The kind of cross-organizational collaboration a shared semantic foundation requires was antithetical to the incentive structure.

**What changed.** Three things converged. First, the computational linguistics infrastructure matured. The object problem became tractable. Second, global interconnection made a shared vocabulary both necessary and viable: the network that makes the problem acute is the same network that makes the solution practical. Third, the open source movement created the collaborative environment a semantic layer requires. The linguistic databases (WordNet, CILI, FrameNet, VerbNet) carry permissive licenses. The entire implementation stack can be built on open foundations. A semantic base layer is inherently a commons. It only works if it is shared, and it can only be shared if it is open. That commons is now possible in a way it was not during the era of proprietary platform wars.

---

## 3. Why Retrofitting Fails

The need for a semantic layer has been recognized for decades, and there have been serious, well-funded attempts to provide one. Each contributed valuable ideas. None became foundational.

**The Semantic Web** is the most ambitious attempt. Berners-Lee's 2001 vision described a web in which "information is given well-defined meaning, better enabling computers and people to work in cooperation" (Berners-Lee et al., 2001). The technical realization (RDF triples, OWL ontologies, SPARQL queries) is rigorous and powerful. Twenty-five years later, RDF is widely used in specialized domains (biomedical ontologies, library science, government data) but has not become a general-purpose semantic layer. The web remains overwhelmingly opaque bytes at URLs.

RDF's genuine strengths are substantial: a universal graph model, formal inference via RDFS and OWL entailment, a powerful query language in SPARQL. In specialized domains where those capabilities matter, RDF has proven its value. But three structural problems kept it from becoming general-purpose.

First, RDF annotates existing resources. It is layered *on top of* the web, not *built into* it. A web page can exist without any RDF. Most do. The semantic annotation is optional, which means it is absent in the vast majority of cases. The cost of creating semantic metadata falls on the producer while the benefit accrues to the consumer: a classic misaligned-incentive problem.

Second, RDF requires the author to commit to an ontology (Gruber, 1993). In practice, choosing and using an ontology correctly is hard. It requires expertise that most content creators do not have and are not motivated to acquire. The Semantic Web effectively asks every web author to be a knowledge engineer.

Third, the annotation is disconnected from the content. The RDF description of a web page is a separate artifact from the page itself. It can become stale, incorrect, or inconsistent without any mechanism to detect the divergence.

**Schema.org** addressed some of these problems by providing a single vocabulary backed by major search engines. Its adoption is broader than RDF/OWL, precisely because it is simpler and because search engines provide a direct incentive (better rankings) for using it. But Schema.org remains a metadata annotation: a sprinkle of JSON-LD in an HTML header. It describes pages *about* things, not the things themselves.

**Dublin Core**, **EXIF**, **ID3 tags**, **OpenGraph**, and dozens of other metadata standards each solve a narrow problem. They do not compose. A photograph with EXIF data and a document with Dublin Core metadata cannot be queried together because they share no vocabulary, no addressing scheme, and no common notion of what "subject" or "creator" means.

The pattern across all of these efforts reveals a structural lesson: **you cannot make a semantically inert layer semantic by annotating it.** The annotation is always optional, always disconnected from the content, always maintained by a different process, and always expressed in a vocabulary local to one standard or domain. The layer itself remains opaque.

The solution must be a *layer* where creating data *is* creating semantic structure, because the two are not separate operations.

---

## 4. What "Semantic" Actually Requires

If a semantic base layer cannot be achieved by annotating existing layers, what must it look like?

### Grounded predicates, not strings

A semantic layer requires keys that carry *meaning*, not labels. The key must refer to a concept, not a string, and that concept must be shared across systems, applications, and languages.

Gruber (1993) argued that shared vocabularies are essential for knowledge sharing among systems. The Semantic Web pursued this through URI-identified predicates. But URIs are locations, not meanings. They are globally unique, but they do not carry semantic content intrinsically. Two different URIs can denote the same concept (schema.org/author vs. Dublin Core's dc:creator), and nothing in the infrastructure connects them.

What we need are keys that refer to *meanings*: language-independent, application-independent units of semantic content with stable identities. Computational linguistics provides exactly such units. More on what those units are in section 7. For now, the requirement: keys must be grounded meanings, not strings.

### Structured assertions

A flat key-value pair (`author: Tolkien`) captures a single relationship but loses the structure that gives it meaning. Who is asserting this? About what? In what capacity?

Fillmore's frame semantics (1968; 1982) provides the theoretical foundation. Fillmore observed that understanding a word like "buy" requires understanding an entire *scene*: a buyer, a seller, goods, money, a transaction. A frame, in Fillmore's sense, is "any system of concepts related in such a way that to understand any one of them you have to understand the whole structure in which it fits" (Fillmore, 1982). The participants (buyer, seller, goods, money) are not arbitrary attributes but *thematic roles*: semantic functions catalogued and standardized across decades of research.

The frame's power is connective. "I eat an apple." Three concepts (a person, an action, a fruit) that in isolation are unrelated. The frame connects them: the person is the Agent (performing the action), the apple is the Patient (being affected), and the eating is the predicate that defines how they relate. Without the frame, three separate concepts. With it, a coherent assertion.

A flat key-value pair fails not just because the key is a string, but because it has no structure to express the *kind* of relationship, the *participants* and their roles, or the *context* in which the assertion holds. What we need is the frame pattern: a **predicate** that defines a structured assertion, and **role bindings** that fill its slots with values.

**FrameNet** (Baker, Fillmore, & Lowe, 1998), developed at Berkeley as the direct computational realization of Fillmore's theory, defines over 1,200 semantic frames, each with frame-specific roles. Its Commerce_buy frame defines Buyer, Seller, Goods, Money. Its Authorship frame defines Author and Work.

**VerbNet** (Palmer, Gildea, & Kingsbury, 2005) takes a complementary approach, organizing ~300 verb classes by shared syntactic and semantic behavior and mapping FrameNet's frame-specific roles to a smaller set of universal thematic roles (Agent, Theme, Goal) standardized by ISO 24617-4. Both resources are linked to WordNet synsets, and **SemLink** (Bonial et al.) provides cross-walks between them.

The role vocabulary comes from Fillmore's original case roles (1968), refined over decades into the ~25 thematic roles standardized by VerbNet and ISO 24617-4:

- **Core participant roles**: Agent (intentional initiator), Patient (affected entity), Theme (existing/located entity), Experiencer (perceiver), Cause (non-intentional initiator)
- **Directional roles**: Goal (endpoint), Source (origin), Destination (physical endpoint), Path (route)
- **Transfer roles**: Recipient (receiver), Beneficiary (one who benefits), Partner (co-participant)
- **Manner roles**: Instrument (tool), Manner (how), Extent (degree), Purpose (intended outcome)
- **Setting roles**: Location (where), Time (when)
- **Information roles**: Topic (subject of communication), Name (designation)

This inventory is not arbitrary and it is not infinite. It reflects empirical findings about how human languages structure meaning. Every language studied, from English to Lakhota to Japanese, uses the same core set of semantic functions to describe who did what to whom, where, when, how, and why (Youn et al., 2016). The roles are universal; the words that express them vary.

### Write-time resolution

This is the core inversion. Every existing system stores data first and tries to determine its meaning later. Search engines crawl. NLP systems annotate after the fact. Data integration pipelines map between schemas post-hoc. All of these are attempts to recover meaning that was present in the creator's mind but never captured in the data.

A semantic base layer inverts this. Meaning is resolved *at the moment of creation*, when it is trivially easy, because the creator knows what they mean. The disambiguation that search engines and NLP pipelines struggle to perform after the fact is effortless at write time. When a user creates a relationship between a person and a book, they know whether they mean "authored," "edited," "reviewed," or "purchased." If the system captures that distinction as a grounded semantic predicate at creation time, no subsequent system ever needs to guess.

The predicate, once chosen, tells the system what roles to expect. The system prompts for them, offers completions, validates inputs. The act of creating data *is* the act of resolving meaning, because selecting a predicate and filling its roles is inherently a semantic operation.

This is not natural language understanding. The system does not parse free text and try to extract meaning. It structures the input environment so that meaning is captured as a natural consequence of creation. The user selects a predicate, fills roles, and the result is a grounded semantic structure. The hardest problem in NLP (disambiguation) is trivially solved by the person who knows what they mean.

### Cross-lingual stability

A semantic layer that works only in English is an English-language metadata standard, not a semantic layer. The concept that English speakers call "dog," Spanish speakers call "perro," and Japanese speakers call "inu" is the same concept. A semantic layer must represent meanings independently of the words that express them.

This requires a clean separation between *meanings* and *words*. Meanings (which I will call sememes, following usage in structural semantics) are language-neutral units with stable identities. Words are language-specific expressions that point to meanings. The predicate AUTHORED exists independently of the English "authored," the Spanish "escrito," or the German "verfasst." Each word, in its language, points to the same meaning.

---

## 5. The Frame as Primitive

I propose the semantic frame as the fundamental primitive. Not the frame as Fillmore defined it for linguistic analysis, but the frame *repurposed*: extended from a tool for understanding language into a tool for structuring data.

A semantic frame, in this usage, is:

```
Frame {
    predicate:  a grounded meaning    (what kind of assertion)
    bindings:   role-value pairs      (the semantic content)
}
```

A predicate and its role bindings. Nothing else is structurally required. Every element of the frame (what it asserts, what it's about, who is involved, what content it carries) is expressed as a role binding on the predicate.

A **title assertion**: predicate TITLE, bindings (THEME) = the-book, (NAME) = "The Hobbit". The predicate TITLE defines two roles: what is being titled and what the title is.

A **chess move**: predicate MOVE, bindings (LOCATION) = the-game, (AGENT) = Fischer, (THEME) = king-pawn, (SOURCE) = e2, (GOAL) = e4. Location (which game), Agent (who moved), Theme (what piece), Source (from where), Goal (to where). A single move is a single semantic assertion.

A **video**: predicate VIDEO, bindings (THEME) = the-movie, (VIDEO, MKV, UHD) = master-file, (VIDEO, MKV, HD) = transcode. Content in different formats expressed through compound role keys.

An **authorship assertion**: predicate AUTHORED, bindings (THEME) = The Hobbit, (AGENT) = Tolkien.

These are all structurally identical: a predicate and role bindings with compound semantic keys. The predicate determines what roles the frame expects. The roles determine what the values mean.

### Two levels of role

The ~25 universal thematic roles (Agent, Theme, Goal) are powerful because they are universal, but they are also general. In a chess game, both Fischer and Spassky are Agents. Calling them both "Agent" is correct but insufficient. We need to say they are *players*, and that one plays white and the other black.

FrameNet and VerbNet resolve this tension differently. FrameNet defines frame-specific elements: the Commerce_buy frame has Buyer, Seller, Goods, Money. VerbNet maps these back to universal roles: Buyer maps to Agent, Goods maps to Theme. Both levels are useful. The universal level enables cross-frame queries ("all frames where someone is an Agent"). The frame-specific level enables precision ("all frames where someone is a Buyer").

The semantic base layer needs both, connected through the vocabulary's inheritance hierarchy. PLAYER, BUYER, and AUTHOR are all meanings in the shared vocabulary, each a *specialization* of the universal role AGENT. This relationship is expressed in the system's own terms: HYPERNYM { (THEME) = PLAYER, (GOAL) = AGENT }. PLAYER is a kind of AGENT. The vocabulary describes itself with the same primitives it uses to describe everything else.

So a PLAYER frame on a chess game uses the PLAYER role, not the generic Agent, because PLAYER carries the additional meaning the context needs. But because PLAYER inherits from AGENT, any query at the universal level still works: "all frames where Fischer is an AGENT" finds chess games, authorship assertions, and anything else where Fischer acts intentionally.

### Compound keys

Roles can be further qualified through **compound keys**: sequences of meanings that together identify what a binding *is*. A movie might have (VIDEO, MKV, UHD) and (VIDEO, MKV, HD). A document might have (GLOSS, ENGLISH) and (GLOSS, SPANISH). The compound key is a sequence of meanings (role plus qualifiers) that together specify the binding with arbitrary precision.

Every element of a compound key is a grounded meaning. VIDEO is not a MIME type prefix; it is the meaning "moving visual content." MKV is not a file extension; it is the meaning "Matroska multimedia container format." ENGLISH is not a locale string; it is the sememe for the English language.

And every meaning in a compound key is an *opportunity for indexing*. If the system indexes frames by the meanings in their binding keys, then "show me all videos" is a simple index lookup on VIDEO. "Show me all UHD videos" narrows to frames whose keys include both VIDEO and UHD. "All MKV content" finds every frame with MKV in its key. The compound key is a multi-dimensional index built from the vocabulary itself. No separate tagging system, no search facets, no metadata catalog. The key *is* the index.

### Everything is a role binding

There is no fundamental distinction between "the data" and "the metadata" of a frame. A title's text, a video's master file, a chess move's destination square, a document's author: each is a role binding. Provenance is a binding. Signatures are bindings. Timestamps are bindings. What we call "data" is a value filling a role. What we call "metadata" is also a value filling a role. The distinction is conventional, not structural.

### Predicates carry behavior

The predicate is not merely a data template. It is a behavioral specification. A predicate can declare how it participates in *parsing* (what syntax it expects, what roles it fills from context) and how it *evaluates* (what computation it performs with filled bindings).

The operator `+` is a meaning in the shared vocabulary, the same kind of object as "author" or "create." As a meaning, it carries properties: it is infix, it has a precedence, it associates left-to-right. These are not grammar rules maintained by a parser. They are data on the predicate. The parser reads them. There is no separate precedence table. There is no grammar.

This extends to structural symbols. Parentheses are meanings that declare "I open a group" and "I close a group." There is no reserved syntax. Everything (verbs, operators, functions, parentheses, commas) resolves through the shared vocabulary. Syntax is vocabulary.

Any domain can bring its own notation. Chess algebraic notation ("e4," "Nf3," "O-O") is a set of meanings with their own parsing declarations. A regular expression is a set of meanings with their own parsing declarations. They are predicates that declare how they parse, resolved through the same mechanism as arithmetic operators or English prepositions. The frame primitive reaches all the way into how input is interpreted.

---

## 6. Coherence: What Frames Cohere Around

Frames are the primitive. But a single frame is rarely the whole story. A book is a TITLE frame ("The Hobbit"), an AUTHORED frame (Tolkien), TEXT frames (the chapters), a COVER_ART frame, a PUBLICATION frame (1937, Allen & Unwin), and more. Each frame is a separate assertion with its own predicate and bindings. But they are all *about the same thing*. They only make sense together.

If frames can be about the same thing, they need a shared identity to point to. That identity, and the collection of frames that describe it, I call an **item**.

An item is not a new primitive in the way a frame is. It is an architectural choice: a stable, cryptographic identity that frames can reference to indicate "I am about *this thing*." The book is an item. Tolkien is an item. A chess game is an item. Each exists as an identity around which frames accumulate, building up a coherent, multi-faceted description. The role that carries this reference depends on the predicate: THEME for an authorship assertion (the work being described), LOCATION for a chess move (the game where it happens).

Entity identity uses content-addressed cryptography: an item's identity is a hash derived from its defining characteristics, making it stable, verifiable, and independent of any central registry. Identity is not assigned by an authority but established by the convergence of content. This choice has consequences, and those consequences are load-bearing parts of the architecture.

A chess game makes the pattern vivid. The game is an item with a type. But it is not a monolithic structure. It is an accumulation of signed frames.

Players register by signing their own PLAYER frames: `PLAYER { (AGENT) = Fischer, (ROLE) = WHITE }` signed by Fischer; `PLAYER { (AGENT) = Spassky, (ROLE) = BLACK }` signed by Spassky. Each player attests their own participation. It is not assigned by a third party; it is declared by the participant and carries their signature.

Then moves: `MOVE { (LOCATION) = the-game, (AGENT) = Fischer, (THEME) = king-pawn, (SOURCE) = e2, (GOAL) = e4 }` signed by Fischer. Each move is independently meaningful, independently signed, independently verifiable. The game is the ordered sequence of these signed assertions, all cohering around the same item identity.

No special game engine data structure is needed. Each move is a frame, the same primitive as a title or a video. And because each move is a frame, it is queryable. "All games where someone opened with pawn to e4" is an index lookup on MOVE frames with (GOAL) = e4. "All games Fischer played" is a lookup on PLAYER frames with (AGENT) = Fischer. "Fischer's longest game" is a count of MOVE frames per game item where Fischer has a PLAYER frame.

This generalizes immediately. A chat room is an item where people join with signed MEMBERSHIP frames and contribute with signed MESSAGE frames. A key log is an item with KEY frames, REVOKE frames, and DELEGATE frames. An auction is an item where bidders assert signed BID frames. All the same pattern: an item exists, people make signed assertions on it, and those assertions collectively define what it is.

And the architecture closes a circle: even sememes themselves (the units of meaning in the shared vocabulary) are items. The sememe METER carries a GLOSS frame in English ("the base unit of length in the metric system"), a GLOSS in Spanish, a DIMENSION frame (LENGTH), CONVERSION frames to other units, a HYPERNYM frame (METER is-a LENGTH_UNIT), and a SYMBOL frame ("m"). The meaning is not a definition string. It is the structured totality of everything asserted about it.

The same holds for every sememe. AUTHOR has glosses, hierarchical relationships, and lexemes in every imported language. A language itself (English, Spanish, Japanese) is an item whose frames include its entire lexicon. The vocabulary lives *in* the graph, as items made of frames, using the same primitives as everything else.

This is where the analogy to files becomes concrete:

| Files | Items |
|---|---|
| Opaque bytes; the system cannot interpret content | Typed frames; the system knows what everything means |
| Named by path in a hierarchy | Discoverable by meaning; exist in a semantic graph, not a tree |
| No built-in authorship, versioning, or integrity | Every frame is signed, content-addressed, and verifiable |
| Metadata is a sidecar (EXIF, xattr, .DS_Store) | Metadata IS frames, first-class and queryable |
| "Relatedness" means same folder or a hyperlink | Typed, signed, indexed, traversable semantic links |
| Application decides how to interpret it | Item carries its own vocabulary and presentation |
| Search by filename or keyword | Query by meaning across the graph |

The item is what replaces the file for the user. Not at the POSIX level (bytes and streams are a fine substrate for low-level I/O) but for user-facing data: the things people create, name, share, organize, search for, and care about. The item is the thing that knows what it means, because it is made of frames, and frames are meaning.

---

## 7. The Shared Meaning Space

A semantic frame is only as useful as the vocabulary it draws from. If every application defines its own predicates and roles, frames reproduce the same fragmentation as string-keyed pairs, just with more structure.

This is an old problem. Gruber (1993) argued that shared ontologies are essential for knowledge sharing. Lenat's CYC (1995) attempted to solve it by hand-encoding millions of common-sense assertions, demonstrating both the importance of shared knowledge and the intractability of creating it manually. The Semantic Web attempted ontology languages (RDF, RDFS, OWL), but the proliferation of competing ontologies became a problem in itself.

I propose a different anchor: the empirically documented structure of human lexical semantics.

### The vocabulary of types, predicates, and roles

**WordNet** (Miller et al., 1993) organizes English into ~120,000 *synsets* (synonym sets representing distinct concepts). Each synset is a meaning, not a word. WordNet provides hierarchical relationships (dog is-a canine is-a mammal), part-whole relationships, antonymy, and other semantic relations.

**CILI** (the Collaborative Interlingual Index; Bond, Vossen, McCrae, & Fellbaum, 2016) extends WordNet across languages. CILI provides language-neutral concept identifiers linking synsets to their equivalents in other languages' wordnets. The English "dog," the Spanish "perro," and the Japanese "inu" share the same CILI identifier. Not a translation; an identity.

Three additional resources provide vocabulary for the frame primitive specifically:

**FrameNet** (Baker, Fillmore, & Lowe, 1998; Ruppenhofer et al., 2006) provides over 1,200 frame definitions with named roles, hierarchical relationships, and annotated examples. It is, in a direct sense, a library of data templates.

**VerbNet** (Palmer, Gildea, & Kingsbury, 2005) organizes ~300 verb classes by shared behavior, mapping FrameNet's frame-specific roles to universal thematic roles. VerbNet entries include WordNet sense keys, bridging concept to role expectations.

**ISO 24617-4** standardizes ~25 thematic roles sufficient for characterizing argument structure across languages. These roles, validated across VerbNet, FrameNet, and PropBank, provide the binding keys that semantic frames need.

Together, three layers:

1. **Types and concepts** (WordNet/CILI): what kinds of things exist (PERSON, BOOK, GAME, LANGUAGE), organized hierarchically and linked across languages
2. **Predicates** (WordNet verb synsets, VerbNet classes): what assertions can be made (AUTHORED, PURCHASED, TITLED), each declaring expected roles
3. **Roles** (VerbNet, ISO 24617-4): what semantic functions participants play (Agent, Theme, Goal, Source, Instrument), the universal binding keys

### The entity problem

The AUTHORED example: predicate AUTHORED, (THEME) = The Hobbit, (AGENT) = Tolkien. AUTHORED is a shared meaning. PERSON is a shared meaning. BOOK is a shared meaning. But what about Tolkien *himself*?

Today, Tolkien exists as a Wikipedia page, an Amazon author page, a Goodreads entry, a TMDB profile, a Library of Congress authority record, a Wikidata entry, and countless other disconnected representations. None is the canonical Tolkien that every system could use as the AGENT binding.

This is the hardest problem the shared meaning space must address. WordNet provides the *concept* PERSON, but not an identity for every specific person. Every previous attempt at scale entity identity has hit the same tension: centralized registries (Wikidata, Library of Congress) are fragile, political, and exclusionary. Fully decentralized naming is ambiguous.

The semantic base layer takes a specific position. Entities are items: collections of frames with cryptographic identities. Tolkien is not a string or a URL or a row in a registry. He is an item carrying frames that assert his name, birth date, works, relationships. These frames are signed by the people and institutions that assert them.

Convergence happens through the social graph. When Alice creates an AUTHORED frame binding a Tolkien entity as AGENT, she binds to a specific cryptographic identity from her trust network. If the Library of Congress publishes a SAME_AS frame linking their authority record to Alice's Tolkien entity, and Bob trusts both, Bob's system resolves them as the same entity. No central registry. Accumulation of signed assertions from trusted parties.

I will not pretend this is a solved problem. It trades the problems of centralized identity (political control, single points of failure) for different problems (convergence latency, conflicting identities). I believe the trade-off is correct for a decentralized semantic layer, but the entity problem remains the area where the architecture is most genuinely unproven.

### Meaning and expression

The architecture separates *meaning* from *expression*: meanings are language-neutral; words belong to specific languages and point to meanings. To "translate" a concept from English to Spanish, look up the English word's meaning, then find the Spanish word for that meaning. Import English and Spanish WordNet (both linked via CILI), and you have a bidirectional dictionary covering 120,000 concepts. Not a feature. A structural consequence of separating meaning from expression.

### An open commons

The shared meaning space is not a closed vocabulary. Domain-specific communities can extend it with their own concepts (medical terminology, legal concepts, engineering standards), connected to the base through the same hierarchical relationships. New languages connect by linking their words to existing meanings. The vocabulary grows from the edges, not from the center: extensible without fragmentation, because every extension is anchored in the shared backbone.

---

## 8. Computation as Frames

The claim that semantic frames constitute a genuine base layer (not merely a metadata system) requires demonstrating expressiveness in domains far removed from natural language. Mathematics is the strongest test case: the most formal, least ambiguous domain of structured knowledge. If thematic roles can describe mathematical operations, they are not linguistic conveniences. They are universal structuring principles.

The mapping turns out to be natural.

### Arithmetic

3 + 5 = 8. The operation ADD is the predicate. The operands are not Agents (they don't initiate anything) or Patients (they don't change). One is the Theme (the entity being operated on) and the other is the Instrument (the means by which the operation is performed). Natural language reveals the asymmetry: we say "add 5 *to* 3," not "add 3 and 5 symmetrically." The answer is a Result (something that comes into existence through the operation).

```
ADD { (THEME) = 3, (INSTRUMENT) = 5, (RESULT) = 8 }
```

Subtraction makes the asymmetry explicit: 10 - 3 = 7. 10 is the Theme (the quantity being diminished). 3 is the Instrument. 7 is the Result.

```
SUBTRACT { (THEME) = 10, (INSTRUMENT) = 3, (RESULT) = 7 }
```

Theme ("the thing being acted on") and Instrument ("by what means") are exactly the semantic functions these values serve. The roles were defined for natural language, but they describe the same cognitive structure.

### Calculus

The definite integral ∫₀¹ x² dx:

```
INTEGRATE { (THEME) = x², (SOURCE) = 0, (GOAL) = 1, (INSTRUMENT) = dx, (RESULT) = ⅓ }
```

Source and Goal for the bounds of integration. These roles were defined for physical motion ("move from the house to the store") but they map onto abstract endpoints with no strain, because the cognitive structure is the same: a starting point, an ending point, a traversal.

Differentiation: d/dx(x²) = 2x becomes `DIFFERENTIATE { (THEME) = x², (INSTRUMENT) = x, (RESULT) = 2x }`.

Limits: lim(x→∞) 1/x = 0 becomes `LIMIT { (THEME) = 1/x, (GOAL) = ∞, (RESULT) = 0 }`. The variable approaches the Goal, the same directional structure as physical motion.

### The role mapping

| Math concept | Thematic role | Linguistic parallel |
|---|---|---|
| Operand / expression being operated on | Theme | "the thing being acted on" |
| Second operand / applied quantity | Instrument | "by what means" |
| Lower bound / starting value | Source | "where from" |
| Upper bound / ending value | Goal | "where to" |
| Answer / output | Result | "what comes into existence" |
| Both sides of an equation | Pivot | "central participant in fixed state" |
| Degree or magnitude of change | Extent | "by how much" |
| Path of integration (line integrals) | Path | "the route taken" |

### Why this matters

The mapping is significant not because it enables a math engine (though it does: `5 meters + 3 feet` is an ADD frame whose operands are quantities with unit sememes, resolvable because METER and FOOT are both LENGTH units with known conversion factors). It is significant because it demonstrates that thematic roles are cognitive structuring principles, not linguistic artifacts.

Mathematics and natural language both express: what is being operated on (Theme), by what means (Instrument), where we start (Source), where we end (Goal), by how much (Extent), and what results (Result). The roles are the same because the underlying cognitive operations are the same.

If ~25 thematic roles can structure natural language, social interactions, and mathematical expressions, those roles are genuinely universal. A base layer built on them is as general as meaning itself.

### Mathematics as a language

Not only are mathematical operations frames, they constitute a *language* with its own grammar. And that grammar is data on the predicates themselves.

`+` is a meaning in the shared vocabulary. It carries properties: infix, a precedence level, left-to-right associativity. The parser reads these properties from the operator, the same way it reads role expectations from a verb. There is no separate grammar for mathematical expressions. There are predicates with parsing metadata.

The consequence: natural language, mathematical expressions, and domain-specific notations coexist within a single input stream. "Create chess where score > sqrt(9) named rematch" mixes English ("create chess"), a mathematical sub-expression ("score > sqrt(9)"), and an auxiliary predicate ("named rematch"). One resolution pipeline, where each predicate declares its parsing behavior. The language being spoken is inferred from the tokens, not assumed.

Mathematical and functional expressions are not bolted onto the side of a semantic layer. They are frames. A spreadsheet cell is a frame whose value is the result of an expression frame. The boundary between "data" and "computation" dissolves the same way "data" and "metadata" does: both are role bindings on predicates.

---

## 9. What Follows

If we accept the premises (computing needs a semantic base layer, it must be built in not bolted on, the frame is the right primitive, the vocabulary is anchored in empirical linguistics) then several consequences follow. They are not independent features. They are structural properties, coupled: you cannot get some without the others, and you do not need to engineer them separately.

**Queryability without crawling.** Every piece of data is a frame with a grounded predicate and semantically-keyed bindings. The data *is* the index. "All books authored by Tolkien" is not a text search; it is a lookup on AUTHORED frames where AGENT refers to Tolkien. Each frame is indexed by its predicate and by each meaning in its compound binding keys. For N frames with K bindings on average, the index contains O(N × K) entries. Queries resolve in O(log N). Standard data structures, richer keys.

**Multilingual interoperability.** A Spanish speaker and an English speaker see the same data through their own words but operate on the same semantic structures. The system does not translate; it resolves, through different words, to the same concept.

**Trust as data.** Every frame is a signed assertion by an identified party. A "like" is a signed frame. A spam label is a signed frame. A fact-check is a signed frame. Different users, with different trust relationships, see different views of the same underlying data, not because a platform is making editorial decisions, but because trust policies (themselves data) produce different evaluations. This is Szabo's (1997) vision of formalizing relationships on public networks, realized through the frame primitive.

**Content-addressed identity.** Frame identity is determined by semantic content (predicate + bindings). Two identical assertions produce the same identity regardless of who makes them or when. The same principle as content-addressed storage (Merkle, 1979; Benet, 2014), applied to semantic structures rather than opaque bytes.

**Composability.** A document is frames. A chat room is frames. A chess game is frames. A trust relationship is frames. A mathematical expression is frames. There is no structural distinction between content, metadata, relationships, configuration, and computation.

**Liveness.** Real-time shared presence is not a separate system. A PRESENT frame asserts "I am in this space." An AVATAR_STATE frame with a retention policy of LATEST carries position and orientation at 60Hz. Stream bindings carry video and audio. Three temporal modes (durable, ephemeral, streaming), one frame model. "Entering" a shared space is creating a PRESENT frame on that item. Other participants see it through normal subscriptions. The renderer (3D, 2D, text) handles it per fidelity. This is how Croquet's (Smith, Kay, Raab, & Reed, 2003) vision of a shared, replicated environment is realized without requiring a single runtime: the frame primitive absorbs what Croquet needed a custom collaboration protocol (TeaTime) to achieve.

**Syntax as vocabulary.** Predicates carry their own parsing behavior. Operators declare precedence, functions declare grouping, prepositions declare role assignment. One resolution pipeline. Natural language, mathematics, chess notation, and any future domain syntax all flow through the same mechanism. Parsing is resolution.

**Self-describing data.** A frame carries everything needed to interpret it. Its predicate says what kind of assertion it is. Its binding keys say what each value means. No external schema, no format specification, no application-specific decoder ring.

**Subsumption of platforms.** A product listing is frames (PRICE, CATEGORY, LOCATION, DESCRIPTION, OFFER). A community is frames (MEMBERSHIP, MODERATION, TOPIC, MESSAGE). A review is frames (RATING, TOPIC, AGENT). A citation graph is CITES frames. A social network is frames (FOLLOW, POST, COMMENT, BLOCK). Each currently a proprietary database on a proprietary platform. In the shared meaning space, all the same primitive.

---

## 10. Honest Reckoning

I am not the first to propose an ambitious rethinking of how computing handles information. The history of such proposals is largely a history of instructive failures, and I would be foolish to ignore it.

**Xanadu** (Nelson, 1974) envisioned a global, versioned, bidirectional-linking document system with micropayments and transclusion. It got content addressing, versioning, and bidirectional links right (concepts that took decades to resurface in Git and IPFS). It failed because it demanded solving everything simultaneously before shipping anything. After sixty years, it remains unfinished. Lesson: scope ambition ruthlessly. Ship incremental function, not a complete vision.

**CYC** (Lenat, 1995) set out to encode all of common-sense knowledge as logical assertions. It got the diagnosis right: computers need world knowledge, not just data. It stalled because hand-authoring millions of axioms does not scale. Lenat himself noted the project's dependence on "a large team of knowledge enterers." Lesson: do not try to encode all knowledge by hand. Anchor in existing resources and let meaning emerge from use.

**Croquet** (Smith, Kay, Raab, & Reed, 2003), Alan Kay's vision of a shared, replicated 3D environment where all computation is transparent and collaborative, got replicated state, late-binding, and seamless collaboration right. It faded because it required a complete runtime (Squeak Smalltalk), could not interoperate with existing software, and presented an interface that was ahead of its time. Lesson: platforms that cannot meet users where they already are face adoption cliffs that no technical elegance can overcome.

**Plan 9** (Pike et al., 1995) pushed Unix's "everything is a file" to its logical conclusion: all resources accessible as file trees via 9P. Technically superior to Unix in almost every way. It failed to displace Unix because it required abandoning the entire Unix ecosystem. No migration path, no backwards compatibility, no critical mass. Lesson: even a cleaner design loses to an entrenched ecosystem unless it provides a bridge.

**The Semantic Web** (Berners-Lee et al., 2001) got the diagnosis exactly right: the web needs machine-readable semantics. It built a rigorous stack that works in specialized domains. It did not become general-purpose because it was layered *on top of* the web rather than built into it. Lesson: a semantic layer that is optional will remain marginal.

What do these teach?

**Incremental delivery is non-negotiable.** A system that requires completeness before it provides value will never reach completeness. Each increment must be useful on its own.

**Build on existing resources.** CYC tried to encode all knowledge manually. The Semantic Web required ontology engineering for every domain. I would rather anchor in WordNet, CILI, VerbNet, and ISO 24617-4: resources built and validated over decades by the computational linguistics community. I am not inventing a vocabulary; I am giving an existing, empirically validated vocabulary a new job.

**Provide a bridge.** Plan 9 and Croquet demanded that users abandon their ecosystems. A semantic base layer must coexist with files, filesystems, and the web. POSIX is a reasonable base layer for byte handling. It was never intended to be a base layer for meaning.

**The semantic layer must not be optional.** This is the deepest lesson from the Semantic Web. If creating semantic structure is a separate step from creating data, most people will skip it. The design must make creating data *be* creating semantic structure, the way writing a sentence *is* expressing meaning, not writing sounds and then separately annotating what they mean.

Can this proposal avoid the fates of its predecessors? Honestly: I do not know. The ambition is large, the history is cautionary, and the engineering challenges are real. But the linguistic resources now exist. WordNet has 120,000 synsets. CILI links them across languages. VerbNet classifies 300 verb classes with role declarations. ISO 24617-4 standardizes the role inventory. UniMorph provides morphological data for 100+ languages. These resources represent decades of cumulative scholarly work. They did not exist when CYC began, when the Semantic Web was proposed, or when Croquet was built.

And, worth stating plainly: AI assistance has compressed what was previously decades of solo implementation work into feasible timescales. The bottleneck for ambitious software projects has always been the sheer volume of code required. That bottleneck has narrowed dramatically. This does not guarantee success, but it changes the economics of ambition.

The path forward is incremental: frames as a local data format; a shared vocabulary seeded from WordNet and CILI; a local query engine that resolves by meaning; and eventually a peer-to-peer network where semantic data is exchanged between nodes connected by trust. Each step independently useful. Together, the semantic base layer that computing has been missing since 1970.

Whether it works is an empirical question. I offer it not as a certainty but as a proposal, grounded in established theory and validated resources, that the time is right to try.

---

## References

Baker, C. F., Fillmore, C. J., & Lowe, J. B. (1998). The Berkeley FrameNet Project. In *Proceedings of ACL/COLING*, 86-90.

Benet, J. (2014). IPFS — Content Addressed, Versioned, P2P File System. arXiv:1407.3561.

Berners-Lee, T., Hendler, J., & Lassila, O. (2001). The Semantic Web. *Scientific American*, May 2001.

Bizer, C., Heath, T., & Berners-Lee, T. (2009). Linked Data — The Story So Far. *International Journal on Semantic Web and Information Systems*, 5(3).

Bond, F., Vossen, P., McCrae, J., & Fellbaum, C. (2016). CILI: the Collaborative Interlingual Index. In *Proceedings of the 8th Global WordNet Conference*, 50-57.

Bond, F. & Foster, R. (2013). Linking and Extending an Open Multilingual Wordnet. In *Proceedings of ACL*, 1352-1362.

Bonial, C., Stowe, K., & Palmer, M. (2011). Renewing and Revising SemLink. In *Proceedings of the 2nd Workshop on Linked Data in Linguistics*.

Bush, V. (1945). As We May Think. *The Atlantic Monthly*, July 1945.

Engelbart, D. C. (1962). Augmenting Human Intellect: A Conceptual Framework. SRI Summary Report AFOSR-3223.

Fillmore, C. J. (1968). The Case for Case. In Bach, E. & Harms, R. T. (Eds.), *Universals in Linguistic Theory*, 1-88. Holt, Rinehart & Winston.

Fillmore, C. J. (1982). Frame Semantics. In *Linguistics in the Morning Calm*, 111-137. Seoul: Hanshin Publishing.

Gruber, T. R. (1993). Toward Principles for the Design of Ontologies Used for Knowledge Sharing. Technical Report KSL 93-04, Stanford University. *Knowledge Acquisition*, 5(2), 199-220.

Hewitt, C., Bishop, P., & Steiger, R. (1973). A Universal Modular ACTOR Formalism for Artificial Intelligence. In *IJCAI'73*, 235-245.

Hogan, A. et al. (2021). Knowledge Graphs. *ACM Computing Surveys*, 54(4).

Kay, A. C. (1993). The Early History of Smalltalk. In *HOPL-II: History of Programming Languages*. ACM.

Kleppmann, M., Wiggins, A., van Hardenberg, P., & McGranaghan, M. (2019). Local-First Software: You Own Your Data, in spite of the Cloud. In *Onward! 2019*, ACM.

Lenat, D. B. (1995). CYC: A Large-Scale Investment in Knowledge Infrastructure. *Communications of the ACM*, 38(11), 33-38.

Merkle, R. C. (1979). *Secrecy, Authentication, and Public Key Systems*. Ph.D. dissertation, Stanford University.

Miller, G. A. et al. (1993). Introduction to WordNet: An On-line Lexical Database. Princeton University.

Montague, R. (1973). The Proper Treatment of Quantification in Ordinary English. In *Approaches to Natural Language*, 221-242. Reidel.

Nelson, T. (1974). *Computer Lib / Dream Machines*. Self-published.

Palmer, M., Gildea, D., & Kingsbury, P. (2005). The Proposition Bank: An Annotated Corpus of Semantic Roles. *Computational Linguistics*, 31(1), 71-106.

Pike, R. et al. (1995). Plan 9 from Bell Labs. *Computing Systems*, 8(3), 221-254.

Ruppenhofer, J. et al. (2006). FrameNet II: Extended Theory and Practice. ICSI Berkeley.

Shapiro, M. et al. (2011). Conflict-free Replicated Data Types. In *SSS 2011*, Springer.

Smith, D. A., Kay, A., Raab, A., & Reed, D. P. (2003). Croquet — A Collaboration System Architecture. In *Proceedings of C5*, IEEE.

Szabo, N. (1997). Formalizing and Securing Relationships on Public Networks. *First Monday*, 2(9).

Tarr, D., Lavoie, E., Meyer, A., & Tschudin, C. (2019). Secure Scuttlebutt: An Identity-Centric Protocol for Subjective and Decentralized Applications. In *ACM ICN '19*.

Vossen, P. (1998). EuroWordNet: A Multilingual Database with Lexical Semantic Networks. *Computational Linguistics*, 25(4).

Youn, H. et al. (2016). On the Universal Structure of Human Lexical Semantics. *PNAS*, 113(7), 1766-1771.
