---
type: atomic-note
id: CONCEPT-值对象-ValueObject
created: 2026-05-22
updated: 2026-05-22
tags: [ddd, tactics]
status: 🌱
mastery: 0
related_emrg: [EMRG-DDD]
related_goal: []
---

# 值对象（Value Object）

## 一句话定义
**值对象是没有标识的对象**：通过属性值判断相等性，不可变，用于描述领域中的一个特定方面（如金额、日期范围、ID）。

## 核心理解

### 值对象 vs 实体（核心区别）

| 维度 | 值对象 (Value Object) | 实体 (Entity) |
|------|---------------------|--------------|
| **标识** | ❌ 无 ID，通过属性值判断相等性 | ✅ 有全局/局部唯一 ID |
| **可变性** | ✅ 不可变（Immutable） | ✅ 可变（状态会变化） |
| **生命周期** | 依附于实体或聚合根 | 有独立生命周期 |
| **替换方式** | 整体替换（不能修改内部属性） | 通过方法修改状态 |
| **示例** | Money, EnvironmentId, DateRange | Order, Experiment, OrderItem |

### 值对象的特征

1. **不可变性**：创建后不能修改（所有字段 final）
2. **值语义**：`equals()` 和 `hashCode()` 基于所有属性值
3. **自验证性**：构造函数中校验不变量（如金额不能为负）
4. **可替换性**：整体替换，不能部分修改

### 在 DDD 中的关键作用

**跨聚合根引用的桥梁**：
```java
// Experiment 聚合根只引用 EnvironmentId 值对象
public class Experiment extends AggregateRoot<ExperimentId> {
    private EnvironmentId dedicatedEnvironmentId; // 值对象，不是 Environment 实体
}
```

**为什么用值对象而不是原始类型？**
- 类型安全：`EnvironmentId` 不能误传为 `ExperimentId`
- 自文档化：代码意图清晰
- 封装验证逻辑：构造时校验格式

## 关键关联

- [[聚合根-AggregateRoot]] - 关联原因：聚合根之间通过值对象（如 EnvironmentId）引用，而不是直接持有对象
- [[聚合根引用规则]] - 关联原因：值对象是实现"ID 引用"的关键技术手段
- [[实体-Entity]] - 关联原因：值对象和实体是 DDD 建模的两种基本元素

## 我的误区与疑问

- ❌ 误区：以为 String userId 就够了，不需要 UserId 值对象
- ❌ 误区：以为值对象可以有 setter 方法修改内部状态
- ❓ 疑问：值对象的性能开销如何？每次操作都创建新对象？

## 代码与实践

```java
// ✅ 正确的值对象实现
@Embeddable
public class EnvironmentId implements Serializable {
    private final String id;
    
    public EnvironmentId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("EnvironmentId 不能为空");
        }
        this.id = id.trim();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EnvironmentId)) return false;
        return id.equals(((EnvironmentId) o).id);
    }
    
    @Override
    public int hashCode() {
        return id.hashCode();
    }
    
    // 无 getter（防止外部获取后修改），只提供业务方法
    public String asString() {
        return id;
    }
}

// 使用场景
public class Experiment extends AggregateRoot<ExperimentId> {
    @Embedded
    private EnvironmentId dedicatedEnvironmentId;
    
    public void assignTo(EnvironmentId envId) {
        this.dedicatedEnvironmentId = Objects.requireNonNull(envId);
    }
}
```

## 深入思考
💡 你的项目中是否有应该建模为值对象但使用了原始类型（String/Long）的情况？这会导致什么问题？

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
