package com.devops.dashboard.domain.environment;

import com.devops.dashboard.domain.shared.AggregateId;

public class EnvironmentId extends AggregateId<EnvironmentId> {
    
    private EnvironmentId(String value) {
        super(value);
    }
    
    public static EnvironmentId of(String value) {
        return new EnvironmentId(value);
    }
    
    public static EnvironmentId generate() {
        return new EnvironmentId("env-" + UUID.randomUUID().toString().substring(0, 8));
    }
}
