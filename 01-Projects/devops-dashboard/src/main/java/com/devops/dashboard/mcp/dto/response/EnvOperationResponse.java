package com.devops.dashboard.mcp.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 环境操作响应 DTO（通用）。
 *
 * <p>作为所有环境管理 MCP Tool 的统一响应格式，包含操作结果、环境状态和访问信息。
 * 采用 Builder 模式支持不同操作返回不同子集的字段。</p>
 *
 * <h3>响应结构</h3>
 * <pre>{
 *   "success": true,
 *   "message": "Environment created successfully",
 *   "envId": "exp-1745123456789",
 *   "envName": "nacos-perf-test",
 *   "status": "CREATING",
 *   "hostId": "vm-ubuntu-test",
 *   "runtime": "DOCKER",
 *   "accessEndpoints": {},
 *   "timestamp": "2026-05-22T10:30:00"
 * }</pre>
 *
 * @see com.devops.dashboard.mcp.handler.EnvironmentHandler
 */
@Getter
@Builder
public class EnvOperationResponse {

    /** 操作是否成功 */
    private final boolean success;

    /** 结果描述消息（成功时的确认信息或失败时的错误原因） */
    private final String message;

    /** 环境 ID（创建后返回） */
    private final String envId;

    /** 环境名称 */
    private final String envName;

    /** 当前环境状态 */
    private final String status;

    /** 目标主机 ID */
    private final String hostId;

    /** 运行时类型 */
    private final String runtime;

    /** 访问端点映射（name -> URL） */
    private final Map<String, String> accessEndpoints;

    /** 服务实例列表摘要 */
    private final java.util.List<ServiceSummary> services;

    /** 环境列表（仅 env_list 使用） */
    private final java.util.List<Map<String, Object>> environments;

    /** 可用宿主机列表（仅 env_list 使用） */
    private final java.util.List<Map<String, Object>> availableHosts;

    /** 响应生成时间 */
    private final LocalDateTime timestamp;

    /**
     * 服务实例摘要内部类。
     */
    @Getter
    @Builder
    public static class ServiceSummary {
        private final String instanceId;
        private final String templateName;
        private final String status;
    }

    /**
     * 创建成功响应的工厂方法。
     *
     * @param message 成功消息
     * @return 带有 success=true 的响应构建器
     */
    public static EnvOperationResponse.EnvOperationResponseBuilder success(String message) {
        return EnvOperationResponse.builder()
                .success(true)
                .message(message)
                .timestamp(LocalDateTime.now());
    }

    /**
     * 创建失败响应的工厂方法。
     *
     * @param message 错误消息
     * @return 带有 success=false 的响应构建器
     */
    public static EnvOperationResponse.EnvOperationResponseBuilder failure(String message) {
        return EnvOperationResponse.builder()
                .success(false)
                .message(message)
                .timestamp(LocalDateTime.now());
    }
}
