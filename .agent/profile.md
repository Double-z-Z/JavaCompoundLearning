# Learner Profile

> 学习者画像 - 帮助AI了解学习者的背景、目标和进度
>
> 🎯 **本文件是动态学习规划的唯一可信来源**
> 📚 历史记录：`.agent/_system/history.md`（不需主动读取）
> 📊 能力评估：`.agent/assessment/current.json`
> 🗺️ GOAL索引：`.agent/goals/GOAL-Index.md`

---

## 基本信息

| 属性 | 值 |
|------|-----|
| 当前水平 | 中级 (L2) |
| 目标水平 | L3（精通级） |
| 学习风格 | 项目驱动型，偏好通过实践理解原理 |
| 时间投入 | 时间不限 |
| 环境 | Windows + WSL / Java 8+17 / PVE |

---

## 当前快照

> 详见 `.agent/_system/META_认知快照.md`

---

## 拒绝清单

> 详见 `.agent/_system/META-拒绝清单.md`

---

## 环境默认值

| 类别 | 值 |
|------|-----|
| OS | Windows + WSL |
| Java | 8 / 17 |
| 虚拟化 | PVE (Proxmox Virtual Environment) |
| 构建工具 | Maven |
| IDE | VS Code / Trae |

---

## 个性化指令

- 解释原理时，使用我已掌握的概念作类比（如用线程池类比 NIO 的 Selector）
- 生成练习时，优先针对我的错误模式
- 发现知识关联时，主动建议更新知识图谱（如 NIO 与并发编程的关联）
- 长文内容自动提取结构化要点
- **P0 GOAL优先**：推荐前先检查是否与P0 GOAL缺口匹配

---

## 学习进度追踪

### 2026-05-07: Redis 压测瓶颈定位与优化验证

**完成内容**：
- 使用 bombardier 进行 HTTP 压测（预热后 38.7K QPS）
- 使用 Jedis 直连测试绕过 HTTP 层（89.7K QPS）
- 精确定位瓶颈：Spring MVC + JSON 序列化消耗 57% 性能
- **实施并验证纯文本响应优化**：QPS 从 38K → 44K（+14.2%），P99 延迟从 9.9ms → 7.2ms（-27.7%）

**产出物**：
- `docs/压测记录.md` - 更新分层测试数据和优化结果
- `03-Practice/drills/2026-05-07-redis-bottleneck-analysis.md` - 练习记录
- `02-Knowledge/redis/concepts/Redis-性能压测-分层排除法.md` - 方法论笔记
- `02-Knowledge/jvm/concepts/JVM预热效应.md` - 新增概念笔记
- `02-Knowledge/performance/concepts/压测工具对比.md` - 新增概念笔记
- `02-Knowledge/spring/concepts/Spring-MVC性能瓶颈.md` - 新增概念笔记
- `03-Practice/assessment/2026-05-07-Redis压测瓶颈定位-评估卡片.md` - 评估卡片

**mastery 更新**：
- Redis-性能压测: 40 → 70 (+30)
- 分层排除法: 0 → 65 (+65)
- Spring-MVC性能: 30 → 65 (+35)
- JVM预热效应: 0 → 55 (+55)
- 压测工具对比: 0 → 60 (+60)
