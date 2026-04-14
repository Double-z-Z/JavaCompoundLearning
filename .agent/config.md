# Agent Configuration

> 本文件定义 Agent 查询项目所需的所有路径配置
> 路径均相对于项目根目录（当前目录）
>
> 💡 **双平台支持**：本配置同时适用于 Trae IDE 和 Kimi CLI。

***

## 项目结构

```
├── .agent/                 # Agent 配置（本文件所在目录）
│   ├── config.md          # 路径配置
│   └── profile.md         # 学习者画像
├── 01-Projects/           # 项目实战
├── 02-Knowledge/          # 知识库
├── 03-Practice/           # 练习与反思
├── 04-Maps/               # 知识图谱（MOC）
├── 05-Templates/          # 笔记模板
└── README.md              # 项目说明
```

***

## 路径配置

### 1. Agent配置

| 配置项           | 路径                  | 说明    |
| ------------- | ------------------- | ----- |
| config\_file  | `.agent/config.md`  | 本文件   |
| profile\_file | `.agent/profile.md` | 学习者画像 |

### 2. 知识库 (02-Knowledge/)

| 主题          | 概念笔记路径                               | 深度文档路径                                 |
| ----------- | ------------------------------------ | -------------------------------------- |
| concurrency | `02-Knowledge/concurrency/concepts/` | `02-Knowledge/concurrency/deep-dives/` |
| nio         | `02-Knowledge/nio/concepts/`         | `02-Knowledge/nio/deep-dives/`         |

### 3. 练习日志 (03-Practice/)

| 类型          | 路径                         | 用途   |
| ----------- | -------------------------- | ---- |
| drills      | `03-Practice/drills/`      | 练习记录 |
| reflections | `03-Practice/reflections/` | 对话反思 |
| mistakes    | `03-Practice/mistakes/`    | 错误档案 |

### 4. 知识图谱 (04-Maps/)

| 类型        | 路径         | 说明      |
| --------- | ---------- | ------- |
| moc\_path | `04-Maps/` | MOC主题地图 |

### 5. 模板 (05-Templates/)

| 类型             | 路径              | 说明   |
| -------------- | --------------- | ---- |
| template\_path | `05-Templates/` | 笔记模板 |

***

## 知识库操作规范

本项目基于 Obsidian 原子笔记构建，操作知识库时遵循以下优先级：

### 检索与查询

| 方式                     | 优先级 | 使用场景                      |
| ---------------------- | --- | ------------------------- |
| **obsidian-cli skill** | 优先  | 搜索笔记、查询标签、获取反向链接、检查知识图谱关系 |
| 文件系统读取                 | 备选  | Obsidian 未运行时             |

### 创建与编辑

| 方式                          | 优先级 | 使用场景                             |
| --------------------------- | --- | -------------------------------- |
| **obsidian-markdown** skill | 优先  | 创建/编辑符合 Obsidian 规范的 Markdown 文件 |
| 标准文件操作                      | 备选  | 简单文件读写                           |

> 注：`obsidian-markdown` 技能在 Trae 中位于 `.trae/skills/obsidian-markdown/`，在 Kimi CLI 中位于 `.agents/skills/obsidian-markdown/`。

### 常用命令示例

```bash
# 搜索知识
obsidian search query="tag:concurrency"
obsidian search query="线程状态"

# 读取笔记
obsidian read file="futex"

# 获取反向链接
obsidian backlinks file="futex"

# 列出所有标签
obsidian tags
```

***

## Agent查询流程

### 对话前必须执行

1. 读取 `.agent/config.md` 获取路径配置
2. 读取 `.agent/profile.md` 了解学习者画像
3. **使用 obsidian-cli 扫描知识库**（优先）或文件系统扫描（备选），获取已掌握知识点
4. **使用 obsidian-cli 扫描错误档案**（优先）或文件系统扫描（备选），了解历史错误模式

### 路径解析规则

- 所有路径均为相对于项目根目录的相对路径
- 读取文件时，将上述路径拼接至项目根目录
- 使用 obsidian-cli 时，通过 `vault=<vault名称>` 指定目标仓库（如需要）

***

## 更新记录

| 日期         | 更新内容        |
| ---------- | ----------- |
| 2026-04-13 | 初始配置，重构目录结构 |

