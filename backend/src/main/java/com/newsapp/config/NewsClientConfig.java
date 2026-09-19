package com.newsapp.config;

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
}
