# MOC (Map of Content)

> 主题地图，知识网络的可视化导航

---

## 定义

MOC = Map of Content（内容地图）

作用：
- 某个主题的知识导航中心
- 汇总该主题下的所有原子笔记
- 查看学习进度
- 发现知识缺口

## 文件格式

```markdown
---
tags: [MOC, 并发编程]
---

# MOC: 并发编程

## 核心概念
| 概念 | 描述 | 掌握状态 |
|-----|------|---------|
| [[线程池]] | | 🌿 |
| [[LongAdder]] | | 🌱 |

## 知识网络
```dataview
TABLE status, mastery
FROM #并发编程
SORT mastery DESC
```

## 学习进度
- [ ] 核心概念掌握80%以上
- [ ] 完成至少1个项目

## 存放位置

```
04-Maps/MOC-<主题>.md
```

## 与原子笔记的关系

```
MOC-并发编程.md（导航中心）
    ├── [[线程池]]（原子笔记）
    ├── [[LongAdder]]（原子笔记）
    └── [[CAS]]（原子笔记）
```

---

## 相关概念

- [原子笔记](atomic-notes.md) - MOC汇总原子笔记
- [复利学习](compound-learning.md) - MOC可视化知识网络
