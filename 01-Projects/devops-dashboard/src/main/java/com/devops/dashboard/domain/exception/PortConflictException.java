package com.devops.dashboard.domain.exception;

public class PortConflictException extends DomainException {
    
    public PortConflictException(int port, String service1, String service2) {
        super(String.format("Port conflict on %d between %s and %s", port, service1, service2));
    }
}
