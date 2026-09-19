package com.newsapp.model;

/** One article exactly as NewsAPI sends it. Any field except title/url may be null. */
public record NewsApiArticle(
        NewsApiSource source,
        String author,
        String title,
        String description,
        String url,
        String urlToImage,
        String publishedAt,
        String content) {

    /** {@code id} is null for some sources (e.g. Fox Business, NPR); fall back to {@code name}. */
    public record NewsApiSource(String id, String name) {}
}
