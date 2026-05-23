package com.devops.dashboard.domain.mcp;

import java.util.List;

/**
 * 部署规格（Value Object）。
 *
 * @param serviceName        服务名（来自 ServiceRegistry 白名单）
 * @param targetHostId       目标宿主机 ID（来自 env_list）
 * @param version            镜像标签或 Git tag
 * @param envType            运行时隔离类型：docker | native
 * @param verifyEndpoints    部署后验证的 HTTP 端点路径列表
 * @param runtimeConstraint  运行时版本约束，如 openjdk:21-jre-slim
 * @param keepOnFailure      失败时是否保留环境用于排查
 */
public record DeploySpec(
    String serviceName,
    String targetHostId,
    String version,
    String envType,
    List<String> verifyEndpoints,
    String runtimeConstraint,
    boolean keepOnFailure
) {
    public static DeploySpec of(
            String serviceName,
            String targetHostId,
            String version,
            String envType) {
        return new DeploySpec(serviceName, targetHostId, version, envType, List.of(), null, false);
    }

    public static DeploySpec of(
            String serviceName,
            String targetHostId,
            String version,
            String envType,
            List<String> verifyEndpoints,
            String runtimeConstraint,
            boolean keepOnFailure) {
        return new DeploySpec(serviceName, targetHostId, version, envType, verifyEndpoints, runtimeConstraint, keepOnFailure);
    }
}