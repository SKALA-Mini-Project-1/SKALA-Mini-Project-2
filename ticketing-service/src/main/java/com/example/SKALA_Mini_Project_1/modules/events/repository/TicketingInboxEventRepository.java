package com.example.SKALA_Mini_Project_1.modules.events.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.SKALA_Mini_Project_1.modules.events.domain.TicketingInboxEvent;

public interface TicketingInboxEventRepository extends JpaRepository<TicketingInboxEvent, UUID> {
    Optional<TicketingInboxEvent> findByDedupeKey(String dedupeKey);

    long countByStatus(String status);

    List<TicketingInboxEvent> findTop50ByOrderByLastSeenAtDesc();
}
