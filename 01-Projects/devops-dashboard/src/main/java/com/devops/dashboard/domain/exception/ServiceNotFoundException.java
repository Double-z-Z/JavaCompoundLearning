package com.devops.dashboard.domain.exception;

public class ServiceNotFoundException extends DomainException {
    
    public ServiceNotFoundException(String instanceId) {
        super("Service instance not found: " + instanceId);
    }
}
