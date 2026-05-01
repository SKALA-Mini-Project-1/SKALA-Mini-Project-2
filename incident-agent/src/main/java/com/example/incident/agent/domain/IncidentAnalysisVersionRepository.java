package com.example.incident.agent.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface IncidentAnalysisVersionRepository extends JpaRepository<IncidentAnalysisVersion, UUID> {

    List<IncidentAnalysisVersion> findByAnalysisStatus(String status, Pageable pageable);

    boolean existsByIncidentIdAndAnalysisStatus(UUID incidentId, String status);

    @Query("""
            SELECT av FROM IncidentAnalysisVersion av
            WHERE av.incidentId = :incidentId
            ORDER BY av.versionNumber DESC
            """)
    List<IncidentAnalysisVersion> findByIncidentIdOrderByVersionDesc(
            @Param("incidentId") UUID incidentId, Pageable pageable);
}
