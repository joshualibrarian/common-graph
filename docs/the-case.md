# Below the Application, Above the Bytes

**Joshua Chambers**
*Spring 2026*

---

## Abstract

Two structural properties of modern computing compound to produce what users experience as platform ownership, meaning the grip platforms have on users and their work, not any technical claim users hold over their data.  No layer of the stack stores meaning: data is opaque bytes at every layer below the application, interpretable only by whichever application holds the schema.  And the software that interprets that data has migrated off the user's device and onto corporate server farms: applications are services, not programs, and the most capable consumer hardware ever built runs as a set of rendering terminals.  Users cannot leave because their data has no meaning without the application, and the application is not something they even run.

This paper argues that both gaps must be addressed at the same level and in the same primitive.  It proposes a base layer built on two co-equal pillars: a shared semantic commons and a local-first peer-to-peer substrate.  The semantic primitive is the frame, a predicate-role structure drawn from Fillmore's frame semantics and grounded in empirically validated linguistic resources (WordNet, the Collaborative Interlingual Index, FrameNet, VerbNet, ISO 24617-4).  Keys refer to meanings, not strings.  The substrate is a peer network of local runtimes holding content-addressed, cryptographically signed items described by frames.  Frames carry meaning.  Items carry identity.  Trust drives routing.  Computation happens wherever the relevant frames live.

The result is a base layer where data is self-describing and queryable by meaning; where applications are interchangeable local runtimes rather than hosted services; and where the structural asymmetries that define the current platform era dissolve because there is nothing platform-specific left to own.  Neither pillar is novel in isolation.  Semantic structuring has been pursued for decades; local-first software has an established tradition.  What is new, and what this paper attempts, is the claim that both belong in the same primitive, at the same layer, as an open commons.

---

## 1. The Semantic Void

In 1945, Vannevar Bush described the central problem of information management: "The summation of human experience is being expanded at a prodigious rate, and the means we use for threading through the consequent maze to the momentarily important item is the same as was used in the days of square-rigged ships" (Bush, 1945).  Eighty years later, the maze is incomparably larger, and the threading, while faster, is no less indirect.  The reason is structural.  It sits below every tool we build on top of it, and it has been there since the foundational layers of modern computing were laid down.

Consider what happens when a user saves a photograph.  The filesystem records bytes at a path.  The operating system tracks the file's size, its modification time, its location on disk.  The image format encodes pixels and, sometimes, a small amount of EXIF metadata: camera model, GPS coordinates, exposure settings.  Yet no layer of the stack knows what the photograph *is*.  Who is in it.  What occasion it documents.  How it relates to other photographs, to the people depicted, to the day it was taken.  That knowledge exists only in the user's head, or in the proprietary database of whichever application the user happens to use.  The stack has stored the photograph without storing any of what makes the photograph matter.

This is not specific to image files.  It is a structural property of every layer beneath the application:

- **The filesystem** sees bytes at paths.  Whether those bytes are a novel, a spreadsheet, or a genome sequence is invisible to the layer that stores them.
- **The operating system** sees processes, file descriptors, and memory pages.  It cannot distinguish a medical record from a restaurant menu.
- **The network layer** sees packets with source and destination addresses.  HTTP adds content-type headers, which identify *format* (text/html, application/json), never *meaning*.
- **The database** sees rows and columns, or documents and collections.  Its schema is local to the application that defined it.  Two databases storing information about the same person share no vocabulary for describing what they hold.
- **The web** sees pages at URLs.  Search engines exist precisely because the web cannot answer "what is this about?"  Third parties crawl billions of pages, guess at meaning from word frequency and link structure, and sell access to their proprietary guesses.

Each layer was designed for generality, and each achieves it the same way: treat data as opaque and leave interpretation to the layer above.  This is a defensible engineering choice when generality is the goal, but the cumulative consequence is that meaning has no home in the architecture.  Every application that needs to do anything with meaning must build its own semantic layer from scratch: its own schema, its own vocabulary, its own query logic, its own integration adapters.  The cost is immense but invisible, because it is paid everywhere and attributed nowhere.

The cost takes familiar forms.  Every API integration is a bespoke translation between two systems that cannot describe their own contents to each other.  Every search engine is a probabilistic compensation for the fact that data does not know what it means.  Every data migration is an exercise in reconstructing meaning that was present in the creator's mind but never captured by the infrastructure.  The most recent and most expensive compensation is the large language model.  The current wave of AI is, at its core, an effort to recover meaning from data that never stored it.  LLMs are trained on vast corpora of human text precisely because meaning lives in the text, not in the infrastructure that holds it.  Many of the tasks they perform (classification, extraction, translation, summarization, relationship discovery) are tasks a semantic layer would make trivial, because those tasks reduce to structured lookups when data already carries its meaning.  We are building ever-larger statistical models to guess at what could have been recorded directly.

A smaller but revealing instance of the same pattern appears wherever applications exchange key-value pairs, which is nearly everywhere.  Configuration files, HTTP headers, database rows, JSON objects, environment variables: the key-value pair is the most fundamental composable pattern in computing.  Yet because keys are application-defined strings, they are fractured beyond repair.  One system's `author` is another's `creator`, another's `created_by`, another's `dc:creator`, another's `writtenBy`.  They all mean the same thing, yet no layer of infrastructure knows this.  The fragmentation is not cosmetic, it is what makes integration between systems the hardest and most durable cost in the industry.

When no layer below the application stores meaning, meaning must live somewhere, and the only place left is application code.  An application's schema and interpretation logic become the sole route from bytes to whatever users actually care about.  Users do not care about bytes.  They care about photographs, messages, relationships, purchases, conversations, every one of which is a structured interpretation.  Whoever holds the code that produces the interpretation holds the thing users actually value.  Data without an application that understands its schema is bytes without value.

Exporting data is therefore possible but insufficient.  Standard export mechanisms (GDPR requests, JSON dumps, CSV downloads) deliver bytes accompanied by a schema that is meaningful only within the originating application's conventions.  Another application can interpret those bytes only by reconstructing that schema, a translation that is expensive at scale, lossy at the edges, and bespoke per platform.  Users can leave nominally but not practically.  The gap between nominal and practical exit is what we experience as platform ownership.  It is not a policy that could be legislated away by mandating more export formats, it is the shape of the substrate.  Every data-portability regulation confronts the same underlying constraint: bytes move, meaning does not.

A semantic base layer would alter this equation at its foundation.  If data carries its own meaning at the moment of creation, any application that understands the shared vocabulary can interpret it, so exit cost approaches zero and the application becomes interchangeable.  The bundle of data, schema, interface, social graph, and moderation that platforms sell as one thing unbundles, because each component becomes expressible in the same substrate.  What we call the platform's moat, the structural advantage that makes leaving impractical, does not survive that unbundling; it dissolves not through regulation or competition but because there is nothing bundle-specific left to own.

That is the first of two structural gaps that together produce the platform era.  The other operates at a different layer of the stack, and its emergence is more recent.  The next section turns to where the software that interprets your data has gone.

---

## 2. The Age of SaaS

Consumer computing has passed through two completed eras and is deep into a third.  The trajectory has not been linear.  The current moment is, in a specific sense, a return to something the industry already left behind, with the critical difference that the centralization is now a commercial choice rather than a hardware necessity.

In the mainframe era of the 1950s through the 1970s, compute was centralized because it had to be.  The hardware was expensive, rare, and physically immobile, and users reached it through terminals with just enough intelligence to negotiate a session.  All real work happened in the machine room.  This was not consumer computing in any meaningful sense; mainframes were institutional.  Still, the arrangement established a pattern: compute in one place, access from another, the asymmetry between them non-negotiable.

The personal computing era of the early 1980s through the mid-1990s was the first time ordinary people could run real software on their own machines.  The IBM PC, the Macintosh, and their peers put meaningful compute on individual desks.  Software came on floppies and later on CDs, installed locally, and ran locally.  The user's data lived on the user's disk.  The software itself was almost always proprietary, closed-source, and licensed rather than owned, but the locus of computation and state was local.  The user ran the software, held the data, and controlled when the software ran, and when it stopped.  The vendor controlled the code; the user controlled the operation.

The networked era began in the mid-1990s and continues in steadily deepening form.  The web started as a document-retrieval system and evolved into an application-delivery system through incremental accretion: HTML learned forms; JavaScript learned to manipulate the page; AJAX learned to talk back to servers without reloading.  The browser slowly became a runtime, but the runtime's job was to render what a server decided, and each accretion moved the locus of computation further from the user's machine.

What began as hypertext documents has become software as a service (SaaS).  Applications are no longer something you run, they are something you access.  They live in data centers you do not operate, on infrastructure you do not own, under subscriptions you do not escape without losing access to your own work.  Nearly every productive tool a modern user touches (email, documents, design work, communication, project management, calendar, photos, notes, code hosting, source-control hosting, payment processing) is a service running on someone else's computers.  Even the tools that retain a local presence, like Slack or Discord or Photoshop or 1Password, are clients for remote services, useless if the service goes away.

The web protocol carries part of the responsibility for where this has led.  HTTP was designed for document retrieval: a client asks a server for a thing, the server sends it.  Everything interactive we have built on that foundation has preserved the direction of authority.  The server holds state, runs logic, and decides what to send; the client renders.  JavaScript in the browser has closed some of this gap, but the direction is unchanged: the client's code is code the server chose to ship, running against data the server chose to expose.  When the server goes away, the client becomes decoration.

The most powerful consumer computers ever built are being used as near-dumb terminals.  The phone in a user's pocket in 2026 has more processing, more memory, more storage, and more bandwidth than the mainframes of the 1970s.  A modern laptop has more capacity than most users' everyday workloads come close to using.  Yet very few consumer-facing applications actually run on these machines; the device renders what a server farm computes.  The sophistication of the hardware is spent on rendering engines rather than on the work the user came to do.  This is not a technical outcome but a business outcome, imposed on users whose hardware could do vastly more if anyone would let it.

Running the computation is how you own the decisions, and the set of decisions a server makes on a user's behalf is larger than users typically consider.  Recommendation order, feed curation, content moderation, search ranking, fraud detection, A/B test assignment, price discrimination based on account signals, the shape of what is shown and the timing of when it is shown, the filtering of what is hidden.  All of this happens on servers, inside code users cannot inspect, on data users cannot access, subject to policies users did not agree to and cannot amend.  Even if a user's data were semantic and portable, the decisions made about that data would still belong to whoever was running the application logic.  Platforms own not just the meaning of data but the agency over it.

Data opacity and the SaaS era are complementary structural causes of platform ownership, and fixing only one is insufficient.  Semantic data trapped on server-side infrastructure is no more portable than opaque data on server-side infrastructure.  Local compute operating on opaque data is no more powerful than remote compute operating on opaque data.  Together the two produce the condition users experience in the current era: your data means nothing without the application, and the application is not something you run.

What is missing is not a better search engine or a smarter parser, and not a different platform hosting the same structural pattern.  What is missing is a layer where meaning is the fundamental unit of storage, identity, and retrieval, and where that layer lives on hardware the user actually controls.  Both gaps must be closed in the same layer, or neither closure is effective.

---

## 3. How We Got Here

Neither gap is the result of inattention.  The historical conditions that produced them have been understood for decades, and serious attempts to close them have been made on both sides.  Each deserves a closer look, because the failure patterns explain why the substrate itself, rather than another addition to it, is the only way forward.

When the foundational layers were laid down in the 1970s, nodes were disconnected and bytes were precious.  The byte-stream abstraction (everything is a file, a file is a sequence of bytes) was a practical triumph given the constraints.  TCP/IP, HTTP, SQL: each subsequent layer solved the problem in front of it with the resources available.  A semantic data model was not rejected, it was beyond the horizon.  The centralizing trajectory of the commercial web was even further out.

The semantic gap persisted in part because the linguistic foundations for closing it took decades to mature.  A semantic key cannot be a string; it must refer to a stable, language-independent concept with a hierarchy, cross-lingual equivalents, and participation in structured scenes.  Building those objects requires empirical research into how meaning is structured across human languages.  The resources that make it tractable (WordNet, CILI, FrameNet, VerbNet, ISO 24617-4) are products of computational linguistics that have only recently reached the maturity needed to serve as a practical foundation.  And once they did, the commercial landscape of the 1990s and 2000s ran in the wrong direction: every major platform held its data models close because controlling the model meant controlling the ecosystem.  Interoperability was a competitive threat to the kind of cross-organizational collaboration a shared semantic foundation requires.

The locality gap has a different shape.  The hardware became capable enough for serious local computation by the mid-1990s and has only grown more so.  What drove computation away from users was not a technical limit but a convergence of commercial incentives: cloud hosting got cheap, network effects rewarded centralization, and the subscription business model worked perfectly for services and not for shipped software.  For any product taking shape in the 2000s and 2010s, a hosted service was easier to monetize, easier to update, easier to monitor, and easier to prevent users from leaving.  A generation of developers grew up with SaaS as the default mental model rather than as the historical anomaly it is.  Local-first and peer-to-peer architectures remained technically viable throughout this period but lacked the commercial pull, carried as a counter-current by specific communities (Kleppmann's academic work, Freenet, Secure Scuttlebutt, IPFS, and others) without displacing SaaS as the industry default.

That ambient state has been the backdrop for a second history: deliberate attempts to retrofit semantic structure onto opaque substrates and decentralization onto server-centric ones.  None has become foundational.  Their failures, taken together, point at the same diagnosis.

### The semantic retrofits

**The Semantic Web** is the most ambitious attempt on the semantic side.  Berners-Lee's 2001 vision described a web in which "information is given well-defined meaning, better enabling computers and people to work in cooperation" (Berners-Lee et al., 2001).  The technical realization (RDF triples, OWL ontologies, SPARQL queries) is rigorous and powerful.  Twenty-five years later, RDF is widely used in specialized domains (biomedical ontologies, library science, government data) but has not become a general-purpose semantic layer.  The web remains overwhelmingly opaque bytes at URLs.

RDF's genuine strengths are substantial: a universal graph model, formal inference via RDFS and OWL entailment, a powerful query language in SPARQL.  In specialized domains where those capabilities matter, RDF has proven its value.  Three structural problems kept it from becoming general-purpose.

First, RDF annotates existing resources.  It is layered *on top of* the web, not *built into* it.  A web page can exist without any RDF.  Most do.  The semantic annotation is optional, which means it is absent in the vast majority of cases.  The cost of creating semantic metadata falls on the producer while the benefit accrues to the consumer: a classic misaligned-incentive problem.

Second, RDF requires the author to commit to an ontology (Gruber, 1993).  In practice, choosing and using an ontology correctly is hard.  It requires expertise that most content creators do not have and are not motivated to acquire.  The Semantic Web effectively asks every web author to be a knowledge engineer.

Third, the annotation is disconnected from the content.  The RDF description of a web page is a separate artifact from the page itself.  It can become stale, incorrect, or inconsistent without any mechanism to detect the divergence.

Concrete systems built on the full RDF/OWL/SPARQL stack, such as the Open Semantic Framework (Structured Dynamics, 2009), confirmed these limitations in practice: technically rigorous, adopted in narrow domains, but unable to achieve the general-purpose uptake the vision required.  The project went quiet after 2016.

**Schema.org** addressed some of these problems by providing a single vocabulary backed by major search engines.  Its adoption is broader than RDF/OWL, precisely because it is simpler and because search engines provide a direct incentive (better rankings) for using it.  Schema.org remains a metadata annotation regardless: a sprinkle of JSON-LD in an HTML header.  It describes pages *about* things, not the things themselves.

**Dublin Core**, **EXIF**, **ID3 tags**, **OpenGraph**, and dozens of other metadata standards each solve a narrow problem.  They do not compose.  A photograph with EXIF data and a document with Dublin Core metadata cannot be queried together because they share no vocabulary, no addressing scheme, and no common notion of what "subject" or "creator" means.

The structural lesson from these efforts is crisp: **you cannot make a semantically inert layer semantic by annotating it.**  The annotation is always optional, always disconnected from the content, always maintained by a different process, and always expressed in a vocabulary local to one standard or domain.  The layer itself remains opaque.

### The locality retrofits

A parallel set of attempts has tried to retrofit decentralization and user control onto an infrastructure whose business model depends on their absence.  Each of these efforts was built by thoughtful practitioners and has adoption within specific communities.  None has become the default.

**XMPP** (originally Jabber, 1999 onward) was an earlier federation attempt focused on messaging and presence.  It saw significant adoption in the 2000s, including as the transport for Google Talk and early Facebook Chat.  That adoption proved instructive: once the largest deployments sat in proprietary hands, those operators withdrew, leaving the ecosystem without the network effects that had made federation useful.  A cautionary tale about relying on large commercial adopters to carry a federated protocol.

**The Fediverse** (Mastodon, Pleroma, and other ActivityPub-based systems) provides a federated alternative to centralized social media.  Users choose an instance, and their accounts interact across instances via ActivityPub.  The model genuinely decentralizes operation: there is no single company whose servers must run for the network to function.  Its adoption grew substantially after high-profile disruptions at centralized platforms.  ActivityPub is a federation protocol, however, not a local-first substrate.  User data lives on the chosen instance's servers.  Moving between instances is an operation that often loses history or followers.  The instance operator remains a gatekeeper for the user's experience; this approach has distributed the gatekeepers, not removed them.

**Solid** (Berners-Lee and collaborators, 2016 onward) proposed personal data "pods": user-controlled storage that applications request access to.  The direction is correct, but Solid layers atop HTTP and OAuth, and applications still carry proprietary schemas for what the pods hold.  Without a semantic substrate underneath, the pod is another storage location rather than a reorientation of where meaning lives.

**Secure Scuttlebutt** (Tarr et al., 2019) demonstrated a fully peer-to-peer social protocol with cryptographic identity and append-only signed logs.  Technically it is closer to what a local-first substrate should do.  Adoption required users to manage their own identities and accept a user experience shaped by a research-grade protocol, however.  The community that embraced it has been small and committed, and SSB has not displaced mainstream social software.

**FreeNet** (Clarke et al., 2001) was one of the earliest sustained attempts at a peer-to-peer substrate for publishing and retrieval.  It introduced content-addressed storage routed through a distributed overlay, on the premise that data could be held by peers without any central server knowing where it lived.  Its design directly influenced much of what followed, and its continued development across more than two decades, including a substantial recent rewrite, demonstrates both that the technical approach is viable and that it still rewards fresh thinking.  FreeNet has not reached the mainstream because its user experience, content model, and threat-model trade-offs shaped it for a specific community, anonymity-focused publishing, rather than for general-purpose use.

**BitTorrent** (Cohen, 2003) demonstrated at massive scale that peer-to-peer distribution works when the architecture fits the problem.  In its trackerless form, using a DHT, it operates without any central coordinator at all, and it has carried significant fractions of global internet traffic for over two decades.  It solves bulk file transfer extremely well, but does not attempt to be a substrate: files remain opaque, with no common data model, identity, or semantic structure.

**Git** (2005) is the most widely used distributed system in the world and an instructive case: technical decentralization can survive while cultural centralization takes hold anyway.  Each clone is a full repository; content-addressing via SHA-1 (migrating to SHA-256) making every object identifiable by its content; any repository can sync with any other over any transport; no single server is architecturally required.  Yet the surrounding workflow (pull requests, issue tracking, CI, code search, discovery) became the value proposition of GitHub and a handful of similar platforms (GitLab, BitBucket, and others), and the developer community centralized around them despite Git itself being fully distributed.  The lesson cuts against complacency about technical decentralization: it is necessary but not sufficient.  If the workflow and social layers around the artifact become the real center of gravity, those layers become the point of centralization, and the underlying tool's distributedness does not save users from a new gatekeeper.

**IPFS** (Benet, 2014) builds on FreeNet's lineage, on BitTorrent's chunked-distribution model, and on the broader distributed-hash-table research tradition (Chord, Kademlia, Pastry, Tapestry and their descendants) that made decentralized lookup practical at scale.  It provides content-addressed storage and a peer-to-peer distribution network, solving the real problem of moving bytes between peers without a coordinating server.  IPFS on its own, however, is a storage layer.  The data moving through it is still opaque.  Without a semantic substrate, a file retrieved from IPFS is the same bytes one would retrieve from any CDN, with the same interpretation problem.

**The AT Protocol** (underlying Bluesky) and **Matrix** take different approaches to federation with real trade-offs around identity portability and decentralization.  Neither carries data with meaning in the sense this paper means; both remain federated services rather than a reoriented substrate.

Two structural lessons emerge, mirroring but distinct from the semantic one.

**You cannot make a server-centric layer local-first by federating it.**  Federation (Fediverse, Solid, AT Protocol, Matrix) distributes the servers but preserves the client-server boundary.  The data still lives on servers; interpretation still requires application code that lives on servers; agency over what happens to the data still belongs to whoever operates the server.  The problem was never the number of servers; it was the architectural privilege of being a server.

**Peer-to-peer transport solves a real problem, genuinely well.**  FreeNet, BitTorrent, SSB, and IPFS each made real contributions: content addressing, distributed hash tables, incentive-compatible chunk exchange, append-only signed logs.  The problem of moving bytes between peers without a central coordinator is solved by them.  Their limitation is not in execution but in assumption: each treats all data as homogeneous.  A torrent is a torrent; a CID is a CID; a blob is a blob.  A movie, a medical record, a chess move, and a photograph are the same kind of thing as far as any of these protocols is concerned, opaque bytes to be stored and retrieved.  Real data is not homogeneous.  A chess move has a player, a piece, and a destination square.  A photograph depicts specific people at a specific place.  A license grants specific rights from a specific party.  A substrate that cannot see these differences remains a transport layer, not a foundation on which applications become interchangeable.

### The common lesson

The three patterns of retrofit converge on a single diagnosis: additions cannot compensate for a substrate whose shape is wrong for the job.  Annotation does not make opaque layers semantic.  Federation does not remove the client-server boundary.  Peer-to-peer transport does not carry meaning.  Each is an addition to an architecture that was not built for what the addition requires, and each fails in the specific way the underlying architecture leaves no room for it to succeed.  Closing either gap requires a different substrate, not another addition to this one.

The solution must be a *layer* where creating data is simultaneously creating semantic structure and locating that data in a user-controlled peer.  The two properties are not separate operations, and neither can be supplied by annotation, federation, or transport alone.

### What's different now

Four things have converged to make such a substrate possible now in a way it was not before.  First, the computational linguistics infrastructure matured: WordNet, CILI, FrameNet, VerbNet, and ISO 24617-4 collectively provide the grounded vocabulary the semantic pillar needs.  Second, the technical infrastructure for distributed state matured as well: CRDTs have become practical, signing cryptography is cheap enough to apply at the per-message level, content-addressing is ubiquitous (every Git commit is a use of it), and local-first synchronization has moved from research topic to shipping practice.  Third, global interconnection made shared vocabularies both necessary and viable; the network that makes the semantic problem acute is the same network that makes a collaborative solution practical, and the same network over which a peer substrate would operate.  Fourth, the open-source movement created the collaborative environment such a commons requires.  The linguistic databases carry permissive licenses.  The cryptographic foundations are open.  The storage and networking building blocks are open.  A base layer along both pillars is inherently a commons: it only works if shared, and it can only be shared if open.  That commons is now possible in a way it was not during the era of proprietary platform wars and server-centric default architectures.

What such a base layer would actually require is the subject of the next section.

---

## 4. What a Base Layer Requires

If neither a semantic layer nor a local-first substrate can be achieved by annotating, federating, or moving bytes peer-to-peer on top of what we already have, what must a new foundation look like?  The missing layer sits above the raw byte substrate that filesystems and networks already provide, and below the application that currently monopolizes interpretation; hence the title.  The answer to what it requires has two halves.  The first concerns meaning: what must data be for any application to interpret it?  The second concerns locality: where must the data and its interpretation live?

(A note on terminology: "semantic layer" is already used in the data analytics industry to mean a translation layer between database schemas and business concepts, as in tools like Looker and dbt.  What is meant here is different.  A BI semantic layer sits above the data and translates queries; the layer proposed here would sit *with* the data, because the data would already know what it means.)

### Grounded predicates, not strings

The key-value pair is everywhere in computing, and the key is always a string.  One system stores a date of birth as `dob`, another as `date_of_birth`, another as `birthDate`, another as `geburtsdatum`.  They all mean the same thing; nothing in the infrastructure connects them.  The root of this fragmentation is that strings have no inherent connection to what they denote.  A semantic layer requires keys that carry *meaning*, not just labels.  The key must refer to a concept, not a string, and that concept must be shared across systems, applications, and languages.

The problem of vocabulary sharing across systems was formalized in foundational ontology research.  Tom Gruber (1993) argued that systems cannot meaningfully share knowledge without committing to shared vocabularies whose terms have agreed-upon meanings, and laid out design principles (clarity, coherence, minimal ontological commitment, and others) for the ontologies such sharing requires.  The Semantic Web pursued this insight through URI-identified predicates.  URIs, however, are locations, not meanings.  They are globally unique, but they do not carry semantic content intrinsically.  Two different URIs can denote the same concept (`schema.org/author` vs. Dublin Core's `dc:creator`), and nothing in the infrastructure connects them.

What we need are keys that refer to *meanings*: language-independent, application-independent units of semantic content with stable identities.  Decades of computational linguistics research have produced extensive catalogs of empirically validated meanings, organized by hierarchy and cross-linked across languages.  These resources do not hand us a finished vocabulary with stable identities, but they give us the raw material from which one can be built.

### Structured assertions

A flat key-value pair (`author: Tolkien`) captures a single relationship, a simple assertion, but loses the structure that gives it meaning.  Who is asserting this?  About what?  In what capacity?  A key-value pair has no structure to express the *kind* of relationship, the *participants* and their roles, or the *context* in which the assertion holds.

What we need is the frame pattern: a **predicate** that defines a structured assertion, and **role bindings** that fill its slots with values.  The theoretical foundation for this pattern comes from Fillmore's frame semantics (1968; 1982), and the empirical grounding comes from computational-linguistics research (FrameNet, VerbNet, ISO 24617-4) that has catalogued and standardized the 25 universal thematic roles human languages use to describe who did what to whom, where, when, how, and why (Youn et al., 2016).

### Write-time resolution

This is the core inversion.  The dominant pattern in computing is to store data first and try to determine its meaning later.  Some systems capture partial structure through schemas, but those schemas are local to the application; the meaning does not travel with the data.  Search engines crawl, natural language processing systems annotate after the fact, data integration pipelines map between schemas post-hoc.  All of these are attempts to recover meaning that was present in the creator's mind but never fully captured in the infrastructure.

A semantic base layer reverses this. Meaning could be resolved *at the moment of creation*, when it is trivially easy, because the creator knows what they mean. The disambiguation that search engines and NLP pipelines struggle to perform after the fact is effortless at write time. When a user creates a relationship between a person and a book, they know whether they mean "authored," "edited," "reviewed," or "purchased." If the layer captures that distinction as a grounded semantic predicate at creation time, no subsequent system ever needs to guess.

The predicate, once chosen, tells the system what roles to expect. The system can prompt for them, offer completions, validate inputs. The act of creating data *becomes* the act of resolving meaning, because selecting a predicate and filling its roles is inherently a semantic operation.

This is not natural language understanding.  Such a layer need not parse free text and try to extract meaning.  It would structure the input environment so that meaning is captured as a natural consequence of creation.  The user selects a predicate, fills roles, and the result is a grounded semantic structure.  The hardest problem in NLP (disambiguation) is trivially solved at write time by the person who knows what they mean.

### Cross-lingual stability

A semantic layer that works only in English is an English-language metadata standard, not a semantic layer.  The concept that English speakers call "dog," Spanish speakers call "perro," and Japanese speakers call "犬" is the same concept.  A semantic layer must represent meanings independently of the words that express them.

This requires a clean separation between *meanings* and *words*.  Meanings (which I will call **sememes**, following usage in structural semantics) are language-neutral units with stable identities.  Words, in their role as language-specific expressions that point to meanings, are called **lexemes**.  Each lexeme belongs to a particular language and carries the morphological apparatus of that language (inflection, conjugation, case, gender, tense).  Multiple lexemes across languages can point at the same sememe: the English "authored," the Spanish "escrito," and the German "verfasst" are three lexemes, one sememe.  The predicate AUTHORED exists independently of any of them.

The four requirements above describe data.  Four more describe where the data lives and who runs the code that interprets it.

### Cryptographic identity

The preceding sections used "identity" to describe the stable referent of a meaning in the vocabulary: the concept DOG is identifiable across languages and systems because it refers to one meaning, not a string.  A different kind of identity is needed for the locality pillar: the identity of *participants*.  Users, organizations, devices, services, anything that signs assertions or holds data must be identifiable without depending on a central authority to assign or revoke that identification.

In practice, this means a participant's identity would be a cryptographic keypair rather than a row in a registry.  The paradigm is not new: Pretty Good Privacy (PGP; Zimmermann, 1995) established the foundational design thirty years ago, in which identity is a keypair, trust is a web of signed endorsements rather than a certificate authority, and no central registry is required.  What PGP demonstrated for email, a base layer would generalize to all assertions.  A user should not need to register with a service or keep an account active on a server to exist as a participant.  The keypair is something the user holds, generates locally, and presents when they sign an assertion or establish a relationship.  No authority grants it; no authority can revoke it (though peers can choose to stop trusting it, which is a social decision, not an architectural one).

This is the minimum condition for agency without a gatekeeper, and the foundation on which everything else in the locality pillar rests.  Signatures depend on the signer having a key.  Relationships depend on the parties being identifiable.  Content attribution depends on the assertion being linked to a verifiable identity.  Each of these builds on identity being a thing users hold rather than a thing services grant.

### Content-addressed data

In most existing systems, data is named by where it lives: a filesystem path, a URL, a database row ID, a cloud storage key.  Move the data and the name breaks.  Copy the data and the copies have no way to recognize each other as the same thing.  The name is an address, not a fingerprint.

Content-addressing (Merkle, 1979; Benet, 2014) reverses this.  Data would be named by a hash of its content, a cryptographic fingerprint derived from what the data *is* rather than where it happens to be stored.  The same data retrieved from any source produces the same fingerprint, so copies are recognizably identical and integrity is verifiable locally.  This is what decouples data from storage location, which is in turn what makes data portable between peers without losing its meaning, provenance, or relationship to other data.  Storage would become commodity; hosting would become a user-revocable choice rather than a platform commitment.

### "Social networking"

Identity is a keypair and data is content-addressed, yet neither on its own determines the shape of the network.  Where does data live?  How does it reach the people who care about it?  How do users find data they do not yet know exists?  Who can see what?  In a centralized architecture, a server answers all of these: it stores the data, routes requests, curates discovery, and gates access.  A substrate without servers needs a different organizing principle.

Peer-to-peer systems have historically answered these questions through arbitrary distance metrics over a hash space (Freenet, Kademlia, Chord, Pastry and their descendants).  Data is stored on nodes whose IDs are mathematically "close" to the content's hash, and found by navigating toward that hash through successive hops.  A peer is responsible for a piece of data not because it cares about the content but because of a mathematical property of the ID.  This works technically, but it treats all peers as interchangeable and all content as equally relevant, which is rarely true in practice.

Data is not uniformly relevant.  Every person cares about specific data: their own work, data from people they know, data about topics or places or communities they follow.  Every organization is the same, caring about its own operations and about data from specific partners, customers, and peers.  Relevance follows relationships, between individuals, between organizations, between communities, between devices and the people who use them.

If relevance follows relationships, then relationships should organize the network.  Data would live with the peers who care about it, travel along the connections that make it relevant, and surface to new users through shared relationships rather than algorithmic curation.  Access would follow from whom the holder chose to include.  Sociologists have studied such structures under the name *social networks* for more than half a century (Barnes, 1954 and the social-network-analysis tradition that followed), as the shape through which information, influence, and resources actually flow among humans.  The commercial platforms that adopted the phrase "social networking" in the late 1990s and 2000s built closed enclosures around a small slice of this phenomenon and sold access to it.  The substrate proposed here is aimed at the underlying structure rather than any particular enclosure of it: a network whose topology is the social graph itself, not a product that owns and resells it.

The claim that social structure can serve as an organizing principle is not just philosophical.  Human social networks empirically exhibit *small-world* properties (Milgram, 1967; Watts & Strogatz, 1998): despite their sparseness, most pairs of people are connected through a small number of intermediate relationships.  Data stored and discovered along the social graph can therefore reach most users through a handful of hops on average, even at scale.  The efficiency is not an algorithmic property of the substrate but an empirical property of the network the substrate traces.  This does not mean the social graph is a perfect topology.  Communities can be insular, trust circles can overlap unevenly, and some users may sit at the edges of the network with few connections.  The claim is not that social structure eliminates all routing and discovery problems, but that it is a better organizing principle than either centralized servers or socially-blind distance metrics, and that the gaps it leaves are addressable through mechanisms (well-connected community peers, public directories, opt-in discovery services) that complement rather than replace the social structure underneath.

Users of today's centralized services store, discover, and access data through relationships inherited rather than chosen: the employer's cloud, the default search engine, the platform where the relevant people already happen to be.  Replacing inherited structure with deliberate choice, expressed in the substrate itself, is the shift.

The social graph, used as the organizing principle, answers the questions this section opened with at once: data lives with the peers who have relationships with it (storage), travels along those relationships when needed (routing), surfaces to new users through shared connections (discovery), and remains visible only to those the holder chose to share with (access).

### Local execution

The previous three requirements address the data: how it is signed, how it is named, and how it is organized across the network.  The fourth addresses the code that interprets it.

In the SaaS model, the runtime lives on the server.  The user's device receives the results of computation it did not perform, rendered into a form it cannot inspect or modify.  The application is something the user accesses, not something the user runs.  As we saw earlier, this is where decision-ownership lives: recommendation, curation, moderation, pricing, filtering all happen server-side, inside code users cannot see.

Local execution reverses this.  The runtime that interprets data and acts on it would run on the user's machine.  The user's device would be a full participant rather than a renderer, and the computation that turns data into experience would happen there.  This does not mean every computation must be local (some workloads require specialized infrastructure), but it means the *default* is local, and remote computation is an explicit delegation rather than the only option.  The user's relationship to their own data stops being mediated by code they do not control.

These requirements do not name a structure, but rather constrain one.  Whatever fits has to be built around meaning rather than strings, carry role-keyed values rather than flat attributes, be complete at the moment of writing, survive translation between languages, be identified by content rather than location, carry identity through keypairs rather than server accounts, route along deliberately-chosen relationships rather than through central brokers, and execute locally rather than remotely.  None of these requirements is individually novel.  What would be new is asking a single structure to satisfy all of them at once, and to do so as the foundation of a layer rather than an annotation or federation laid over existing layers.

---

## 5. The Frame as Primitive

The preceding sections diagnosed two structural gaps and laid out what closing them would require: eight properties, four semantic and four locality, that a new substrate must satisfy simultaneously.  The diagnosis is complete.  What follows is a proposal: a specific primitive and the architecture that grows from it.  The primitive is not the only shape that could satisfy the requirements, but it is the one this paper develops, and it has the advantage of being grounded in half a century of empirical linguistics rather than invented from scratch.

Fillmore's *semantic frame* (1968; 1982) was designed for a different purpose: analyzing what sentences mean rather than structuring data.  Using it as a data primitive is an approach the linguistic literature never had occasion to consider.  A semantic frame, in this usage, is:

```
Frame {
    predicate:  a grounded meaning              (what kind of assertion)
    bindings:   compound-key → value pairs      (the semantic content)
}
```

A predicate and its bindings.  Nothing else is structurally required.  Each binding maps a **compound key** (a sequence of one or more grounded meanings that together identify what the binding *is*) to a value.  A key might be a single role like `(AGENT)`, or a qualified sequence like `(VALUE, ENGLISH)` where additional meanings narrow the role with arbitrary precision.  Every element of the frame (what it asserts, what it is about, who is involved, what content it carries) is expressed as a binding on the predicate.

A **title assertion**: `TITLE { (THEME) = the-book, (VALUE, ENGLISH) = "The Hobbit" }`.  A separate frame carries the Russian title: `TITLE { (THEME) = the-book, (VALUE, RUSSIAN) = "Хоббит" }`.  These are two independent assertions, each separately signable, because a translator should not need the original signer's key to add a translation.  The compound key `(VALUE, RUSSIAN)` distinguishes the binding from `(VALUE, ENGLISH)` through the language qualifier.

A **chess move**: predicate MOVE, bindings (LOCATION) = the-game, (AGENT) = Fischer, (THEME) = king-pawn, (SOURCE) = e2, (GOAL) = e4. Location (which game), Agent (who moved), Theme (what piece), Source (from where), Goal (to where).  A single move is a single semantic assertion.

A **definition**: `GLOSS { (THEME) = the-sememe, (VALUE, ENGLISH) = "a domesticated canine" }` and separately `GLOSS { (THEME) = the-sememe, (VALUE, SPANISH) = "un canino domesticado" }`.  Same pattern as the title: each language's gloss is its own independently-authored frame.

An **authorship assertion**: predicate AUTHORED, bindings (THEME) = The Hobbit, (AGENT) = Tolkien.

These are all structurally identical: a predicate and role bindings.  The predicate determines what roles the frame expects; the roles determine what the values mean.

The predicate is worth pausing on, because it is easy to treat it as a distinct kind of thing. It is not. It is a sememe, a unit of meaning from the shared vocabulary, acting in a particular structural role. In that role, a sememe serves as a template or schema for the frame: it declares what bindings a frame of this kind is expected to carry and how those bindings relate. Calling a sememe a *predicate* names the role it plays, not a category it belongs to. The same vocabulary must supply everything. TOLKIEN, HOBBIT, AUTHORED, DOG, and TITLE are all meanings. Which of them naturally fits the predicate role is a matter of what each one denotes, not a structural constraint. Meanings that name relations or events (AUTHORED, TITLE, MOVE) naturally fit as predicates. Meanings that name kinds of things or instances thereof (HOBBIT, TOLKIEN, CHESS, DOG) naturally fit as binding values or as templates for items.

### Compound keys and indexing

The title and gloss examples above demonstrate compound keys in action: `(VALUE, ENGLISH)` and `(VALUE, RUSSIAN)` use a language qualifier to distinguish bindings that share the same base role.  The same mechanism extends to any domain: `(VIDEO, MKV, UHD)` and `(VIDEO, MKV, HD)` would distinguish video resolutions within the same container format; `(CONFIG, REPLICATION)` and `(CONFIG, RETENTION)` would distinguish policy types.  Every element of a compound key is a grounded sememe, not an arbitrary string.

Every such meaning is an opportunity for indexing.  A layer that indexes frames by the meanings in their binding keys gets multi-dimensional search as a structural consequence: "show me all videos" is a lookup on frames with VIDEO in their keys; "all UHD videos" narrows to frames with both VIDEO and UHD.  No separate tagging system, no search facets, no metadata catalog.  The key *is* the index.

### Queries are frames

The indexing infrastructure described above has a natural consequence: queries are not a separate mechanism.  A query is an incomplete frame, a frame with one or more bindings left unfilled, and the unfilled bindings indicate what you are asking for.

`AUTHORED { (AGENT) = Tolkien }` with no THEME binding asks "what did Tolkien author?"  `DEPICTS { (VALUE) = sunset }` asks "what depicts a sunset?"  `MOVE { (LOCATION) = the-game }` asks "what moves were made in this game?"  In each case, the system matches the filled bindings against the index and returns frames that complete the pattern.

Any binding in a query can hold an **expression**: a sub-frame that evaluates rather than matching literally.  `LISTING { (THEME) = book, (VALUE, PRICE, USD) = LESS_THAN { (VALUE) = 20 } }` uses a LESS_THAN sub-frame as a filter on the price binding.  The sub-frame is itself a frame with its own predicate and bindings, composed the same way assertions are composed.

Queries can also be **compound**: multiple incomplete frames joined by shared references.  "Find books authored by Tolkien that have a listing under $20 USD" requires two pattern frames, because the book and its listing are different items:

```
AUTHORED { (AGENT) = Tolkien, (THEME) = book }
LISTING  { (THEME) = book, (VALUE, PRICE, USD) = LESS_THAN { (VALUE) = 20 } }
```

The shared reference `book` joins the two patterns: the first finds books Tolkien authored, the second filters to those with a listing under $20.  The query machinery finds assignments to `book` that satisfy both patterns simultaneously.

A frame is an assertion when every binding is filled, and a query when at least one is left open.  There is no separate query language, no SQL, no SPARQL, no GraphQL.  The frame IS the query, the shared vocabulary IS the schema, and the compound-key index IS the query engine.  This is arguably the most powerful consequence of using a single semantic primitive for everything: the ability to ask questions about data is not a feature bolted on after the data model is defined, but a structural property of the data model itself.

### Everything is a role binding

There is no fundamental distinction between "the data" and "the metadata" of a frame. A title's text, a video's master file, a chess move's destination square, a document's author: each is a role binding. Provenance is a binding. Signatures are bindings. Timestamps are bindings. What we call "data" is a value filling a role. What we call "metadata" is also a value filling a role. The distinction is conventional, not structural.

### Beyond the natural-language inventory

The 22 thematic roles inherited from linguistics (drawn from VerbNet and ISO 24617-4) describe the participants in events: who did what to whom, where, when, how, why.  For a frame primitive that has to stand in for everything a data layer stores, they are necessary but not quite sufficient.  Three additional roles emerge as soon as the frame is asked to do work that natural language did not need to do.

The first gap appears as soon as a frame needs to carry raw content.  An IMAGE frame's JPEG bytes, a GLOSS frame's definition text, a MEASUREMENT frame's numeric result, a TITLE frame's designation: none of these are Agents, Themes, or Goals.  They are payloads, the content a predicate asserts rather than a participant in an event.  The standard role inventories have no general-purpose role for "this is the content itself."  **VALUE** generalizes VerbNet's narrow Value role (which covers only scalar endpoints like prices) into the role for whatever a predicate carries as its payload: a designation, a quantity, a measurement, a piece of text, a binary blob.

The second gap is operational.  How should an assertion be handled once made?  Replicated?  Encrypted?  Retained for how long?  **CONFIG** is the role for policy on the assertion itself: (CONFIG, REPLICATION), (CONFIG, PRESENTATION), (CONFIG, RETENTION), and so on.  Any frame can carry CONFIG bindings regardless of its predicate.

The third gap is causal ordering.  A chess move follows the previous move; a paragraph edit follows the edit before it; a reply follows the message it answers.  **FOLLOWS** is the role for causal or temporal predecessors, and like CONFIG it is cross-cutting: any frame can carry a FOLLOWS binding pointing at the content hash of an earlier frame.  The result is a hash-linked chain of signed assertions, the same data structure commonly called a "block chain" (in the narrow, structural sense of a hash chain, not in the cryptocurrency sense that has dominated popular usage of that term).  The difference is that these chains are local and contextual (this game, this conversation, this document) rather than global consensus mechanisms.

Three roles added to the linguistic inheritance: VALUE (generalized), CONFIG (new), FOLLOWS (new).  Each names a function the linguistic literature had no need to catalog.

### Frames as portable units

A frame carries within itself everything needed to interpret it *structurally*: its predicate names the kind of assertion, its binding keys name what each value means, and the values carry the content.  No external schema is required, no application-specific decoder ring, no lookup against a central authority.  Understanding what the predicate and roles *mean*, however, requires holding those sememes in your vocabulary.  If a frame arrives referencing a predicate or role you have not encountered before, you fetch it from a peer who has it, the same way you would fetch any other item.  An application can define new predicates for its own functionality, new item archetypes as targets for its frames, and even new roles if the standard inventory does not cover its needs, and those extensions propagate through the vocabulary as items like everything else.

A frame retrieved from any source is legible on arrival to any runtime that holds the relevant vocabulary.  That self-sufficiency is what makes the frame a unit of transit between peers as well as a unit of storage.  Signed, it is a verifiable assertion that can travel through any route and arrive intact.  Content-addressed by the hash of its predicate and bindings, it is identifiable as the same frame regardless of where it was retrieved from.  These properties are not an addition to the frame primitive; they are consequences of what the frame already is.

### Predicates carry behavior

So far we have treated a predicate as a structural template, declaring what bindings a frame expects. A predicate can do more. It can also declare how frames of its kind behave: how they might be expressed in text or other input, how they might be evaluated if they carry a computation. These declarations would be data on the predicate itself, not rules maintained by a separate parser or interpreter.

Consider the token `+`. In ordinary treatment, `+` is a symbol that a language's grammar rules know how to parse. In a frame-based layer, the picture is different. `+` is not a meaning. It is a *token*, a written symbol used in some notations. Other notations use different tokens for the same idea: "plus," "más," "加える." All of them point to the same underlying meaning, the sememe ADD. That sememe, in its role as a predicate, can declare the properties a parser would need to know about it: it is infix, it has a precedence, it associates left-to-right. These properties would not be grammar rules the parser has to be told in advance. They would be data the parser reads off the sememe once it resolves it from a token. No separate precedence table. No grammar.

This extends to structural symbols. Parentheses are tokens whose corresponding meanings declare "I open a group" and "I close a group." There is no reserved syntax. Everything (verbs, operators, functions, parentheses, commas) would resolve through the shared vocabulary. Syntax becomes vocabulary.

Any domain can bring its own notation.  Chess algebraic notation, regular expressions, mathematical symbols: each is a set of tokens whose corresponding predicates declare how they parse, resolved through the same mechanism as arithmetic operators or English prepositions.

The behavior a predicate declares is best understood as a *contract*, not a piece of code.  The contract lives with the predicate in the vocabulary; code that satisfies the contract (an actual parser, an actual evaluator) is something else entirely.  Where such code comes from and how it gets attached to a predicate is a question the primitive itself does not answer.  That question is taken up later.

---

## 6. What Frames Cohere Around

Frames are the primitive, yet a single frame is rarely the whole story.  A book is described by TITLE frames (in various languages), AUTHORED frames (possibly more than one author), TEXT frames (the chapters), COVER_ART frames (different editions have different covers), PUBLICATION frames (different editions, different publishers, different years), and more.  Each frame is a separate assertion with its own predicate and bindings, and they are all *about the same thing*.  They only make sense together.

If frames can be about the same thing, they need a shared anchor to point to.  That anchor, and the frames cohering around it, is what we will call an **item**.

An item is not a new primitive in the way a frame is.  It is what falls out when frames need to be *about* something: a stable anchor that frames can reference to indicate "I am about *this thing*."  The book is an item.  Tolkien is an item.  Chess as a concept is an item, and so is each individual chess game.  Each exists as an anchor around which frames cohere, building up a coherent, multi-faceted description.  Any binding on a frame can reference an item, and all such references are equal: THEME in an authorship assertion, LOCATION in a chess move, AGENT in a player registration, RECIPIENT in a recommendation.  The frame relates to each referenced item through whatever role the binding carries.

Section 4 required that data be named by what it is rather than where it lives, and items satisfy this requirement.  Each item has a stable, location-independent identifier, its **Item ID (IID)**, that functions the same way regardless of where the item is stored.  The IID is not assigned by a central registry; in most cases it is generated as a unique random value, though a small number of foundational vocabulary items use deterministic IIDs derived from canonical keys so they can be referenced in code.  In either case, the same IID is recognized on any device, by any peer, without coordination.  The same item can exist in many places and still be identifiable as the same item.

New frames bind to an item over time.  Some are assertions the item's maintainer chooses to endorse (a new chapter in a book, a new move in a chess game); others are assertions by third parties that bind to the item without being endorsed (a reaction, a review, a fact-check).  Both kinds reference the same IID; endorsement is a separate decision.

The set of frames an item endorses at any point in time is recorded in its **manifest**: a signed list of frame endorsements.  The manifest is itself content-addressed: its hash is the item's **version ID (VID)**.  When the maintainer endorses a new frame, a new manifest is produced, with a new VID, while the IID remains stable.  Version history is a directed graph of manifests, each pointing at one or more predecessors, structurally similar to Git's commit graph.  The similarity goes further: an item can be forked (a new maintainer starts endorsing a divergent set of frames under the same or a new IID) and merged (two divergent manifest chains are reconciled into one).  Peers holding the same item may hold different versions, and synchronization is a matter of exchanging the manifests and frames each is missing.  Conflict resolution when two peers have diverged is an area of active design, informed by CRDT research and by Git's own practical experience managing exactly this problem.

### The archetype

What makes a particular item the kind of thing it is?  A book is recognizably a book, not because some authority declares it so, but because the frames that describe it are the frames a book is *expected* to have.  There are TEXT frames for the chapters, there are AUTHORED frames, there are TITLE frames, there are PUBLICATION frames.  The collection of expected frames is what makes the item, in any meaningful sense, a book.

Where does that expectation live?  In the same place predicates live: the shared vocabulary.  BOOK is a sememe, just like AUTHORED is a sememe.  Where AUTHORED, in its role as a predicate, declares what bindings a frame of its kind expects, BOOK, in a different structural role, declares what frames an item of its kind is expected to endorse. Call a sememe playing this latter role an *archetype*.  BOOK, CHESS_GAME, PERSON, LANGUAGE, and DOG (which might expect NAME, BREED, and OWNER frames) are all archetypes: sememes in the shared vocabulary acting as templates for items rather than for frames.

Predicate and archetype are not categories of sememe. They are functional roles a sememe can play, parallel and complementary. A predicate is a sememe acting as a template for a frame. An archetype is a sememe acting as a template for an item. The same vocabulary supplies both. Which role a particular sememe naturally fits depends on what it denotes: meanings that name relations or events fit as predicates, meanings that name kinds of thing fit as archetypes. Nothing in the system would enforce the assignment, but in practice the division falls out cleanly from the meanings themselves.

There is one important asymmetry between archetypes and predicates, and it shapes how items grow. A predicate's declaration is mostly closed: a frame using a particular predicate carries the bindings the predicate calls for, though cross-cutting structural roles like CONFIG can appear on any frame regardless of predicate.  An archetype's declaration is *open*: it lists the frames an instance is expected to endorse, but anyone can assert additional frames that bind to the item without the archetype having defined them.  A book is expected to endorse TEXT, TITLE, and AUTHORED frames.  Nothing prevents someone else from signing a LIKE frame, a citation, a fact-check, a translation, or a review that binds to the same book.  The archetype defines what makes something a book. It does not gatekeep what others may say about it.

A chess game makes the pattern vivid.  The game is an item.  CHESS is its archetype, declaring the frames a chess game is expected to endorse: the players, the moves, the result.  The item itself, however, is not a monolithic structure.  It is an anchor around which signed frames cohere.

Players would register by signing their own PLAYER frames: `PLAYER { (AGENT) = Fischer, (VALUE) = WHITE }` signed by Fischer and `PLAYER { (AGENT) = Spassky, (VALUE) = BLACK }` signed by Spassky.  Each player attests their own participation; it is not assigned by a third party but declared by the participant and carries their signature.

Then moves: `MOVE { (LOCATION) = the-game, (AGENT) = Fischer, (THEME) = king-pawn, (SOURCE) = e2, (GOAL) = e4, (FOLLOWS) = previous-move }` signed by Fischer.  Each move is independently meaningful, independently signed, independently verifiable, and linked to its predecessor by FOLLOWS.  The game's move order is not maintained by a separate sequence number or a server's database; it falls out of the FOLLOWS chain, the same hash-linked structure described earlier.

Because each move is a self-contained signed frame, the game can be played peer-to-peer: each move travels directly from the player who made it to the other, with no referee server, no hosted backend, no central authority.  The game is recorded by being signed; it does not need any third party to become real.  And because each move is a frame, it is queryable: "all games where someone opened e4" is a lookup on MOVE frames with (GOAL) = e4 whose FOLLOWS binding points at the game's initial state (meaning it was the first move).

The pattern generalizes immediately.  A chat room would be an item with signed MEMBERSHIP and MESSAGE frames.  A key log would be KEY, REVOKE, and DELEGATE frames.  An auction would be signed BID frames.  Even real-time shared presence fits: a PRESENT frame asserts "I am in this space," an AVATAR_STATE frame with a LATEST retention policy carries position at 60Hz, and stream bindings carry video and audio.  Three temporal modes (durable, ephemeral, streaming), one frame model.  All the same pattern: an item exists, people make signed assertions about it, and those assertions collectively define what it is.

### The photograph, revisited

This paper opened with a photograph: a user saves it, and no layer of the stack knows what it is, who is in it, what occasion it documents, how it relates to other photographs or to the people depicted. In the picture being described here, a photograph is an item.  Its archetype is PHOTOGRAPH.  The information that today lives only in the user's head, or in a proprietary application's database, would be captured as frames at the moment of creation.

The photographer takes a picture of Alice and Bob at Alice's graduation. The item is created, and with it, the frames:

```
IMAGE    { (THEME) = the-photo, (VALUE, JPEG) = <image data>, (VALUE, JPEG, THUMBNAIL) = <thumbnail> }
DEPICTS  { (THEME) = the-photo, (AGENT) = Alice, (LOCATION) = [120px, 340px, 280px, 510px] }
DEPICTS  { (THEME) = the-photo, (AGENT) = Bob, (LOCATION) = [400px, 320px, 560px, 500px] }
OCCASION { (THEME) = the-photo, (VALUE) = graduation }
PLACE    { (THEME) = the-photo, (LOCATION) = [37.4275°N, 122.1697°W] }
CAPTURED { (THEME) = the-photo, (INSTRUMENT) = iPhone, (TIME) = 2024-06-15 }
```

Each frame is a separate assertion, signed by the photographer and indexed by the meanings in its binding keys.  The photograph does not merely *have* this information attached to it as metadata.  The frames ARE the photograph's semantic content, each independently signed and each independently verifiable.

The photograph lives on the photographer's device, managed by her **Librarian**: the local runtime that stores items, endorses frames, maintains relationships with other peers, and handles replication.  Every participant in the network runs a Librarian; it is the software that makes the substrate real on a given device.  Alice and Bob, who were depicted, have a relationship with the photographer; her Librarian replicates the photograph to theirs because she has chosen to include them.  Alice's mother, who follows Alice, sees the photograph arrive on her own device because Alice chose to include her.  No upload to a central server occurred.  No platform decided who should see it.  The photograph flowed along relationships, the same way the chess game's moves flowed between the two players, and it landed on each recipient's device as a local copy they hold and control.

Now the queries that no existing layer can answer become index lookups:

- "All photographs of Alice" finds DEPICTS frames where (AGENT) = Alice.
- "All graduation photos" finds OCCASION frames where (VALUE) = graduation.
- "Photos taken near this location" finds PLACE frames whose (LOCATION) coordinates fall within a radius.
- "Photos taken by this device in June 2024" filters CAPTURED frames by INSTRUMENT and TIME.

No crawling. No NLP. No reconstruction. The meaning was captured when the photograph was created, by the person who knew what the photograph was of.

The archetype PHOTOGRAPH declares that photographs are expected to carry DEPICTS, PLACE, and CAPTURED frames.  The accumulation surface is open regardless. Later, someone else adds:

```
LIKE      { (THEME) = the-photo, (AGENT) = Carol, (VALUE) = "Great shot!" }
FUNNY     { (THEME) = the-photo, (AGENT) = Jeff, (VALUE) = "😂" }
RECOMMEND { (THEME) = the-photo, (AGENT) = Dave, (RECIPIENT) = Eve }
DEPICTS   { (THEME) = the-photo, (VALUE) = sunset }
```

Each is a first-class assertion with its own predicate and roles, not a flat comment or tag.  Carol's LIKE travels back to the photographer along the same relationship the photograph traveled to Carol.  Dave's RECOMMEND reaches Eve directly, because Dave included Eve in his relationships; the photographer need not be involved.  The photograph accumulates reactions, recommendations, and additional descriptions the same way a chess game accumulates moves: signed assertions from identified parties, cohering around the same anchor, without a platform brokering any of it.  The PHOTOGRAPH archetype did not mention LIKE, FUNNY, RECOMMEND, or a third-party DEPICTS.  It did not need to.

A Spanish speaker sees the same photograph through Spanish lexemes.  The frames are the same; the words that surface them differ.  No translation has occurred.  The meanings were language-neutral from the start.

The architecture closes a circle: even sememes themselves would be items.  The sememe METER carries a GLOSS frame in English, a GLOSS in Spanish, a DIMENSION frame (LENGTH), CONVERSION frames to other units, a HYPERNYM frame (METER is-a LENGTH_UNIT), and a SYMBOL frame ("m").  The meaning is not a definition string but the structured totality of everything asserted about it.  A language itself (English, Spanish, Japanese) is an item whose frames include its entire lexicon.  The vocabulary lives *in* the graph, as items made of frames, using the same primitives as everything else.  Your local runtime carries the vocabulary you have encountered; when you meet a sememe you have not seen, you fetch it from peers who have it, the same way you fetch any other item.

This is where the analogy to files becomes concrete:

| Files | Items |
|---|---|
| Opaque bytes; no layer interprets content | Typed frames; the layer knows what everything means |
| Named by path in a hierarchy | Named by content-addressed IID; discoverable by meaning |
| No built-in authorship, versioning, or integrity | Every frame is signed; every version is a manifest hash |
| Metadata is a sidecar (EXIF, xattr, .DS_Store) | Metadata IS frames, first-class and queryable |
| Relatedness means same folder or a hyperlink | Relatedness is structural: frames bind to the same anchor |
| Application decides how to interpret it | Frames describe the item in the shared vocabulary; any application can read them |
| Search by filename or keyword | Query by meaning across the graph |
| Lives on a server or a single device | Lives on your device, replicated to chosen peers |
| Disappears when the service shuts down | Persists as long as any peer holds it |

The item is what would replace the file for the user.  Not at the POSIX level (bytes and streams are a fine substrate for low-level I/O) but for user-facing data: the things people create, name, share, organize, search for, and care about.  The item knows what it means, because it is described by frames, and frames are meaning.

---

## 7. The Open Commons

The vocabulary described in previous sections provides shared meanings for predicates, roles, and archetypes.  Two questions remain: how does the vocabulary handle specific entities rather than general concepts, and how does it grow?

### The entity problem

The AUTHORED example: predicate AUTHORED, (THEME) = The Hobbit, (AGENT) = Tolkien.  AUTHORED is a shared meaning.  PERSON is a shared meaning.  BOOK is a shared meaning.  What about Tolkien *himself*?

Today, Tolkien exists as a Wikipedia page, an Amazon author page, a Goodreads entry, a TMDB profile, a Library of Congress authority record, a Wikidata entry, and countless other disconnected representations. None is the canonical Tolkien that every system could use as the AGENT binding.

This is the hardest problem the shared meaning space must address.  WordNet provides the *concept* PERSON, but not a unique identifier for every specific person.  Every previous attempt to solve this has faced the same dilemma: centralized registries (Wikidata, Library of Congress) work but create gatekeepers, while fully decentralized naming avoids gatekeepers but introduces ambiguity.

The approach taken here is that entities are items: cryptographic anchors described by frames.  Tolkien, in this picture, is not a string or a URL or a row in a registry.  He is an item around which frames cohere: frames that assert his name, birth date, works by him, works about him, relationships.  These frames are signed by the people and institutions that assert them, and the item endorses whichever of those frames its maintainer accepts.

In practice, multiple items representing the same real-world entity will inevitably exist.  Alice creates a Tolkien item for her reading history (though she probably didn't actually have to).  The Library of Congress has one for its authority records.  A university library has another.  Each was created independently, each has a different IID, and each is described by frames from a different angle.

Convergence happens socially.  Alice encounters the Library of Congress's Tolkien item through a peer and recognizes it as the same person her item represents.  The natural resolution is merge: the frames from both items converge around one canonical IID, typically whichever is most widely referenced or most trusted.  Which IID wins is not decided by the substrate but by the community of peers who reference it.

Merge may not always happen, and that is acceptable.  When two communities genuinely disagree about whether two items represent the same entity, or when conflicting frames make a clean merge impossible, multiple items coexist.  A SAME_AS frame can assert that two items represent the same entity, but the assertion is a signed claim by whoever makes it, not a system-level fact.  Different users may accept or reject it based on their own judgment.  Honest disagreement, represented as coexisting items with overlapping but conflicting descriptions, is what decentralized identity looks like when no authority can declare a winner.

I will not pretend this is a solved problem.  It trades the problems of centralized identity (political control, single points of failure) for different problems (convergence latency, conflicting representations).  I believe the trade-off is correct, but the entity problem remains the area where the architecture is most genuinely unproven.

### How the vocabulary grows

The shared meaning space is not a closed vocabulary.  Domain-specific communities can extend it with their own concepts (medical terminology, legal concepts, engineering standards), connected to the base through the same hierarchical relationships.  New languages connect by linking their words to existing meanings.  The vocabulary grows from the edges, not from the center: extensible without fragmentation, because every extension is anchored in the shared backbone.  Extensions propagate the same way every other item does, through signed, content-addressed replication between trusting peers.  A medical community defines its vocabulary among its own peers without permission from a central authority; anyone outside who wants access fetches it through the same substrate.  The commons has no curator, only authors and trust.

---

## 8. The Trust Matrix

Every frame carries meaning in its predicate.  A FUNNY frame is not a generic "reaction"; it is a specific semantic assertion that its target is funny.  HILARIOUS and AMUSING are related but distinct assertions, and because they are all sememes in the same vocabulary hierarchy, they cluster naturally under a common ancestor (something like HUMOR).  SPAM, ASTROTURF, and JUNK cluster similarly.  INSIGHTFUL, CLARIFYING, and WELL_ARGUED cluster under a different branch.  These clusters are not engineered categories; they fall out of the vocabulary's existing structure.

An important structural point: frames can target other frames, not just items.  A SPAM frame's THEME binding can point at another frame rather than at an item, meaning "this specific assertion is spam."  A FUNNY frame can target another frame, meaning "this specific assertion is funny."  Assessments of assertions are themselves assertions, made with the same primitive, signed by identified parties, subject to the same trust evaluation as everything else.

Trust is what emerges from the accumulation of these assessments.  If Alice consistently reacts to Bob's content with FUNNY, INSIGHTFUL, or AGREE, her Librarian can compute a trust in Bob's judgment in those domains from the pattern of her reactions alone.  If Carol's Librarian has been reliably relaying Alice's messages and storing her data, Alice's Librarian can compute infrastructure trust from its own operational history.  The trust is the pattern; the pattern is the data.

Explicit trust declarations are also possible (a signed frame stating "I trust Bob for content moderation"), but the computed, emergent form is the common case.  Most trust accumulates naturally from the history of interactions the system already records, without anyone having to stop and declare it.

Trust is not a single number.  It is a **matrix**: multi-dimensional, with separate assessments for separate domains.  I might trust Alice's taste in music without trusting her political judgment.  I might trust Bob's Librarian for reliable message relay without trusting Bob's content assessments.  Even identity verification, how confident am I that this key represents the person I think it does, is one dimension among many, extending the web-of-trust concept that PGP introduced into a richer, domain-aware structure.

**Moderation** falls out of the trust matrix without requiring a separate system.  If I mark several of your assertions as SPAM, my trust in your content decreases.  Others who trust my judgment as a moderator will see my SPAM assessments and may lower their own trust in your content accordingly; others who do not trust my moderation will be unaffected.  No moderator was appointed.  No appeals board was convened.  Two different domains are at work simultaneously: your trustworthiness as a content creator, and my trustworthiness as a moderator.

This plays out in practice as a conversation in frames.  I mark your posts as SPAM.  James disagrees and marks my SPAM frames with DISAGREE (frames targeting frames, the same mechanism).  Perhaps a discussion ensues, with others chiming in.  Each participant's trust in each other, across the relevant domains, influences the net result: for some users, your posts survive because they trust James's judgment over mine; for others, your posts effectively disappear because they trust my moderation more.  No single outcome is imposed.  The trust matrix produces a different resolution for each user, from the same underlying data.

Crucially, the user always retains the ability to override computed trust values.  If the accumulated assessments produce a result that feels wrong, the user can adjust it directly.  The trust computation itself should be transparent and user-configurable; the algorithms that compute trust scores from accumulated frames are themselves implementations, items in the graph like any other, replaceable with alternatives that weight the inputs differently.  The substrate provides the data; how that data is aggregated into trust is a choice the user and their Librarian make together.

Trust is **transitive**, and the strongest form of transitive trust requires no explicit endorsement at all.  If Alice, Bob, and I independently react positively to the same restaurants, our Librarians can compute an overlap in taste from the convergence of our independent assertions about the same targets.  Nobody endorsed anyone.  Nobody clicked "helpful."  The trust emerged from the pattern of agreement across independently-authored frames.  This convergent-taste signal is more reliable than explicit endorsement, because it cannot be faked without faking the underlying reactions themselves.

Where reactions are visible, a secondary signal is available: if Alice is in my relationships and I can see her public reactions, my Librarian can observe her patterns and compute trust in her judgment from them directly.

When neither convergence nor visible reactions are available (perhaps Alice's reactions are private), an explicit trust query is possible: an incomplete frame like `TRUST { (THEME) = Bob, (ATTRIBUTE) = RESTAURANT }` sent to Alice's Librarian, which evaluates it against its computed trust matrix and returns the result as a response.  This follows the same pattern as any other query in the system: an incomplete frame whose unfilled bindings indicate what you are asking for, with the answer returned separately.  Nothing about trust is special-cased.

Each Librarian computes its own trust matrix **locally**.  There is no global trust score, no universal reputation number, no platform-computed ranking.  Two users looking at the same content may see different things because their trust matrices differ.  This is Szabo's (1997) vision of formalizing relationships on public networks: not a single view imposed by a platform, but overlapping views produced by overlapping trust relationships.

The trust model is still being refined.  The dimensions, the decay functions, the weighting of transitive paths, the interaction between implicit computation and explicit declaration: these are areas of active design.  The structural commitment is clear: trust is local, multi-dimensional, computed from the data the system already produces, and expressed through the same primitives as everything else.  The specific algorithms will evolve.

---

## 9. Computation as Frames

The claim that semantic frames constitute a genuine base layer (not merely a metadata system) requires demonstrating expressiveness in domains far removed from natural language. Mathematics is the strongest test case: the most formal, least ambiguous domain of structured knowledge. If thematic roles can describe mathematical operations, they are not linguistic conveniences. They are universal structuring principles.

The mapping turns out to be natural.

### Arithmetic

Take the expression: 3 + 5 = 8. The operation ADD is the predicate. The operands are not Agents (they don't initiate anything) or Patients (they don't change). One is the Theme (the entity being operated on) and the other is the Instrument (the means by which the operation is performed). Natural language reveals the asymmetry: we say "add 5 *to* 3," not "add 3 and 5 symmetrically."

```
ADD { (THEME) = 3, (INSTRUMENT) = 5 }
```

The frame is the input form: a predicate and its bindings, nothing more. Evaluating the frame produces a value, in this case 8. That value plays the role of Result in the cognitive structure (the thing that comes into existence through the operation), but it is not a binding on the input frame. It is what comes out the other end when the frame is run against an implementation of ADD's contract.

Subtraction confirms the asymmetry: `SUBTRACT { (THEME) = 10, (INSTRUMENT) = 3 }` evaluates to 7.  Theme ("the thing being acted on") and Instrument ("by what means") are exactly the semantic functions the input values serve.  The roles were defined for natural language, but they describe the same cognitive structure.

### Calculus

The definite integral of x² from 0 to 1:

```
INTEGRATE { (THEME) = x², (SOURCE) = 0, (GOAL) = 1, (INSTRUMENT) = dx }
```

Source and Goal for the bounds of integration. These roles were defined for physical motion ("move from the house to the store") but they map onto abstract endpoints with no strain, because the cognitive structure is the same: a starting point, an ending point, a traversal. Evaluating the frame produces 1/3, the Result.

Differentiation and limits follow the same pattern: `DIFFERENTIATE { (THEME) = x², (INSTRUMENT) = x }` evaluates to 2x; `LIMIT { (THEME) = 1/x, (GOAL) = ∞ }` evaluates to 0, the variable approaching the Goal the same way a physical object approaches a destination.

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

(Result here names the role for the *output* of evaluation, not an input binding on the frame. The other rows describe input bindings.)

### Why this matters

The mapping is significant not because it enables a math engine (though it does: `5 meters + 3 feet` is an ADD frame whose operands are quantities with unit sememes, resolvable because METER and FOOT are both LENGTH units with known conversion factors). It is significant because it demonstrates that thematic roles are cognitive structuring principles, not linguistic artifacts.

Mathematics and natural language both express: what is being operated on (Theme), by what means (Instrument), where we start (Source), where we end (Goal), by how much (Extent), and what results (Result). The roles are the same because the underlying cognitive operations are the same.

If 25 thematic roles can structure natural language, social interactions, and mathematical expressions, those roles are genuinely universal. A base layer built on them is as general as meaning itself.

### Mathematics as a language

As noted in section 5, predicates may carry parsing behavior: `+` is a token whose corresponding sememe ADD declares itself as infix with a precedence and associativity.  The consequence for mathematics specifically is that natural language, mathematical expressions, and domain-specific notations coexist within a single input stream.  "find books authored by Tolkien where price < 20 dollars" mixes English with a mathematical comparison, and both resolve through the same pipeline.  "Show games where Fischer opened 1. e4 e5 2. Nf3 and won in under 30 moves" mixes English with chess algebraic notation and a numeric filter.  "5 feet + 30cm in meters" mixes unit-bearing arithmetic across two measurement systems.  In each case, every token resolves to a sememe, every sememe declares its parsing behavior, and the language being spoken is inferred from the tokens rather than assumed.

Mathematical expressions are not bolted onto the side of a semantic layer.  They are frames.  A spreadsheet cell is a frame whose value is the result of an expression frame.  The boundary between "data" and "computation" dissolves the same way "data" and "metadata" does: both are role bindings on predicates.

### The contract and the code

A predicate carries a *contract*: it declares what values it expects, what result it produces, and how it might be parsed and evaluated.  The contract, however, is not the code that satisfies it.  ADD can declare that it takes two operands and produces a sum; it cannot, by itself, compute the sum.

The answer that fits the rest of the architecture is that code is published the same way every other thing is: as items.  An implementation of ADD would itself be an item, carrying the executable form (source code, compiled bytes, or a formal specification) alongside frames declaring which contract it satisfies, who signed it, and what runtime is needed.  The relationship between an implementation and the predicate it satisfies is itself a frame.

A predicate could have many implementations: different runtimes, different trade-offs, different authors, all coexisting.  A runtime evaluating an ADD frame would pick an implementation it can execute and whose author it trusts.  The contract is the meaning; the code is the machinery.  Nobody would own the contract.  ADD is a sememe in the shared vocabulary, no different from BOOK or AUTHORED.  Anyone could publish an implementation item, sign it, and let it propagate; whether a particular runtime uses it would be a matter of local trust, not central authorization.

Code distribution would become a special case of data distribution.  Today, software reaches users through centralized clearinghouses: app stores, package managers, vendor release servers.  In the picture being described, code would travel the same peer-to-peer mechanism as any other item: signed, content-addressed, replicated through relationships, versioned, forkable.  The package manager dissolves into the same medium that carries the rest of the data.

The choice raises serious security concerns.  Running code from arbitrary peers is a recipe for disaster unless the runtimes loading and executing it are properly sandboxed.  Sandboxing in this picture would not be a separate special system; it would be another kind of policy attached to frames, the same way replication or retention policy would be.  The problem is hard, but it is the same kind of hard as sandboxing untrusted JavaScript in a web browser, and the existing landscape of techniques (capability-based interfaces, isolated execution environments, formal verification of restricted languages) gives plenty to draw on.

An expected objection is that social trust is insufficient for authorizing code execution.  The objection deserves a precise answer: all code distribution already relies on social trust.  When you install software from the Google Play Store, you are trusting Google's review process.  When you install a package from apt, you are trusting the Debian maintainers.  When you download from the Apple App Store, you are trusting Apple.  All of this is still "social" in a very real, society-wide sense.  Even reproducible builds only verify that the build is reproducible, not that the code is safe; someone still has to audit it, and you still have to trust whoever did.  Supply-chain attacks like SolarWinds and the xz backdoor demonstrate that centralized trust intermediaries are not immune to compromise.  What this proposal changes is not whether code execution depends on trust, but who is being trusted: a personally-chosen network of peers whose reputations are visible and whose endorsements are signed, rather than an opaque corporate process whose incentives may not align with your safety.  Nothing prevents Google or Apple from publishing their own code items in the graph, or their own signed reviews and recommendations of other applications.  Users who trust their judgment can continue to rely on it.  The difference is that Google stands alongside every other reviewer rather than occupying a privileged gatekeeping position that no one else can fill.

Because code is an item and data is an item, one of the main structural rationales for SaaS centralization dissolves.  Most software became a service in part because the operator held both the code and the data, and running the code required their infrastructure.  Here, both travel through the same peer substrate, and execution happens wherever the user has a runtime, most naturally on their own device.  The server farm, the subscription, the operator as gatekeeper: these arrangements lose the structural basis that made them seem like the only option.

The deeper point is structural. Computation would not need a separate apparatus alongside the data. The contract for a piece of computation is a meaning in the vocabulary, played as a predicate. The code that satisfies the contract is an item in the graph. The link between them is a frame. Everything that has to exist for computation to happen is the same kind of object that has to exist for everything else.

---

## 10. What Changes

The architecture described in the preceding sections has consequences that extend beyond the technical.  Several are worth naming because they are not obvious from the primitives alone.

The most visible change is the **subsumption of platforms**.  A product listing, a community forum, a social feed, a review, a citation graph: each of these is currently a proprietary database on a proprietary platform, and each would be expressible as frames in the shared vocabulary.  The platform-specific data model that locks users in dissolves, because the data model is shared.  Applications become interchangeable runtimes over the same data: an email client, a document editor, a social feed, a project tracker would all be applications over frames.  The user picks the client; the data does not belong to it.  Competing clients would read the same data, and no user would be locked in to an interface because their data is readable only by its author's binaries.

**The economics of the internet do not disappear.**  Businesses still want customers.  Individuals still want to find information and buy services.  Hosting remains relevant, especially for commercial interests that want an always-available presence.  What disappears is the ability to monetize user entrapment: the platform business model built on lock-in.  The hosting and service business model, where providers compete on quality, reliability, and price rather than on the structural impossibility of leaving, remains entirely viable.

Two properties of the locality pillar deserve explicit mention because they are structural consequences rather than features to be engineered.  **Offline capability** is trivial when the runtime and the data are both local; when a network returns, changes propagate to peers.  **Resilience to vendor disappearance** is equally structural: items live on users' devices and on the peers who have replicated them, so a company that shut down or was acquired would lose the ability to ship updates, but the data, the tools, and the peer network would remain.  Kleppmann et al. call this the "long now" property of local-first software; here it is a consequence of the substrate, not a feature built on top of it.

---

## 11. Authorship, Not Ownership

A popular slogan in the decentralization and crypto communities is "own your data."  The phrase evokes the right sentiment.  The lopsided relationship between users and platforms is unjust.  Something must change.  Users deserve agency over their own contributions.  The word "ownership" applied to data, however, promises more than any technical system can deliver, and it is worth stating plainly what this proposal does and does not accomplish.

Ownership, in every meaningful sense, implies control.  A thing I own is a thing I can exclude others from, a thing I can hold or withhold, a thing that is mine and not yours.  These properties hold for physical objects because matter occupies space and cannot be in two places at once.  They hold for artificially scarce digital assets like cryptocurrency tokens because the network's rules enforce the scarcity.  They do not hold for ordinary data, and they cannot.  Once I have given you a copy, I have no technical means of revoking it.  You can keep it, forward it, publish it, transform it, or forget it.  My wishes have no technical weight.  This is not a failure of the substrate proposed here, or of any honest substrate; it is a property of copyable information, and any system that preserves human agency must accept it.

Before going further, a distinction worth naming.  What the substrate technically delivers is *attribution*: the provable linkage between a signed assertion and the key that signed it.  *Authorship* is the broader human concept, the social claim that a particular person created something.  Under the usual assumption that the signer is the creator, attribution stands in for authorship closely enough that I will use the words almost interchangeably below, reaching for "authorship" when the human framing matters and "attribution" when the technical mechanism does.  The two diverge in edge cases (ghostwriting, delegated signatures, AI-generated content), but those edges do not change the core claim: what the substrate provides is a reliable, verifiable linkage from assertions to keys.  Whether the signer is the author is a social-layer question no substrate can answer.

What this proposal can accomplish is closer to the *spirit* of what people mean when they say "own your data," without claiming the part that cannot be delivered.  You hold your own keys, and nobody can sign as you.  You author your own assertions, and nobody can forge them.  You keep local custody of your data, and no vendor can revoke your access to work you already have.  You choose which peers you share with going forward, and you do so through deliberate trust relationships rather than by default exposure to whoever hosts your platform.  These are real, technically enforceable properties.  They are not ownership of data.  They are ownership of your *participation* in a network: your keys, your authorship, your custody, your consent to new copies.

What the proposal cannot do, nor should any honest proposal claim to do, is compel other parties to honor your wishes about copies they already hold.  If you share risqué photographs with a trusted partner and the relationship goes wrong, no substrate can un-share what is already shared.  The partial solution is social, not technical.  A trust paradigm is what you have when you can *choose* whom to share with, and when the act of sharing is deliberate rather than default.  If someone violates that trust, you can stop trusting them, and the trust graph as a whole responds: others who trust you see your revised posture and may update their own relationships with the offender.  This is how human communities have always handled the problem.  The substrate does not replace that social mechanism.  It returns computing to a state where social mechanisms can actually function, because sharing stops being a default and becomes a choice.

Legal frameworks, contracts, and commercial licenses compose naturally with all of this, and the primitives CG provides (signed assertions, key-based identity, content-addressed verification) make them easier to encode when they apply.  Nothing here displaces them.

The word "ownership" invites confusion because it borrows from property law what the medium cannot enforce.  A more honest vocabulary for this project is authorship, custody, and consent.  What you can have, in this proposal, is exactly those three: provable authorship of what you assert, local custody of what you hold, and a meaningful say in the propagation of new copies you make.  That is substantially more than platforms currently permit, and substantially less than the "ownership" rhetoric suggests.  The difference between those two is where honest design lives.

---

## 12. Honest Reckoning

I am not the first to propose an ambitious rethinking of how computing handles information. The history of such proposals is largely a history of instructive failures, and I would be foolish to ignore it.

**Xanadu** (Nelson, 1974) envisioned a global, versioned, bidirectional-linking document system.  It got content addressing, versioning, and bidirectional links right (concepts that took decades to resurface in Git and IPFS).  It failed because it demanded solving everything simultaneously before shipping anything.  Lesson: ship incremental function, not a complete vision.

**CYC** (Lenat, 1995) set out to encode all common-sense knowledge as logical assertions.  It got the diagnosis right: computers need world knowledge, not just data.  It stalled because hand-authoring millions of axioms does not scale.  Lesson: anchor in existing resources and let meaning emerge from use.

**Croquet** (Smith, Kay, Raab, & Reed, 2003), Alan Kay's vision of shared, replicated 3D environments, got replicated state and seamless collaboration right.  It faded because it required a complete runtime (Squeak Smalltalk) and could not interoperate with existing software.  Lesson: platforms that cannot meet users where they already are face adoption cliffs no technical elegance can overcome.

**Plan 9** (Pike et al., 1995) pushed Unix's "everything is a file" to its logical conclusion.  Technically superior to Unix in almost every way, it failed to displace it because it required abandoning the entire Unix ecosystem.  Lesson: even a cleaner design loses to an entrenched ecosystem unless it provides a bridge.

**The Semantic Web** (Berners-Lee et al., 2001) got the diagnosis exactly right: the web needs machine-readable semantics. It built a rigorous stack that works in specialized domains. It did not become general-purpose because it was layered *on top of* the web rather than built into it. Lesson: a semantic layer that is optional will remain marginal.

**Local-first software** (Kleppmann et al., 2019) is the tradition directly upstream of this paper's second pillar.  Its seven ideal properties describe applications that live on the user's device, work offline, sync peer-to-peer, and retain user ownership and control.  The tradition is healthy in research and in niche commercial software but has not displaced the SaaS default, because solving sync and interoperability without a central coordinator, while preserving user experience, has been hard enough that most teams took the easier centralized path.  Lesson: the technical problems are now serviceable with current tools; what is missing is a substrate that makes local-first the easier path, not just a more virtuous one.

What do these teach?

**Incremental delivery is non-negotiable.** A system that requires completeness before it provides value will never reach completeness. Each increment must be useful on its own.

**Build on existing resources.** CYC tried to encode all knowledge manually. The Semantic Web required ontology engineering for every domain. I would rather anchor in WordNet, CILI, VerbNet, and ISO 24617-4: resources built and validated over decades by the computational linguistics community. I am not inventing a vocabulary; I am giving an existing, empirically validated vocabulary a new job.

**Provide a bridge.**  Plan 9 and Croquet demanded that users abandon their ecosystems.  A semantic base layer must coexist with files, filesystems, and the web.  POSIX is a reasonable base layer for byte handling; it was never intended to be a base layer for meaning.  On the web side, this means a gateway: a Librarian that accepts remote clients and renders items as web pages, so that people can discover the system's content through a browser before they install a Librarian of their own.  The bridge is how users arrive; the local runtime is where they stay.

**Neither pillar can be optional.** This is the deepest lesson from the Semantic Web on one side and from local-first retrofits on the other.  If creating semantic structure is a separate step from creating data, most people skip it.  If local control is a separate feature to opt into, most people stay with the hosted default.  The design must make creating data *be* creating semantic, user-held structure, the way writing a sentence *is* expressing meaning, not writing sounds and then separately annotating what they mean.

Can this proposal avoid the fates of its predecessors?  Honestly: I do not know.  The ambition is large, the history is cautionary, and the engineering challenges are real.  The linguistic resources now exist, however.  WordNet has 120,000 synsets.  CILI links them across languages.  VerbNet classifies 300 verb classes with role declarations.  ISO 24617-4 standardizes the role inventory.  UniMorph provides morphological data for 100+ languages.  These resources represent decades of cumulative scholarly work.  They did not exist when CYC began, when the Semantic Web was proposed, or when Croquet was built.

Neither did the technical infrastructure for the locality pillar.  Modern signing cryptography (ed25519 and related primitives) is cheap enough to apply at the per-message level.  Content-addressing is ubiquitous (every Git commit is a use of it).  CRDTs have moved from research to shipping practice.  Open-source P2P transport stacks (libp2p, modern QUIC implementations) are mature.  A local-first substrate in 2026 is not conjuring machinery from nothing; it is composing pieces that have all shipped, some many times over.

And, worth stating plainly: AI assistance has compressed what was previously decades of solo implementation work into feasible timescales, and sustained dialogue with it has contributed to the clarity of the model as a whole.  The bottleneck for ambitious software projects has always been the sheer volume of code required.  That bottleneck has narrowed dramatically.  This does not guarantee success, but it changes the economics of ambition.

The path forward is incremental: frames as a local data format; a shared vocabulary seeded from WordNet and CILI; a local runtime that stores, queries, and resolves data by meaning; and a peer-to-peer network where that data is exchanged between nodes connected by trust.  Each step independently useful.  Together, the semantic and local-first base layer that computing has been missing since the networked era began.

Whether it works is an empirical question. I offer it not as a certainty but as a proposal, grounded in established theory and validated resources, that the time is right to try.

---

## References

Baker, C. F., Fillmore, C. J., & Lowe, J. B. (1998). The Berkeley FrameNet Project. In *Proceedings of ACL/COLING*, 86-90.

Barnes, J. A. (1954). Class and Committees in a Norwegian Island Parish. *Human Relations*, 7(1), 39-58.

Benet, J. (2014). IPFS — Content Addressed, Versioned, P2P File System. arXiv:1407.3561.

Berners-Lee, T., Hendler, J., & Lassila, O. (2001). The Semantic Web. *Scientific American*, May 2001.

Bizer, C., Heath, T., & Berners-Lee, T. (2009). Linked Data — The Story So Far. *International Journal on Semantic Web and Information Systems*, 5(3).

Bond, F., Vossen, P., McCrae, J., & Fellbaum, C. (2016). CILI: the Collaborative Interlingual Index. In *Proceedings of the 8th Global WordNet Conference*, 50-57.

Bond, F. & Foster, R. (2013). Linking and Extending an Open Multilingual Wordnet. In *Proceedings of ACL*, 1352-1362.

Bonial, C., Stowe, K., & Palmer, M. (2011). Renewing and Revising SemLink. In *Proceedings of the 2nd Workshop on Linked Data in Linguistics*.

Bush, V. (1945). As We May Think. *The Atlantic Monthly*, July 1945.

Clarke, I., Sandberg, O., Wiley, B., & Hong, T. W. (2001). Freenet: A Distributed Anonymous Information Storage and Retrieval System. In *Designing Privacy Enhancing Technologies*, Springer, 46-66.

Cohen, B. (2003). Incentives Build Robustness in BitTorrent. In *Workshop on Economics of Peer-to-Peer Systems*.

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

Milgram, S. (1967). The Small-World Problem. *Psychology Today*, 1(1), 61-67.

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

Watts, D. J., & Strogatz, S. H. (1998). Collective Dynamics of 'Small-World' Networks. *Nature*, 393(6684), 440-442.

Zimmermann, P. R. (1995). *The Official PGP User's Guide*. MIT Press.

Youn, H. et al. (2016). On the Universal Structure of Human Lexical Semantics. *PNAS*, 113(7), 1766-1771.
