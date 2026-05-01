package com.example.incident.detector.inbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface DetectorInboxRepository extends JpaRepository<DetectorInboxEvent, UUID> {

    @Modifying
    @Query("UPDATE DetectorInboxEvent e SET e.duplicateCount = e.duplicateCount + 1 WHERE e.dedupeKey = :dedupeKey")
    void incrementDuplicateCount(@Param("dedupeKey") String dedupeKey);
}
