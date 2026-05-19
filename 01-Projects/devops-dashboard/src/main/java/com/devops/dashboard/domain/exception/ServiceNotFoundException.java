package com.devops.dashboard.domain.exception;

public class ServiceNotFoundException extends com.devops.dashboard.domain.exception.shared.SharedException {

    public ServiceNotFoundException(String instanceId) {
        super("Service instance not found: " + instanceId);
    }
}