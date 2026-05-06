---
name: 错误归档
description: 分析出错误原因并且经过我的确认后
---

# 加载清单（严格按顺序）

## 1. 必载文件

| 顺序 | 文件 | 章节 | 用途 |
|------|------|------|------|
| 1.1 | `05-Templates/错误档案模板.md` | 完整模板 | 错误档案格式 |
| 1.2 | `.agent/_system/META-系统健康仪表盘.md` | 错误统计 | 更新错误计数 |
| 1.3 | `.agent/_system/META-涌现触发器日志.md` | 高频错误检测 | 涌现检测 |

## 2. 按需加载

| 文件 | 触发条件 | 用途 |
|------|---------|------|
| `03-Practice/mistakes/` | 序号生成 | 最新档案序号 |
| `.agent/goals/GOAL-*.md` | 关联GOAL时 | 更新gap_analysis |
| `02-Knowledge/<主题>/concepts/` | 关联知识时 | 更新mastery |
| `.agent/_system/META-Gap-诊断矩阵.md` | 更新Gap时 | 同步诊断 |
| `04-Maps/EMRG-*.md` | GOAL关联检查时 | 通过 EMRG frontmatter 查找关联 GOAL |

## 3. 禁止加载

- 在知道错误主题前扫描全库知识
- 加载与当前错误无关的 GOAL 完整内容

---

# 错误归档

## 归档流程

### Step 1: 创建错误档案

使用 `05-Templates/错误档案模板.md` 创建错误档案。
模板已包含 frontmatter 字段：`type: mistake`、`error-id`、`related_emrg`、`related_goal`。

### Step 2: GOAL关联检查

创建错误档案后，按以下步骤查找并关联 GOAL：

**Step 2.1：通过概念标签查找**
```
1. 提取错误涉及的核心概念
   → 例：[[线程池拒绝策略]] 涉及 [[线程池]]

2. 使用 Obsidian CLI 检索该概念的原子笔记
   → obsidian search query="tag:线程池" --limit 5

3. 读取命中笔记的 frontmatter.related_goal
   → 如有，记录关联的 GOAL
   → 如无，进入 Step 2.2
```

**Step 2.2：通过 EMRG 关联查找**
```
1. 读取 `04-Maps/EMRG-*.md` 文件
2. 查找错误概念所属主题的 EMRG 文件
3. 读取 EMRG frontmatter.goals 字段
   → 例：EMRG-并发编程 frontmatter.goals = [GOAL-Java核心深化]
4. 将找到的 GOAL 记录到错误档案的 related_goal 字段
```

**Step 2.3：未找到 GOAL 时的处理**
```
如错误涉及的概念不属于任何 active GOAL：
→ 在对话中提示："该错误暴露了新能力缺口（涉及：XXX），是否创建新 GOAL？"
→ 用户确认后：
   a. 调用 GOAL 创建模板
   b. 生成 `.agent/goals/GOAL-<主题>.md`
   c. 在 EMRG frontmatter.goals 中添加该 GOAL
   d. 将新 GOAL 记录到错误档案
```

**Step 2.4：更新 GOAL 错误记录**
```
在对应 GOAL 文件中更新：
```markdown
## 错误档案关联

| 错误ID | 错误类型 | 关联概念 | 预防措施 |
|--------|---------|---------|---------|
| [[MISTAKE-001]] | 资源泄漏 | [[NIO-Buffer]] | 记得调用clear() |
| [[MISTAKE-002]] | 线程安全 | [[线程池]] | 使用isShutdown()检查 |
```

### Step 3: 预防措施与提醒

```markdown
## 预防措施
<!-- 下次如何避免 -->
1.
2.

## 复习提醒
> 如果该错误关联GOAL，在GOAL的review_date时提醒复习此错误档案
```

### Step 4: 更新系统健康仪表盘（必做）

```
更新 `.agent/_system/META-系统健康仪表盘.md` 的错误统计：
1. 该主题累计错误数 +1
2. 该 GOAL 关联的错误数 +1
3. 如错误类型为新类别，添加新分类
```

### Step 5: 高频错误涌现检测（必做）

```
检查该错误是否构成"高频错误模式涌现"：
1. 检索 03-Practice/mistakes/ 中最近 30 天的错误档案
2. 统计相同主题/相同错误类型的出现次数
3. 如 ≥2 次：
   a. 在 `.agent/_system/META-涌现触发器日志.md` 中记录：
      | 触发类型 | 检测结果 | LLM建议 |
      |---------|---------|---------|
      | 高频错误模式 | 30天内[[XXX]]错误出现N次 | 建议标记为"已知盲区"，升级为GOAL缺口 |
   b. 在对话中提示："该错误在近期反复出现，建议将 XXX 加入 GOAL 已知盲区清单"
```

---

## 示例

- 错误现象：RejectedExecutionException
- 根本原因：线程池关闭后仍提交任务
- 我的思维误区：以为shutdown只是停止接收新任务
- 正确模式：shutdown后需等待终止，或使用isTerminated检查
- 关联知识：线程池生命周期管理
- **关联GOAL**: [[GOAL-Java核心深化]]（因为线程池是Java并发的一部分）
- 预防措施：提交前检查线程池状态

---

## 质量检查

归档完成后检查：
- [ ] 是否找到了根本原因？
- [ ] 是否关联了相关知识点？
- [ ] 是否通过 EMRG 找到了关联 GOAL？
- [ ] 如有新 GOAL 缺口暴露，是否提示用户创建？
- [ ] 是否有具体的预防措施？
- [ ] 是否更新了 GOAL 中的错误记录？
- [ ] 是否更新了 `.agent/_system/META-系统健康仪表盘.md`？
- [ ] 是否检测了高频错误涌现？
