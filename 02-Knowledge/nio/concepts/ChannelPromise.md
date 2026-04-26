---
created: 2026-04-25
tags: [nio, netty, async]
status: 🌿
mastery: 50
---

# ChannelPromise

## 一句话定义
Netty 中可写的异步 I/O 操作结果，允许 Handler 手动标记操作成功或失败，是 ChannelFuture 的可写版本。


## 核心理解

### 与 ChannelFuture 的关系
```
ChannelFuture (接口)
    ├── 获取 Channel
    ├── 添加监听器 (addListener)
    ├── 阻塞等待 (sync/await)
    └── 查询状态 (isSuccess/isDone)
    
    ↑ 继承
    
ChannelPromise (接口)
    ├── 继承所有 ChannelFuture 能力
    ├── setSuccess()          ← 新增：标记成功
    ├── setFailure()          ← 新增：标记失败
    └── setUncancellable()    ← 新增：设置为不可取消
```

### 角色对比

| 角色 | 对应类型 | 能力 | 使用场景 |
|------|---------|------|---------|
| **调用者** | ChannelFuture | 只读，监听结果 | 应用代码发起 I/O 操作 |
| **执行者** | ChannelPromise | 可写，标记完成 | Handler 执行 I/O 操作 |

### 与 CompletableFuture 的对应

| Java 标准库 | Netty | 角色 |
|------------|-------|------|
| `Future` | `ChannelFuture` | 只读票根 |
| `CompletableFuture` | `ChannelPromise` | 可写收据 |

### 使用流程
```
应用层调用：
    ChannelFuture future = channel.write(msg);
           │
           ▼
    Netty 创建 ChannelPromise（可写）
           │
           ▼
    传递给 Pipeline 的 Outbound Handler
           │
           ▼
    HeadContext.write(msg, promise)
           │
           ▼
    写入 Socket 缓冲区
           │
           ├── 成功 ──▶ promise.setSuccess()
           │                  │
           │                  ▼
           │            触发 future 的监听器
           │            唤醒 sync() 等待的线程
           │
           └── 失败 ──▶ promise.setFailure(e)
                          │
                          ▼
                    触发监听器（带异常）
                    唤醒等待线程（抛出异常）
```

### 代码示例

**调用者视角（只读）**：
```java
// 发起写操作，获取 Future
ChannelFuture future = channel.writeAndFlush(message);

// 方式1：阻塞等待
future.sync();  // 等待写入完成

// 方式2：监听回调
future.addListener(f -> {
    if (f.isSuccess()) {
        System.out.println("发送成功");
    } else {
        System.out.println("发送失败: " + f.cause());
    }
});
```

**Handler 视角（可写）**：
```java
public class MyHandler extends ChannelOutboundHandlerAdapter {
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        try {
            // 执行实际的写入操作
            doWrite(msg);
            
            // 成功！手动标记完成
            promise.setSuccess();  // 调用者会收到通知
            
        } catch (Exception e) {
            // 失败！手动标记异常
            promise.setFailure(e);  // 调用者会收到异常通知
        }
    }
}
```


## 关键关联

- [[ChannelFuture]] - 关联原因：ChannelPromise 继承 ChannelFuture，是"可写版本"的 Future
- [[CompletableFuture]] - 关联原因：设计思想相同，都是支持手动完成的异步结果
- [[ChannelPipeline]] - 关联原因：Promise 在 Pipeline 中传递，Handler 通过 Promise 通知操作完成
- [[Netty-OutboundHandler]] - 关联原因：Outbound Handler 通过 Promise 标记出站操作完成


## 我的误区与疑问

- ❌ 误区：以为 ChannelPromise 和 ChannelFuture 是两个独立的对象
  - ✅ 纠正：它们通常是同一个对象的不同接口视图，Promise 是可写接口，Future 是只读接口


## 代码与实践

### 创建 Promise
```java
// 通过 Channel 创建
ChannelPromise promise = channel.newPromise();

// 手动完成
promise.setSuccess();
promise.setFailure(new RuntimeException("error"));
```

### 在 Handler 中处理
```java
@Override
public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
    // 可以添加自己的逻辑
    System.out.println("Writing: " + msg);
    
    // 传递给下一个 Handler，同时传递 Promise
    ctx.write(msg, promise);
    
    // 或者自己处理并标记完成
    // promise.setSuccess();
}
```


## 深入思考

💡 为什么 Netty 要区分 ChannelFuture 和 ChannelPromise？
- **类型安全**：调用者只能读，不能写；Handler 可以写
- **职责分离**：调用者关注"什么时候完成"，Handler 关注"如何标记完成"
- **框架控制**：Netty 控制 Promise 的创建，确保生命周期管理

💡 ChannelPromise 与 CompletableFuture 的区别？
- ChannelPromise 专门用于 I/O 操作，与 Channel 绑定
- 支持取消操作（`cancel()`、`setUncancellable()`）
- 与 Netty 的 EventLoop 线程模型集成


## 来源
- 项目：[[netty-chatroom]]
- 对话：[[2026-04-25-ChannelPromise-对话]]


---

## 🤖 AI评价

### 掌握度评估
- 当前等级：🌿理解
- 更新记录：
  - 2026-04-25: mastery=50 (理解角色区分，能对比 CompletableFuture)

### 建议下一步
1. 阅读 Netty 源码，理解 DefaultChannelPromise 的实现
2. 实践：在自定义 Handler 中使用 ChannelPromise
3. 对比：ChannelPromise 与 Java 的 CompletableFuture 在异常处理上的差异

---

```dataview
TABLE status, mastery, length(file.inlinks) as "入链", length(file.outlinks) as "出链"
FROM #nio OR #netty
SORT mastery DESC
```
