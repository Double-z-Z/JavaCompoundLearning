---
---

# 🎯 Java 复利学习仪表盘

> 今天是 {{date:YYYY-MM-DD}}


## 📊 学习统计

### 知识库健康度
```dataview
table length(file.inlinks) as "入链数", length(file.outlinks) as "出链数", status
from "02-Atomic-Notes"
sort file.mtime desc
limit 10
```

### 最近更新
```dataview
list
from "02-Atomic-Notes" or "03-Maps-of-Content"
sort file.mtime desc
limit 5
```


## 🌱 进行中的主题

| 主题 | 核心概念数 | 掌握度 | 下一步 |
|-----|----------|-------|-------|
| [[MOC-并发编程]] | | | |
| [[MOC-NIO网络编程]] | | | |


## ⚠️ 需要关注

### 活跃的错误模式
```dataview
list
from #mistake and #active
```

### 待整理的Inbox
- [ ] 检查 [[00-Inbox]] 是否需要清理


## 🔄 今日行动

- [ ] 复习：[[间隔重复卡片]]
- [ ] 整理：将Inbox中的内容原子化
- [ ] 链接：为新笔记添加至少2个双向链接
- [ ] 检查：Graph View中的孤立节点


## 📈 长期目标

- [ ] 并发编程：完成所有核心概念🌳
- [ ] NIO网络编程：开始第一个项目
- [ ] JVM底层：建立基础概念网络


---
*最后更新: {{date:YYYY-MM-DD HH:mm}}*
