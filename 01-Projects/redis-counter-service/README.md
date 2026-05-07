# Redis 高并发计数器服务

基于 Redis 实现的高性能分布式计数器服务，支持 Redis Cluster 模式，适用于秒杀库存扣减、限流计数、统计计数等场景。

## 功能特性

- ✅ 计数器自增/自减（原子操作）
- ✅ 支持 Redis Cluster 集群模式
- ✅ 设置计数器过期时间
- ✅ RESTful API 接口
- ✅ 环境变量配置支持
- 🔄 高并发压测验证（待完成）

## 技术栈

- Spring Boot 3.2.0
- Spring Data Redis
- Lettuce（Redis 客户端）
- Redis Cluster 6.x

## 快速开始

### 1. 环境要求

- JDK 17+
- Maven 3.6+
- Redis Cluster（6节点）或单节点 Redis

### 2. 配置

编辑 `application.yml` 或使用环境变量：

```yaml
spring:
  data:
    redis:
      cluster:
        nodes: 10.0.0.101:6379,10.0.0.102:6379,10.0.0.103:6379,10.0.0.104:6379,10.0.0.105:6379,10.0.0.106:6379
        max-redirects: 3
      password: ${REDIS_PASSWORD:}
```

**环境变量方式：**
```bash
export REDIS_CLUSTER_NODES="10.0.0.101:6379,10.0.0.102:6379,10.0.0.103:6379,10.0.0.104:6379,10.0.0.105:6379,10.0.0.106:6379"
export REDIS_PASSWORD="your-password"
```

### 3. 编译运行

```bash
mvn clean package
java -jar target/redis-counter-service-1.0-SNAPSHOT.jar
```

服务默认启动在 `http://localhost:8080`

---

## API 文档

Base URL: `http://localhost:8080`

### 计数器 API

| 方法 | URL | 说明 | cURL 示例 |
|------|-----|------|-----------|
| GET | `/counter/{key}` | 获取计数器值 | `curl http://localhost:8080/counter/test` |
| POST | `/counter/{key}/incr` | 自增 1 | `curl -X POST http://localhost:8080/counter/test/incr` |
| POST | `/counter/{key}/incr/{delta}` | 自增指定值 | `curl -X POST http://localhost:8080/counter/test/incr/10` |
| POST | `/counter/{key}/decr` | 自减 1 | `curl -X POST http://localhost:8080/counter/test/decr` |
| POST | `/counter/{key}/set/{value}` | 设置值 | `curl -X POST http://localhost:8080/counter/test/set/100` |
| POST | `/counter/{key}/set/{value}/expire?seconds={s}` | 设置值+过期时间 | `curl -X POST "http://localhost:8080/counter/test/set/100/expire?seconds=60"` |
| DELETE | `/counter/{key}` | 删除计数器 | `curl -X DELETE http://localhost:8080/counter/test` |

### 库存 API（防超卖）

| 方法 | URL | 说明 | cURL 示例 |
|------|-----|------|-----------|
| POST | `/stock/{sku}/init?quantity={n}` | 初始化库存 | `curl -X POST "http://localhost:8080/stock/SKU001/init?quantity=100"` |
| POST | `/stock/{sku}/decrement?quantity={n}` | 扣减库存 | `curl -X POST "http://localhost:8080/stock/SKU001/decrement?quantity=1"` |
| GET | `/stock/{sku}` | 查询库存 | `curl http://localhost:8080/stock/SKU001` |

---

## 配置说明

### application.yml 完整配置

```yaml
spring:
  application:
    name: redis-counter-service
  
  # Redis Cluster 配置
  data:
    redis:
      cluster:
        nodes: ${REDIS_CLUSTER_NODES:localhost:6379}
        max-redirects: 3
      password: ${REDIS_PASSWORD:}
      
      # Lettuce 连接池配置
      lettuce:
        pool:
          max-active: 50
          max-idle: 20
          min-idle: 5
          max-wait: 3000ms
        shutdown-timeout: 100ms
        cluster:
          refresh:
            adaptive: true
            period: 60s

# 服务器配置
server:
  port: 8080
  tomcat:
    threads:
      max: 200
      min-spare: 10

# 日志配置
logging:
  level:
    com.example.counter: DEBUG
    org.springframework.data.redis: INFO
```

### 环境变量说明

| 环境变量 | 说明 | 默认值 |
|----------|------|--------|
| `REDIS_CLUSTER_NODES` | Redis 集群节点地址，逗号分隔 | `localhost:6379` |
| `REDIS_PASSWORD` | Redis 密码 | 空 |
| `SERVER_PORT` | 服务端口 | `8080` |

---

## 项目结构

```
redis-counter-service/
├── src/
│   └── main/
│       ├── java/com/example/counter/
│       │   ├── CounterApplication.java      # 启动类
│       │   ├── CounterController.java       # REST API
│       │   ├── CounterService.java          # 服务接口
│       │   ├── CounterServiceImpl.java      # 服务实现
│       │   └── config/
│       │       └── RedisConfig.java         # Redis 配置
│       └── resources/
│           └── application.yml              # 配置文件
├── pom.xml
└── README.md
```

---

## 关联知识

- [[Redis-String]] - 计数器底层数据结构
- [[Redis-Cluster模式]] - 集群部署与连接
- [[Spring-Boot-自动配置]] - 自动配置原理
- [[ResponseEntity]] - REST API 响应封装

## 相关文档

- 项目笔记: [[项目设计和规划]]
- 设计思考: [[docs/design]]
