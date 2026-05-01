package com.example.incident.detector.correlation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PendingCorrelationRepository extends JpaRepository<PendingCorrelation, UUID> {

    Optional<PendingCorrelation> findByCorrelationTypeAndKeyTypeAndKeyValueAndResolvedFalse(
            String correlationType, String keyType, String keyValue);

    List<PendingCorrelation> findByResolvedFalseAndDeadlineAtBefore(OffsetDateTime now);

    @Modifying
    @Query("""
            UPDATE PendingCorrelation c
               SET c.resolved = true
             WHERE c.correlationType = :type
               AND c.keyType         = :keyType
               AND c.keyValue        = :keyValue
               AND c.resolved        = false
            """)
    int resolveByTypeAndKey(
            @Param("type") String type,
            @Param("keyType") String keyType,
            @Param("keyValue") String keyValue
    );
}
