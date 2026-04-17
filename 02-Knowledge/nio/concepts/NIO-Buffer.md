---
created: 2026-04-17
tags: [nio, buffer, io-model]
status: 🌿
mastery: 55
---

# NIO Buffer

## 一句话定义
Buffer是NIO中用于**数据暂存**的容器，通过维护position/limit/capacity三个状态实现读写模式切换，是Channel读写数据的"中转站"。


## 核心理解

### Buffer vs BIO byte[]

在BIO中，数据直接从`InputStream.read()`读到字节数组，需要手动维护读取位置和长度。而NIO的Buffer是一个"智能"容器，内部自动维护读写状态。

| 场景 | BIO (byte[]) | NIO (Buffer) |
|------|-------------|--------------|
| 记录读了多少 | 自己维护变量 | position自动跟踪 |
| 防止越界写 | 手动检查 | limit自动限制 |
| 重读数据 | 手动重置索引 | `rewind()`一键搞定 |
| 部分读取后继续写 | 手动移动数据 | `compact()`自动压缩 |

### 四个核心属性

```
0        position    limit              capacity
│           │          │                   │
▼           ▼          ▼                   ▼
┌───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┐
│ H │ e │ l │ l │ o │   │   │   │   │   │   │  底层数组
└───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┘
     ▲           ▲
     │           │
  已读数据     未读/可写区域
```

| 属性 | 作用 | 类比 |
|------|------|------|
| **capacity** | Buffer最大容量，创建后不变 | 数组的`length` |
| **position** | 下一个要读/写的位置 | "光标" |
| **limit** | 当前可读写边界 | "到此为止" |
| **mark** | 标记位置，可reset回退 | 书签 |

### 读写模式切换

```java
// 标准读写循环
ByteBuffer buffer = ByteBuffer.allocate(1024);

// 1. 写模式：从Channel读入Buffer
while (channel.read(buffer) != -1) {
    // 2. flip切换为读模式
    buffer.flip();
    
    // 3. 从Buffer读出数据
    while (buffer.hasRemaining()) {
        byte b = buffer.get();
    }
    
    // 4. clear清空，回到写模式
    buffer.clear();
}
```

**flip()做了什么？**
1. `limit = position`（设置可读边界为实际写入长度）
2. `position = 0`（从头开始读）

这让我想起之前学的**分段思想**——Buffer本质上也是"分段管理"：
- `0 ~ position-1`：已读区域
- `position ~ limit-1`：未读区域
- `limit ~ capacity-1`：不可读区域


## 关键关联

- [[BIO-BlockingIO]] - 关联原因：Buffer解决了BIO中byte[]需要手动维护读写位置的问题，通过flip/clear实现自动状态管理
- [[线程池]] - 关联原因：NIO的Buffer设计思想与线程池类似，都是"容器+状态管理"的模式，一个管理数据，一个管理线程
- [[分段锁思想]] - 关联原因：Buffer通过position/limit将数组分段管理，与ConcurrentHashMap的分段思想一脉相承
- [[NIO-Channel]] - 关联原因：Buffer是Channel的数据容器，Channel是Buffer的数据来源/目的地，两者配合完成NIO读写


## 我的误区与疑问

- ❌ 误区：`clear()`会清空数据
  - 纠正：`clear()`只是重置position和limit，数据还在，下次写入会覆盖

- ❓ 疑问：为什么需要`compact()`？
  - 答案：当Buffer还有未读数据，但需要写入新数据时，`compact()`把未读数据移到开头，避免数据丢失


## 代码与实践

```java
// Buffer的典型使用模式
ByteBuffer buffer = ByteBuffer.allocate(1024);

// 写数据
buffer.put("Hello".getBytes());

// 切换为读模式
buffer.flip();

// 读数据
while (buffer.hasRemaining()) {
    System.out.print((char) buffer.get());
}

// 回到写模式（数据还在，会被覆盖）
buffer.clear();
```


## 深入思考
💡 Buffer的设计体现了"状态机"思想：同一个对象通过状态切换实现不同功能，这比BIO的流式API更灵活但也更复杂。这种设计在Netty的ByteBuf中进一步发展（读写索引分离）。


## 来源
- 项目：[[bio-chatroom]]（对比学习）
- 对话：[[2026-04-17-NIO学习对话]]


---

## 🤖 AI评价

### 掌握度评估
- 当前等级：🌿理解
- 更新记录：
  - 2026-04-17: mastery=55 (深入理解Buffer核心概念，能解释与BIO的区别，理解flip/clear机制)

### 建议下一步
1. 完成Buffer练习（文件复制、消息编解码）
2. 学习Channel，理解Buffer+Channel协作模式

---

```dataview
TABLE status, mastery, length(file.inlinks) as "入链", length(file.outlinks) as "出链"
FROM #nio
SORT mastery DESC
```
