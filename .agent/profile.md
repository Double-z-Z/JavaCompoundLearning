# Learner Profile

> 学习者画像 - 帮助AI了解学习者的背景、目标和进度
>
> 🎯 **本文件是动态学习规划的唯一可信来源**
> 📚 历史记录：`.agent/_system/history.md`（不需主动读取）
> 📊 能力评估：`.agent/assessment/current.json`
> 🗺️ GOAL索引：`.agent/goals/GOAL-Index.md`

---

## 基本信息

| 属性 | 值 |
|------|-----|
| 当前水平 | 中级 (L2) |
| 目标水平 | L3（精通级） |
| 学习风格 | 项目驱动型，偏好通过实践理解原理 |
| 时间投入 | 时间不限 |
| 环境 | Fedora / Java 17 / PVE |

---

## 当前快照（META-Index）

| 指标 | 值 | 说明 |
|------|-----|-----|
| 活跃EMRG | 3 | 并发/NIO/Redis |
| GOAL总数 | 8 | P0×3, P1×4, P2×1 |
| GOAL完成率 | 0/8 | 刚开始 |
| P0 GOAL | 3 | Java核心/Redis深入/SpringCloud |
| 上次更新 | 2026-05-06 | 三层架构重构 |

### P0优先级

| GOAL | 状态 | driver | deadline | 关键缺口 |
|------|------|--------|----------|---------|
| [[GOAL-Java核心深化]] | active | promotion | 2026-08-06 | epoll机制/线程池原理/反射 |
| [[GOAL-Redis深入]] | active | promotion | 2026-07-06 | 数据结构底层 |
| [[GOAL-SpringCloud微服务]] | active | promotion | 2026-08-06 | 原理空白 |

> 详细GOAL信息见 `.agent/goals/GOAL-*.md`

### 已掌握的强项

| 领域 | mastery | 证据 |
|------|---------|------|
| 并发编程 | 🍎 80 | futex、LongAdder、线程池 |
| Redis Cluster | 🍎 85 | Cluster部署+故障转移验证 |
| NIO/网络 | 🍎 70+ | Selector/Buffer/Netty |

---

## 拒绝清单（Anti-MOC）

> LLM推荐学习内容前必须检查此清单，命中则过滤

| 主题 | 拒绝原因 | 复审条件 |
|-----|---------|---------|
| React/Vue 前端深入 | 非职业方向，仅了解即可 | 转全栈时复审 |
| 区块链/Web3 | 与后端架构无关 | 永不复审 |

---

## 环境默认值

| 类别 | 值 |
|------|-----|
| OS | Fedora |
| Java | 17 |
| 虚拟化 | PVE (Proxmox Virtual Environment) |
| 构建工具 | Maven |
| IDE | (待补充) |

---

## 个性化指令

- 解释原理时，使用我已掌握的概念作类比（如用线程池类比 NIO 的 Selector）
- 生成练习时，优先针对我的错误模式
- 发现知识关联时，主动建议更新知识图谱（如 NIO 与并发编程的关联）
- 长文内容自动提取结构化要点
- **P0 GOAL优先**：推荐前先检查是否与P0 GOAL缺口匹配