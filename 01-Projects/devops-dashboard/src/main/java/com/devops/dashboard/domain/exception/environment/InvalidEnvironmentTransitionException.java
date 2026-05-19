package com.devops.dashboard.domain.exception.environment;

public class InvalidEnvironmentTransitionException extends EnvironmentException {

    public InvalidEnvironmentTransitionException(Object from, Object to) {
        super(String.format("Invalid state transition from %s to %s", from, to));
    }
}