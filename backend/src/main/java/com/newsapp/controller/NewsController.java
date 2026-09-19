package com.newsapp.controller;

import com.newsapp.model.NewsApiArticle;
import com.newsapp.service.NewsApiSearchService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class NewsController {

    private final NewsApiSearchService newsApiSearchService;

    public NewsController(NewsApiSearchService newsApiSearchService) {
        this.newsApiSearchService = newsApiSearchService;
    }

    @GetMapping("/articles")
    public List<NewsApiArticle> search(@RequestParam String keyword) {
        if (keyword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "keyword must not be blank");
        }
        return newsApiSearchService.searchEverything(keyword);
    }
}
