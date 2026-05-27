---
type: atomic-note
id: CONCEPT-Redis-Ziplist
created: 2026-05-27
updated: 2026-05-27
tags: [redis, 数据结构, 内存管理]
status: 🌿
mastery: 70
related_emrg: [EMRG-Redis]
related_goal: [GOAL-Redis深入]
---

# Redis Ziplist（压缩列表）

## 一句话定义

Ziplist 是 Redis 为小数据场景设计的连续内存编码结构，将多个元素紧凑打包在一块连续内存中，通过变长编码追求极致空间效率，代价是 O(n) 的遍历和写操作中可能触发的连锁更新。

## 核心理解

### 整体布局

```
<zlbytes> <zltail> <zllen> <entry1> <entry2> ... <entryN> <zlend>
   4B        4B       2B                              1B(0xFF)
```

| 字段 | 大小 | 作用 |
|------|------|------|
| `zlbytes` | 4 字节 | 整个 ziplist 占用字节数 |
| `zltail` | 4 字节 | 最后一个 entry 的偏移量（支持尾部插入） |
| `zllen` | 2 字节 | entry 数量（>65535 时退化，需遍历获取） |
| `zlend` | 1 字节 | 结束标记 `0xFF` |

### Entry 结构（变长编码）

```
[prevlen] [encoding] [data]
    ↑          ↑         ↑
前一个entry  当前data  实际内容
的长度      的类型+长度
```

**prevlen**：

| prevlen 值 | 编码长度 | 含义 |
|-----------|---------|------|
| < 254 | 1 字节 | 直接存长度 |
| 254 (0xFE) | 1 + 4 = 5 字节 | 后 4 字节存真实长度 |

**encoding**：高 2 位区分类型 — `00`(6bit整数)、`01`(14bit整数)、`10`(字符串，后续字节存长度)、`11`(整数，后续字节存具体编码)。

### 导航方式：无指针的双向遍历

```
正向：next = current + sizeof(current_entry)
反向：prev = current - prevlen
```

没有 `next` 指针，全靠整数运算推算位置。这和网络协议中的"消息长度字段确定消息边界"思路一致。

### 连锁更新（Cascade Update）

Ziplist 最经典的工程缺陷。触发条件：在前面插入一个 ≥254 字节的元素。

```
插入大元素 → 下一个 entry 的 prevlen 从 1B 膨胀到 5B 
→ 这个 entry 变大了 4B → 再下一个的 prevlen 也可能膨胀 
→ 多米诺骨牌效应，最坏情况全量重写
```

这就是 QuickList 出现的直接原因：用多个小 Ziplist 串联，把连锁更新的爆炸半径限制在单个节点内。

### 缓存优势

所有 entry 挤在连续内存中，小数据集（<512 entry）全部落在 2-3 个 cache line 里。一次加载，CPU 后续操作全走缓存。

hashtable 做一次 HINCRBY 背后多次指针跳转，每次都可能命中不同 cache line → 多次主存访问。所以 **n=100 时，O(n) 遍历 + 全缓存 可能比 O(1) 哈希 + 多次访存更快**。512 的阈值不是拍脑袋。

### 写的代价

修改一个 entry 如果长度变了，后面所有 entry 都要 `memmove` 移位。Ziplist 不适合频繁写。

## 适用边界

| 场景 | 适合 | 不适合 |
|------|------|--------|
| 数据量 | 小（<512 entry） | 大 |
| 元素大小 | 小（<64 字节） | 大 |
| 读/写比例 | 读多写少 | 频繁写 |
| 插入位置 | 尾部追加 | 头部/中间插入 |
| 数据类型 | Hash / ZSet 小数据 | List（已被 QuickList 替代） |

## 关键关联

- [[Redis-QuickList设计]] - 关联原因：List 3.2+ 用 QuickList（链表 + Ziplist）替代纯 Ziplist，把连锁更新锁在 4KB 节点内
- [[Redis-SDS设计]] - 关联原因：都是连续内存 + 变长设计的思路，SDS 存单个字符串，Ziplist 存多元素
- [[Redis-数据类型与编码]] - 关联原因：Ziplist 是 Hash/ZSet 小数据时的内部编码，超过阈值自动切换
- [[网络协议-消息边界]] - 关联原因：通过长度字段确定元素边界的思路与网络协议设计一致
- [[Java-ArrayList]] - 关联原因：同样基于连续内存，同样写操作需要 memmove，同样有扩容成本

## 深入思考

💡 Ziplist 的 prevlen 设计是为了支持反向遍历 — 但 Redis 单线程命令处理基本不需要反向遍历，这个设计是否过度？
💡 如果去掉 prevlen，改用 forward-only 设计，连锁更新问题就不存在了 — 代价是什么？
💡 Ziplist 的设计哲学是"内存极度紧凑"，SDS 是"安全 + 性能"，为什么同样在 Redis 内部，两种结构的优先级不同？

## 来源

- 对话：2026-05-27 SDS/Ziplist 苏格拉底式对话
- 相关笔记：[[Redis-SDS设计]]、[[Redis-数据类型与编码]]

---

## 更新记录

| 日期 | 操作 |
|------|------|
| 2026-05-27 | 创建，mastery=70（从 SDS 对话延伸，深入分析了结构设计、连锁更新、缓存优势、适用边界） |
