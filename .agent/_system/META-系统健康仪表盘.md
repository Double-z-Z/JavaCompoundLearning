---
type: meta_health
description: 系统健康仪表盘 - 知识图谱健康度监控
created: 2026-05-06
updated: {{date:YYYY-MM-DD}}
---

# META-系统健康仪表盘

> 本文件追踪知识图谱的健康状态
> 使用Dataview自动计算指标

---

## 健康度指标

### 整体健康

| 指标 | 值 | 状态 | 说明 |
|------|-----|------|------|
| EMRG数量 | 3 | ✅ | 结构清晰 |
| GOAL完成率 | 2/8 | 🟡 | 需加速 |
| 孤儿笔记 | 待统计 | - | 需检查 |
| 僵尸MOC | 0 | ✅ | 无 |

### 链接健康

| 指标 | 值 | 状态 | 说明 |
|------|-----|------|------|
| 平均链接密度 | 待计算 | - | 目标≥0.4 |
| 孤立笔记 | 待统计 | - | 应为0 |
| 跨域链接 | 待统计 | - | 知识关联度 |

### 遗忘预警

| 指标 | 值 | 状态 | 说明 |
|------|-----|------|------|
| 90天未更新 | 待统计 | - | 应归档 |
| mastery<50且7天未复习 | 待统计 | - | 需复习 |

---

## Dataview查询

### 孤儿笔记检测

```dataview
TABLE mastery, status, file.mtime as "最后更新"
FROM "02-Knowledge"
WHERE length(file.outlinks) + length(file.inlinks) = 0
SORT file.mtime ASC
```

### 遗忘预警

```dataview
TABLE mastery, file.mtime as "最后更新"
FROM ""
WHERE mastery < 50 AND file.mtime < date(today) - dur(7 days)
SORT file.mtime ASC
```

### EMRG链接密度

```dataview
TABLE 
  length(file.outlinks) + length(file.inlinks) as "总链接数",
  file.mtime as "最后更新"
FROM ""
WHERE contains(frontmatter.type, "emrg")
SORT length(file.outlinks) + length(file.inlinks) DESC
```

---

## 异常处理

| 异常 | 阈值 | 处理方式 |
|------|------|---------|
| 孤儿笔记 | > 0 | 检查是否需要补充链接或归档 |
| 90天未更新 | > 5 | 触发归档流程 |
| 链接密度过低 | < 0.3 | 检查是否需要合并或删除 |
| mastery<50聚集 | > 10 | 触发集中复习 |

---

## 健康度评分

| 评分 | 等级 | 说明 |
|------|------|------|
| 90-100 | 🟢 优秀 | 无需干预 |
| 70-89 | 🟡 良好 | 轻微关注 |
| 50-69 | 🟠 警告 | 需要整理 |
| <50 | 🔴 危险 | 急需整理 |

**当前评分**: 待评估

---

## 最近更新

| 日期 | 事件 | 影响 |
|------|------|------|
| 2026-05-06 | 完成三层架构重构 | 🟢 正面 |
