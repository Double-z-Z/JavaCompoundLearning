package com.devops.dashboard.domain.exception;

public class InvalidEnvironmentTransitionException extends DomainException {
    
    public InvalidEnvironmentTransitionException(Object from, Object to) {
        super(String.format("Invalid state transition from %s to %s", from, to));
    }
}
