# AGENTS.md 设计文档

> 本文件是 AGENTS.md 的设计说明，Agent 运行时不需要读取。
> 供开发者在配置 Skill/Rules 时参考。

---

## 1. AGENTS.md 结构

AGENTS.md 只包含 Agent 运行时的全局约束，不重复路径配置和 Skill 细节。

### 1.1 路径配置

所有路径配置集中在 `.agent/config.md`，AGENTS.md 不重复声明路径信息。

### 1.2 Skill 承载意图

**意图的落地点是 Skill，不是 AGENTS.md。**

AGENTS.md 只约束所有 Skill 共享的边界：
- 全局加载约束
- 强制检查规则
- 加载上限

每个 Skill 自己定义：
- **加载清单**（该 Skill 需要的文件）
- **执行流程**（Phase 状态机）
- **输出格式**

详细对话规范、Skill 触发条件、Mastery 规则、质量红线和错误处理规则已按主题拆分至 `.trae/rules/` 目录，并设置为 alwaysApply。Claude Code 通过 `CLAUDE.md` 中的 `@` 导入同步加载。

学习者画像、强项薄弱领域和优先学习路径统一读取 `.agent/profile.md`，不单独维护规则文件。

| 规则文件 | 内容 |
|---------|------|
| `.trae/rules/01-project-workflow.md` | Skill 触发条件、Mastery 规则、质量红线、对话后必做 |
| `.trae/rules/02-feedback-rules.md` | 知识关联原则、苏格拉底式引导、回复结构、代码规则 |
| `.trae/rules/03-error-handling.md` | 错误处理、错误归档、强化练习规则 |

**Skill 文件位置**：
- `.claude/skills/<Skill名>/SKILL.md` — Claude Code Skill
- `.trae/skills/<Skill名>/SKILL.md` — Trae IDE Skill

---

## 2. 评估触发条件（Assessment Triggers）

以下场景**必须**触发「评估触发」Skill：

| 场景 | 评估类型 | 触发时机 |
|------|---------|---------|
| 用户声明完成一个项目/练习 | 项目评估 | 项目完成时 |
| 完成架构设计讨论 | 架构评审 | 设计定稿时 |
| 完成故障排查 | 故障复盘 | 故障恢复后 |
| **达成里程碑目标** | **里程碑复盘** | 里程碑达成时 |

**里程碑定义**（由学习者自主设定，非固定时间）：
- 完成一个主题的系统学习（如"完成NIO专题"）
- 达到某个能力等级（如"并发编程达到L3"）
- 完成一个综合项目（如"完成高并发计数器服务"）
- 积累一定量的知识资产（如"产出10个原子笔记"）

> 详细评估流程见 `.trae/rules/01-project-workflow.md`

---

## 3. 目录结构说明

```
JavaLearning/
├── .agent/                    # AI 共享配置（跨平台）
│   ├── config.md             # 路径配置
│   ├── profile.md            # 学习者画像（仅含动态状态）
│   ├── goals/                 # 技术负债 MOC（GOAL 层）
│   │   └── GOAL-*.md         # 每个 GOAL 独立文件
│   └── _system/               # 系统日志与仪表盘（Meta 层）
│       └── META-*.md          # 健康度/Gap/涌现日志
├── .trae/                     # Trae IDE 专属配置
│   ├── rules/                 # alwaysApply 规则文件
│   └── skills/                 # Trae Skill
├── .claude/                   # Claude Code 专属配置
│   └── skills/                 # Claude Skill
├── 01-Projects/               # 项目实战
├── 02-Knowledge/              # 知识库（核心）
│   └── <主题>/
│       ├── concepts/         # 原子笔记
│       └── deep-dives/       # 深度文档
├── 03-Practice/               # 练习与反思
│   ├── drills/               # 练习记录
│   ├── mistakes/             # 错误档案
│   └── reflections/          # 对话反思
├── 04-Maps/                   # 知识图谱（EMRG 层）
│   └── EMRG-*.md            # 涌现 MOC
├── 05-Templates/              # 笔记模板
├── 99-Archive/                # 归档资料（AI忽略）
├── design/                    # 设计文档（本目录）
│   └── AGENTS-DESIGN.md      # 本文件
└── wiki/                      # 项目文档
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

## 4. 双平台支持说明

本框架兼容 **Trae IDE** 和 **Claude Code**，共享数据层：
- `.agent/config.md`
- `.agent/profile.md`

平台专属配置：
- Trae IDE：`.trae/rules/` + `.trae/skills/`
- Claude Code：`.claude/skills/`

---

*最后更新：2026-05-06*