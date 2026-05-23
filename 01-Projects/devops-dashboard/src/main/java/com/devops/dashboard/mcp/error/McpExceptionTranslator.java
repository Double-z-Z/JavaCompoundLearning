package com.devops.dashboard.mcp.error;

import com.devops.dashboard.domain.exception.shared.SharedException;
import org.springframework.stereotype.Component;

/**
 * 异常翻译器：将 Java 异常层次结构映射为 {@link McpError} 标准格式。
 *
 * <h3>翻译策略</h3>
 * <p>采用<strong>优先级匹配</strong>策略：从最具体的领域异常开始逐层降级，
 * 确保业务语义不丢失。任何异常最终都会被捕获并转换为 {@link McpError}，
 * 不会向客户端泄露原始异常堆栈。</p>
 *
 * <h3>支持的异常类型层次（优先级从高到低）</h3>
 * <ol>
 *   <li>{@link SharedException} — 领域层业务异常，委托 {@link McpError#fromDomainException} 细分</li>
 *   <li>{@link IllegalArgumentException} — 参数校验失败，映射为 400 + INVALID_PARAMS</li>
 *   <li>{@link IllegalStateException} — 内部状态不一致，映射为 500 + INTERNAL_ERROR</li>
 *   <li>其他 {@link Exception} — 兜底处理，映射为 500 + UNKNOWN_ERROR</li>
 * </ol>
 *
 * <p><strong>设计意图</strong>：MCP 层不应感知领域异常的具体子类型，
 * 所有异常分类逻辑收敛在 {@link McpError} 和本类中，Handler 层只需调用
 * {@code translate()} 即可获得标准化错误响应。</p>
 */
@Component
public class McpExceptionTranslator {

    /**
     * 将任意 Java 异常翻译为 MCP 标准错误记录。
     *
     * @param ex 待翻译的异常实例，不能为 null
     * @return 永远非 null 的 {@link McpError}，包含 code / message / httpStatus / userHint
     */
    public McpError translate(Exception ex) {
        if (ex instanceof SharedException domainEx) {
            return McpError.fromDomainException(domainEx);
        }
        if (ex instanceof IllegalArgumentException) {
            return new McpError(
                    "INVALID_PARAMS",
                    ex.getMessage(),
                    400,
                    "参数错误: " + ex.getMessage()
            );
        }
        if (ex instanceof IllegalStateException) {
            return new McpError(
                    "INTERNAL_ERROR",
                    ex.getMessage(),
                    500,
                    "内部状态异常: " + ex.getMessage()
            );
        }

        return new McpError(
                "UNKNOWN_ERROR",
                ex.getMessage(),
                500,
                "未知错误: " + ex.getClass().getSimpleName()
        );
    }
}
