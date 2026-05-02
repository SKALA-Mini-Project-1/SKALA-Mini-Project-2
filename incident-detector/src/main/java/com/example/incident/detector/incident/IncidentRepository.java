package com.example.incident.detector.incident;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    @Query("""
            SELECT i FROM Incident i
            WHERE i.incidentType = :type
              AND i.incidentKey  = :key
              AND i.status NOT IN ('RESOLVED')
            """)
    Optional<Incident> findOpenByTypeAndKey(
            @Param("type") String type,
            @Param("key") String key
    );

    @Query("""
            SELECT COUNT(i) FROM Incident i
            WHERE i.incidentType = :type
              AND i.status NOT IN ('RESOLVED')
            """)
    long countOpenByType(@Param("type") String type);
}
