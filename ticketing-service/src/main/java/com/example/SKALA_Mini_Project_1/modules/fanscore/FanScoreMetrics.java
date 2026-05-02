package com.example.SKALA_Mini_Project_1.modules.fanscore;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class FanScoreMetrics {

    private final MeterRegistry meterRegistry;
    private final ObservationRegistry observationRegistry;

    public FanScoreMetrics(MeterRegistry meterRegistry, ObservationRegistry observationRegistry) {
        this.meterRegistry = meterRegistry;
        this.observationRegistry = observationRegistry;
    }

    public <T> T observe(String name, String contextualName, String operation, Supplier<T> supplier) {
        return Observation.createNotStarted(name, observationRegistry)
                .contextualName(contextualName)
                .lowCardinalityKeyValue("operation", operation)
                .observe(supplier);
    }

    public void incrementSyncRequest(String operation, String result) {
        meterRegistry.counter(
                "fairline.fan_score.sync.requests",
                "operation", operation,
                "result", result
        ).increment();
    }

    public void incrementTarget(String result) {
        meterRegistry.counter(
                "fairline.fan_score.sync.targets",
                "result", result
        ).increment();
    }

    public void recordBatchTargetCount(int count) {
        if (count < 0) {
            return;
        }
        DistributionSummary.builder("fairline.fan_score.sync.batch.target.count")
                .baseUnit("targets")
                .register(meterRegistry)
                .record(count);
    }

    public void recordAppliedCount(int count) {
        if (count < 0) {
            return;
        }
        DistributionSummary.builder("fairline.fan_score.sync.batch.applied.count")
                .baseUnit("targets")
                .register(meterRegistry)
                .record(count);
    }
}
