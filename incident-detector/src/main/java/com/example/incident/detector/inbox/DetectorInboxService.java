package com.example.incident.detector.inbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DetectorInboxService {

    private final DetectorInboxRepository repository;

    /**
     * Kafka 이벤트 중복 제거.
     * DB unique 제약(dedupe_key)으로 중복을 차단한다.
     *
     * @return 최초 수신이면 true, 중복이면 false
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryRecord(String dedupeKey, String eventType, String sourceTopic) {
        try {
            DetectorInboxEvent ev = new DetectorInboxEvent();
            ev.setId(UUID.randomUUID());
            ev.setDedupeKey(dedupeKey);
            ev.setEventType(eventType);
            ev.setSourceTopic(sourceTopic);
            ev.setReceivedAt(OffsetDateTime.now(ZoneOffset.UTC));
            ev.setDuplicateCount(0);
            repository.saveAndFlush(ev);
            return true;
        } catch (DataIntegrityViolationException e) {
            repository.incrementDuplicateCount(dedupeKey);
            return false;
        }
    }
}
