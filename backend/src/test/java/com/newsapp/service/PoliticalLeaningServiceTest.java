package com.newsapp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.http.Headers;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.CacheCreation;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.ServerToolUsage;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.Usage;
import com.anthropic.services.blocking.MessageService;
import com.newsapp.model.Article;
import com.newsapp.model.Leaning;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class PoliticalLeaningServiceTest {

    private AnthropicClient client;
    private MessageService messages;
    private PoliticalLeaningService service;

    @BeforeEach
    void setUp() {
        client = mock(AnthropicClient.class);
        messages = mock(MessageService.class);
        when(client.messages()).thenReturn(messages);
        service = new PoliticalLeaningService(client, "claude-sonnet-5");
    }

    private static Article article(String title, String description, String fullText) {
        return new Article(
                new Article.Source("the-guardian", "The Guardian"),
                null,
                title,
                description,
                "https://theguardian.com/politics/" + title.hashCode(),
                null,
                "2026-09-22T10:00:00Z",
                null,
                "The Guardian",
                "Politics",
                null,
                fullText);
    }

    /** A minimal but valid Message carrying one text block of raw JSON, as Claude's reply would look on the wire. */
    private static Message fakeMessage(String json) {
        TextBlock textBlock = TextBlock.builder().text(json).citations(List.of()).build();
        return Message.builder()
                .id("msg_test")
                .model("claude-sonnet-5")
                .addContent(textBlock)
                .stopDetails(Optional.empty())
                .stopReason(Optional.empty())
                .stopSequence(Optional.<String>empty())
                .usage(Usage.builder()
                        .inputTokens(100)
                        .outputTokens(50)
                        .cacheCreation(CacheCreation.builder()
                                .ephemeral1hInputTokens(0)
                                .ephemeral5mInputTokens(0)
                                .build())
                        .cacheCreationInputTokens(0)
                        .cacheReadInputTokens(0)
                        .inferenceGeo(Optional.<String>empty())
                        .serverToolUse(Optional.<ServerToolUsage>empty())
                        .serviceTier(Optional.<Usage.ServiceTier>empty())
                        .build())
                .build();
    }

    @SuppressWarnings("unchecked")
    private void replyWith(String json) {
        Message raw = fakeMessage(json);
        when(messages.create(any(StructuredMessageCreateParams.class)))
                .thenAnswer(invocation -> {
                    StructuredMessageCreateParams<Object> params = invocation.getArgument(0);
                    return new StructuredMessage<>(params.outputType(), raw);
                });
    }

    /** The user message text Claude actually received, for asserting the title/body made it into the prompt. */
    @SuppressWarnings("unchecked")
    private String capturedUserMessage() {
        ArgumentCaptor<StructuredMessageCreateParams<Object>> captor = ArgumentCaptor.forClass(StructuredMessageCreateParams.class);
        org.mockito.Mockito.verify(messages).create(captor.capture());
        return captor.getValue().rawParams().messages().get(0).content().string().get();
    }

    @Test
    void mapsALeftLeaningAssessmentAndSendsTitleAndFullBody() {
        replyWith(
                """
                {"label":"LEFT","score":0.82,"explanation":"Uses sympathetic framing of the policy.",
                 "excerpts":[{"quote":"a landmark step forward","side":"LEFT","reason":"Positive framing of the policy"}]}
                """);
        Article article = article("Bill passes", "A short summary", "The full article body text goes here, describing a landmark step forward for the policy.");

        Leaning leaning = service.classify(article);

        assertEquals(Leaning.Label.LEFT, leaning.label());
        assertEquals(0.82, leaning.score());
        assertEquals("Uses sympathetic framing of the policy.", leaning.explanation());
        assertEquals(1, leaning.excerpts().size());
        assertEquals("a landmark step forward", leaning.excerpts().get(0).quote());
        assertEquals(Leaning.Label.LEFT, leaning.excerpts().get(0).side());

        String sent = capturedUserMessage();
        assertTrue(sent.contains(article.title()), "the title is sent");
        assertTrue(sent.contains(article.fullText()), "the full body is sent, not just the summary");
    }

    @Test
    void fallsBackToDescriptionThenTitleWhenThereIsNoBody() {
        replyWith("{\"label\":\"CENTER\",\"score\":0.9,\"explanation\":\"Neutral report.\",\"excerpts\":[]}");
        Article noBody = article("Council meets", "The council discussed the budget on Tuesday", null);

        Leaning leaning = service.classify(noBody);

        assertEquals(Leaning.Label.CENTER, leaning.label());
        assertTrue(leaning.excerpts().isEmpty());
    }

    @Test
    void dropsAnExcerptThatIsNotActuallyInTheSourceText() {
        replyWith(
                """
                {"label":"RIGHT","score":0.7,"explanation":"Frames the tax cut favourably.",
                 "excerpts":[{"quote":"a real quote from the body","side":"RIGHT","reason":"favourable framing"},
                             {"quote":"this was never actually written","side":"RIGHT","reason":"fabricated"}]}
                """);
        Article article = article("Tax cut passes", null, "Here is a real quote from the body of the article.");

        Leaning leaning = service.classify(article);

        assertEquals(1, leaning.excerpts().size(), "the fabricated quote is dropped, the real one kept");
        assertEquals("a real quote from the body", leaning.excerpts().get(0).quote());
    }

    @Test
    void excerptMatchingIsCaseAndWhitespaceTolerant() {
        replyWith(
                """
                {"label":"LEFT","score":0.6,"explanation":"x",
                 "excerpts":[{"quote":"A REAL   Quote","side":"LEFT","reason":"y"}]}
                """);
        Article article = article("Headline", null, "Some text with a real\nquote right here.");

        Leaning leaning = service.classify(article);

        assertEquals(1, leaning.excerpts().size());
    }

    @Test
    void dropsACenterTaggedExcerptEvenIfItIsInTheText() {
        replyWith(
                """
                {"label":"CENTER","score":0.5,"explanation":"x",
                 "excerpts":[{"quote":"balanced reporting here","side":"CENTER","reason":"y"}]}
                """);
        Article article = article("Headline", null, "This is balanced reporting here on the topic.");

        Leaning leaning = service.classify(article);

        assertTrue(leaning.excerpts().isEmpty(), "only LEFT/RIGHT excerpts are meaningful");
    }

    @Test
    void rateLimitBecomesABadGatewayWithASafeMessage() {
        when(messages.create(any(StructuredMessageCreateParams.class)))
                .thenThrow(RateLimitException.builder()
                        .headers(Headers.builder().build())
                        .body(com.anthropic.core.JsonValue.from(java.util.Map.of()))
                        .build());

        ResponseStatusException e =
                assertThrows(ResponseStatusException.class, () -> service.classify(article("A", null, "body text")));

        assertEquals(HttpStatus.BAD_GATEWAY, e.getStatusCode());
    }

    @Test
    void anIoFailureBecomesABadGateway() {
        when(messages.create(any(StructuredMessageCreateParams.class)))
                .thenThrow(new AnthropicIoException("connect failed", new java.io.IOException()));

        ResponseStatusException e =
                assertThrows(ResponseStatusException.class, () -> service.classify(article("A", null, "body text")));

        assertEquals(HttpStatus.BAD_GATEWAY, e.getStatusCode());
        assertEquals("Could not reach the leaning service", e.getReason());
    }
}
