---
type: deep-dive
id: DEEPDIVE-reactor-backpressure-netty
created: 2026-05-14
tags:
  - reactor
  - netty
  - 背压
  - webflux
  - backpressure
mastery: 75
source: "[[03-Practice/reflections/2026-05-14-Reactor-背压与WebFlux多线程-dialogue.md]]"
related_emrg: [[EMRG-Spring性能优化]]
related_goal: [GOAL-Java核心深化]
---

# Reactor 背压与 Netty 协调

## 一句话定义

Reactor Netty 将 Reactive Streams 的 item 级 `request(n)` 背压语义，通过 `FluxReceive` 对 `autoRead` 的控制（读方向）和 `MonoSendMany` 对 `isWritable()` 的检查（写方向），桥接到 Netty 的字节级缓冲水位，实现端到端的非阻塞流量控制。


## Netty 的两级缓冲

| 层级 | 名称 | 控制参数 | 作用 |
|------|------|---------|------|
| **内核级** | TCP 发送/接收缓冲区 | `SO_SNDBUF` / `SO_RCVBUF` | 内核网络栈缓冲 |
| **用户级** | `ChannelOutboundBuffer`（写）<br>`autoRead` + `SocketBufferHandler`（读） | `WRITE_BUFFER_HIGH_WATER_MARK`（默认 64KB）<br>`WRITE_BUFFER_LOW_WATER_MARK`（默认 32KB） | Netty 用户态缓冲与控制 |


## 读方向：HTTP 请求体背压

### 机制：`FluxReceive` 控制 `autoRead`

当 HTTP 请求体作为 `Flux<DataBuffer>` 到达时，`FluxReceive` 是源头 Publisher。

```java
// FluxReceive.drainReceiver() 简化逻辑
long r = receiverDemand;   // 下游通过 request(n) 累加的需求
long e = 0L;

while (e != r) {
    Object v = q.poll();   // 从接收队列取数据
    a.onNext(v);           // 推给下游（如 request.getBody()）
    e++;
}

// 背压核心：根据 receiverDemand 控制 Netty autoRead
if ((receiverDemand -= e) > 0L || (e > 0L && q.size() < QUEUE_LOW_LIMIT)) {
    if (needRead) {
        needRead = false;
        channel.config().setAutoRead(true);   // 下游还能吃，继续读 TCP
    }
} else if (!needRead) {
    needRead = true;
    channel.config().setAutoRead(false);      // 下游处理不过来了，停止读 TCP
}
```

### 端到端背压链条

```
业务代码消费慢
    → request.getBody().subscribe() 的 demand 小
    → FluxReceive.receiverDemand 耗尽
    → channel.config().setAutoRead(false)
    → Netty 不再从 SocketChannel read()
    → TCP 接收窗口不滑动
    → 客户端发送缓冲区填满
    → 客户端 write() 阻塞或返回 EAGAIN
```

### HTTP 请求头 vs 请求体

| 部分 | 是否有背压 | 原因 |
|------|-----------|------|
| **请求头** | ❌ 无 | 必须一次性读完整才能做 URI 路由、Header 解析、Controller 匹配，通常只有几 KB |
| **请求体** | ✅ 有 | `FluxReceive` 通过 `receiverDemand` 控制 `autoRead`，实现完整背压 |


## 写方向：HTTP 响应背压

### 机制：`MonoSendMany` 检查 `isWritable()`

```java
// MonoSendMany.SendManyInner.run() 简化逻辑
while (Integer.MAX_VALUE == r || r-- > 0) {
    I sourceMessage = queue.poll();
    ctx.write(encodedMessage, this);

    // 背压判断：Netty 写缓冲区满了，立刻 flush 并停止 request
    if (!ctx.channel().isWritable() 
        || readableBytes > ctx.channel().bytesBeforeUnwritable()) {
        needFlush = false;
        ctx.flush();
    }
}

// 根据实际写入量向上游请求下一批
int nextRequest = this.nextRequest;
if (terminalSignal == null && nextRequest != 0) {
    this.nextRequest = 0;
    s.request(nextRequest);  // ★ 只有 Netty 吃得下时才向上游要更多
}
```

### 大响应流式写入

WebFlux 不会一次性把整个大对象塞进内存：

1. 序列化层（Jackson、Spring Data）把大对象拆成多个 `DataBuffer` chunk，包装成 `Flux<DataBuffer>`
2. `ServerHttpResponse.writeWith(Publisher<DataBuffer>)` 接收这个 `Flux`
3. `MonoSendMany` 订阅该 `Flux`，每次只拿**当前 Netty 写缓冲区吃得下**的量
4. `isWritable()` 为 `false` 时停止 `request`，上游（数据库游标/序列化器）暂停生产

**典型场景**：MongoDB 流式返回 1GB 数据，背压确保内存平稳。


## 背压粒度：item 级到字节级的桥接

### 规范层 vs 实现层

| 层级 | 机制 | 作用 |
|------|------|------|
| **Reactive Streams 规范** | `request(n)` 控制 item 数量 | 控制上游发射多少个 `DataBuffer` |
| **Netty 桥接层** | `channel.isWritable()` / `bytesBeforeUnwritable()` | 写入时检查 Netty 字节级缓冲区 |
| **TCP 层** | TCP 滑动窗口 / 拥塞控制 | 内核级最终兜底 |

### 桥接逻辑

- `MonoSendMany` 从上游拿到一个 `DataBuffer`（item）
- 写入前检查 `channel.isWritable()`
- 若 Netty 写缓冲区还剩 10KB，当前 `DataBuffer` 是 8KB → 可以写
- 若写缓冲区已满（`isWritable()=false`），停止 `request`，已拿到的 item 暂存内部队列
- 当 Netty 发送后缓冲区低于低水位，触发 `channelWritabilityChanged`，恢复 `request`

**item 级背压不是缺陷**：每个 `DataBuffer` 带有 `readableByteCount()`，Reactor Netty 在写入时将 item 数量 × 每个 item 字节数，与 Netty 字节水位比较，实现了**item 级语义到字节级缓冲的映射**。


## Tomcat vs Reactor Netty 背压对比

| 维度 | Tomcat (Servlet) | Reactor Netty (WebFlux) |
|------|-----------------|------------------------|
| **背压协议** | 无应用级协议，靠阻塞/TCP 窗口隐式实现 | Reactive Streams 显式协议：`request(n)` + `cancel()` |
| **读控制** | 阻塞读（线程挂起）或 `ReadListener`（布尔信号） | `FluxReceive` 根据 `receiverDemand` 控制 `autoRead`，item 级精确控制 |
| **写控制** | 阻塞写（线程挂起）或 `WriteListener.isReady()` | `MonoSendMany` 根据 `isWritable()` 控制上游 request |
| **跨组件传播** | 不能。Controller 慢 → 线程阻塞，无法通知上游减速 | 可以。`request()` 从 Netty Channel 传播到 R2DBC/WebClient/Kafka |
| **线程代价** | 背压 = 阻塞 Worker 线程，线程池耗尽即拒绝 | 背压 = EventLoop 非阻塞调度，线程数固定，停止 request 让上游自限 |
| **大文件流式** | 阻塞模式下线程长期占用；非阻塞靠 `isReady()` 脉冲写入 | `Flux<DataBuffer>` 分 chunk，背压精确控制每个 chunk |

### 本质差异

> **Tomcat = "堵水管"**：下游慢 → 数据积存在 TCP 缓冲区 → 缓冲满 → 客户端阻塞/线程挂起。被动、粗粒度、以线程为代价。
>
> **Reactor Netty = "拉链条"**：下游 `request(n)` → 上游按需生产 → `autoRead`/`isWritable()` 执行。主动、细粒度、无阻塞线程代价。


## 关键关联

- [[WebFlux-生命周期与多线程时序]] -- 关联原因：背压发生在 Emission 阶段，需要理解 Subscription 到 Emission 的线程切换才能理解 `request(n)` 的跨线程传播
- [[Flux核心概念]] -- 关联原因：`Flux` 是背压的载体，冷流 "订阅后才生产" 的特性是背压生效的前提
- [[响应式生命周期信号]] -- 关联原因：`onSubscribe` 阶段建立的 `Subscription` 契约是背压的协议基础
- [[NIO-Selector]] -- 关联原因：`autoRead` 控制本质上是对 `OP_READ` 事件注册/注销的封装


## 常见误区

| 误区 | 正确理解 |
|------|---------|
| "HTTP 读没有背压" | 请求头无背压（必须一次性读完），请求体有完整背压 |
| "item 级背压无法控制字节流量" | Reactor Netty 通过 `DataBuffer` 字节数 × item 数量映射到 Netty 字节水位 |
| "背压只是让线程阻塞" | Reactor Netty 的背压是**非阻塞**的：停止 `request`，EventLoop 继续处理其他事件 |
| "Tomcat 和 Reactor Netty 背压效果一样" | 两者哲学完全不同：Tomcat 靠阻塞被动限流，Reactor Netty 靠协议主动协作 |


---

## 🤖 AI评价

### 掌握度评估
- 当前等级：🍎 应用（接近掌握）
- 已理解：
  - ✅ 读方向 `FluxReceive` → `autoRead` 的背压链条
  - ✅ 写方向 `MonoSendMany` → `isWritable()` 的背压链条
  - ✅ item 级到字节级的桥接原理
  - ✅ Tomcat 与 Reactor Netty 的背压哲学差异
- 下一步：源码级阅读 `FluxReceive.drainReceiver()` 和 `MonoSendMany` 的实现细节
