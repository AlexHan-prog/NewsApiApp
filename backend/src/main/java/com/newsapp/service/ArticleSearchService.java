package com.newsapp.service;

import com.newsapp.model.Article;
import com.newsapp.model.SearchCriteria;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Sends one search to every {@link NewsProvider} that can honour it, then merges the answers into one list, newest
 * first, with duplicate URLs removed. If some providers fail the others' articles are still returned; only if every
 * provider that was asked fails does the search fail.
 */
@Service
public class ArticleSearchService {

    private static final Logger log = LoggerFactory.getLogger(ArticleSearchService.class);

    private final List<NewsProvider> providers;
    // The providers are different upstreams and mostly wait on I/O, so they are called side by side.
    private final ExecutorService executor;

    public ArticleSearchService(List<NewsProvider> providers) {
        this.providers = List.copyOf(providers);
        this.executor = Executors.newFixedThreadPool(Math.max(1, providers.size()), runnable -> {
            Thread thread = new Thread(runnable, "news-provider");
            thread.setDaemon(true);
            return thread;
        });
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    /**
     * Asks each provider's narrow() whether it can honour every filter,
     * E.g. NewsAPI cannot filter by section so its skipped and TheGuardian provider
     * can only source guardian articles ofc.
     * @param requested
     * @return
     */
    public List<Article> search(SearchCriteria requested) {
        List<Call> calls = new ArrayList<>();
        for (NewsProvider provider : providers) {
            provider.narrow(requested).ifPresent(criteria -> calls.add(new Call(provider, criteria)));
        }

        List<CompletableFuture<Outcome>> futures = calls.stream()
                .map(call -> CompletableFuture.supplyAsync(() -> run(call), executor))
                .toList();

        List<Article> merged = new ArrayList<>();
        ResponseStatusException firstFailure = null;
        int failures = 0;
        for (CompletableFuture<Outcome> future : futures) {
            Outcome outcome = future.join();
            if (outcome.failure() != null) {
                failures++;
                if (firstFailure == null) {
                    firstFailure = outcome.failure();
                }
            } else {
                merged.addAll(outcome.articles());
            }
        }
        if (failures > 0 && failures == calls.size()) {
            throw firstFailure;
        }
        return dedupeAndSort(merged);
    }

    private Outcome run(Call call) {
        try {
            return new Outcome(call.provider().search(call.criteria()), null);
        } catch (ResponseStatusException e) {
            log.warn("{} failed: {}", call.provider().name(), e.getReason());
            return new Outcome(List.of(), e);
        } catch (RuntimeException e) {
            log.warn("{} failed unexpectedly", call.provider().name(), e);
            return new Outcome(
                    List.of(),
                    new ResponseStatusException(HttpStatus.BAD_GATEWAY, call.provider().name() + " failed"));
        }
    }

    /**
     * Drops duplicate URLS and sorts newest first by published date
     * @param articles
     * @return
     */
    private static List<Article> dedupeAndSort(List<Article> articles) {
        Map<String, Article> byUrl = new LinkedHashMap<>();
        for (Article article : articles) {
            if (article.url() != null) {
                byUrl.putIfAbsent(article.url(), article);
            }
        }
        List<Article> sorted = new ArrayList<>(byUrl.values());
        sorted.sort(Comparator.comparing(ArticleSearchService::published).reversed());
        return sorted;
    }

    /** Providers format the timestamp slightly differently (Z, +00:00, fractions), so parse tolerantly. */
    private static Instant published(Article article) {
        if (article.publishedAt() == null) {
            return Instant.MIN;
        }
        try {
            return OffsetDateTime.parse(article.publishedAt()).toInstant();
        } catch (DateTimeParseException e) {
            return Instant.MIN;
        }
    }

    private record Call(NewsProvider provider, SearchCriteria criteria) {}

    private record Outcome(List<Article> articles, ResponseStatusException failure) {}
}
