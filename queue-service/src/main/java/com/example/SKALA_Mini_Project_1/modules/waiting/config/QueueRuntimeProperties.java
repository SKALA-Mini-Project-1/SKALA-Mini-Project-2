package com.example.SKALA_Mini_Project_1.modules.waiting.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "queue.runtime")
public class QueueRuntimeProperties {

    @Min(1)
    private long maxSeatCapacity = 500;
}
