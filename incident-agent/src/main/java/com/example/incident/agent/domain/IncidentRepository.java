package com.example.incident.agent.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {
}
