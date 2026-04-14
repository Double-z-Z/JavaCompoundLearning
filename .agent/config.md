# Agent Configuration

> 路径配置与操作规范
> 路径均相对于项目根目录

---

## 路径配置

### Agent配置

| 配置项 | 路径 | 说明 |
|--------|------|------|
| config_file | `.agent/config.md` | 本文件 |
| profile_file | `.agent/profile.md` | 学习者画像 |
| assessment_dir | `.agent/assessment/` | 能力评估档案 |

### 知识库 (02-Knowledge/)

| 主题 | 概念笔记 | 深度文档 |
|------|---------|---------|
| concurrency | `02-Knowledge/concurrency/concepts/` | `02-Knowledge/concurrency/deep-dives/` |
| nio | `02-Knowledge/nio/concepts/` | `02-Knowledge/nio/deep-dives/` |

### 练习日志 (03-Practice/)

| 类型 | 路径 | 用途 |
|------|------|------|
| drills | `03-Practice/drills/` | 练习记录 |
| reflections | `03-Practice/reflections/` | 对话反思 |
| mistakes | `03-Practice/mistakes/` | 错误档案 |
| assessment | `03-Practice/assessment/` | 项目评估卡片 |

### 其他路径

| 类型 | 路径 | 说明 |
|------|------|------|
| moc_path | `04-Maps/` | 知识图谱MOC |
| template_path | `05-Templates/` | 笔记模板 |
| projects_path | `01-Projects/` | 项目实战 |
| archive_path | `99-Archive/` | 归档资料（AI忽略） |

---

## 知识库操作规范

### 工具优先级

| 操作 | 优先工具 | 备选方案 |
|------|---------|---------|
| 检索查询 | obsidian-cli skill | 文件系统读取 |
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

---

*更新：2026-04-14*
