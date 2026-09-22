package com.newsapp.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.newsapp.model.Article;
import com.newsapp.model.Leaning;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Asks Claude to read a whole article and assess its political leaning: which way it leans, how confident Claude is,
 * a short explanation, and quotes backing it up. This is a general-purpose LLM's own reading of the piece, not a
 * specialised, trained classifier - {@link Leaning#score()}'s Javadoc explains what that means for the number shown.
 * <p>
 * Deliberately small in scope: only {@link LeaningDemoService} calls this, for a fixed handful of articles, never as
 * a side effect of an ordinary search.
 */
@Service
public class PoliticalLeaningService {

    private static final Logger log = LoggerFactory.getLogger(PoliticalLeaningService.class);

    private static final String SYSTEM_PROMPT =
            """
            You assess the political leaning of a news article, in the context of American politics (left roughly \
            tracks Democratic-leaning framing, right roughly tracks Republican-leaning framing). You are given the \
            article's title and full body text.

            Judge the coverage itself - its framing, word choice, choice of sources, and what it includes or leaves \
            out - not who published it or what the subject's own politics are. Straightforward, neutral wire-style \
            reporting on a political topic is CENTER, not LEFT or RIGHT.

            Back your assessment with a few short quotes copied EXACTLY, word-for-word, from the article text you \
            were given - not paraphrased and not invented. If nothing in the article clearly signals a leaning, \
            leave the quotes list empty rather than stretching for one.""";

    private final AnthropicClient client;
    private final String model;

    public PoliticalLeaningService(AnthropicClient client, @Value("${anthropic.model}") String model) {
        this.client = client;
        this.model = model;
    }

    /**
     * Reads the whole article (title + full body, falling back to the summary or just the title when there is no
     * body) and returns Claude's assessment. Cached per article URL, since the same article is re-classified every
     * time the demo cache expires, not on every page load.
     * <p>
     * Failures become {@link ResponseStatusException} with a message safe to show users - never the raw SDK
     * exception text, which can include request details.
     */
    @Cacheable(cacheNames = "leaning", key = "#article.url()", sync = true)
    public Leaning classify(Article article) {
        String text = textFor(article);
        StructuredMessageCreateParams<LlmAssessment> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(2000L)
                .system(SYSTEM_PROMPT)
                .outputConfig(LlmAssessment.class)
                .addUserMessage("TITLE: " + article.title() + "\n\nARTICLE:\n" + text)
                .build();

        try {
            LlmAssessment assessment = client.messages().create(params).content().stream()
                    .flatMap(block -> block.text().stream())
                    .findFirst()
                    .map(typed -> typed.text())
                    .orElseThrow(PoliticalLeaningService::unexpectedReply);
            return toLeaning(assessment, text);
        } catch (RateLimitException e) {
            log.warn("Claude rate limit hit while classifying an article");
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "The leaning service is rate limited right now");
        } catch (AnthropicServiceException e) {
            log.warn("Claude returned an error [{}] {}: {}", e.statusCode(), e.errorType().orElse(null), e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The leaning service returned an error");
        } catch (AnthropicIoException e) {
            log.warn("Could not reach Claude: {}", e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not reach the leaning service");
        }
    }

    /** Title + full body; falls back to the summary, then just the title, for content with no body. */
    private static String textFor(Article article) {
        if (article.fullText() != null && !article.fullText().isBlank()) {
            return article.fullText();
        }
        if (article.description() != null && !article.description().isBlank()) {
            return article.description();
        }
        return article.title();
    }

    /**
     * Drops any excerpt tagged CENTER (only LEFT/RIGHT support a leaning) and any quote that isn't actually in the
     * text Claude was given - a guard against a fabricated or paraphrased "quote". label/score/explanation are kept
     * regardless of how many excerpts survive.
     */
    private static Leaning toLeaning(LlmAssessment assessment, String sourceText) {
        String normalizedSource = normalize(sourceText);
        List<Leaning.Excerpt> excerpts = assessment.excerpts() == null
                ? List.of()
                : assessment.excerpts().stream()
                        .filter(excerpt -> excerpt.side() == Leaning.Label.LEFT || excerpt.side() == Leaning.Label.RIGHT)
                        .filter(excerpt -> excerpt.quote() != null && !excerpt.quote().isBlank())
                        .filter(excerpt -> {
                            boolean found = normalizedSource.contains(normalize(excerpt.quote()));
                            if (!found) {
                                log.warn("Dropping an unverifiable leaning excerpt (not found in the source text)");
                            }
                            return found;
                        })
                        .map(excerpt -> new Leaning.Excerpt(excerpt.quote().strip(), excerpt.side(), excerpt.reason()))
                        .toList();
        return new Leaning(assessment.label(), assessment.score(), assessment.explanation(), excerpts);
    }

    /** Case-insensitive, whitespace-collapsed, so a quote copied with different line breaks still matches. */
    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }

    private static ResponseStatusException unexpectedReply() {
        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY, "The leaning service sent a reply that could not be understood");
    }

    /** The structured-output target: Claude's raw assessment, before excerpt verification. */
    record LlmAssessment(
            @JsonPropertyDescription(
                            "Overall political leaning of the article, in the context of American politics: LEFT,"
                                    + " CENTER, or RIGHT.")
                    Leaning.Label label,
            @JsonPropertyDescription(
                            "Your own confidence in this label, from 0.0 (barely more than a guess) to 1.0 (very"
                                    + " confident). This is your self-assessment, not a statistical probability.")
                    double score,
            @JsonPropertyDescription(
                            "2 to 4 sentences explaining your reasoning: the article's framing, word choice,"
                                    + " sourcing, or what it includes or leaves out.")
                    String explanation,
            @JsonPropertyDescription(
                            "0 to 5 short quotes copied EXACTLY, word-for-word, from the ARTICLE text that best"
                                    + " illustrate the leaning. Empty if the article reads as neutral, wire-style"
                                    + " reporting with no clear leaning language.")
                    List<LlmExcerpt> excerpts) {}

    record LlmExcerpt(
            @JsonPropertyDescription(
                            "An exact, word-for-word quote copied from the ARTICLE text - not paraphrased. Must"
                                    + " match the source text exactly, so it can be verified against it.")
                    String quote,
            @JsonPropertyDescription("Which side this quote leans toward: LEFT or RIGHT only, never CENTER.")
                    Leaning.Label side,
            @JsonPropertyDescription("One short sentence on why this quote signals that leaning.") String reason) {}
}
