package com.devops.dashboard.domain.exception;

// === 实验相关异常 ===

public class ExperimentNotFoundException extends DomainException {
    
    private final String experimentId;
    
    public ExperimentNotFoundException(String experimentId) {
        super("Experiment not found: " + experimentId);
        this.experimentId = experimentId;
    }
}

public class ExperimentAlreadyConcludedException extends DomainException {
    
    public ExperimentAlreadyConcludedException(String experimentId) {
        super("Experiment " + experimentId + " has already been concluded");
    }
}

public class ExperimentLifetimeExceededException extends DomainException {
    
    public ExperimentLifetimeExceededException(String experimentId, String maxLifetime) {
        super("Experiment " + experimentId + " has exceeded maximum lifetime of " + maxLifetime);
    }
}

public class InvalidExperimentTransitionException extends DomainException {
    
    public InvalidExperimentTransitionException(Object from, Object to) {
        super(String.format("Invalid state transition from %s to %s", from, to));
    }
}
