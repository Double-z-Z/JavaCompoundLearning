package com.devops.dashboard.domain.exception;

public class ExperimentNotFoundException extends DomainException {
    
    private final String experimentId;
    
    public ExperimentNotFoundException(String experimentId) {
        super("Experiment not found: " + experimentId);
        this.experimentId = experimentId;
    }
}
