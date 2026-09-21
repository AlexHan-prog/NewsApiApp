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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
        mvc = MockMvcBuilders.standaloneSetup(new NewsController(new ArticleSearchService(List.of(recorder))))
                .build();
    }

    @Test
    void normalizesFiltersAndReturnsArticleJson() throws Exception {
        mvc.perform(get("/api/articles")
                        .param("keyword", "  AI  ")
                        .param("domains", "B.com, a.com,")
                        .param("sections", "Technology,sport,technology")
                        .param("tags", "Technology/Apple"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("T"))
                .andExpect(jsonPath("$[0].source.name").value("Src"))
                .andExpect(jsonPath("$[0].provider").value("recorder"))
                .andExpect(jsonPath("$[0].section").value("Technology"));

        SearchCriteria criteria = requested.get();
        assertNotNull(criteria);
        assertEquals("AI", criteria.keyword());
        assertEquals(List.of("a.com", "b.com"), List.copyOf(criteria.domains()));
        assertEquals(List.of("sport", "technology"), List.copyOf(criteria.sections()));
        assertEquals(List.of("technology/apple"), List.copyOf(criteria.tags()));
    }

    @Test
    void aSectionAloneIsAValidSearch() throws Exception {
        mvc.perform(get("/api/articles").param("sections", "technology")).andExpect(status().isOk());
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
