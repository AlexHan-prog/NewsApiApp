package com.newsapp.service;

import com.newsapp.model.NewsApiArticle;
import com.newsapp.model.NewsApiResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NewsApiSearchService {

    private static final Logger log = LoggerFactory.getLogger(NewsApiSearchService.class);

    private final RestClient newsApiRestClient;
    private final DailyCallBudget dailyCallBudget;

    public NewsApiSearchService(RestClient newsApiRestClient, DailyCallBudget dailyCallBudget) {
        this.newsApiRestClient = newsApiRestClient;
        this.dailyCallBudget = dailyCallBudget;
    }

    /**
     * The body only runs on a cache miss, so cache hits never touch the daily budget. The key keeps the original
     * case because NewsAPI only treats upper-case AND/OR/NOT as operators, for example 'cats and dogs' is a different
     * search to cats AND dogs. 
     * {@code sync} makes concurrent identical searches share one call; exceptions are not cached.
     * The cache is set up as TinyLFU, meaning the LFU entry is evicted first if cache is full
     * <p>
     * The cache key is "keyword|domains", so the same keyword filtered to different outlets is a separate entry.
     * {@code domains} must already be normalized (lower-case, sorted, comma-separated, "" for none) by the caller,
     * because the key is evaluated from the raw arguments before this body runs. {@code keyword} may be empty when
     * domains are given.
     */
    @Cacheable(cacheNames = "articles", key = "#keyword.strip() + '|' + #domains", sync = true)
    public List<NewsApiArticle> searchEverything(String keyword, String domains) {
        if (!dailyCallBudget.tryConsume()) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS, "Daily NewsAPI request budget reached. It resets at 00:00 UTC.");
        }
        log.info("NewsAPI call for \"{}\" domains=\"{}\"", keyword, domains);
        try {
            NewsApiResponse response = newsApiRestClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/everything");
                        if (!keyword.isBlank()) {
                            uriBuilder.queryParam("q", keyword);
                        }
                        if (!domains.isEmpty()) {
                            uriBuilder.queryParam("domains", domains);
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(NewsApiResponse.class);

            return response == null || response.articles() == null ? List.of() : response.articles();
        } catch (RestClientException e) {
            // 4xx/5xx from NewsAPI (bad key, rate limit, ...) or an I/O failure reaching it.
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        }
    }
}
