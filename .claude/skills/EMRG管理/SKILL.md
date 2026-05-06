---
name: EMRG管理
description: 知识图谱的生命周期管理：创建、分裂、归档EMRG。Invoke when 整理笔记涌现检测被用户确认、每周回顾涌现审批通过、或概念裂变/时间衰减触发时。
---

# EMRG管理

> **触发时机**
> - 整理笔记 Skill 的涌现检测被用户确认（✅ 同意创建）
> - 每周回顾 Skill 的涌现审批通过
> - 概念裂变检测：某 EMRG 子主题超过 7 个
> - 时间衰减检测：某 EMRG 90 天未编辑且其下笔记无新增链接
>
> **核心原则**: EMRG 不是"分类文件夹"，而是"经过涌现规则筛选的知识节点俱乐部"

---

## 加载清单（严格按顺序）

1. `05-Templates/EMRG模板.md` — 读取创建模板
2. `04-Maps/Emergent/` — 扫描所有 `.md` 文件的 frontmatter（检查命名冲突、避免重复）
3. `.agent/_system/META-涌现触发器日志.md` — 读取待执行的涌现事件（仅创建/分裂时）

## 禁止加载

- `02-Knowledge/` 全库正文（只通过 Obsidian CLI 按需检索具体笔记）
- `03-Practice/` 全库正文
- `.agent/goals/` 正文（只读 frontmatter 的 `related_goals` 字段）

---

## 行为分支

| 触发源 | 分支 | 动作 |
|--------|------|------|
| 涌现审批通过（密度溢出/跨界链接） | **创建** | 生成新 EMRG 文件 |
| 概念裂变检测（子主题 > 7） | **分裂** | 将原 EMRG 拆分为 2-3 个次级 EMRG |
| 时间衰减检测（90 天无更新） | **归档** | 移入 `04-Maps/_archive/` |

---

## 分支 A: 创建 EMRG

### Step 1: 确定边界（强制）

**规则**: EMRG 必须回答"它包含什么，不包含什么"

**执行**:
```
1. 读取涌现日志中的触发数据：
   - 密度溢出: 涉及哪些原子笔记？（笔记列表）
   - 跨界链接: 被哪些不同领域引用？（引用源列表）

2. 执行边界检查：
   - `obsidian search query="tag:主题"` 列出所有相关笔记
   - 区分: 核心成员（必须纳入）vs 边缘关联（可链接但不纳入）
   - 检查是否有笔记同时属于其他 EMRG（跨界笔记）

3. 定义边界声明：
   - 包含: {具体概念 A, B, C}
   - 不包含: {相关但归属其他 EMRG 的 X, Y}
   - 跨界笔记: {笔记 Z}（同时链接到 EMRG-A 和 EMRG-B）
```

**边界声明模板**:
```markdown
## 边界声明

### 核心成员（纳入本 EMRG）
- [[笔记 A]] — 原因：...
- [[笔记 B]] — 原因：...

### 边缘关联（链接但不纳入）
- [[笔记 X]] → 归属 [[EMRG-其他主题]]

### 跨界枢纽（被多个 EMRG 引用）
- [[笔记 Z]] — 同时被 [[EMRG-A]] 和 [[EMRG-B]] 引用
```

### Step 2: 设置成熟度

**执行**:
```
1. 检查涉及笔记的 frontmatter：
   - 如有 ≥1 篇含 GitHub Commit 链接或 Incident 编号 → maturity: verified
   - 如全部仅为理论笔记 → maturity: theoretical

2. 设置初始成熟度并记录原因
```

**成熟度标记**:
```yaml
---
maturity: verified | theoretical
maturity_evidence: "[[笔记 A]] 含生产验证 Commit: xxx"
---
```

### Step 3: 关联 GOAL（双向链接）

**执行**:
```
1. 检查该 EMRG 主题是否被任何 GOAL 的 gaps[] 引用：
   - 扫描 .agent/goals/ 的 frontmatter
   - 如 GOAL-Java-Core 的 gaps 包含 "epoll机制"
   - 且本 EMRG 包含 epoll 相关笔记 → 建立关联

2. 在 EMRG frontmatter 中设置：
   related_goals: ["GOAL-Java-Core-2026-Q2"]

3. 在对应 GOAL 文件的 ## 缺口矩阵 中更新：
   - 当前 EMRG 证据列填入本 EMRG 链接
```

### Step 4: 生成 EMRG 文件

使用 `05-Templates/EMRG模板.md` 创建 EMRG 文件。

**文件路径**: `04-Maps/Emergent/EMRG-{主题}.md`

**frontmatter 必填字段**（模板已包含）：
- `type: emrg` — 标识文件类型
- `id: EMRG-{主题}` — 唯一标识
- `maturity: verified | theoretical` — 成熟度
- `maturity_evidence` — 验证证据
- `related_goals: []` — 关联 GOAL（双向链接）
- `subtopics: []` — 用于裂变检测

### Step 5: 更新涌现日志

**执行**:
```
1. 在 `.agent/_system/META-涌现触发器日志.md` 中找到对应条目
2. 更新状态: 🟡 待审批 → ✅ 已执行
3. 追加执行记录: "已创建 [[EMRG-xxx]]，路径: 04-Maps/Emergent/EMRG-xxx.md"
```

---

## 分支 B: 分裂 EMRG

### 触发条件

- 某 EMRG 的 `subtopics` 数量 > 7
- 或用户/每周回顾明确要求分裂

### 执行流程

```
Step 1: 分析子主题聚类
  → 读取该 EMRG 下的所有原子笔记 frontmatter
  → 基于链接关系执行聚类（笔记间链接密度）
  → 建议分裂为 2-3 个次级 EMRG

Step 2: 定义新边界
  → 为每个次级 EMRG 定义包含/不包含声明
  → 确定跨界笔记的归属（保留在原 EMRG 作为枢纽，或复制到多个子 EMRG）

Step 3: 生成次级 EMRG
  → 按"创建 EMRG"流程生成新文件
  → 文件名: EMRG-{主题}-{子领域}.md
  → frontmatter 中增加 parent_emrg: "EMRG-{原主题}"

Step 4: 改造原 EMRG 为枢纽
  → 保留原 EMRG 文件，但改造为"索引型 MOC"
  → 内容改为次级 EMRG 的导航页
  → 移除具体原子笔记列表，改为链接到次级 EMRG

Step 5: 更新链接
  → 扫描所有引用原 EMRG 的笔记
  → 更新链接指向最合适的次级 EMRG（或保持指向枢纽）
```

**枢纽型 EMRG 改造模板**:
```markdown
# EMRG-{主题}（枢纽索引）

> 本 EMRG 已分裂为以下子主题，具体知识节点请访问子 EMRG

## 子 EMRG 导航
| 子主题 | 成熟度 | 核心概念 | 关联 GOAL |
|--------|--------|---------|---------|
| [[EMRG-{主题}-A]] | 🟢 | ... | [[GOAL-xxx]] |
| [[EMRG-{主题}-B]] | 🟡 | ... | [[GOAL-yyy]] |

## 跨界枢纽（仍保留于此）
- [[笔记 Z]] — 同时关联多个子领域
```

---

## 分支 C: 归档 EMRG

### 触发条件

- 90 天未编辑（`updated` frontmatter 超过 90 天）
- 且其下所有原子笔记 90 天内无新增入链/出链
- 每周回顾确认归档

### 执行流程

```
Step 1: 验证归档条件
  → 检查 EMRG frontmatter 的 updated 字段
  → 执行 `obsidian backlinks file="EMRG-xxx"` 检查最近 90 天是否有新链接
  → 如仍有活跃链接，拒绝归档并提示

Step 2: 更新 frontmatter
  → status: archived
  → archived_date: YYYY-MM-DD
  → archive_reason: "时间衰减 — 90 天无更新且无新链接"

Step 3: 物理移动
  → 从 `04-Maps/Emergent/EMRG-xxx.md`
  → 到 `04-Maps/_archive/EMRG-xxx.md`

Step 4: 清理关联
  → 在 `.agent/_system/META-涌现触发器日志.md` 记录归档事件
  → 更新关联 GOAL 的缺口矩阵（如该 EMRG 是某缺口的唯一证据，标记缺口为 🔴 缺失）
  → 通知用户："[[EMRG-xxx]] 已归档。关联 GOAL [[GOAL-yyy]] 的缺口 [X] 现在缺乏 EMRG 证据，请评估是否创建新 EMRG 或调整 GOAL。"

Step 5: 保留重激活路径
  → 归档文件中保留说明："如需重激活，请执行 `obsidian restore file="EMRG-xxx"` 或手动移回 Emergent/"
```

---

## 质量红线

- ❌ 禁止创建边界模糊的 EMRG（必须明确"包含什么，不包含什么"）
- ❌ 禁止创建无关联 GOAL 的 EMRG（如无 GOAL 关联，先创建 GOAL 或说明原因）
- ❌ 禁止分裂后原 EMRG 仍保留具体笔记（必须改造为枢纽索引）
- ❌ 禁止归档仍有活跃链接的 EMRG（活跃 = 30 天内有新入链或出链）
- ✅ 所有 EMRG 文件名必须遵循 `EMRG-{主题}.md` 格式
- ✅ 所有次级 EMRG 必须设置 `parent_emrg` 字段指向枢纽
- ✅ 所有归档操作必须在 `.agent/_system/emergence.md` 留下审计记录
