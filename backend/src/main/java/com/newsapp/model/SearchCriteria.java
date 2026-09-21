package com.newsapp.model;

import java.util.Collection;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * What the user asked for, already normalized by the controller (lower-case, de-duplicated, validated). The sets are
 * sorted so the same selection always produces the same {@link #cacheKey()}. The keyword keeps its original case
 * because NewsAPI and the Guardian only treat upper-case AND/OR/NOT as operators.
 *
 * @param keyword  free text, may be empty when another filter is given
 * @param domains  outlet domains such as {@code bbc.co.uk}
 * @param sections Guardian section ids such as {@code technology}
 * @param tags     Guardian tag ids such as {@code technology/apple}
 */
public record SearchCriteria(
        String keyword, SortedSet<String> domains, SortedSet<String> sections, SortedSet<String> tags) {

    public SearchCriteria {
        keyword = keyword == null ? "" : keyword.strip();
        domains = immutableSorted(domains);
        sections = immutableSorted(sections);
        tags = immutableSorted(tags);
    }

    /** True if nothing at all was asked for; the controller rejects that. */
    public boolean isEmpty() {
        return keyword.isEmpty() && domains.isEmpty() && sections.isEmpty() && tags.isEmpty();
    }

    /** Distinct for every distinct search, so it can key a cache. */
    public String cacheKey() {
        return keyword + '|' + String.join(",", domains) + '|' + String.join(",", sections) + '|'
                + String.join(",", tags);
    }

    /**
     * 
     * @param values e.g. domains: a.com, b.com...
     * @return SortedSet<String> immutable and sorted set of criteria
     */
    private static SortedSet<String> immutableSorted(Collection<String> values) {
        return Collections.unmodifiableSortedSet(new TreeSet<>(values == null ? Collections.emptySet() : values));
    }
}
