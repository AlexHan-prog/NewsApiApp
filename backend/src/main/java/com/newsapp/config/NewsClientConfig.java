package com.newsapp.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.newsapp.service.DailyCallBudget;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class NewsClientConfig {

    // Both clients start from Spring Boot's RestClient.Builder (a new one per injection), not the static
    // RestClient.builder(): only the Boot one applies spring.http.client.connect-timeout / read-timeout. Without those
    // a hung upstream would block the search, and a worker thread, forever.

    @Bean
    public RestClient newsApiRestClient(RestClient.Builder builder, @Value("${newsapi.key}") String apiKey) {
        return builder.baseUrl("https://newsapi.org/v2")
                .defaultHeader("X-Api-Key", apiKey)
                .build();
    }

    /** The Guardian wants its key as an {@code api-key} query parameter, so GuardianProvider adds it per request. */
    @Bean
    public RestClient guardianRestClient(RestClient.Builder builder) {
        return builder.baseUrl("https://content.guardianapis.com").build();
    }

    /**
     * Claude, for the political-leaning demo. The key is read explicitly from {@code anthropic.key}, the same way
     * every other key in this app is wired, rather than relying on the SDK's own {@code ANTHROPIC_API_KEY}
     * environment-variable lookup.
     */
    @Bean
    public AnthropicClient anthropicClient(
            @Value("${anthropic.key}") String apiKey, @Value("${anthropic.timeout}") Duration timeout) {
        return AnthropicOkHttpClient.builder().apiKey(apiKey).timeout(timeout).build();
    }

    @Bean
    public DailyCallBudget newsApiCallBudget(@Value("${newsapi.daily-budget}") int limit) {
        return new DailyCallBudget(limit);
    }

    @Bean
    public DailyCallBudget guardianCallBudget(@Value("${guardian.daily-budget}") int limit) {
        return new DailyCallBudget(limit);
    }
}
