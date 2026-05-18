# 服务模板库

本文档定义支持的中间件服务模板。模板是预配置的服务蓝图。

## 模板结构规范

每个模板必须包含:
- `display_name`: 显示名称
- `category`: 分类
- `version`: 默认版本
- `default_config`: 基础配置（可被覆盖）
- `health_check`: 健康检查方式
- `resource_defaults`: 资源建议值
- `documentation`: 使用文档链接

---

## 已支持模板

### 1. Nacos Server (v2.2.3)

```yaml
template_id: nacos-server
display_name: "Nacos注册中心&配置中心"
category: service-discovery
version: "v2.2.3"

default_config:
  image: "nacos/nacos-server:v2.2.3"
  ports:
    - {container: 8848, host: 8848, desc: "Console & OpenAPI"}
    - {container: 9848, host: 9848, desc: "gRPC"}
    - {container: 9849, host: 9849, desc: "gRPC"}
  
  environment_variables:
    MODE: standalone
    NACOS_AUTH_ENABLE: "true"
    JVM_XMS: "512m"
    JVM_XMX: "1024m"
  
  depends_on: [mysql]           # 集群模式时依赖

health_check:
  endpoint: "/nacos/v1/console/health/readiness"
  initial_delay_seconds: 40
  period_seconds: 10
  timeout_seconds: 5

resource_defaults:
  memory: "1.5Gi"
  cpu: "500m"

documentation:
  console_url: "http://{host}:{port}/nacos"
  docs: "https://nacos.io/docs/latest/"
```

**使用场景**:
- Spring Cloud微服务的注册中心
- 分布式配置管理
- 服务发现与DNS

**注意事项**:
- 单机模式使用内嵌Derby数据库
- 生产环境必须外置MySQL
- 集群模式至少3节点

---

### 2. RabbitMQ (3.12-management)

```yaml
template_id: rabbitmq
display_name: "RabbitMQ消息队列"
category: message-queue
version: "3.12-management"

default_config:
  image: "rabbitmq:3.12-management"
  ports:
    - {container: 5672, host: 5672, desc: "AMQP Protocol"}
    - {container: 15672, host: 15672, desc: "Management UI"}
  
  environment_variables:
    RABBITMQ_DEFAULT_USER: admin
    RABBITMQ_DEFAULT_PASS: password
    RABBITMQ_VM_MEMORY_HIGH_WATERMARK: "0.6"

health_check:
  endpoint: "/api/healthchecks/node"
  initial_delay_seconds: 20
  period_seconds: 10

resource_defaults:
  memory: "512Mi"
  cpu: "250m"

documentation:
  console_url: "http://{host}:{port}"
  docs: "https://www.rabbitmq.com/documentation.html"
```

**使用场景**:
- 异步消息处理
- 任务队列
- 事件驱动架构

**性能参考** (单机):
- QPS: ~50K (简单路由)
- P99延迟: <10ms (局域网)
- 内存占用: ~512MB (空载)

---

### 3. MySQL 8.0

```yaml
template_id: mysql
display_name: "MySQL关系型数据库"
category: database
version: "8.0"

default_config:
  image: "mysql:8.0"
  ports:
    - {container: 3306, host: 3306}
  
  environment_variables:
    MYSQL_ROOT_PASSWORD: root
    MYSQL_CHARACTER_SET_SERVER: utf8mb4
    MYSQL_COLLATION_SERVER: utf8mb4_unicode_ci
  
  volumes:
    - {source: "./data/mysql", dest: "/var/lib/mysql", mode: rw}

health_check:
  command: "mysqladmin ping -h localhost -u root -p${MYSQL_ROOT_PASSWORD}"
  initial_delay_seconds: 30
  period_seconds: 10

resource_defaults:
  memory: "512Mi"
  cpu: "250m"

documentation:
  client_cmd: "mysql -h{host} -P{port} -uroot -p"
  docs: "https://dev.mysql.com/doc/refman/8.0/en/"
```

**使用场景**:
- Nacos集群的外置存储
- 业务应用数据库
- 开发测试环境

---

### 4. Redis 7.0

```yaml
template_id: redis
display_name: "Redis缓存/消息代理"
category: cache
version: "7.0"

default_config:
  image: "redis:7.0-alpine"
  ports:
    - {container: 6379, host: 6379}
  
  environment_variables:
    REDIS_PASSWORD: ""           # 空表示无密码
  
  command: "redis-server --appendonly yes --maxmemory 256mb --maxmemory-policy allkeys-lru"

health_check:
  command: "redis-cli ping"
  initial_delay_seconds: 10
  period_seconds: 5

resource_defaults:
  memory: "256Mi"
  cpu: "100m"

documentation:
  client_cmd: "redis-cli -h{host} -p{port}"
  docs: "https://redis.io/docs/"
```

**使用场景**:
- 应用缓存
- Session存储
- 发布/订阅消息
- Rate limiting

---

### 5. Prometheus + Grafana (监控栈)

```yaml
template_id: monitoring-stack
display_name: "Prometheus+Grafana监控"
category: observability
version: "latest"

default_config:
  services:
    prometheus:
      image: "prom/prometheus:v2.45.0"
      ports: [{container: 9090, host: 9090}]
      volumes:
        - {source: "./config/prometheus.yml", dest: "/etc/prometheus/prometheus.yml"}
    
    grafana:
      image: "grafana/grafana:10.2.0"
      ports: [{container: 3000, host: 3000}]
      environment_variables:
        GF_SECURITY_ADMIN_USER: admin
        GF_SECURITY_ADMIN_PASSWORD: admin123
      depends_on: [prometheus]

health_check:
  prometheus_endpoint: "/-/healthy"
  grafana_endpoint: "/api/health"

resource_defaults:
  prometheus:
    memory: "512Mi"
    cpu: "250m"
  grafana:
    memory: "256Mi"
    cpu: "100m"

documentation:
  prometheus_url: "http://{host}:9090"
  grafana_url: "http://{host}:3000"
  default_credentials: "admin/admin123"
```

**使用场景**:
- 服务监控与告警
- 性能指标可视化
- 日志分析（配合Loki）

---

## 模板扩展指南

添加新模板时需遵循:

1. **在`service-templates.yml`中注册**
2. **提供健康检查端点**
3. **标注资源需求**
4. **编写简短使用说明**

示例:
```yaml
template_id: my-custom-service
display_name: "我的自定义服务"
category: custom
version: "1.0.0"
# ... 其他必填字段
```
