package com.devops.dashboard.domain.environment;

import java.util.Objects;

/**
 * 运行时类型枚举，定义系统支持的运行环境类型。
 */
public enum RuntimeType {

    /**
     * Docker 容器运行时
     */
    DOCKER("docker", "Docker 容器"),

    /**
     * 原生进程运行时
     */
    NATIVE("native", "原生进程");

    /**
     * 运行时类型编码
     */
    private final String code;

    /**
     * 运行时类型显示名称
     */
    private final String displayName;

    /**
     * 构造函数
     *
     * @param code        运行时类型编码
     * @param displayName 运行时类型显示名称
     */
    RuntimeType(String code, String displayName) {
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.displayName = Objects.requireNonNull(displayName, "displayName must not be null");
    }

    /**
     * 获取运行时类型编码
     *
     * @return 运行时类型编码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取运行时类型显示名称
     *
     * @return 运行时类型显示名称
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 根据编码获取运行时类型
     *
     * @param code 运行时类型编码
     * @return 对应的运行时类型
     * @throws IllegalArgumentException 如果编码无效
     */
    public static RuntimeType fromCode(String code) {
        for (RuntimeType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown RuntimeType code: " + code);
    }
}
