package com.example.incident.agent.llm;

public record LlmResponse(
        String text,
        int inputTokens,
        int outputTokens,
        long latencyMs
) {}
