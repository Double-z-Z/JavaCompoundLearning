# DevOps Dashboard Java 接口定义

> **版本**: v2.0 (V1 + V2 扩展)
> **语言**: Java 17+
> **架构**: DDD (Domain-Driven Design)
> **状态**: 双轨运行 — REST API 层与 MCP 适配层共存

---

## 📖 目录

1. [架构总览](#架构总览)
2. [环境管理接口](#环境管理接口)
3. [实验管理接口](#实验管理接口)
4. [可观测性接口](#可观测性接口)
5. [流水线接口](#流水线接口)
6. [Host 相关接口（V2 新增）](#host-相关接口v2-新增)
7. [Loadgen 相关接口（V2 新增）](#loadgen-相关接口v2-新增)
8. [Evidence 相关接口（V2 新增）](#evidence-相关接口v2-新增)
9. [MCP 适配层接口（V2 新增）](#mcp-适配层接口v2-新增)
10. [异常体系](#异常体系)

---

## 架构总览

### 分层架构

```
┌─────────────────────────────────────────────────────┐
│                  Interfaces Layer                    │
│    ┌──────────────┐    ┌────────────────────────┐   │
│    │ REST API     │    │ MCP Server             │   │
│    │ (Controller) │    │ (Handler)              │   │
│    └──────┬───────┘    └───────────┬────────────┘   │
│           │                        │                │
├───────────┼────────────────────────┼────────────────┤
│       Application Layer           │                │
│  ┌──────────┐ ┌────────┐ ┌──────┐ │ ┌──────────┐   │
│  │EnvService│ │ExpSvc  │ │MonSvc│ │ │HostService│   │
│  │          │ │        │ │      │ │ │LoadgenSvc │   │
│  └────┬─────┘ └───┬────┘ └──┬───┘ │ │EvidCollector│ │
│       │          │         │      │ └─────┬────┘   │
├───────┼──────────┼─────────┼──────┼───────┼────────┤
│     Domain Layer                 │               │
│  ┌────────┐ ┌──────┐ ┌──────┐ │ ┌─────┐ ┌────┐  │
│  │Environ │ │Exper │ │Serv  │ │ │ Host│ │Ev  │  │
│  │ment    │ │iment │ │ice   │ │ │     │ |id  │  │
│  └────┬───┘ └──┬───┘ └──┬───┘ │ └──┬──┘ └─┬──┘  │
│       │        │        │      │    │      │     │
├───────┼────────┼────────┼──────┼────┼──────┼─────┤
│   Infrastructure Layer            │              │
│  ┌────────┐ ┌──────┐ ┌────────┐│ ┌───┐ ┌────┐  │
│  │EnvRepo │ │ExpRepo│ │Docker ││ │Yam│ │InMem│ │
│  │        │ │       │ │Provider│ │lHst│ │Repo │ │
│  └────────┘ └──────┘ └────────┘│ └───┘ └────┘  │
└─────────────────────────────────────────────────┘
```

### 包结构

```
com.devops.dashboard
├── domain/                          # 领域层
│   ├── environment/                 # 环境聚合根
│   ├── experiment/                  # 实验聚合根
│   ├── host/                        # 【V2 新增】主机聚合根
│   ├── evidence/                    # 【V2 新增】证据聚合
│   ├── loadgen/                     # 【V2 新增】压测领域
│   └── exception/                   # 领域异常
├── application/                     # 应用服务层
│   ├── service/                     # 应用服务接口
│   │   ├── EnvironmentService.java
│   │   ├── ExperimentService.java
│   │   ├── MonitoringService.java
│   │   ├── PipelineOrchestrator.java
│   │   ├── HostService.java         # 【V2 新增】
│   │   ├── LoadgenService.java      # 【V2 新增】
│   │   └── EvidenceCollector.java   # 【V2 新增】
│   └── impl/                        # 应用服务实现
├── infrastructure/                  # 基础设施层
│   ├── environment/
│   ├── experiment/
│   ├── host/                        # 【V2 新增】
│   ├── loadgen/                     # 【V2 新增】
│   └── evidence/                    # 【V2 新增】
├── interfaces/                      # 接口适配层
│   ├── rest/                        # REST API (V1)
│   │   ├── EnvironmentController.java
│   │   └── ExperimentController.java
│   └── mcp/                         # 【V2 新增】MCP Server
│       ├── server/
│       ├── handler/
│       ├── dto/
│       ├── error/
│       └── prompt/
└── shared/                          # 共享组件
```

---

## 环境管理接口

### EnvironmentService（环境服务）

```java
package com.devops.dashboard.application.service;

/**
 * 环境管理核心服务
 * 负责环境的创建、销毁、服务部署等操作
 * 
 * V2 增强：增加 Host 校验逻辑，支持多机部署
 */
public interface EnvironmentService {

    /**
     * 从规格说明创建环境
     * V2 增强：校验 target host 的角色和资源
     *
     * @param spec 环境规格（包含服务列表、资源配置等）
     * @return 创建的环境实例（含访问地址等信息）
     * @throws EnvironmentCreationException 创建失败时抛出
     * @throws HostNotFoundException 目标主机不存在（V2）
     * @throws InvalidHostRoleException 主机角色不匹配（V2）
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
     * V2 增强：支持模板化部署
     *
     * @param envId 目标环境ID
     * @param manifest 服务清单（引用模板+覆盖配置）
     * @return 部署的服务实例
     */
    ServiceInstance deployService(EnvironmentId envId, ServiceManifest manifest);

    /**
     * 停止环境中的指定服务
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

    /**
     * 【V2 新增】获取环境的访问端点信息
     * 用于 MCP Tool env_get_access
     *
     * @param envId 环境ID
     * @return 访问端点映射（服务名 -> URL）
     */
    Map<String, String> getAccessEndpoints(EnvironmentId envId);
}
```

### EnvironmentRepository（环境仓储）

```java
package com.devops.dashboard.infrastructure.environment;

/**
 * 环境持久化仓储接口
 */
public interface EnvironmentRepository {

    Environment save(Environment environment);

    Optional<Environment> findById(EnvironmentId id);

    List<Environment> findByStatus(EnvironmentStatus status);

    List<Environment> findByType(EnvironmentType type);

    /** 【V2 新增】按目标主机查询 */
    List<Environment> findByHostId(HostId hostId);

    void delete(EnvironmentId id);

    boolean existsById(EnvironmentId id);
}
```

---

## 实验管理接口

### ExperimentService（实验服务）

```java
package com.devops.dashboard.application.service;

/**
 * 实验管理核心服务
 * 负责Spike实验的全生命周期管理
 * 
 * V2 增强：集成 Evidence 收集，支持 MCP session 管理
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
package com.devops.dashboard.infrastructure.experiment;

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
package com.devops.dashboard.application.service;

/**
 * 可观测性服务
 * 提供日志流、指标查询、健康检查等功能
 * 
 * V2 增强：支持实时流式日志、细粒度指标采集
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

    /**
     * 【V2 新增】收集指定时间段的性能指标
     * 用于 MCP Tool test_collect_metrics
     *
     * @param envId 环境ID
     * @param serviceName 服务名（可选）
     * @param metrics 要采集的指标列表
     * @param duration 采集时长
     * @return 指标样本数据
     */
    List<MetricSample> collectMetrics(
        EnvironmentId envId,
        String serviceName,
        List<String> metrics,
        Duration duration
    );
}
```

---

## 流水线接口

### PipelineOrchestrator（流水线编排器）

```java
package com.devops.dashboard.application.service;

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

## Host 相关接口（V2 新增）

### HostRepository（主机仓储）

```java
package com.devops.dashboard.domain.host;

import java.util.Optional;
import java.util.List;

/**
 * 主机仓储接口
 * 支持从 YAML 配置或数据库加载主机拓扑
 */
public interface HostRepository {

    /**
     * 根据 ID 查找主机
     */
    Optional<Host> findById(HostId id);

    /**
     * 查询所有主机
     */
    List<Host> findAll();

    /**
     * 按角色查询主机
     * 如查找所有具有 'target' 角色的主机
     */
    List<Host> findByRole(HostRole role);

    /**
     * 按类型查询主机
     * 如查找所有 VM 类型的主机
     */
    List<Host> findByType(HostType type);

    /**
     * 查询子节点
     * 如 PVE 宿主机下的所有 VM
     */
    List<Host> findChildren(HostId parentId);

    /**
     * 检查主机是否存在
     */
    boolean existsById(HostId id);
}
```

### HostService（主机服务）

```java
package com.devops.dashboard.application.host;

import java.util.Optional;
import java.util.List;

/**
 * 主机应用服务
 * 提供主机拓扑查询、校验等功能
 * 主要供 MCP Handler 和 EnvironmentService 使用
 */
public interface HostService {

    /**
     * 获取完整的主机层次拓扑
     * 用于 MCP Resource: hosts://topology
     *
     * @return 包含当前 MCP Host ID 和完整主机列表的拓扑对象
     */
    HostTopology getTopology();

    /**
     * 校验主机是否具备指定角色
     *
     * @param hostId 主机ID
     * @param role 期望的角色
     * @throws HostNotFoundException 主机不存在
     * @throws InvalidHostRoleException 角色不匹配
     */
    void validateRole(HostId hostId, HostRole role);

    /**
     * 校验主机是否具备指定能力
     *
     * @param hostId 主机ID
     * @param capability 期望的能力（如 DOCKER）
     * @throws HostCapabilityMismatchException 能力不匹配
     */
    void validateCapability(HostId hostId, Capability capability);

    /**
     * 检查主机资源是否充足
     *
     * @param hostId 主机ID
     * @param requiredCpu 所需 CPU（millicores）
     * @param requiredMemoryMb 所需内存（MB）
     * @return 是否充足
     */
    boolean checkResourceAvailability(HostId hostId, int requiredCpu, int requiredMemoryMb);

    /**
     * 获取主机的可用压测工具列表
     * 仅对 loadgen 角色的主机有效
     *
     * @param hostId 主机ID
     * @return 工具名称列表（如 ["wrk", "hey", "ab"]）
     */
    List<String> getAvailableLoadgenTools(HostId hostId);
}
```

### NetworkPathAnalyzer（网络路径分析器）

```java
package com.devops.dashboard.domain.host;

/**
 * 网络路径分析器
 * 分析两台主机之间的网络路径特征
 * 用于判断压测结果的可信度
 */
public interface NetworkPathAnalyzer {

    /**
     * 分析从源主机到目标主机的网络路径
     *
     * @param source 源主机（通常是 Loadgen Host）
     * @param target 目标主机（被测服务所在主机）
     * @param targetPort 目标端口
     * @return 网络路径分析结果
     */
    NetworkPath analyze(Host source, Host target, int targetPort);

    /**
     * 预估网络往返延迟（RTT）
     * 基于路径类型进行估算
     *
     * @param pathType 路径类型
     * @return 预估 RTT（毫秒）
     */
    double estimateRtt(NetworkPathType pathType);

    /**
     * 判断是否经过物理网卡
     * same-host 和 same-hypervisor 通常不经过物理网卡
     *
     * @param source 源主机
     * @param target 目标主机
     * @return 是否经过物理网卡
     */
    boolean isPhysicalNicInvolved(Host source, Host target);
}
```

**返回值对象定义**:

```java
package com.devops.dashboard.domain.host;

/**
 * 网络路径分析结果
 */
public class NetworkPath {
    private final NetworkPathType pathType;        // 路径类型枚举
    private final int estimatedHops;                // 预估跳数
    private final double estimatedRttMs;            // 预估 RTT (ms)
    private final boolean natTraversal;             // 是否经过 NAT
    private final boolean goesThroughBridge;        // 是否经过 Bridge
    private final boolean physicalNicInvolved;      // 是否经过物理网卡
    private final String warning;                   // 警告信息
    private final String recommendation;            // 建议

    // getters...
}

/**
 * 网络路径类型枚举
 */
public enum NetworkPathType {
    SAME_HOST("same-host", "同机", "⚠️ 极低"),
    SAME_HYPERVISOR("same-hypervisor", "同虚拟化宿主机", "⚠️ 低"),
    SAME_LAN("same-lan", "同局域网", "✅ 高"),
    WAN("wan", "跨广域网", "⚠️ 中");

    private final String code;
    private final String displayName;
    private final String credibility;  // 压测可信度
}
```

---

## Loadgen 相关接口（V2 新增）

### LoadgenService（压测服务）

```java
package com.devops.dashboard.application.loadgen;

import reactor.core.publisher.Mono;

/**
 * 压测服务接口
 * 负责远程执行负载测试
 * 实现 SSH 远程调用 wrk/hey/ab 等工具
 */
public interface LoadgenService {

    /**
     * 执行负载测试
     *
     * @param spec 压测规格（目标URL、并发数、时长等）
     * @return 压测结果（QPS、延迟、错误率等）
     * @throws HostNotFoundException 压测机不存在
     * @throws InvalidHostRoleException 主机无 loadgen 角色
     * @throws LoadgenToolNotAvailableException 未安装指定工具
     */
    Mono<LoadTestResult> executeLoadTest(LoadTestSpec spec);

    /**
     * 取消正在执行的压测
     *
     * @param executionId 压测执行 ID
     */
    void cancelTest(String executionId);

    /**
     * 查询压测执行状态
     *
     * @param executionId 压测执行 ID
     * @return 当前状态和已采集的部分结果
     */
    LoadTestExecutionStatus getTestStatus(String executionId);
}
```

### LoadTestSpec（压测规格）

```java
package com.devops.dashboard.domain.loadgen;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * 压测规格定义
 * 对应 MCP Tool test_load 的输入参数
 */
public class LoadTestSpec {
    private final HostId loadgenHostId;             // 压测机 ID
    private final String targetUrl;                  // 目标 URL
    private final HttpMethod method;                 // HTTP 方法
    private final LoadgenTool tool;                  // 压测工具 (wrk/hey/ab)
    private final Duration duration;                 // 持续时间
    private final int connections;                   // 并发连接数
    private final OptionalInt rpsLimit;              // RPS 上限
    private final Optional<String> payload;          // POST body
    private final Map<String, String> headers;       // 请求头

    // Builder pattern...
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        // builder methods...
        public LoadTestSpec build() { ... }
    }
}

/**
 * 压测工具枚举
 */
public enum LoadgenTool {
    WRK("wrk", "现代多线程 HTTP 压测工具"),
    HEY("hey", "Go 语言实现的 HTTP 负载生成器"),
    AB("ab", "Apache Bench 经典压测工具");

    private final String command;
    private final String description;
}
```

### LoadTestResult（压测结果）

```java
package com.devops.dashboard.domain.loadgen;

import java.time.Instant;

/**
 * 压测结果
 * 对应 MCP Tool test_load 的返回值
 */
public class LoadTestResult {
    private final long totalRequests;                // 总请求数
    private final double avgQps;                      // 平均 QPS
    private final double avgLatencyMs;                // 平均延迟 (ms)
    private final double p50LatencyMs;                // P50 延迟 (ms)
    private final double p99LatencyMs;                // P99 延迟 (ms)
    private final double maxLatencyMs;                // 最大延迟 (ms)
    private final double errorRatePercent;            // 错误率 (%)
    private final String rawOutput;                   // 原始输出
    private final Instant executedAt;                 // 执行时间

    // getters...
}
```

---

## Evidence 相关接口（V2 新增）

### EvidenceCollector（证据收集器）

```java
package com.devops.dashboard.application.evidence;

import java.time.Instant;
import java.util.List;

/**
 * 证据收集器
 * 负责实验过程中各类证据的收集、存储和检索
 * 支持 AI 自动收集和用户手动录入
 */
public interface EvidenceCollector {

    /**
     * 记录一条证据
     * 对应 MCP Tool: session_record_evidence
     *
     * @param sessionId 会话 ID
     * @param type 证据类型（metric/artifact/observation/check_result）
     * @param name 证据名称
     * @param value 证据值
     * @param unit 单位（可选）
     * @param source 来源（load_test_tool/monitoring_agent/manual_input 等）
     * @param metadata 附加元数据（可选）
     * @return 创建的证据对象
     */
    Evidence record(
        SessionId sessionId,
        EvidenceType type,
        String name,
        String value,
        String unit,
        EvidenceSource source,
        Object metadata
    );

    /**
     * 查询会话的所有证据
     *
     * @param sessionId 会话 ID
     * @return 证据列表（按时间倒序）
     */
    List<Evidence> findBySession(SessionId sessionId);

    /**
     * 按类型查询证据
     */
    List<Evidence> findBySessionAndType(SessionId sessionId, EvidenceType type);

    /**
     * 生成证据摘要
     * 用于实验结论时的汇总展示
     *
     * @param sessionId 会话 ID
     * @return 结构化的证据摘要
     */
    EvidenceSummary summarize(SessionId sessionId);
}
```

**领域对象定义**:

```java
package com.devops.dashboard.domain.evidence;

/**
 * 证据聚合根
 */
public class Evidence {
    private final EvidenceId id;
    private final SessionId sessionId;
    private final EvidenceType type;          // metric | artifact | observation | check_result
    private final String name;                // 证据名称
    private final String value;               // 证据值
    private final String unit;                // 单位
    private final EvidenceSource source;      // 来源
    private final Object metadata;            // 元数据
    private final Instant recordedAt;         // 记录时间

    // getters...
}

/**
 * 证据类型枚举
 */
public enum EvidenceType {
    METRIC("metric", "数值型指标"),
    ARTIFACT("artifact", "产物文件"),
    OBSERVATION("observation", "观察笔记"),
    CHECK_RESULT("check_result", "检查结果");
}

/**
 * 证据来源枚举
 */
public enum EvidenceSource {
    LOAD_TEST_TOOL("load_test_tool"),
    MONITORING_AGENT("monitoring_agent"),
    HEALTH_CHECK("health_check"),
    FUNCTIONAL_TEST("functional_test"),
    MANUAL_INPUT("manual_input"),
    COMMAND_EXEC("command_exec");
}
```

---

## MCP 适配层接口（V2 新增）

### McpHandler（Handler 基类）

```java
package com.devops.dashboard.mcp.handler;

import reactor.core.publisher.Mono;

/**
 * MCP Handler 基类
 * 所有 MCP Tool Handler 的公共抽象
 * 提供统一的错误处理和响应格式化
 */
public abstract class McpHandler {

    protected final McpExceptionTranslator errorTranslator;

    protected McpHandler(McpExceptionTranslator errorTranslator) {
        this.errorTranslator = errorTranslator;
    }

    /**
     * 异步处理服务调用，统一错误处理
     *
     * @param serviceCall 服务层调用
     * @return MCP 格式的响应
     */
    protected Mono<McpCallToolResult> handleAsync(Mono<?> serviceCall) {
        return serviceCall
            .map(this::toSuccessResult)
            .onErrorResume(DomainException.class, ex -> 
                Mono.just(toErrorResult(errorTranslator.translate(ex))));
    }

    /**
     * 将成功结果转换为 MCP 响应格式
     */
    protected McpCallToolResult toSuccessResult(Object data) {
        String json = JsonUtils.toJson(data);
        return new McpCallToolResult(List.of(new McpTextContent(json)), false);
    }

    /**
     * 将错误转换为 MCP 响应格式
     */
    protected McpCallToolResult toErrorResult(McpError error) {
        String json = JsonUtils.toJson(error);
        return new McpCallToolResult(List.of(new McpTextContent(json)), true);
    }
}
```

### 各 Handler 接口定义

#### DiscoveryHandler（发现类 Handler）

```java
package com.devops.dashboard.mcp.handler;

import org.springframework.stereotype.Component;

/**
 * 发现类 Handler
 * 提供 Resources: hosts://topology, templates://list, envs://list
 * 只读操作，无需用户确认
 */
@Component
public class DiscoveryHandler extends McpHandler {

    private final HostService hostService;
    private final TemplateService templateService;
    private final EnvironmentService environmentService;

    public DiscoveryHandler(
            McpExceptionTranslator errorTranslator,
            HostService hostService,
            TemplateService templateService,
            EnvironmentService environmentService) {
        super(errorTranslator);
        this.hostService = hostService;
        this.templateService = templateService;
        this.environmentService = environmentService;
    }

    /**
     * 获取 hosts://topology Resource
     */
    public McpResource getHostsTopology() {
        HostTopology topology = hostService.getTopology();
        return McpResource.builder()
            .uri("hosts://topology")
            .name("主机拓扑")
            .mimeType("application/json")
            .text(JsonUtils.toJson(topology))
            .build();
    }

    /**
     * 获取 templates://list Resource
     */
    public McpResource getTemplatesList() {
        List<Template> templates = templateService.findAll();
        return McpResource.builder()
            .uri("templates://list")
            .name("服务模板列表")
            .text(JsonUtils.toJson(Map.of("templates", templates)))
            .build();
    }

    /**
     * 获取 templates://{id} Resource
     */
    public McpResource getTemplateDetail(String templateId) {
        Template template = templateService.findById(templateId)
            .orElseThrow(() -> new TemplateNotFoundException(templateId));
        return McpResource.builder()
            .uri("templates://" + templateId)
            .name(template.getDisplayName())
            .text(JsonUtils.toJson(template))
            .build();
    }

    /**
     * 获取 envs://list Resource
     */
    public McpResource getEnvsList() {
        List<Environment> environments = environmentService.findAllActive();
        return McpResource.builder()
            .uri("envs://list")
            .name("环境列表")
            .text(JsonUtils.toJson(Map.of("environments", environments)))
            .build();
    }
}
```

#### EnvironmentHandler（环境类 Handler）

```java
package com.devops.dashboard.mcp.handler;

import org.springframework.stereotype.Component;

/**
 * 环境 Handler
 * 提供 Tools: env_create, env_deploy_service, env_get_access, env_destroy
 * 写操作，需要用户确认
 */
@Component
public class EnvironmentHandler extends McpHandler {

    private final EnvironmentService environmentService;
    private final HostService hostService;
    private final TemplateService templateService;

    public EnvironmentHandler(
            McpExceptionTranslator errorTranslator,
            EnvironmentService environmentService,
            HostService hostService,
            TemplateService templateService) {
        super(errorTranslator);
        this.environmentService = environmentService;
        this.hostService = hostService;
        this.templateService = templateService;
    }

    /**
     * 注册 env_create Tool
     */
    public McpTool getCreateTool() {
        return McpTool.builder()
            .name("env_create")
            .description("在指定的 Target Host 上创建一个新的空环境...")
            .inputSchema(JsonSchema.of(EnvCreateRequest.class))
            .handler(this::handleCreate)
            .build();
    }

    private Mono<McpCallToolResult> handleCreate(Map<String, Object> args) {
        EnvCreateRequest req = JsonUtils.fromMap(args, EnvCreateRequest.class);

        // 校验 Target Host
        hostService.validateRole(HostId.of(req.getTargetHostId()), HostRole.TARGET);
        
        if (req.getRuntime() != null) {
            hostService.validateCapability(
                HostId.of(req.getTargetHostId()), 
                Capability.valueOf(req.getRuntime().toUpperCase())
            );
        }

        EnvironmentSpec spec = EnvironmentSpec.builder()
            .name(req.getEnvName())
            .type(EnvironmentType.valueOf(req.getType()))
            .hostId(HostId.of(req.getTargetHostId()))
            .runtime(RuntimeType.valueOf(req.getRuntime()))
            .resourceQuota(parseResourceLimit(req.getResourceLimit()))
            .lifecyclePolicy(LifecyclePolicy.autoDestroy(parseDuration(req.getAutoDestroyDuration())))
            .build();

        return handleAsync(environmentService.createFromSpec(spec)
            .map(env -> toEnvCreateResponse(env)));
    }

    /**
     * 注册 env_deploy_service Tool
     */
    public McpTool getDeployServiceTool() { /* ... */ }

    /**
     * 注册 env_get_access Tool
     */
    public McpTool getAccessTool() { /* ... */ }

    /**
     * 注册 env_destroy Tool
     */
    public McpTool getDestroyTool() { /* ... */ }
}
```

#### TestingHandler（测试类 Handler）

```java
package com.devops.dashboard.mcp.handler;

import org.springframework.stereotype.Component;

/**
 * 测试 Handler
 * 提供 Tools: test_health_check, test_functional, test_load, 
 *             test_collect_metrics, test_stream_logs, test_exec_command
 */
@Component
public class TestingHandler extends McpHandler {

    private final LoadgenService loadgenService;
    private final MonitoringService monitoringService;
    private final HostService hostService;

    public TestingHandler(
            McpExceptionTranslator errorTranslator,
            LoadgenService loadgenService,
            MonitoringService monitoringService,
            HostService hostService) {
        super(errorTranslator);
        this.loadgenService = loadgenService;
        this.monitoringService = monitoringService;
        this.hostService = hostService;
    }

    /**
     * 注册 test_load Tool
     */
    public McpTool getLoadTool() {
        return McpTool.builder()
            .name("test_load")
            .description("在指定的 Loadgen Host 上执行负载/压测...")
            .inputSchema(JsonSchema.of(LoadTestRequest.class))
            .handler(this::handleLoadTest)
            .build();
    }

    private Mono<McpCallToolResult> handleLoadTest(Map<String, Object> args) {
        LoadTestRequest req = JsonUtils.fromMap(args, LoadTestRequest.class);

        // 校验 Loadgen Host
        hostService.validateRole(HostId.of(req.getLoadgenHostId()), HostRole.LOADGEN);
        
        // 检查工具可用性
        List<String> availableTools = hostService.getAvailableLoadgenTools(
            HostId.of(req.getLoadgenHostId())
        );
        LoadgenTool tool = LoadgenTool.valueOf(req.getTool().toUpperCase());
        if (!availableTools.contains(tool.name().toLowerCase())) {
            throw new LoadgenToolNotAvailableException(
                req.getLoadgenHostId(), tool, availableTools
            );
        }

        LoadTestSpec spec = LoadTestSpec.builder()
            .loadgenHostId(HostId.of(req.getLoadgenHostId()))
            .targetUrl(req.getTargetUrl())
            .method(HttpMethod.valueOf(req.getMethod()))
            .tool(tool)
            .duration(Duration.ofSeconds(req.getDurationSeconds()))
            .connections(req.getConnections())
            .rpsLimit(Optional.ofNullable(req.getRequestsPerSecond()).map(OptionalInt::of).orElse(OptionalInt.empty()))
            .payload(Optional.ofNullable(req.getPayload()))
            .headers(req.getHeaders() != null ? req.getHeaders() : Map.of())
            .build();

        return handleAsync(loadgenService.executeLoadTest(spec)
            .map(result -> toLoadTestResponse(req, result)));
    }

    /**
     * 注册其他测试 Tools
     */
    public McpTool getHealthCheckTool() { /* ... */ }
    public McpTool getFunctionalTool() { /* ... */ }
    public McpTool getCollectMetricsTool() { /* ... */ }
    public McpTool getStreamLogsTool() { /* ... */ }
    public McpTool getExecCommandTool() { /* ... */ }
}
```

#### DiagnosisHandler（诊断类 Handler）

```java
package com.devops.dashboard.mcp.handler;

import org.springframework.stereotype.Component;

/**
 * 诊断 Handler
 * 提供 Tool: analyze_network_path
 */
@Component
public class DiagnosisHandler extends McpHandler {

    private final HostRepository hostRepository;
    private final NetworkPathAnalyzer pathAnalyzer;

    public DiagnosisHandler(
            McpExceptionTranslator errorTranslator,
            HostRepository hostRepository,
            NetworkPathAnalyzer pathAnalyzer) {
        super(errorTranslator);
        this.hostRepository = hostRepository;
        this.pathAnalyzer = pathAnalyzer;
    }

    /**
     * 注册 analyze_network_path Tool
     */
    public McpTool getAnalyzeNetworkPathTool() {
        return McpTool.builder()
            .name("analyze_network_path")
            .description("分析从 Loadgen Host 到 Target Host 的网络路径...")
            .inputSchema(JsonSchema.of(AnalyzePathRequest.class))
            .handler(this::handleAnalyzePath)
            .build();
    }

    private Mono<McpCallToolResult> handleAnalyzePath(Map<String, Object> args) {
        AnalyzePathRequest req = JsonUtils.fromMap(args, AnalyzePathRequest.class);

        Host source = hostRepository.findById(HostId.of(req.getSourceHostId()))
            .orElseThrow(() -> new HostNotFoundException(req.getSourceHostId()));
        
        Host target = hostRepository.findById(HostId.of(req.getTargetHostId()))
            .orElseThrow(() -> new HostNotFoundException(req.getTargetHostId()));

        NetworkPath path = pathAnalyzer.analyze(source, target, req.getTargetPort());

        return Mono.just(toSuccessResult(toAnalyzePathResponse(source, target, path)));
    }
}
```

#### EvidenceHandler（会话类 Handler）

```java
package com.devops.dashboard.mcp.handler;

import org.springframework.stereotype.Component;

/**
 * 证据与会话 Handler
 * 提供 Tools: session_create, session_record_evidence, session_conclude
 */
@Component
public class EvidenceHandler extends McpHandler {

    private final EvidenceCollector evidenceCollector;
    private final SessionService sessionService;

    public EvidenceHandler(
            McpExceptionTranslator errorTranslator,
            EvidenceCollector evidenceCollector,
            SessionService sessionService) {
        super(errorTranslator);
        this.evidenceCollector = evidenceCollector;
        this.sessionService = sessionService;
    }

    /**
     * 注册 session_create Tool
     */
    public McpTool getSessionCreateTool() { /* ... */ }

    /**
     * 注册 session_record_evidence Tool
     */
    public McpTool getSessionRecordEvidenceTool() { /* ... */ }

    /**
     * 注册 session_conclude Tool
     */
    public McpTool getSessionConcludeTool() { /* ... */ }
}
```

---

## 异常体系

### 基础异常

```java
package com.devops.dashboard.domain.exception;

/**
 * 领域异常基类
 * 所有业务异常都应继承此类
 */
public class DomainException extends RuntimeException {
    private final String errorCode;
    
    public DomainException(String message) {
        super(message);
        this.errorCode = this.getClass().getSimpleName();
    }
    
    public DomainException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = this.getClass().getSimpleName();
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}
```

### 环境相关异常（V1 已有）

```java
// 环境相关
public class EnvironmentNotFoundException extends DomainException { }
public class EnvironmentCreationException extends DomainException { }
public class EnvironmentDestroyException extends DomainException { }
public class ResourceQuotaExceededException extends DomainException { }
public class PortConflictException extends DomainException { }
public class InvalidEnvironmentTransitionException extends DomainException { }
```

### 实验相关异常（V1 已有）

```java
// 实验相关
public class ExperimentNotFoundException extends DomainException { }
public class ExperimentAlreadyConcludedException extends DomainException { }
public class ExperimentLifetimeExceededException extends DomainException { }
public class InvalidExperimentTransitionException extends DomainException { }
```

### 服务相关异常（V1 已有）

```java
// 服务相关
public class ServiceDeploymentFailedException extends DomainException { }
public class HealthCheckTimeoutException extends DomainException { }
public class ServiceNotFoundException extends DomainException { }
```

### 流水线相关异常（V1 已有）

```java
// 流水线相关
public class PipelineExecutionException extends DomainException { }
public class ApprovalTimeoutException extends DomainException { }
public class StageNotApprovedException extends DomainException { }
```

### 基础设施相关异常（V1 已有）

```java
// 基础设施相关
public class ProviderConnectionException extends DomainException { }
public class CommandExecutionException extends DomainException { }
public class ContainerNotFoundException extends DomainException { }
```

### Host 相关异常（V2 新增）

```java
package com.devops.dashboard.domain.exception.host;

/**
 * 主机未找到
 */
public class HostNotFoundException extends DomainException {
    public HostNotFoundException(String hostId) {
        super("Host not found: " + hostId);
    }
}

/**
 * 主机角色无效
 * 如尝试用非 target 角色的主机部署环境
 */
public class InvalidHostRoleException extends DomainException {
    private final String hostId;
    private final HostRole expectedRole;
    private final HostRole actualRole;
    
    public InvalidHostRoleException(String hostId, String expectedRole) {
        super(String.format("Host %s does not have required role: %s", hostId, expectedRole));
        this.hostId = hostId;
        this.expectedRole = HostRole.valueOf(expectedRole);
        this.actualRole = null;
    }
}

/**
 * 主机能力不匹配
 * 如在仅支持 native 的主机上使用 docker runtime
 */
public class HostCapabilityMismatchException extends DomainException {
    private final String hostId;
    private final Capability requiredCapability;
    
    public HostCapabilityMismatchException(String hostId, String capability) {
        super(String.format("Host %s does not support capability: %s", hostId, capability));
        this.hostId = hostId;
        this.requiredCapability = Capability.valueOf(capability);
    }
}
```

### Loadgen 相关异常（V2 新增）

```java
package com.devops.dashboard.domain.exception.loadgen;

/**
 * 压测工具不可用
 * 指定的 Loadgen Host 上未安装所需的压测工具
 */
public class LoadgenToolNotAvailableException extends DomainException {
    private final String hostId;
    private final LoadgenTool requestedTool;
    private final List<String> availableTools;
    
    public LoadgenToolNotAvailableException(
            String hostId, 
            LoadgenTool requestedTool, 
            List<String> availableTools) {
        super(String.format(
            "Tool %s not available on host %s. Available tools: %s",
            requestedTool.name(), hostId, availableTools
        ));
        this.hostId = hostId;
        this.requestedTool = requestedTool;
        this.availableTools = availableTools;
    }
}

/**
 * 压试执行超时
 */
public class LoadTestTimeoutException extends DomainException { }

/**
 * 压试执行失败
 */
public class LoadTestExecutionException extends DomainException { }
```

### Evidence 相关异常（V2 新增）

```java
package com.devops.dashboard.domain.exception.evidence;

/**
 * 会话未找到
 */
public class SessionNotFoundException extends DomainException { }

/**
 * 会话已结束，无法添加证据
 */
public class SessionAlreadyConcludedException extends DomainException { }

/**
 * 无效的证据类型
 */
public class InvalidEvidenceTypeException extends DomainException { }
```

### 异常映射表（用于 MCP Error 转换）

| 领域异常 | MCP Error Code | HTTP Status | 用户提示 |
|---------|---------------|-------------|---------|
| `HostNotFoundException` | `HOST_NOT_FOUND` | 404 | "指定的主机不存在" |
| `InvalidHostRoleException` | `INVALID_HOST_ROLE` | 400 | "主机角色不满足要求" |
| `HostCapabilityMismatchException` | `HOST_CAPABILITY_MISMATCH` | 400 | "主机不支持该能力" |
| `LoadgenToolNotAvailableException` | `LOADGEN_TOOL_NOT_AVAILABLE` | 400 | "压测机上未安装该工具" |
| `EnvironmentNotFoundException` | `ENVIRONMENT_NOT_FOUND` | 404 | "环境不存在" |
| `InvalidEnvironmentTransitionException` | `INVALID_ENV_STATUS` | 409 | "环境状态不允许此操作" |
| `ResourceQuotaExceedededException` | `RESOURCE_QUOTA_EXCEEDED` | 409 | "资源配额超限" |
| `PortConflictException` | `PORT_CONFLICT` | 409 | "端口冲突" |

---

## 使用示例

### 示例 1: 通过 REST API 创建开发环境

```java
@Service
public class QuickStartUseCase {

    private final EnvironmentService environmentService;
    private final MonitoringService monitoringService;

    public void startDevEnvironment() {
        var spec = EnvironmentSpec.builder()
            .type(EnvironmentType.DEV)
            .targetNode(NodeRef.of("redis-1", "10.0.0.102"))
            .resourceQuota(ResourceQuota.development())
            .build();
        
        spec.addService(ServiceManifest.fromTemplate("nacos-server"));
        
        var env = environmentService.createFromSpec(spec);
        
        var health = monitoringService.checkAllHealth(env.getId());
        
        System.out.println("✅ Nacos Console: " + env.getAccessEndpoints().get("nacos_console"));
    }
}
```

### 示例 2: 通过 MCP 进行 Spike 实验（V2）

```java
@Service
public class McpExperimentFlow {

    private final HostService hostService;
    private final EnvironmentService envService;
    private final LoadgenService loadgenService;
    private final NetworkPathAnalyzer pathAnalyzer;
    private final EvidenceCollector evidenceCollector;

    public void runNacosPerformanceExperiment() {
        // 1. 查询拓扑
        HostTopology topology = hostService.getTopology();
        
        // 2. 选择 Target Host
        Host target = topology.findByRole(HostRole.TARGET).get(0);
        
        // 3. 创建环境
        Environment env = envService.createFromSpec(EnvironmentSpec.builder()
            .type(EnvironmentType.EXPERIMENT)
            .hostId(target.getId())
            .name("nacos-perf-01")
            .build());
        
        // 4. 选择 Loadgen Host
        Host loadgen = topology.findByRole(HostRole.LOADGEN).get(0);
        
        // 5. 分析网络路径
        NetworkPath path = pathAnalyzer.analyze(loadgen, target, 8848);
        System.out.println("路径类型: " + path.getPathType().getDisplayName());
        
        // 6. 执行压测
        LoadTestResult result = loadgenService.executeLoadTest(LoadTestSpec.builder()
            .loadgenHostId(loadgen.getId())
            .targetUrl("http://" + target.getAccess().getSshHost() + ":8848/nacos/v1/ns/instance")
            .tool(LoadgenTool.WRK)
            .connections(10)
            .duration(Duration.ofSeconds(60))
            .build()).block();
        
        System.out.println("QPS: " + result.getAvgQps());
        
        // 7. 记录证据
        evidenceCollector.record(
            SessionId.of("sess-001"),
            EvidenceType.METRIC,
            "平均QPS",
            String.valueOf(result.getAvgQps()),
            "req/s",
            EvidenceSource.LOAD_TEST_TOOL,
            Map.of("tool", "wrk", "duration", "60s")
        );
    }
}
```

---

## 相关文档

- [REST API 使用指南](./01-REST-API.md) — V1 双轨运行的 REST 接口
- [MCP Tools 定义](./02-MCP-Tools.md) — V2 MCP 协议完整定义
- [迁移路线图](../04-实施计划/01-迁移路线图.md) — V1→V2 改造计划
- [当前任务清单](../04-实施计划/02-当前任务清单.md) — Phase 1 任务拆解

---

**文档结束**。
