package com.devops.dashboard.domain.exception.experiment;

public class InvalidExperimentTransitionException extends ExperimentException {

    public InvalidExperimentTransitionException(Object from, Object to) {
        super(String.format("Invalid state transition from %s to %s", from, to));
    }
}