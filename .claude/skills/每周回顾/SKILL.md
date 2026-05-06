---
name: 每周回顾
description: 周期性系统健康检查、涌现审批、Gap矩阵生成与下周计划。Invoke when 用户说"开始每周回顾"、"weekly review"、"周五回顾"时。
---

# 每周回顾

> **触发时机**
> - 用户说"开始每周回顾"
> - 用户说"weekly review"
> - 用户说"周五回顾"
>
> **周期**: 建议每周五执行一次
>
> **加载上限**: 单次对话累计加载完整文件 ≤ 3 个，frontmatter 切片不计入

---

## 加载清单（严格按顺序）

1. `.agent/profile.md` — 读取 `#当前快照`、`#拒绝清单`、`#个性化指令`
2. `.agent/_system/health.md` — 读取系统健康仪表盘（Dataview 查询结果）
3. `.agent/_system/emergence.md` — 读取涌现触发日志中的待审批项

## 禁止加载

- `02-Knowledge/` 全库正文扫描
- `03-Practice/` 全库正文扫描
- GOAL 正文（只扫描 `.agent/goals/` 的 frontmatter）
- EMRG 正文（只扫描 `04-Maps/Emergent/` 的 frontmatter）

---

## 执行阶段（Phase 流程）

### Phase 1: 系统健康扫描（5 分钟）

**输入**: `.agent/_system/health.md`

**执行**:
```
1. 读取 health.md 中的 Dataview 查询结果：
   - 僵尸 MOC 数（30 天未编辑的 EMRG）
   - 孤儿笔记数（7 天无入链的原子笔记）
   - 到期 GOAL 数（deadline 7 天内）
   - 未验证知识占比（maturity ≠ verified 的 EMRG 比例）

2. 输出健康报告摘要（≤ 20 行）：
```

**输出格式**:
```
📊 系统健康报告（截至 YYYY-MM-DD）

├─ 活跃 EMRG: X 个（verified: Y, theoretical: Z）
├─ 僵尸预警: A 个（30 天未编辑）
├─ 孤儿笔记: B 个（7 天无入链）
├─ 到期 GOAL: C 个（7 天内 deadline）
├─ 未验证知识: D%（建议 < 30%）
└─ 知识库负载: 健康 / 警告 / 危险
```

---

### Phase 2: 涌现审批（10 分钟）

**输入**: `.agent/_system/emergence.md`

**执行**:
```
1. 读取 emergence.md 中状态为 🟡 待审批 的所有条目
2. 按触发类型分组展示：
   - 密度溢出（建议创建 EMRG）
   - 跨界链接（建议桥接或分类确认）
   - 概念裂变（建议分裂 EMRG）
   - 时间衰减（建议归档 EMRG）

3. 对每个待审批项，输出：
   - 触发数据（笔记数、链接数、涉及文件）
   - LLM 建议（创建 / 分裂 / 归档 / 忽略）
   - 请求用户决策
```

**输出格式**:
```
🔍 涌现待审批（共 N 项）

### 项 1: 密度溢出 — EMRG-Redis
- 触发数据: 02-Knowledge/redis/ 下 6 篇笔记，4 条内部链接
- 涉及笔记: [[Redis-持久化]], [[Redis-主从复制]] ...
- LLM 建议: 创建 [[04-Maps/Emergent/EMRG-Redis]]
- 你的决策: [ ] ✅ 同意创建  [ ] ❌ 拒绝  [ ] ⏸️ 暂缓

### 项 2: 概念裂变 — EMRG-Java-并发
- 触发数据: 子主题 9 个（超过阈值 7）
- LLM 建议: 分裂为 EMRG-Java-锁机制 + EMRG-Java-线程池
- 你的决策: [ ] ✅ 同意分裂  [ ] ❌ 拒绝  [ ] ⏸️ 暂缓
```

**用户决策后执行**:
- ✅ 同意 → 调用 **EMRG管理 Skill** 执行创建/分裂/归档
- ❌ 拒绝 → 在 emergence.md 中更新状态为 ❌ 已拒绝，记录原因
- ⏸️ 暂缓 → 更新状态为 ⏸️ 暂缓，设置 review_date

---

### Phase 3: Gap 矩阵生成（10 分钟）

**输入**: `.agent/goals/` frontmatter + `04-Maps/Emergent/` frontmatter

**执行**:
```
1. 扫描 .agent/goals/ 下所有 status: active 的 GOAL：
   - 读取 frontmatter: id, title, priority, driver, deadline, gaps[]

2. 扫描 04-Maps/Emergent/ 下所有 EMRG：
   - 读取 frontmatter: id, maturity, subtopics[], related_goals[]

3. 对比：每个 GOAL 的 gaps[] vs 对应 EMRG 的成熟度
   - 缺口有 EMRG 证据且 maturity=verified → 🟢 已达标
   - 缺口有 EMRG 证据但 maturity=theoretical → 🟡 进行中
   - 缺口无 EMRG 证据 → 🔴 高

4. 写入 `.agent/_system/gap-analysis.md`：
   - Agent 填写前 5 列（缺口 ID / 目标能力 / 当前证据 / 差距等级 / 建议策略）
   - 人填写第 6 列（决策: ✅ 执行 / ❌ 拒绝 / ⏸️ 推迟）
```

**输出格式**:
```
📋 Gap 矩阵草案（Agent 生成，人填决策）

| 缺口 ID | 目标能力 | 当前 EMRG 证据 | 差距 | 建议策略 | 你的决策 |
|---------|---------|---------------|------|---------|---------|
| G1-ZGC | ZGC 调优 | [[EMRG-Java-JVM]] 仅有 CMS | 🔴 高 | 专项突破 2 周 | [待填] |
| G2-Saga | Saga 模式 | 无 EMRG 证据 | 🔴 高 | 项目驱动 1 月 | [待填] |
| G3-NIO | 零拷贝优化 | [[EMRG-Java-NIO]] verified | 🟢 达标 | 关闭缺口 | [待填] |

> 💡 请填写"你的决策"列。只有标记 ✅ 的缺口会进入下周学习推荐。
```

---

### Phase 4: GOAL 时效检查（5 分钟）

**输入**: `.agent/goals/` frontmatter

**执行**:
```
1. 扫描所有 active GOAL 的 deadline：
   - deadline ≤ 7 天: 标红预警
   - deadline 已过期: 标红并提示归档

2. 对过期 GOAL：
   - 输出: "[[GOAL-xxx]] 已于 YYYY-MM-DD 到期，当前完成度 X%"
   - 询问: "是否归档？或延期？"
   - 用户确认归档 → 调用 **GOAL管理 Skill** 执行归档
```

**输出格式**:
```
⏰ GOAL 时效检查

### 即将到期（7 天内）
| GOAL | Deadline | 完成度 | 动作 |
|------|----------|--------|------|
| [[GOAL-JVM-Tuning]] | 2026-06-30 | 40% | 加速或延期 |

### 已过期（需决策）
| GOAL | Deadline | 状态 | 建议 |
|------|----------|------|------|
| [[GOAL-xxx]] | 2026-05-01 | 过期 | 归档或延期 |

> 你的决策: [ ] 归档  [ ] 延期至 YYYY-MM-DD  [ ] 保持 active（需说明原因）
```

---

### Phase 5: 下周计划生成（5 分钟）

**输入**: `.agent/_system/gap-analysis.md` 中已标记 ✅ 的缺口

**执行**:
```
1. 读取 gap-analysis.md，筛选"人决策"为 ✅ 的行
2. 对每个 ✅ 缺口，生成 1 个学习选项：
   - 关联 GOAL ID
   - 缺口说明
   - 关联 EMRG（如有）
   - 预计产出（原子笔记 / 练习 / 项目交付物）
   - 预计耗时

3. 拒绝清单过滤：任何选项涉及 Anti-MOC 主题，直接排除
4. 输出 1-3 个下周学习选项
```

**输出格式**:
```
🎯 下周学习计划（基于已批准的 Gap）

#### 选项 1: 【G1-ZGC 缺口】（推荐度 ⭐⭐⭐⭐⭐）
- 关联 GOAL: [[GOAL-JVM-Tuning-2026-Q2]]
- 内容: ZGC 调优专项突破
- 关联 EMRG: [[EMRG-Java-JVM]]（需补充 ZGC 分支）
- 预计产出: 1 篇原子笔记 + 1 份压测报告
- 预计耗时: 2 周

> 请确认下周主攻选项，或调整优先级。
```

---

## 对话后必做

1. **更新 `.agent/_system/emergence.md`** — 将用户已审批的项更新状态（✅ 已执行 / ❌ 已拒绝 / ⏸️ 暂缓）
2. **更新 `.agent/_system/gap-analysis.md`** — 将用户填写的决策列同步写入
3. **更新 `.agent/profile.md` #当前快照** — 记录"上次每周回顾: YYYY-MM-DD"
4. **告知用户**: "本周回顾已完成。X 项涌现已审批，Y 个 Gap 待执行，Z 个 GOAL 需关注时效。"

---

## 质量红线

- ❌ 禁止忽略过期的 GOAL 不提示
- ❌ 禁止生成 Gap 矩阵时不关联具体 EMRG 证据
- ❌ 禁止下周计划包含未标记 ✅ 的缺口
- ✅ 必须让用户显式审批每一项涌现
- ✅ 必须让用户显式决策每一个 Gap
- ✅ 所有时间计算必须基于文件的 `mtime` 或 frontmatter 的 `updated` 字段，不能估算
