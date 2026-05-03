package com.example.incident.agent.analysis;

import com.example.incident.agent.domain.Incident;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public class AnalysisInputBuilder {

    private final ObjectMapper objectMapper;

    public AnalysisInputBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String build(Incident incident) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", "incident-input.v1");
        root.put("incidentId", incident.getIncidentId().toString());
        root.put("incidentTypeCandidate", incident.getIncidentType());
        root.put("incidentKey", incident.getIncidentKey());
        root.put("status", incident.getStatus());
        root.put("severity", incident.getSeverity());
        root.put("firstDetectedAt", incident.getFirstDetectedAt().toString());
        root.put("lastDetectedAt", incident.getLastDetectedAt().toString());

        if (incident.getPrimaryPaymentId() != null) {
            root.put("primaryPaymentId", incident.getPrimaryPaymentId().toString());
        }
        if (incident.getPrimaryBookingId() != null) {
            root.put("primaryBookingId", incident.getPrimaryBookingId().toString());
        }
        if (incident.getUserId() != null) {
            root.put("userId", incident.getUserId());
        }
        if (incident.getConcertId() != null) {
            root.put("concertId", incident.getConcertId());
        }
        if (incident.getScheduleId() != null) {
            root.put("scheduleId", incident.getScheduleId());
        }
        if (incident.getOpenReasonSignal() != null) {
            root.put("openReasonSignal", incident.getOpenReasonSignal());
        }

        if (incident.getCurrentStateJsonb() != null) {
            try {
                com.fasterxml.jackson.databind.JsonNode stateNode =
                        objectMapper.readTree(incident.getCurrentStateJsonb());
                root.set("signals", stateNode);
                if (stateNode.has("timeline")) {
                    root.set("timeline", stateNode.get("timeline"));
                }
            } catch (JsonProcessingException e) {
                root.put("signals", incident.getCurrentStateJsonb());
            }
        }

        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize analysis input", e);
        }
    }
}
