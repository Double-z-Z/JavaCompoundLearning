package com.devops.dashboard.domain.host;

/**
 * 运行能力枚举。
 *
 * <p>定义主机支持的应用运行方式，决定在该主机上可以部署何种形态的服务。
 * 能力由基础设施配置决定（如是否安装 Docker），属于主机的静态属性。</p>
 *
 * <h3>V2 设计文档关联</h3>
 * <p>对应 V2 设计文档中 <em>"Host 能力定义"</em> 章节，
 * 与 {@link Host#getCapabilities()} 形成聚合根的能力集合属性。</p>
 *
 * <h3>能力与角色的协作</h3>
 * <ul>
 *   <li>{@link #DOCKER} — 支持容器化部署，应用以容器形式运行</li>
 *   <li>{@link #NATIVE} — 支持原生进程部署，应用直接运行在操作系统上</li>
 *   <li>{@link #VM} — 具备虚拟机管理能力（仅限 hypervisor 类型主机）</li>
 * </ul>
 *
 * <p>能力与角色正交：一台 {@link HostRole#TARGET} 节点可能同时具备
 * {@link #DOCKER} 和 {@link #NATIVE} 能力。</p>
 */
public enum Capability {

    /**
     * Docker 容器化能力。
     *
     * <p>主机上已安装 Docker Engine，可通过容器镜像拉取、构建和运行容器化应用。
     * 是现代微服务部署的首选方式。</p>
     */
    DOCKER("docker", "Docker 容器化"),

    /**
     * 原生进程能力。
     *
     * <p>可直接在主机操作系统上运行原生二进制程序或脚本（如 JAR、systemd 服务），
     * 无需容器运行时环境。</p>
     */
    NATIVE("native", "原生进程"),

    /**
     * 虚拟机管理能力。
     *
     * <p>具备创建和管理虚拟机实例的能力，通常仅 {@link HostType#PVE_HYPERVISOR}
     * 类型的主机拥有此能力。</p>
     */
    VM("vm", "虚拟机管理");

    /** 能力编码，用于序列化和配置文件映射。 */
    private final String code;

    /** 可读显示名称，用于 UI 展示。 */
    private final String displayName;

    /**
     * 私有构造器，绑定编码与显示名称。
     *
     * @param code       能力编码
     * @param displayName 显示名称
     */
    Capability(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    /**
     * 获取能力编码。
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
     * @param code 能力编码，如 {@code "docker"} 或 {@code "native"}
     * @return 匹配的 {@code Capability} 枚举值
     * @throws IllegalArgumentException 当编码无法匹配任何已知能力时抛出
     */
    public static Capability fromCode(String code) {
        for (Capability capability : values()) {
            if (capability.code.equalsIgnoreCase(code)) {
                return capability;
            }
        }
        throw new IllegalArgumentException("Unknown Capability code: " + code);
    }
}
