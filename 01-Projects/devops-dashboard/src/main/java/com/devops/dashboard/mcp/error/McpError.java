package com.devops.dashboard.mcp.error;

import com.devops.dashboard.domain.exception.host.HostCapabilityMismatchException;
import com.devops.dashboard.domain.exception.host.HostNotFoundException;
import com.devops.dashboard.domain.exception.host.InvalidHostRoleException;
import com.devops.dashboard.domain.exception.shared.SharedException;

/**
 * MCP 协议层的标准化错误记录。
 *
 * <p>作为领域异常（{@link SharedException}）与 HTTP 响应之间的桥梁，
 * 将业务语义错误转换为 MCP 客户端可消费的结构化格式。每个字段职责明确：</p>
 *
 * <table border="1" summary="字段说明">
 *   <tr><th>字段</th><th>含义</th></tr>
 *   <tr><td>{@code code}</td><td>机器可读的错误分类标识，用于客户端程序化判断</td></tr>
 *   <tr><td>{@code message}</td><td>原始异常信息，供日志和调试使用</td></tr>
 *   <tr><td>{@code httpStatus}</td><td>对应的 HTTP 状态码，保持 REST 语义一致性</td></tr>
 *   <tr><td>{@code userHint}</td><td>面向用户的中文提示，指导下一步操作</td></tr>
 * </table>
 *
 * <h3>fromDomainException 映射表</h3>
 * <table border="1">
 *   <tr><th>领域异常</th><th>code</th><th>httpStatus</th></tr>
 *   <tr><td>{@link HostNotFoundException}</td><td>HOST_NOT_FOUND</td><td>404</td></tr>
 *   <tr><td>{@link InvalidHostRoleException}</td><td>INVALID_HOST_ROLE</td><td>400</td></tr>
 *   <tr><td>{@link HostCapabilityMismatchException}</td><td>HOST_CAPABILITY_MISMATCH</td><td>400</td></tr>
 *   <tr><td>其他 {@link SharedException}</td><td>DOMAIN_ERROR</td><td>500</td></tr>
 * </table>
 *
 * @param code     错误分类码，如 {@code HOST_NOT_FOUND}
 * @param message  原始异常消息
 * @param httpStatus HTTP 状态码
 * @param userHint 面向用户的操作建议
 */
public record McpError(
        String code,
        String message,
        int httpStatus,
        String userHint) {

    /**
     * 将领域层异常转换为 MCP 标准错误记录。
     *
     * <p>按异常类型进行模式匹配，未识别的 {@link SharedException} 统一映射为
     * {@code DOMAIN_ERROR}(500)。新增领域异常时需在此方法中补充映射分支。</p>
     *
     * @param ex 领域层异常，不能为 null
     * @return 对应的 {@link McpError} 实例，httpStatus 和 userHint 已根据异常类型填充
     */
    public static McpError fromDomainException(SharedException ex) {
        if (ex instanceof HostNotFoundException e) {
            return new McpError(
                    "HOST_NOT_FOUND",
                    ex.getMessage(),
                    404,
                    "指定的主机不存在，请通过 hosts://topology 查询可用节点"
            );
        }
        if (ex instanceof InvalidHostRoleException e) {
            return new McpError(
                    "INVALID_HOST_ROLE",
                    ex.getMessage(),
                    400,
                    "主机角色不满足要求，需要 " + (e.getExpectedRole() != null ? e.getExpectedRole().name() : "指定") + " 角色"
            );
        }
        if (ex instanceof HostCapabilityMismatchException e) {
            return new McpError(
                    "HOST_CAPABILITY_MISMATCH",
                    ex.getMessage(),
                    400,
                    "主机不支持该能力，需要 " + (e.getRequiredCapability() != null ? e.getRequiredCapability().name() : "指定") + " 能力"
            );
        }
        return new McpError(
                "DOMAIN_ERROR",
                ex.getMessage(),
                500,
                "领域层错误: " + ex.getMessage()
        );
    }
}
