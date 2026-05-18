package com.devops.dashboard.domain.exception;

public class InvalidExperimentTransitionException extends DomainException {
    
    public InvalidExperimentTransitionException(Object from, Object to) {
        super(String.format("Invalid state transition from %s to %s", from, to));
    }
}
