# Java复利学习工程 — AI Agent 指南

> 本项目是一个基于**复利思维 + AI协作 + Obsidian知识管理**的个人深度学习框架。
>
> 💡 **双平台支持**：本工程同时兼容 **Trae IDE** 和 **Kimi CLI**。
> - Trae 使用 `.trae/skills/` 下的 Skill 定义
> - Kimi CLI 使用 `.agents/skills/` 下的 Skill 定义
> - 共享数据层：`.agent/config.md` + `.agent/profile.md`

---

## 1. 项目概述

### 这是什么？

Java复利学习工程是一个**个人知识管理系统**，通过结合AI智能体和Obsidian笔记工具，帮助Java学习者：

- ✅ **建立知识网络** — 每个新概念自动链接到已有知识
- ✅ **避免重复犯错** — 错误模式归档，AI主动预警
- ✅ **智能学习推荐** — 基于掌握程度和遗忘曲线推荐学习内容
- ✅ **项目驱动学习** — 通过实战项目整合多个知识点
- ✅ **复利式增长** — 知识资产持续积累，越学越快

### 核心理念

1. **复利学习思维**：每个新知识必须链接到已有知识，形成网络，产生复利
2. **Obsidian + AI 知识库**：Obsidian 负责可视化与管理，AI 负责智能推荐与连接
3. **人制定计划，AI 负责推荐**：人决定学什么，AI 基于数据给出建议并记录执行

---

## 2. 目录结构

```
JavaLearning/
├── .agent/                    # AI 共享配置（跨平台）
│   ├── config.md             # 路径配置
│   └── profile.md            # 学习者画像
├── .trae/                     # Trae IDE 专属配置
│   ├── prompts/              # 智能体提示词
│   ├── rules/                # 对话规则
│   ├── skills/               # Skill 定义（Trae）
│   └── skill-config.json     # Trae 技能配置
├── .agents/                   # Kimi CLI 专属配置
│   └── skills/               # Skill 定义（Kimi CLI）
├── 01-Projects/               # 项目实战
├── 02-Knowledge/              # 知识库
│   └── <主题>/
│       ├── concepts/         # 原子笔记
│       └── deep-dives/       # 深度文档
├── 03-Practice/               # 练习与反思
│   ├── drills/               # 练习记录
│   ├── mistakes/             # 错误档案
│   └── reflections/          # 对话反思
├── 04-Maps/                   # 知识图谱（MOC）
├── 05-Templates/              # 笔记模板
├── wiki/                      # 项目文档
├── README.md                  # 项目说明
├── LEARNING-ROADMAP.md        # 学习路线图
└── TODOLIST.md                # 待办事项
```

---

## 3. 对话前必做（Pre-Dialogue Requirements）

在回应任何用户查询之前，**必须**完成以下三步：

1. **读取 `.agent/config.md`** — 获取项目结构、路径配置、操作规范
2. **读取 `.agent/profile.md`** — 了解学习者画像、已掌握知识点、mastery 分数
3. **扫描知识库** — 使用 `obsidian-cli` 技能（优先）或文件系统扫描，获取已掌握知识点和错误模式

**路径解析规则**：
- `config.md` 中的所有路径均为相对于项目根目录的相对路径
- 知识库位置：`02-Knowledge/<主题>/`
- 练习日志位置：`03-Practice/{drills|reflections|mistakes}/`

只有在完成以上步骤后，才能继续回应。

---

## 4. 对话中规范（During Dialogue Guidelines）

### 知识关联原则
- **优先用学习者已掌握的概念作类比**解释新知识
- **主动指出知识连接**，用"这让我想起你之前学的..."等句式
- **绝不用陌生概念解释新概念**，如果无法关联，先教前置概念

### 错误处理
- 学习者遇到错误时，**先检查错误模式库** (`03-Practice/mistakes/`)
- 引用历史类似错误，帮助识别模式、避免重复
- 帮助理解根因，而非仅修复表面问题

### 回复结构
- **长回复（超过3段）必须在开头加 TL;DR 摘要**
- 用清晰的标题和 bullet points 组织内容

### 代码回答规则
- **绝不直接给出完整代码答案**，除非学习者明确要求
- 提供提示、关键概念和部分代码片段，引导主动思考

---

## 5. 对话后必做（Post-Dialogue Requirements）

完成主要回应后，**必须**：

1. **询问是否需要归档本次对话要点**
2. **建议更新哪些知识库文件**
3. **基于当前进度预测并建议下一步学习路径**

---

## 6. 技能索引（Skill Index）

以下 Skill 在 `.trae/skills/` 和 `.agents/skills/` 中均有定义，内容一致：

| Skill | 触发时机 | 核心职责 |
|-------|---------|---------|
| **学习推荐** | 用户说"开始今日学习" | 分析状态，生成推荐选项 |
| **整理笔记** | 对话结束 / 用户说"整理笔记" | 沉淀知识到知识库，更新 mastery |
| **项目拆解** | 开始新项目 | 分析知识点，创建项目笔记 |
| **错误分析** | 用户遇到错误 | 扫描错误档案，苏格拉底式提问定位根因 |
| **错误归档** | 错误原因确认后 | 创建错误档案，关联知识点 |
| **强化练习** | 错误归档后 | 生成变式练习 |
| **苏格拉底式引导** | 深入理解场景 | 通过提问引导用户自己发现知识本质 |
| **横向拓展** | 理解困难场景 | 用类比和关联帮助建立直觉 |
| **obsidian-cli** | 需要与 Obsidian 交互时 | 使用 obsidian 命令行工具操作仓库 |
| **obsidian-markdown** | 创建/编辑 Obsidian 笔记时 | 生成符合 Obsidian 规范的 Markdown |

---

## 7. 质量红线

### ❌ 禁止
- 先写大笔记，再事后拆分
- 只记录到临时文件，不结构化
- 对话结束才想起来保存
- 原子笔记少于2个双向链接

### ✅ 必须
- 每个知识点讨论完立即保存
- 按模板结构化内容
- 添加知识关联（至少 2 个 + 关联原因）
- 更新 `.agent/profile.md`
- 更新相关概念的 `mastery` 字段

---

## 8. 平台差异说明

| 功能 | Trae | Kimi CLI |
|-----|------|----------|
| 项目上下文 | 读取 `.trae/prompts/` + `AGENTS.md` | 自动读取 `AGENTS.md` |
| Skill 目录 | `.trae/skills/` | `.agents/skills/` |
| Rules 系统 | `.trae/rules/` | 通过 `AGENTS.md` 和 Skill 内嵌规则实现 |
| 共享数据 | `.agent/config.md` + `.agent/profile.md` | `.agent/config.md` + `.agent/profile.md` |

---

*最后更新：2026-04-14*
