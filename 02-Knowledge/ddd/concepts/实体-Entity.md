---
type: atomic-note
id: CONCEPT-实体-Entity
created: 2026-05-22
updated: 2026-05-22
tags: [ddd, tactics]
status: 🌱
mastery: 0
related_emrg: [EMRG-DDD]
related_goal: []
---

# 实体（Entity）

## 一句话定义
**实体是有标识的可变对象**：通过唯一 ID 判断相等性，有独立生命周期（除了内部实体），状态随时间变化。

## 核心理解

### 实体的分类

#### 1. 聚合根实体（Aggregate Root）
- 全局唯一 ID
- 是聚合的**外部访问入口**
- 拥有独立的 Repository
- 一个事务的粒度

**例**：Order, Experiment, Environment

#### 2. 内部实体（非聚合根）
- 局部唯一 ID（仅在聚合内唯一）
- 外部不能直接引用
- 无独立 Repository，随聚合根一起持久化
- 生命周期依附于聚合根

**例**：OrderItem, ServiceInstance

### 实体 vs 值对象（完整对比）

| 维度 | 实体 (Entity) | 值对象 (Value Object) |
|------|--------------|---------------------|
| **标识** | ✅ 有 ID（全局或局部） | ❌ 无 ID |
| **相等性判断** | `this.id == other.id` | 所有字段逐一比较 |
| **可变性** | ✅ 可变（状态会变化） | ✅ 不可变 |
| **生命周期** | 独立（或依附于聚合根） | 依附于实体 |
| **替换方式** | 通过方法修改状态 | 整体替换 |
| **Repository** | 聚合根有，内部实体无 | 无 |

### 内部实体的设计原则

1. **只能被其所属聚合根操作**
   ```java
   // ❌ 错误：外部直接操作内部实体
   orderItem.setQuantity(5);
   
   // ✅ 正确：通过聚合根操作
   order.updateItemQuantity(itemSeq, 5);  // Order 保护不变量
   ```

2. **标识只在聚合内唯一**
   ```java
   public class OrderItem {
       private Long seq;  // 订单内序号，不是全局 ID
   }
   ```

3. **不能被其他聚合引用**
   - 如果需要引用，应该引用聚合根 ID，而不是内部实体 ID

## 关键关联

- [[聚合根-AggregateRoot]] - 关联原因：聚合根是特殊的实体，是聚合边界内的"老大"
- [[值对象-ValueObject]] - 关联原因：实体和值对象是 DDD 建模的两个基本元素，经常配合使用

## 我的误区与疑问

- ❌ 误区：以为所有 Entity 都必须有 Repository
- ❌ 误区：以为内部实体也可以被其他 Service 直接查询
- ❓ 疑问：什么时候应该把一个实体设计为聚合根，什么时候作为内部实体？

## 代码与实践

```java
// 聚合根实体
@Entity
public class Experiment extends AggregateRoot<ExperimentId> {
    @Id
    private ExperimentId id;                    // 全局唯一 ID
    
    @ElementCollection
    private List<Hypothesis> hypotheses;         // 内部实体集合
    
    public void addHypothesis(String description) {
        Hypothesis hypothesis = new Hypothesis(hypotheses.size() + 1, description);
        this.hypotheses.add(hypothesis);
    }
}

// 内部实体
@Embeddable
public class Hypothesis {
    private Long seq;                            // 局部 ID（在 Experiment 内唯一）
    private String description;
    private HypothesisStatus status;
    
    public void validate() {
        if (this.status != HypothesisStatus.PENDING) {
            throw new InvalidStateTransitionException("只有待验证的假设可以被验证");
        }
        this.status = HypothesisStatus.VALIDATED;
    }
}
```

## 深入思考
💡 你的项目中是否存在"本应该是内部实体但被错误地设计为聚合根"的情况？这会导致什么性能或一致性问题？

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
