# DDD 分层架构与 DevOps Dashboard 项目设计复盘笔记

## 一、Controller / Service / Domain 的层级关系

### 核心结论
Controller 和 Application Service 位于 Domain 的上层（依赖 Domain），Domain Service 位于 Domain 内部（与 Entity 平行）。Domain 层是整个架构的"内核"。

### 分层架构（从上到下）
| 层级 | 组件 | 职责 |
|------|------|------|
| 用户界面层 | Controller / REST API | 接收请求，调用 Application Service |
| 应用层 | Application Service | 用例编排、事务控制、DTO 转换 |
| 领域层 | Entity, Value Object, Aggregate, Domain Service, Repository Interface | 业务规则、不变量保护 |
| 基础设施层 | Repository Impl, DB, MQ, Cache | 技术实现 |

### 两种 Service 的本质区别
- **Application Service**：位于应用层，在 Domain 之上。编排用例，协调多个 Domain 对象，控制事务边界。包含 `save()`、`findById()`、DTO 转换、权限检查。
- **Domain Service**：位于领域层，与 Entity 平行。处理不适合放在单个 Entity/Value Object 中的纯领域逻辑。无状态，只操作领域对象，不接触基础设施。

### 依赖规则
**上层可以调用下层，下层不能调用上层**。Domain 层不依赖任何其他层，是纯 POJO。

---

## 二、聚合根（Aggregate Root）与非聚合根的区别

### 核心差异
| 维度 | 聚合根 | 非聚合根 |
|------|--------|---------|
| 标识范围 | 全局唯一 ID | 局部唯一 ID（仅在聚合内唯一） |
| 外部访问入口 | 是外部访问聚合的唯一入口 | 外部不能直接引用 |
| Repository | 拥有独立的 Repository | 没有独立 Repository，随聚合根一起持久化 |
| 事务边界 | 一个聚合根 = 一个事务边界 | 随聚合根事务一起提交 |
| 跨聚合引用 | 通过 ID 引用其他聚合根 | 不能直接被外部聚合引用 |

### 一致性边界
聚合的本质是**一致性边界**。聚合根负责维护聚合内所有不变量（invariant），是乐观锁的粒度，保证整个聚合的一致性。一个事务只修改一个聚合，跨聚合通过**最终一致性**（领域事件）同步。

### 代码体现
```java
// 聚合根：全局 ID，独立 Repository
public class Order extends AggregateRoot<OrderId> {
    private OrderId id;
    private List<OrderItem> items; // 内部实体，外部不可直接操作
}

// 内部实体：局部 ID，无独立 Repository
public class OrderItem {
    private Long seq; // 仅在 Order 内唯一
}
```

---

## 三、领域边界（Bounded Context）vs 聚合根边界

### 层次定位
| 概念 | DDD 层级 | 解决的问题 | 类比 |
|------|----------|-----------|------|
| 领域边界 | **战略设计** | 业务子域划分、团队分工、语言统一 | 国家边境 |
| 聚合根边界 | **战术设计** | 数据一致性、事务边界、并发控制 | 小区围墙 |

### 判断标准
**领域边界**：
- 同一个名词在不同地方含义是否不同？（如"商品"在销售上下文是售价+标题，在库存上下文是 SKU+库位）
- 是否由不同团队维护？
- 是否有独立部署/演进的需求？

**聚合根边界**：
- 这些对象是否必须一起保持业务规则不变量？
- 是否存在"没有 A 就没有 B"的强归属关系？
- 并发冲突是否应作为一个整体处理？

### 映射关系
一个领域（Bounded Context）内可以有**多个聚合根**。聚合根之间不存在"包含"和"继承"，只存在"通过 ID 引用"。

---

## 四、Java 多模块的技术维度

### 三种维度对比
| 维度 | 代表技术 | 是否分开编译 | 是否不同 JVM | 隔离机制 |
|------|---------|-------------|-------------|---------|
| 构建工具多模块 | Maven/Gradle | ✅ | ❌ | 无运行时隔离，仅靠依赖约束 |
| 语言级模块化 | Java JPMS / OSGi | ✅ | ❌ | 强封装、ClassLoader 隔离 |
| 架构级模块化 | 微服务 / SOA | ✅ | ✅ | 网络协议 + 进程隔离 |

### Maven 多模块的局限
Maven 多模块只有**编译期组织**作用，**没有运行时隔离**。模块间调用是普通 Java 方法调用，仅靠团队约定 + ArchUnit 等工具做"软性隔离"。

### 决策建议
- **领域边界**先按逻辑划分（Bounded Context）
- 再根据团队规模、部署需求决定是否物理拆分（单体多模块 vs 微服务）
- **不要为了 DDD 而强行拆服务**

---

## 五、Environment 与 Experiment 的设计纠偏

### 原始设计的问题
项目初期设计存在三处自相矛盾：
1. **关系图错误**：Environment "包含" Experiment，但 Decision 2 明确 Experiment 是独立聚合根
2. **继承滥用**：`ExperimentEnvironment` 继承 `Environment` 聚合根，违反"聚合根不能被继承"原则
3. **ID 归属混乱**：`ExperimentEnvironment` 使用 `EnvironmentId`，暗示它本质上是 Environment 实例

### DDD 原则
- 聚合根之间不存在"包含"关系，只存在**通过 ID 引用**
- 聚合根不能被继承（Repository 困境、事务边界重叠、生命周期混乱）
- 组合优于继承

### 修正方案（独立聚合根 + ID 引用）
```java
// Experiment 聚合根：只引用 EnvironmentId，不持有对象
public class Experiment extends AggregateRoot<<ExperimentId> {
    private EnvironmentId dedicatedEnvironmentId; // 值对象引用
    private Hypothesis hypothesis;
    
    public void conclude(ExperimentDecision decision) {
        registerEvent(new ExperimentConcludedEvent(id, dedicatedEnvironmentId));
    }
}

// Environment 聚合根：专注资源管理
public class Environment extends AggregateRoot<<EnvironmentId> {
    private List<ServiceInstance> services;
}
```

**领域关系图修正**：
```
Experiment (AR) ──引用 EnvironmentId──► Environment (AR)
```

---

## 六、JPA @OneToOne(cascade=ALL) 的 DDD 违规

### 核心差异
| 维度 | @OneToOne 持对象 | DDD 推荐（EnvironmentId 值对象） |
|------|------------------|-------------------------------|
| 聚合边界 | ❌ 模糊 | ✅ 清晰 |
| 事务范围 | ❌ 隐式扩大（两个聚合根共享事务） | ✅ 各自独立 |
| 生命周期 | ❌ 耦合（orphanRemoval 自动级联删除） | ✅ 解耦（领域事件或应用层编排） |
| Repository 归属 | ❌ 混乱 | ✅ 明确 |

### 问题本质
`@OneToOne(cascade = CascadeType.ALL)` 让 JPA 的"实体关系"覆盖了 DDD 的"聚合边界"。Experiment 在内存中真实持有 Environment 对象引用，可以**直接修改**另一个聚合根，违反了"聚合根是外部访问唯一入口"的规则。

### 正确做法
```java
// 领域层：只持 ID
public class Experiment extends AggregateRoot<<ExperimentId> {
    private EnvironmentId dedicatedEnvironmentId;
}

// 基础设施层：JPA 映射为普通字段
@Entity
public class ExperimentJpaEntity {
    @Embedded
    private EnvironmentId dedicatedEnvironmentId;
    // ❌ 没有 @OneToOne Environment 映射
}
```

跨聚合操作通过 **Repository 显式加载** 或 **领域事件异步协调**。

---

## 七、Infrastructure 层的设计原则

### 核心原则：按领域划分，而非按技术划分
基础设施层的组织原则是**"围绕领域接口的实现来分包"**。

| 划分方式 | 问题 |
|---------|------|
| 按技术划分（`persistence/`, `virtualization/`, `ci/`） | 领域概念被技术分类肢解，修改时需跨包跳转 |
| 按领域划分（`environment/`, `experiment/`, `pipeline/`） | 同一领域的技术实现内聚，替换技术时只改一个包 |

### 推荐目录结构
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
│   └── publisher/
└── shared/               ← 纯技术工具（仅当多领域复用时）
    └── ssh/
```

### 判断标准
- 包含领域术语（`Environment`、`Experiment`）→ 放在对应领域的 infra 包
- 纯技术工具（`SshClient`、`JsonSerializer`）→ 放在 shared

---

## 八、InfrastructureProvider 接口的合理性分析

### 原始接口的问题
原始 `InfrastructureProvider` 是一个"技术大杂烩接口"，融合了：
- 环境生命周期（`provision` / `teardown`）→ 合理
- 容器管理（`startContainer` / `stopContainer`）→ **违规**，聚合根内部实体不应直接暴露
- 日志采集（`streamLogs`）→ 技术工具，不是领域行为
- 远程命令（`executeCommand`）→ 纯技术工具，与领域无关
- 健康轮询（`waitForHealthy`）→ 应是 `provision` 内部实现细节

### 正确拆分
领域层只保留**环境生命周期接口**：
```java
public interface EnvironmentProvisioner {
    Mono<<Environment> provision(EnvironmentSpec spec);
    Mono<Void> teardown(EnvironmentId id);
    Mono<<EnvironmentStatus> checkStatus(EnvironmentId id);
}
```

容器启停、日志读取、健康轮询、命令执行，全部作为**实现细节**，锁死在 `DockerComposeProvisioner` 等具体类内部。

---

## 九、异常分层设计

### 必须分开的三层异常
| 层级 | 异常类型 | 示例 |
|------|---------|------|
| Domain | 业务规则异常 | `EnvironmentNotReadyException`、`InvalidStateTransitionException` |
| Application | 用例编排异常 | `ExperimentNotFoundException` |
| Infrastructure | 技术故障异常 | `DockerComposeException`、`JpaOptimisticLockException` |

### 核心原则
- **领域层**不能感知技术（不能 import `JpaPersistenceException`）
- **基础设施层**负责"翻译"：技术异常 → 领域异常
- 调用方（ApplicationService）只捕获领域异常

### 翻译示例
```java
public class DockerComposeProvisioner implements EnvironmentProvisioner {
    @Override
    public Mono<<Environment> provision(EnvironmentSpec spec) {
        return executeDockerCompose(spec)
            .onErrorMap(DockerAPIException.class, e -> 
                new EnvironmentProvisionException("环境创建失败: " + e.getMessage(), e)
            );
    }
}
```

### 目录结构
```
domain/environment/exception/
    EnvironmentException.java
    InvalidStatusTransitionException.java

infrastructure/environment/exception/
    DockerComposeException.java
    PveApiException.java
```

---

## 十、基础设施启动方式的选择

### 三种模式
| 模式 | 命令 | 适用场景 |
|------|------|---------|
| 分开启动 | `docker-compose up` + `mvn spring-boot:run` | 日常开发迭代 |
| 合并启动 | 一个 `docker-compose.yml` 含 app + db | 演示部署、CI 集成测试 |
| 应用自托管 | `spring-boot-docker-compose` | 纯本地开发 |

### 分开启动的合理性
1. **数据库是有状态服务**，生命周期独立于应用，数据卷持久化
2. **开发迭代不需要重启数据库**，反馈循环秒级 vs 分钟级
3. **Portainer 是运维工具**，不是应用依赖，不应绑定应用生命周期

### Spring Boot 自动托管（推荐）
Spring Boot 3.1+ 提供 `spring-boot-docker-compose`：
```xml
<<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-docker-compose</artifactId>
    <scope>runtime</scope>
</dependency>
```

效果：`mvn spring-boot:run` 自动拉起 `docker-compose.yml` 中的依赖，应用停止时可配置是否销毁。

---

## 十一、防退化红线

### 常见退化路径
1. Domain 接口混入技术词汇（`saveAndFlush`、`findByIdWithLock`）
2. 接口变成"技术需求汇总表"
3. 领域层退化为 DAO 层，所有逻辑以"实现类"名义堆在 Infra

### 守住两条红线
1. **Domain 接口只有业务语义**，没有技术词汇（没有 `docker`、`kafka`、`flush`、`batch`）
2. **Infra 实现只翻译、不决策**，业务规则留在 Domain

**检查法**：如果把底层技术从 JPA 换成 MyBatis，或从 Docker 换成 K8s，**Domain 层的任何文件都不应该需要修改**。如果需要改，说明技术细节泄漏了。

---

## 十二、关键决策清单

| 决策项 | 推荐方案 | 理由 |
|--------|---------|------|
| Controller/Service/Domin 关系 | Controller → AppService → Domain | Domain 是内核，上层依赖下层 |
| Experiment 与 Environment | 独立聚合根，ID 引用 | 职责分离，生命周期差异，避免继承滥用 |
| Infra 层分包 | 按领域（`environment/`、`experiment/`） | 领域优先，技术后置 |
| 异常设计 | Domain / Application / Infra 三层分离 | 技术异常翻译为领域异常后上浮 |
| 数据库启动 | 开发期分开，CI 期合并 | 数据生命周期独立于应用 |
| 聚合根操作 | 禁止 `@OneToOne` 跨聚合级联 | 保持聚合边界清晰，事务独立 |

---

## 十三、一句话总结

> **DDD 的本质是让业务规则（Domain）独立于技术实现（Infra）。聚合根之间通过 ID 引用，不持有对象、不共享事务、不继承。Infra 层按领域分包，负责将技术细节翻译为领域契约。异常分层、防腐层厚度、充血模型，都是为了让业务语义在代码中清晰可见，不因技术选型变动而腐蚀。**