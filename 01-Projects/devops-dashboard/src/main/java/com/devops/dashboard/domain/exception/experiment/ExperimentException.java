package com.devops.dashboard.domain.exception.experiment;

/**
 * 实验领域异常基类
 */
public abstract class ExperimentException extends RuntimeException {

    protected ExperimentException(String message) {
        super(message);
    }

    protected ExperimentException(String message, Throwable cause) {
        super(message, cause);
    }
}