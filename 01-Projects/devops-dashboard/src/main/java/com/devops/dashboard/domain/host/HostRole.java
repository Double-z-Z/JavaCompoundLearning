package com.devops.dashboard.domain.host;

/**
 * 主机角色枚举。
 *
 * <p>定义主机在 DevOps 工作流中的职能角色，一台主机可以承担多个角色（多对多关系）。
 * 角色决定该主机在系统中的行为定位，例如是否可作为压测执行端、MCP Server 运行节点等。</p>
 *
 * <h3>V2 设计文档关联</h3>
 * <p>对应 V2 设计文档中 <em>"Host 角色定义"</em> 章节，
 * 与 {@link Host#getRoles()} 形成聚合根的角色集合属性。</p>
 *
 * <h3>角色关系</h3>
 * <ul>
 *   <li>{@link #MCP_HOST} — 运行 MCP Server 的节点，提供工具调用能力</li>
 *   <li>{@link #TARGET} — 应用部署的目标节点，是被操作的对象</li>
 *   <li>{@link #LOADGEN} — 压测流量生成节点，主动发起负载测试</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 一台主机同时具备 MCP 和 Target 角色
 * Set<HostRole> roles = Set.of(HostRole.MCP_HOST, HostRole.TARGET);
 * Host host = Host.builder()
 *     .id(HostId.of("node-01"))
 *     .type(HostType.VM)
 *     .label("Web Server")
 *     .roles(roles)
 *     .build();
 * }</pre>
 */
public enum HostRole {

    /**
     * MCP Server 运行节点。
     *
     * <p>在此主机上部署并运行 MCP（Model Context Protocol）Server 进程，
     * 通过标准协议对外暴露工具能力供 AI Agent 调用。</p>
     */
    MCP_HOST("mcp-host", "MCP Server 运行节点"),

    /**
     * 部署目标节点。
     *
     * <p>作为应用的实际运行环境，接收来自 CI/CD 流水线或 MCP 操作的部署指令，
     * 是被管理和监控的对象。</p>
     */
    TARGET("target", "部署目标节点"),

    /**
     * 压测执行节点。
     *
     * <p>安装并运行压测工具（如 wrk、k6），向 {@link #TARGET} 节点发送测试流量，
     * 用于性能基准测试和容量评估。</p>
     */
    LOADGEN("loadgen", "压测执行节点");

    /** 角色编码，用于序列化和配置文件映射。 */
    private final String code;

    /** 可读显示名称，用于 UI 展示。 */
    private final String displayName;

    /**
     * 私有构造器，绑定编码与显示名称。
     *
     * @param code       角色编码
     * @param displayName 显示名称
     */
    HostRole(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    /**
     * 获取角色编码。
     *
     * @return 小写英文编码字符串，含连字符
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取显示名称。
     *
     * @return 中文显示名称
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 根据编码字符串查找对应的枚举值（大小写不敏感）。
     *
     * @param code 角色编码，如 {@code "mcp-host"} 或 {@code "target"}
     * @return 匹配的 {@code HostRole} 枚举值
     * @throws IllegalArgumentException 当编码无法匹配任何已知角色时抛出
     */
    public static HostRole fromCode(String code) {
        for (HostRole role : values()) {
            if (role.code.equalsIgnoreCase(code)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown HostRole code: " + code);
    }
}
