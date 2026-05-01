package com.example.SKALA_Mini_Project_1.modules.waiting.service;

import org.springframework.stereotype.Component;

@Component
public class QueuePriorityPolicy {

    public long resolveBoostMillis(Long userId, Long concertId) {
        // Queue service is now isolated from fan-score persistence.
        // Until a dedicated fan-score API exists, queue ordering stays neutral.
        return 0L;
    }
}
