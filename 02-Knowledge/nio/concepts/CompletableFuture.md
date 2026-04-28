---
created: 2026-04-25
tags: [nio, concurrency, async]
status: 🌿
mastery: 55
---

# CompletableFuture

## 一句话定义
Java 8 提供的异步编程工具，支持函数式组合和 DAG（有向无环图）结构的异步任务编排，是对 Future 的增强版。


## 核心理解

### 与 Future 的关系
- **Future**（Java 5）：只读票根，只能阻塞获取结果（`get()`）
- **CompletableFuture**（Java 8）：可写收据，支持回调、组合、手动完成

### 核心设计：DAG 异步任务编排
```
CompletableFuture 不是简单的线性链，而是支持分叉-合并的 DAG：

                    source
                   /   |   \
                 [A]  [B]  [C]   ← 同一个 Future 的多个回调（Stack）
                  |    |    |
                  ▼    ▼    ▼
                 fA   fB   fC
                  |    |
                  ▼    ▼
                thenCombine  ← 合并多个 Future
                  |
                  ▼
               combined
```

### 数据结构：每个 Future 有自己的 Stack
```java
class CompletableFuture<T> {
    volatile Object result;           // 结果或异常
    volatile Completion stack;        // 等待这个 Future 的回调栈
}

abstract static class Completion {
    volatile Completion next;         // 链表下一个节点
    final CompletableFuture<?> dep;   // 依赖的 Future（子 Future）
    final Executor executor;          // 执行器
}
```

**为什么用 Stack 而不是 Queue？**
1. Treiber Stack 是无锁并发数据结构（CAS 实现）
2. 回调之间独立，执行顺序不影响结果
3. LIFO 符合"后添加的先处理"的直觉

### 链式调用 vs 分叉调用
```java
// 链式调用：A → B → C（顺序执行）
future.thenApply(A).thenApply(B).thenApply(C);
// 实际结构：A 在 future.stack，B 在 fA.stack，C 在 fB.stack

// 分叉调用：A、B、C 都依赖 source（独立执行）
source.thenApply(A);
source.thenApply(B);
source.thenApply(C);
// 实际结构：A、B、C 都在 source.stack 中
```

### 异常处理：显式分离
```java
future
    .thenApply(v -> transform(v))     // 正常链
    .exceptionally(ex -> {            // 异常链（独立 Handler）
        log.error(ex);
        return defaultValue;
    })
    .thenApply(v -> nextTransform(v)); // 继续正常链
```

**关键点**：出现异常后，后续的 `thenApply` 会被跳过，直到遇到 `exceptionally` 或 `handle`。

### 手动完成（类似 Promise 的 resolve/reject）
```java
CompletableFuture<String> future = new CompletableFuture<>();

// 在其他线程完成它
future.complete("success");           // 类似 resolve
future.completeExceptionally(e);      // 类似 reject
```

### 触发机制：谁"点燃鞭炮"？
```java
// 创建者调用 complete() 触发所有回调
future.complete(value);
    ├── lazySet(result, value)      // 快速设置结果
    ├── CAS(state, NEW, COMPLETED)  // 标记完成（强屏障）
    └── postComplete()              // 遍历 stack 触发回调
            ├── pop C, 执行 C.tryFire()
            ├── pop B, 执行 B.tryFire()
            └── pop A, 执行 A.tryFire()
```


## 关键关联

- [[Future]] - 关联原因：CompletableFuture 继承 Future，解决了 Future 只能阻塞获取的局限
- [[Promise]] - 关联原因：设计思想相同，都是"可写的异步结果"，支持手动完成和回调
- [[ChannelPromise]] - 关联原因：Netty 的 Promise 实现，与 CompletableFuture 角色相同
- [[Treiber-Stack]] - 关联原因：使用无锁 Treiber Stack 存储回调，保证并发安全
- [[ForkJoinPool]] - 关联原因：默认使用 ForkJoinPool.commonPool() 执行异步回调


## 我的误区与疑问

- ❌ 误区：以为 `thenApply(A).thenApply(B).thenApply(C)` 是把 A、B、C 放在同一个 stack 中
  - ✅ 纠正：每个 thenApply 创建新的 Future，A、B、C 分别在不同 Future 的 stack 中
  
- ❌ 误区：以为 Stack 的 LIFO 会影响链式调用的执行顺序
  - ✅ 纠正：链式调用是顺序执行（A→B→C），LIFO 只影响"同一个 Future 的多个独立回调"


## 代码与实践

### 基本用法
```java
// 创建
CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> "data");

// 链式转换
CompletableFuture<Integer> result = future
    .thenApply(String::length)
    .thenApply(len -> len * 2);

// 异常处理
future
    .thenApply(this::transform)
    .exceptionally(ex -> {
        log.error("Failed", ex);
        return "default";
    });

// 组合多个 Future
CompletableFuture<String> combined = future1
    .thenCombine(future2, (v1, v2) -> v1 + v2);

// 等待所有/任意
CompletableFuture<Void> all = CompletableFuture.allOf(f1, f2, f3);
CompletableFuture<Object> any = CompletableFuture.anyOf(f1, f2, f3);
```

### 手动完成
```java
CompletableFuture<String> future = new CompletableFuture<>();

// 在其他线程完成
new Thread(() -> {
    try {
        String result = doWork();
        future.complete(result);          // 成功
    } catch (Exception e) {
        future.completeExceptionally(e);  // 失败
    }
}).start();

// 等待结果
String result = future.get();
```


## 深入思考

💡 为什么 CompletableFuture 用 DAG 而不用简单链表？
- 支持复杂的异步流程：分叉（一个结果多个处理）、合并（多个结果一个处理）
- 每个 Future 独立管理自己的回调，避免共享状态的复杂性

💡 lazySet + CAS 的设计意义？
- lazySet：快速写入结果，延迟可见（性能优化）
- CAS：原子标记完成，强制刷新缓存（保证可见性）
- 配合：既保证性能，又保证最终一致性


## 来源
- 项目：[[netty-chatroom]]
- 对话：[[2026-04-25-CompletableFuture-对话]]


---

## 🤖 AI评价

### 掌握度评估
- 当前等级：🌿理解
- 更新记录：
  - 2026-04-25: mastery=55 (深入理解实现原理，能对比 TS Promise)

### 建议下一步
1. 阅读 CompletableFuture 源码，理解 `uniApply`、`postComplete` 的具体实现
2. 对比 Netty 的 `ChannelPromise`，理解框架特定优化
3. 实践：用 CompletableFuture 重构 NIO 聊天室的异步逻辑

---

```dataview
TABLE status, mastery, length(file.inlinks) as "入链", length(file.outlinks) as "出链"
FROM #nio OR #concurrency
SORT mastery DESC
```
