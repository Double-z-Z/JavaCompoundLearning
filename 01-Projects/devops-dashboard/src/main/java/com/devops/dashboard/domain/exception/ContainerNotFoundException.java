package com.devops.dashboard.domain.exception;

public class ContainerNotFoundException extends com.devops.dashboard.domain.exception.shared.SharedException {

    public ContainerNotFoundException(String containerId) {
        super("Container not found: " + containerId);
    }
}