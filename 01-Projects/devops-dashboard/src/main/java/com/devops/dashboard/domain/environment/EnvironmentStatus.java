package com.devops.dashboard.domain.environment;

import java.util.Set;

/**
 * V3 Environment 状态枚举。
 *
 * <p>相比 V2 的变化：
 * <ul>
 *   <li>新增 READY（环境就绪，允许部署）</li>
 *   <li>新增 DEPLOYING（部署进行中）</li>
 *   <li>新增 ERROR（替代 FAILED，统一异常态）</li>
 *   <li>移除 STOPPED、FAILED、NOT_FOUND</li>
 * </ul>
 *
 * <h3>状态转换规则</h3>
 * <pre>{@code
 * CREATING  → READY | ERROR
 * READY     → DEPLOYING | DESTROYED
 * DEPLOYING → RUNNING | ERROR
 * RUNNING   → DESTROYED | DEPLOYING (覆盖部署)
 * ERROR     → DESTROYED | READY (修复后)
 * DESTROYED → (终态)
 * }</pre>
 */
public enum EnvironmentStatus {
    CREATING("创建中"),
    READY("就绪"),         // V3 新增
    DEPLOYING("部署中"),   // V3 新增
    RUNNING("运行中"),
    ERROR("异常"),        // V3 新增，替代 FAILED
    DESTROYED("已销毁");

    private final String description;

    EnvironmentStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 判断当前状态是否可以转换到目标状态。
     *
     * @param target 目标状态
     * @return 是否允许转换
     */
    public boolean canTransitionTo(EnvironmentStatus target) {
        return validTransitions().contains(target);
    }

    /**
     * 获取当前状态允许转换的目标状态集合。
     */
    public Set<EnvironmentStatus> validTransitions() {
        return switch (this) {
            case CREATING  -> Set.of(READY, ERROR);
            case READY     -> Set.of(DEPLOYING, DESTROYED);
            case DEPLOYING -> Set.of(RUNNING, ERROR);
            case RUNNING   -> Set.of(DESTROYED, DEPLOYING); // 支持覆盖部署
            case ERROR     -> Set.of(DESTROYED, READY);     // 修复后重新部署
            case DESTROYED -> Set.of();                     // 终态，不可转换
        };
    }

    /**
     * 生成状态转换错误时的提示信息。
     *
     * @param target 目标状态
     * @return 错误提示
     */
    public String suggestPrecondition(EnvironmentStatus target) {
        return switch (target) {
            case DEPLOYING -> "确认环境处于 READY 或 RUNNING 状态";
            case RUNNING -> "等待部署完成";
            case DESTROYED -> "先停止或修复环境";
            case READY -> "修复环境错误后重试";
            default -> "检查当前状态";
        };
    }

    /**
     * 判断是否为终态。
     */
    public boolean isTerminal() {
        return this == DESTROYED;
    }

    /**
     * 判断是否为可部署状态。
     */
    public boolean isDeployable() {
        return this == READY || this == RUNNING;
    }
}