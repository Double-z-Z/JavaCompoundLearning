package com.devops.dashboard.domain.environment;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum EnvironmentStatus {
    CREATING("创建中"),
    RUNNING("运行中"),
    STOPPED("已停止"),
    DESTROYED("已销毁"),
    FAILED("失败");
    
    private final String description;
    
    public boolean canTransitionTo(EnvironmentStatus target) {
        return switch (this) {
            case CREATING -> target == RUNNING || target == FAILED;
            case RUNNING -> target == STOPPED || target == DESTROYED || target == FAILED;
            case STOPPED -> target == RUNNING || target == DESTROYED;
            case DESTROYED -> false;  // 终态
            case FAILED -> target == CREATING;  // 可以重试创建
        };
    }
}
