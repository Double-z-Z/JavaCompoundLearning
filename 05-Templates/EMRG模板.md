---
type: emrg
id: EMRG-{{主题}}
title: {{主题}}网络
maturity: verified | theoretical
created: {{date:YYYY-MM-DD}}
updated: {{date:YYYY-MM-DD}}
related_goals: [GOAL-xxA, GOAL-xxB]
subtopics:
  - "子主题 A"
  - "子主题 B"
---

# EMRG-{{主题}}

> 成熟度: 🟢 verified / 🟡 theoretical

## 一句话定义
<!-- 用一句话概括本主题的核心认知模型 -->


## 知识拓扑
<!-- 人看：手动维护的 ASCII 树，表达结构意图。不使用代码块围栏，确保双链在 Obsidian 中生效 -->

[核心概念]
  ├─ [[笔记 A]]
  │   └─ 关联 [[EMRG-其他主题/笔记 X]]
  └─ [[笔记 B]]

## 关键缺口（待补充）
- [ ] ...

## 项目实战
| 项目 | 状态 | 关联笔记 |
|------|------|---------|
| [[项目 A]] | ✅ 完成 | ... |

## 关联领域
- [[EMRG-xxx]] — ...

---

## 🤖 AI 工作区（以下由 Dataview 自动维护，请勿手动编辑）

### 核心成员
```dataviewjs
const emrgId = dv.current().id;
dv.table(
  ["笔记", "mastery", "验证状态"],
  dv.pages()
    .where(p => p.related_emrg && p.related_emrg.includes(emrgId))
    .sort(p => p.mastery, 'desc')
    .map(p => [
      p.file.link,
      p.mastery ?? ' ',
      (p.mastery >= 60) ? '🟢' : '🟡'
    ])
);
```

### 边界声明

#### 边缘关联（链接但不纳入）
<!-- 人工判断：哪些笔记 wikilink 了本 EMRG 成员但归属其他 EMRG -->
- [[笔记 X]] → 归属 [[EMRG-其他主题]]

#### 跨界枢纽（被多个 EMRG 引用）
<!-- 人工判断：哪些核心成员同时出现在其他 EMRG 中 -->
- [[笔记 Z]] — 同时被 [[EMRG-A]] 和 [[EMRG-B]] 引用

### 涌现历史
- {{date:YYYY-MM-DD}}: 因密度溢出创建（涉及 N 篇笔记，M 条链接）

### 成熟度说明
<!-- 人工撰写：为什么当前是这个成熟度？涉及哪些笔记/项目/实战经验？ -->

### 检查点
- [ ] 子主题数: X（超过 7 则触发裂变）
- [ ] 最后更新: {{date:YYYY-MM-DD}}（超过 90 天则触发归档检查）
