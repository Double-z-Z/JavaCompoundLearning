---
type: atomic-note
id: CONCEPT-Infrastructure分包原则
created: 2026-05-22
updated: 2026-05-22
tags: [ddd, constraints]
status: 🌱
mastery: 0
related_emrg: [EMRG-DDD]
related_goal: []
---

# Infrastructure 分包原则

## 一句话定义
**Infrastructure 层应该按领域划分，而不是按技术划分**——让同一领域的技术实现内聚，替换技术时只改一个包。

## 核心理解

### 两种分包方式对比

#### ❌ 按技术划分（反模式）
```
infrastructure/
├── persistence/          ← 所有 Repository 实现
│   ├── JpaExperimentRepo.java
│   └── JpaEnvironmentRepo.java
├── virtualization/       ← 所有虚拟化实现
│   ├── DockerComposeProvisioner.java
│   └── PveVmProvisioner.java
└── ci/                   ← 所有 CI 集成
    └── JenkinsClient.java
```

**问题**：修改 Environment 的持久化逻辑需要跳转到 `persistence/` 包，但它的 Provisioner 在 `virtualization/` 包——领域概念被技术分类肢解。

#### ✅ 按领域划分（推荐）
```
infrastructure/
├── environment/          ← 环境领域的所有技术实现
│   ├── repository/
│   │   └── JpaEnvironmentRepository.java
│   └── provisioner/
│       ├── DockerComposeProvisioner.java
│       ├── PveVmProvisioner.java
│       └── SshRemoteProvisioner.java
├── experiment/
│   ├── repository/
│   │   └── JpaExperimentRepository.java
│   └── publisher/
│       └── KafkaEventPublisher.java
└── shared/               ← 纯技术工具（仅当多领域复用时）
    └── ssh/
        └── SshClient.java
```

**优势**：
1. **高内聚**：Environment 的所有实现在一个包里，修改时不用跳转
2. **易替换**：把 Docker 换成 K8s，只需改 `environment/provisioner/` 
3. **符合 OOP**：包的边界 = 领域的边界

### 判断标准

| 放在哪里 | 判断依据 |
|---------|---------|
| `environment/` | 包含领域术语（Environment、ServiceInstance） |
| `experiment/` | 包含领域术语（Experiment、Hypothesis） |
| `shared/` | 纯技术工具（SshClient、JsonSerializer），且被多领域复用 |

## 关键关联

- [[DDD分层架构]] - 关联原因：Infrastructure 是四层架构的最底层，分包原则影响可维护性
- [[领域接口纯洁性]] - 关联原因：按领域分包更容易保持接口纯洁性
- [[防退化红线]] - 关联原因：按技术分包是常见的退化路径

## 我的误区与疑问

- ❌ 误区：以为按技术分包更"清晰"（persistence/virtualization/cache）
- ❓ 疑问：如果多个领域共享同一个技术组件（如 Redis），放哪里？

## 代码与实践

```java
// ✅ 按领域分包的实现示例
package infrastructure.environment.provisioner;

@Component
public class DockerComposeProvisioner implements EnvironmentProvisioner {
    
    @Override
    public Mono<Environment> provision(EnvironmentSpec spec) {
        return executeDockerCompose(spec)
            .map(this::toDomain);
            // 容器启停、日志读取、健康轮询等全部锁死在这个类内部
    }
    
    private Mono<DockerComposeResult> executeDockerCompose(EnvironmentSpec spec) {
        // 技术细节不暴露到接口层
    }
}

// Domain 层只看到这个
package domain.environment;

public interface EnvironmentProvisioner {
    Mono<Environment> provision(EnvironmentSpec spec);
    Mono<Void> teardown(EnvironmentId id);
    Mono<EnvironmentStatus> checkStatus(EnvironmentId id);
}
```

## 深入思考
💡 你当前项目的 Infrastructure 层是按什么方式分包的？如果改成按领域分包，哪些文件需要移动？

## 来源
- 项目：DevOps Dashboard 设计复盘
- 对话：2026-05-22-DDD架构整理对话

---

## 🤖 AI评价

### 掌握度评估
- 当前等级：🌱初识
- 更新记录：
  - 2026-05-22: mastery=0 (从复盘笔记提取)

---

```dataview
TABLE status, mastery, length(file.inlinks) as "入链", length(file.outlinks) as "出链"
FROM #ddd
SORT mastery DESC
```
