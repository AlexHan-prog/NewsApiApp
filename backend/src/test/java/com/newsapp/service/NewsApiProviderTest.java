package com.newsapp.service;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.newsapp.model.Article;
import com.newsapp.model.SearchCriteria;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class NewsApiProviderTest {

    private static final String ONE_ARTICLE =
            """
            {"status":"ok","totalResults":1,"articles":[
              {"source":{"id":null,"name":"NPR"},"author":null,"title":"A title","description":null,
               "url":"https://npr.org/a","urlToImage":null,"publishedAt":"2026-09-19T11:01:34Z","content":null}
            ]}
            """;

    private MockRestServiceServer server;

    private NewsApiProvider providerWithBudget(int budget) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://newsapi.org/v2");
        server = MockRestServiceServer.bindTo(builder).build();
        return new NewsApiProvider(builder.build(), new DailyCallBudget(budget));
    }

    private static SearchCriteria criteria(String keyword, List<String> domains, List<String> sections) {
        return new SearchCriteria(keyword, new TreeSet<>(domains), new TreeSet<>(sections), new TreeSet<>());
    }

    @Test
    void mapsArticlesAndTagsThemWithTheProvider() {
        NewsApiProvider provider = providerWithBudget(5);
        server.expect(requestTo(startsWith("https://newsapi.org/v2/everything?")))
                .andExpect(requestTo(containsString("q=cats")))
                .andExpect(requestTo(containsString("domains=bbc.co.uk,npr.org")))
                .andRespond(withSuccess(ONE_ARTICLE, MediaType.APPLICATION_JSON));

        List<Article> articles = provider.search(criteria("cats", List.of("npr.org", "bbc.co.uk"), List.of()));

        assertEquals(1, articles.size());
        Article article = articles.get(0);
        assertEquals("A title", article.title());
        assertEquals("NPR", article.source().name());
        assertNull(article.source().id());
        assertNull(article.author());
        assertEquals("NewsAPI", article.provider());
        assertNull(article.section());
        server.verify();
    }

    @Test
    void omitsQueryParamsThatAreNotSet() {
        NewsApiProvider provider = providerWithBudget(5);
        server.expect(requestTo(not(containsString("q="))))
                .andExpect(requestTo(containsString("domains=npr.org")))
                .andRespond(withSuccess(ONE_ARTICLE, MediaType.APPLICATION_JSON));

        provider.search(criteria("", List.of("npr.org"), List.of()));

        server.verify();
    }

    @Test
    void refusesWhenTheBudgetIsUsedUp() {
        NewsApiProvider provider = providerWithBudget(0);

        ResponseStatusException e = assertThrows(
                ResponseStatusException.class, () -> provider.search(criteria("cats", List.of(), List.of())));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.getStatusCode());
    }

    @Test
    void narrowIsSkippedForSectionAndTagSearches() {
        NewsApiProvider provider = providerWithBudget(5);

        assertTrue(provider.narrow(criteria("k", List.of(), List.of("sport"))).isEmpty());
        SearchCriteria withTag =
                new SearchCriteria("k", new TreeSet<>(), new TreeSet<>(), new TreeSet<>(List.of("a/b")));
        assertTrue(provider.narrow(withTag).isEmpty());
    }

    @Test
    void narrowLeavesTheGuardianDomainToTheGuardianProvider() {
        NewsApiProvider provider = providerWithBudget(5);

        Optional<SearchCriteria> mixed =
                provider.narrow(criteria("k", List.of("bbc.co.uk", "theguardian.com"), List.of()));
        assertTrue(mixed.isPresent());
        assertEquals(List.of("bbc.co.uk"), List.copyOf(mixed.get().domains()));

        assertTrue(
                provider.narrow(criteria("k", List.of("theguardian.com"), List.of())).isEmpty(),
                "nothing left for NewsAPI once the Guardian's domain is removed");
    }

    @Test
    void narrowRunsForPlainKeywordAndOutletSearches() {
        NewsApiProvider provider = providerWithBudget(5);

        assertTrue(provider.narrow(criteria("k", List.of(), List.of())).isPresent());
        assertTrue(provider.narrow(criteria("", List.of("bbc.co.uk"), List.of())).isPresent());
    }
}
