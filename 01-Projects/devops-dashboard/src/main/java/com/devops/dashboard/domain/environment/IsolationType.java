package com.devops.dashboard.domain.environment;

import java.util.Objects;

/**
 * 隔离类型枚举，定义系统支持的运行隔离方式。
 */
public enum IsolationType {

    /**
     * Docker 容器隔离
     */
    DOCKER("docker", "Docker 容器"),

    /**
     * 原生进程隔离
     */
    NATIVE("native", "原生进程");

    /**
     * 隔离类型编码
     */
    private final String code;

    /**
     * 隔离类型显示名称
     */
    private final String displayName;

    /**
     * 构造函数
     *
     * @param code        隔离类型编码
     * @param displayName 隔离类型显示名称
     */
    IsolationType(String code, String displayName) {
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.displayName = Objects.requireNonNull(displayName, "displayName must not be null");
    }

    /**
     * 获取隔离类型编码
     *
     * @return 隔离类型编码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取隔离类型显示名称
     *
     * @return 隔离类型显示名称
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 根据编码获取隔离类型
     *
     * @param code 隔离类型编码
     * @return 对应的隔离类型
     * @throws IllegalArgumentException 如果编码无效
     */
    public static IsolationType fromCode(String code) {
        for (IsolationType t : values()) {
            if (t.code.equalsIgnoreCase(code)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown IsolationType code: " + code);
    }
}
