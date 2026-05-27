# 学习决策记录

> 记录每次学习推荐的用户选择，用于追踪学习路径

---

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
