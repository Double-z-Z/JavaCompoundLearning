---
type: atomic-note
id: CONCEPT-Redis-渐进式rehash
created: 2026-05-17
updated: 2026-05-17
tags: [redis, 数据结构, 哈希表, 并发]
status: 🌿
mastery: 60
related_emrg: [EMRG-Redis]
related_goal: [GOAL-Redis深入, GOAL-Java核心深化]
---

# Redis 渐进式 rehash

## 一句话定义
渐进式 rehash 是 Redis 在单线程模型下将哈希表扩容/缩容的迁移操作分摊到每次增删改查中，每次只迁移一个桶，避免一次性拷贝所有数据造成的长时间阻塞。


## 核心理解

### 为什么需要渐进式？

Redis 的核心操作都在单线程事件循环中执行。如果像 Java HashMap 那样一次性 rehash：

- 百万级键值对可能需要数十毫秒
- 期间 Redis 无法响应任何客户端请求
- 对于内存数据库，这等同于"宕机"

**设计约束**：单线程模型下，任何长时间操作都是不可接受的。

### 渐进式 rehash 的工作机制

Redis 维护**两个哈希表**：`ht[0]`（旧表）和 `ht[1]`（新表）。

```
扩容触发条件：
- 无 BGSAVE/BGREWRITEAOF 时：used / size >= 1
- 有 BGSAVE/BGREWRITEAOF 时：used / size >= 5
（避免 copy-on-write 期间不必要的内存复制）
```

**迁移过程**：
1. 为 `ht[1]` 分配 2×size 的空间
2. **每次增删改查操作**，顺带将 `ht[0]` 的 `rehashidx` 指向的桶迁移到 `ht[1]`
3. 迁移完成后，`ht[1]` 变为 `ht[0]`，释放旧表

### 查询时的"双表查找"

渐进式 rehash 期间，查询操作需要查两张表：

```c
dictEntry *dictFind(dict *d, const void *key) {
    // 先查旧表
    if ((he = ht[0].table[h]) != NULL) return he;
    // 再查新表（仅在 rehash 期间）
    if (isRehashing() && (he = ht[1].table[h]) != NULL) return he;
    return NULL;
}
```

**关键细节**：新插入的数据**只写入 `ht[1]`**，保证不会重复迁移。

### 与 Java HashMap 的对比

| 维度 | Redis 渐进式 rehash | Java HashMap |
|------|-------------------|--------------|
| **触发时机** | 负载因子 ≥ 1（或 5） | 负载因子 ≥ 0.75 |
| **迁移方式** | 分摊到每次操作，每次一个桶 | 一次性全量迁移 |
| **查询策略** | 双表查找 | 单表查询 |
| **线程模型** | 单线程，必须避免阻塞 | 多线程，需快速完成释放锁 |
| **新数据写入** | 只写入新表 `ht[1]` | 写入新表 |
| **时间复杂度** | 均摊 O(1)，无单次长尾 | 扩容时单次 O(n) |

**核心洞察**：设计选择取决于运行环境。单线程模型适合"细水长流"，多线程模型适合"速战速决"。

### 缩容的渐进式

Redis 的缩容也是渐进的：
- 当 `used / size < 0.1` 时触发
- 同样使用 `ht[1]` 渐进迁移
- 但 Redis 默认**不启用自动缩容**（`activerehashing` 只控制定时任务，不控制阈值）

**原因**：缩容节省的内存有限，而 rehash 有 CPU 开销；内存相对便宜，稳定性更优先。


## 关键关联

- [[Redis-数据类型与编码]] - 关联原因：Hash 类型在元素过多时从 ziplist 切换为 hashtable，触发 rehash
- [[HashMap]] - 关联原因：两者都是哈希表实现，但 Redis 渐进式 vs Java 一次性，体现了单线程与多线程的设计差异
- [[Copy-On-Write]] - 关联原因：Redis 在 BGSAVE 期间提高 rehash 阈值（从 1 到 5），避免 COW 期间的内存复制
- [[NIO-Selector]] - 关联原因：单线程事件循环模型下，任何长时间操作都会阻塞整个系统，与 NIO 的 Reactor 模式面临同样的设计约束


## 我的误区与疑问

- ❌ 误区：以为渐进式 rehash 期间新数据会写入旧表（实际只写入新表，避免重复迁移）
- ❓ 疑问：如果 rehash 期间没有任何增删改查操作，迁移会停滞吗？（答：Redis 有定时任务 `serverCron` 每 100ms 主动推进 100 步）
- ❓ 疑问：为什么 Java 不采用渐进式 rehash？（答：多线程环境下渐进式需要复杂的同步机制，一次性扩容+快速释放锁更简单）


## 代码与实践

```bash
# 查看哈希表大小和键数量
DBSIZE
INFO keyspace

# 手动触发 rehash（调试用）
# Redis 4.0+ 提供 ACTIVEREHASHING 配置
CONFIG SET activerehashing yes
```

```c
// 渐进式 rehash 核心逻辑（简化版）
int dictRehashStep(dict *d) {
    if (!dictIsRehashing(d)) return 0;

    // 每次迁移一个桶（可能包含多个entry）
    while(d->rehashidx < d->ht[0].size) {
        if (d->ht[0].table[d->rehashidx] != NULL) {
            migrateBucket(d, d->rehashidx);
            d->rehashidx++;
            return 1;  // 迁移了一个桶
        }
        d->rehashidx++;
    }

    // 完成，交换 ht[0] 和 ht[1]
    d->ht[0] = d->ht[1];
    resetHT(d->ht[1]);
    d->rehashidx = -1;
    return 0;
}
```


## 深入思考

💡 渐进式 rehash 的"每次一个桶"策略，在极端情况下（单个桶有百万级冲突）是否仍然会阻塞？
💡 如果 Redis 改为多线程模型，渐进式 rehash 是否还有必要？
💡 一致性哈希的平滑迁移与渐进式 rehash 有什么异同？


## 来源
- 项目：[[redis-counter-service]]（Hash 结构大量使用）
- 对话：[[2026-04-29-Redis数据结构对话]]


---

## 🤖 AI评价

### 掌握度评估
- 当前等级：🌿理解
- 更新记录：
  - 2026-05-17: mastery=60 (从对话中萃取出渐进式 rehash 机制、双表查找策略、与 Java HashMap 的设计对比)

---

```dataview
TABLE status, mastery, length(file.inlinks) as "入链", length(file.outlinks) as "出链"
FROM #redis and #哈希表
SORT mastery DESC
```
