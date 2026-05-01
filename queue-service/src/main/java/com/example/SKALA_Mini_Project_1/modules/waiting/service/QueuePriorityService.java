package com.example.SKALA_Mini_Project_1.modules.waiting.service;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QueuePriorityService {

    public static final int MAX_QUEUE_PRIORITY_BOOST_MILLIS = 5000;

    private final QueuePriorityPolicy queuePriorityPolicy;

    public int getQueuePriorityBoostMillis(Long userId, Long concertId) {
        long boostMillis = queuePriorityPolicy.resolveBoostMillis(userId, concertId);
        return (int) Math.min(Math.max(boostMillis, 0L), MAX_QUEUE_PRIORITY_BOOST_MILLIS);
    }
}
