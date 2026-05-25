package com.devops.dashboard.domain.host;

/**
 * 主机运行时健康状态。
 *
 * <p>表示通过 SSH 探活获得的宿主机连通性状态，由 {@code HostHealthChecker} 定期更新。</p>
 *
 * <h3>状态语义</h3>
 * <ul>
 *   <li>{@link #UNKNOWN} — 从未探测或无法获取主机信息</li>
 *   <li>{@link #UNREACHABLE} — SSH 连接不通，主机可能关机或网络异常</li>
 *   <li>{@link #DEGRADED} — SSH 通但部分能力不可用</li>
 *   <li>{@link #HEALTHY} — SSH 通且所有能力正常</li>
 *   <li>{@link #UNHEALTHY} — 保留兼容，等同于 UNREACHABLE</li>
 * </ul>
 */
public enum HostHealthStatus {
    UNKNOWN,
    UNREACHABLE,
    DEGRADED,
    HEALTHY,
    UNHEALTHY
}
