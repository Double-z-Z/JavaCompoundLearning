package com.devops.dashboard.domain.exception.experiment;

public class ExperimentNotFoundException extends ExperimentException {

    private final String experimentId;

    public ExperimentNotFoundException(String experimentId) {
        super("Experiment not found: " + experimentId);
        this.experimentId = experimentId;
    }

    public String getExperimentId() {
        return experimentId;
    }
}