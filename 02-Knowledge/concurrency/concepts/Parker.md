---
created: 2026-04-12
tags: [concurrency, jvm]
status: 🌱
---

# Parker

## 一句话定义
Parker是JVM层封装**线程阻塞/唤醒**机制的C++类，通过_counter计数器优化，避免不必要的系统调用。


## 核心机制
1. **_counter计数器**：记录未消费的unpark，park时先检查
2. **pthread条件变量**：跨平台的阻塞/唤醒实现
3. **饱和语义**：多次unpark只计1次，避免计数器无限增长


## 为什么重要
- 是`LockSupport.park()/unpark()`的底层实现
- _counter优化减少了大量不必要的系统调用
- 理解Java并发工具的JVM层基础


## 与已学知识的关联
- [[线程阻塞]] → Parker是JVM层的实现
- [[futex]] → Parker底层通过pthread调用futex
- LockSupport → Java层API，直接调用Parker


## 代码逻辑
```cpp
class Parker : public os::PlatformParker {
    volatile int _counter;  // 关键优化：先检查再阻塞
    
    void park() {
        if (_counter > 0) {  // 有未消费的unpark
            _counter = 0;
            return;  // 直接返回，无需系统调用！
        }
        // 真正需要阻塞...
        pthread_cond_wait(&_cond, &_mutex);
    }
    
    void unpark() {
        if (_counter == 0) {
            _counter = 1;
            pthread_cond_signal(&_cond);
        }
    }
};
```


## 深入问题（苏格拉底式）
💡 为什么unpark()可以在park()之前调用？这和wait/notify有什么区别？


## 来源
- 原文档：[[thread-blocking-mechanism]]


---
- [x] 是否建立了至少2个知识关联？
- [x] 是否记录了可能的误区？
- [x] 是否留下了深入思考的问题？
