package com.example.incident.api.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IncidentAnalysisVersionRepository extends JpaRepository<IncidentAnalysisVersion, UUID> {

    @Query("""
            SELECT av FROM IncidentAnalysisVersion av
            WHERE av.incidentId = :incidentId
            ORDER BY av.versionNumber DESC
            """)
    List<IncidentAnalysisVersion> findByIncidentIdOrderByVersionDesc(
            @Param("incidentId") UUID incidentId, Pageable pageable);

    @Query("""
            SELECT av FROM IncidentAnalysisVersion av
            WHERE av.incidentId = :incidentId
            ORDER BY av.versionNumber DESC
            LIMIT 1
            """)
    Optional<IncidentAnalysisVersion> findLatestByIncidentId(@Param("incidentId") UUID incidentId);

    int countByIncidentId(UUID incidentId);
}
