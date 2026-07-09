# 学习决策记录

> 记录每次学习推荐的用户选择，用于追踪学习路径

---

## 2026-07-08

- **用户选择**: 计划重置 — 保留全部 7 个 GOAL，重新设置冲刺 deadline + 启动新 1 个月冲刺 (7.8-8.5)
- **触发**: 旧冲刺 5.29-6.29 0/7 完成；40 天无任何决策记录；文档全面过期 5-7 周
- **AI推荐**: 同上 (3 个决策点全部同意)
- **6 月实际进展扫描** (重大发现):
  - mybatis-sql-lab 项目 5.30-6.01 完成 6 个 Phase，68 测试通过
  - devops-dashboard 项目 5.17 启动 Phase 1
  - 新增 29 篇原子笔记 (os/storage/jvm/performance/devops/cache/spring/orm)
- **Gap 状态修正**:
  - 6 Gap 升级 🟢: G-ORM-01/02 + G-LIN-01/02/03
  - 5 Gap 升级 🟡: G-SPR-02/03/04 + G-DB-03 + G-MQ-02/03
- **新冲刺分批**:
  | 批次 | deadline | GOAL | 备注 |
  |------|----------|------|------|
  | W1 | 7.15 | Linux (P2 收尾) | 提振信心 |
  | W2 | 7.22 | ORM(收尾) + 容器-Docker | 巩固成果 |
  | W3 | 7.29 | 数据库 + 消息中间件 | 新增能力 |
  | W4 | 8.5 | SpringCloud + Java核心 (P0) | 高难度攻坚 |
- **失败防御机制** (上轮 0/7 教训):
  1. 每周五回顾 → 缺口进度录入 Gap 矩阵（强制）
  2. 每周日决策 → 决定下周具体动作（不再批量规划）
  3. 每完成 1 个 GOAL → 立即标 completed + 更新认知快照（避免漏更）
  4. 每个 GOAL 设 3 个最小可交付物（避免"看了=做了"）
- **关联知识点**: [[EMRG-ORM与持久层]] / [[EMRG-Linux]] / [[EMRG-SpringCloud微服务]] / [[GOAL-ORM与缓存]] / [[GOAL-Linux系统管理]]

---

## 2026-05-29

- **用户选择**: 选项1 - 启动 W1 冲刺：Linux 系统管理（进程/内存/IO 监控）
- **AI推荐**: 选项1（匹配分批冲刺计划 W1，deadline 06-05，仅剩7天）
- **Gap关联**: G-LIN-01(Linux命令🟡) / G-LIN-02(Shell脚本🟡) / G-LIN-03(系统监控🔴)
- **关联知识点**: [[EMRG-Docker]]、[[ansible-redis-cluster]]、[[shell重定向]]
- **批次**: W1 低难度热身，建立冲刺节奏

## 2026-05-27

- **用户选择**: 选项1 - Redis 数据结构底层实现（SDS深入 → Ziplist → QuickList）
- **AI推荐**: 选项1（匹配P0 GOAL-Redis深入的G-RED-01缺口，deadline 07-06）
- **对话模式**: 苏格拉底式引导（从 char* 内存分配 → SDS 设计 → Ziplist 连锁更新 → QuickList 分块策略）
- **本次产出**:
  - [[Redis-SDS设计]] mastery 60→75（补充 5.0+ 多类型 header、Bitmap 利用、ArrayList 对比）
  - [[Redis-Ziplist设计]] 新建 mastery=70（结构设计、连锁更新、缓存优势、适用边界）
  - [[Redis-QuickList设计]] 新建 mastery=70（分块策略、OS 页对齐、LZF 压缩、爆炸半径控制）
- **Gap状态**: G-RED-01 剩余缺口 → SkipList（已有笔记待深化）
- **关联知识点**: [[EMRG-Redis]]、[[操作系统-内存页]]、[[Java-ArrayList]]、[[网络协议-消息边界]]

## 2026-05-16

- **用户选择**: 选项1 - Spring Cloud 服务注册与发现原理
- **AI推荐**: 选项1（匹配P0 GOAL-SpringCloud微服务的🔴高缺口）
- **Gap关联**: G-SPR-02 Spring Cloud核心组件、G-SPR-03 服务注册/发现
- **预计完成时间**: 待定
- **关联知识点**: [[EMRG-Sentinel-核心机制]]（熔断限流基础）、[[Spring配置管理]]、[[WebFlux响应式编程]]
