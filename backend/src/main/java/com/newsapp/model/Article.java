package com.newsapp.model;

/**
 * One article in the shape the frontend consumes, whichever provider it came from. The field names match what
 * NewsAPI sends so the frontend didn't have to change. Any field except title/url may be null.
 *
 * @param content  a short preview of the text (NewsAPI's truncated content); always null for the Guardian, whose
 *                 article bodies are not fetched
 * @param provider display name of the API that returned it, e.g. "NewsAPI" or "The Guardian"
 * @param section  the provider's own section label (e.g. "Technology"); only the Guardian sets it
 */
public record Article(
        Source source,
        String author,
        String title,
        String description,
        String url,
        String urlToImage,
        String publishedAt,
        String content,
        String provider,
        String section) {

    /** {@code id} is null for some sources (e.g. Fox Business, NPR); fall back to {@code name}. */
    public record Source(String id, String name) {}
}
