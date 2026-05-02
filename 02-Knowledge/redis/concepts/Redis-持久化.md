---
created: 2026-05-01
tags: [redis, 持久化, 高可用]
status: 🌿
mastery: 65
---

# Redis 持久化机制

## 一句话定义
Redis 提供 RDB（快照）和 AOF（日志）两种持久化方式，分别对应"定期拍照"和"实时录像"策略，可独立或组合使用，在数据安全与性能之间动态权衡。


## 核心理解

### RDB（Redis Database）- 快照持久化

**原理**：在指定时间点将内存数据集快照写入磁盘。

```
触发时机：
├── 手动触发：SAVE（阻塞）、BGSAVE（后台）
├── 自动触发：配置 save 条件（如 save 900 1）
└── 其他：shutdown、主从复制时
```

**执行流程（BGSAVE）**：
1. 父进程 fork 出子进程（Copy-On-Write）
2. 子进程遍历内存数据集，写入临时 RDB 文件
3. 完成后替换旧 RDB 文件

**关键特性**：
| 特性 | 说明 |
|------|------|
| 文件格式 | 紧凑的二进制文件 |
| 恢复速度 | 快（直接加载到内存） |
| 数据安全 | 可能丢失最后一次快照后的数据 |
| 对性能影响 | fork 时短暂阻塞，后续无影响 |

### AOF（Append Only File）- 日志持久化

**原理**：将每个写操作命令追加到日志文件，重启时重新执行命令恢复数据。

**刷盘策略（appendfsync）**：
| 策略 | 说明 | 数据安全 | 性能 |
|------|------|---------|------|
| `always` | 每个命令都 fsync | 最高（不丢数据） | 最低 |
| `everysec` | 每秒 fsync（默认） | 可能丢1秒数据 | 平衡 |
| `no` | 由OS决定 | 最低 | 最高 |

**AOF 重写（Rewrite）**：
- 问题：AOF 文件会不断增长
- 解决：定期重写 AOF，压缩命令（如 100 次 INCR 合并为 1 次 SET）
- 机制：fork 子进程，生成新的精简 AOF 文件

### 混合持久化（Redis 4.0+）

```
AOF 文件结构：
├── RDB 头部（全量数据快照）
└── AOF 尾部（增量命令）
```

**优势**：
- 恢复时先加载 RDB（快），再重放 AOF 尾部（少）
- 兼顾 RDB 的速度和 AOF 的安全性

### RDB vs AOF 对比

| 维度 | RDB | AOF |
|------|-----|-----|
| **文件体积** | 紧凑（经压缩） | 较大（纯文本命令） |
| **恢复速度** | 快 | 慢（需重放命令） |
| **数据安全** | 可能丢数据 | 更安全（取决于策略） |
| **性能影响** | fork 时短暂阻塞 | 持续写盘开销 |
| **可读性** | 二进制，不可读 | 文本格式，可读 |


## 关键关联

- [[Redis-数据类型与编码]] - 关联原因：RDB 持久化时保留数据的内部编码（ziplist/intset 等），加载时直接还原
- [[Redis-主从复制]] - 关联原因：全量同步使用 BGSAVE 生成 RDB 传输给 Slave
- [[fork-系统调用]] - 关联原因：BGSAVE 和 AOF 重写都依赖 fork() + COW 机制
- [[NIO-Buffer]] - 关联原因：AOF 的 `everysec` 策略类似于批量刷盘的权衡思想


## 我的误区与疑问

- ❌ 误区：以为 RDB 和 AOF 必须二选一（实际可以都开启）
- ❓ 疑问：AOF 重写期间的新命令如何处理？（写入缓冲区，最后追加）


## 代码与实践

```bash
# 查看持久化配置
CONFIG GET save
CONFIG GET appendonly

# 手动触发 RDB
SAVE        # 阻塞
BGSAVE      # 后台

# 查看 AOF 文件大小
ls -lh appendonly.aof

# AOF 重写
BGREWRITEAOF
```


## 深入思考

💡 为什么 Redis 选择多种编码而不是单一通用结构？
💡 在生产环境中，如何选择 RDB/AOF/混合持久化的组合？
💡 COW 机制在高写入场景下的内存膨胀问题如何缓解？


## 来源

- 项目：[[netty-chatroom]]（后续 Redis 持久化改造）
- 对话：[[2026-05-01-Redis持久化与Cluster对话]]


---

## 🤖 AI评价

### 掌握度评估
- 当前等级：🌿理解
- 更新记录：
  - 2026-05-01: mastery=65 (深入理解 RDB/AOF/混合持久化原理，掌握 fork+COW 机制)

### 建议下一步
1. 实践 Jedis 操作并观察 AOF 文件变化
2. 学习 Redis 主从复制与持久化的结合
3. 阅读 Redis 源码中的 rdbSave 实现

---

```dataview
TABLE status, mastery, length(file.inlinks) as "入链", length(file.outlinks) as "出链"
FROM #redis
SORT mastery DESC
```
