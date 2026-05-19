package com.devops.dashboard.domain.exception.environment;

/**
 * 环境领域异常基类
 */
public abstract class EnvironmentException extends RuntimeException {

    protected EnvironmentException(String message) {
        super(message);
    }

    protected EnvironmentException(String message, Throwable cause) {
        super(message, cause);
    }
}