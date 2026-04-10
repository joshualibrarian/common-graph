Hi Bob,

Good question, and it exposes something fundamental about how CG differs from traditional file-based systems.

In CG, a book doesn't really have a "location" the way a file or a website does.  A book is an item: a signed collection of frames with a content-addressed identity.  The identity is derived from the content itself, not from where it happens to be stored.  The book just IS.

Let me walk through a concrete example.  Suppose you write a book, and I follow your public work.  Here's what happens, step by step:

You author the book.  You begin by generating a unique random ItemID that identifies the book as a whole.  This ItemID is the book's permanent logical identity; it stays the same across every future version.  Then you create the frames that constitute the book: a TITLE frame ("My Excellent Book"), an AUTHOR frame binding you (the Item that represents you, your public identity) as AGENT, TEXT frames for each chapter, COVER_ART frames, PUBLICATION frames, and so on.  Each frame is signed with your private key, and each has its own ContentID, which is the hash of its body.  As you revise your book, each version is captured as a signed manifest that bundles references to the frames making up that version, and each manifest is itself content-addressed by its own hash.  So the book has a stable ItemID, but many versions, each with its own ContentID.  None of this touches a physical filesystem path.  You're not choosing a location; you're making structured semantic assertions and collecting them into versions of an item.

Your librarian persists them.  Your local librarian is the runtime node on your machine, sort of like a local git clone or a local database (under the hood, it literally is a local database).  As you create each frame, your librarian persists the bytes to its object store and indexes the frame by its predicate and each meaning in its binding keys.  As you produce new versions, those manifests are persisted and indexed too.  The book is queryable on your machine: any frame you've created can be found by meaning (there are no "filenames").

You decide to publish.  When the book is finished, you change the book's replication policy.  Publishing is a policy change, not an event.  From that moment on, your librarian treats the book as something the world is allowed to see, and begins propagating it accordingly.

Replication policy takes over.  Policy in CG is expressed as CONFIG bindings attached to items and frames, declaring what should propagate to which peers and under what conditions.  Other policies (encryption, durability, and so on) work the same way, as CONFIG bindings on the items and frames they govern.  When the book's policy changes, those bindings are what your librarian consults, and it starts pushing the book's latest manifest (and the frames it references) toward the destinations those policies name.

Your social relations shape who gets it.  I follow your public work.  "Following" in CG is a signed frame asserting a FOLLOWS relationship from me to you.  The exact mechanics are still being worked out in the implementation, but the general shape is this: your librarian knows I'm subscribed to your public output because that FOLLOWS frame has been replicated to you (using the same mechanisms).  So when you publish the book, my librarian is one of the destinations your replication policy knows to reach.  This is the same mechanism that carries everything you share with followers, from a book to a common "social media" style post.

Peer replication delivers the bytes.  Your librarian sends a Delivery message over the peer protocol to mine, carrying the new manifest and the frames it references.  My librarian receives them, verifies the signatures, and persists them to its own store.  My local librarian now has a replica of the book: the same ItemID, the same manifest ContentID, the same frame ContentIDs as yours.  Content-addressing makes that identity trivially verifiable.

I read the book.  I ask my local librarian for the book by its ItemID.  It's locally persisted now, so I get it immediately.  No network round-trip, no filesystem path, no "where" in the traditional sense.  The book just IS on my librarian, and on yours, and on anyone else who's replicated it.  And any frames I create about your book (a LIKE frame, a FUNNY frame, a comment, a discussion) travel back to you the same way, through the same peer-replication mechanism.

This is fundamentally a peer-to-peer system.  There's no central server.  Every librarian is a peer.  Data flows between librarians based on trust relationships (who you follow, who follows you, what you've declared public) and the replication policies those relationships imply.  The same content may live on dozens of librarians, all referenced by the same ContentIDs, and all of those replicas are equally legitimate.

"Location" in CG is about which peers have replicas, driven by social trust and declarative policy.  There is no single place that the item is.  There are many, and the network decides where the data goes by following your intent.

Yours,
Joshua
