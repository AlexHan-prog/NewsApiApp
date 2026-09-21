package com.newsapp.service;

import com.newsapp.model.Article;
import com.newsapp.model.SearchCriteria;
import java.util.List;
import java.util.Optional;

/** One upstream news API. {@link ArticleSearchService} fans a search out to every provider that can answer it. */
public interface NewsProvider {

    /** Display name, also put on each article as {@link Article#provider()}. */
    String name();

    /**
     * The search this provider should run for {@code requested}, or empty if it can't honour every filter the user
     * set (running it anyway would return articles that break the filter). The returned criteria carries only the
     * fields this provider uses, so {@link SearchCriteria#cacheKey()} doesn't split on irrelevant ones.
     */
    Optional<SearchCriteria> narrow(SearchCriteria requested);

    /**
     * Runs a search built by {@link #narrow}. Failures (budget used up, upstream error) are thrown as
     * {@link org.springframework.web.server.ResponseStatusException} with a message that is safe to show to users.
     */
    List<Article> search(SearchCriteria criteria);
}
