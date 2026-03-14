# References

Academic papers and foundational works relevant to Common Graph's design, organized by topic.

---

## Visionary Computing and Augmented Intellect

The philosophical ancestors of Common Graph — systems that reimagine how humans interact with information.

- **Bush 1945 - As We May Think.pdf** — Bush, V. (1945). "As We May Think." *The Atlantic Monthly*, July 1945. *The memex — a device for storing, linking, and retrieving all of a person's knowledge. The ur-text of hypertext and associative information systems.*

- **Engelbart 1962 - Augmenting Human Intellect.pdf** — Engelbart, D. C. (1962). "Augmenting Human Intellect: A Conceptual Framework." SRI Summary Report AFOSR-3223. *The H-LAM/T framework — artifacts, language, methodology, and training as an integrated system for amplifying human capability. Common Graph's "everything is an Item" is a descendant of this vision.*

---

## Visionary Projects and Cautionary Tales

Ambitious systems that attempted what Common Graph attempts — and the lessons from their struggles. Each got something profoundly right, and each failed (or is still failing) for instructive reasons.

- **Nelson 1974 - Computer Lib Dream Machines (excerpt).pdf** — Nelson, T. (1974). *Computer Lib / Dream Machines*. Self-published. *Ted Nelson's manifesto introduced "hypertext" and the vision of Xanadu — a global, versioned, bidirectional-linking document system with micropayments and transclusion. **What it got right**: content addressing, versioning, bidirectional links, attribution. **Why it stalled**: Xanadu demanded solving everything simultaneously (storage, networking, payment, UI, versioning) before shipping anything. After 60+ years, it remains unfinished. The lesson: scope ambition ruthlessly. Ship incremental function, not a complete vision.*

- **Lenat 1995 - CYC A Large-Scale Investment in Knowledge Infrastructure.pdf** — Lenat, D. B. (1995). "CYC: A Large-Scale Investment in Knowledge Infrastructure." *Communications of the ACM*, 38(11), 33-38. (Also published in *Journal of Artificial Intelligence Research*, 1995.) *Douglas Lenat's 40-year project to encode all common-sense knowledge as logical assertions. **What it got right**: the insight that computers need world knowledge, not just data. **Why it stalled**: hand-authoring millions of axioms doesn't scale — the knowledge acquisition bottleneck never broke. CG sidesteps this by seeding from existing lexical resources (WordNet/CILI) and letting meaning emerge from use, not from manual encoding.*

- **Smith, Kay, Raab, Reed 2003 - Croquet A Collaboration System Architecture.pdf** — Smith, D. A., Kay, A., Raab, A., & Reed, D. P. (2003). "Croquet — A Collaboration System Architecture." In *Proceedings of the First Conference on Creating, Connecting and Collaborating through Computing (C5)*, IEEE. *Alan Kay's vision of a shared, replicated 3D environment where all computation is transparent and collaborative. **What it got right**: replicated state, late-binding, everything-is-an-object, seamless collaboration. **Why it faded**: required a complete runtime (Squeak Smalltalk), couldn't interoperate with existing software ecosystems, and the 3D interface was ahead of its time. Evolved into OpenCobalt, then Open Simulator, each losing coherence. The lesson: platforms that can't meet users where they already are face adoption cliffs.*

- **Pike et al 1995 - Plan 9 from Bell Labs.pdf** — Pike, R., Presotto, D., Dorward, S., Flandrena, B., Thompson, K., Trickey, H., & Winterbottom, P. (1995). "Plan 9 from Bell Labs." *Computing Systems*, 8(3), 221-254. *The Bell Labs operating system that pushed Unix's "everything is a file" to its logical conclusion: all resources (network, display, processes) accessible as file trees via 9P protocol. **What it got right**: uniform resource access through a single namespace, per-process namespace composition, network transparency. **Why it failed to displace Unix**: it was technically superior but required abandoning the entire Unix software ecosystem. No migration path, no backwards compatibility, no critical mass. The lesson: even a cleaner design loses to an entrenched ecosystem unless it provides a bridge.*

- **OpenDoc** (Apple, IBM, et al., 1992-1997) — *A compound document architecture where documents were composed of parts from different applications, each rendered in-place by its handler component. **What it got right**: the fundamental insight that documents should be composed of semantic parts, not monolithic application files. **Why it died**: killed by Apple's internal politics (Steve Jobs returned and cancelled it to focus on NeXT integration), hostile competition from Microsoft OLE/COM, and the complexity of cross-vendor component interop. The lesson: technical merit means nothing if organizational politics and market dynamics work against you. Also: component interop is genuinely hard.*

These projects collectively demonstrate that the ideas behind Common Graph are not new — they've been attempted repeatedly for decades. What's new is the convergence of enabling technologies (content addressing, CRDTs, modern cryptography, commodity hardware) and, critically, the existence of AI assistance that can compress what was previously decades of solo implementation work into feasible timescales.

---

## Frame Semantics and FrameNet

The vocabulary system's `SemanticFrame` + `ThematicRole` bindings descend directly from Fillmore's frame semantics. Common Graph uses frames for *dispatch*, not just annotation.

- **Fillmore 1982 - Frame Semantics.pdf** — Fillmore, C. J. (1982). "Frame Semantics." In *Linguistics in the Morning Calm*, pp. 111-137. Seoul: Hanshin. *The foundational paper on frame semantics.*

- **Baker, Fillmore, Lowe 1998 - The Berkeley FrameNet Project.pdf** — Baker, C. F., Fillmore, C. J., & Lowe, J. B. (1998). "The Berkeley FrameNet Project." In *Proceedings of ACL/COLING*, pp. 86-90. *Introduces FrameNet as a computational resource for frame semantics.*

- **Ruppenhofer et al 2006 - FrameNet II Extended Theory and Practice.pdf** — Ruppenhofer, J., Ellsworth, M., Petruck, M. R. L., Johnson, C. R., & Scheffczyk, J. (2006). *FrameNet II: Extended Theory and Practice*. ICSI Berkeley. *Comprehensive manual for FrameNet annotation theory and practice.*

- **Gildea, Jurafsky 2002 - Automatic Labeling of Semantic Roles.pdf** — Gildea, D. & Jurafsky, D. (2002). "Automatic Labeling of Semantic Roles." *Computational Linguistics*, 28(3), 245-288. *Automatic semantic role labeling using FrameNet data — the computational bridge between frame theory and NLP.*

---

## WordNet, CILI, and Multilingual Lexical Resources

Common Graph seeds its sememes from WordNet via CILI identifiers, enabling language-neutral concept anchoring.

- **Miller et al 1993 - Introduction to WordNet.pdf** — Miller, G. A. et al. (1993). *Introduction to WordNet: An On-line Lexical Database*. Princeton University. *The original five WordNet papers describing nouns, verbs, adjectives, and the database design.*

- **Fellbaum - WordNet Word Relations Senses and Disambiguation.pdf** — Fellbaum, C. "WordNet: Word Relations, Senses, and Disambiguation." *Overview of WordNet's lexical organization and word sense disambiguation.*

- **Bond, Vossen et al 2016 - CILI the Collaborative Interlingual Index.pdf** — Bond, F., Vossen, P., McCrae, J., & Fellbaum, C. (2016). "CILI: the Collaborative Interlingual Index." In *Proceedings of the 8th Global WordNet Conference*, pp. 50-57. *Defines the language-neutral concept identifiers that Common Graph uses as canonical keys for seed sememes.*

- **Bond, Foster 2013 - Linking and Extending an Open Multilingual Wordnet.pdf** — Bond, F. & Foster, R. (2013). "Linking and Extending an Open Multilingual Wordnet." In *Proceedings of ACL*, pp. 1352-1362. *Open multilingual wordnet with 26+ languages — the model for Common Graph's language import pipeline.*

- **Vossen 1998 - EuroWordNet Multilingual Lexical Semantic Networks.pdf** — Vossen, P. (1998). "EuroWordNet: A Multilingual Database with Lexical Semantic Networks." *Computational Linguistics*, 25(4). *The inter-lingual index (ILI) design that preceded CILI — same core idea of linking autonomous language-specific wordnets.*

- **Navigli, Ponzetto 2010 - BabelNet.pdf** — Navigli, R. & Ponzetto, S. P. (2010). "BabelNet: Building a Very Large Multilingual Semantic Network." In *Proceedings of ACL*, pp. 216-225. *Automatic construction of a multilingual semantic network integrating WordNet and Wikipedia.*

- **Youn et al 2016 - Universal Structure of Human Lexical Semantics.pdf** — Youn, H. et al. (2016). "On the Universal Structure of Human Lexical Semantics." *PNAS*. *Cross-linguistic evidence that semantic networks share universal structure — empirical support for Common Graph's language-neutral sememe layer.*

- **Snow, Jurafsky, Ng 2006 - Semantic Taxonomy Induction.pdf** — Snow, R., Jurafsky, D., & Ng, A. Y. (2006). "Semantic Taxonomy Induction from Heterogeneous Evidence." In *Proceedings of ACL*. *Automatic taxonomy construction from multiple knowledge sources.*

- **Sarkar, Law - Document Classification using WordNet.pdf** — Sarkar, S. & Law, S. "Document Classification using WordNet." *WordNet-based document classification techniques.*

---

## Semantic Parsing and Executable Semantics

Common Graph's token resolution + frame assembly + verb dispatch mirrors semantic parsing: language in, execution out.

- **Zettlemoyer, Collins 2005 - Learning to Map Sentences to Logical Form.pdf** — Zettlemoyer, L. S. & Collins, M. (2005). "Learning to Map Sentences to Logical Form: Structured Classification with Probabilistic Categorial Grammars." In *Proceedings of UAI*. *Maps natural language to lambda calculus via CCG — the statistical counterpart to Common Graph's deterministic vocabulary resolution.*

- **Berant et al 2013 - Semantic Parsing on Freebase.pdf** — Berant, J., Chou, A., Frostig, R., & Liang, P. (2013). "Semantic Parsing on Freebase from Question-Answer Pairs." In *Proceedings of EMNLP*, pp. 1533-1544. *Maps natural language questions to knowledge graph queries — analogous to Common Graph's token -> item resolution -> dispatch pipeline.*

- **Liang 2016 - Learning Executable Semantic Parsers.pdf** — Liang, P. (2016). "Learning Executable Semantic Parsers for Natural Language Understanding." *Communications of the ACM*, 59(9), 68-76. *Survey of executable semantic parsing — the field closest to what Common Graph does with vocabulary-driven dispatch.*

---

## Combinatory Categorial Grammar (CCG)

CCG's compositional semantics — where surface syntax maps directly to meaning — influences Common Graph's approach to expression parsing.

- **Steedman 1996 - A Very Short Introduction to CCG.pdf** — Steedman, M. (1996). "A Very Short Introduction to CCG." University of Edinburgh. *Concise tutorial on CCG's type-driven composition.*

- **Steedman 2022 - Combinatory Categorial Grammar.pdf** — Steedman, M. (2022). "Combinatory Categorial Grammar." University of Edinburgh. *Comprehensive overview of CCG theory with current developments.*

- **Steedman 2017 - CCG for NLP.pdf** — Steedman, M. (2017). "Combinatory Categorial Grammar for NLP." University of Edinburgh. *CCG applied to natural language processing — the computational angle.*

- **Baldridge 2003 - Multi-Modal CCG.pdf** — Baldridge, J. (2003). "Multi-Modal Combinatory Categorial Grammar." In *Proceedings of EACL*. *Extension of CCG with modalities — relevant to Common Graph's multi-modal input handling.*

---

## Montague Semantics

Montague's principle — "no important theoretical difference between natural languages and formal languages" — is the philosophical ancestor of Common Graph's design.

- **Montague 1973 - The Proper Treatment of Quantification.pdf** — Montague, R. (1973). "The Proper Treatment of Quantification in Ordinary English." In *Approaches to Natural Language*, pp. 221-242. Reidel. *The landmark paper showing natural language can be given a formal semantics.*

- **Partee - Montague Grammar.pdf** — Partee, B. H. "Montague Grammar." Elsevier Encyclopedia entry. *Accessible overview of Montague's framework and its linguistic impact.*

---

## Speech Act Theory

Common Graph's verb dispatch model — every utterance is an *action* on an item — mirrors speech act theory directly.

- **Austin 1962 - How to Do Things with Words.pdf** — Austin, J. L. (1962). *How to Do Things with Words*. Oxford: Clarendon Press. *The foundational work on performative utterances — saying IS doing.*

- **Searle 1969 - Speech Acts.pdf** — Searle, J. R. (1969). *Speech Acts: An Essay in the Philosophy of Language*. Cambridge University Press. *Formalizes speech act theory with illocutionary force, constitutive rules, and felicity conditions.*

---

## Construction Grammar

The idea that grammatical constructions carry meaning independently of words maps to Common Graph's VocabularyContribution model.

- **Chaves 2019 - Construction Grammar.pdf** — Chaves, R. P. (2019). "Construction Grammar." In *Current Approaches to Syntax*. De Gruyter. *Modern overview of construction grammar including Goldberg's contributions.*

---

## Lexical Semantics and Generative Lexicon

The treatment of word meaning as compositional, with qualia structure and type coercion, connects to how Common Graph represents component capabilities.

- **Pustejovsky 1991 - The Generative Lexicon.pdf** — Pustejovsky, J. (1991). "The Generative Lexicon." *Computational Linguistics*, 17(4). *Theory of how words generate meaning compositionally — the problem of polysemy.*

- **Pustejovsky - Introduction to Generative Lexicon.pdf** — Pustejovsky, J. "Introduction to Generative Lexicon." Brandeis University. *Concise introduction to GL theory.*

---

## Lexical-Functional Grammar (LFG)

LFG's separation of c-structure (form) from f-structure (function) parallels Common Graph's separation of surface tokens from semantic resolution.

- **Kaplan, Bresnan 1982 - Lexical-Functional Grammar.pdf** — Kaplan, R. M. & Bresnan, J. (1982). "Lexical-Functional Grammar: A Formal System for Grammatical Representation." In *The Mental Representation of Grammatical Relations*, pp. 173-281. MIT Press.

---

## Ontologies and Knowledge Graphs

Common Graph's type system, relations, and sememe hierarchy are ontological structures. These papers define the field.

- **Gruber 1993 - Toward Principles for the Design of Ontologies.pdf** — Gruber, T. R. (1993). "Toward Principles for the Design of Ontologies Used for Knowledge Sharing." Technical Report KSL 93-04, Stanford University. *The foundational paper on ontology engineering — defines ontology as "an explicit specification of a conceptualization."*

- **Hogan et al 2021 - Knowledge Graphs.pdf** — Hogan, A. et al. (2021). "Knowledge Graphs." *ACM Computing Surveys*, 54(4). *Comprehensive survey covering graph data models, query languages, schema, knowledge extraction, and applications — the modern reference for everything Common Graph's relation layer does.*

---

## Semantic Web and Linked Data

Common Graph's relations (filled semantic frames with predicate + role bindings) and content addressing share DNA with the Semantic Web vision, though CG replaces RDF triples with Fillmore-inspired frame semantics and URLs with content-addressed IIDs.

- **Berners-Lee, Hendler, Lassila 2001 - The Semantic Web.pdf** — Berners-Lee, T., Hendler, J., & Lassila, O. (2001). "The Semantic Web." *Scientific American*, May 2001. *The original vision paper for machine-readable meaning on the web.*

- **Bizer, Heath, Berners-Lee 2009 - Linked Data The Story So Far.pdf** — Bizer, C., Heath, T., & Berners-Lee, T. (2009). "Linked Data — The Story So Far." *International Journal on Semantic Web and Information Systems*, 5(3). *Survey of linked data principles and practice — the four rules for publishing structured data on the web.*

---

## Interlingua and Machine Translation

Common Graph's sememe layer IS an interlingua — a language-neutral meaning representation mediating between surface forms.

- **Cardenosa et al 2005 - Universal Networking Language.pdf** — Cardenosa, J. et al. (2005). *Universal Networking Language: Advances in Theory and Applications*. *The UNL interlingua system developed by the United Nations University.*

---

## Dialog Systems and Conversational AI

Modern task-oriented dialog systems decompose input into intents + entities, which maps to Common Graph's verb + thematic role bindings.

- **Bocklisch et al 2017 - Rasa Open Source Language Understanding.pdf** — Bocklisch, T. et al. (2017). "Rasa: Open Source Language Understanding and Dialogue Management." arXiv:1712.05181. *Open-source NLU + dialog management — the closest industrial system to Common Graph's dispatch model.*

---

## Message-Passing, Actor Model, and Object Systems

Common Graph's "every item has a prompt" and inner-to-outer dispatch is closer to Smalltalk's message-passing and the Actor model than to traditional NLP.

- **Kay 1993 - The Early History of Smalltalk.pdf** — Kay, A. C. (1993). "The Early History of Smalltalk." In *HOPL-II: History of Programming Languages*. ACM. *"The big idea is messaging" — objects communicating through messages, not method calls.*

- **Hewitt, Bishop, Steiger 1973 - A Universal Modular ACTOR Formalism.pdf** — Hewitt, C., Bishop, P., & Steiger, R. (1973). "A Universal Modular ACTOR Formalism for Artificial Intelligence." In *IJCAI'73*, pp. 235-245. *The actor model — independent computational entities communicating by sending messages. Direct ancestor of Common Graph's item-as-agent dispatch.*

---

## Content Addressing and Cryptographic Data Structures

Common Graph identifies all content by cryptographic hash (multihash). These papers define the foundations.

- **Merkle 1979 - Secrecy Authentication and Public Key Systems.pdf** — Merkle, R. C. (1979). *Secrecy, Authentication, and Public Key Systems*. Ph.D. dissertation, Stanford University. *Introduces Merkle trees — the hash-tree structure underlying all content addressing in Common Graph.*

- **Merkle 1988 - A Digital Signature Based on Conventional Encryption.pdf** — Merkle, R. C. (1988). "A Digital Signature Based on a Conventional Encryption Function." In *CRYPTO '87*, Springer. *The compact presentation of Merkle trees for digital signatures.*

- **Benet 2014 - IPFS Content Addressed Versioned P2P File System.pdf** — Benet, J. (2014). "IPFS — Content Addressed, Versioned, P2P File System." arXiv:1407.3561. *Content-addressed storage, Merkle DAGs, and self-certifying naming. Common Graph's storage architecture shares core principles with IPFS.*

- **Tschudin, Baumann 2019 - Merkle-CRDTs.pdf** — Tschudin, C. & Baumann, A. (2019). "Merkle-CRDTs: Merkle-DAGs meet CRDTs." arXiv:1907.12487. *Content-addressed CRDTs — merging Merkle DAG integrity with conflict-free replication. Directly relevant to Common Graph's storage and sync model.*

---

## Cryptography and Trust

Common Graph's signing model (Ed25519, device keys, key logs) and trust policies build on these foundations.

- **Diffie, Hellman 1976 - New Directions in Cryptography.pdf** — Diffie, W. & Hellman, M. E. (1976). "New Directions in Cryptography." *IEEE Transactions on Information Theory*, 22(6), 644-654. *The foundational paper introducing public-key cryptography — the conceptual basis for all of Common Graph's signing and key exchange.*

- **Bernstein et al 2012 - High-Speed High-Security Signatures Ed25519.pdf** — Bernstein, D. J., Duif, N., Lange, T., Schwabe, P., & Yang, B.-Y. (2012). "High-Speed High-Security Signatures." *Journal of Cryptographic Engineering*, 2, 77-89. *The Ed25519 signature scheme — Common Graph's signing primitive.*

- **Szabo 1997 - Formalizing and Securing Relationships on Public Networks.pdf** — Szabo, N. (1997). "Formalizing and Securing Relationships on Public Networks." *First Monday*, 2(9). *Smart contracts — formalizing agreements as executable protocols. Common Graph's signed relations and policy engine echo this vision.*

- **Nakamoto 2008 - Bitcoin A Peer-to-Peer Electronic Cash System.pdf** — Nakamoto, S. (2008). "Bitcoin: A Peer-to-Peer Electronic Cash System." *Proof-of-work consensus, Merkle trees for transaction integrity, and decentralized trust without central authority. Demonstrated that content-addressed, signed data structures can form a global consensus system.*

- **Nathan 2005 - Visualizing the PGP Web of Trust.doc** — Nathan, D. (2005). "Visualizing the PGP Web of Trust." *Visualization of decentralized trust networks — relevant to Common Graph's trust model.*

---

## Capability-Based Security

Common Graph's trust model — where items carry capabilities and access is mediated by signed relations — draws from capability-based security.

- **Dennis, Van Horn 1966 - Programming Semantics for Multiprogrammed Computations.pdf** — Levy, H. M. (1984). "Early Capability Architectures." In *Capability-Based Computer Systems*, Chapter 3. (Covers Dennis & Van Horn 1966.) *The origin of capability-based protection — access rights as unforgeable tokens.*

- **Miller 2006 - Robust Composition.pdf** — Miller, M. S. (2006). *Robust Composition: Towards a Unified Approach to Access Control and Concurrency Control*. Ph.D. dissertation, Johns Hopkins University. *Object-capability security — enabling cooperation while minimizing destructive interference. The intellectual framework for Common Graph's trust-through-capabilities model.*

---

## Peer-to-Peer Networks and Distributed Hash Tables

Common Graph's networking layer (peer discovery, content routing, relay forwarding) builds on DHT and P2P research.

- **Ratnasamy et al 2001 - A Scalable Content-Addressable Network.pdf** — Ratnasamy, S. et al. (2001). "A Scalable Content-Addressable Network." In *Proceedings of SIGCOMM*. *Content-Addressable Network (CAN) — a distributed hash table using d-dimensional coordinate spaces for routing.*

- **Stoica et al 2001 - Chord Scalable Peer-to-Peer Lookup.pdf** — Stoica, I., Morris, R., Karger, D., Kaashoek, M. F., & Balakrishnan, H. (2001). "Chord: A Scalable Peer-to-peer Lookup Protocol for Internet Applications." *IEEE/ACM Transactions on Networking*. *Consistent hashing ring for scalable peer lookup — O(log N) hops.*

- **Maymounkov, Mazieres 2002 - Kademlia DHT.pdf** — Maymounkov, P. & Mazieres, D. (2002). "Kademlia: A Peer-to-peer Information System Based on the XOR Metric." In *IPTPS*. *XOR-distance routing — the DHT design used by BitTorrent, IPFS, and Ethereum. Relevant to Common Graph's peer routing.*

- **Rowstron, Druschel 2001 - Pastry Scalable Decentralized Object Location and Routing.pdf** — Rowstron, A. & Druschel, P. (2001). "Pastry: Scalable, Decentralized Object Location, and Routing for Large-Scale Peer-to-Peer Systems." In *Middleware 2001*, Springer. *Prefix-based DHT routing with proximity awareness.*

- **Zhao, Kubiatowicz, Joseph 2001 - Tapestry Fault-Tolerant Wide-Area Location and Routing.pdf** — Zhao, B. Y., Kubiatowicz, J. D., & Joseph, A. D. (2001). "Tapestry: An Infrastructure for Fault-tolerant Wide-area Location and Routing." Technical Report UCB/CSD-01-1141, UC Berkeley. *Suffix-based overlay routing with locality optimization.*

- **Clarke et al 2001 - Freenet Distributed Anonymous Information Storage.pdf** — Clarke, I., Sandberg, O., Wiley, B., & Hong, T. W. (2001). "Freenet: A Distributed Anonymous Information Storage and Retrieval System." In *Designing Privacy Enhancing Technologies*, Springer. *Anonymous content-addressed P2P storage with adaptive routing — no centralized index, files dynamically replicated near requestors.*

- **Tarr et al 2019 - Secure Scuttlebutt Identity-Centric Protocol.pdf** — Tarr, D., Lavoie, E., Meyer, A., & Tschudin, C. (2019). "Secure Scuttlebutt: An Identity-Centric Protocol for Subjective and Decentralized Applications." In *ACM ICN '19*. *Identity-centric append-only log replication — the closest existing protocol to Common Graph's signed-manifest, local-first design.*

---

## Consensus and Distributed Time

Common Graph's protocol layer (signed manifests, version ordering, conflict resolution) operates in distributed time.

- **Lamport 1978 - Time Clocks and Ordering of Events.pdf** — Lamport, L. (1978). "Time, Clocks, and the Ordering of Events in a Distributed System." *Communications of the ACM*, 21(7), 558-565. *The foundational paper on logical clocks and causal ordering — the theoretical basis for ordering events without global time.*

- **Mattern 1989 - Virtual Time and Global States of Distributed Systems.pdf** — Mattern, F. (1989). "Virtual Time and Global States of Distributed Systems." In *Parallel and Distributed Algorithms*, North-Holland. *Vector clocks — capturing complete causal relationships between distributed events.*

- **Baird 2016 - Swirlds Hashgraph Consensus Algorithm.pdf** — Baird, L. (2016). "The Swirlds Hashgraph Consensus Algorithm." Swirlds Tech Report. *Hashgraph consensus via virtual voting on a gossip-about-gossip DAG.*

- **Baird 2016 - Hashgraph Consensus Detailed Examples.pdf** — Baird, L. (2016). "Hashgraph Consensus: Detailed Examples." Swirlds Tech Report. *Worked examples of the hashgraph consensus algorithm.*

- **Shapiro 2011 - Conflict-free Replicated Data Types.pdf** — Shapiro, M. et al. (2011). "Conflict-free Replicated Data Types." In *SSS 2011*, Springer. *CRDTs — data structures that converge without coordination. Relevant to Common Graph's eventual consistency model.*

---

## Local-First Software

Common Graph is local-first by design — user data lives on user devices, networking is explicit, and sync is merge-based.

- **Kleppmann 2019 - Local-First Software.pdf** — Kleppmann, M. et al. (2019). "Local-First Software: You Own Your Data, in spite of the Cloud." In *Onward! 2019*. *The manifesto for local-first software — seven ideals for data ownership, offline capability, and collaboration without servers.*

---

## Network Architecture

Common Graph's protocol design exists in dialogue with the dominant network architecture of the web.

- **Fielding 2000 - Architectural Styles and Network-Based Software REST.pdf** — Fielding, R. T. (2000). *Architectural Styles and the Design of Network-based Software Architectures*. Ph.D. dissertation, University of California, Irvine. *Defines REST — the architectural style of the web. Common Graph's Peer Protocol departs from REST's stateless client-server model toward signed, content-addressed peer-to-peer exchange.*

---

## Serialization

Common Graph's CG-CBOR encoding (deterministic, tagged, no floats) builds on CBOR.

- **Bormann, Hoffman 2013 - CBOR Concise Binary Object Representation RFC 7049.txt** — Bormann, C. & Hoffman, P. (2013). "Concise Binary Object Representation (CBOR)." RFC 7049. *The base serialization format that CG-CBOR extends with custom tags and deterministic encoding rules.*

---

## Formal Languages and Syntax

- **Scowen 1996 - EBNF A Notation to Describe Syntax.pdf** — Scowen, R. S. (1996). "EBNF: A Notation to Describe Syntax." ISO/IEC 14977. *The standard notation for describing formal language syntax.*

---
