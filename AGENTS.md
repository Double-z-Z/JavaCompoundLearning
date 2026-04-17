# Java复利学习工程 — AI Agent 指南

> 本项目是一个基于**复利思维 + AI协作 + Obsidian知识管理**的个人深度学习框架。
>
> 💡 **双平台支持**：兼容 **Trae IDE** 和 **Claude Code**，共享数据层 `.agent/config.md` + `.agent/profile.md`

---

## 1. 项目概述

### 核心理念

1. **复利学习思维**：每个新知识必须链接到已有知识，形成网络
2. **Obsidian + AI 知识库**：Obsidian 负责可视化，AI 负责智能推荐与连接
3. **人制定计划，AI 负责推荐**：人决定学什么，AI 基于数据给出建议并记录执行

---

## 2. 目录结构

```
JavaLearning/
├── .agent/                    # AI 共享配置（跨平台）
│   ├── config.md             # 路径配置
│   ├── profile.md            # 学习者画像
│   └── assessment/           # 能力评估档案
├── .trae/                     # Trae IDE 专属配置
├── 01-Projects/               # 项目实战
├── 02-Knowledge/              # 知识库（核心）
│   └── <主题>/
│       ├── concepts/         # 原子笔记
│       └── deep-dives/       # 深度文档
├── 03-Practice/               # 练习与反思
│   ├── drills/               # 练习记录
│   ├── mistakes/             # 错误档案
│   └── reflections/          # 对话反思
├── 04-Maps/                   # 知识图谱（MOC）
├── 05-Templates/              # 笔记模板
├── 99-Archive/                # 归档资料（AI忽略）
├── wiki/                      # 项目文档
├── README.md                  # 项目说明
├── LEARNING-ROADMAP.md        # 学习路线图
└── TODOLIST.md                # 待办事项
```

### 目录说明

| 目录 | 用途 | AI扫描 |
|------|------|--------|
| `01-Projects/` | 项目实战代码 | ✅ 扫描 |
| `02-Knowledge/` | **核心知识库** - 原子笔记和深度文档 | ✅ 主动扫描 |
| `03-Practice/` | 练习记录、错误档案、对话反思 | ✅ 扫描 |
| `04-Maps/` | 知识图谱MOC | ✅ 扫描 |
| `05-Templates/` | 笔记模板 | ✅ 扫描 |
| `99-Archive/` | **归档资料** - 备份文件、参考资料 | ❌ **默认忽略** |
| `.agent/` | AI配置和评估档案 | ✅ 读取 |

**路径解析规则**：
- `config.md` 中的所有路径均为相对于项目根目录的相对路径
- 知识库位置：`02-Knowledge/<主题>/`
- 练习日志位置：`03-Practice/{drills|reflections|mistakes}/`

---

## 3. 对话前必做（Pre-Dialogue Requirements）

在回应用户之前，**必须**完成以下三步：

1. **读取 `.agent/config.md`** — 获取项目结构、路径配置、操作规范
2. **读取 `.agent/profile.md`** — 了解学习者画像、已掌握知识点、mastery 分数
3. **扫描知识库** — 使用 `obsidian-cli` 技能（优先）或文件系统扫描，获取已掌握知识点和错误模式

只有在完成以上步骤后，才能继续回应。

---

## 4. 对话规范与工作流程

详细的对话规范、Skill 触发条件、Mastery 规则、质量红线和错误处理规则已按主题拆分至 `.trae/rules/` 目录，并设置为 alwaysApply。Claude Code 通过 `CLAUDE.md` 中的 `@` 导入同步加载。

学习者画像、强项薄弱领域和优先学习路径统一读取 `.agent/profile.md`，不单独维护规则文件。

| 规则文件 | 内容 |
|---------|------|
| `.trae/rules/01-project-workflow.md` | Skill 触发条件、Mastery 规则、质量红线、对话后必做 |
| `.trae/rules/02-feedback-rules.md` | 知识关联原则、苏格拉底式引导、回复结构、代码规则 |
| `.trae/rules/03-error-handling.md` | 错误处理、错误归档、强化练习规则 |

---

## 5. 评估触发条件（Assessment Triggers）

以下场景**必须**触发「评估触发」Skill：

| 场景 | 评估类型 | 触发时机 |
|------|---------|---------|
| 用户声明完成一个项目/练习 | 项目评估 | 项目完成时 |
| 完成架构设计讨论 | 架构评审 | 设计定稿时 |
| 完成故障排查 | 故障复盘 | 故障恢复后 |
| **达成里程碑目标** | **里程碑复盘** | **里程碑达成时** |

**里程碑定义**（由学习者自主设定，非固定时间）：
- 完成一个主题的系统学习（如"完成NIO专题"）
- 达到某个能力等级（如"并发编程达到L3"）
- 完成一个综合项目（如"完成高并发计数器服务"）
- 积累一定量的知识资产（如"产出10个原子笔记"）

> 详细评估流程见 `.trae/rules/01-project-workflow.md`

---

*最后更新：2026-04-17*
