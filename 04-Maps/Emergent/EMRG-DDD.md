---
type: emrg
id: EMRG-DDD
title: 领域驱动设计网络
maturity: theoretical
created: 2026-05-22
updated: 2026-05-22
related_goals: []
subtopics:
  - "领域边界-BoundedContext"
  - "DDD分层架构"
  - "聚合根-AggregateRoot"
  - "值对象-ValueObject"
  - "实体-Entity"
  - "聚合根引用规则"
  - "Infrastructure分包原则"
  - "领域接口纯洁性"
  - "异常分层设计"
  - "防退化红线"
---

# EMRG-DDD

> 成熟度: 🟡 theoretical

## 一句话定义
DDD 的本质是让**业务规则（Domain）独立于技术实现（Infra）**，通过战略设计划分边界、战术设计建模领域、实现约束防止退化。

## 知识拓扑

[战略层：为什么这样划分？]
├─ [[领域边界-BoundedContext]] — 业务子域、团队分工、语言统一
│  └─ 关联: [[EMRG-SpringCloud微服务]]（部署边界）
│
[战术层：如何建模领域？]
├─ [[DDD分层架构]] — Controller/AppService/Domain/Infra 依赖规则
│  ├─ [[聚合根-AggregateRoot]] — 一致性边界、事务粒度、Repository 归属
│  │  ├─ [[实体-Entity]] — 内部实体、局部 ID、生命周期依附
│  │  └─ [[值对象-ValueObject]] — EnvironmentId、不可变、无标识
│  └─ 关联: [[EMRG-Sentinel-核心机制]]（限流是领域逻辑？）
│
[实现约束层：代码如何保证不退化？]
├─ [[聚合根引用规则]] — ID 引用 vs 对象引用、禁止跨聚合级联
├─ [[Infrastructure分包原则]] — 按领域划分、技术细节锁死在实现类
├─ [[领域接口纯洁性]] — 无技术词汇、Infra 只翻译不决策
├─ [[异常分层设计]] — Domain/App/Infra 三层异常翻译
└─ [[防退化红线]] — 检查法：换技术实现是否需要改 Domain 层

## 关键缺口（待补充）
- [ ] 领域事件（Domain Event）与最终一致性
- [ ] 上下文映射（Context Mapping）模式
- [ ] 反腐层（Anti-Corruption Layer）实战
- [ ] 贫血模型 vs 充血模型对比

## 项目实战
| 项目 | 状态 | 关联笔记 |
|------|------|---------|
| DevOps Dashboard 设计复盘 | ✅ 设计阶段 | 本 EMRG 所有笔记均源自此复盘 |

## 关联领域
- [[EMRG-Sentinel-核心机制]] — 熔断/限流既是基础设施也是领域逻辑（跨界枢纽）
- [[EMRG-SpringCloud微服务]] — 微服务拆分依赖 DDD 领域边界
- [[EMRG-Reactive响应式编程]] — WebFlux 响应式编程与 Domain 层集成

---

## 🤖 AI 工作区（以下由 Dataview 自动维护，请勿手动编辑）

### 核心成员
```dataviewjs
const emrgId = dv.current().id;
dv.table(
  ["笔记", "mastery", "验证状态"],
  dv.pages()
    .where(p => p.related_emrg && p.related_emrg.includes(emrgId))
    .sort(p => p.mastery, 'desc')
    .map(p => [
      p.file.link,
      p.mastery ?? ' ',
      (p.mastery >= 60) ? '🟢' : '🟡'
    ])
);
```

### 边界声明

#### 边缘关联（链接但不纳入）
- [[工厂模式-Factory]] → 归属 `architecture` 通用设计模式
- [[策略模式-Strategy]] → 归属 `architecture` 通用设计模式
- [[服务注册与发现]] → 归属 [[EMRG-SpringCloud微服务]]
- [[PVE虚拟化]] → 归属 [[devops]]

#### 跨界枢纽（被多个 EMRG 引用）
- [[Sentinel-熔断机制]] — 同时被 [[EMRG-Sentinel-核心机制]] 和本 EMRG 引用（限流策略是领域规则还是基础设施？）

### 涌现历史
- 2026-05-22: 因用户主动整理 DDD 复盘笔记创建（涉及 10 个子主题，13 个知识节点）

### 成熟度说明

当前为 🟡 theoretical，原因：
- 所有概念均来自 DevOps Dashboard 项目**设计阶段复盘**，尚未经过完整编码验证
- 核心原则（聚合根 ID 引用、Infra 按领域分包）已在设计中决策，但未提交 Commit
- **升级路径**：当项目完成 Domain 层编码并通过 ArchUnit 测试后，升级为 verified

### 检查点
- [ ] 子主题数: 10（⚠️ 接近裂变阈值 7，建议关注是否需要分裂为「战略 DDD」和「战术 DDD」）
- [ ] 最后更新: 2026-05-22（归档检查起点）
