package com.devops.dashboard.domain.exception;

public class HealthCheckTimeoutException extends DomainException {
    
    public HealthCheckTimeoutException(String serviceInstance, long timeoutMs) {
        super("Health check timeout for " + serviceInstance + " after " + timeoutMs + "ms");
    }
}
