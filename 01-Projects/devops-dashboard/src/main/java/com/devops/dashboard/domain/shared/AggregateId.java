package com.devops.dashboard.domain.shared;

import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

@Embeddable
@Getter
@EqualsAndHashCode
public abstract class AggregateId<T> {
    
    protected final String value;
    
    protected AggregateId() {
        this.value = UUID.randomUUID().toString();
    }
    
    protected AggregateId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Aggregate ID cannot be null or blank");
        }
        this.value = value;
    }
    
    @Override
    public String toString() {
        return value;
    }
}
