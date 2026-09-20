package com.newsapp.controller;

import com.newsapp.model.NewsApiArticle;
import com.newsapp.service.NewsApiSearchService;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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

    private final NewsApiSearchService newsApiSearchService;

    public NewsController(NewsApiSearchService newsApiSearchService) {
        this.newsApiSearchService = newsApiSearchService;
    }

    /** A search needs a keyword, at least one domain, or both. {@code domains} is comma-separated. */
    @GetMapping("/articles")
    public List<NewsApiArticle> search(
            @RequestParam(defaultValue = "") String keyword, @RequestParam(required = false) String domains) {
        String normalizedKeyword = keyword.strip();
        String normalizedDomains = normalizeDomains(domains);
        if (normalizedKeyword.isEmpty() && normalizedDomains.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a keyword or pick at least one outlet");
        }
        return newsApiSearchService.searchEverything(normalizedKeyword, normalizedDomains);
    }

    /**
     * Lower-cases, de-duplicates and sorts so the same set of domains always yields the same string; the search
     * cache key is built from it, so "B.com,a.com" and "a.com,b.com" share one cache entry. Never null.
     */
    private static String normalizeDomains(String domains) {
        if (domains == null) {
            return "";
        }
        Set<String> unique = new TreeSet<>();
        for (String part : domains.split(",")) {
            String domain = part.strip().toLowerCase(Locale.ROOT);
            if (domain.isEmpty()) {
                continue;
            }
            if (!DOMAIN.matcher(domain).matches()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid domain: " + part.strip());
            }
            unique.add(domain);
        }
        return String.join(",", unique);
    }
}
