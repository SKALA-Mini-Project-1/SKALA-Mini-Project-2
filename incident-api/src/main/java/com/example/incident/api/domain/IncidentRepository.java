package com.example.incident.api.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    Page<Incident> findByStatus(String status, Pageable pageable);

    Page<Incident> findByIncidentTypeAndStatus(String incidentType, String status, Pageable pageable);

    Page<Incident> findBySeverity(String severity, Pageable pageable);
}
