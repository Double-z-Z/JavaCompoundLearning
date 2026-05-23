package com.devops.dashboard.domain.experiment;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

@Embeddable
@Getter
@EqualsAndHashCode
public class ExperimentId {

    @Column(name = "exp_id")
    private final String value;

    private ExperimentId() {
        this.value = UUID.randomUUID().toString();
    }

    private ExperimentId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Experiment ID cannot be null or blank");
        }
        this.value = value;
    }

    public static ExperimentId of(String value) {
        return new ExperimentId(value);
    }

    public static ExperimentId generate() {
        return new ExperimentId("exp-" + UUID.randomUUID().toString().substring(0, 8));
    }

    @Override
    public String toString() {
        return value;
    }
}
