package com.newsapp.service;

import com.newsapp.model.NewsApiArticle;
import com.newsapp.model.NewsApiResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NewsApiSearchService {

    private final RestClient newsApiRestClient;

    public NewsApiSearchService(RestClient newsApiRestClient) {
        this.newsApiRestClient = newsApiRestClient;
    }

    public List<NewsApiArticle> searchEverything(String keyword) {
        try {
            NewsApiResponse response = newsApiRestClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/everything").queryParam("q", keyword).build())
                    .retrieve()
                    .body(NewsApiResponse.class);

            return response == null || response.articles() == null ? List.of() : response.articles();
        } catch (RestClientException e) {
            // 4xx/5xx from NewsAPI (bad key, rate limit, ...) or an I/O failure reaching it.
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        }
    }
}
