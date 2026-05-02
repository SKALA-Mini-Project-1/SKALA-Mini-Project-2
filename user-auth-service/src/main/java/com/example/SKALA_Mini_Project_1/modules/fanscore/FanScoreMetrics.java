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

    public void incrementRead(String scope, String result) {
        meterRegistry.counter(
                "fairline.fan_score.reads",
                "scope", scope,
                "result", result
        ).increment();
    }

    public void incrementApply(String result) {
        meterRegistry.counter(
                "fairline.fan_score.apply",
                "result", result
        ).increment();
    }

    public void recordTotalScoreAfterApply(int totalScore) {
        if (totalScore < 0) {
            return;
        }
        DistributionSummary.builder("fairline.fan_score.total.after.apply")
                .baseUnit("points")
                .register(meterRegistry)
                .record(totalScore);
    }
}
