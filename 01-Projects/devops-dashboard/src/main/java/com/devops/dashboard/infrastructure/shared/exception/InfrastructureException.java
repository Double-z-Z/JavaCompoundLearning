/**
 * 基础设施层异常基类
 *
 * 基础设施异常表达"技术调用失败了"，用于：
 * - Docker/K8s 连接失败
 * - 命令执行超时
 * - 网络不可达
 *
 * 设计原则：
 * - 包含技术上下文（provider类型、退出码等）
 * - 由基础设施层翻译为领域异常后上浮
 */
package com.devops.dashboard.infrastructure.shared.exception;

public abstract class InfrastructureException extends RuntimeException {

    private final String providerType;

    protected InfrastructureException(String providerType, String message) {
        super(message);
        this.providerType = providerType;
    }

    protected InfrastructureException(String providerType, String message, Throwable cause) {
        super(message, cause);
        this.providerType = providerType;
    }

    public String getProviderType() {
        return providerType;
    }
}