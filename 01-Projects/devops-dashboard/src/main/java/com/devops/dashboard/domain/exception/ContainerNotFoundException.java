package com.devops.dashboard.domain.exception;

public class ContainerNotFoundException extends DomainException {
    
    public ContainerNotFoundException(String containerId) {
        super("Container not found: " + containerId);
    }
}
