package com.devops.dashboard.domain.environment;

import com.devops.dashboard.domain.environment.valueobject.LifecyclePolicy;
import com.devops.dashboard.domain.environment.valueobject.ResourceQuota;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 环境规格说明值对象（Value Object）。
 *
 * <p>封装创建环境所需的全部参数，作为 {@link Environment#create(String, EnvironmentSpec)}
 * 工厂方法的输入。采用 Builder 模式支持灵活组合，所有字段均为可选并提供合理默认值。</p>
 *
 * <h3>V2 Phase 2 扩展字段</h3>
 * <table border="1">
 *   <tr><th>字段</th><th>类型</th><th>说明</th><th>默认值</th></tr>
 *   <tr><td>{@code hostId}</td><td>{@code String}</td><td>目标主机标识符</td><td>{@code null}（本地部署）</td></tr>
 *   <tr><td>{@code isolationType}</td><td>{@link IsolationType}</td><td>隔离类型</td><td>{@link IsolationType#DOCKER}</td></tr>
 * </table>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * EnvironmentSpec spec = EnvironmentSpec.builder()
 *     .environmentType(EnvironmentType.EXPERIMENT)
 *     .hostId("vm-ubuntu-test")
 *     .isolationType(IsolationType.DOCKER)
 *     .resourceQuota(ResourceQuota.development())
 *     .build();
 * }</pre>
 *
 * @see Environment 环境聚合根
 * @see IsolationType 隔离类型枚举
 */
@Getter
@Builder
public class EnvironmentSpec {

    /** 环境类型（必填） */
    private final EnvironmentType environmentType;

    /** 目标节点引用列表，指定环境部署的主机节点 */
    private final List<TargetNodeRef> targetNodes;

    /** 资源配额配置（CPU/内存限制等） */
    private final ResourceQuota resourceQuota;

    /** 生命周期策略（自动销毁时间、健康检查间隔等） */
    private final LifecyclePolicy lifecyclePolicy;

    /** 网络配置（端口映射、DNS 设置等） */
    private final Map<String, String> networkConfig;

    /**
     * 目标主机标识符（V2 Phase 2 新增）。
     *
     * <p>指定环境部署的目标主机，必须对应 {@code hosts.yml} 中定义的有效主机 ID。
     * 为 {@code null} 时表示本地部署或未指定主机。</p>
     */
    private final String hostId;

    /**
     * 隔离类型（V2 Phase 2 新增）。
     *
     * <p>定义环境的运行隔离方式：
     * <ul>
     *   <li>{@link IsolationType#DOCKER} — Docker 容器隔离（默认）</li>
     *   <li>{@link IsolationType#NATIVE} — 原生进程隔离</li>
     * </ul>
     * 选择 {@code DOCKER} 时，目标主机必须具备 {@code docker} 能力。</p>
     */
    private final IsolationType isolationType;

    /** 运行时版本约束（可选），如 "docker:26.0" */
    private final String runtimeConstraint;

    public static EnvironmentSpecBuilder builder() {
        return new EnvironmentSpecBuilder();
    }
}
