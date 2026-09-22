package com.newsapp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.newsapp.model.Article;
import com.newsapp.model.Leaning;
import com.newsapp.model.SearchCriteria;
import com.newsapp.model.SearchResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class LeaningDemoServiceTest {

    private GuardianProvider guardianProvider;
    private PoliticalLeaningService politicalLeaning;

    @BeforeEach
    void setUp() {
        guardianProvider = mock(GuardianProvider.class);
        politicalLeaning = mock(PoliticalLeaningService.class);
    }

    private LeaningDemoService serviceWith(int count, String sections) {
        return new LeaningDemoService(guardianProvider, politicalLeaning, count, sections);
    }

    private static Article article(String title) {
        return new Article(
                new Article.Source("the-guardian", "The Guardian"),
                null,
                title,
                null,
                "https://theguardian.com/politics/" + title.hashCode(),
                null,
                "2026-09-22T10:00:00Z",
                null,
                "The Guardian",
                "Politics",
                null,
                "Full body of " + title);
    }

    @Test
    void asksTheGuardianForTheConfiguredSectionsAndTakesOnlyTheFirstCount() {
        when(guardianProvider.search(any()))
                .thenReturn(List.of(article("A"), article("B"), article("C")));
        when(politicalLeaning.classify(any())).thenReturn(new Leaning(Leaning.Label.CENTER, 0.5, "x", List.of()));
        LeaningDemoService service = serviceWith(2, "politics");

        SearchResult result = service.demo();

        assertEquals(2, result.articles().size());
        assertEquals("A", result.articles().get(0).title());
        assertEquals("B", result.articles().get(1).title());
        verify(politicalLeaning, times(2)).classify(any());

        org.mockito.ArgumentCaptor<SearchCriteria> captor = org.mockito.ArgumentCaptor.forClass(SearchCriteria.class);
        verify(guardianProvider).search(captor.capture());
        assertEquals(List.of("politics"), List.copyOf(captor.getValue().sections()));
        assertTrue(captor.getValue().domains().isEmpty());
        assertTrue(captor.getValue().keyword().isEmpty());
    }

    @Test
    void supportsMultipleConfiguredSections() {
        when(guardianProvider.search(any())).thenReturn(List.of());
        LeaningDemoService service = serviceWith(5, "politics,us-news");

        service.demo();

        org.mockito.ArgumentCaptor<SearchCriteria> captor = org.mockito.ArgumentCaptor.forClass(SearchCriteria.class);
        verify(guardianProvider).search(captor.capture());
        assertEquals(List.of("politics", "us-news"), List.copyOf(captor.getValue().sections()));
    }

    @Test
    void everyArticleGetsClassifiedAndAttached() {
        when(guardianProvider.search(any())).thenReturn(List.of(article("A"), article("B")));
        Leaning left = new Leaning(Leaning.Label.LEFT, 0.9, "x", List.of());
        Leaning right = new Leaning(Leaning.Label.RIGHT, 0.8, "y", List.of());
        when(politicalLeaning.classify(article("A"))).thenReturn(left);
        when(politicalLeaning.classify(article("B"))).thenReturn(right);
        LeaningDemoService service = serviceWith(5, "politics");

        SearchResult result = service.demo();

        assertEquals(left, result.articles().get(0).leaning());
        assertEquals(right, result.articles().get(1).leaning());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void oneArticleFailingDoesNotStopTheOthersAndProducesOneWarning() {
        when(guardianProvider.search(any()))
                .thenReturn(List.of(article("A"), article("B"), article("C")));
        Leaning ok = new Leaning(Leaning.Label.CENTER, 0.5, "x", List.of());
        when(politicalLeaning.classify(article("A"))).thenReturn(ok);
        when(politicalLeaning.classify(article("B")))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not reach the leaning service"));
        when(politicalLeaning.classify(article("C"))).thenReturn(ok);
        LeaningDemoService service = serviceWith(5, "politics");

        SearchResult result = service.demo();

        assertEquals(3, result.articles().size(), "all three articles are still returned");
        assertEquals(ok, result.articles().get(0).leaning());
        assertNull(result.articles().get(1).leaning());
        assertEquals(ok, result.articles().get(2).leaning(), "one failure doesn't stop the rest");
        assertEquals(1, result.warnings().size());
        assertEquals("Political leaning", result.warnings().get(0).provider());
        assertTrue(result.warnings().get(0).message().contains("1 of 3"));
        verify(politicalLeaning, times(3)).classify(any());
    }

    @Test
    void aGuardianFailureIsNotHiddenBecauseThereIsNothingToShow() {
        when(guardianProvider.search(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Daily Guardian request budget reached."));
        LeaningDemoService service = serviceWith(5, "politics");

        org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException.class, service::demo);

        verify(politicalLeaning, never()).classify(any());
    }
}
