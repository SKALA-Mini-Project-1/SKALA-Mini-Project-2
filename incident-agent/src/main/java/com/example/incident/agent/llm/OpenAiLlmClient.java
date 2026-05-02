package com.example.incident.agent.llm;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class OpenAiLlmClient {

    private final OpenAIClient client;
    private final String model;
    private final long maxTokens;

    public OpenAiLlmClient(
            @Value("${agent.llm.api-key}") String apiKey,
            @Value("${agent.llm.model:gpt-4o}") String model,
            @Value("${agent.llm.max-tokens:2048}") long maxTokens
    ) {
        this.client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
        this.model = model;
        this.maxTokens = maxTokens;
    }

    public LlmResponse call(String systemPrompt, String userMessage) {
        long start = System.currentTimeMillis();

        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(model)
                .maxCompletionTokens(maxTokens)
                .messages(List.of(
                        ChatCompletionMessageParam.ofSystem(
                                ChatCompletionSystemMessageParam.builder()
                                        .content(systemPrompt)
                                        .build()
                        ),
                        ChatCompletionMessageParam.ofUser(
                                ChatCompletionUserMessageParam.builder()
                                        .content(userMessage)
                                        .build()
                        )
                ))
                .build();

        ChatCompletion response = client.chat().completions().create(params);
        long latencyMs = System.currentTimeMillis() - start;

        String text = response.choices().stream()
                .findFirst()
                .flatMap(choice -> choice.message().content())
                .orElse("");

        int inputTokens = response.usage().map(u -> (int) u.promptTokens()).orElse(0);
        int outputTokens = response.usage().map(u -> (int) u.completionTokens()).orElse(0);

        log.info("[llm] Response. model={}, inputTokens={}, outputTokens={}, latencyMs={}",
                model, inputTokens, outputTokens, latencyMs);

        return new LlmResponse(text, inputTokens, outputTokens, latencyMs);
    }
}
