package com.example.incident.detector.zombie;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface ZombieHoldCandidateRepository extends JpaRepository<ZombieCandidate, UUID> {

    List<ZombieCandidate> findByCheckedFalseAndCheckAfterAtBefore(OffsetDateTime now);

    boolean existsByBookingId(UUID bookingId);
}
