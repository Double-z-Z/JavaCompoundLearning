---
name: 错误归档
description: 分析出错误原因并且经过我的确认后
---

将这次错误归档到错误模式库。
格式：
- 错误现象：RejectedExecutionException
- 根本原因：线程池关闭后仍提交任务
- 我的思维误区：以为shutdown只是停止接收新任务
- 正确模式：shutdown后需等待终止，或使用isTerminated检查
- 关联知识：线程池生命周期管理
- 预防措施：提交前检查线程池状态
AI自动创建/更新  /03-Practice-Log/mistakes/thread-pool-01.md