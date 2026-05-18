package com.devops.dashboard.domain.exception;

public class ExperimentLifetimeExceededException extends DomainException {
    
    public ExperimentLifetimeExceededException(String experimentId, String maxLifetime) {
        super("Experiment " + experimentId + " has exceeded maximum lifetime of " + maxLifetime);
    }
}
