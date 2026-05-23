package com.devops.dashboard.domain.host;

import com.devops.dashboard.domain.loadgen.LoadgenTool;

import java.util.Collections;
import java.util.Set;

/**
 * 主机聚合根实体（Aggregate Root）。
 *
 * <p>作为 Host 领域模型的核心实体，聚合并管理主机的全部身份信息、拓扑关系和运行属性。
 * 是 DDD 分层架构中领域层的核心概念，承载业务规则和不变量约束。</p>
 *
 * <h3>聚合边界</h3>
 * <p>本实体为聚合根，以下值对象/枚举构成其内部聚合：
 * <ul>
 *   <li>{@link HostId} — 身份标识（聚合根 ID）</li>
 *   <li>{@link HostType} — 物理形态分类</li>
 *   <li>{@link HostRole} — 职能角色集合</li>
 *   <li>{@link Capability} — 运行能力集合</li>
 *   <li>{@link Resources} — 资源容量快照</li>
 *   <li>{@link HostAccess} — SSH 访问配置</li>
 * </ul></p>
 *
 * <h3>设计约束</h3>
 * <ul>
 *   <li><b>不可变性</b>：所有字段 {@code final}，通过 {@link Builder} 创建后不可修改，
 *       任何状态变更需创建新实例（事件溯源友好）</li>
 *   <li><b>自验证</b>：构建时强制校验 {@code id}、{@code type}、{@code label} 为必填项</li>
 *   <li><b>集合防御</b>：{@code capabilities}、{@code roles}、{@code loadgenTools}
 *       在构造时包装为不可修改视图，防止外部篡改</li>
 *   <li><b>父子拓扑</b>：通过 {@code parentId} 与父节点建立树形结构，
 *       {@link #isSibling(Host)} 支持兄弟节点判断</li>
 * </ul>
 *
 * <h3>V2 设计文档关联</h3>
 * <p>对应 V2 设计文档中 <em>"Host 实体定义"</em> 章节，
 * 映射 YAML 配置文件中的完整主机条目。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * Host host = Host.builder()
 *     .id(HostId.of("prod-web-01"))
 *     .type(HostType.VM)
 *     .parentId(HostId.of("pve-node-1"))
 *     .label("Production Web Server")
 *     .networkZone("prod-lan")
 *     .capabilities(Set.of(Capability.DOCKER))
 *     .roles(Set.of(HostRole.TARGET))
 *     .resources(Resources.builder().cpuTotal(4).cpuFree(2)
 *         .memTotalMb(8192).memFreeMb(4096).build())
 *     .access(HostAccess.builder().sshHost("10.0.0.101")
 *         .user("deploy").keyPath("/home/deploy/.ssh/id_ed25519").build())
 *     .loadgenTools(Set.of(LoadgenTool.WRK))
 *     .build();
 *
 * boolean isTarget = host.isTarget();          // true
 * boolean canDocker = host.supportsDocker();    // true
 * boolean isLeaf = host.isRoot();               // false
 * }</pre>
 */
public class Host {

    /** 主机唯一标识，作为聚合根的身份锚点。 */
    private final HostId id;

    /** 主机物理/虚拟形态类型。 */
    private final HostType type;

    /** 父节点标识（仅 VM 类型有值，hypervisor 和物理机为 null）。 */
    private final HostId parentId;

    /** 可读标签，用于 UI 展示和人机交互。 */
    private final String label;

    /** 所属网络区域标识，用于网络路径分析。 */
    private final String networkZone;

    /** 支持的运行能力集合（不可变视图）。 */
    private final Set<Capability> capabilities;

    /** 承担的职能角色集合（不可变视图）。 */
    private final Set<HostRole> roles;

    /** 资源容量快照。 */
    private final Resources resources;

    /** SSH 访问配置。 */
    private final HostAccess access;

    /** 已安装的压测工具集合（不可变视图）。 */
    private final Set<LoadgenTool> loadgenTools;

    /**
     * 私有构造器，仅由 {@link Builder#build()} 调用。
     *
     * @param builder 已完成字段填充的构建器
     */
    private Host(Builder builder) {
        this.id = builder.id;
        this.type = builder.type;
        this.parentId = builder.parentId;
        this.label = builder.label;
        this.networkZone = builder.networkZone;
        this.capabilities = builder.capabilities != null ?
                Collections.unmodifiableSet(builder.capabilities) : Set.of();
        this.roles = builder.roles != null ?
                Collections.unmodifiableSet(builder.roles) : Set.of();
        this.resources = builder.resources;
        this.access = builder.access;
        this.loadgenTools = builder.loadgenTools != null ?
                Collections.unmodifiableSet(builder.loadgenTools) : Set.of();
    }

    /**
     * 获取主机唯一标识。
     *
     * @return 主机 ID 值对象
     */
    public HostId getId() {
        return id;
    }

    /**
     * 获取主机类型。
     *
     * @return 物理形态分类枚举
     */
    public HostType getType() {
        return type;
    }

    /**
     * 获取父节点标识。
     *
     * <p>仅当本主机为虚拟机且有宿主机父节点时有值，
     * hypervisor 和物理机的此字段为 {@code null}。</p>
     *
     * @return 父节点 ID，若为顶层节点则返回 {@code null}
     */
    public HostId getParentId() {
        return parentId;
    }

    /**
     * 获取可读标签。
     *
     * @return 标签字符串
     */
    public String getLabel() {
        return label;
    }

    /**
     * 获取所属网络区域。
     *
     * @return 网络区域标识字符串
     */
    public String getNetworkZone() {
        return networkZone;
    }

    /**
     * 获取支持的运行能力集合。
     *
     * @return 能力集合的不可变视图，永不为 {@code null}
     */
    public Set<Capability> getCapabilities() {
        return capabilities;
    }

    /**
     * 获取承担的角色集合。
     *
     * @return 角色集合的不可变视图，永不为 {@code null}
     */
    public Set<HostRole> getRoles() {
        return roles;
    }

    /**
     * 获取资源容量快照。
     *
     * @return 资源值对象，可能为 {@code null}
     */
    public Resources getResources() {
        return resources;
    }

    /**
     * 获取 SSH 访问配置。
     *
     * @return 访问信息值对象，可能为 {@code null}
     */
    public HostAccess getAccess() {
        return access;
    }

    /**
     * 获取已安装的压测工具集合。
     *
     * @return 工具集合的不可变视图，永不为 {@code null}
     */
    public Set<LoadgenTool> getLoadgenTools() {
        return loadgenTools;
    }

    /**
     * 判断是否具备部署目标角色。
     *
     * @return 若角色中包含 {@link HostRole#TARGET} 则返回 {@code true}
     */
    public boolean isTarget() {
        return roles.contains(HostRole.TARGET);
    }

    /**
     * 判断是否具备压测执行角色。
     *
     * @return 若角色中包含 {@link HostRole#LOADGEN} 则返回 {@code true}
     */
    public boolean isLoadgen() {
        return roles.contains(HostRole.LOADGEN);
    }

    /**
     * 判断是否具备 MCP Server 运行角色。
     *
     * @return 若角色中包含 {@link HostRole#MCP_HOST} 则返回 {@code true}
     */
    public boolean isMcpHost() {
        return roles.contains(HostRole.MCP_HOST);
    }

    /**
     * 判断是否支持 Docker 容器化部署。
     *
     * @return 若能力中包含 {@link Capability#DOCKER} 则返回 {@code true}
     */
    public boolean supportsDocker() {
        return capabilities.contains(Capability.DOCKER);
    }

    /**
     * 判断是否支持原生进程部署。
     *
     * @return 若能力中包含 {@link Capability#NATIVE} 则返回 {@code true}
     */
    public boolean supportsNative() {
        return capabilities.contains(Capability.NATIVE);
    }

    /**
     * 判断是否安装了指定压测工具。
     *
     * @param tool 待查询的压测工具
     * @return 若该工具已在工具列表中则返回 {@code true}
     */
    public boolean hasLoadgenTool(LoadgenTool tool) {
        return loadgenTools != null && loadgenTools.contains(tool);
    }

    /**
     * 判断是否为拓扑树的根节点（无父节点）。
     *
     * <p>根节点通常是 hypervisor 或物理机。</p>
     *
     * @return 若 {@code parentId} 为 {@code null} 则返回 {@code true}
     */
    public boolean isRoot() {
        return parentId == null;
    }

    /**
     * 判断两台主机是否为兄弟节点（共享同一父节点）。
     *
     * <p>任一方无父节点则返回 {@code false}。</p>
     *
     * @param another 另一台主机
     * @return 若两者 {@code parentId} 相等且均非空则返回 {@code true}
     */
    public boolean isSibling(Host another) {
        if (this.parentId == null || another.parentId == null) return false;
        return this.parentId.equals(another.parentId);
    }

    /**
     * 创建新的 {@link Builder} 实例。
     *
     * @return 空状态的构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * {@code Host} 的流式构建器。
     *
     * <p>必填字段：{@code id}、{@code type}、{@code label}。
     * 其余字段均为可选，未设置时使用默认值（null 或空集合）。</p>
     */
    public static class Builder {

        /** 主机 ID（必填）。 */
        private HostId id;

        /** 主机类型（必填）。 */
        private HostType type;

        /** 父节点 ID（可选）。 */
        private HostId parentId;

        /** 标签（必填）。 */
        private String label;

        /** 网络区域（可选）。 */
        private String networkZone;

        /** 能力集合（可选）。 */
        private Set<Capability> capabilities;

        /** 角色集合（可选）。 */
        private Set<HostRole> roles;

        /** 资源容量（可选）。 */
        private Resources resources;

        /** SSH 访问（可选）。 */
        private HostAccess access;

        /** 压测工具集合（可选）。 */
        private Set<LoadgenTool> loadgenTools;

        /**
         * 设置主机 ID。
         *
         * @param id 主机标识值对象（必填）
         * @return 当前构建器，支持链式调用
         */
        public Builder id(HostId id) {
            this.id = id;
            return this;
        }

        /**
         * 设置主机类型。
         *
         * @param type 物理形态分类（必填）
         * @return 当前构建器，支持链式调用
         */
        public Builder type(HostType type) {
            this.type = type;
            return this;
        }

        /**
         * 设置父节点 ID。
         *
         * @param parentId 父节点标识（VM 类型通常需要设置）
         * @return 当前构建器，支持链式调用
         */
        public Builder parentId(HostId parentId) {
            this.parentId = parentId;
            return this;
        }

        /**
         * 设置可读标签。
         *
         * @param label 显示标签（必填，不允许空白）
         * @return 当前构建器，支持链式调用
         */
        public Builder label(String label) {
            this.label = label;
            return this;
        }

        /**
         * 设置所属网络区域。
         *
         * @param networkZone 网络区域标识
         * @return 当前构建器，支持链式调用
         */
        public Builder networkZone(String networkZone) {
            this.networkZone = networkZone;
            return this;
        }

        /**
         * 设置运行能力集合。
         *
         * @param capabilities 能力枚举集合
         * @return 当前构建器，支持链式调用
         */
        public Builder capabilities(Set<Capability> capabilities) {
            this.capabilities = capabilities;
            return this;
        }

        /**
         * 设置职能角色集合。
         *
         * @param roles 角色枚举集合
         * @return 当前构建器，支持链式调用
         */
        public Builder roles(Set<HostRole> roles) {
            this.roles = roles;
            return this;
        }

        /**
         * 设置资源容量快照。
         *
         * @param resources 资源值对象
         * @return 当前构建器，支持链式调用
         */
        public Builder resources(Resources resources) {
            this.resources = resources;
            return this;
        }

        /**
         * 设置 SSH 访问配置。
         *
         * @param access 访问信息值对象
         * @return 当前构建器，支持链式调用
         */
        public Builder access(HostAccess access) {
            this.access = access;
            return this;
        }

        /**
         * 设置已安装的压测工具集合。
         *
         * @param loadgenTools 压测工具枚举集合
         * @return 当前构建器，支持链式调用
         */
        public Builder loadgenTools(Set<LoadgenTool> loadgenTools) {
            this.loadgenTools = loadgenTools;
            return this;
        }

        /**
         * 构建 {@code Host} 聚合根实例。
         *
         * <p>校验必填字段后创建不可变实体。集合字段自动包装为不可修改视图。</p>
         *
         * @return 构建完成的 {@code Host} 聚合根
         * @throws IllegalArgumentException 当必填字段缺失或无效时抛出
         */
        public Host build() {
            if (id == null) {
                throw new IllegalArgumentException("Host ID is required");
            }
            if (type == null) {
                throw new IllegalArgumentException("Host type is required");
            }
            if (label == null || label.isBlank()) {
                throw new IllegalArgumentException("Host label is required");
            }
            return new Host(this);
        }
    }
}
