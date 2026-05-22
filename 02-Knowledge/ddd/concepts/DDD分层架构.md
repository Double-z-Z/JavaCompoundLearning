---
type: atomic-note
id: CONCEPT-DDD分层架构
created: 2026-05-22
updated: 2026-05-22
tags: [ddd, tactics]
status: 🌱
mastery: 0
related_emrg: [EMRG-DDD]
related_goal: []
---

# DDD 分层架构

## 一句话定义
**分层架构的依赖规则**：上层可以调用下层，下层不能调用上层。Domain 层是不依赖任何其他层的"纯 POJO 内核"。

## 核心理解

### 四层结构（从上到下）

| 层级 | 组件 | 职责 | 依赖关系 |
|------|------|------|---------|
| **用户界面层** | Controller / REST API | 接收请求，参数校验 | → 应用层 |
| **应用层** | Application Service | 用例编排、事务控制、DTO 转换 | → 领域层 |
| **领域层** | Entity, Value Object, Domain Service, Repository Interface | 业务规则、不变量保护 | ← 无依赖 |
| **基础设施层** | Repository Impl, DB, MQ, Cache | 技术实现 | → 领域层接口 |

### 两种 Service 的本质区别（⚠️ 高频混淆点）

| 维度 | Application Service | Domain Service |
|------|-------------------|----------------|
| **位置** | 应用层（Domain 之上） | 领域层（与 Entity 平行） |
| **职责** | 编排用例、协调多个 Domain 对象 | 处理不适合放在单个 Entity 中的纯领域逻辑 |
| **包含内容** | save(), findById(), DTO 转换, 权限检查 | 无状态，只操作领域对象 |
| **能否接触基础设施** | ✅ 可以（通过 Repository） | ❌ 不能 |

### 核心原则
**Domain 层是整个架构的"内核"**：
- 不 import 任何 Spring/Servlet/JPA 注解
- 不依赖任何其他三层
- 是纯 POJO，可以脱离框架单独编译测试

## 关键关联

- [[聚合根-AggregateRoot]] - 关联原因：聚合根位于 Domain 层核心，Application Service 通过 Repository 操作聚合根
- [[领域接口纯洁性]] - 关联原因：Domain 层接口必须保持纯洁，不能泄漏技术细节
- [[异常分层设计]] - 关联原因：不同层抛出不同类型的异常，Domain 层只抛业务异常

## 我的误区与疑问

- ❌ 误区：以为 Controller 直接调用 Domain Service（跳过 Application Service）
- ❌ 误区：以为 Domain Service 可以调用 Repository（实际上只有 AppService 可以）

## 代码与实践

```java
// ✅ 正确的分层调用链
@RestController
public class ExperimentController {
    @Autowired
    private ExperimentApplicationService experimentService;
    
    @PostMapping("/experiments")
    public ResponseEntity<ExperimentDTO> create(@RequestBody CreateExperimentCommand cmd) {
        return ResponseEntity.ok(experimentService.create(cmd)); // Controller → AppService
    }
}

@Service
public class ExperimentApplicationService {
    @Autowired
    private ExperimentRepository experimentRepository; // AppService → Repository(Infra)
    @Autowired
    private EnvironmentRepository environmentRepository;
    
    @Transactional
    public ExperimentDTO create(CreateExperimentCommand cmd) {
        // 编排用例：创建实验 + 检查环境状态
        Environment env = environmentRepository.findById(cmd.getEnvironmentId());
        Experiment experiment = Experiment.create(cmd, env.getId());
        return experimentRepository.save(experiment);
    }
}

// Domain 层：纯 POJO，无任何框架依赖
public class Experiment extends AggregateRoot<ExperimentId> {
    private Hypothesis hypothesis;
    
    public void conclude(ExperimentDecision decision) {
        if (!canConclude()) {
            throw new InvalidStateTransitionException("实验状态不允许结束");
        }
        registerEvent(new ExperimentConcludedEvent(id));
    }
}
```

## 深入思考
💡 如果把底层技术从 JPA 换成 MyBatis，或者从 Spring Boot 换成 Quarkus，你的 Domain 层需要修改吗？如果需要改，说明什么？

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
