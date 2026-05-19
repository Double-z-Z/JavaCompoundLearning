package com.devops.dashboard.domain.environment;

import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

@Embeddable
@EqualsAndHashCode
public class EnvironmentId {

    private final String value;

    public String getValue() {
        return value;
    }

    // For JPA/Orm tool
    public String value() {
        return value;
    }

    private EnvironmentId() {
        this.value = UUID.randomUUID().toString();
    }

    private EnvironmentId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Environment ID cannot be null or blank");
        }
        this.value = value;
    }

    public static EnvironmentId of(String value) {
        return new EnvironmentId(value);
    }

    public static EnvironmentId generate() {
        return new EnvironmentId("env-" + UUID.randomUUID().toString().substring(0, 8));
    }

    @Override
    public String toString() {
        return value;
    }
}
