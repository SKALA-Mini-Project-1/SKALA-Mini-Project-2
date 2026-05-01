package com.example.SKALA_Mini_Project_1.modules.events.repository;

import com.example.SKALA_Mini_Project_1.modules.events.domain.TicketingOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketingOutboxRepository extends JpaRepository<TicketingOutbox, Long> {
    List<TicketingOutbox> findTop50ByPublishStatusOrderByCreatedAtAsc(String publishStatus);
}
