# Agent Configuration

> 路径配置与操作规范
> 路径均相对于项目根目录

***

## 路径配置

### Agent配置

| 配置项             | 路径                       | 说明               |
| --------------- | ------------------------ | ---------------- |
| config\_file    | `.agent/config.md`       | 本文件              |
| profile\_file   | `.agent/profile.md`      | 学习者画像            |
| assessment\_dir | `.agent/assessment/`     | 能力评估档案           |
| goals\_dir      | `.agent/goals/`          | 技术负债 MOC（GOAL 层） |
| system\_dir     | `.agent/_system/`        | 系统日志与仪表盘（Meta 层） |
| archive\_dir    | `.agent/goals/_archive/` | 过期 GOAL 归档       |

### 知识库 (02-Knowledge/)

| 主题          | 概念笔记                                 | 深度文档                                   |
| ----------- | ------------------------------------ | -------------------------------------- |
| concurrency | `02-Knowledge/concurrency/concepts/` | `02-Knowledge/concurrency/deep-dives/` |
| nio         | `02-Knowledge/nio/concepts/`         | `02-Knowledge/nio/deep-dives/`         |

### 练习日志 (03-Practice/)

| 类型          | 路径                         | 用途     |
| ----------- | -------------------------- | ------ |
| drills      | `03-Practice/drills/`      | 练习记录   |
| reflections | `03-Practice/reflections/` | 对话反思   |
| mistakes    | `03-Practice/mistakes/`    | 错误档案   |
| assessment  | `03-Practice/assessment/`  | 项目评估卡片 |

### 其他路径

| 类型             | 路径              | 说明                          |
| -------------- | --------------- | --------------------------- |
| moc\_path      | `04-Maps/`      | 知识图谱EMRG                    |
| template\_path | `05-Templates/` | 笔记模板                        |
| projects\_path | `01-Projects/`  | 项目实战                        |
| wiki\_path     | `wiki/`         | 项目文档（人读，Agent 忽略）           |
| todo\_file     | `TODOLIST.md`   | 待办事项（人维护，Agent 忽略）          |
| trae\_dir      | `.trae/`        | Trae IDE 专属配置（Agent 不操作）    |
| claude\_dir    | `.claude/`      | Claude Code 专属配置（Agent 不操作） |
| design\_dir    | `design/`       | 设计文档（人读，Agent 忽略）           |

***

## 知识库操作规范

### 工具优先级

| 操作   | 优先工具                    | 备选方案   |
| ---- | ----------------------- | ------ |
| 检索查询 | obsidian-cli skill      | 文件系统读取 |
| 创建编辑 | obsidian-markdown skill | 标准文件操作 |

### 常用命令

```bash
# 搜索
obsidian search query="tag:concurrency"

# 读取笔记
obsidian read file="futex"

# 获取反向链接
obsidian backlinks file="futex"

# 列出标签
obsidian tags
```

***

*更新：2026-05-06*
