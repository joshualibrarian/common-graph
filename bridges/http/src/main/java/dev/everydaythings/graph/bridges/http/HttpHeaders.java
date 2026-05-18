package dev.everydaythings.graph.bridges.http;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Case-insensitive HTTP header collection.
 *
 * <p>HTTP header names are case-insensitive (RFC 7230 §3.2): {@code
 * Content-Type}, {@code content-type}, {@code CONTENT-TYPE} all refer to
 * the same header.  Stored under the canonical lower-cased form;
 * preserves insertion order so headers serialize in a predictable order.
 *
 * <p>Multiple values per name are supported (some headers like {@code
 * Set-Cookie} legitimately repeat) but the common single-valued case is
 * the ergonomic one — {@link #first} returns the first value.
 *
 * <p>Mutable.  Build one, populate it, hand it off — instances should
 * not be shared concurrently.
 */
public final class HttpHeaders {

    private final Map<String, List<String>> entries = new LinkedHashMap<>();

    public HttpHeaders() {}

    /** Add a value for a header, preserving existing values. */
    public HttpHeaders add(String name, String value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        entries.computeIfAbsent(canonicalize(name), k -> new ArrayList<>()).add(value);
        return this;
    }

    /** Replace any existing values for a header with the single new one. */
    public HttpHeaders set(String name, String value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        List<String> single = new ArrayList<>(1);
        single.add(value);
        entries.put(canonicalize(name), single);
        return this;
    }

    /** Remove all values for a header.  No-op if absent. */
    public HttpHeaders remove(String name) {
        Objects.requireNonNull(name, "name");
        entries.remove(canonicalize(name));
        return this;
    }

    /** First value of a header, if present. */
    public Optional<String> first(String name) {
        Objects.requireNonNull(name, "name");
        List<String> values = entries.get(canonicalize(name));
        if (values == null || values.isEmpty()) return Optional.empty();
        return Optional.of(values.get(0));
    }

    /** All values of a header (empty list if absent). */
    public List<String> all(String name) {
        Objects.requireNonNull(name, "name");
        List<String> values = entries.get(canonicalize(name));
        return values == null ? Collections.emptyList() : Collections.unmodifiableList(values);
    }

    /** True if any value is set for the header. */
    public boolean contains(String name) {
        Objects.requireNonNull(name, "name");
        List<String> values = entries.get(canonicalize(name));
        return values != null && !values.isEmpty();
    }

    /** Snapshot of all entries, in insertion order, names in canonical lower-case. */
    public Map<String, List<String>> asMap() {
        Map<String, List<String>> copy = new LinkedHashMap<>(entries.size());
        for (Map.Entry<String, List<String>> e : entries.entrySet()) {
            copy.put(e.getKey(), List.copyOf(e.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    public boolean isEmpty() { return entries.isEmpty(); }
    public int size() { return entries.size(); }

    private static String canonicalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "HttpHeaders" + entries;
    }
}
