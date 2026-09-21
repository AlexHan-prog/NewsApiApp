package com.newsapp.service;

import com.newsapp.model.Article;
import com.newsapp.model.GuardianResponse;
import com.newsapp.model.SearchCriteria;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/**
 * The Guardian Open Platform. Unlike NewsAPI it can filter by section and tag, and returns the full article body.
 * It covers one publisher only, so it can't honour a filter for any other outlet.
 */
@Service
public class GuardianProvider implements NewsProvider {

    public static final String NAME = "The Guardian";
    /** An outlet filter on this domain is answered by this provider, not by NewsAPI (whose free plan has no coverage). */
    public static final String DOMAIN = "theguardian.com";

    private static final Logger log = LoggerFactory.getLogger(GuardianProvider.class);

    private static final int PAGE_SIZE = 50; // the API maximum
    private static final String SHOW_FIELDS = "trailText,byline,thumbnail,body";
    private static final String SHOW_TAGS = "contributor";

    private final RestClient restClient;
    private final DailyCallBudget dailyCallBudget;
    private final String apiKey;

    public GuardianProvider(
            @Qualifier("guardianRestClient") RestClient restClient,
            @Qualifier("guardianCallBudget") DailyCallBudget dailyCallBudget,
            @Value("${guardian.key}") String apiKey) {
        this.restClient = restClient;
        this.dailyCallBudget = dailyCallBudget;
        this.apiKey = apiKey;
    }

    @Override
    public String name() {
        return NAME;
    }

    /** Skipped if the user restricted the search to outlets other than the Guardian. Outlet filters are then moot. */
    @Override
    public Optional<SearchCriteria> narrow(SearchCriteria requested) {
        if (!requested.domains().isEmpty() && !requested.domains().contains(DOMAIN)) {
            return Optional.empty();
        }
        return Optional.of(new SearchCriteria(requested.keyword(), new TreeSet<>(), requested.sections(), requested.tags()));
    }

    /**
     * Same caching rules as {@link NewsApiProvider#search}: the body, and so the budget, only runs on a cache miss.
     * Several sections or tags are OR-ed, using the Guardian's {@code |} filter syntax.
     * <p>
     * Error messages are written here rather than taken from the exception: a failed request's message contains its
     * URL, which holds the api-key, and this message is shown to the browser.
     */
    @Override
    @Cacheable(cacheNames = "guardianArticles", key = "#criteria.cacheKey()", sync = true)
    public List<Article> search(SearchCriteria criteria) {
        // Daily budget checked first
        if (!dailyCallBudget.tryConsume()) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS, "Daily Guardian request budget reached. It resets at 00:00 UTC.");
        }
        log.info(
                "Guardian call for \"{}\" sections=\"{}\" tags=\"{}\"",
                criteria.keyword(),
                String.join(",", criteria.sections()),
                String.join(",", criteria.tags()));
        try {
            GuardianResponse body = restClient.get()
                    .uri(uriBuilder -> {
                        // Values go in as URI variables so they are fully encoded ('+' and '&' in a keyword included).
                        Map<String, String> vars = new HashMap<>();
                        uriBuilder.path("/search").queryParam("api-key", "{key}");
                        vars.put("key", apiKey);
                        uriBuilder.queryParam("page-size", PAGE_SIZE);
                        uriBuilder.queryParam("show-fields", SHOW_FIELDS);
                        uriBuilder.queryParam("show-tags", SHOW_TAGS);
                        if (!criteria.keyword().isEmpty()) {
                            uriBuilder.queryParam("q", "{q}");
                            vars.put("q", criteria.keyword());
                        }
                        if (!criteria.sections().isEmpty()) {
                            uriBuilder.queryParam("section", "{section}");
                            vars.put("section", String.join("|", criteria.sections()));
                        }
                        if (!criteria.tags().isEmpty()) {
                            uriBuilder.queryParam("tag", "{tag}");
                            vars.put("tag", String.join("|", criteria.tags()));
                        }
                        return uriBuilder.build(vars);
                    })
                    .retrieve()
                    .body(GuardianResponse.class);

            if (body == null || body.response() == null) {
                return List.of();
            }
            GuardianResponse.Response response = body.response();
            if (!"ok".equals(response.status())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY, "The Guardian returned status " + response.status());
            }
            return response.results() == null
                    ? List.of()
                    : response.results().stream().map(GuardianProvider::toArticle).toList();
        } catch (RestClientResponseException e) {
            log.warn("Guardian returned HTTP {}", e.getStatusCode().value());
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "The Guardian returned HTTP " + e.getStatusCode().value());
        } catch (RestClientException e) {
            log.warn("Could not reach the Guardian: {}", e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not reach The Guardian");
        }
    }

    private static Article toArticle(GuardianResponse.Result result) {
        GuardianResponse.Fields fields = result.fields();
        return new Article(
                new Article.Source("the-guardian", NAME),
                author(result),
                result.webTitle(),
                fields == null ? null : blankToNull(HtmlText.toPlain(fields.trailText())),
                result.webUrl(),
                fields == null ? null : blankToNull(fields.thumbnail()),
                result.webPublicationDate(),
                fields == null ? null : blankToNull(HtmlText.toPlain(fields.body())),
                NAME,
                result.sectionName());
    }

    /** The byline if there is one, otherwise the first contributor tag. */
    private static String author(GuardianResponse.Result result) {
        if (result.fields() != null && result.fields().byline() != null && !result.fields().byline().isBlank()) {
            return result.fields().byline().strip();
        }
        if (result.tags() != null) {
            return result.tags().stream()
                    .filter(tag -> "contributor".equals(tag.type()))
                    .map(GuardianResponse.Tag::webTitle)
                    .filter(title -> title != null && !title.isBlank())
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
