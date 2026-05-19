package com.devops.dashboard.domain.experiment;

public enum ExperimentStatus {
    PLANNING("规划中"),
    RUNNING("运行中"),
    COMPLETED("已完成"),
    ARCHIVED("已归档"),
    CANCELLED("已取消");

    private final String description;

    ExperimentStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean canTransitionTo(ExperimentStatus target) {
        return switch (this) {
            case PLANNING -> target == RUNNING || target == CANCELLED;
            case RUNNING -> target == COMPLETED || target == CANCELLED;
            case COMPLETED -> target == ARCHIVED;
            case ARCHIVED, CANCELLED -> false;
        };
    }
}