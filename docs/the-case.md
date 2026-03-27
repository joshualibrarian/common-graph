# Below the Application, Above the Bytes: The Case for a Semantic Base Layer

**Joshua Chambers**
*March 2026*

---

## Abstract

Every layer of the computing stack — from filesystems to operating systems to the web — is semantically inert. Data is stored as opaque bytes, identified by location, and interpreted only by the specific application that created it. The consequence is that no layer of infrastructure can answer the most basic question about any piece of data: *what does it mean?* This paper argues that the absence of a shared semantic layer is not merely an inconvenience but a foundational deficiency, one that forces every application, search engine, and integration layer to reinvent meaning-handling from scratch. We propose that this deficiency cannot be repaired by annotating existing layers, as decades of attempts have demonstrated, but requires a new base layer built on a semantic primitive. Drawing on Fillmore's frame semantics, we propose the *semantic frame* — a predicate-role structure grounded in established computational linguistics — as that primitive. We further argue that frames require a shared vocabulary of meanings, anchored not in application-specific schemas but in the empirically validated, cross-lingual semantic structures documented by WordNet, the Collaborative Interlingual Index (CILI), VerbNet, and ISO 24617-4. Together, frames and a shared meaning vocabulary constitute a base layer in which data is self-describing, queryable by meaning, and interoperable across languages, applications, and systems — without crawling, probabilistic parsing, or centralized indexing. We show that this primitive is expressive enough to subsume not only document-like data and social structures, but mathematical and functional expressions as well — evidence that semantic frames capture universal cognitive structure, not merely linguistic convention.

---

## 1. The Semantic Void

In 1945, Vannevar Bush described the central problem of information management: "The summation of human experience is being expanded at a prodigious rate, and the means we use for threading through the consequent maze to the momentarily important item is the same as was used in the days of square-rigged ships" (Bush, 1945). Eighty years later, the maze is incomparably larger, and the threading — while faster — is no less indirect.

Consider what happens when a user saves a photograph. The filesystem records a sequence of bytes at a path. The operating system knows the file's size, its modification time, and its location on disk. The image format (JPEG, PNG) encodes pixel data and perhaps some EXIF metadata — camera model, GPS coordinates, exposure settings. But no layer of the stack knows what the photograph *is*: who is in it, what occasion it documents, how it relates to other photographs or to the people depicted. That knowledge exists only in the user's head, or in the proprietary database of whichever application the user happened to use to organize their photos.

This is not a limitation specific to photographs, or to filesystems, or to any single technology. It is a structural property of every layer in the computing stack:

- **The filesystem** sees bytes at paths. A file is an opaque stream with a name, a size, and a set of permission bits. Whether the bytes represent a novel, a spreadsheet, or a genome sequence is invisible to the layer that stores and retrieves them.

- **The operating system** sees processes, file descriptors, and memory pages. It can schedule work and enforce access control, but it cannot distinguish a medical record from a restaurant menu.

- **The network layer** sees packets with source and destination addresses. HTTP adds request methods, status codes, and content-type headers — a step toward description, but one that identifies *format* (text/html, application/json), never *meaning*.

- **The database** sees rows, columns, and indexes — or documents and collections. The schema is local to the application. Two databases storing information about the same real-world entity (a person, a book, a transaction) share no vocabulary for describing what they hold.

- **The web** sees pages at URLs. A hyperlink asserts that one page relates to another, but says nothing about *how*. Search engines exist precisely because the web cannot answer "what is this about?" — so third parties crawl billions of pages, guess at meaning from word frequency and link structure, and sell access to their proprietary guesses.

Each of these layers was designed for generality, and each achieves it by the same strategy: treat data as opaque and leave interpretation to the layer above. This was a defensible engineering choice. But the cumulative consequence is that meaning — the thing humans actually care about when they create, store, query, and share information — has no home in the architecture. Every application that needs to do anything with meaning must build its own semantic layer from scratch: its own schema, its own vocabulary, its own query logic, its own integration adapters.

The cost of this is immense but invisible, because it is so pervasive. Every API integration is a bespoke translation between two systems that cannot describe their own contents to each other. Every search engine is a probabilistic compensation for the fact that data doesn't know what it means. Every data migration is a painful exercise in reconstructing meaning that was present in the creator's mind but was never captured by the infrastructure. Every new application begins by reinventing key-value schemas that express, in local and incompatible terminology, the same relationships that thousands of other applications have already expressed in their own local and incompatible terminology.

The key-value pair is perhaps the most fundamental pattern in computing. Configuration files, HTTP headers, database rows, JSON objects, environment variables — the pattern is everywhere. Yet precisely because keys are application-defined strings, they are fractured beyond repair. One system's `author` is another's `creator`, another's `created_by`, another's `dc:creator`, another's `writtenBy`. They all mean the same thing. No layer of infrastructure knows this.

What is missing is not a better search engine, a smarter parser, or a more comprehensive metadata standard. What is missing is a *layer* — a base layer where meaning is not an annotation bolted onto opaque bytes, but the fundamental unit of storage, identity, and retrieval.

---

## 2. Why It's Missing

The absence of a semantic layer reflects the world in which computing was built — and the world in which it grew up.

When the foundational layers of computing were laid down in the 1970s, nodes were disconnected and bytes were precious. Engineers were writing assembly to make bits land on a disk. The byte-stream abstraction — everything is a file, a file is a sequence of bytes — was a practical triumph given the constraints. TCP/IP, HTTP, SQL: each subsequent layer solved the problem in front of it (reliable storage, reliable transport, reliable query) with the resources available. A semantic data model was not rejected; it was simply beyond the horizon.

**The object problem.** A semantic data model requires something that no amount of engineering could have produced at the time: the *objects*. A semantic key cannot be a string — "author," "creator," "created_by," and "writtenBy" are four labels for the same meaning, and nothing connects them. A semantic key must refer to a *meaning*: a stable, language-independent concept with a defined identity, a place in a hierarchy, cross-lingual equivalents, and participation in structured scenes. A semantic key is not a label. It is an *object* — and building those objects requires decades of empirical research into how meaning is structured across human languages. The resources that make it tractable — WordNet's taxonomy of 120,000+ meanings (Miller, 1995), the Collaborative Interlingual Index linking those meanings across languages, FrameNet's 1,200+ structured semantic scenes (Baker et al., 1998), VerbNet's universal thematic role inventory (Palmer et al., 2005), and the cross-walks connecting them (SemLink) — are products of computational linguistics research that has only recently reached the maturity needed to serve as a practical foundation.

**Fragmentation.** Even as these linguistic resources matured, the software industry developed in a direction hostile to the kind of collaboration a semantic layer demands. A shared vocabulary of meaning is, by definition, a collective project — it requires open agreement on what the objects are, how they relate, and how they are identified. But the commercial software landscape of the 1990s and 2000s was defined by proprietary lock-in and aggressive patent strategies. Every major platform held its data models close, because controlling the data model meant controlling the ecosystem. Interoperability was a competitive threat, not a goal. The kind of cross-organizational, cross-platform collaboration that a shared semantic foundation requires was antithetical to the incentive structure of the industry.

**What changed.** Three things converged. First, the computational linguistics infrastructure matured — the object problem became tractable. Second, global interconnection made a shared vocabulary both necessary and *viable*: we can share a common vocabulary precisely because we are already passing data between systems constantly. The network that makes the problem acute is the same network that makes the solution practical.

Third, and critically: the open source movement created the collaborative environment that a semantic layer requires. The linguistic databases that ground the vocabulary — WordNet, CILI, FrameNet, VerbNet — carry permissive licenses that allow open use. The entire implementation stack, from the JVM to rendering pipelines to sandboxing to storage engines, can be built on open source foundations. A semantic base layer is inherently a commons — it only works if it is shared, and it can only be shared if it is open. That commons is now possible in a way it simply was not during the era of proprietary platform wars.

---

## 3. Why Retrofitting Fails

The need for a semantic layer has been recognized for decades, and there have been serious, well-funded attempts to provide one. Each has contributed valuable ideas. None has become foundational. Understanding *why* is essential to understanding what a solution requires.

**The Semantic Web** is the most ambitious attempt. Berners-Lee's 2001 vision in *Scientific American* described a web in which "information is given well-defined meaning, better enabling computers and people to work in cooperation" (Berners-Lee et al., 2001). The technical realization — RDF (Resource Description Framework) triples, OWL (Web Ontology Language) ontologies, SPARQL queries — is intellectually rigorous and computationally powerful. Twenty-five years later, RDF is widely used in specialized domains (biomedical ontologies, library science, government open data) but has not become a general-purpose semantic layer. The web remains overwhelmingly opaque bytes at URLs.

This is not because the technical stack is weak. RDF's genuine strengths are substantial: it provides a universal graph model, a formal basis for inference (RDFS and OWL entailment), and a powerful query language in SPARQL that supports graph traversal, federation, and reasoning over distributed data. In specialized domains where these capabilities matter — biomedical knowledge graphs, library cataloging, government linked data — RDF has proven its value. The failure is not technical but structural: RDF did not become the *general-purpose* semantic layer because of how it relates to the data it describes.

First, RDF annotates existing resources — it is layered *on top of* the web, not *built into* it. A web page can exist without any RDF. Most do. The semantic annotation is optional, which means it is optional, which means it is absent in the vast majority of cases. Any semantic layer that is optional will remain marginal, because the cost of creating semantic metadata falls on the producer while the benefit accrues to the consumer. This is a classic misaligned-incentive problem.

Second, RDF requires the author to commit to an ontology — a formal specification of the concepts and relationships relevant to their domain (Gruber, 1993). In practice, choosing and using an ontology correctly is hard. It requires expertise that most content creators do not have and are not motivated to acquire. The Semantic Web effectively asks every web author to be a knowledge engineer. Most are not, and most have no reason to become one.

Third, the annotation is *disconnected from the content*. The RDF description of a web page is a separate artifact from the page itself. It can become stale, incorrect, or inconsistent without any mechanism to detect the divergence. The content and its semantic description have independent lifecycles, maintained by potentially different parties, with no structural coupling between them.

**Schema.org** addressed some of these problems by providing a single, widely-supported vocabulary backed by major search engines (Google, Microsoft, Bing, Yahoo). Its adoption is far broader than RDF/OWL, precisely because it is simpler and because search engines provide a direct incentive (better search results) for using it. But Schema.org remains a metadata annotation: a sprinkle of JSON-LD in the head of an HTML page. It describes pages *about* things, not the things themselves. It makes the web slightly more transparent to search engines, but it does not change the fundamental abstraction. The data is still opaque bytes; the annotation is still optional; the semantics are still an afterthought.

**Dublin Core**, **EXIF**, **ID3 tags**, **OpenGraph**, and dozens of other metadata standards each solve a narrow problem: describing documents, photographs, music files, or social media links. Each defines its own vocabulary, its own format, its own embedding mechanism. They do not compose. A photograph with EXIF data and a document with Dublin Core metadata cannot be queried together because they share no vocabulary, no addressing scheme, and no common notion of what a "subject" or "creator" means. Each standard is a small island of semantics in an ocean of opacity.

The pattern across all of these efforts reveals a structural lesson: **you cannot make a semantically inert layer semantic by annotating it.** The annotation is always optional, always disconnected from the content, always maintained by a different process than the content itself, and always expressed in a vocabulary that is local to one standard or one domain. The layer itself — the filesystem, the web, the database — remains opaque. The annotations are a gloss on top that can be added, removed, or ignored without affecting the underlying data.

This is why the solution cannot be another metadata standard, another annotation format, or another ontology language. The solution must be a *layer* — a data abstraction in which meaning is structural, not decorative. Where creating data *is* creating semantic structure, because the two are not separate operations. Where the vocabulary is shared, not because everyone agreed to use it, but because it is grounded in something more stable than any single standard body's decisions.

---

## 4. What "Semantic" Actually Requires

If a semantic base layer is needed and cannot be achieved by annotating existing layers, what must it look like? We can derive the requirements from the failures above and from established results in knowledge representation and computational linguistics.

### Grounded predicates, not strings

The key-value pair is computing's universal pattern for associating data with description. But when keys are application-defined strings — `author`, `creator`, `created_by`, `writtenBy` — they are meaningless to any system that didn't define them. A semantic layer requires keys that carry *meaning*, not just labels. The key must refer to a concept, not a string — and that concept must be shared across systems, applications, and languages.

This is not a new insight. Gruber (1993) defined an ontology as "an explicit specification of a conceptualization" and argued that shared vocabularies are essential for knowledge sharing among systems. The Semantic Web pursued this through URI-identified predicates. But URIs are locations, not meanings — they are globally unique identifiers, but they do not carry semantic content intrinsically. Two different URIs can denote the same concept (schema.org/author vs. Dublin Core's dc:creator), and nothing in the infrastructure connects them.

What we need instead are keys that refer to *meanings* — language-independent, application-independent units of semantic content with stable identities. Computational linguistics provides exactly such units. We will return to what those units are and where they come from in section 7. For now, the requirement: keys must be grounded meanings, not strings.

### Structured assertions: predicates, roles, and bindings

A flat key-value pair (`author: Tolkien`) captures a single relationship but loses the structure that gives it meaning. Who is asserting this? About what? In what capacity? What else does the assertion entail? A semantic assertion is not a pair but a *structure* — and the structure of meaning has been studied extensively.

Fillmore's frame semantics (1968; 1982) provides the theoretical foundation. Fillmore observed that understanding a word like "buy" requires understanding an entire *scene* — a buyer, a seller, goods, money, a transaction — and that the word's meaning is constituted by this scene and the roles within it. A frame, in Fillmore's sense, is "any system of concepts related in such a way that to understand any one of them you have to understand the whole structure in which it fits" (Fillmore, 1982). The participants in the frame — buyer, seller, goods, money — are not arbitrary attributes but *thematic roles*: semantic functions that have been catalogued, classified, and standardized across decades of research.

The frame's power is connective. Take the sentence "I eat an apple." Three words, three concepts (the article is a language-specific syntax and carries no real semantic content) — a person, an action, a fruit — that in isolation are unrelated. The frame *connects* them: it establishes that the person is the Agent (the one performing the action), the apple is the Patient (the thing being affected), and the eating is the predicate that defines how they relate. Without the frame, you have three separate concepts. With it, you have a coherent assertion with defined semantic roles. This is what Fillmore meant by "the whole structure" — it is the structure that creates meaning from parts.

This connective structure is precisely what a semantic data layer needs. The flat key-value pair `author: Tolkien` fails not just because the key is a string, but because it has no structure to express the *kind* of relationship, the *participants* and their roles, or the *context* in which the assertion holds. What we need is the frame pattern: a **predicate** that defines a structured assertion, and a set of **role bindings** that fill the predicate's slots with values.

#### The predicate defines the shape

A predicate is a grounded meaning — a concept from the shared vocabulary — that defines what kind of assertion the frame makes. Crucially, each predicate declares the *roles* it expects: the semantic slots that must (or may) be filled to make the assertion complete, to make it make *sense*.

The predicate AUTHORED, for instance, defines an assertion about creative origination. It expects certain roles: who did the authoring (an Agent), and what was authored (a Theme — the entity that exists or is located, in this case the work). These role expectations are not arbitrary — they reflect the thematic role structure that Fillmore first described as "case frames" (1968) and that has since been catalogued computationally by two major projects:

**FrameNet** (Baker, Fillmore, & Lowe, 1998; Ruppenhofer et al., 2006), developed at Berkeley as the direct computational realization of Fillmore's own theory, defines over 1,200 semantic frames, each with its own set of *frame elements* — the specific roles that participants play within that frame. FrameNet's Commerce_buy frame, for instance, defines roles for Buyer, Seller, Goods, Money, and several optional elements like Purpose and Means. Its Authorship frame defines roles for Author and Work. Each frame is a structured scene in exactly the sense Fillmore described — and each frame's role declarations are precisely the kind of "data template" we are proposing.

**VerbNet** (Palmer, Gildea, & Kingsbury, 2005) takes a complementary approach, organizing approximately 300 verb classes by shared syntactic and semantic behavior. Where FrameNet defines frame-specific roles (Buyer, Seller), VerbNet maps these to a smaller set of universal thematic roles (Agent, Theme, Goal) standardized by ISO 24617-4. Both resources are linked to WordNet synsets, and **SemLink** (Bonial et al.) provides cross-walks between them — meaning that a frame definition can draw on FrameNet's rich scene descriptions, VerbNet's universal role mappings, and WordNet's concept hierarchy simultaneously.

Different predicates expect different roles:

- **AUTHORED** expects an Agent (who created it) and a Theme (what was created)
- **COMMERCIAL_TRANSACTION** expects a Buyer, a Seller, Goods, and Money — Fillmore's original commercial event frame, formalized in FrameNet as Commerce_buy
- **MOTION** expects a Theme (what moves), a Source (where from), a Goal (where to), and optionally a Path
- **TITLE** expects a Name (the designation being assigned) and the thing being named
- **MOVE** (in chess) expects a Location (which game), an Agent (who moved), a Theme (what piece), a Source (from where), and a Goal (to where)

The predicate is, in effect, a *data template*. It declares: "to make this kind of assertion, fill these roles." This is exactly what FrameNet's frame definitions do — but applied to data rather than to linguistic annotation. The roles are not arbitrary attribute names — they are grounded meanings from the same shared vocabulary as the predicate itself. Agent, Theme, Goal, Source, Instrument — these are semantic functions that have been studied, classified, and validated across decades of linguistic research, catalogued in over 1,200 frames by FrameNet, organized into universal role inventories by VerbNet and ISO 24617-4, and cross-linked by SemLink.

#### Roles as semantic keys

Each role binding in a frame connects a semantic role to a value. The role is the key; the value is the data. But unlike string keys in a flat key-value store, the role carries *semantic content*. The role AGENT does not merely label a slot — it asserts that the bound value is the intentional initiator of the action described by the predicate. The role THEME asserts that the bound value is the entity that exists, moves, or is located without undergoing change. The role INSTRUMENT asserts that the bound value is the tool or means by which the action is accomplished.

This is a richer notion of "key" than computing typically employs. In a JSON object, `{"author": "Tolkien"}`, the key "author" is a label — it has whatever meaning the application developer intended, and no other system can verify or rely on that intention. In a semantic frame, the key is a *concept* — a meaning with a stable identity, a definition, a place in a hierarchy of related meanings, and a set of expectations about what kind of value it binds.

The role vocabulary itself comes from established research. Fillmore's original case roles (1968) — Agent, Patient, Instrument, and others — were refined through decades of work into the approximately 25 thematic roles standardized by VerbNet and ISO 24617-4 (Bonial et al., 2011). These include:

- **Core participant roles**: Agent (intentional initiator), Patient (affected entity), Theme (existing/located entity), Experiencer (perceiver), Cause (non-intentional initiator)
- **Directional roles**: Goal (endpoint), Source (origin), Destination (physical endpoint), Path (route)
- **Transfer roles**: Recipient (receiver), Beneficiary (one who benefits), Partner (co-participant)
- **Manner roles**: Instrument (tool), Manner (how), Extent (degree), Purpose (intended outcome)
- **Setting roles**: Location (where), Time (when)
- **Information roles**: Topic (subject of communication), Name (designation)

This inventory is not arbitrary and it is not infinite. It reflects empirical findings about how human languages structure meaning. Every language studied — from English to Lakhota to Japanese — uses the same core set of semantic functions to describe who did what to whom, where, when, how, and why (Youn et al., 2016). The roles are universal; the words that express them vary by language.

#### Two levels of role: universal and frame-specific

The ~25 universal thematic roles (Agent, Theme, Goal, etc.) are powerful precisely because they are universal — the same roles appear across all predicates, all languages, all domains. But they are also *general*. In a chess game, both Fischer and Spassky are Agents — intentional initiators of action. Calling them both "Agent" is correct but insufficient. We need to say *more*: that they are *players*, and that one plays white and the other plays black.

This is the same tension that FrameNet and VerbNet resolve differently. FrameNet defines frame-specific elements: the Commerce_buy frame has Buyer, Seller, Goods, Money — roles that are specific to that frame and carry more meaning than the universal Agent, Theme, Source. VerbNet maps these back to universal roles: Buyer maps to Agent, Goods maps to Theme. Both levels are useful. The universal level enables cross-frame queries ("find all frames where someone is an Agent"). The frame-specific level enables precise description ("find all frames where someone is a Buyer").

The semantic base layer needs both levels, connected through the vocabulary's own inheritance hierarchy. PLAYER, BUYER, and AUTHOR are all meanings in the shared vocabulary, and each is a *specialization* of the universal role AGENT. This relationship is expressed in the system's own terms — as a frame: HYPERNYM { (THEME) = PLAYER, (GOAL) = AGENT }. PLAYER is a kind of AGENT. The vocabulary describes itself with the same primitives it uses to describe everything else.

This means a PLAYER frame on a chess game doesn't use the generic "Agent" role — it uses the more specific **PLAYER** role, because PLAYER carries the additional meaning that the context needs. But because PLAYER inherits from AGENT, any query at the universal level still works: "all frames where Fischer is an AGENT" finds chess games, authorship assertions, and anything else where Fischer acts intentionally.

#### Compound keys

Roles can be further qualified through **compound keys** — sequences of meanings that together identify what a binding *is*. A movie might have multiple format variants: (VIDEO, MKV, UHD) and (VIDEO, MKV, HD) are both VIDEO bindings, qualified by container format and resolution. A document might have both an English and a Spanish gloss: (GLOSS, ENGLISH) and (GLOSS, SPANISH) are both GLOSS bindings, qualified by language. The compound key is a sequence of meanings — role plus qualifiers — that together specify the binding with arbitrary precision.

Every element of a compound key is a grounded meaning from the shared vocabulary. VIDEO is not a MIME type prefix — it is the meaning "moving visual content." MKV is not a file extension — it is the meaning "Matroska multimedia container format." UHD is not an abbreviation — it is the meaning "ultra-high-definition resolution." ENGLISH is not a locale string — it is the sememe for the English language. Each element carries semantic content, and the combination creates a precise identification through composition, not through convention.

Crucially, every meaning in a compound key is an *opportunity for indexing*. If the system indexes frames by the meanings in their binding keys, then a query like "show me all videos" is a simple index lookup on the VIDEO meaning — instantly finding every frame that has a VIDEO binding, regardless of format or resolution. A more specific query, "show me all UHD videos," narrows to frames whose keys include both VIDEO and UHD. A query for "all MKV content" finds every frame with MKV in its key — video, audio, whatever container format it carries. The compound key is not just a label; it is a *multi-dimensional index* built from the vocabulary itself. No separate tagging system, no application-specific search facets, no metadata catalog. The key *is* the index.

#### Everything is a role binding

An important consequence of this structure: there is no fundamental distinction between "the data" and "the metadata" of a frame. A title's text, a video's master file, a chess move's destination square, a document's author — each is a role binding on a frame. The title text fills the NAME role on a TITLE frame. The video file fills a content role on a VIDEO frame. The destination square fills the GOAL role on a MOVE frame. The author fills the AGENT role on an AUTHORED frame. Provenance is a binding. Signatures are bindings. Timestamps are bindings. Everything is a role filled on a predicate.

This uniformity is not a simplification that sacrifices expressiveness. It is the recognition that meaning *is* the relationship between a predicate and its role-fillers. There is nothing else. What we call "data" is a value filling a role. What we call "metadata" is also a value filling a role. The distinction is conventional, not structural.

### Write-time resolution, not read-time interpretation

This is the core inversion. Every existing system stores data first and tries to determine its meaning later. Search engines crawl and index. NLP systems annotate after the fact. Data integration pipelines map between schemas post-hoc. All of these are read-time interpretation — attempts to recover meaning that was present in the creator's mind but was never captured in the data.

A semantic base layer must invert this. Meaning should be resolved *at the moment of creation*, when it is trivially easy — because the creator knows what they mean. The disambiguation that search engines, NLP pipelines, and integration layers struggle to perform after the fact is effortless at write time. When a user creates a relationship between a person and a book, they know whether they mean "authored," "edited," "reviewed," or "purchased." If the system captures that distinction as a grounded semantic predicate at creation time, no subsequent system ever needs to guess.

This is where the frame structure earns its keep as a *data entry* mechanism, not merely a storage format. The predicate, once chosen, tells the system what roles to expect. The system can prompt for them, offer completions, validate inputs. The user is not filling out a form designed by a specific application — they are filling semantic roles defined by the predicate itself, from the shared vocabulary. The act of creating data *is* the act of resolving meaning, because selecting a predicate and filling its roles is inherently a semantic operation.

This is the fundamental departure: not smarter reading, but more precise writing. The data is pre-indexed by meaning at the moment it is created. Queries resolve against meaning, not against text. No crawling, no probabilistic ranking, no NLP disambiguation required.

It is worth noting what this is *not*. It is not natural language understanding. The system does not parse free text and try to extract meaning — the approach of NLP and information extraction, which is probabilistic and error-prone. Instead, the system structures the input environment so that meaning is captured as a natural consequence of creation. The user selects a predicate (choosing what kind of thing they're asserting), fills roles (specifying the participants), and the result is a grounded semantic structure. The hardest problem in NLP — disambiguation — is trivially solved by the person who knows what they mean and trivially disambiguate with a dropdown or another narrowing meaning.

### Cross-lingual stability

A semantic layer that works only in English is not a semantic layer — it is an English-language metadata standard. Meaning is not a property of words. The concept that English speakers call "dog," Spanish speakers call "perro," and Japanese speakers call "inu" is the same concept in each case. A semantic layer must represent meanings independently of the words that express them, and connect those meanings to words in any language.

This requires a clean separation between *meanings* and *words*. Meanings (which we will call sememes, following usage in structural semantics) are language-neutral units with stable identities. Words are language-specific expressions that point to meanings. The predicate AUTHORED is a meaning — it exists independently of the English word "authored," the Spanish word "escrito," or the German word "verfasst." Each of those words, in its respective language, points to the same meaning.

---

## 5. The Frame as Primitive

We propose the semantic frame as the fundamental primitive for a semantic base layer. Not the frame as Fillmore defined it for linguistic analysis, but the frame *repurposed*: extended from a tool for understanding language into a tool for structuring data.

In Fillmore's frame semantics, a frame is a structured representation of a situation — a predicate and a set of thematic roles that together define a coherent scene. The verb "buy" evokes a commercial transaction frame with roles for Buyer, Seller, Goods, and Money. The verb "move" evokes a motion frame with roles for Theme (what moves), Source (where from), Goal (where to), and Path (how). The insight is that meaning is not carried by isolated words but by the *structure of relationships* between participants in a scene.

Fillmore's analysis was descriptive — a way to understand how language works. The proposal here is to use the same structure *prescriptively* — as a data format. A semantic frame, in our usage, is:

```
Frame {
    predicate:  a grounded meaning    (what kind of assertion)
    bindings:   role-value pairs      (the semantic content)
}
```

That's it. A predicate and its role bindings. Nothing else is structurally required. Every element of the frame — what it asserts, what it's about, who is involved, what content it carries — is expressed as a role binding on the predicate.

Consider what this structure subsumes:

A **title assertion**: predicate TITLE, bindings (THEME) = the-book, (NAME) = "The Hobbit". The predicate TITLE defines two roles: what is being titled (THEME) and what the title is (NAME).

A **chess move**: predicate MOVE, bindings (LOCATION) = the-game, (AGENT) = Fischer, (THEME) = king-pawn, (SOURCE) = e2, (GOAL) = e4. The thematic roles fit naturally — Location (where it happened), Agent (who moved), Theme (what was moved), Source (from where), Goal (to where). A single move is a single semantic assertion.

A **video**: predicate VIDEO, bindings (THEME) = the-movie, (VIDEO, MKV, UHD) = master-file, (VIDEO, MKV, HD) = transcode. Content in different formats is expressed through compound role keys.

An **authorship assertion**: predicate AUTHORED, bindings (THEME) = The Hobbit, (AGENT) = Tolkien. The assertion connects an agent to a work through the predicate that defines their relationship.

The critical observation is that these are all structurally identical: a predicate and a set of role bindings with compound semantic keys. The predicate determines what roles the frame expects. The roles determine what the values mean. The compound key structure eliminates the need for separate type metadata — (MKV, UHD) is both the role (video content) and the type (Matroska, ultra-high-definition) in a single semantic expression.

Note that each of these frames has a binding that anchors it to its *subject* — the thing the frame is about. For TITLE and AUTHORED, that anchor is the THEME binding ("The Hobbit" — the entity being described). For MOVE, it is the LOCATION binding (the chess game — the context where the move occurs). This is not a coincidence but a structural pattern: every frame has some role that connects it to the item it describes. Which role that is depends on the predicate. Property predicates (TITLE, AUTHORED, HYPERNYM) naturally use THEME — the entity being characterized. Event predicates (MOVE, MESSAGE, BID) naturally use LOCATION — the setting where the event occurs. The predicate determines the anchor, because the predicate defines what roles the frame expects and what they mean.

This is the sense in which a frame is a *data template with its primary semantic components as the key*. The predicate is the template. The roles are the slots. The bindings fill them. The frame does what Fillmore showed frames do in language — it connects disparate things (a person, a book, the relationship of authorship) into something with coherent unity and meaning. But where Fillmore's frames describe pre-existing utterances, these frames *structure data at the point of creation*. The frame is not an annotation on data. It *is* the data.

#### Predicates carry behavior, not just structure

The predicate is not merely a data template. It is a *behavioral specification*. A predicate can declare how it participates in *parsing* — what syntax it expects, what roles it fills from context, what sub-language it delegates to — and how it *evaluates* — what computation it performs with filled bindings. The predicate is simultaneously the schema, the parser instruction, and the program.

Consider: the operator `+` is not a hardcoded token in a grammar. It is a meaning in the shared vocabulary — the same kind of object as "author" or "create." As a meaning, it carries properties: it is infix (appears between its operands), it has a precedence (binds tighter than subtraction, looser than multiplication), it associates left-to-right. These are not grammar rules maintained by a parser — they are *data on the predicate*, the same way "expects AGENT and THEME" is data on the predicate AUTHORED. The parser reads these declarations and applies them. There is no separate precedence table. There is no grammar.

This extends to structural symbols. Parentheses are not reserved syntax. They are meanings in the shared vocabulary — meanings that declare "I open a group" and "I close a group." There is, in fact, *no reserved syntax at all*. Everything — verbs, operators, functions, parentheses, commas — resolves through the same shared vocabulary. Syntax is vocabulary.

The consequence is that *any domain can bring its own notation*. Chess algebraic notation ("e4," "Nf3," "O-O") is a set of meanings with their own parsing declarations. A regular expression is a set of meanings with their own parsing declarations. These are not special-cased. They are predicates that declare how they parse, resolved through the same mechanism as arithmetic operators or English prepositions. The frame primitive reaches all the way into how input is interpreted, not just how data is stored.

The key-value pair, as noted, is computing's most ubiquitous pattern. But its ubiquity is also its weakness: because every application defines its own keys, the pattern produces fragmentation rather than interoperability. Semantic frames address this by making the keys *themselves* drawn from a shared vocabulary. When the key is not the string "author" but the *meaning* AUTHOR — a globally-anchored concept with a stable identity — then any system using that key is, by construction, using the same vocabulary. The frame does not merely structure data. It structures it in a way that is *commonly intelligible*.

This is a stronger claim than what ontology languages like OWL provide. OWL lets you *define* a vocabulary and *declare* that two terms are equivalent (owl:sameAs). Semantic frames, as proposed here, avoid the problem entirely: there is one shared vocabulary, grounded in empirical linguistics, and every frame draws its predicates and roles from it. There are no competing vocabularies to reconcile, because the vocabulary is not invented per-application — it is anchored in the structure of human meaning itself in a common meaning-space.

---

## 6. Coherence: What Frames Cohere Around

Frames are the primitive. But a single frame is rarely the whole story. A book is not one frame — it is a TITLE frame ("The Hobbit"), an AUTHORED frame (Tolkien), one or more TEXT frames (the chapters), a COVER_ART frame (the dust jacket illustration), perhaps a TABLE_OF_CONTENTS, a PUBLICATION frame (1937, Allen & Unwin), and more. Each of these frames is a separate assertion with its own predicate and bindings. But they are all *about the same thing* — the book. They only make sense together, as a coherent whole.

This observation leads directly to a design decision. If frames can be about the same thing, they need a shared identity to point to. That identity — and the collection of frames that describe it — we call an **item**.

An item is not a new primitive in the way a frame is. It is an architectural choice: a stable, cryptographic identity that frames can reference in a role binding to indicate "I am about *this thing*." The book is an item. Tolkien is an item. A chess game is an item. Each exists as an identity around which frames accumulate — title frames, authorship frames, move frames, player frames — building up a coherent, multi-faceted description. The role that carries this reference depends on the predicate: THEME for an authorship assertion (the work being described), LOCATION for a chess move (the game where it happens). The predicate determines how the frame connects to its item, using whatever role is semantically natural.

This is a deliberate design decision, not an emergent property. The frame primitive alone does not conjure stable identities into existence. Something must *provide* them. We choose content-addressed cryptographic identity: an item's identity is a hash derived from its defining characteristics, making it stable, verifiable, and independent of any central registry. This choice has consequences — it means identity is not assigned by a central authority but established by the convergence of content — and those consequences are load-bearing parts of the architecture, not incidental details.

A chess game makes the pattern vivid. The game is an item — a stable identity with a type. But the game is not a single monolithic structure. It is an *accumulation of signed frames*:

Players register by signing their own PLAYER frames: `PLAYER { (AGENT) = Fischer, (ROLE) = WHITE }` signed by Fischer; `PLAYER { (AGENT) = Spassky, (ROLE) = BLACK }` signed by Spassky. Each player *attests their own participation* — it is not assigned by a third party, it is declared by the participant and carries their signature.

Then each move is a signed frame: `MOVE { (LOCATION) = the-game, (AGENT) = Fischer, (THEME) = king-pawn, (SOURCE) = e2, (GOAL) = e4 }` signed by Fischer. The next move: `MOVE { (LOCATION) = the-game, (AGENT) = Spassky, (THEME) = queen-pawn, (SOURCE) = d7, (GOAL) = d5 }` signed by Spassky. Each move is independently meaningful, independently signed, and independently verifiable. The LOCATION binding connects each move to the game — the item where the event occurs. The game is the ordered sequence of these signed assertions, all cohering around the same item identity.

No special "game engine" data structure is needed. No move log, no state machine, no event stream. Each move is a frame — the same primitive as a title, an authorship assertion, or a video. And because each move is a frame, it is independently queryable. "All games where someone opened with pawn to e4" is an index lookup on MOVE frames with (GOAL) = e4. "All games Fischer played" is a lookup on PLAYER frames with (AGENT) = Fischer. "Fischer's longest game" is a count of MOVE frames per game item where Fischer has a PLAYER frame. The game's history, its participants, its outcome — all are frames, all are indexed, all are queryable.

This is a stronger claim than "games can be modeled as frames." It is that a game *is* an item in exactly the same way a book is an item. A book is its title frame plus its authorship frame plus its chapter frames plus its cover art frame. A chess game is its player frames plus its move frames plus its result frame. The item IS the accumulation of its frames. There is no separate "game object" or "book object" hiding behind the frames. The frames are the thing.

This pattern generalizes immediately. A chat room is an item where people join with signed MEMBERSHIP frames and contribute with signed MESSAGE frames. A key log — the history of a person's cryptographic keys — is an item with KEY frames (key published), REVOKE frames (key revoked), and DELEGATE frames (key authorized another). An auction is an item where bidders assert signed BID frames. A band, a class, a project — all the same pattern: an item exists, people make signed assertions on it, and those assertions collectively define what it is and who is involved.

And here the architecture closes a circle: even sememes themselves — the units of meaning that make up the shared vocabulary — are items. Consider the sememe METER (the unit of length). It is not a bare label. It is a coherent collection of frames: a GLOSS frame in English ("the base unit of length in the metric system"), a GLOSS frame in Spanish ("la unidad base de longitud en el sistema métrico"), a DIMENSION frame (LENGTH, which is another sememe itself), CONVERSION frames to other units (1 meter = 3.28084 feet), a HYPERNYM frame (METER is-a LENGTH_UNIT), and a SYMBOL frame ("m"). Each of these is a separate assertion — a separate frame with its own predicate and bindings. Together, they constitute what METER *means*. The meaning is not a definition string. It is the structured totality of everything asserted about it.

The same is true for every sememe in the vocabulary. AUTHOR has glosses, hierarchical relationships (AUTHOR is-a AGENT), and lexemes in every imported language ("author" in English, "autor" in Spanish, "著者" in Japanese). A language itself — English, Spanish, Japanese — is an item whose frames include its entire lexicon: every word-to-meaning mapping is a frame on the language item. The vocabulary is not a separate system maintained outside the graph. It lives *in* the graph, as items made of frames, using the same primitives as everything else.

This is where the analogy to files and folders becomes concrete. A file is an opaque sequence of bytes at a path, interpreted only by the application that created it. An item is a *typed, self-describing collection of frames* — each frame carrying grounded semantic content, each addressable by its compound semantic key, all cohering around a shared identity. The item doesn't live at a path. It exists by identity, and you find it by meaning — by querying the frames that describe it.

| Files | Items |
|---|---|
| Opaque bytes — the system cannot interpret content | Typed frames — the system knows what everything means |
| Named by path in a hierarchy — one location per file | Discoverable by meaning — exist in a semantic graph, not a tree |
| No built-in authorship, versioning, or integrity | Every frame is signed, content-addressed, and verifiable |
| Metadata is a sidecar (EXIF, xattr, .DS_Store) | Metadata IS frames — first-class, queryable, same as content |
| "Relatedness" means same folder or a hyperlink | Semantic frames: typed, signed, indexed, traversable |
| Application decides how to interpret it | Item carries its own vocabulary and presentation |
| Search by filename or full-text keyword | Query by meaning across the graph |

The item is what replaces the file for the user. Not at the POSIX level — bytes and streams and file descriptors are a perfectly good substrate for low-level I/O, and they are not going away. But for user-facing data — the things people create, name, share, organize, search for, and care about — the item is the natural unit. It is the thing that knows what it means, because it is made of frames, and frames are meaning.

---

## 7. The Shared Meaning Space

A semantic frame is only as useful as the vocabulary it draws from. If every application defines its own predicates and roles, frames reproduce the same fragmentation as string-keyed key-value pairs — just with more structure. The frame primitive requires a *shared meaning space*: a common vocabulary of predicates, roles, and concepts that is open, extensible, cross-lingual, and grounded in something more stable than any single application's design decisions.

This is an old problem. Gruber (1993) argued that shared ontologies are essential for knowledge sharing and that the key challenge is portability — making ontological commitments that are independent of any particular representation system. Lenat's CYC project (Lenat, 1995) attempted to solve it by hand-encoding millions of common-sense assertions, a heroic effort that demonstrated both the importance of shared knowledge and the intractability of creating it manually. The Semantic Web attempted to solve it through a layered architecture of ontology languages (RDF, RDFS, OWL), but the proliferation of competing ontologies — each defining its own vocabulary for overlapping domains — became a problem in itself.

We propose a different approach: anchor the shared vocabulary not in hand-authored axioms or application-specific ontologies, but in the empirically documented structure of human lexical semantics.

### The vocabulary of types, predicates, and roles

**WordNet** (Miller et al., 1993) provides the foundation. Developed at Princeton over decades, WordNet organizes English into approximately 120,000 *synsets* — synonym sets that represent distinct concepts. Each synset is a meaning, not a word: the synset for "dog" (the animal) is distinct from the synset for "dog" (to follow persistently). WordNet provides hierarchical relationships (dog is a kind of canine, which is a kind of mammal), part-whole relationships, antonymy, and other semantic relations.

**CILI** — the Collaborative Interlingual Index (Bond, Vossen, McCrae, & Fellbaum, 2016) — extends WordNet across languages. CILI provides language-neutral concept identifiers that link WordNet synsets to their equivalents in other languages' wordnets. The English concept "dog," the Spanish concept "perro," and the Japanese concept "inu" share the same CILI identifier. This is not a translation — it is an identity: these words in different languages refer to the same meaning.

Three additional resources provide critical vocabulary for the frame primitive specifically:

**FrameNet** (Baker, Fillmore, & Lowe, 1998; Ruppenhofer et al., 2006), the direct computational heir of Fillmore's theory, provides over 1,200 frame definitions — structured scenes with named roles, hierarchical relationships between frames (inheritance, subframes, causation), and annotated examples from real text. FrameNet is, in a very direct sense, a library of data templates: each frame declares what roles it expects, which are required, and how it relates to other frames. This is the richest source of predicate-to-role mappings available.

**VerbNet** (Palmer, Gildea, & Kingsbury, 2005) organizes approximately 300 verb classes by shared syntactic and semantic behavior, mapping FrameNet's frame-specific roles (Buyer, Seller) to a smaller inventory of universal thematic roles (Agent, Theme, Goal). VerbNet entries include WordNet sense keys, providing a direct bridge from concept to role expectations.

**ISO 24617-4** (the international standard for semantic role annotation, growing from the LIRICS project and the unification work of Bonial et al., 2011) standardizes approximately 25 thematic roles — Agent, Patient, Theme, Goal, Source, Instrument, and others — that are sufficient for characterizing the argument structure of verbs across languages. These roles, validated across VerbNet, FrameNet, and PropBank, provide the binding keys that semantic frames need.

Together, these resources provide three layers of shared vocabulary:

1. **Types and concepts** (from WordNet/CILI): what kinds of things exist — PERSON, BOOK, GAME, LANGUAGE, DOCUMENT — organized hierarchically and linked across languages
2. **Predicates** (from WordNet verb synsets, VerbNet classes): what kinds of assertions can be made — AUTHORED, PURCHASED, LOCATED_AT, TITLED — each declaring the roles it expects
3. **Roles** (from VerbNet, ISO 24617-4): what semantic functions participants play — Agent, Theme, Goal, Source, Instrument — the universal binding keys

### The entity problem

Consider the AUTHORED example from section 5: predicate AUTHORED, bindings (THEME) = The Hobbit, (AGENT) = Tolkien. The predicate AUTHORED is a shared meaning — a grounded concept from the vocabulary. The type PERSON is a shared meaning. The type BOOK is a shared meaning. But what about Tolkien *himself*?

Today, Tolkien exists as a page on Wikipedia, an author page on Amazon, an entry on Goodreads, a profile on TMDB, a record in the Library of Congress authority file, an entry in Wikidata, and countless other disconnected representations across the web. Each of these is a silo. None of them is the *canonical Tolkien* — the single identity that every system could use as the AGENT binding in an AUTHORED frame.

This is the hardest problem the shared meaning space must address, and we should be honest about its difficulty. WordNet provides the *concept* PERSON, but not an identity for every specific person. CILI links meanings across languages, but meanings are types, not individuals. Every previous attempt to solve entity identity at scale has run into the same tension: centralized registries (Wikidata, Library of Congress authority files) are fragile, political, and exclusionary — who decides which entities get canonical identifiers? — while fully decentralized naming is ambiguous — which "Tolkien" do you mean?

The semantic base layer takes a specific architectural position on this. Entities are items — collections of frames with cryptographic identities. Tolkien is not a string, not a URL, not a row in a central registry. He is an item with a stable cryptographic identity, carrying frames that assert his name, his birth date, his works, his relationships. These frames are signed by the people and institutions that assert them.

The mechanism for convergence is the social graph itself. When Alice creates an AUTHORED frame binding a Tolkien entity as the AGENT, she is binding to a specific cryptographic identity — one that she encountered through her trust network. If the Library of Congress publishes a SAME_AS frame linking their Tolkien authority record to Alice's Tolkien entity, and if Bob trusts both Alice and the Library of Congress, then Bob's system can resolve all three as the same entity. Convergence happens not through a central registry but through the accumulation of signed assertions from parties that trust each other.

This is not a complete solution. It is a mechanism — one that trades the problems of centralized identity (political control, single points of failure) for different problems (convergence latency, conflicting identities that must be resolved through trust). We believe this trade-off is correct for a decentralized semantic layer, but we acknowledge that the entity problem remains the area where the architecture is most genuinely unproven.

### Meaning and expression

The resulting architecture separates *meaning* from *expression*: meanings (synsets, concepts, entity identities) are language-neutral; words (lexemes) belong to specific languages and point to meanings. This separation is what makes the system genuinely multilingual without any translation machinery. To "translate" a concept from English to Spanish, you look up the English word's meaning, then find the Spanish word for that meaning. Import English WordNet and Spanish WordNet (both linked via CILI), and you have a bidirectional dictionary covering 120,000 concepts — not as a feature that was built, but as a structural consequence of separating meaning from expression.

### An open commons

The shared meaning space is not a closed vocabulary. It is a commons. Domain-specific communities can extend it with their own concepts (medical terminology, legal concepts, engineering standards), connected to the base vocabulary through the same hierarchical relationships. Individuals can create entities and describe them with frames. New languages can connect to it by linking their words to existing meanings. The vocabulary grows from the edges, not from the center — extensible without fragmentation, because every extension is anchored in the same shared backbone.

---

## 8. Computation as Frames

The claim that semantic frames constitute a genuine base layer — not merely a metadata system for documents and social data — requires demonstrating that the primitive is expressive enough for domains far removed from natural language. Mathematics is the strongest test case: the most formal, least ambiguous domain of structured knowledge. If thematic roles can describe mathematical operations, they are not linguistic conveniences — they are universal structuring principles.

The mapping turns out to be natural, not forced.

### Arithmetic

Consider the simplest case: 3 + 5 = 8. The operation ADD is the predicate. The operands are not Agents (they don't initiate anything) or Patients (they don't change). One is the Theme — the entity being operated on — and the other is the Instrument — the means by which the operation is performed. Even though addition is commutative, natural language reveals the asymmetry: we say "add 5 *to* 3," not "add 3 and 5 symmetrically." The answer is a Result — something that comes into existence through the operation.

```
ADD { (THEME) = 3, (INSTRUMENT) = 5, (RESULT) = 8 }
```

Subtraction makes the asymmetry explicit: 10 - 3 = 7. 10 is the Theme — the quantity being diminished. 3 is the Instrument — the means of reduction. 7 is the Result.

```
SUBTRACT { (THEME) = 10, (INSTRUMENT) = 3, (RESULT) = 7 }
```

This is not a metaphor. Theme ("the thing being acted on") and Instrument ("by what means") are exactly the semantic functions these values serve in the operation. The roles were defined for natural language, but they describe the same cognitive structure: a thing, something acting on it, and an outcome.

### Calculus

The real test is calculus, where operations are rich enough to demand multiple roles.

The definite integral ∫₀¹ x² dx has a clear frame structure:

```
INTEGRATE { (THEME) = x², (SOURCE) = 0, (GOAL) = 1, (INSTRUMENT) = dx, (RESULT) = ⅓ }
```

- **x²** is the Theme — the thing being operated on
- **0** is the Source — where integration begins
- **1** is the Goal — where integration ends
- **dx** is the Instrument — the means by which integration is performed
- **⅓** is the Result — what comes into existence

Source and Goal for the bounds of integration. These roles were defined for physical motion — "move from the house to the store" — but they map onto the abstract endpoints of integration with no strain, because the cognitive structure is the same: a starting point, an ending point, and a traversal between them.

Differentiation: d/dx(x²) = 2x becomes `DIFFERENTIATE { (THEME) = x², (INSTRUMENT) = x, (RESULT) = 2x }`. The variable of differentiation is the Instrument — the means by which the operation is performed.

Limits: lim(x→∞) 1/x = 0 becomes `LIMIT { (THEME) = 1/x, (GOAL) = ∞, (RESULT) = 0 }`. The variable approaches the Goal — the same directional metaphor as physical motion.

### The role mapping

The correspondence is systematic:

| Math concept | Thematic role | Linguistic parallel |
|---|---|---|
| Operand / expression being operated on | Theme | "the thing being acted on" |
| Second operand / applied quantity | Instrument | "by what means" |
| Lower bound / starting value | Source | "where from" |
| Upper bound / ending value | Goal | "where to" |
| Answer / output | Result | "what comes into existence" |
| Both sides of an equation | Pivot | "central participant in fixed state" |
| Degree or magnitude of change | Extent | "by how much" |
| Path of integration (in line integrals) | Path | "the route taken" |

### Why this matters

This mapping is significant not because it enables a math engine (though it does — an expression like `5 meters + 3 feet` is an ADD frame whose operands are quantities with unit sememes, and the system can resolve it because METER and FOOT are both LENGTH units in the vocabulary with known conversion factors). It is significant because it demonstrates that thematic roles are not linguistic artifacts. They are cognitive structuring principles.

Mathematics and natural language both need to express: what is being operated on (Theme), by what means (Instrument), where we start (Source), where we end (Goal), by how much (Extent), and what results (Result). The roles are the same because the underlying cognitive operations are the same — whether the domain is physical motion, financial transactions, linguistic predication, or abstract mathematics.

If a single set of ~25 thematic roles can structure natural language assertions, social interactions, and mathematical expressions, then those roles are genuinely universal — and a base layer built on them is not domain-specific. It is as general as meaning itself.

### Mathematics as a language

The argument goes further. Not only are mathematical operations frames — they constitute a *language* with its own grammar. And that grammar is not a separate system. It is data on the predicates themselves.

An operator like `+` is a meaning in the shared vocabulary, just like the verb "create" or the preposition "to." As a meaning, it carries properties: it is infix (appears between its operands), it has a precedence level, it associates left-to-right. The parser does not maintain a separate precedence table — it reads precedence from the operator, the same way it reads role expectations from a verb. There is no grammar for mathematical expressions. There are predicates with parsing metadata.

This unification has a striking consequence: natural language, mathematical expressions, and domain-specific notations can coexist *within a single input stream*. A user typing "create chess where score > sqrt(9) named rematch" is mixing English ("create chess"), a mathematical sub-expression ("score > sqrt(9)"), and an auxiliary predicate ("named rematch"). These are not three separate inputs handled by three separate parsers. They flow through one resolution pipeline, where each predicate — the verb, the comparison operator, the function, the auxiliary — declares its own parsing behavior. The pipeline consults each one as it encounters them and routes accordingly.

The language being spoken is *inferred from the tokens themselves*. Each resolved token carries a scope — which language's vocabulary it came from. The system counts scopes and infers the active language. It does not assume English. If the tokens come from a Spanish vocabulary, the Spanish parser handles them. If they come from a mathematical vocabulary, the expression parser handles them. Mixed input triggers language switching within a single expression — naturally, without configuration.

This extends to any domain-specific notation. Chess algebraic notation ("e4," "Nf3," "O-O") is, in this framework, a language — a set of meanings with their own parsing declarations. When a user types "e4" in the context of a chess game, the token resolves through the vocabulary to a chess-move meaning, and that meaning's parsing declaration says: "I understand this notation." The result is a MOVE frame — the same primitive as a title assertion or a bank transaction. Same frame, different notation language, same output.

The practical consequence: mathematical and functional expressions are not a separate system bolted onto the side of a semantic layer. They are frames, using the same predicates and roles as everything else. A spreadsheet cell is a frame whose value is the result of an expression frame. The boundary between "data" and "computation" dissolves the same way the boundary between "data" and "metadata" does — because both are role bindings on predicates, and the frame primitive is expressive enough to carry either. And the boundary between "syntax" and "vocabulary" dissolves too — because syntax is just another property that predicates carry, drawn from the same shared meaning space as everything else.

---

## 9. What Follows

If we accept the premises — that computing needs a semantic base layer, that it must be built in rather than bolted on, that the frame is the right primitive, and that the shared meaning space is anchored in empirical linguistics — then several consequences follow. These are not features to be engineered. They are structural properties that emerge from the premises.

**Queryability without crawling.** If every piece of data is a frame with a grounded predicate and semantically-keyed bindings, then the data *is* the index. Querying "all books authored by Tolkien" is not a text search — it is a lookup: frames with predicate AUTHORED, where the binding for AGENT refers to Tolkien. No crawler needs to have visited and interpreted every document. No search engine needs to guess, from word frequency and link structure, what a page is about. The data described itself at the moment of creation. This is not a hypothetical benefit — it is a direct consequence of write-time resolution.

The indexing cost is concrete and bounded. Each frame is indexed by its predicate and by each meaning in its compound binding keys — the same multi-dimensional index described in section 4. For a system with N frames and an average of K bindings per frame, the index contains O(N × K) entries. Queries resolve in O(log N) via standard index lookup. This is not a research problem; it is a well-understood data structure operating on richer keys than most systems use.

**Multilingual interoperability without translation.** If meanings are language-neutral and words are language-specific pointers to meanings, then a Spanish speaker and an English speaker interacting with the same data see it through their own words but operate on the same semantic structures. The English speaker sees "author"; the Spanish speaker sees "autor"; both interact with the same grounded meaning. The system does not translate — it resolves, through different words, to the same concept.

**Trust as data.** If every frame is a signed assertion by an identified party, then trust and provenance are not separate systems — they are properties of the data itself. A "like" is a signed frame. A spam label is a signed frame. A fact-check is a signed frame. Different users, with different trust relationships, see different views of the same underlying data — not because a platform is making editorial decisions, but because trust policies (themselves data) produce different evaluations of the same signed assertions. This is Szabo's (1997) vision of formalizing relationships on public networks, realized through the frame primitive.

**Content-addressed identity.** If frames are the data primitive and their identity is determined by their semantic content (predicate + bindings), then two identical assertions produce the same identity regardless of who makes them or when. Identity follows from content, not from location. This is the same principle as content-addressed storage (Merkle, 1979; Benet, 2014), applied not to opaque bytes but to semantic structures.

**Composability.** Because everything is the same primitive — a frame with predicate and bindings — everything composes in the same way. A document is frames. A chat room is frames. A chess game is frames. A trust relationship is frames. A mathematical expression is frames. There is no structural distinction between "content" and "metadata" and "relationships" and "configuration" and "computation" — these are all frames with different predicates.

**Syntax as vocabulary.** If predicates carry their own parsing behavior — operators declare precedence, functions declare grouping, prepositions declare role assignment, domain notations declare delegation — then there is no separate grammar for each input type. There is one resolution pipeline, and every token's parsing role is determined by the meaning it resolves to. Natural language, mathematical expressions, chess notation, and any future domain syntax all flow through the same mechanism. The language being spoken is inferred from the tokens, not assumed. This is not an engineering convenience — it is a structural consequence of predicates carrying behavior. When syntax is vocabulary, parsing is resolution.

**Self-describing data.** A frame carries everything needed to interpret it. Its predicate says what kind of assertion it is. Its binding keys say what each value means. All of these are references into the shared meaning space. No external schema, no format specification, no application-specific decoder ring is needed. This is the property that files, database rows, and web pages lack — and the property whose absence creates the need for integration layers, documentation, and reverse-engineering.

**Subsumption of platforms.** Consider how much of the modern web is built from the same handful of semantic patterns — patterns that are currently implemented from scratch by every platform, in every silo, with no interoperability. A product listing is frames (PRICE, CATEGORY, LOCATION, DESCRIPTION, OFFER). A community is frames (MEMBERSHIP, MODERATION, TOPIC, MESSAGE). A review is frames (RATING, TOPIC, AGENT). An academic paper's citation graph is CITES frames. A social network's interactions are frames (FOLLOW, POST, COMMENT, BLOCK). Each of these is currently a proprietary database on a proprietary platform. In the shared meaning space, they are all the same primitive — signed frames on items, visible according to trust, queryable by meaning.

Each of these properties is a direct consequence of the architecture, not an independent design decision. This is important because it means the properties are *coupled*: you cannot get some without the others, and you do not need to engineer them separately. A base layer built on semantic frames with a shared meaning vocabulary is, by construction, queryable, multilingual, trust-aware, content-addressed, composable, and self-describing.

---

## 10. Honest Reckoning

We are not the first to propose an ambitious rethinking of how computing handles information. The history of such proposals is largely a history of instructive failures, and we would be foolish to ignore it.

**Xanadu** (Nelson, 1974) envisioned a global, versioned, bidirectional-linking document system with micropayments and transclusion. It got content addressing, versioning, and bidirectional links right — concepts that took decades to resurface in systems like Git and IPFS. It failed because it demanded solving everything simultaneously before shipping anything. After sixty years, it remains unfinished. The lesson: scope ambition ruthlessly. Ship incremental function, not a complete vision.

**CYC** (Lenat, 1995) set out to encode all of common-sense knowledge as logical assertions — millions of hand-authored axioms about how the world works. It got the diagnosis right: computers need world knowledge, not just data. It stalled because hand-authoring millions of axioms does not scale. The knowledge acquisition bottleneck never broke. Lenat himself noted the project's dependence on "a large team of knowledge enterers" — a dependency that proved fatal to the timeline. The lesson: do not try to encode all knowledge by hand. Anchor in existing resources and let meaning emerge from use.

**Croquet** (Smith, Kay, Raab, & Reed, 2003) — Alan Kay's vision of a shared, replicated 3D environment where all computation is transparent and collaborative — got replicated state, late-binding, and seamless collaboration right. It faded because it required a complete runtime (Squeak Smalltalk), could not interoperate with existing software, and presented an interface (immersive 3D) that was ahead of its time. The lesson: platforms that cannot meet users where they already are face adoption cliffs that no amount of technical elegance can overcome.

**Plan 9** (Pike et al., 1995) pushed Unix's "everything is a file" to its logical conclusion: all resources — network, display, processes — accessible as file trees via the 9P protocol. It was technically superior to Unix in almost every way. It failed to displace Unix because it required abandoning the entire Unix software ecosystem. No migration path, no backwards compatibility, no critical mass. The lesson: even a cleaner design loses to an entrenched ecosystem unless it provides a bridge.

**The Semantic Web** (Berners-Lee et al., 2001) got the diagnosis exactly right: the web needs machine-readable semantics. It built a rigorous technical stack (RDF, OWL, SPARQL) that works well in specialized domains. It did not become the web's semantic layer because it was *layered on top of* the web rather than *built into* it — optional, disconnected from content, and requiring expertise most content creators lack. The lesson: a semantic layer that is optional will remain marginal.

What do we learn from these? Several things:

First, **incremental delivery is non-negotiable**. A system that requires completeness before it provides value will never reach completeness. Each increment must be useful on its own.

Second, **build on existing resources rather than inventing from scratch**. CYC tried to encode all knowledge manually. The Semantic Web required ontology engineering for every domain. We would rather anchor in WordNet, CILI, VerbNet, and ISO 24617-4 — resources built and validated by the computational linguistics community over decades. We are not inventing a vocabulary; we are giving an existing, empirically validated vocabulary a new job.

Third, **provide a bridge**. Plan 9 and Croquet demanded that users abandon their existing ecosystems. A semantic base layer must coexist with files, filesystems, and the web. It must be able to represent and interoperate with existing data formats, not require that everything be rewritten. The filesystem is probably never going away — and it doesn't need to. POSIX is a reasonable base layer for low-level byte handling. What it is not, and was never intended to be, is a base layer for *meaning*.

Fourth, **the semantic layer must not be optional**. This is the deepest lesson from the Semantic Web. If creating semantic structure is a separate, additional step from creating data, most people will skip it. The design must make creating data *be* creating semantic structure — the way that writing a sentence *is* expressing meaning, not writing sounds and then separately annotating what they mean.

Can this proposal avoid the fates of its predecessors? Honestly: we do not know. The ambition is large, the history is cautionary, and the engineering challenges are real. But two things have changed that make the attempt more tractable than it was for previous generations.

First, the linguistic resources now exist. WordNet has 120,000 synsets. CILI links them across languages. VerbNet classifies 300 verb classes with thematic role declarations. ISO 24617-4 standardizes the role inventory. UniMorph provides morphological data for 100+ languages. These resources represent decades of cumulative scholarly work. They did not exist when CYC began, when the Semantic Web was proposed, or when Croquet was built.

Second — and this is worth stating plainly — AI assistance has compressed what was previously decades of solo implementation work into feasible timescales. The bottleneck for ambitious software projects has always been the sheer volume of code required to realize a vision. That bottleneck has narrowed dramatically. This does not guarantee success, but it changes the economics of ambition.

The path forward is incremental delivery of a usable system that provides immediate value at each step: frames as a local data format; a shared vocabulary seeded from WordNet and CILI; a local query engine that resolves by meaning; and, eventually, a peer-to-peer network where semantic data is exchanged between nodes connected by explicit trust relationships. Each of these steps is independently useful. Together, they constitute the semantic base layer that computing has been missing since 1970.

Whether it works — whether this particular attempt succeeds where others have not — is an empirical question. We offer it not as a certainty but as a proposal, grounded in established theory and validated linguistic resources, that the time is right to try.

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
