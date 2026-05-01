package com.example.incident.agent.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AnalysisOutputValidator {

    private static final List<String> REQUIRED_FIELDS = List.of(
            "schemaVersion", "incidentType", "severity", "confidence",
            "summary", "suspectedRootCause", "recommendedActions",
            "needsHumanApproval", "resolutionSuggestion"
    );

    private static final List<String> VALID_INCIDENT_TYPES = List.of(
            "DUPLICATE_PAYMENT", "GHOST_ORDER", "UNCONFIRMED_PAYMENT", "ZOMBIE_HOLD"
    );

    private static final List<String> VALID_SEVERITIES = List.of(
            "critical", "high", "medium", "low"
    );

    private final ObjectMapper objectMapper;

    public AnalysisOutputValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ValidationResult validate(String jsonText) {
        JsonNode root;
        try {
            root = objectMapper.readTree(jsonText);
        } catch (Exception e) {
            return ValidationResult.fail("LLM output is not valid JSON: " + e.getMessage());
        }

        for (String field : REQUIRED_FIELDS) {
            if (!root.has(field) || root.get(field).isNull()) {
                return ValidationResult.fail("Missing required field: " + field);
            }
        }

        String incidentType = root.get("incidentType").asText();
        if (!VALID_INCIDENT_TYPES.contains(incidentType)) {
            return ValidationResult.fail("Invalid incidentType: " + incidentType);
        }

        String severity = root.get("severity").asText();
        if (!VALID_SEVERITIES.contains(severity)) {
            return ValidationResult.fail("Invalid severity: " + severity);
        }

        double confidence = root.get("confidence").asDouble(-1);
        if (confidence < 0.0 || confidence > 1.0) {
            return ValidationResult.fail("confidence must be between 0.0 and 1.0");
        }

        String summary = root.get("summary").asText("");
        if (summary.length() > 500) {
            return ValidationResult.fail("summary exceeds 500 characters");
        }

        return ValidationResult.ok(root);
    }

    public record ValidationResult(boolean valid, String errorMessage, JsonNode parsed) {
        static ValidationResult ok(JsonNode parsed) {
            return new ValidationResult(true, null, parsed);
        }

        static ValidationResult fail(String errorMessage) {
            return new ValidationResult(false, errorMessage, null);
        }
    }
}
