package com.devops.dashboard.domain.exception.experiment;

public class ExperimentLifetimeExceededException extends ExperimentException {

    public ExperimentLifetimeExceededException(String experimentId, String maxLifetime) {
        super("Experiment " + experimentId + " has exceeded maximum lifetime of " + maxLifetime);
    }
}