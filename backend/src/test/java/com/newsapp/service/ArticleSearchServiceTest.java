package com.newsapp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.newsapp.model.Article;
import com.newsapp.model.ProviderWarning;
import com.newsapp.model.SearchCriteria;
import com.newsapp.model.SearchResult;
import java.time.Duration;
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

    private static List<String> urls(SearchResult result) {
        return result.articles().stream().map(Article::url).toList();
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

        SearchResult result = new ArticleSearchService(List.of(a, b)).search(ANY);

        assertEquals(List.of("u2", "dup", "u1", "undated"), urls(result));
        assertEquals("A", result.articles().get(1).provider(), "first provider's copy of a duplicate wins");
        assertTrue(result.warnings().isEmpty(), "nothing failed, so nothing to warn about");
    }

    @Test
    void oneProviderFailingStillReturnsTheOthersWithAWarning() {
        StubProvider ok = new StubProvider("ok", List.of(article("u1", "2026-09-19T10:00:00Z", "ok")));
        StubProvider broken = new StubProvider(
                "NewsAPI", new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Daily NewsAPI request budget reached."));

        SearchResult result = new ArticleSearchService(List.of(broken, ok)).search(ANY);

        assertEquals(List.of("u1"), urls(result));
        assertEquals(
                List.of(new ProviderWarning("NewsAPI", "Daily NewsAPI request budget reached.")), result.warnings());
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

        SearchResult result = new ArticleSearchService(List.of(buggy, ok)).search(ANY);

        assertEquals(1, result.articles().size());
        assertEquals(List.of(new ProviderWarning("buggy", "buggy failed")), result.warnings());
    }

    @Test
    void aProviderThatNeverAnswersIsGivenUpOnAndReportedAsAWarning() {
        StubProvider ok = new StubProvider("ok", List.of(article("u1", "2026-09-19T10:00:00Z", "ok")));
        StubProvider hung = new StubProvider("hung", List.of(article("never", null, "hung")));
        hung.delayMillis = 5_000;
        ArticleSearchService service = new ArticleSearchService(List.of(hung, ok), Duration.ofMillis(200));

        long started = System.nanoTime();
        SearchResult result = service.search(ANY);
        long tookMillis = (System.nanoTime() - started) / 1_000_000;

        assertTrue(tookMillis < 3_000, "waited " + tookMillis + " ms for a provider that had a 200 ms timeout");
        assertEquals(List.of("u1"), urls(result));
        assertEquals(List.of(new ProviderWarning("hung", "hung took too long to respond")), result.warnings());
    }

    @Test
    void providersThatNarrowAwayAreNotCalled() {
        StubProvider skipped = new StubProvider("skipped", List.of(article("x", null, "skipped")));
        skipped.skip = true;
        StubProvider used = new StubProvider("used", List.of(article("u1", "2026-09-19T10:00:00Z", "used")));

        SearchResult result = new ArticleSearchService(List.of(skipped, used)).search(ANY);

        assertEquals(0, skipped.calls.get());
        assertEquals(1, used.calls.get());
        assertEquals(1, result.articles().size());
        assertTrue(result.warnings().isEmpty(), "a provider that was never asked can't have failed");
    }

    @Test
    void noProviderAbleToAnswerMeansNoArticlesNotAnError() {
        StubProvider skipped = new StubProvider("skipped", List.of());
        skipped.skip = true;

        SearchResult result = new ArticleSearchService(List.of(skipped)).search(ANY);

        assertTrue(result.articles().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }

    static final class StubProvider implements NewsProvider {
        final String name;
        final List<Article> articles;
        final RuntimeException failure;
        final AtomicInteger calls = new AtomicInteger();
        boolean skip;
        long delayMillis;

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
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (failure != null) {
                throw failure;
            }
            return articles;
        }
    }
}
