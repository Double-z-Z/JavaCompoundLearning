---
name: GOAL管理
description: 技术负债的生命周期管理：创建、更新、归档GOAL。Invoke when 用户说"创建GOAL"、错误归档暴露新缺口、每周回顾发现新缺口、或GOAL到期需要归档时。
---

# GOAL管理

> **触发时机**
> - 用户说"创建 GOAL" / "新建目标"
> - 错误归档 Skill 发现错误涉及概念不属于任何 active GOAL
> - 每周回顾 Skill 发现新能力缺口
> - GOAL deadline 到期且用户确认归档
>
> **核心原则**: GOAL 不是"学习计划"，而是"带破产清算日期的能力缺口合约"

---

## 加载清单（严格按顺序）

1. `.agent/profile.md` — 读取 `「当前快照」`（了解当前坐标，避免重复定义）
2. `.agent/goals/` — 扫描所有 `.md` 文件的 frontmatter（检查命名冲突、避免重复创建）
3. `05-Templates/GOAL模板.md` — 读取创建模板

## 禁止加载

- `02-Knowledge/` 全库正文
- `04-Maps/Emergent/` 全库正文
- `03-Practice/` 全库正文（创建时只读取驱动事件的单篇文件）

---

## 行为分支

根据触发源，进入不同分支：

| 触发源 | 分支 | 动作 |
|--------|------|------|
| 用户说"创建 GOAL" | **创建** | 引导用户填写模板，生成新 GOAL |
| 错误归档 / 每周回顾发现新缺口 | **创建** | 基于缺口数据预填充模板，用户确认后生成 |
| 整理笔记填补缺口 | **更新** | 更新 GOAL 缺口矩阵的进度 |
| 每周回顾发现 GOAL 到期 | **归档** | 标记 archived，移入 `_archive/` |

---

## 分支 A: 创建 GOAL

### Step 1: 确认驱动事件（强制）

**规则**: 每个 GOAL 必须绑定一个真实事件，不能是"我觉得应该学"

**执行**:
```
1. 询问用户："这个 GOAL 的驱动事件是什么？"
   - 选项 A: 生产事故 → 要求提供 03-Practice/mistakes/ 中的 Incident 链接
   - 选项 B: 面试被问倒 → 要求提供面试复盘笔记链接
   - 选项 C: Code Review 被 block → 要求提供相关记录
   - 选项 D: 晋升要求 → 要求提供职级文档或 JD 链接
   - 选项 E: 项目需要 → 要求提供 01-Projects/ 中的项目笔记链接

2. 如用户无法提供具体事件：
   → 拒绝创建，提示："GOAL 必须绑定真实技术债务，请先记录驱动事件到 03-Practice/"
```

### Step 2: 定义缺口矩阵（与 EMRG 对比）

**执行**:
```
1. 询问："这个 GOAL 需要填补哪些具体能力？"
2. 对每个能力缺口：
   - 执行 `obsidian search query="缺口关键词"` 检查现有 EMRG 证据
   - 如有 EMRG 且 maturity=verified → 该缺口可能已达标，提醒用户
   - 如无 EMRG → 标记为 🔴 高缺口
   - 如有 EMRG 但 maturity=theoretical → 标记为 🟡 中缺口

3. 生成缺口矩阵表格（至少 1 个 🔴 或 🟡 缺口）
```

**输出格式**:
```
## 缺口矩阵（与 EMRG 层对比）

| 能力 | 当前 EMRG 证据 | 缺口等级 |
|-----|---------------|---------|
| ZGC 调优 | [[EMRG-Java-JVM]] 仅有 CMS | 🔴 高 |
| GC 日志自动化 | 无 | 🔴 高 |
```

### Step 3: 设置退出条件（强制）

**规则**: GOAL 必须有明确的完成标准和破产清算机制

**执行**:
```
1. 询问："什么产出证明这个 GOAL 已完成？"
   - 必须是可验证的交付物（原子笔记 / 压测报告 / 项目 Commit / 生产配置）
   - 不能是"理解了"或"学完了"

2. 询问："如果到期未完成，怎么办？"
   - 默认：自动归档，不占用下季度带宽
   - 用户可指定：延期（需新 deadline）或降级为 P1/P2

3. 写入 GOAL 文件的 ## 退出条件 章节
```

**退出条件模板**:
```markdown
## 退出条件
- [ ] 产出 [[EMRG-G1-ZGC-压测报告]]（验证通过标准：8G 堆下 99.9% < 100ms）
- [ ] 未完成则于 2026-06-30 自动移入 `.agent/goals/_archive/`
- [ ] 或用户主动延期至 YYYY-MM-DD（需说明原因）
```

### Step 4: 生成 GOAL 文件

使用 `05-Templates/GOAL模板.md` 创建 GOAL 文件。

**文件路径**: `.agent/goals/GOAL-{主题}-{季度}.md`

**frontmatter 必填字段**（模板已包含）：
- `type: goal` — 标识文件类型
- `id: G{序号}` — 唯一标识
- `priority: P0 | P1 | P2` — 优先级
- `deadline: YYYY-MM-DD` — 破产清算日期
- `evidence: "[[驱动事件]]"` — 绑定真实技术债务

### Step 5: 更新索引

**执行**:
```
1. 无需手动维护 GOAL-Index.md（已被 frontmatter 扫描取代）
2. 在 `.agent/_system/META-涌现触发器日志.md` 记录："新 GOAL 创建: [[GOAL-xxx]]"
3. 如该 GOAL 来自错误归档或每周回顾的缺口暴露，更新触发源文件的状态
```

---

## 分支 B: 更新 GOAL

### 触发条件

- 整理笔记 Skill 发现某笔记填补了某 GOAL 的缺口
- 错误归档 Skill 关联了某 GOAL
- 用户主动声明完成某缺口

### 执行流程

```
Step 1: 接收触发
  → 获取目标 GOAL 文件路径和缺口 ID

Step 2: 读取 GOAL frontmatter
  → 确认 status=active，如为 archived 则拒绝更新并提示

Step 3: 更新缺口矩阵
  → 将该缺口标记为 🟢 已达标（如已有验证证据）
  → 或更新进度百分比

Step 4: 检查是否全部达标
  → 如所有缺口为 🟢：询问用户是否标记 status: completed
  → 如用户确认：更新 frontmatter status: completed，记录完成日期

Step 5: 追加进度记录
  → 在 GOAL 文件的 ## 进度更新记录 中追加一行
```

**更新格式**:
```markdown
## 进度更新记录
- 2026-05-06: [[EMRG-G1-ZGC-压测报告]] 验证通过，缺口 "ZGC 调优" 标记为 🟢 已达标
- 2026-05-06: 全部缺口达标，status 更新为 completed
```

---

## 分支 C: 归档 GOAL

### 触发条件

- deadline 到期且未完成（每周回顾发现）
- 用户主动要求放弃某 GOAL
- 驱动事件已失效（如生产事故场景已不存在）

### 执行流程

```
Step 1: 确认归档原因
  → 到期未完成 / 用户放弃 / 驱动事件失效

Step 2: 更新 frontmatter
  → status: archived
  → archived_date: YYYY-MM-DD
  → archive_reason: {原因}

Step 3: 物理移动文件
  → 从 `.agent/goals/GOAL-xxx.md`
  → 到 `.agent/goals/_archive/GOAL-xxx.md`

Step 4: 清理关联
  → 在 `.agent/_system/META-涌现触发器日志.md` 记录归档事件
  → 如有关联 EMRG，更新 EMRG frontmatter 的 related_goals 字段（移除该 GOAL）

Step 5: 通知用户
  → "[[GOAL-xxx]] 已归档。原因：{原因}。如需重新激活，请创建新 GOAL。"
```

---

## 质量红线

- ❌ 禁止创建无驱动事件的 GOAL
- ❌ 禁止创建无退出条件的 GOAL
- ❌ 禁止创建 deadline 超过 6 个月的 GOAL（超过则拆分为季度子 GOAL）
- ❌ 禁止更新 status: archived 的 GOAL（必须先重新创建）
- ✅ 所有缺口必须关联具体 EMRG 或明确标记为"无证据"
- ✅ 所有 GOAL 文件名必须遵循 `GOAL-{主题}-{季度}.md` 格式
