package com.devops.dashboard.domain.exception;

// === 环境相关异常 ===

public class EnvironmentNotFoundException extends DomainException {
    
    private final String environmentId;
    
    public EnvironmentNotFoundException(String environmentId) {
        super("Environment not found: " + environmentId);
        this.environmentId = environmentId;
    }
    
    public String getEnvironmentId() {
        return environmentId;
    }
}

public class EnvironmentCreationException extends DomainException {
    
    public EnvironmentCreationException(String reason) {
        super("Failed to create environment: " + reason);
    }
    
    public EnvironmentCreationException(String reason, Throwable cause) {
        super("Failed to create environment: " + reason, cause);
    }
}

public class EnvironmentDestroyException extends DomainException {
    
    public EnvironmentDestroyException(String envId, String reason) {
        super("Failed to destroy environment " + envId + ": " + reason);
    }
}

public class ResourceQuotaExceededException extends DomainException {
    
    public ResourceQuotaExceededException(String resource, String requested, String limit) {
        super(String.format("Resource quota exceeded for %s: requested %s, limit %s", 
            resource, requested, limit));
    }
}

public class PortConflictException extends DomainException {
    
    public PortConflictException(int port, String service1, String service2) {
        super(String.format("Port conflict on %d between %s and %s", port, service1, service2));
    }
}

public class InvalidEnvironmentTransitionException extends DomainException {
    
    public InvalidEnvironmentTransitionException(Object from, Object to) {
        super(String.format("Invalid state transition from %s to %s", from, to));
    }
}
