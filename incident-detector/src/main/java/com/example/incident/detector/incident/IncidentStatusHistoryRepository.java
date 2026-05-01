package com.example.incident.detector.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IncidentStatusHistoryRepository extends JpaRepository<IncidentStatusHistory, UUID> {}
