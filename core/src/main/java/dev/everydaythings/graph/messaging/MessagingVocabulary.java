package dev.everydaythings.graph.messaging;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;

/**
 * Messaging vocabulary — predicates for content-bearing frames sent between
 * signers.
 *
 * <p>A {@link Message} frame is the canonical "I sent you a thing" primitive.
 * Used uniformly for chat messages, imported emails, and any other free-form
 * communication.  The body carries a {@code VALUE} binding for the content;
 * optional bindings name the sender (Agent), recipient (Recipient), location
 * (Location, for room-style grouping), time (Time), and so on.
 *
 * <p>Encryption is orthogonal: any frame body, including a MESSAGE body, may
 * be wrapped in {@code Opaque.Encrypted}.  See
 * {@link dev.everydaythings.graph.identity.vault.Vault Vault} session methods
 * for the Double-Ratchet path that produces such wrappers.
 */
public final class MessagingVocabulary {

    private MessagingVocabulary() {}

    /**
     * MESSAGE — a content-bearing communication from one signer toward one or
     * more recipients.  The body carries the content as a value binding;
     * other bindings name the participants and context.
     */
    @Seed.Item(key = Message.KEY)
    public static final class Message {
        public static final String KEY = "cg.predicate:message";
        private Message() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "a content-bearing communication from one signer toward one or more "
                        + "recipients; the body carries the content as a value binding";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "message";
    }
}
