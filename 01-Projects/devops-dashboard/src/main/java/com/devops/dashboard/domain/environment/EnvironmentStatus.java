package com.devops.dashboard.domain.environment;

public enum EnvironmentStatus {
    CREATING("创建中"),
    RUNNING("运行中"),
    STOPPED("已停止"),
    DESTROYED("已销毁"),
    FAILED("失败"),
    NOT_FOUND("未找到");

    private final String description;

    EnvironmentStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean canTransitionTo(EnvironmentStatus target) {
        return switch (this) {
            case CREATING -> target == RUNNING || target == FAILED;
            case RUNNING -> target == STOPPED || target == DESTROYED || target == FAILED;
            case STOPPED -> target == RUNNING || target == DESTROYED;
            case DESTROYED -> false;
            case FAILED -> target == CREATING;
            case NOT_FOUND -> false;
        };
    }
}