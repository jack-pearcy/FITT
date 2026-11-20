package com.fitt.coach.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.fitt.coach.service.exception.CoachFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.stream.Collectors;

// The coach's model, served by Claude through the official Anthropic SDK.
//
// The API key stays on the server: the app talks to this backend and only the
// backend talks to Anthropic. With no key configured no client is built at all.
// The request carries the instructions and the training summary, nothing else:
// no user id and no metadata.
@Component
@EnableConfigurationProperties(CoachProperties.class)
public class ClaudeCoachModel implements CoachModel {

    private static final Logger log = LoggerFactory.getLogger(ClaudeCoachModel.class);

    private final CoachProperties properties;
    private final AnthropicClient client;

    public ClaudeCoachModel(CoachProperties properties) {
        this.properties = properties;
        String apiKey = properties.getApiKey();
        this.client = (apiKey == null || apiKey.isBlank())
                ? null
                : AnthropicOkHttpClient.builder()
                        .apiKey(apiKey.strip())
                        .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                        // One retry at most, so a slow failure cannot hold the request
                        // for several timeouts in a row.
                        .maxRetries(1)
                        .build();
    }

    @Override
    public boolean isAvailable() {
        return client != null;
    }

    @Override
    public String review(String instructions, String trainingSummary) {
        if (client == null) {
            throw new CoachFailedException("The coach has no API key");
        }

        MessageCreateParams params = MessageCreateParams.builder()
                .model(properties.getModel())
                .maxTokens(properties.getMaxTokens())
                .system(instructions)
                .addUserMessage(trainingSummary)
                .build();

        Message message;
        try {
            message = client.messages().create(params);
        } catch (AnthropicServiceException e) {
            // Status only. The request and response bodies are never logged.
            log.warn("Coach request failed with HTTP status {}", e.statusCode());
            throw new CoachFailedException("The coach could not answer right now", e);
        } catch (AnthropicException e) {
            log.warn("Coach request failed before a response arrived: {}", e.getClass().getSimpleName());
            throw new CoachFailedException("The coach could not answer right now", e);
        }

        // Token counts are what a review costs. They say nothing about its content.
        log.info("Coach review: model {}, {} input tokens, {} output tokens",
                properties.getModel(), message.usage().inputTokens(), message.usage().outputTokens());

        if (message.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
            throw new CoachFailedException("The coach declined to answer");
        }

        String text = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(TextBlock::text)
                .collect(Collectors.joining("\n"))
                .strip();
        if (text.isEmpty()) {
            throw new CoachFailedException("The coach gave an empty answer");
        }
        return text;
    }
}
