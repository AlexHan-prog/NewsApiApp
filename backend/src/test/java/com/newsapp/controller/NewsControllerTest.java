package com.newsapp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.newsapp.model.Article;
import com.newsapp.model.SearchCriteria;
import com.newsapp.service.ArticleSearchService;
import com.newsapp.service.NewsProvider;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class NewsControllerTest {

    private final AtomicReference<SearchCriteria> requested = new AtomicReference<>();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        NewsProvider recorder = new NewsProvider() {
            @Override
            public String name() {
                return "recorder";
            }

            @Override
            public Optional<SearchCriteria> narrow(SearchCriteria criteria) {
                requested.set(criteria);
                return Optional.of(criteria);
            }

            @Override
            public List<Article> search(SearchCriteria criteria) {
                return List.of(new Article(
                        new Article.Source(null, "Src"),
                        null,
                        "T",
                        null,
                        "https://x.test/1",
                        null,
                        null,
                        null,
                        "recorder",
                        "Technology"));
            }
        };
        // Always fails, so every response also carries a warning.
        NewsProvider flaky = new NewsProvider() {
            @Override
            public String name() {
                return "flaky";
            }

            @Override
            public Optional<SearchCriteria> narrow(SearchCriteria criteria) {
                return Optional.of(criteria);
            }

            @Override
            public List<Article> search(SearchCriteria criteria) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "flaky is having a bad day");
            }
        };
        mvc = MockMvcBuilders.standaloneSetup(
                        new NewsController(new ArticleSearchService(List.of(recorder, flaky))))
                .build();
    }

    @Test
    void normalizesFiltersAndReturnsArticleJson() throws Exception {
        mvc.perform(get("/api/articles").param("keyword", "  AI  ").param("domains", "B.com, a.com,"))
                .andExpect(status().isOk());

        SearchCriteria outlets = requested.get();
        assertNotNull(outlets);
        assertEquals("AI", outlets.keyword());
        assertEquals(List.of("a.com", "b.com"), List.copyOf(outlets.domains()));

        mvc.perform(get("/api/articles")
                        .param("domains", "TheGuardian.com")
                        .param("sections", "Technology,sport,technology")
                        .param("tags", "Technology/Apple"))
                .andExpect(status().isOk());

        SearchCriteria guardian = requested.get();
        assertEquals(List.of("theguardian.com"), List.copyOf(guardian.domains()));
        assertEquals(List.of("sport", "technology"), List.copyOf(guardian.sections()));
        assertEquals(List.of("technology/apple"), List.copyOf(guardian.tags()));
    }

    @Test
    void returnsArticlesAndWarningsInOneEnvelope() throws Exception {
        mvc.perform(get("/api/articles").param("keyword", "AI"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articles[0].title").value("T"))
                .andExpect(jsonPath("$.articles[0].source.name").value("Src"))
                .andExpect(jsonPath("$.articles[0].provider").value("recorder"))
                .andExpect(jsonPath("$.articles[0].section").value("Technology"))
                .andExpect(jsonPath("$.warnings[0].provider").value("flaky"))
                .andExpect(jsonPath("$.warnings[0].message").value("flaky is having a bad day"));
    }

    @Test
    void aSectionAloneIsAValidSearch() throws Exception {
        mvc.perform(get("/api/articles").param("sections", "technology")).andExpect(status().isOk());
    }

    @Test
    void sectionsAndTagsMayBeCombinedWithTheGuardianOutlet() throws Exception {
        mvc.perform(get("/api/articles").param("sections", "technology").param("domains", "theguardian.com"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/articles").param("tags", "technology/apple").param("domains", "TheGuardian.com"))
                .andExpect(status().isOk());
    }

    @Test
    void sectionsAndTagsCannotBeCombinedWithOtherOutlets() throws Exception {
        mvc.perform(get("/api/articles").param("sections", "technology").param("domains", "bbc.co.uk"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/articles").param("sections", "technology").param("domains", "theguardian.com,bbc.co.uk"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/articles").param("tags", "technology/apple").param("domains", "bbc.co.uk"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void otherOutletsAreFineWithoutSectionsOrTags() throws Exception {
        mvc.perform(get("/api/articles").param("keyword", "x").param("domains", "bbc.co.uk,npr.org"))
                .andExpect(status().isOk());
    }

    @Test
    void anEmptySearchIsRejected() throws Exception {
        mvc.perform(get("/api/articles")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/articles").param("keyword", "   ").param("sections", " , "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedFiltersAreRejected() throws Exception {
        mvc.perform(get("/api/articles").param("sections", "Tech Stuff!")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/articles").param("tags", "a//b")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/articles").param("domains", "not a domain")).andExpect(status().isBadRequest());
    }
}
