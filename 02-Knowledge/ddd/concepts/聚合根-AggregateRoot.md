---
type: atomic-note
id: CONCEPT-聚合根-AggregateRoot
created: 2026-05-22
updated: 2026-05-22
tags: [ddd, tactics]
status: 🌱
mastery: 0
related_emrg: [EMRG-DDD]
related_goal: []
---

# 聚合根（Aggregate Root）

## 一句话定义
**聚合根是一致性边界**：它是外部访问聚合的唯一入口，负责维护聚合内所有不变量（invariant），是一个事务的粒度。

## 核心理解

### 聚合根 vs 非聚合根（内部实体）

| 维度 | 聚合根 | 非聚合根（内部实体） |
|------|--------|-------------------|
| **标识范围** | 全局唯一 ID | 局部唯一 ID（仅在聚合内唯一） |
| **外部访问入口** | ✅ 是 | ❌ 外部不能直接引用 |
| **Repository** | 拥有独立的 Repository | 无独立 Repository，随聚合根一起持久化 |
| **事务边界** | 一个聚合根 = 一个事务边界 | 随聚合根事务一起提交 |
| **跨聚合引用** | 通过 ID 引用其他聚合根 | 不能直接被外部聚合引用 |

### 一致性边界的含义

**聚合的本质 = 一致性边界**：

1. **不变量保护**：聚合根负责保证聚合内的业务规则始终成立
   - 例：Order（聚合根）必须保证 OrderItems 总额 = Order.totalAmount
   
2. **并发控制粒度**：乐观锁应该在聚合根级别，而不是内部实体级别
   - 例：`UPDATE orders SET version = version + 1 WHERE id = ? AND version = ?`

3. **事务边界**：一个事务只修改一个聚合，跨聚合通过**最终一致性**同步

### 代码体现

```java
// 聚合根：全局 ID，独立 Repository
public class Order extends AggregateRoot<OrderId> {
    private OrderId id;                    // 全局唯一
    private List<OrderItem> items;         // 内部实体集合
    private Money totalAmount;
    
    public void addItem(Product product, int quantity) {
        OrderItem item = new OrderItem(items.size() + 1, product, quantity); // 局部 seq
        this.items.add(item);
        this.recalculateTotal();           // 保护不变量：总额 = 明细之和
    }
    
    private void recalculateTotal() {
        this.totalAmount = items.stream()
            .map(item -> item.getSubtotal())
            .reduce(Money.ZERO, Money::add);
    }
}

// 内部实体：局部 ID，无独立 Repository
public class OrderItem {
    private Long seq;                      // 仅在 Order 内唯一
    private ProductId productId;
    private int quantity;
    private Money price;
    
    public Money getSubtotal() {
        return price.multiply(quantity);
    }
}
```

## 关键关联

- [[实体-Entity]] - 关联原因：内部实体是聚合的组成部分，生命周期依附于聚合根
- [[值对象-ValueObject]] - 关联原因：聚合根和实体都引用值对象（如 Money、EnvironmentId）
- [[聚合根引用规则]] - 关联原因：聚合根之间只能通过 ID 引用，不能持有对象引用

## 我的误区与疑问

- ❌ 误区：以为聚合根之间可以用 @OneToOne / @ManyToOne 直接关联
- ❌ 误区：以为内部实体也可以有自己的 Repository
- ❓ 疑问：聚合根多大合适？一个聚合根包含多少个内部实体合理？

## 代码与实践

```java
// ❌ 错误：聚合根之间的对象引用
public class Experiment extends AggregateRoot<ExperimentId> {
    @OneToOne(cascade = CascadeType.ALL)
    private Environment environment;  // 违反聚合边界！
}

// ✅ 正确：聚合根之间的 ID 引用
public class Experiment extends AggregateRoot<ExperimentId> {
    private EnvironmentId dedicatedEnvironmentId;  // 只持 ID，不持对象
    
    public EnvironmentId getEnvironmentId() {
        return this.dedicatedEnvironmentId;
    }
}
```

## 深入思考
💡 你的项目中是否存在"伪聚合根"？即虽然标记为 Aggregate Root，但实际上被其他聚合根直接持有对象引用？

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
