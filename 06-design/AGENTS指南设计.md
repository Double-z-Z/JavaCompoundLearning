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
## 2.全局行为原则（所有 Skill 共享）

1. **策展优于生成**: 推荐新知识前必须先扫描 GOAL 缺口；创建笔记前必须先 `obsidian search`；横向拓展前必须先确认 P0 缺口达标
2. **人拥有决策权**: 涌现建议/学习推荐/GOAL创建/归档删除，Agent 只有建议权，必须等人审批
3. **事件驱动为主，周期回顾为辅**: 主要行为由学习事件触发，每周回顾仅用于涌现审批和系统健康检查

## 3. 双平台支持说明

本框架兼容 **Trae IDE** 和 **Claude Code**，共享数据层：
- `.agent/config.md`
- `.agent/profile.md`

平台专属配置：
- Trae IDE：`.trae/rules/` + `.trae/skills/`
- Claude Code：`.claude/skills/`

---

*最后更新：2026-05-06*