package com.devops.dashboard.domain.experiment;

import com.devops.dashboard.domain.shared.AggregateId;

public class ExperimentId extends AggregateId<ExperimentId> {
    
    private ExperimentId(String value) {
        super(value);
    }
    
    public static ExperimentId of(String value) {
        return new ExperimentId(value);
    }
    
    public static ExperimentId generate() {
        return new ExperimentId("exp-" + UUID.randomUUID().toString().substring(0, 8));
    }
}
