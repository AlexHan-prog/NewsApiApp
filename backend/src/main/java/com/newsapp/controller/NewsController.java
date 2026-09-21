package com.newsapp.controller;

import com.newsapp.model.Article;
import com.newsapp.model.SearchCriteria;
import com.newsapp.service.ArticleSearchService;

import java.util.List;
import java.util.Locale;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class NewsController {

    private static final Pattern DOMAIN = Pattern.compile("[a-z0-9.-]+");
    // Guardian ids: lower-case words joined by '-', e.g. "technology" or "us-news".
    private static final Pattern SECTION = Pattern.compile("[a-z0-9-]+");
    // Guardian tag ids are section/name paths, e.g. "technology/apple" or "tone/news".
    private static final Pattern TAG = Pattern.compile("[a-z0-9-]+(/[a-z0-9-]+)*");

    private final ArticleSearchService articleSearchService;

    public NewsController(ArticleSearchService articleSearchService) {
        this.articleSearchService = articleSearchService;
    }

    /**
     * A search needs a keyword, an outlet, a section or a tag (or any mix). {@code domains}, {@code sections} and
     * {@code tags} are comma-separated. Sections and tags are Guardian filters, so they narrow the search to it.
     */
    @GetMapping("/articles")
    public List<Article> search(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) String domains,
            @RequestParam(required = false) String sections,
            @RequestParam(required = false) String tags) {
        SearchCriteria criteria = new SearchCriteria(
                keyword,
                normalizeList(domains, DOMAIN, "domain"),
                normalizeList(sections, SECTION, "section"),
                normalizeList(tags, TAG, "tag"));
        if (criteria.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Enter a keyword or pick at least one outlet or section");
        }
        return articleSearchService.search(criteria);
    }

    /**
     * Lower-cases, de-duplicates and sorts so the same selection always yields the same set; the search cache key is
     * built from it, so "B.com,a.com" and "a.com,b.com" share one cache entry. Never null.
     */
    private static SortedSet<String> normalizeList(String csv, Pattern valid, String what) {
        SortedSet<String> unique = new TreeSet<String>();
        if (csv == null) {
            return unique;
        }
        for (String part : csv.split(",")) {
            String value = part.strip().toLowerCase(Locale.ROOT);
            if (value.isEmpty()) {
                continue;
            }
            if (!valid.matcher(value).matches()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid " + what + ": " + part.strip());
            }
            unique.add(value);
        }
        return unique;
    }
}
