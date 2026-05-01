package com.example.incident.detector.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IncidentAnalysisVersionRepository extends JpaRepository<IncidentAnalysisVersion, UUID> {
}
