# Redis 高并发计数器服务

基于 Redis 实现的高性能分布式计数器服务，支持百万 QPS 的计数场景。

## 功能特性

- ✅ 基础计数器（自增、自减、获取、设置）
- ✅ 过期时间设置
- ✅ 高并发支持（连接池优化）
- 🔄 Lua 脚本原子操作（Phase 2）
- 🔄 批量持久化到 MySQL（Phase 3）

## 快速开始

### 1. 启动 Redis

```bash
# 使用你之前部署的 Redis Cluster
# 或者本地启动单节点 Redis
redis-server
```

### 2. 编译运行

```bash
# 编译
mvn clean package

# 运行
java -jar target/redis-counter-service-1.0-SNAPSHOT.jar

# 或者使用 Spring Boot 插件
mvn spring-boot:run
```

### 3. 测试 API

```bash
# 自增计数器
curl -X POST http://localhost:8080/counter/article:123/views/incr
# {"key":"article:123","value":1,"action":"incremented"}

# 再次自增
curl -X POST http://localhost:8080/counter/article:123/views/incr
# {"key":"article:123","value":2,"action":"incremented"}

# 获取值
curl http://localhost:8080/counter/article:123
# {"key":"article:123","value":2,"action":"retrieved"}

# 自增指定值
curl -X POST http://localhost:8080/counter/article:123/incr/10
# {"key":"article:123","value":12,"action":"incremented"}

# 设置值
curl -X POST http://localhost:8080/counter/article:123/set/100
# {"key":"article:123","value":100,"action":"set"}

# 设置带过期时间（1小时）
curl -X POST "http://localhost:8080/counter/temp:counter/set/100/expire?seconds=3600"

# 删除
curl -X DELETE http://localhost:8080/counter/article:123
# {"key":"article:123","status":"deleted"}
```

## 项目结构

```
redis-counter-service/
├── src/main/java/com/example/counter/
│   ├── CounterApplication.java          # 启动类
│   ├── CounterService.java              # 服务接口
│   ├── CounterServiceImpl.java          # 服务实现
│   ├── CounterController.java           # REST API
│   └── config/
│       └── RedisConfig.java             # Redis 配置
├── src/main/resources/
│   ├── application.yml                  # 配置文件
│   └── lua/                             # Lua 脚本（Phase 2）
├── src/test/
│   └── CounterServiceTest.java          # 测试类
└── pom.xml
```

## 性能测试

```bash
# 使用 wrk 进行压测
wrk -t4 -c100 -d30s http://localhost:8080/counter/test:article/incr
```

## 学习阶段

### Phase 1: 基础计数器 ✅
- 实现基本的 INCR/DECR/GET/SET
- 配置 Redis 连接池
- REST API 接口

### Phase 2: Lua 脚本与库存扣减 🔄
- Lua 脚本管理器
- 库存扣减原子操作
- Pipeline 批量操作

### Phase 3: 批量持久化 🔄
- 批量写入管理器
- 异步持久化到 MySQL
- 性能压测与优化

## 关联知识

- [[Redis-String]] - 核心数据结构
- [[Redis-Lua脚本]] - 原子操作
- [[Redis-Pipeline]] - 性能优化
- [[Redis-持久化]] - 数据可靠性

## 项目笔记

详见：[redis-counter-service.md](../redis-counter-service.md)
