---
type: atomic-note
id: CONCEPT-领域边界-BoundedContext
created: 2026-05-22
updated: 2026-05-22
tags: [ddd, strategy]
status: 🌱
mastery: 0
related_emrg: [EMRG-DDD]
related_goal: []
---

# 领域边界（Bounded Context）

## 一句话定义
**领域边界是战略设计的核心**：解决"同一个名词在不同地方含义不同"的问题，通过划分业务子域来统一团队语言和职责。

## 核心理解

### 层次定位
| 概念 | DDD 层级 | 解决的问题 | 类比 |
|------|----------|-----------|------|
| **领域边界** | **战略设计** | 业务子域划分、团队分工、语言统一 | 国家边境 |
| 聚合根边界 | 战术设计 | 数据一致性、事务边界、并发控制 | 小区围墙 |

### 判断标准（什么时候需要划领域边界？）

**✅ 需要独立领域边界的信号**：
1. 同一个名词在不同上下文含义不同
   - 例："商品"在销售上下文 = 售价 + 标题
   - 例："商品"在库存上下文 = SKU + 库位
2. 由不同团队维护
3. 有独立部署/演进的需求

**❌ 不需要独立领域边界的情况**：
- 只是数据表不同但业务语义一致
- 仅为了技术解耦（如读写分离）— 这是战术层面的事

### 与微服务的关系
- 领域边界 **≠** 微服务边界
- 一个领域可以部署为单体多模块或多个微服务
- **先按逻辑划分（Bounded Context），再根据团队规模/部署需求决定是否物理拆分**

## 关键关联

- [[聚合根-AggregateRoot]] - 关联原因：聚合根是领域边界内的**战术设计**单元，一个领域包含多个聚合根
- [[EMRG-SpringCloud微服务]] - 关联原因：微服务拆分应该**遵循领域边界**，而不是反过来
- [[DDD分层架构]] - 关联原因：每个领域边界内都有独立的分层架构

## 我的误区与疑问

- ❓ 疑问：领域边界和模块化（Maven 多模块）有什么本质区别？
- ❓ 疑问：一个小项目（3-5 人）是否需要划分领域边界？

## 代码与实践

```java
// 错误示例：跨领域边界的直接引用
public class SalesService {
    @Autowired
    private InventoryRepository inventoryRepo; // ❌ 销售域直接依赖库存域
}

// 正确示例：通过领域事件或应用层编排
public class SalesService {
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    public void completeOrder(OrderId orderId) {
        eventPublisher.publishEvent(new OrderCompletedEvent(orderId));
        // 库存域监听此事件并扣减库存
    }
}
```

## 深入思考
💡 如果让你重新设计你当前项目的领域边界，你会如何划分？哪些地方存在"隐含的领域边界"但没有显式建模？

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
