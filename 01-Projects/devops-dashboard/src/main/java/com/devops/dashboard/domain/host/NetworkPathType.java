package com.devops.dashboard.domain.host;

/**
 * 网络路径类型枚举。
 *
 * <p>定义两台主机之间的网络拓扑关系分类，从物理距离和网络层级角度描述通信路径特征。
 * 用于压测场景下的网络质量预判和结果可信度标注。</p>
 *
 * <h3>V2 设计文档关联</h3>
 * <p>对应 V2 设计文档中 <em>"网络路径分析 → path_type"</em> 定义，
 * 是 {@link NetworkPath} 值对象的核心属性之一。</p>
 *
 * <h3>可信度等级说明</h3>
 * <table border="1">
 *   <tr><th>类型</th><th>网络延迟特征</th><th>压测结果可信度</th></tr>
 *   <tr><td>{@link #SAME_HOST}</td><td>回环接口，几乎无延迟</td><td>极低（⚠️）— 不代表真实网络</td></tr>
 *   <tr><td>{@link #SAME_HYPERVISOR}</td><td>虚拟交换机内部转发</td><td>低（⚠️）— 缺少物理网卡开销</td></tr>
 *   <tr><td>#SAME_LAN</td><td>局域网内 1~2 跳</td><td>高（✅）— 接近生产环境</td></tr>
 *   <tr><td>{@link #WAN}</td><td>跨网段/跨地域多跳</td><td>中（⚠️）— 受公网波动影响</td></tr>
 * </table>
 */
public enum NetworkPathType {

    /**
     * 同机通信 — 源与目标在同一台主机上。
     *
     * <p>流量经回环接口（lo）转发，无真实网卡参与。
     * 压测结果<strong>不可作为性能基准</strong>，仅适用于功能验证。</p>
     */
    SAME_HOST("same-host", "同机", "⚠️ 极低"),

    /**
     * 同虚拟化宿主机 — 源与目标 VM 运行在同一台 PVE hypervisor 上。
     *
     * <p>流量通过虚拟交换机（linux bridge / OVS）在宿主机内部转发，
     * 不经过物理网卡。延迟低但缺少真实网络栈的开销。</p>
     */
    SAME_HYPERVISOR("same-hypervisor", "同虚拟化宿主机", "⚠️ 低"),

    /**
     * 同局域网 — 源与目标在同一 L2/L3 网络内。
     *
     * <p>流量经过物理网卡和交换机，是<strong>最接近生产环境</strong>的测试拓扑。
     * 压测结果具有较高参考价值。</p>
     */
    SAME_LAN("same-lan", "同局域网", "✅ 高"),

    /**
     * 跨广域网 — 源与目标位于不同网络区域或地理位置。
     *
     * <p>流量经过路由器、防火墙甚至互联网，受带宽限制和网络拥塞影响较大。
     * 结果需结合具体网络条件解读。</p>
     */
    WAN("wan", "跨广域网", "⚠️ 中");

    /** 类型编码，用于序列化和持久化。 */
    private final String code;

    /** 中文显示名称，用于 UI 展示和报告生成。 */
    private final String displayName;

    /** 压测结果可信度标识，含 emoji 直观展示等级。 */
    private final String credibility;

    /**
     * 私有构造器，绑定编码、显示名称和可信度标识。
     *
     * @param code        类型编码
     * @param displayName 显示名称
     * @param credibility 可信度标识
     */
    NetworkPathType(String code, String displayName, String credibility) {
        this.code = code;
        this.displayName = displayName;
        this.credibility = credibility;
    }

    /**
     * 获取类型编码。
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
     * 获取压测结果可信度标识。
     *
     * @return 含 emoji 的可信度描述字符串
     */
    public String getCredibility() {
        return credibility;
    }
}
