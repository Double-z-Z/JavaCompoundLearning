package com.devops.dashboard.infrastructure.provider;

import com.devops.dashboard.domain.exception.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 基础设施提供者接口
 * 实现类: DockerComposeProvider, KubernetesProvider, AnsibleProvider
 */
public interface InfrastructureProvider {
    
    /**
     * 提供者类型标识
     * @return "docker-compose" | "kubernetes" | "ansible"
     */
    String providerType();
    
    // ========== 环境生命周期 ==========
    
    /**
     * 提供基础设施（创建环境）
     * @param spec 基础设施规格
     * @return 预配结果（包含连接信息等）
     */
    Mono<ProvisionResult> provision(InfrastructureSpec spec);
    
    /**
     * 销毁基础设施
     * @param id 基础设施ID
     */
    Mono<Void> teardown(InfrastructureId id);
    
    // ========== 容器管理 ==========
    
    /**
     * 启动容器
     * @param config 容器配置
     * @return 容器ID
     */
    Mono<ContainerId> startContainer(ContainerConfig config);
    
    /**
     * 停止容器
     */
    Mono<Void> stopContainer(ContainerId containerId);
    
    /**
     * 查询容器状态
     */
    Mono<ContainerStatus> checkContainer(ContainerId containerId);
    
    /**
     * 流式获取日志（响应式）
     * @param options 日志过滤选项
     * @return 响应式日志流
     */
    Flux<String> streamLogs(ContainerId containerId, LogOptions options);
    
    // ========== 通用命令执行 ==========
    
    /**
     * 在目标节点执行远程命令
     */
    Mono<CommandResult> executeCommand(Command command);
    
    // ========== 健康检查 ==========
    
    /**
     * 等待容器健康就绪
     * @param containerId 容器ID
     * @param endpoint 健康检查端点
     * @param timeout 超时时间
     * @return 是否健康
     */
    Mono<Boolean> waitForHealthy(ContainerId containerId, String endpoint, Duration timeout);
}

// ========== 值对象 ==========

record InfrastructureId(String value) {}

record ProvisionResult(
    InfrastructureId id,
    Map<String, String> accessEndpoints,
    Duration provisionTime
) {}

record ContainerConfig(
    String name,
    String image,
    Map<Integer, Integer> portMappings,
    Map<String, String> environmentVariables,
    String networkName
) {}

enum ContainerStatus {
    RUNNING,
    STOPPED,
    FAILED,
    NOT_FOUND
}

record Command(
    String targetNode,
    String[] commandParts,
    Duration timeout,
    Map<String, String> environment
) {}

record CommandResult(
    int exitCode,
    String stdout,
    String stderr,
    Duration executionTime
) {
    public boolean isSuccess() {
        return exitCode == 0;
    }
}

record InfrastructureSpec(
    String name,
    String targetNode,
    List<ServiceSpec> services,
    String networkMode,
    ResourceLimits resourceLimits
) {}

record ServiceSpec(
    String name,
    String image,
    Map<Integer, Integer> ports,
    Map<String, String> envVars,
    List<String> dependsOn,
    HealthCheck healthCheck
) {}

record HealthCheck(
    String endpoint,
    int intervalSeconds,
    int timeoutSeconds,
    int retries
) {}

record ResourceLimits(
    String cpuLimit,
    String memoryLimit
) {}
