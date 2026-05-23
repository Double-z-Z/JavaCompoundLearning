package com.devops.dashboard.domain.mcp;

/**
 * 部署流水线状态枚举。
 *
 * <pre>{@code
 * PENDING → CREATING → DEPLOYING → VERIFYING → ANALYZING → SUCCEEDED
 *                                                    ↓
 *                                                  FAILED → COMPENSATED
 * }</pre>
 */
public enum PipelineStatus {
    PENDING,      // 已创建，未执行
    CREATING,     // env_create 阶段
    DEPLOYING,    // env_deploy_service 阶段
    VERIFYING,    // test_health_check 阶段
    ANALYZING,    // analyze_network_path 阶段
    SUCCEEDED,    // 全部通过
    FAILED,       // 某阶段失败
    COMPENSATED;  // 补偿完成

    public boolean isTerminal() {
        return this == SUCCEEDED || this == COMPENSATED;
    }
}