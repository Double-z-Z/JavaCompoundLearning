package com.devops.dashboard.mcp.error;

import com.devops.dashboard.domain.exception.host.HostCapabilityMismatchException;
import com.devops.dashboard.domain.exception.host.HostNotFoundException;
import com.devops.dashboard.domain.exception.host.InvalidHostRoleException;
import com.devops.dashboard.domain.exception.shared.SharedException;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MCP 协议层的标准化错误记录（V3 版本）。
 *
 * <p>相比 V2 的变化：
 * <ul>
 *   <li>新增 {@code data} 字段，包含 forbidden、nextSteps、suggestion 等反退化约束</li>
 *   <li>新增错误码：SERVICE_NOT_REGISTERED、INVALID_ENVIRONMENT_STATUS、ENVIRONMENT_LOCKED</li>
 *   <li>新增工厂方法用于构造 V3 统一错误格式</li>
 * </ul>
 *
 * <h3>V3 错误格式</h3>
 * <pre>{@code
 * {
 *   "code": "INVALID_ENVIRONMENT_STATUS",
 *   "message": "当前状态为 CREATING，不允许 DEPLOYING 操作",
 *   "httpStatus": 400,
 *   "data": {
 *     "currentStatus": "CREATING",
 *     "requiredStatus": "READY",
 *     "suggestion": "调用 env_list 确认状态，或 env_get_logs 排查",
 *     "forbidden": "禁止通过 SSH 进入容器手动部署",
 *     "nextSteps": ["env_list", "env_get_logs"]
 *   }
 * }
 * }</pre>
 *
 * @param code        错误分类码
 * @param message    原始异常消息
 * @param httpStatus HTTP 状态码
 * @param data       V3 错误数据（含 forbidden/nextSteps 等）
 */
public record McpError(
        String code,
        String message,
        int httpStatus,
        Map<String, Object> data) {

    /**
     * 简化的构造方法（无 data 字段，用于兼容旧代码）。
     */
    public McpError(String code, String message, int httpStatus, String userHint) {
        this(code, message, httpStatus, Map.of("suggestion", userHint));
    }

    /**
     * 将领域层异常转换为 MCP 标准错误记录。
     */
    public static McpError fromDomainException(SharedException ex) {
        if (ex instanceof HostNotFoundException e) {
            return new McpError(
                    "HOST_NOT_FOUND",
                    ex.getMessage(),
                    404,
                    Map.of(
                        "suggestion", "调用 env_list 查看可用宿主机池",
                        "forbidden", "禁止手动指定未注册节点",
                        "nextSteps", List.of("env_list")
                    )
            );
        }
        if (ex instanceof InvalidHostRoleException e) {
            return new McpError(
                    "INVALID_HOST_ROLE",
                    ex.getMessage(),
                    400,
                    Map.of(
                        "suggestion", "从 env_list 的 availableHosts 中选择 roles 包含 target 的节点",
                        "forbidden", "禁止向非 TARGET 节点部署服务",
                        "nextSteps", List.of("env_list")
                    )
            );
        }
        if (ex instanceof HostCapabilityMismatchException e) {
            return new McpError(
                    "HOST_CAPABILITY_MISMATCH",
                    ex.getMessage(),
                    400,
                    Map.of(
                        "suggestion", "主机不支持该能力，需要 " + (e.getRequiredCapability() != null ? e.getRequiredCapability().name() : "指定") + " 能力",
                        "forbidden", "禁止部署到能力不匹配的主机",
                        "nextSteps", List.of("env_list")
                    )
            );
        }
        return new McpError(
                "DOMAIN_ERROR",
                ex.getMessage(),
                500,
                Map.of(
                    "suggestion", "领域层错误: " + ex.getMessage(),
                    "forbidden", "禁止本地执行 docker/ssh/curl 替代",
                    "nextSteps", List.of("env_list", "env_get_logs")
                )
        );
    }

    /**
     * 创建状态机错误（V3 新增）。
     *
     * @param currentStatus  当前状态
     * @param requiredStatus 期望状态
     * @param suggestion     修复建议
     */
    public static McpError forEnvironmentStatusError(
            String currentStatus,
            String requiredStatus,
            String suggestion) {
        return new McpError(
                "INVALID_ENVIRONMENT_STATUS",
                String.format("当前状态为 %s，不允许转换为 %s。必须先 %s", currentStatus, requiredStatus, suggestion),
                400,
                Map.of(
                    "currentStatus", currentStatus,
                    "requiredStatus", requiredStatus,
                    "suggestion", suggestion,
                    "forbidden", "禁止通过 SSH 进入容器手动部署",
                    "nextSteps", List.of("env_list", "env_get_logs")
                )
        );
    }

    /**
     * 创建服务未注册错误（V3 新增）。
     *
     * @param serviceName    未注册的服务名
     * @param availableServices 可用服务列表
     */
    public static McpError forServiceNotRegistered(
            String serviceName,
            Set<String> availableServices) {
        return new McpError(
                "SERVICE_NOT_REGISTERED",
                String.format("服务 '%s' 未在 MCP 目录注册", serviceName),
                400,
                Map.of(
                    "serviceName", serviceName,
                    "availableServices", availableServices,
                    "suggestion", "检查 serviceName 是否拼写正确",
                    "forbidden", "禁止部署未注册服务",
                    "nextSteps", List.of("env_list")
                )
        );
    }

    /**
     * 创建环境锁定错误（V3 新增）。
     *
     * @param envId         被锁定的环境 ID
     * @param currentStatus 当前状态（通常是 DEPLOYING）
     */
    public static McpError forEnvironmentLocked(String envId, String currentStatus) {
        return new McpError(
                "ENVIRONMENT_LOCKED",
                String.format("环境 %s 当前被锁定（%s 进行中）", envId, currentStatus),
                409,
                Map.of(
                    "envId", envId,
                    "currentStatus", currentStatus,
                    "suggestion", "等待当前操作完成，或 30 秒后重试",
                    "forbidden", "禁止并发操作同一环境",
                    "nextSteps", List.of("env_list", "env_get_logs")
                )
        );
    }

    /**
     * 创建通用禁止错误。
     *
     * @param action     被禁止的操作描述
     * @param reason     禁止原因
     * @param nextSteps  建议的下一步
     */
    public static McpError forForbidden(String action, String reason, List<String> nextSteps) {
        return new McpError(
                "FORBIDDEN",
                action + " - " + reason,
                403,
                Map.of(
                    "forbidden", action,
                    "suggestion", reason,
                    "nextSteps", nextSteps
                )
        );
    }
}