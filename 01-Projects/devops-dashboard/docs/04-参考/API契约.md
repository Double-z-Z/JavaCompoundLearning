# API接口契约

本文档定义核心领域的公共接口（Java Interface），供应用层调用。

---

## 环境管理接口

### EnvironmentService（环境服务）

```java
/**
 * 环境管理核心服务
 * 负责环境的创建、销毁、服务部署等操作
 */
public interface EnvironmentService {

    /**
     * 从规格说明创建环境
     * 
     * @param spec 环境规格（包含服务列表、资源配置等）
     * @return 创建的环境实例（含访问地址等信息）
     * @throws EnvironmentCreationException 创建失败时抛出
     */
    Environment createFromSpec(EnvironmentSpec spec);

    /**
     * 销毁指定环境
     * 会停止所有服务并释放资源
     * 
     * @param envId 环境ID
     * @throws EnvironmentNotFoundException 环境不存在
     */
    void destroy(EnvironmentId envId);

    /**
     * 向已有环境部署新服务
     * 
     * @param envId 目标环境ID
     * @param manifest 服务清单（引用模板+覆盖配置）
     * @return 部署的服务实例
     */
    ServiceInstance deployService(EnvironmentId envId, ServiceManifest manifest);

    /**
     * 停止环境中的指定服务
     * 
     * @param envId 环境ID
     * @param svcId 服务实例ID
     */
    void stopService(EnvironmentId envId, ServiceInstanceId svcId);

    /**
     * 重启服务
     */
    void restartService(EnvironmentId envId, ServiceInstanceId svcId);

    /**
     * 查询环境状态
     */
    EnvironmentStatus getStatus(EnvironmentId envId);

    /**
     * 列出环境中所有服务实例
     */
    List<ServiceInstance> listServices(EnvironmentId envId);
}
```

### EnvironmentRepository（环境仓储）

```java
/**
 * 环境持久化仓储接口
 */
public interface EnvironmentRepository {

    Environment save(Environment environment);

    Optional<Environment> findById(EnvironmentId id);

    List<Environment> findByStatus(EnvironmentStatus status);

    List<Environment> findByType(EnvironmentType type);

    void delete(EnvironmentId id);

    boolean existsById(EnvironmentId id);
}
```

---

## 实验管理接口

### ExperimentService（实验服务）

```java
/**
 * 实验管理核心服务
 * 负责Spike实验的全生命周期管理
 */
public interface ExperimentService {

    /**
     * 创建新的Spike实验
     * 会自动创建专用的实验环境
     * 
     * @param request 实验请求（包含假设、所需服务等）
     * @return 创建的实验实例
     */
    Experiment createSpike(SpikeRequest request);

    /**
     * 提交实验结论
     * 包含证据数据和决策
     * 
     * @param expId 实验ID
     * @param conclusion 结论（ACCEPT/REJECT等）
     */
    void conclude(ExperimentId expId, Conclusion conclusion);

    /**
     * 归档实验
     * 生成Markdown报告到docs/spikes/
     * 销毁实验环境
     * 
     * @param expId 实验ID
     */
    void archive(ExperimentId expId);

    /**
     * 取消正在运行的实验
     */
    void cancel(ExperimentId expId);

    /**
     * 获取实验证据数据
     */
    Evidence getEvidence(ExperimentId expId);

    /**
     * 列出指定状态的实验
     */
    List<Experiment> findByStatus(ExperimentStatus status);
}
```

### ExperimentRepository（实验仓储）

```java
public interface ExperimentRepository {

    Experiment save(Experiment experiment);

    Optional<Experiment> findById(ExperimentId id);

    List<Experiment> findByStatus(ExperimentStatus status);

    List<Experiment> findByDateRange(LocalDateTime start, LocalDateTime end);

    void delete(ExperimentId id);
}
```

---

## 可观测性接口

### MonitoringService（监控服务）

```java
/**
 * 可观测性服务
 * 提供日志流、指标查询、健康检查等功能
 */
public interface MonitoringService {

    /**
     * 实时流式获取容器日志（WebSocket）
     * 
     * @param containerId 容器ID
     * @param options 日志过滤选项（行数、时间范围、关键词）
     * @return Flux<String> 响应式日志流
     */
    Flux<String> streamLogs(ContainerId containerId, LogOptions options);

    /**
     * 批量查询服务健康状态
     * 
     * @param envId 环境ID（可选，null则查全部）
     * @return 各服务的健康快照
     */
    Map<ServiceInstanceId, HealthSnapshot> checkAllHealth(EnvironmentId envId);

    /**
     * 获取服务资源使用情况
     * CPU、内存、网络IO等
     */
    ResourceUsage getResourceUsage(ServiceInstanceId svcId);

    /**
     * 获取服务事件历史
     * 启动、停止、崩溃重启等
     */
    List<ServiceEvent> getEventHistory(ServiceInstanceId svcId, Duration period);
}
```

---

## 流水线接口

### PipelineOrchestrator（流水线编排器）

```java
/**
 * 流水线编排器
 * 负责执行和管理CI/CD流水线
 */
public interface PipelineOrchestrator {

    /**
     * 触发流水线执行
     * 
     * @param pipelineId 流水线ID
     * @param params 触发参数（分支、版本号等）
     * @return 执行实例ID（用于跟踪进度）
     */
    PipelineExecutionId trigger(String pipelineId, TriggerParams params);

    /**
     * 查询执行进度
     * 返回各Stage/Step的状态
     */
    PipelineExecutionStatus getExecutionStatus(PipelineExecutionId execId);

    /**
     * 手动审批Stage
     * 用于manual_approval类型的Gate
     */
    void approve(PipelineExecutionId execId, String stageId, Approver approver);

    void reject(PipelineExecutionId execId, String stageId, String reason);

    /**
     * 取消正在执行的流水线
     */
    void cancel(PipelineExecutionId execId);

    /**
     * 获取执行历史
     */
    List<PipelineExecution> getHistory(String pipelineId, int limit);
}
```

---

## 基础设施插件接口

### InfrastructureProvider（基础设施提供者）

```java
/**
 * 基础设施提供者接口
 * 实现类: DockerComposeProvider, KubernetesProvider, AnsibleProvider
 */
public interface InfrastructureProvider {

    /**
     * 提供者类型标识
     */
    String providerType();

    /**
     * 提供基础设施（创建环境/集群）
     * 
     * @param spec 基础设施规格
     * @return 预配结果（包含连接信息等）
     */
    ProvisionResult provision(InfrastructureSpec spec);

    /**
     * 销毁基础设施
     */
    void teardown(InfrastructureId id);

    /**
     * 查询容器状态
     */
    ContainerStatus checkContainer(ContainerId containerId);

    /**
     * 启动容器
     */
    ContainerId startContainer(ContainerConfig config);

    /**
     * 停止容器
     */
    void stopContainer(ContainerId containerId);

    /**
     * 流式获取日志
     */
    Flux<String> streamLogs(ContainerId containerId, LogOptions options);

    /**
     * 执行远程命令（通用）
     */
    CommandResult executeCommand(Command command);
}
```

---

## 异常体系

```java
// 基础异常
public class DomainException extends RuntimeException { }

// 环境相关
public class EnvironmentNotFoundException extends DomainException { }
public class EnvironmentCreationException extends DomainException { }
public class EnvironmentDestroyException extends DomainException { }
public class ResourceQuotaExceededException extends DomainException { }
public class PortConflictException extends DomainException { }

// 实验相关
public class ExperimentNotFoundException extends DomainException { }
public class ExperimentAlreadyConcludedException extends DomainException { }
public class ExperimentLifetimeExceededException extends DomainException { }

// 服务相关
public class ServiceDeploymentFailedException extends DomainException { }
public class HealthCheckTimeoutException extends DomainException { }

// 流水线相关
public class PipelineExecutionException extends DomainException { }
public class ApprovalTimeoutException extends DomainException { }
public class StageNotApprovedException extends DomainException { }

// 基础设施相关
public class ProviderConnectionException extends DomainException { }
public class CommandExecutionException extends DomainException { }
```

---

## 使用示例

### 示例1: 创建开发环境

```java
@Service
public class QuickStartUseCase {

    private final EnvironmentService environmentService;
    private final MonitoringService monitoringService;

    public void startDevEnvironment() {
        // 1. 构建环境规格
        var spec = EnvironmentSpec.builder()
            .type(DEV)
            .targetNode(NodeRef.of("redis-1", "10.0.0.102"))
            .resourceQuota(ResourceQuota.development())
            .build();
        
        // 2. 添加服务
        spec.addService(ServiceManifest.fromTemplate("nacos-server"));
        
        // 3. 创建环境
        var env = environmentService.createFromSpec(spec);
        
        // 4. 等待就绪
        var health = monitoringService.checkAllHealth(env.getId());
        
        // 5. 输出访问地址
        System.out.println("✅ Nacos Console: " + env.getAccessEndpoints().get("nacos_console"));
    }
}
```

### 示例2: 进行Spike实验

```java
@Service
public class PerformanceExperimentUseCase {

    private final ExperimentService experimentService;

    public void runRabbitMqPerformanceTest() {
        // 1. 定义假设
        var hypothesis = Hypothesis.builder()
            .statement("RabbitMQ单机可支撑5万QPS")
            .addCriterion("throughput_qps", ">=", 50000)
            .addCriterion("latency_p99_ms", "<=", 10)
            .build();
        
        // 2. 创建实验请求
        var request = SpikeRequest.builder()
            .title("RabbitMQ性能基准")
            .hypothesis(hypothesis)
            .maxLifetime(Duration.ofHours(2))
            .addService(ServiceManifest.fromTemplate("rabbitmq"))
            .targetNode(NodeRef.of("redis-1"))
            .build();
        
        // 3. 启动实验
        var experiment = experimentService.createSpike(request);
        
        // 4. ... 运行压测脚本 ...
        
        // 5. 收集证据并得出结论
        var conclusion = Conclusion.accept(
            "达到52K QPS，满足需求",
            List.of("单机足够", "内存占用合理"),
            List.of("进行稳定性测试")
        );
        
        experimentService.conclude(experiment.getId(), conclusion);
        
        // 6. 归档（自动生成报告+清理环境）
        experimentService.archive(experiment.getId());
    }
}
```
