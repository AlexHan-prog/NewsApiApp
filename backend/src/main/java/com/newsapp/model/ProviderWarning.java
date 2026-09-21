package com.newsapp.model;

/**
 * A provider that was asked to search but failed while others succeeded, so the user only sees part of the picture.
 * {@code message} is safe to show to users (it never contains an API key or a raw upstream response).
 */
public record ProviderWarning(String provider, String message) {}
