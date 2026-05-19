package com.devops.dashboard.domain.exception;

public class ServiceDeploymentFailedException extends com.devops.dashboard.domain.exception.shared.SharedException {

    public ServiceDeploymentFailedException(String serviceName, String reason) {
        super("Service deployment failed for " + serviceName + ": " + reason);
    }

    public ServiceDeploymentFailedException(String serviceName, Throwable cause) {
        super("Service deployment failed for " + serviceName, cause);
    }
}