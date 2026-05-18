package com.devops.dashboard.domain.exception;

public class ExperimentAlreadyConcludedException extends DomainException {
    
    public ExperimentAlreadyConcludedException(String experimentId) {
        super("Experiment " + experimentId + " has already been concluded");
    }
}
