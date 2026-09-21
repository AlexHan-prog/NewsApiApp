package com.newsapp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.newsapp.model.Article;
import com.newsapp.model.SearchCriteria;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ArticleSearchServiceTest {

    private static final SearchCriteria ANY =
            new SearchCriteria("k", new TreeSet<>(), new TreeSet<>(), new TreeSet<>());

    private static Article article(String url, String publishedAt, String provider) {
        return new Article(null, null, "title " + url, null, url, null, publishedAt, null, provider, null);
    }

    @Test
    void mergesSortsNewestFirstAndDropsDuplicateUrls() {
        StubProvider a = new StubProvider(
                "A",
                List.of(
                        article("u1", "2026-09-19T10:00:00Z", "A"),
                        article("dup", "2026-09-20T10:00:00Z", "A"),
                        article("undated", null, "A")));
        StubProvider b = new StubProvider(
                "B",
                List.of(
                        article("dup", "2026-09-20T10:00:00Z", "B"),
                        article("u2", "2026-09-21T09:00:00+00:00", "B")));

        List<Article> result = new ArticleSearchService(List.of(a, b)).search(ANY);

        assertEquals(List.of("u2", "dup", "u1", "undated"), result.stream().map(Article::url).toList());
        assertEquals("A", result.get(1).provider(), "first provider's copy of a duplicate wins");
    }

    @Test
    void oneProviderFailingStillReturnsTheOthers() {
        StubProvider ok = new StubProvider("ok", List.of(article("u1", "2026-09-19T10:00:00Z", "ok")));
        StubProvider broken = new StubProvider("broken", new ResponseStatusException(HttpStatus.BAD_GATEWAY, "down"));

        List<Article> result = new ArticleSearchService(List.of(broken, ok)).search(ANY);

        assertEquals(List.of("u1"), result.stream().map(Article::url).toList());
    }

    @Test
    void failsWhenEveryProviderThatWasAskedFails() {
        ResponseStatusException first = new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "budget");
        StubProvider a = new StubProvider("A", first);
        StubProvider b = new StubProvider("B", new ResponseStatusException(HttpStatus.BAD_GATEWAY, "down"));

        ResponseStatusException thrown = assertThrows(
                ResponseStatusException.class, () -> new ArticleSearchService(List.of(a, b)).search(ANY));

        assertSame(first, thrown);
    }

    @Test
    void anUnexpectedExceptionCountsAsAFailureNotACrash() {
        StubProvider ok = new StubProvider("ok", List.of(article("u1", "2026-09-19T10:00:00Z", "ok")));
        StubProvider buggy = new StubProvider("buggy", new IllegalStateException("bug"));

        List<Article> result = new ArticleSearchService(List.of(buggy, ok)).search(ANY);

        assertEquals(1, result.size());
    }

    @Test
    void providersThatNarrowAwayAreNotCalled() {
        StubProvider skipped = new StubProvider("skipped", List.of(article("x", null, "skipped")));
        skipped.skip = true;
        StubProvider used = new StubProvider("used", List.of(article("u1", "2026-09-19T10:00:00Z", "used")));

        List<Article> result = new ArticleSearchService(List.of(skipped, used)).search(ANY);

        assertEquals(0, skipped.calls.get());
        assertEquals(1, used.calls.get());
        assertEquals(1, result.size());
    }

    @Test
    void noProviderAbleToAnswerMeansNoArticlesNotAnError() {
        StubProvider skipped = new StubProvider("skipped", List.of());
        skipped.skip = true;

        assertTrue(new ArticleSearchService(List.of(skipped)).search(ANY).isEmpty());
    }

    static final class StubProvider implements NewsProvider {
        final String name;
        final List<Article> articles;
        final RuntimeException failure;
        final AtomicInteger calls = new AtomicInteger();
        boolean skip;

        StubProvider(String name, List<Article> articles) {
            this.name = name;
            this.articles = articles;
            this.failure = null;
        }

        StubProvider(String name, RuntimeException failure) {
            this.name = name;
            this.articles = List.of();
            this.failure = failure;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Optional<SearchCriteria> narrow(SearchCriteria requested) {
            return skip ? Optional.empty() : Optional.of(requested);
        }

        @Override
        public List<Article> search(SearchCriteria criteria) {
            calls.incrementAndGet();
            if (failure != null) {
                throw failure;
            }
            return articles;
        }
    }
}
