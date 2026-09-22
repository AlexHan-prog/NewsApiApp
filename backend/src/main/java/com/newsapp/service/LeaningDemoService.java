package com.newsapp.service;

import com.newsapp.model.Article;
import com.newsapp.model.ProviderWarning;
import com.newsapp.model.SearchCriteria;
import com.newsapp.model.SearchResult;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * A small, fixed demonstration of the political-leaning feature: the Guardian's latest Politics articles, capped at
 * {@code leaning.demo-count}, each classified by {@link PoliticalLeaningService}. Nothing else in the app calls the
 * classifier - a normal search, even one filtered to the Politics section, is never classified. That keeps the cost
 * and latency of an ordinary search unaffected, and bounds the whole feature to a handful of (cached) Claude calls.
 */
@Service
public class LeaningDemoService {

    static final String LEANING_PROVIDER = "Political leaning";

    private static final Logger log = LoggerFactory.getLogger(LeaningDemoService.class);

    private final GuardianProvider guardianProvider;
    private final PoliticalLeaningService politicalLeaning;
    private final int count;
    private final List<String> sections;

    public LeaningDemoService(
            GuardianProvider guardianProvider,
            PoliticalLeaningService politicalLeaning,
            @Value("${leaning.demo-count}") int count,
            @Value("${leaning.demo-sections}") String sections) {
        this.guardianProvider = guardianProvider;
        this.politicalLeaning = politicalLeaning;
        this.count = count;
        this.sections = List.of(sections.split(","));
    }

    /**
     * Fetches the fixed set of demo articles from the Guardian (its own cache/budget apply, same as any other
     * Guardian search) and classifies each one in turn - deliberately sequential, since there are only a handful.
     * One article failing to classify doesn't stop the rest; every failure is folded into a single warning.
     */
    public SearchResult demo() {
        List<Article> headlines = guardianProvider
                .search(new SearchCriteria("", new TreeSet<>(), new TreeSet<>(sections), new TreeSet<>()))
                .stream()
                .limit(count)
                .toList();

        List<Article> classified = new ArrayList<>();
        int failures = 0;
        String lastFailureReason = null;
        for (Article article : headlines) {
            try {
                classified.add(article.withLeaning(politicalLeaning.classify(article)));
            } catch (ResponseStatusException e) {
                failures++;
                lastFailureReason = e.getReason();
                log.warn("Could not classify a demo article: {}", e.getReason());
                classified.add(article);
            }
        }

        List<ProviderWarning> warnings = failures == 0
                ? List.of()
                : List.of(new ProviderWarning(
                        LEANING_PROVIDER,
                        failures + " of " + headlines.size() + " articles could not be classified: "
                                + lastFailureReason));
        return new SearchResult(classified, warnings);
    }
}
