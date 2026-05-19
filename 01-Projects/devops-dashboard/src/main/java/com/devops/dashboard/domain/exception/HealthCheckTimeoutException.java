package com.devops.dashboard.domain.exception;

public class HealthCheckTimeoutException extends com.devops.dashboard.domain.exception.shared.SharedException {

    public HealthCheckTimeoutException(String serviceInstance, long timeoutMs) {
        super("Health check timeout for " + serviceInstance + " after " + timeoutMs + "ms");
    }
}