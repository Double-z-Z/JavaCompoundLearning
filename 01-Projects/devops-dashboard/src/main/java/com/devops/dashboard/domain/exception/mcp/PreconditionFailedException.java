package com.devops.dashboard.domain.exception.mcp;

import com.devops.dashboard.domain.exception.shared.SharedException;

import java.util.List;

/**
 * 部署前置条件校验失败异常。
 *
 * <p>在 {@code deploy_pipeline} 执行前进行 fail-fast 校验时抛出，
 * 携带可操作的下一步建议（MCP tool 名称列表），让 AI 知道该调用哪个工具。</p>
 */
public class PreconditionFailedException extends SharedException {

    private final String reason;
    private final List<String> nextSteps;

    public PreconditionFailedException(String reason, List<String> nextSteps) {
        super(reason);
        this.reason = reason;
        this.nextSteps = nextSteps;
    }

    public String getReason() {
        return reason;
    }

    public List<String> getNextSteps() {
        return nextSteps;
    }
}
