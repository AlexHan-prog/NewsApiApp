package com.newsapp.model;

import java.util.List;

/**
 * Envelope returned by the Guardian's /search. Unlike NewsAPI everything is nested under {@code response}. Only the
 * fields we map are declared; {@code fields} and {@code tags} are present only if requested via show-fields/show-tags.
 * The article body is deliberately not requested: it is large and nothing here uses it.
 */
public record GuardianResponse(Response response) {

    public record Response(String status, int total, List<Result> results) {}

    public record Result(
            String id,
            String sectionId,
            String sectionName,
            String webPublicationDate,
            String webTitle,
            String webUrl,
            Fields fields,
            List<Tag> tags) {}

    /** Everything here is a string; {@code trailText} is HTML. */
    public record Fields(String trailText, String byline, String thumbnail) {}

    public record Tag(String id, String type, String webTitle) {}
}
