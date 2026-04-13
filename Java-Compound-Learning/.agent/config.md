# Agent Configuration

> 本文件定义Agent查询项目所需的所有路径配置
> 路径均相对于项目根目录 `Java-Compound-Learning/`

---

## 项目结构

```
Java-Compound-Learning/
├── .agent/                 # Agent配置（本文件所在目录）
│   ├── config.md          # 路径配置
│   └── profile.md         # 学习者画像
├── 01-Projects/           # 项目实战
├── 02-Knowledge/          # 知识库
├── 03-Practice/           # 练习与反思
├── 04-Maps/               # 知识图谱（MOC）
├── 05-Templates/          # 笔记模板
└── README.md              # 项目说明
```

---

## 路径配置

### 1. Agent配置
| 配置项 | 路径 | 说明 |
|-------|------|------|
| config_file | `.agent/config.md` | 本文件 |
| profile_file | `.agent/profile.md` | 学习者画像 |

### 2. 知识库 (02-Knowledge/)
| 主题 | 概念笔记路径 | 深度文档路径 |
|-----|-------------|-------------|
| concurrency | `02-Knowledge/concurrency/concepts/` | `02-Knowledge/concurrency/deep-dives/` |
| nio | `02-Knowledge/nio/concepts/` | `02-Knowledge/nio/deep-dives/` |

### 3. 练习日志 (03-Practice/)
| 类型 | 路径 | 用途 |
|-----|------|------|
| drills | `03-Practice/drills/` | 练习记录 |
| reflections | `03-Practice/reflections/` | 对话反思 |
| mistakes | `03-Practice/mistakes/` | 错误档案 |

### 4. 知识图谱 (04-Maps/)
| 类型 | 路径 | 说明 |
|-----|------|------|
| moc_path | `04-Maps/` | MOC主题地图 |

### 5. 模板 (05-Templates/)
| 类型 | 路径 | 说明 |
|-----|------|------|
| template_path | `05-Templates/` | 笔记模板 |

---

## Agent查询流程

### 对话前必须执行
1. 读取 `.agent/config.md` 获取路径配置
2. 读取 `.agent/profile.md` 了解学习者画像
3. 根据 `profile.md` 中的已掌握知识点，扫描对应知识库
4. 扫描 `03-Practice/mistakes/` 了解历史错误模式

### 路径解析规则
- 所有路径均为相对于项目根目录的相对路径
- 项目根目录为 `Java-Compound-Learning/`
- 读取文件时，将上述路径拼接至项目根目录

---

## 更新记录

| 日期 | 更新内容 |
|-----|---------|
| 2026-04-13 | 初始配置，重构目录结构 |
