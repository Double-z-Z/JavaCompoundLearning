package com.devops.dashboard.domain.environment;

public enum ServiceInstanceStatus {
    PENDING("等待中"),
    DEPLOYING("部署中"),
    RUNNING("运行中"),
    STOPPED("已停止"),
    FAILED("失败");

    private final String description;

    ServiceInstanceStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
