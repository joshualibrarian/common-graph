package dev.everydaythings.graph.item;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Canonical.Canon;
import dev.everydaythings.graph.item.component.Component;
import dev.everydaythings.graph.item.component.Type;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * A Person — a named entity in the graph.
 *
 * <p>Persons are not Signers. They have no keys, no vault, no ability to sign.
 * They are simply named entities that can participate in relations, be seated
 * at game tables, be credited as authors, or referenced in any context that
 * needs a human identity without authentication.
 *
 * <p>Use cases:
 * <ul>
 *   <li>Chess game players — "Fischer, Robert J." vs "Spassky, Boris V."</li>
 *   <li>Authors of imported works</li>
 *   <li>Historical figures referenced in relations</li>
 *   <li>Contacts that haven't yet been linked to a Signer</li>
 * </ul>
 *
 * <p>A Person is a component type. Person Items are regular Items with this
 * component attached. A Person can later be linked to a Signer via a relation
 * (e.g., {@code person SAME_AS signer}) when the real identity is established.
 */
@Type(value = Person.KEY, glyph = "👤", color = 0x6B8E8E)
@Canonical.Canonization
@Getter @Accessors(fluent = true) @NoArgsConstructor
public class Person implements Canonical, Component {

    public static final String KEY = "cg:type/person";

    @Canon(order = 0) private String name;

    public Person(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name != null ? name : "Unknown";
    }
}
