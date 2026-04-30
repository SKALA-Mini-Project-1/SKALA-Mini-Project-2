package com.example.SKALA_Mini_Project_1.modules.payments.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.SKALA_Mini_Project_1.modules.payments.domain.PaymentEvent;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {
    long countByEventType(String eventType);
    boolean existsByEventId(java.util.UUID eventId);
    boolean existsByPaymentIdAndEventTypeAndPgEventId(java.util.UUID paymentId, String eventType, String pgEventId);
    java.util.List<PaymentEvent> findTop100ByEventTypeStartingWithOrderByCreatedAtDesc(String prefix);

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
