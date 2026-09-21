package com.newsapp.model;

import java.util.List;

/** What {@code /api/articles} returns: the merged articles, plus a warning for each provider that failed. */
public record SearchResult(List<Article> articles, List<ProviderWarning> warnings) {}
