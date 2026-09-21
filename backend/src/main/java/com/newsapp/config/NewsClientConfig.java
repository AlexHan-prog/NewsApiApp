package com.newsapp.config;

import com.newsapp.service.DailyCallBudget;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class NewsClientConfig {

    @Bean
    public RestClient newsApiRestClient(@Value("${newsapi.key}") String apiKey) {
        return RestClient.builder()
                .baseUrl("https://newsapi.org/v2")
                .defaultHeader("X-Api-Key", apiKey)
                .build();
    }

    /** The Guardian wants its key as an {@code api-key} query parameter, so GuardianProvider adds it per request. */
    @Bean
    public RestClient guardianRestClient() {
        return RestClient.builder().baseUrl("https://content.guardianapis.com").build();
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
