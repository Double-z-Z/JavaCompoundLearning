# 双平台兼容性说明

> 本文档说明 JavaLearning 项目如何同时兼容 **Trae IDE** 和 **Kimi CLI**。

---

## 设计原则

1. **共享数据，独立技能**
   - `.agent/` 是跨平台的共享配置层
   - `.trae/skills/` 和 `.agents/skills/` 分别是两个平台的技能目录，内容保持一致

2. **文档中立**
   - README、Wiki 等文档不暗示任何平台独占性
   -  setup 说明同时覆盖两个平台

3. **新增技能必须双平台同步**
   - 在 `.trae/skills/` 中添加新 Skill 时，必须同步到 `.agents/skills/`
   - 修改现有 Skill 时，两个目录的文件内容必须同时更新

---

## 目录归属

| 目录/文件 | 平台归属 | 说明 |
|-----------|----------|------|
| `.agent/config.md` | **共享** | 路径配置，Trae 和 Kimi CLI 都读取 |
| `.agent/profile.md` | **共享** | 学习者画像，双平台共同维护 |
| `.trae/prompts/` | Trae 独占 | Trae 智能体系统提示词 |
| `.trae/rules/` | Trae 独占 | Trae 对话规则（Kimi CLI 通过 `AGENTS.md` 实现类似功能） |
| `.trae/skills/` | Trae 独占 | Trae Skill 定义 |
| `.trae/skill-config.json` | Trae 独占 | Trae 技能开关配置 |
| `.agents/skills/` | Kimi 独占 | Kimi CLI Skill 定义 |
| `AGENTS.md` | **共享** | Kimi CLI 自动读取；Trae 也可作为参考 |
| `README.md` | **共享** | 双平台 setup 说明 |
| `COMPATIBILITY.md` | **共享** | 本文件 |

---

## 维护检查清单

### 新增 Skill 时

- [ ] 在 `.trae/skills/<skill-name>/` 下创建 `SKILL.md`
- [ ] 在 `.agents/skills/<skill-name>/` 下创建内容完全相同的 `SKILL.md`
- [ ] 确保两个 `SKILL.md` 都包含正确的 YAML frontmatter（`name` + `description`）
- [ ] 更新 `AGENTS.md` 中的「技能索引」表格
- [ ] 更新 `wiki/reference/skill-system.md` 中的核心 Skill 列表（如适用）

### 修改 Skill 时

- [ ] 同步修改 `.trae/skills/<skill-name>/SKILL.md`
- [ ] 同步修改 `.agents/skills/<skill-name>/SKILL.md`
- [ ] 确认两个文件内容一致

### 修改共享配置时

- [ ] 修改 `.agent/config.md` 或 `.agent/profile.md` 时，确保不引入 Trae/Kimi 专属假设
- [ ] 修改 `AGENTS.md` 时，确保双平台声明仍然准确

---

## 已知差异

| 功能 | Trae | Kimi CLI | 处理方式 |
|------|------|----------|----------|
| Rules 系统 | `.trae/rules/` | 无直接等价机制 | 核心规则已融入 `AGENTS.md` 和各 Skill 定义中 |
| Prompts 仓库 | `.trae/prompts/` | 无本地 Prompt 仓库 | 核心身份提示已融入 `AGENTS.md` |
| Skill 触发 | `skill-config.json` + 前端 UI | 自动根据 `name`/`description` 触发 | 保持 frontmatter 一致即可 |

---

## 待办兼容项

- [ ] `.trae/rules/` 中的苏格拉底式学习规则深度兼容 Kimi CLI（当前通过 `AGENTS.md` 和 Skill 内嵌规则已覆盖主要场景）

---

*最后更新：2026-04-14*
