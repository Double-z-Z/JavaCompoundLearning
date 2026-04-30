---
created: 2026-04-29
tags: [redis, 数据结构]
status: 🌿
mastery: 55
---

# Redis 数据类型与内部编码

## 一句话定义
Redis 5种基础数据类型（String/Hash/List/Set/ZSet）根据数据特征自动选择最优内部编码，在空间效率和操作性能之间动态权衡。


## 核心理解

### 编码设计哲学

Redis 采用"运行时自适应编码"策略：
- **小数据**：紧凑编码（ziplist/intset），节省内存
- **大数据**：高效编码（hashtable/skiplist），保证性能
- **透明切换**：用户无感知，自动升级/降级

### 5种数据类型编码对照

| 类型 | 小数据编码 | 大数据编码 | 切换阈值 |
|-----|-----------|-----------|---------|
| **String** | int/embstr | raw | 44字节/整数范围 |
| **Hash** | ziplist | hashtable | 512字段/64字节 |
| **List** | quicklist(ziplist) | quicklist(大ziplist) | 配置-2(8KB) |
| **Set** | intset | hashtable | 512元素/整数 |
| **ZSet** | ziplist | skiplist+hashtable | 128元素/64字节 |

### 关键编码详解

#### ziplist - 通用紧凑结构
```
布局：<zlbytes><zltail><zllen><entry>...<entry><zlend>
entry: <prevlen><encoding><content>
```
- 连续内存，CPU缓存友好
- 变长编码，小数据极度紧凑
- 插入/删除需内存拷贝 O(n)

#### intset - 整数集合
- 有序数组，支持二分查找
- 编码升级（int16→int32→int64）
- 只存整数，内存极省

#### skiplist - 跳表
- 概率平衡，实现简单
- 查询/插入/删除 O(log n)
- 支持范围查询和排名查询


## 关键关联

- [[Redis-持久化]] - 关联原因：RDB/AOF持久化时需考虑编码格式
- [[HashMap]] - 关联原因：Redis hashtable与Java HashMap设计对比
- [[时间复杂度分析]] - 关联原因：不同编码的操作复杂度差异
- [[内存管理]] - 关联原因：编码切换的内存权衡


## 我的误区与疑问

- ❌ 误区：以为编码是按业务类型分类（实际是按运行时数据特征）
- ❌ 误区：以为44字节是SDS最小值（实际是jemalloc 64字节桶的最优利用）
- ❓ 疑问：ziplist的内存碎片真的比hashtable少吗？（预分配减少分配次数）


## 代码与实践

```bash
# 查看编码类型
OBJECT ENCODING mykey

# String编码示例
SET key "123"          # int编码
SET key "hello"        # embstr编码（<44字节）
SET key "a".repeat(100) # raw编码（>44字节）

# Hash编码切换
HSET hash field1 value1  # ziplist
# 超过阈值后自动转为hashtable
```


## 深入思考

💡 为什么Redis选择多种编码而不是单一通用结构？
💡 跳表的随机层数设计如何保证期望平衡？
💡 渐进式rehash和Java HashMap的一次性扩容各有什么优劣？


## 来源
- 项目：[[netty-chatroom]]（后续Redis持久化改造）
- 对话：[[2026-04-29-Redis数据结构对话]]


---

## 🤖 AI评价

### 掌握度评估
- 当前等级：🌿理解
- 更新记录：
  - 2026-04-29: mastery=55 (深入理解5种数据类型编码原理和跳表算法)

### 建议下一步
1. 实践Jedis操作5种数据类型
2. 学习Redis持久化机制（RDB/AOF）
3. 阅读Redis源码中的ziplist/skiplist实现

---

```dataview
TABLE status, mastery, length(file.inlinks) as "入链", length(file.outlinks) as "出链"
FROM #redis
SORT mastery DESC
```
