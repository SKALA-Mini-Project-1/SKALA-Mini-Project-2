package com.example.SKALA_Mini_Project_1.modules.payments.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.example.SKALA_Mini_Project_1.modules.payments.domain.PaymentEvent;

import java.time.OffsetDateTime;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {
    long countByEventType(String eventType);
    boolean existsByEventId(java.util.UUID eventId);
    boolean existsByPaymentIdAndEventTypeAndPgEventId(java.util.UUID paymentId, String eventType, String pgEventId);
    java.util.List<PaymentEvent> findTop100ByEventTypeStartingWithOrderByCreatedAtDesc(String prefix);

    java.util.List<PaymentEvent> findTop50ByPublishStatusOrderByCreatedAtAsc(String publishStatus);

    @Modifying
    @Query("UPDATE PaymentEvent e SET e.publishStatus = :status, e.publishedAt = :publishedAt, e.retryCount = :retryCount, e.lastError = :lastError WHERE e.id = :id")
    void updatePublishStatus(Long id, String status, OffsetDateTime publishedAt, Integer retryCount, String lastError);

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
