package com.devops.dashboard.domain.exception.host;

import com.devops.dashboard.domain.exception.shared.SharedException;

/**
 * 主机未找到异常，当请求的主机 ID 在系统中不存在时抛出。
 *
 * <h3>触发条件</h3>
 * <ul>
 *   <li>通过 {@code hostId} 查询主机时，数据库/缓存中无匹配记录</li>
 *   <li>尝试对已删除的主机执行操作（逻辑删除场景）</li>
 *   <li>MCP 工具调用时传入的 hostId 参数无效或已过期</li>
 * </ul>
 *
 * <h3>MCP Error Code 映射</h3>
 * <p>映射为 {@code HOST_NOT_FOUND} 错误码，HTTP 状态码 404。
 *
 * <h3>用户提示信息</h3>
 * <p>"主机 {hostId} 不存在，请检查主机 ID 是否正确，或先注册该主机。"
 */
public class HostNotFoundException extends SharedException {

    /**
     * 未找到的主机 ID
     *
     * <p>诊断作用：用于定位是哪个主机的查询失败了，
     * 可直接在日志中搜索此 ID 追踪完整调用链。
     */
    private final String hostId;

    /**
     * 构建主机未找到异常
     *
     * @param hostId 未找到的主机标识符
     */
    public HostNotFoundException(String hostId) {
        super("Host not found: " + hostId);
        this.hostId = hostId;
    }

    /**
     * 获取未找到的主机 ID
     *
     * @return 主机标识符
     */
    public String getHostId() {
        return hostId;
    }
}
