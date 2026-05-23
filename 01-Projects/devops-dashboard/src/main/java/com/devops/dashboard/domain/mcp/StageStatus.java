package com.devops.dashboard.domain.mcp;

/**
 * 阶段状态枚举。
 */
public enum StageStatus {
    PENDING,    // 待执行
    RUNNING,    // 执行中
    SUCCEEDED,  // 成功
    FAILED,     // 失败
    SKIPPED     // 跳过
}