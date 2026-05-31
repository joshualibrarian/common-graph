package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.HashID;
import dev.everydaythings.graph.ref.ItemRef;

import java.util.List;
import java.util.Optional;

/**
 * Helpers for runtime-language implementation bindings on item manifests.
 *
 * <p>An implementation binding declares "this item's runtime form is expressed
 * in this language, via this form (class name, source code, bytecode CID)."
 * The binding's role is the language sememe (Java, Python, Lisp, etc.); the
 * qualifier is the form sememe (ClassName, SourceCode); the target is the
 * concrete reference.
 *
 * <p>{@link Manifest} carries the parametric primitive
 * ({@link Manifest#implementation(ItemRef, ItemRef, Object)}); this class
 * carries the language-specific shortcuts and queries that don't belong on
 * Manifest itself (Manifest stays language-agnostic and substrate-shape).
 */
public final class Implementations {

    private Implementations() {}

    /** Build a Java implementation binding ({@code JAVA:[ClassName] → fqcn}). */
    public static Binding forJava(Class<?> clazz) {
        return Manifest.implementation(
                ItemRef.iid(RuntimeVocabulary.Java.KEY),
                ItemRef.iid(RuntimeVocabulary.ClassName.KEY),
                clazz.getName());
    }

    /**
     * Whether a binding is a Java implementation binding — role is
     * {@link RuntimeVocabulary.Java} and the qualifier list includes
     * {@link RuntimeVocabulary.ClassName}.
     */
    public static boolean isJava(Binding b) {
        if (!ItemRef.iid(RuntimeVocabulary.Java.KEY).equals(b.role())) {
            return false;
        }
        for (var q : b.qualifiers()) {
            if (q instanceof CompoundKey.Sememe s
                    && ItemRef.iid(RuntimeVocabulary.ClassName.KEY).equals(s.id())) {
                return true;
            }
        }
        return false;
    }

    /**
     * First implementation binding on {@code manifest} whose role is a known
     * runtime language sememe (Java, Python, Lisp, JavaScript, Clojure, Rust).
     * Returns empty if none.
     */
    public static Optional<Binding> firstKnownLanguage(Manifest manifest) {
        for (Binding b : manifest.body().bindings()) {
            if (isKnownLanguageRole(b.role())) return Optional.of(b);
        }
        return Optional.empty();
    }

    /** All implementation bindings whose role is a known runtime language sememe. */
    public static List<Binding> allKnownLanguages(Manifest manifest) {
        return manifest.body().bindings().stream()
                .filter(b -> isKnownLanguageRole(b.role()))
                .toList();
    }

    private static boolean isKnownLanguageRole(HashID role) {
        return ItemRef.iid(RuntimeVocabulary.Java.KEY).equals(role)
                || ItemRef.iid(RuntimeVocabulary.Python.KEY).equals(role)
                || ItemRef.iid(RuntimeVocabulary.Lisp.KEY).equals(role)
                || ItemRef.iid(RuntimeVocabulary.JavaScript.KEY).equals(role)
                || ItemRef.iid(RuntimeVocabulary.Clojure.KEY).equals(role)
                || ItemRef.iid(RuntimeVocabulary.Rust.KEY).equals(role);
    }
}
