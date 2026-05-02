package com.example.incident.agent.analysis;

import com.example.incident.agent.domain.Incident;
import com.example.incident.agent.domain.IncidentAnalysisVersion;
import com.example.incident.agent.domain.IncidentAnalysisVersionRepository;
import com.example.incident.agent.domain.IncidentRepository;
import com.example.incident.agent.llm.OpenAiLlmClient;
import com.example.incident.agent.llm.LlmResponse;
import com.example.incident.agent.prompt.SystemPromptBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AnalysisService {

    private final IncidentAnalysisVersionRepository versionRepository;
    private final IncidentRepository incidentRepository;
    private final OpenAiLlmClient llmClient;
    private final SystemPromptBuilder systemPromptBuilder;
    private final AnalysisInputBuilder inputBuilder;
    private final AnalysisOutputValidator outputValidator;

    @Value("${agent.llm.model:claude-sonnet-4-6}")
    private String llmModel;

    @Value("${agent.llm.max-retry:2}")
    private int maxRetry;

    public void analyze(IncidentAnalysisVersion version) {
        UUID incidentId = version.getIncidentId();

        Optional<Incident> incidentOpt = incidentRepository.findById(incidentId);
        if (incidentOpt.isEmpty()) {
            markFailed(version, "Incident not found: " + incidentId);
            return;
        }

        Incident incident = incidentOpt.get();
        String inputJson = inputBuilder.build(incident);

        markRunning(version, inputJson);

        String systemPrompt = systemPromptBuilder.build();
        Exception lastError = null;

        for (int attempt = 0; attempt <= maxRetry; attempt++) {
            try {
                LlmResponse llmResponse = llmClient.call(systemPrompt, inputJson);
                AnalysisOutputValidator.ValidationResult result = outputValidator.validate(llmResponse.text());

                if (!result.valid()) {
                    lastError = new IllegalStateException("Validation failed: " + result.errorMessage());
                    log.warn("[agent] Output validation failed attempt={} versionId={} reason={}",
                            attempt, version.getAnalysisVersionId(), result.errorMessage());
                    continue;
                }

                markCompleted(version, llmResponse, llmResponse.text());
                return;

            } catch (Exception e) {
                lastError = e;
                log.warn("[agent] LLM call failed attempt={} versionId={} error={}",
                        attempt, version.getAnalysisVersionId(), e.getMessage());
            }
        }

        markFailed(version, lastError != null ? lastError.getMessage() : "Unknown error after retries");
    }

    private void markRunning(IncidentAnalysisVersion version, String inputJson) {
        version.setAnalysisStatus("RUNNING");
        version.setStartedAt(OffsetDateTime.now());
        version.setInputSnapshotJsonb(inputJson);
        version.setLlmModel(llmModel);
        versionRepository.save(version);
    }

    private void markCompleted(IncidentAnalysisVersion version, LlmResponse llmResponse, String outputJson) {
        AnalysisOutputValidator.ValidationResult validated = outputValidator.validate(outputJson);
        String summary = validated.parsed() != null
                ? validated.parsed().path("summary").asText("")
                : "";

        version.setAnalysisStatus("COMPLETED");
        version.setCompletedAt(OffsetDateTime.now());
        version.setOutputJsonb(outputJson);
        version.setSummaryText(summary);
        version.setPromptTokens(llmResponse.inputTokens());
        version.setCompletionTokens(llmResponse.outputTokens());
        version.setLatencyMs(llmResponse.latencyMs());
        version.setOutputSchemaVersion("incident-analysis-output.v1");
        versionRepository.save(version);

        log.info("[agent] Analysis completed. versionId={} incidentId={} tokens={}+{}",
                version.getAnalysisVersionId(), version.getIncidentId(),
                llmResponse.inputTokens(), llmResponse.outputTokens());
    }

    private void markFailed(IncidentAnalysisVersion version, String reason) {
        version.setAnalysisStatus("FAILED");
        version.setCompletedAt(OffsetDateTime.now());
        version.setFailureReason(reason);
        versionRepository.save(version);

        log.error("[agent] Analysis failed. versionId={} incidentId={} reason={}",
                version.getAnalysisVersionId(), version.getIncidentId(), reason);
    }
}
