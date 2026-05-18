package com.devops.dashboard.domain.environment;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum EnvironmentType {
    DEV("开发环境", "dev"),
    TEST("测试环境", "test"),
    STAGING("预发布环境", "staging"),
    PROD("生产环境", "prod"),
    EXPERIMENT("实验环境", "experiment");
    
    private final String displayName;
    private final String code;
}
