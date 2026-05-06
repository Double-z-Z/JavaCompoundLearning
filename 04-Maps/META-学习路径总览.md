---
type: moc
description: Java学习路径可视化图谱 - 展示知识深度与递进关系
created: 2026-05-06
---

# Java 学习路径图谱

> 基于 profile.md 和 current.json 自动生成
> 展示学习深度递进与分支选择

---

## 学习路径总览（Mermaid Mindmap）

```mermaid
mindmap
  root((Java全栈学习路径))
    🍎并发编程
      L3 80分
      futex
      LongAdder
      线程池
      分段锁思想
    🍎NIO网络
      Buffer 70
      Channel 50
      Selector 70
      Netty 65
    🍎Redis
      持久化 70
      主从复制 70
      Cluster 85
    🌿DevOps
      Ansible 70
      Docker 40
      K8s 25
    ⏳JVM
      内存模型 40
      GC算法 40
    ⏳架构
      CAP定理 45
      高并发设计 45
```

### 传统Flowchart视图

```mermaid
graph TB
    java["🌱 Java基础"] --> concurrent["🍎 并发 L3"]
    java --> nio["🍎 NIO L2"]
    java --> redis["🍎 Redis L2"]

    concurrent --> futex["futex"]
    concurrent --> longadder["LongAdder"]
    concurrent --> threadpool["线程池"]

    nio --> buffer["Buffer 70"]
    nio --> channel["Channel 50"]
    nio --> selector["Selector 70"]
    nio --> netty["Netty 65"]

    redis --> persistence["持久化 70"]
    redis --> replication["主从复制 70"]
    redis --> cluster["Cluster 85"]

    netty -.->|"后续深入"| reactor["Reactor模式"]
    cluster -.->|"后续深入"| dist["分布式系统"]

    style java fill:#e7f5ff,stroke:#1971c2
    style concurrent fill:#d3f9d8,stroke:#2f9e44
    style nio fill:#fff4e6,stroke:#e67700
    style redis fill:#d3f9d8,stroke:#2f9e44
    style netty fill:#fff4e6,stroke:#e67700
    style cluster fill:#d3f9d8,stroke:#2f9e44
    style reactor fill:#f3d9fa,stroke:#862e9c
    style dist fill:#f3d9fa,stroke:#862e9c
```

---

## 学习深度指标

| 专题 | 深度等级 | 掌握度 | 状态 |
|------|---------|--------|------|
| 并发编程 | L3 | 80/100 | 🍎 已掌握 |
| NIO/网络 | L2 | 65/100 | 🍎 应用中 |
| Redis | L2 | 70-85/100 | 🍎 应用中 |
| DevOps | L1-L2 | 40-70/100 | 🌿 学习中 |
| JVM | L1 | 40/100 | ⏳ 待学习 |
| 架构设计 | L1 | 45/100 | ⏳ 待学习 |
| 分布式 | L1 | 30/100 | ⏳ 待学习 |

---

## 当前学习焦点

```mermaid
graph LR
    A["当前阶段"] --> B["Redis Cluster 深入"]
    A --> C["Ansible 部署实战"]
    A --> D["Netty WebSocket"]

    B --> E["故障转移原理"]
    B --> F["Gossip 协议"]
    C --> G["Playbook 编写"]
    D --> H["协议握手"]
```

---

## 下一步推荐路径

### 路径A: 纵向深入
```mermaid
graph TB
    A["Redis 深入"] --> B["分布式缓存设计"]
    B --> C["缓存一致性"]
    B --> D["缓存穿透/雪崩"]
```

### 路径B: 横向拓展
```mermaid
graph TB
    A["Netty 深入"] --> B["源码阅读"]
    A --> C["性能优化"]
    C --> D["零拷贝"]
    C --> E["背压机制"]
```

---

## 与XMind对比

| 维度 | XMind | 当前MOC |
|------|-------|---------|
| 层级展示 | 树状展开，清晰直观 | 扁平列表+双向链接 |
| 学习进度 | 手动维护，容易直观 | AI驱动，需要图谱视图 |
| 路径指引 | 颜色/图标标注深度 | 需要在Graph View中探索 |
| 知识关联 | 需要手动建立 | 自动双向链接 |

---

## 图谱使用建议

1. **在 Obsidian 中打开 Graph View**，用过滤器只看当前正在学习的专题
2. **用 Excalidraw 插件**手绘学习路径的层级关系
3. **每完成一个主题**，在对应MOC中更新学习进度
4. **用标签系统**(`#learning #done #in-progress`)追踪状态

---

*生成时间: 2026-05-06*
*数据来源: .agent/profile.md, .agent/assessment/current.json*
