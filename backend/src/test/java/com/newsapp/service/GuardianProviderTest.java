package com.newsapp.service;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.newsapp.model.Article;
import com.newsapp.model.SearchCriteria;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class GuardianProviderTest {

    private static final String KEY = "secret-test-key";
    private static final String SEARCH = "https://content.guardianapis.com/search";

    private static final String TWO_RESULTS =
            """
            {"response":{"status":"ok","total":2,"results":[
              {"id":"technology/2026/sep/20/ai","sectionId":"technology","sectionName":"Technology",
               "webPublicationDate":"2026-09-20T10:00:00Z","webTitle":"AI news",
               "webUrl":"https://www.theguardian.com/technology/2026/sep/20/ai",
               "fields":{"trailText":"A <strong>big</strong> story &amp; more","byline":"Jane Doe",
                         "thumbnail":"https://media.guim.co.uk/x.jpg","body":"<p>Full body paragraph one.</p><p>Paragraph two.</p>"},
               "tags":[]},
              {"id":"sport/2026/sep/19/x","sectionId":"sport","sectionName":"Sport",
               "webPublicationDate":"2026-09-19T08:00:00Z","webTitle":"Sport story",
               "webUrl":"https://www.theguardian.com/sport/x",
               "fields":{"trailText":""},
               "tags":[{"id":"a","type":"keyword","webTitle":"Kw"},
                       {"id":"profile/joe","type":"contributor","webTitle":"Joe Bloggs"}]}
            ]}}
            """;

    private MockRestServiceServer server;

    private GuardianProvider providerWithBudget(int budget) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://content.guardianapis.com");
        server = MockRestServiceServer.bindTo(builder).build();
        return new GuardianProvider(builder.build(), new DailyCallBudget(budget), KEY);
    }

    private static SearchCriteria criteria(
            String keyword, List<String> domains, List<String> sections, List<String> tags) {
        return new SearchCriteria(keyword, new TreeSet<>(domains), new TreeSet<>(sections), new TreeSet<>(tags));
    }

    private static SearchCriteria plain(String keyword) {
        return criteria(keyword, List.of(), List.of(), List.of());
    }

    @Test
    void mapsResultsIntoArticles() {
        GuardianProvider provider = providerWithBudget(5);
        server.expect(requestTo(startsWith(SEARCH + "?")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(TWO_RESULTS, MediaType.APPLICATION_JSON));

        List<Article> articles = provider.search(plain("ai"));

        assertEquals(2, articles.size());
        Article first = articles.get(0);
        assertEquals("AI news", first.title());
        assertEquals("https://www.theguardian.com/technology/2026/sep/20/ai", first.url());
        assertEquals("2026-09-20T10:00:00Z", first.publishedAt());
        assertEquals("Jane Doe", first.author());
        assertEquals("A big story & more", first.description());
        assertEquals("https://media.guim.co.uk/x.jpg", first.urlToImage());
        assertNull(first.content(), "the body isn't shown in the UI as an article preview");
        assertEquals("Full body paragraph one.\nParagraph two.", first.fullText(), "but it is read for the leaning demo");
        assertEquals("Technology", first.section());
        assertEquals("The Guardian", first.provider());
        assertEquals("The Guardian", first.source().name());

        Article second = articles.get(1);
        assertEquals("Joe Bloggs", second.author(), "no byline, so the contributor tag is used");
        assertNull(second.description(), "blank trailText becomes null");
        assertNull(second.urlToImage());
        assertNull(second.content());
        assertNull(second.fullText(), "no body and a blank summary leaves fullText null too");
        server.verify();
    }

    @Test
    void sendsKeyFieldsAndFiltersEncoded() {
        GuardianProvider provider = providerWithBudget(5);
        server.expect(requestTo(containsString("api-key=" + KEY)))
                .andExpect(requestTo(containsString("show-fields=trailText,byline,thumbnail,body")))
                .andExpect(requestTo(containsString("page-size=50")))
                .andExpect(requestTo(containsString("q=a%2Bb%20%26%20c")))
                .andExpect(requestTo(containsString("section=business%7Ctechnology")))
                .andExpect(requestTo(containsString("tag=technology%2Fapple")))
                .andRespond(withSuccess(TWO_RESULTS, MediaType.APPLICATION_JSON));

        provider.search(criteria("a+b & c", List.of(), List.of("technology", "business"), List.of("technology/apple")));

        server.verify();
    }

    @Test
    void leavesOutFiltersThatAreNotSet() {
        GuardianProvider provider = providerWithBudget(5);
        server.expect(requestTo(not(containsString("q="))))
                .andExpect(requestTo(not(containsString("section="))))
                .andExpect(requestTo(not(containsString("tag="))))
                .andRespond(withSuccess(TWO_RESULTS, MediaType.APPLICATION_JSON));

        provider.search(plain(""));

        server.verify();
    }

    @Test
    void httpErrorBecomesBadGatewayWithoutTheKey() {
        GuardianProvider provider = providerWithBudget(5);
        server.expect(requestTo(startsWith(SEARCH))).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> provider.search(plain("x")));

        assertEquals(HttpStatus.BAD_GATEWAY, e.getStatusCode());
        assertEquals("The Guardian returned HTTP 401", e.getReason());
    }

    @Test
    void ioErrorMessageDoesNotLeakTheKey() {
        GuardianProvider provider = providerWithBudget(5);
        server.expect(requestTo(startsWith(SEARCH))).andRespond(withException(new IOException("connect failed")));

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> provider.search(plain("x")));

        assertEquals(HttpStatus.BAD_GATEWAY, e.getStatusCode());
        assertFalse(e.getReason().contains(KEY));
        assertFalse(e.getMessage().contains(KEY));
    }

    @Test
    void nonOkStatusInBodyIsAnError() {
        GuardianProvider provider = providerWithBudget(5);
        server.expect(requestTo(startsWith(SEARCH)))
                .andRespond(withSuccess(
                        "{\"response\":{\"status\":\"error\",\"message\":\"nope\"}}", MediaType.APPLICATION_JSON));

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> provider.search(plain("x")));

        assertEquals(HttpStatus.BAD_GATEWAY, e.getStatusCode());
    }

    @Test
    void refusesWithoutCallingOutWhenTheBudgetIsUsedUp() {
        GuardianProvider noBudget = providerWithBudget(0);

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> noBudget.search(plain("x")));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.getStatusCode());
        server.verify(); // no request was expected, none was made
    }

    @Test
    void narrowKeepsSectionsAndTagsAndDropsDomains() {
        GuardianProvider provider = providerWithBudget(5);

        Optional<SearchCriteria> narrowed = provider.narrow(
                criteria("k", List.of("theguardian.com"), List.of("sport"), List.of("sport/football")));

        assertTrue(narrowed.isPresent());
        assertEquals("k", narrowed.get().keyword());
        assertTrue(narrowed.get().domains().isEmpty());
        assertEquals(List.of("sport"), List.copyOf(narrowed.get().sections()));
        assertEquals(List.of("sport/football"), List.copyOf(narrowed.get().tags()));
    }

    @Test
    void narrowIsSkippedWhenOnlyOtherOutletsWereAskedFor() {
        GuardianProvider provider = providerWithBudget(5);

        assertTrue(provider.narrow(criteria("k", List.of("bbc.co.uk"), List.of(), List.of())).isEmpty());
        assertTrue(provider.narrow(criteria("k", List.of("bbc.co.uk"), List.of("sport"), List.of())).isEmpty());
    }

    @Test
    void narrowRunsWhenNoOutletWasChosenOrTheGuardianIsAmongThem() {
        GuardianProvider provider = providerWithBudget(5);

        assertTrue(provider.narrow(plain("k")).isPresent());
        assertTrue(provider
                .narrow(criteria("k", List.of("bbc.co.uk", "theguardian.com"), List.of(), List.of()))
                .isPresent());
    }
}
