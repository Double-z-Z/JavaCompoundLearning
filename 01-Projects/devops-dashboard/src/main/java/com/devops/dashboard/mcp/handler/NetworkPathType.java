package com.devops.dashboard.mcp.handler;

/**
 * 网络路径类型枚举。
 *
 * <p>定义源主机到目标主机之间的网络路径分类，用于网络路径分析 Tool
 * ({@code analyze_network_path}) 的结果输出。</p>
 */
enum NetworkPathType {

    SAME_HOST("same-host", "同机", "low"),
    SAME_HYPERVISOR("same-hypervisor", "同一宿主机", "high"),
    SAME_LAN("same-lan", "同一局域网", "high"),
    WAN("wan", "跨广域网", "medium");

    private final String code;
    private final String displayName;
    private final String credibility;

    NetworkPathType(String code, String displayName, String credibility) {
        this.code = code;
        this.displayName = displayName;
        this.credibility = credibility;
    }

    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }
    public String getCredibility() { return credibility; }
}
