package com.devops.dashboard.domain.environment;

public enum EnvironmentType {
    DEV("开发环境", "dev"),
    TEST("测试环境", "test"),
    STAGING("预发布环境", "staging"),
    PROD("生产环境", "prod"),
    EXPERIMENT("实验环境", "experiment");

    private final String displayName;
    private final String code;

    EnvironmentType(String displayName, String code) {
        this.displayName = displayName;
        this.code = code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCode() {
        return code;
    }
}