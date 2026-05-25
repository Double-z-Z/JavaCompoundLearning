package com.devops.dashboard.mcp.error;

import com.devops.dashboard.domain.exception.mcp.PreconditionFailedException;
import com.devops.dashboard.domain.exception.mcp.ServiceNotRegisteredException;
import com.devops.dashboard.domain.exception.shared.SharedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 异常翻译器：将 Java 异常层次结构映射为 {@link McpError} V3 标准格式。
 *
 * <h3>翻译策略（优先级从高到低）</h3>
 * <ol>
 *   <li>{@link SharedException} — 领域层业务异常</li>
 *   <li>{@link IllegalStateException} — 状态机错误，映射为 INVALID_ENVIRONMENT_STATUS</li>
 *   <li>{@link IllegalArgumentException} — 参数校验失败，映射为 INVALID_PARAMS</li>
 *   <li>其他 {@link Exception} — 兜底处理，映射为 UNKNOWN_ERROR</li>
 * </ol>
 *
 * <h3>V3 变化</h3>
 * <ul>
 *   <li>IllegalStateException 特殊处理：解析状态机错误并返回 V3 格式（含 forbidden/nextSteps）</li>
 *   <li>所有错误通过 {@link McpError} 构造，确保包含 data 字段</li>
 * </ul>
 */
@Component
public class McpExceptionTranslator {

    private static final Logger log = LoggerFactory.getLogger(McpExceptionTranslator.class);

    /**
     * 将任意 Java 异常翻译为 MCP V3 标准错误记录。
     */
    public McpError translate(Exception ex) {
        if (ex instanceof ServiceNotRegisteredException snre) {
            return McpError.forServiceNotRegistered(snre.getServiceName(), snre.getAvailableServices());
        }
        if (ex instanceof PreconditionFailedException pfe) {
            return McpError.forPreconditionFailed(pfe.getReason(), pfe.getNextSteps());
        }
        if (ex instanceof SharedException domainEx) {
            return McpError.fromDomainException(domainEx);
        }
        if (ex instanceof IllegalStateException stateEx) {
            return translateIllegalState(stateEx);
        }
        if (ex instanceof IllegalArgumentException) {
            return new McpError(
                    "INVALID_PARAMS",
                    ex.getMessage(),
                    400,
                    Map.of(
                        "suggestion", "检查参数格式是否正确",
                        "forbidden", "禁止本地执行 docker/ssh/curl 替代",
                        "nextSteps", List.of("env_list")
                    )
            );
        }

        log.error("Unknown exception type: {}", ex.getClass().getName(), ex);
        return new McpError(
                "UNKNOWN_ERROR",
                ex.getMessage(),
                500,
                Map.of(
                    "suggestion", "未知错误，请联系管理员",
                    "forbidden", "禁止本地执行 docker/ssh/curl 替代",
                    "nextSteps", List.of("env_list", "env_get_logs")
                )
        );
    }

    /**
     * 特殊处理 IllegalStateException，解析状态机错误信息。
     *
     * <p>V3 状态机错误消息格式：
     * {@code "当前状态为 XXX，不允许转换为 YYY。必须先 ZZZ。"}
     */
    private McpError translateIllegalState(IllegalStateException ex) {
        String message = ex.getMessage();

        // 尝试解析状态机错误消息
        if (message != null && message.contains("当前状态为")) {
            return parseStateTransitionError(message);
        }

        // 兜底：环境锁定
        if (message != null && (message.contains("被锁定") || message.contains("locked"))) {
            return new McpError(
                    "ENVIRONMENT_LOCKED",
                    message,
                    409,
                    Map.of(
                        "suggestion", "等待当前操作完成，或 30 秒后重试",
                        "forbidden", "禁止并发操作同一环境",
                        "nextSteps", List.of("env_list", "env_get_logs")
                    )
            );
        }

        // 其他 IllegalStateException
        return new McpError(
                "INTERNAL_ERROR",
                message,
                500,
                Map.of(
                    "suggestion", "内部状态异常，请联系管理员",
                    "forbidden", "禁止本地执行 docker/ssh/curl 替代",
                    "nextSteps", List.of("env_list", "env_get_logs")
                )
        );
    }

    /**
     * 解析状态机错误消息，提取 currentStatus、requiredStatus、suggestion。
     *
     * <p>消息格式：{@code "当前状态为 XXX，不允许转换为 YYY。必须先 ZZZ。"}
     */
    private McpError parseStateTransitionError(String message) {
        try {
            // 解析 "当前状态为 XXX"
            String currentStatus = extractBetween(message, "当前状态为 ", "，不允许");
            // 解析 "不允许转换为 YYY"
            String requiredStatus = extractBetween(message, "不允许转换为 ", "。必须先");
            // 解析 "必须先 ZZZ"
            String suggestion = extractAfter(message, "必须先");

            if (currentStatus != null && requiredStatus != null) {
                return McpError.forEnvironmentStatusError(currentStatus, requiredStatus, suggestion);
            }
        } catch (Exception e) {
            log.warn("Failed to parse state transition error message: {}", message);
        }

        // 解析失败，返沪通用状态错误
        return new McpError(
                "INVALID_ENVIRONMENT_STATUS",
                message,
                400,
                Map.of(
                    "suggestion", "调用 env_list 确认状态",
                    "forbidden", "禁止通过 SSH 进入容器手动部署",
                    "nextSteps", List.of("env_list", "env_get_logs")
                )
        );
    }

    /**
     * 提取字符串中两个标记之间的内容。
     */
    private String extractBetween(String text, String start, String end) {
        int startIdx = text.indexOf(start);
        if (startIdx < 0) return null;
        startIdx += start.length();
        int endIdx = text.indexOf(end, startIdx);
        if (endIdx < 0) return null;
        return text.substring(startIdx, endIdx);
    }

    /**
     * 提取字符串中某个标记之后的内容。
     */
    private String extractAfter(String text, String marker) {
        int idx = text.indexOf(marker);
        if (idx < 0) return null;
        return text.substring(idx + marker.length());
    }
}