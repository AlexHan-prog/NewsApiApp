package com.newsapp.config;

import com.newsapp.service.DailyCallBudget;
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

    @Bean
    public DailyCallBudget newsApiCallBudget(@Value("${newsapi.daily-budget}") int limit) {
        return new DailyCallBudget(limit);
    }

    @Bean
    public DailyCallBudget guardianCallBudget(@Value("${guardian.daily-budget}") int limit) {
        return new DailyCallBudget(limit);
    }
}
