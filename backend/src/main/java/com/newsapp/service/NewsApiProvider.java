package com.newsapp.service;

import com.newsapp.model.Article;
import com.newsapp.model.NewsApiArticle;
import com.newsapp.model.NewsApiResponse;
import com.newsapp.model.SearchCriteria;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/** NewsAPI's /v2/everything. It has no section or tag filter, but can restrict a search to outlet domains. */
@Service
public class NewsApiProvider implements NewsProvider {

    public static final String NAME = "NewsAPI";

    private static final Logger log = LoggerFactory.getLogger(NewsApiProvider.class);

    private final RestClient restClient;
    private final DailyCallBudget dailyCallBudget;

    public NewsApiProvider(
            @Qualifier("newsApiRestClient") RestClient restClient,
            @Qualifier("newsApiCallBudget") DailyCallBudget dailyCallBudget) {
        this.restClient = restClient;
        this.dailyCallBudget = dailyCallBudget;
    }

    @Override
    public String name() {
        return NAME;
    }

    /**
     * Skipped for section/tag searches, which it can't filter. The Guardian's own domain is left to
     * {@link GuardianProvider} (NewsAPI's free plan returns nothing for it); if that was the only outlet asked for,
     * there is nothing left for NewsAPI to do.
     */
    @Override
    public Optional<SearchCriteria> narrow(SearchCriteria requested) {
        if (!requested.sections().isEmpty() || !requested.tags().isEmpty()) {
            return Optional.empty();
        }
        TreeSet<String> domains = new TreeSet<>(requested.domains());
        domains.remove(GuardianProvider.DOMAIN);
        if (domains.isEmpty() && !requested.domains().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SearchCriteria(requested.keyword(), domains, new TreeSet<>(), new TreeSet<>()));
    }

    /**
     * The body only runs on a cache miss, so cache hits never touch the daily budget. The key keeps the original
     * keyword case because NewsAPI only treats upper-case AND/OR/NOT as operators, for example 'cats and dogs' is a
     * different search to cats AND dogs.
     * {@code sync} makes concurrent identical searches share one call; exceptions are not cached.
     * The cache is set up as TinyLFU, meaning the LFU entry is evicted first if cache is full.
     * <p>
     * The key is {@link SearchCriteria#cacheKey()}, so the same keyword filtered to different outlets is a separate
     * entry. The keyword may be empty when domains are given.
     */
    @Override
    @Cacheable(cacheNames = "articles", key = "#criteria.cacheKey()", sync = true)
    public List<Article> search(SearchCriteria criteria) {
        if (!dailyCallBudget.tryConsume()) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS, "Daily NewsAPI request budget reached. It resets at 00:00 UTC.");
        }
        String domains = String.join(",", criteria.domains());
        log.info("NewsAPI call for \"{}\" domains=\"{}\"", criteria.keyword(), domains);
        try {
            NewsApiResponse response = restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/everything");
                        if (!criteria.keyword().isEmpty()) {
                            uriBuilder.queryParam("q", criteria.keyword());
                        }
                        if (!domains.isEmpty()) {
                            uriBuilder.queryParam("domains", domains);
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(NewsApiResponse.class);

            return response == null || response.articles() == null
                    ? List.of()
                    : response.articles().stream().map(NewsApiProvider::toArticle).toList();
        } catch (RestClientResponseException e) {
            // 4xx/5xx from NewsAPI (bad key, rate limit, ...). The message is shown to users, so it is written here
            // rather than taken from the exception, which contains NewsAPI's raw response body.
            log.warn("NewsAPI returned HTTP {}", e.getStatusCode().value());
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "NewsAPI returned HTTP " + e.getStatusCode().value());
        } catch (RestClientException e) {
            // I/O failure reaching NewsAPI, including a connect or read timeout.
            log.warn("Could not reach NewsAPI: {}", e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not reach NewsAPI");
        }
    }

    private static Article toArticle(NewsApiArticle article) {
        Article.Source source = article.source() == null
                ? null
                : new Article.Source(article.source().id(), article.source().name());
        return new Article(
                source,
                article.author(),
                article.title(),
                article.description(),
                article.url(),
                article.urlToImage(),
                article.publishedAt(),
                article.content(),
                NAME,
                null);
    }
}
