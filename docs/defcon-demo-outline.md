# Common Graph: Live Demo

## A Semantic Base Layer in Action

**DEF CON 34 Demo Labs Outline -- 45 minutes (30 demo + 15 Q&A)**
**Presenter: Joshua Chambers**
**Volunteers: up to 2 (badges provided)**

---

### Demo Summary

Common Graph proposes a base layer where data carries its own meaning, every assertion is cryptographically signed, and the network is organized by trust rather than servers.  The accompanying paper ("Below the Application, Above the Bytes") develops the architecture in detail.  This demo makes it tangible.

Three machines on an isolated local network run independent Librarians — the local runtime that makes the substrate real on a given device.  Each renders the same data through a different interface: a 3D graphical client, a 2D graphical client, and a terminal — and one displays everything in a non-English language.  The demo is deliberately non-linear: a chess game plays out between volunteers while the presenter posts and discusses a meme, reactions flow across both simultaneously, and a clock app ticks in the corner the entire time.  This is not three sequential demos.  It is one substrate doing everything at once, the way people actually use computers.

No server coordinates any of it.  Every assertion is signed.  The data is the same; the applications are interchangeable; the language is a rendering choice, not a data transformation.

### Equipment

**Provided by DEF CON**: podium with mic, projector and screen, network access (not relied upon).

**Brought by presenter**:
- Three laptops running Common Graph (Librarian + UI), each with a different rendering surface: Filament 3D, Skia 2D, and JLine terminal
- A travel router creating an isolated local network (not relying on DEF CON WiFi)
- HDMI splitter or switcher to show different machines on the projector at different moments, or a single machine screen-sharing the others
- Printed copies of the paper for attendees

**Volunteers**: Two people with some familiarity with CG, ideally including one who speaks a non-English language (Spanish, German, or other language with CG vocabulary support).  Each operates one of the three machines.

---

## I. Setup (2 minutes)

   1. Three laptops visible on the table.  The projector shows whichever machine is most relevant, switching throughout.
   2. Brief introduction: "Three machines, three different interfaces, one shared data layer.  No server.  Everything you see is signed assertions flowing peer-to-peer over the router on this table.  Nothing leaves this room."
   3. Show the three Librarians peered with each other.  "These are independent runtimes that have established trust relationships.  They will exchange signed frames directly."
   4. Point out that one machine displays its interface in [Spanish/German/other].  "Same data, different language.  No translation is occurring.  The meanings are language-neutral; only the words that surface them differ."

## II. The Clock (1 minute)

   1. Launch the clock app on the presenter's machine.  It appears in the corner of the screen and stays there for the entire demo.
   2. "This is a clock.  It is an item with no frames.  The sememe CLOCK exists in the vocabulary; this app implements it.  It renders a clock face in its scene.  No assertions, no signatures, no storage.  Just code running against an archetype."
   3. Set a TIMER for the demo: `TIMER { (VALUE, DURATION) = 25 minutes }`.  "THAT was a frame.  The clock is not a frame; it is an application.  The timer IS a frame — a signed assertion that something should happen in 25 minutes.  Same item, different layers."
   4. Set an ALARM for 5 minutes before the end: `ALARM { (VALUE, TIME) = [5 min before end] }`.  "Another frame.  These will fire during the demo."
   5. The clock, timer, and alarms are now visible in the corner and will remain there throughout.  They serve as real infrastructure for the presentation, not just a demo artifact.

## III. The Chess Game Begins (2 minutes)

   1. The two volunteers start a chess game.  Create the game item; CHESS is the archetype.
   2. Each player registers with a signed PLAYER frame.  "Each player attests their own participation.  Not assigned by a third party."
   3. The game appears on all three machines: 3D board, 2D board, text notation (in the non-English language on the third machine — piece names, move descriptions all localized).
   4. "They are going to play this game during the rest of the demo.  I am going to mostly ignore them and talk about something else.  Watch what happens."
   5. The volunteers begin playing.  Moves flow between machines in real time throughout the rest of the demo.

## IV. The Meme (8 minutes, overlapping with the ongoing chess game)

Throughout this section, chess moves continue to arrive on all three screens.  The presenter occasionally glances at the game and reacts.

### A. Post the meme (2 minutes)

   1. On the presenter's machine: create a new item.  A meme image — something the DEF CON audience will find funny.  The item is created with frames: IMAGE (the picture), TITLE (a caption), AUTHORED (the poster).
   2. Show the frames that were just created.  Each is signed by the presenter's key.  Each is content-addressed.
   3. Switch projector view: the meme has arrived on the other two machines.  It replicated through the peer connections.  Show that the frames are identical — same body hashes, same content, different location.
   4. On the terminal machine: same meme, rendered as text.  Title, author, image reference.  Same data, third rendering.

### B. Semantic reactions (3 minutes)

   1. A volunteer reacts to the meme: a FUNNY frame, signed by their key.  "Not a generic 'like.'  A specific semantic assertion: this meme is funny."
   2. The other volunteer reacts differently: INSIGHTFUL.  Different predicate, different meaning, same target.
   3. Both reactions appear on the presenter's machine.  Reactions from peers, each signed, each with a specific semantic predicate.
   4. A volunteer adds a reaction with a comment: `HILARIOUS { (THEME) = the-meme, (AGENT) = volunteer, (VALUE) = "I can't stop laughing" }`.  "The comment is a VALUE binding on the reaction frame.  The predicate says HILARIOUS; the VALUE carries the elaboration."
   5. On the non-English machine, the reactions appear with localized labels.  FUNNY appears as GRACIOSO or LUSTIG.  Same frames, different words.
   6. [Meanwhile, a chess move arrives.  The presenter glances over: "Nice move!" and fires off a WOW frame targeting that specific MOVE frame.  "I just reacted to a chess move with the same mechanism I used to react to a meme.  Same primitive, different domain.  My Librarian doesn't know the difference."]

### C. Moderation (3 minutes)

   1. The presenter posts a second meme — deliberately low-quality or off-topic.
   2. A volunteer marks it SPAM.  A SPAM frame targeting the meme, signed by the volunteer.
   3. The other volunteer disagrees — marks the SPAM frame with DISAGREE.  "This is a frame targeting a frame.  They are not reacting to the meme.  They are reacting to the moderation call."
   4. Show the presenter's trust view: depending on whose moderation judgment the presenter trusts more, the meme either fades or stays visible.
   5. "No moderator was appointed.  No appeals board.  No single outcome imposed.  Each machine sees the content through its own trust matrix."
   6. [Another chess move arrives.  Maybe a bad one.  The presenter reacts with a D'OH frame.  The audience sees frames from different domains interleaving naturally.]

## V. Cross-Pollination (3 minutes)

This is where the non-linear flow pays off.  Everything is happening simultaneously.

   1. Point to the screen: "Right now, you are watching chess moves, meme reactions, moderation calls, and a ticking clock, all on the same screen, all arriving through the same peer connections, all signed by identified parties, all using the same primitive.  None of these applications know about each other.  They share a Librarian, a vocabulary, and a trust graph."
   2. Show a query on the presenter's machine: "Show me all frames that arrived in the last 5 minutes."  Chess moves, meme reactions, moderation calls, the timer, the alarm — all returned by the same query mechanism.  All structurally identical.
   3. "A chess move, a meme reaction, a moderation call, and a timer are the same kind of thing: a predicate and role bindings.  That is the entire data model.  There is nothing else."
   4. If a volunteer made a particularly interesting chess move earlier, pull it up by query: `MOVE { (LOCATION) = the-game, (AGENT) = volunteer }`.  Show the WOW and D'OH reactions hanging off specific moves.

## VI. The Big Picture (3 minutes)

   1. The timer fires.  "That was a frame evaluating to now.  Same primitive as the chess move, same primitive as the meme reaction."
   2. "You just watched three completely different applications — a clock, a social meme feed, and a chess game — running simultaneously on the same data layer, rendered by three different interfaces, in two languages, with decentralized moderation, peer-to-peer, with every action signed and attributable.  No server.  No platform.  No lock-in."
   3. "Now imagine this for email, documents, medical records, financial transactions, phone calls.  The primitive is the same.  The vocabulary extends.  The trust model scales.  The architecture does not change."
   4. "The full paper is on the table.  Hard copies.  Take one.  It is 30 pages and it covers everything I did not have time to show you: the scaling analysis, the attack surface, the routing privacy model, the honest reckoning with the projects that tried this before."
   5. Flash GitHub URLs on screen: github.com/joshualibrarian/common-graph and github.com/joshualibrarian/keymaster.
   6. The alarm fires.  "Five minutes left.  Also a frame.  Questions?"

## VII. Q&A (15 minutes)

Open the floor.  Likely questions and prepared answers:

   1. **"How does this handle [X] at scale?"** — Point to the paper's Sanity Check section.  Indexing is linear, social graph shards naturally, HyperLogLog handles distributed counting.  Have the specific numbers ready (4.5 GB for a 5M-reaction viral post, 136 bytes per index entry, 90%+ of items have trivial cost).
   2. **"What about DDoS / Sybil / eclipse attacks?"** — Social graph is the firewall, new identities start at zero trust, frame flooding is self-limiting.  Key compromise is the hard problem; that is what Keymaster addresses.
   3. **"How is this different from [IPFS / Fediverse / Solid / AT Protocol]?"** — Each solves part of the problem.  IPFS moves bytes without meaning.  Fediverse federates servers without removing the client-server boundary.  P2P systems route all data as though it is the same.  This layer carries meaning in the data itself, routes by social relationship, and removes the server entirely.
   4. **"Why would platforms adopt this?"** — They would not adopt it to be altruistic.  They would adopt it because their data is already being scraped, their lock-in is eroding, and becoming the most-trusted node in the graph is a stronger position than holding users hostage.  IMDB's catalog is valuable because of curation quality, not because users cannot leave.
   5. **"What about Szabo's smart contracts?"** — Szabo described the protocols; this is the substrate they share.  Bearer certificates, escrow, liens, accounting controls — all collapse into signed frames between identified parties.  [If the Satoshi speculation comes up, don't force it, but don't avoid it either.]
   6. **"What about the code execution / sandboxing problem?"** — Hard but not novel.  Same kind of hard as browser JavaScript sandboxing.  Trust-gated, not gatekeeper-gated.  Active design area.
   7. **"Can I try it?"** — Yes.  It is open source.  The GitHub link is on screen.  Talk to me after.

---

### Supporting Materials

- Full paper: "Below the Application, Above the Bytes: A Base Layer for Meaning and Ownership" (PDF, ~30 pages)
- Source code: github.com/joshualibrarian/common-graph
- Sister project (open-hardware key vault): github.com/joshualibrarian/keymaster
- Hard copies of the paper available at the demo station

### Presenter Bio

[To be filled in by Joshua]
