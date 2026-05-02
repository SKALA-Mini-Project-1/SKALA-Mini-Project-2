package com.example.SKALA_Mini_Project_1.modules.waiting.observability;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class QueueMetrics {

    private final MeterRegistry meterRegistry;
    private final ObservationRegistry observationRegistry;

    public QueueMetrics(MeterRegistry meterRegistry, ObservationRegistry observationRegistry) {
        this.meterRegistry = meterRegistry;
        this.observationRegistry = observationRegistry;
    }

    public <T> T observe(String name, String contextualName, String operation, Supplier<T> supplier) {
        return Observation.createNotStarted(name, observationRegistry)
                .contextualName(contextualName)
                .lowCardinalityKeyValue("operation", operation)
                .observe(supplier);
    }

    public void incrementRequest(String operation, String result) {
        meterRegistry.counter(
                "fairline.queue.requests",
                "operation", operation,
                "result", result
        ).increment();
    }

    public void recordWaitPosition(long position) {
        if (position <= 0) {
            return;
        }
        DistributionSummary.builder("fairline.queue.wait.position")
                .baseUnit("rank")
                .register(meterRegistry)
                .record(position);
    }

    public void incrementRedisFailure(String operation) {
        meterRegistry.counter(
                "fairline.queue.redis.failures",
                "operation", operation
        ).increment();
    }

    public void incrementEntryTokenIssued() {
        meterRegistry.counter("fairline.queue.entry.tokens.issued").increment();
    }

    public void recordFanScoreLookup(String result, long boostMillis) {
        meterRegistry.counter(
                "fairline.queue.fan_score.lookups",
                "result", result
        ).increment();

        if (boostMillis <= 0) {
            return;
        }

        DistributionSummary.builder("fairline.queue.fan_score.boost.millis")
                .baseUnit("milliseconds")
                .register(meterRegistry)
                .record(boostMillis);
    }
}
