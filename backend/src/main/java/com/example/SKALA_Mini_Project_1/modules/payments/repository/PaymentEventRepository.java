package com.example.SKALA_Mini_Project_1.modules.payments.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.SKALA_Mini_Project_1.modules.payments.domain.PaymentEvent;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {
    long countByEventType(String eventType);

    @Query(
            value = """
                    SELECT GREATEST(COUNT(*) - COUNT(DISTINCT payment_id), 0)
                    FROM payment_events
                    WHERE event_type = 'WEBHOOK_DONE_RECEIVED'
                    """,
            nativeQuery = true
    )
    long countDuplicateDoneWebhookEvents();
}

