package com.devops.dashboard.domain.exception;

// === 服务相关异常 ===

public class ServiceDeploymentFailedException extends DomainException {
    
    public ServiceDeploymentFailedException(String serviceName, String reason) {
        super("Service deployment failed for " + serviceName + ": " + reason);
    }
    
    public ServiceDeploymentFailedException(String serviceName, Throwable cause) {
        super("Service deployment failed for " + serviceName, cause);
    }
}

public class HealthCheckTimeoutException extends DomainException {
    
    public HealthCheckTimeoutException(String serviceInstance, long timeoutMs) {
        super("Health check timeout for " + serviceInstance + " after " + timeoutMs + "ms");
    }
}

public class ServiceNotFoundException extends DomainException {
    
    public ServiceNotFoundException(String instanceId) {
        super("Service instance not found: " + instanceId);
    }
}
