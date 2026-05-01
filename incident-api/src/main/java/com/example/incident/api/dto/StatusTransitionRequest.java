package com.example.incident.api.dto;

public record StatusTransitionRequest(
        String operatorId,
        String note
) {}
