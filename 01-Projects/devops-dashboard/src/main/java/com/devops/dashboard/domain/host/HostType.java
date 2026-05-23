package com.devops.dashboard.domain.host;

/**
 * 主机类型枚举。
 *
 * <p>定义基础设施中主机的物理/虚拟形态分类，用于区分宿主机、虚拟机、物理机等部署形态。
 * 作为 {@link Host} 聚合根的类型维度属性，影响拓扑展示和调度策略。</p>
 *
 * <h3>V2 设计文档关联</h3>
 * <p>对应 V2 设计文档中 <em>"Host 基础信息 → type"</em> 字段定义，
 * 支持从配置文件的 code 字符串反序列化。</p>
 *
 * <h3>枚举值说明</h3>
 * <table border="1">
 *   <tr><th>枚举值</th><th>适用场景</th></tr>
 *   <tr><td>{@link #PVE_HYPERVISOR}</td><td>Proxmox VE 虚拟化平台宿主机，承载虚拟机实例</td></tr>
 *   <tr><td>{@link #VM}</td><td>运行在 hypervisor 上的虚拟机实例</td></tr>
 *   <tr><td>{@link #BARE_METAL}</td><td>独立物理服务器，无虚拟化层</td></tr>
 *   <tr><td>{@link #LOCAL}</td><td>开发者本地机器，用于开发调试场景</td></tr>
 * </table>
 */
public enum HostType {

    /** PVE 虚拟化宿主机，运行 Proxmox VE 平台，作为虚拟机的父节点。 */
    PVE_HYPERVISOR("pve-hypervisor", "PVE 虚拟化宿主机"),

    /** 虚拟机实例，由 hypervisor 托管，是实际的工作负载载体。 */
    VM("vm", "虚拟机"),

    /** 物理机（裸金属），直接运行在硬件上，无虚拟化层开销。 */
    BARE_METAL("bare-metal", "物理机"),

    /** 本地机器，通常指开发者工作站或笔记本，用于开发与调试。 */
    LOCAL("local", "本地机器");

    /** 类型编码，用于序列化和配置文件映射。 */
    private final String code;

    /** 可读显示名称，用于 UI 展示。 */
    private final String displayName;

    /**
     * 私有构造器，绑定编码与显示名称。
     *
     * @param code       类型编码
     * @param displayName 显示名称
     */
    HostType(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    /**
     * 获取类型编码。
     *
     * @return 小写英文编码字符串
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
     * @param code 类型编码，如 {@code "vm"} 或 {@code "VM"}
     * @return 匹配的 {@code HostType} 枚举值
     * @throws IllegalArgumentException 当编码无法匹配任何已知类型时抛出
     */
    public static HostType fromCode(String code) {
        for (HostType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown HostType code: " + code);
    }
}
