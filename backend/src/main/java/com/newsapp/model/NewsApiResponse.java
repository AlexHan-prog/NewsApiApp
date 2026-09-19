package com.newsapp.model;

import java.util.List;

/** Envelope returned by NewsAPI's /v2/everything and /v2/top-headlines. */
public record NewsApiResponse(String status, int totalResults, List<NewsApiArticle> articles) {}
