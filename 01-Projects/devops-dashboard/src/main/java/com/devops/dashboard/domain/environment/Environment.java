package com.devops.dashboard.domain.environment;

import com.devops.dashboard.domain.environment.valueobject.HealthCheckConfig;
import com.devops.dashboard.domain.environment.valueobject.LifecyclePolicy;
import com.devops.dashboard.domain.environment.valueobject.ResourceQuota;
import com.devops.dashboard.domain.host.HostId;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 环境聚合根实体（Aggregate Root）。
 *
 * <p>表示一个独立的运行环境（如开发环境、测试环境、实验环境），
 * 是环境子域的核心聚合根，负责管理环境的生命周期状态、服务实例部署和访问端点。</p>
 *
 * <h3>聚合边界</h3>
 * <p>本聚合包含以下实体和值对象：</p>
 * <ul>
 *   <li>{@link ServiceInstance} — 服务实例（一对多关联，级联全部操作）</li>
 *   <li>{@link TargetNodeRef} — 目标节点引用列表</li>
 *   <li>{@link ResourceQuota} — 资源配额值对象</li>
 *   <li>{@link LifecyclePolicy} — 生命周期策略值对象</li>
 *   <li>{@link HealthCheckConfig} — 健康检查配置值对象</li>
 * </ul>
 *
 * <h3>V2 Phase 2 扩展</h3>
 * <p>新增 {@code hostId} 和 {@code runtime} 字段，实现与 {@link Host} 聚合根的关联，
 * 支持 MCP 环境管理工具对目标主机的校验和能力检查。</p>
 *
 * @see EnvironmentSpec 环境规格说明
 * @see EnvironmentStatus 环境状态机
 * @see HostId 主机标识符
 */
@Entity
@Table(name = "environments", indexes = {
    @Index(name = "idx_env_status", columnList = "status"),
    @Index(name = "idx_env_type", columnList = "type"),
    @Index(name = "idx_env_host", columnList = "host_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Environment {

    @EmbeddedId
    private EnvironmentId id;

    private String name;

    @Enumerated(EnumType.STRING)
    private EnvironmentType type;

    @Enumerated(EnumType.STRING)
    private EnvironmentStatus status;

    private LocalDateTime createdAt;

    /** 目标主机标识符（V2 Phase 2 新增），关联 {@link Host} 聚合根 */
    @Column(name = "host_id")
    private String hostId;

    /** 运行时类型（V2 Phase 2 新增），表示环境的执行载体 */
    @Enumerated(EnumType.STRING)
    private RuntimeType runtime;

    // === 访问端点 ===
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "env_access_endpoints", joinColumns = @JoinColumn(name = "env_id"))
    @MapKeyColumn(name = "endpoint_name")
    @Column(name = "endpoint_url")
    private Map<String, String> accessEndpoints = new HashMap<>();

    // === 值对象 ===
    @Embedded
    private ResourceQuota resourceQuota;

    @Embedded
    private LifecyclePolicy lifecyclePolicy;

    // === 目标节点 ===
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "env_target_nodes", joinColumns = @JoinColumn(name = "env_id"))
    private List<TargetNodeRef> targetNodes = new ArrayList<>();

    // === 服务实例（实体）===
    @OneToMany(mappedBy = "environment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ServiceInstance> services = new ArrayList<>();

    /**
     * 工厂方法：从规格说明创建新环境（初始状态为 CREATING）。
     *
     * <p>V2 Phase 2 扩展：支持通过 {@link EnvironmentSpec} 指定目标主机和运行时类型。</p>
     *
     * @param name 环境名称（用户指定或自动生成）
     * @param spec 环境规格，包含类型、资源配置、主机信息等
     * @return 新创建的环境实例，状态为 {@link EnvironmentStatus#CREATING}
     */
    public static Environment create(String name, EnvironmentSpec spec) {
        var env = new Environment();
        env.id = EnvironmentId.generate();
        env.name = name;
        env.type = spec.getType();
        env.status = EnvironmentStatus.CREATING;
        env.createdAt = LocalDateTime.now();
        env.resourceQuota = spec.getResourceQuota() != null ? spec.getResourceQuota() : ResourceQuota.development();
        env.lifecyclePolicy = spec.getLifecyclePolicy() != null ? spec.getLifecyclePolicy() : LifecyclePolicy.defaultForDev();
        env.targetNodes = spec.getTargetNodes() != null ? spec.getTargetNodes() : new ArrayList<>();
        env.hostId = spec.getHostId();
        env.runtime = spec.getRuntime() != null ? spec.getRuntime() : RuntimeType.DOCKER;

        if (env.services == null) env.services = new ArrayList<>();
        if (env.accessEndpoints == null) env.accessEndpoints = new HashMap<>();

        return env;
    }

    /**
     * 工厂方法：创建已就绪的环境实例（状态直接为 RUNNING）。
     *
     * <p>用于从外部系统同步已部署环境的状态，跳过 CREATING 阶段。
     * V2 Phase 2 扩展：支持指定目标主机和运行时类型。</p>
     *
     * <p><strong>V3 变更</strong>：此工厂方法直接设置 status 为 RUNNING，
     * 不经过状态转换验证。因为它代表的是"已存在于外部系统中的环境"，不是"新创建的"。</p>
     *
     * @param idValue 环境ID字符串
     * @param spec    环境规格
     * @return 已就绪的环境实例，状态为 {@link EnvironmentStatus#RUNNING}
     */
    public static Environment provisioned(String idValue, EnvironmentSpec spec) {
        var env = new Environment();
        env.id = EnvironmentId.of(idValue);
        env.name = idValue;
        env.type = spec.getType();
        env.status = EnvironmentStatus.RUNNING;  // V3: 直接设置，跳过状态转换验证
        env.createdAt = LocalDateTime.now();
        env.resourceQuota = spec.getResourceQuota() != null ? spec.getResourceQuota() : ResourceQuota.development();
        env.lifecyclePolicy = spec.getLifecyclePolicy() != null ? spec.getLifecyclePolicy() : LifecyclePolicy.defaultForDev();
        env.targetNodes = spec.getTargetNodes() != null ? spec.getTargetNodes() : new ArrayList<>();
        env.hostId = spec.getHostId();
        env.runtime = spec.getRuntime() != null ? spec.getRuntime() : RuntimeType.DOCKER;
        env.services = new ArrayList<>();
        env.accessEndpoints = new HashMap<>();
        return env;
    }

    public EnvironmentStatus getStatus() {
        return status;
    }

    public String getIdValue() {
        return id.getValue();
    }

    /**
     * 标记环境为就绪状态（V3 新增）。
     * 环境创建完成，等待部署。
     */
    public void markAsReady() {
        validateTransition(EnvironmentStatus.READY);
        this.status = EnvironmentStatus.READY;
    }

    /**
     * 标记环境为部署中状态（V3 新增）。
     * 正在部署服务，状态锁定。
     */
    public void markAsDeploying() {
        validateTransition(EnvironmentStatus.DEPLOYING);
        this.status = EnvironmentStatus.DEPLOYING;
    }

    public void markAsRunning(Map<String, String> endpoints) {
        validateTransition(EnvironmentStatus.RUNNING);
        this.status = EnvironmentStatus.RUNNING;
        this.accessEndpoints.putAll(endpoints);
    }

    public void markAsDestroyed() {
        validateTransition(EnvironmentStatus.DESTROYED);
        this.status = EnvironmentStatus.DESTROYED;
    }

    /**
     * 标记环境为异常状态（V3，替代 V2 的 FAILED）。
     */
    public void markAsError() {
        validateTransition(EnvironmentStatus.ERROR);
        this.status = EnvironmentStatus.ERROR;
    }

    /**
     * 修复环境错误，使其回到 READY 状态以便重新部署（V3 新增）。
     */
    public void markAsReadyFromError() {
        validateTransition(EnvironmentStatus.READY);
        this.status = EnvironmentStatus.READY;
    }

    /**
     * 标记环境为已就绪（由外部系统创建时调用）。
     *
     * <p>V3 专用：跳过状态转换验证，直接设置状态。
     * 因为此方法代表的是"外部系统已完成创建，环境已就绪"的场景，不是内部状态机转换。</p>
     *
     * @param endpoints 访问端点映射
     */
    public void markAsReadyFromExternal(Map<String, String> endpoints) {
        // V3: 直接设置状态，跳过 validateTransition，因为这是外部系统创建的环境
        this.status = EnvironmentStatus.READY;
        if (endpoints != null) {
            this.accessEndpoints.putAll(endpoints);
        }
    }

    public void addService(ServiceInstance service) {
        if (this.services == null) {
            this.services = new ArrayList<>();
        }
        this.services.add(service);
        service.setEnvironment(this);
    }

    public Optional<ServiceInstance> findServiceByInstanceId(String instanceId) {
        return services.stream()
            .filter(s -> s.getInstanceId().equals(instanceId))
            .findFirst();
    }

    private void validateTransition(EnvironmentStatus target) {
        if (!this.status.canTransitionTo(target)) {
            throw new IllegalStateException(
                String.format("当前状态为 %s，不允许转换为 %s。必须先%s。",
                    this.status.name(),
                    target.name(),
                    this.status.suggestPrecondition(target))
            );
        }
    }

    /**
     * 获取状态转换错误的详细信息（用于 V3 错误响应）。
     *
     * @param target 目标状态
     * @return 包含当前状态、期望状态、建议的地图
     */
    public Map<String, Object> getTransitionErrorData(EnvironmentStatus target) {
        return Map.of(
            "currentStatus", this.status.name(),
            "requiredStatus", target.name(),
            "suggestion", "调用 env_list 确认状态，或 env_get_logs 排查",
            "forbidden", "禁止通过 SSH 进入容器手动部署",
            "nextSteps", java.util.List.of("env_list", "env_get_logs")
        );
    }
}