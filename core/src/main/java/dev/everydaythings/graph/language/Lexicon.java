package dev.everydaythings.graph.language;

import dev.everydaythings.graph.frame.Selectable;
import dev.everydaythings.graph.item.id.ItemID;

import java.util.*;
import java.util.stream.Stream;

/**
 * A collection of lexemes (word→sememe mappings) for a language.
 *
 * <p>Lexeme storage and lookup only. Token indexing is handled by the
 * frame-backed posting pipeline — LEXEME frames with indexed NAME bindings
 * are the canonical source of word→sememe mappings in the TokenDictionary.
 *
 * <p>This class will be replaced by direct frame queries once the
 * Vocabulary scope stack is implemented.
 */
public class Lexicon implements Selectable {

    private final ItemID languageId;
    private final List<Lexeme> lexemes = new ArrayList<>();

    public Lexicon(ItemID languageId) {
        this.languageId = Objects.requireNonNull(languageId, "languageId");
    }

    public static Lexicon forLanguage(ItemID languageId) {
        return new Lexicon(languageId);
    }

    public void add(Lexeme lexeme) {
        Objects.requireNonNull(lexeme, "lexeme");
        lexemes.add(lexeme);
    }

    public void addAll(Collection<Lexeme> lexemesToAdd) {
        Objects.requireNonNull(lexemesToAdd, "lexemesToAdd");
        lexemes.addAll(lexemesToAdd);
    }

    public Stream<Lexeme> lookup(String word) {
        String normalized = Posting.normalize(word);
        return lexemes.stream()
                .filter(l -> Posting.normalize(l.word()).equals(normalized));
    }

    public Stream<Lexeme> lookup(String word, ItemID pos) {
        String normalized = Posting.normalize(word);
        return lexemes.stream()
                .filter(l -> Posting.normalize(l.word()).equals(normalized))
                .filter(l -> pos.equals(l.partOfSpeech()));
    }

    public List<Lexeme> lexemes() { return List.copyOf(lexemes); }
    public ItemID languageId() { return languageId; }
    public int size() { return lexemes.size(); }

    @Override
    public Optional<Selectable> select(Query q) {
        return Optional.empty();
    }
}
