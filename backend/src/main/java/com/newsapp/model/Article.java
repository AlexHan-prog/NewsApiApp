package com.newsapp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * One article in the shape the frontend consumes, whichever provider it came from. The field names match what
 * NewsAPI sends so the frontend didn't have to change. Any field except title/url may be null.
 *
 * @param content  a short preview of the text (NewsAPI's truncated content); always null for the Guardian, whose
 *                 article bodies aren't shown in the UI (see {@code fullText})
 * @param provider display name of the API that returned it, e.g. "NewsAPI" or "The Guardian"
 * @param section  the provider's own section label (e.g. "Technology"); only the Guardian sets it
 * @param leaning  Claude's political-leaning assessment; only ever set on the fixed set of articles the leaning demo
 *                 classifies (see {@code LeaningDemoService}), never as a side effect of a normal search
 * @param fullText the Guardian's full article body as plain text, or null (NewsAPI, or a Guardian content type with
 *                 no body, e.g. a picture gallery). {@code @JsonIgnore}d: this exists only so the leaning demo can
 *                 read the whole article, and must never reach the browser - the frontend payload stays small
 *                 regardless of how long an article is.
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
        String section,
        Leaning leaning,
        @JsonIgnore String fullText) {

    /** An article that has not been classified and has no full text (NewsAPI, or a Guardian search that skips it). */
    public Article(
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
        this(source, author, title, description, url, urlToImage, publishedAt, content, provider, section, null, null);
    }

    public Article withLeaning(Leaning leaning) {
        return new Article(
                source, author, title, description, url, urlToImage, publishedAt, content, provider, section,
                leaning, fullText);
    }

    /** {@code id} is null for some sources (e.g. Fox Business, NPR); fall back to {@code name}. */
    public record Source(String id, String name) {}
}
