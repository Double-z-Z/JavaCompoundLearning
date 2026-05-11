# Sentinel 学习地图

> 学习日期：2026-05-11
> 状态：🔄 进行中（核心机制已完成，高级特性待探索）

---

## ✅ 已完成话题

### 1. 架构定位与设计哲学
- **本地优先**：决策在本地内存完成，纳秒级延迟，网络故障时依然可控
- **分层防护**：网关层（粗粒度）→ 应用层（细粒度），漏斗式过滤
- **优雅降级**：Token Server 故障 → 回退本地限流；时钟回拨 → 接受模糊统计
- **部署模式**：SDK 嵌入式 vs 独立网关 vs 集群 Token Server

### 2. Cache Line 性能优化
- **伪共享问题**：同一 Cache Line 的多变量被多线程同时写 → 缓存失效风暴
- **@Contended 注解**：128 字节 padding（2 个 Cache Line），需 JVM 参数 `-XX:-RestrictContended`
- **LongAdder vs AtomicLong**：
  - LongAdder 写快读慢（sum() 遍历所有 Cell）
  - AtomicLong 读快写有 CAS 竞争
  - Sentinel 双轨制：热路径用 AtomicLong，冷路径用 LongAdder

### 3. LeapArray 滑动窗口
- **数据结构**：`AtomicReferenceArray<WindowWrap<T>>` 环形数组
- **时间映射算法**：
  ```
  timeId = timeMillis / windowLengthInMs   // 大周期取商
  idx    = timeId % sampleCount             // 小范围取模
  ```
- **窗口四态处理**：空槽新建 / 过期复用重置 / 时钟回拨 / 有效直接复用
- **固定窗口 vs 滑动窗口**：滑动窗口消除边界突发问题

### 4. 限流算法
- **Sentinel 选择**：滑动窗口计数器（LeapArray 统计 + if 判断）
- **非令牌桶/漏桶原因**：LeapArray 已提供精确时间窗口统计，无需额外数据结构
- **本质**：读取当前窗口 count → 判断是否超阈值 → 通过则更新计数

### 5. 熔断机制
- **三种策略**：
  - 慢调用比例 (SLOW_REQUEST_RATIO)：RT 超时占比 > 阈值
  - 异常比例 (ERROR_RATIO)：异常占比 > 阈值
  - 异常数 (ERROR_COUNT)：绝对异常数 > 阈值
- **悬挂请求问题**：
  - Sentinel 方案：结果视角（延迟计入，请求结束后才统计）
  - 更优方案：分代桶（新生代→老年代→强制回收）+ AtomicLong 计数器
- **熔断状态机**：CLOSED → OPEN → HALF-OPEN → (探测成功)CLOSED / (失败)OPEN

### 6. 热点参数限流
- **三层混合架构**：
  ```
  Doorkeeper(布隆过滤器, 25%放行)
      ↓
  Count-Min Sketch(概率统计, d×w 数组, 取最小值)
      ↓ 晋升
  Precise Counter(HashMap, Top-K 精确限流, 容量上限2000)
  ```
- **核心原则**：长尾参数不存储 = 宽松放行（工程正确取舍）
- **升降级**：CMS 计数超阈值 → 晋升精确层；精确层 QPS 低于冷却阈值 → 降级回 CMS

---

## 🔮 待探索分支

| # | 话题 | 描述 | 优先级 |
|---|------|------|--------|
| A | **流量控制效果** | 直接拒绝 vs 预热(Warm Up) vs 排队等待 | ⭐⭐⭐ |
| B | **系统自适应限流** | 根据 CPU 使用率 / RT / 并发数自动调整阈值 | ⭐⭐⭐ |
| C | **熔断恢复机制** | Half-Open 探测期设计、探测失败策略、恢复超时配置 | ⭐⭐⭐ |
| D | **@SentinelResource 注解** | 编码集成方式：fallback / blockHandler / defaultFallback | ⭐⭐ |
| E | **上下文传播(Context)** | 调用链传递限流信息、父子关系、入口资源定义 | ⭐⭐ |
| F | **授权规则** | 黑白名单控制、来源应用限制 | ⭐ |
| G | **集群限流细节** | Token Server 通信协议、Netty 实现、故障转移机制 | ⭐⭐ |

---

## 📚 关联知识库文件

| 文件 | 内容 |
|------|------|
| `02-Knowledge/architecture/concepts/Sentinel-核心架构.md` | 架构定位、部署模式、监控机制 |
| `02-Knowledge/architecture/concepts/LeapArray-滑动窗口.md` | 时间映射算法、Cache Line 优化、时钟回退 |
| `02-Knowledge/architecture/concepts/Sentinel-熔断机制.md` | 三种策略、悬挂请求、状态机 |
| `02-Knowledge/architecture/concepts/Sentinel-热点参数限流.md` | 三层混合架构、CMS 原理、升降级 |
| `02-Knowledge/cache/concepts/Count-Min-Sketch.md` | CMS 数据结构、误差分析、空间效率 |
| `02-Knowledge/cache/concepts/W-TinyLFU.md` | Doorkeeper 机制、衰减策略 |

---

## 💡 核心洞察总结

```
┌─────────────────────────────────────────────────────────────┐
│                    Sentinel 设计哲学                         │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. 本地优先 > 远程依赖          （可用性优先）              │
│  2. 分层防护 > 单点保障          （纵深防御）                │
│  3. 模糊但稳定 > 精确但脆弱      （优雅降级）                │
│  4. 长尾丢弃 > 全量追踪          （工程取舍）                │
│  5. 复用现有能力 > reinvent wheel（简洁设计）               │
│                                                             │
│  核心数据结构: LeapArray (环形数组 + 时间映射)              │
│  核心算法:     滑动窗口计数器 (统计 + if 判断)              │
│  扩展机制:     三层混合 (Doorkeeper + CMS + HashMap)       │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 🎯 下次学习建议

1. **首选**：A 流量控制效果（最实用——预热和排队是日常高频使用）
2. **次选**：C 熔断恢复机制（衔接今天的话题）
3. **进阶**：B 系统自适应限流（最智能——理解系统负载感知）

---

*最后更新：2026-05-11*
