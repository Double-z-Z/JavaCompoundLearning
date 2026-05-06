---
name: 错误归档
description: 分析出错误原因并且经过我的确认后
---

# 错误归档

> **路径配置**（从 `.agent/config.md` 读取）
> - 错误档案：`03-Practice/mistakes/`
> - GOAL目录：`.agent/goals/`
> - EMRG目录：`04-Maps/`

---

将这次错误归档到错误模式库。

## 归档流程

### Step 1: 创建错误档案

```markdown
---
created: YYYY-MM-DD
tags: [mistake, <主题>]
error-id: MISTAKE-<序号>
status: active | resolved
---

# <错误标题>

**错误ID**: MISTAKE-<序号>
**所属主题**: [[EMRG-<主题>]]
**关联GOAL**: [[GOAL-<技能>]]
```

### Step 2: GOAL关联检查（新增）

创建错误档案后，检查是否需要关联到GOAL：

```
错误涉及的概念 → 属于哪个GOAL？

1. 检查错误涉及的概念属于哪个EMRG
2. 检查该EMRG关联哪个GOAL
3. 在错误档案中记录关联的GOAL
4. 如果GOAL中有"已犯错误记录"，更新它
```

#### GOAL错误记录格式

在对应GOAL文件中更新：

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

---

## 完整归档格式

```markdown
---
created: YYYY-MM-DD
tags: [mistake, <主题>]
error-id: MISTAKE-<序号>
status: active | resolved
related_goal: [[GOAL-<技能>]]  # 新增
---

# <错误标题>

**错误ID**: MISTAKE-<序号>
**所属主题**: [[EMRG-<主题>]]
**关联GOAL**: [[GOAL-<技能>]]  # 新增


## 错误描述
<!-- 我当时是怎么理解的？ -->


## 正确理解
<!-- 现在知道应该是怎样的 -->


## 为什么错了
<!-- 根本原因分析 -->


## 纠正过程
<!-- 苏格拉底对话或自我发现的过程 -->
-


## 关联知识
<!-- 这个错误涉及哪些概念 -->
- [[概念A]]
- [[概念B]]


## 预防措施
<!-- 下次如何避免 -->
1.
2.


## 相关错误
<!-- 类似的错误模式 -->
- [[MISTAKE-XXX]]


---
✅ **检查清单**:
- [ ] 是否找到了根本原因？
- [ ] 是否关联了相关知识点？
- [ ] 是否关联了GOAL？
- [ ] 是否有具体的预防措施？
- [ ] 是否更新了GOAL中的错误记录？
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

## GOAL关联检查流程

```
错误归档完成后：

1. 提取错误涉及的核心概念
   → 例：[[线程池拒绝策略]] 涉及 [[线程池]]

2. 检查该概念属于哪个EMRG
   → 例：[[线程池]] 属于 EMRG-并发编程

3. 检查EMRG关联哪个GOAL
   → 例：EMRG-并发编程 关联 GOAL-Java核心深化

4. 在错误档案中添加 related_goal 字段
5. 在GOAL中添加错误记录
```
