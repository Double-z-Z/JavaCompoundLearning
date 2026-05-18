# 领域模型总览

## 聚合根 (Aggregate Roots)

### 1. Environment（环境）
**身份标识**: `EnvironmentId`

**核心职责**: 管理运行空间的生命周期和资源分配

**不变量**:
- 同一环境下服务端口不冲突
- 资源使用不超过配额
- 状态转移合法: `CREATING → RUNNING → STOPPED → DESTROYED`

**包含**:
- 实体: `ServiceInstance` (1:N)
- 值对象: `EnvironmentSpec`, `ResourceQuota`, `LifecyclePolicy`, `NetworkConfig`

**状态枚举**:
```java
public enum EnvironmentStatus {
    CREATING, RUNNING, STOPPED, DESTROYED, FAILED
}
```

**类型分类**:
```java
public enum EnvironmentType {
    DEV,           // 本地开发
    TEST,          // 集成测试
    STAGING,       // 预发布
    PROD,          // 生产
    EXPERIMENT     // Spike实验专用
}
```

---

### 2. Experiment（实验）
**身份标识**: `ExperimentId`

**核心职责**: 管理Spike实验的全生命周期，从假设到结论归档

**不变量**:
- 必须关联一个专用Environment
- 必须定义Hypothesis（假设）
- 不能超过最大存活时间（默认2小时）

**状态枚举**:
```java
public enum ExperimentStatus {
    PLANNING, RUNNING, COMPLETED, ARCHIVED, CANCELLED
}
```

**包含**:
- 值对象: `Hypothesis`, `Evidence`, `Conclusion`
- 内部实体: `ExperimentEnvironment` (继承Environment)

**决策类型**:
```java
public enum ExperimentDecision {
    ACCEPT,              // 假设成立，采用方案
    REJECT,              // 假设不成立，拒绝方案
    NEED_MORE_DATA,      // 数据不足，需要更多实验
    INCONCLUSIVE         // 结果矛盾，无法判断
}
```

---

## 非聚合根领域对象

### Pipeline（流程编排器）
**定位**: 不是聚合根，而是流程编排工具

**职责**: 定义标准化的部署流水线（CI/CD）

**组成**:
- Stage (阶段): Validate → Build → Deploy → Verify
- Step (步骤): 每个Stage内的具体操作
- Gate (门禁): automatic / manual_approval

**关键特性**:
- 支持条件执行 (`when_mode_is_cluster`)
- 失败处理策略: abort / continue / retry
- 超时控制: 每个Step独立超时

---

## 实体与值对象清单

### 实体 (Entities)
| 名称 | 所属聚合根 | 身份标识 | 说明 |
|------|-----------|---------|------|
| ServiceInstance | Environment | ServiceInstanceId | 具体的服务实例 |
| ExperimentEnvironment | Experiment | EnvironmentId | 实验专用环境 |

### 值对象 (Value Objects)
| 名称 | 用途 | 不可变属性 |
|------|------|-----------|
| EnvironmentSpec | 环境规格 | type, target_nodes, resource_quota |
| ResourceQuota | 资源限制 | cpu, memory, storage |
| LifecyclePolicy | 生命周期策略 | auto_destroy, max_lifetime |
| NetworkConfig | 网络配置 | mode, network_name |
| HealthCheck | 健康检查配置 | endpoint, interval, timeout |
| Hypothesis | 实验假设 | statement, success_criteria |
| Evidence | 实验证据 | metrics, artifacts |
| Conclusion | 实验结论 | decision, summary, lessons |

---

## 领域关系图

```
┌─────────────────────────────────────┐
│         ServiceTemplate Library      │◄── 引用
└─────────────┬───────────────────────┘
              │
              ▼
┌─────────────────────────────────────┐
│        Environment (聚合根)          │
│                                     │
│  ┌─────────────────────────────┐   │
│  │ ServiceInstance[] (1:N实体) │   │
│  └─────────────────────────────┘   │
└──────────────┬────────────────────┘
               │ 包含
               ▼
┌─────────────────────────────────────┐
│        Experiment (聚合根)           │
│                                     │
│  ┌─────────────────────────────┐   │
│  │ ExperimentEnvironment       │   │
│  │ Hypothesis + Evidence       │   │
│  │ Conclusion                  │   │
│  └─────────────────────────────┘   │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│         Pipeline (编排器)            │
│                                     │
│  Stage[Validate] → Stage[Build]     │
│       → Stage[Deploy] → Stage[Verify]│
└─────────────────────────────────────┘
```
