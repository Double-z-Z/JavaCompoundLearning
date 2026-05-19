package com.devops.dashboard.domain.exception.experiment;

public class ExperimentAlreadyConcludedException extends ExperimentException {

    public ExperimentAlreadyConcludedException(String experimentId) {
        super("Experiment " + experimentId + " has already been concluded");
    }
}