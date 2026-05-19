package com.devops.dashboard.infrastructure.environment.exception;

import com.devops.dashboard.infrastructure.shared.exception.InfrastructureException;

/**
 * 容器不存在
 */
public class ContainerNotFoundException extends InfrastructureException {

    public ContainerNotFoundException(String containerId) {
        super("docker", "容器不存在: " + containerId);
    }
}