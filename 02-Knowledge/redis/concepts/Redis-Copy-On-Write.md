---
type: atomic-note
id: CONCEPT-Redis-Copy-On-Write
created: 2026-05-17
updated: 2026-05-17
tags: [redis, 存储, 内存管理, 操作系统]
status: 🌿
mastery: 55
related_emrg: [EMRG-Redis]
related_goal: [GOAL-Redis深入, GOAL-数据库性能优化]
---

# Copy-On-Write（写时复制）

## 一句话定义
Copy-On-Write（COW）是一种延迟复制策略：创建"副本"时只标记共享状态不实际拷贝数据，直到某一方尝试修改时才真正复制被修改的部分，用"按需复制"换取时间和空间的节省。


## 核心理解

### 快照 vs 备份的本质区别

**不是时间粒度的差异，而是复制时机和数据独立性的差异**：

| 维度 | 快照（Snapshot） | 备份（Backup） |
|------|-----------------|---------------|
| **复制时机** | 延迟复制（修改时才拷贝） | 立即全量复制 |
| **复制范围** | 增量（只拷贝被修改的数据块） | 全量（所有数据） |
| **数据独立性** | 依赖原始数据（原数据损坏则失效） | 完全独立 |
| **创建速度** | 瞬间完成（只标记元数据） | 耗时与数据量成正比 |
| **存储成本** | 低（共享未修改数据） | 高（完整副本） |

**类比**：
- **快照** = 拍照 + 便签（记录变化）。原房间烧了，照片就没用了。
- **备份** = 另一个完整的房间。原房间烧了也不怕。

### COW 的工作机制

```
初始状态：父进程和子进程共享同一页物理内存
         ┌─────────┐
父进程 ──→│ 数据页A  │←── 子进程
         └─────────┘
           共享（只读）

当父进程修改数据页A：
1. 触发页保护中断（Page Fault）
2. 内核复制数据页A → 数据页A'
3. 父进程指向 A'（可写）
4. 子进程仍指向 A（只读，保持快照一致性）

         ┌─────────┐
父进程 ──→│ 数据页A' │  （修改后的数据）
         └─────────┘
         ┌─────────┐
子进程 ──→│ 数据页A  │  （快照视图，保持不变）
         └─────────┘
```

**关键特性**：
- 未修改的数据页仍然共享，不占用额外内存
- 只有被修改的页才会复制（增量）
- 子进程看到的是一个"冻结"的一致性视图

### 快照链式依赖

多个快照可以形成链条：

```
Base → Snapshot1 → Snapshot2 → Snapshot3
```

- 每个快照只记录相对于前一个状态的差异
- **删除中间快照必须合并数据**：删除 Snapshot2 时，需要把 Snapshot2 的差异合并到 Snapshot3
- **与 Git commit 的区别**：Git commit 是独立的快照，可以单独删除；文件系统快照是链式依赖的

### Redis RDB 中的 COW

Redis 的 `BGSAVE` 命令使用 COW 创建子进程进行持久化：

1. 父进程（Redis）fork 子进程
2. 子进程共享父进程的内存页
3. 子进程将数据写入 RDB 文件（看到 fork 瞬间的一致性视图）
4. 父进程继续处理请求，修改数据时触发 COW

**为什么 Redis 需要 COW？**
- 如果不使用 COW：创建副本需要拷贝全部内存（数 GB），阻塞主进程数秒
- 使用 COW：fork 瞬间完成，只有被修改的页才会复制

**注意**：COW 不是零成本。如果 BGSAVE 期间父进程大量修改数据，COW 会导致内存占用翻倍。这也是为什么 Redis 在 BGSAVE 期间提高 rehash 阈值（减少内存变动）。

### Java 中的 COW

Java 的 `CopyOnWriteArrayList` 名字里有 COW，但机制不同：

| 维度 | 操作系统 COW | CopyOnWriteArrayList |
|------|-------------|---------------------|
| **复制粒度** | 页级（4KB） | 整个数组 |
| **复制时机** | 写操作时 | 写操作时 |
| **复制范围** | 增量（只改被修改的页） | 全量（整个数组） |
| **适用场景** | 进程间共享内存 | 读多写少的并发集合 |

**关键区别**：操作系统 COW 是"增量复制"，Java COW 是"全量复制"。两者都是"写时才复制"，但粒度完全不同。


## 关键关联

- [[Redis-持久化]] - 关联原因：Redis RDB 的 BGSAVE 命令是 COW 的经典应用场景，fork 子进程后共享内存页
- [[Redis-渐进式rehash]] - 关联原因：Redis 在 BGSAVE 期间提高 rehash 阈值（从 1 到 5），避免 COW 期间不必要的内存页复制
- [[CopyOnWriteArrayList]] - 关联原因：Java 的 COW 是全量复制数组，与操作系统页级 COW 的增量复制形成对比
- [[Git版本控制]] - 关联原因：Git commit 是独立快照可单独删除，文件系统快照是链式依赖删除中间节点需合并


## 我的误区与疑问

- ❌ 误区：以为快照和备份只是时间粒度不同（实际是复制时机和数据独立性不同）
- ❌ 误区：以为 Java CopyOnWriteArrayList 和操作系统 COW 机制相同（实际是页级增量 vs 数组全量）
- ❓ 疑问：COW 在极端写入场景下是否会导致内存翻倍？（答：是的，如果所有页都被修改，相当于完整复制了一份）
- ❓ 疑问：为什么删除快照链中间节点需要合并数据？（答：因为后续快照的差异是基于中间快照计算的，中间快照消失后差异就失去基准）


## 代码与实践

```bash
# Linux 查看 COW 行为（需要 root）
# 观察 fork 后的内存占用变化
watch -n 1 cat /proc/$(pgrep redis-server)/status | grep VmRSS

# 创建 LVM 快照（体验快照链式依赖）
sudo lvcreate -L 1G -s -n mysnap /dev/vg0/data
sudo lvremove /dev/vg0/mysnap  # 删除快照
```

```java
// Java CopyOnWriteArrayList（全量复制）
CopyOnWriteArrayList<String> list = new CopyOnWriteArrayList<>();
list.add("a");  // 复制整个数组，新数组长度+1
list.add("b");  // 再次复制整个数组
// 读操作：直接读取当前数组，无锁
```


## 深入思考

💡 COW 与 MVCC（多版本并发控制）有什么异同？两者都实现了"读不阻塞写"，但实现机制和应用场景有何区别？
💡 如果 Redis 不使用 COW，还有哪些替代方案可以实现不阻塞的持久化？（AOF 重写、日志结构合并树？）
💡 云存储（AWS EBS、阿里云盘）的快照计费模式为什么基于"差异数据量"而非"总数据量"？


## 来源
- 项目：[[ansible-redis-cluster]]（BGSAVE 和持久化配置）
- 对话：[[2026-04-29-快照与备份对话]]


---

## 🤖 AI评价

### 掌握度评估
- 当前等级：🌿理解
- 更新记录：
  - 2026-05-17: mastery=55 (从对话中萃取出 COW 机制、快照与备份的本质区别、快照链式依赖、Java COW 与操作系统 COW 的对比)

---

```dataview
TABLE status, mastery, length(file.inlinks) as "入链", length(file.outlinks) as "出链"
FROM #redis and #存储
SORT mastery DESC
```
