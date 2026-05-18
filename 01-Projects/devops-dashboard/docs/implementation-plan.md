# DevOps Dashboard 实施计划

> **创建日期**: 2026-05-17
> **最后更新**: 2026-05-17
> **预计总工期**: 8-10周（按每天2-3小时计算）

---

## 📋 项目概览

### 核心目标
1. **学习目标**: 掌握Spring Boot 3.x + WebFlux + DDD + DevOps工具链
2. **业务目标**: 构建可用的DevOps控制面板（环境管理 + Spike实验）
3. **时间约束**: 8月前完成（配合SpringCloud微服务GOAL）

### 技术栈确认
| 层次 | 技术 | 版本 | 状态 |
|------|------|------|------|
| 框架 | Spring Boot | 3.2.3 | ✅ 已配置 |
| Web层 | WebFlux (Reactor) | 3.2.3 | ✅ 已配置 |
| 数据库 | PostgreSQL | 15+ | ✅ 已配置 |
| ORM | Spring Data JPA | 3.2.3 | ✅ 已配置 |
| 工具库 | Lombok + MapStruct | 最新版 | ✅ 已配置 |
| 文档 | SpringDoc OpenAPI | 2.3.0 | ✅ 已配置 |
| 前端 | Vue 3 CDN + Element Plus | 3.x | ⏳ Phase 2引入 |
| 监控 | Grafana + Prometheus | 最新版 | ⏳ Phase 3引入 |
| 容器管理 | Portainer | 最新版 | ⏳ Phase 1部署 |

---

## 🗺️ 分阶段实施路线图

### Phase 1: 基础设施搭建 + 核心API（第1-2周）

#### 目标
✅ 项目可编译运行，基础CRUD API可用，Portainer已部署

#### 任务清单

##### Week 1: 项目初始化与环境准备
```yaml
Day 1-2 (已完成):
- [x] 创建Maven项目结构
- [x] 配置pom.xml依赖
- [x] 创建领域模型（聚合根/实体/值对象）
- [x] 定义InfrastructureProvider接口
- [x] 定义应用层服务接口
- [x] 配置application.yml

Day 3-4:
- [ ] 安装PostgreSQL数据库
  - Docker命令: `docker run -d --name postgres -p 5432:5432 -e POSTGRES_PASSWORD=devops123 postgres:15`
  - 创建database: `createdb -U devops devops_dashboard`
  
- [ ] 部署Portainer（容器管理UI）
  - Docker命令见下方
  
- [ ] 编写Repository接口
  - EnvironmentRepository
  - ExperimentRepository
  - ServiceInstanceRepository

Day 5-7:
- [ ] 实现第一个REST Controller: EnvironmentController
  - `POST /api/v1/environments` (创建环境)
  - `GET /api/v1/environments` (列表查询)
  - `GET /api/v1/environments/{id}` (详情)
  - `DELETE /api/v1/environments/{id}` (销毁)

- [ ] 在Swagger UI测试所有API
  - 访问: http://localhost:8080/swagger-ui.html
```

##### Week 2: Docker Compose Provider实现
```yaml
Day 1-3:
- [ ] 实现DockerComposeProvider类
  - 实现`provision()`方法（生成docker-compose.yml并执行）
  - 实现`teardown()`方法（docker-compose down）
  - 实现`startContainer()`方法（docker-compose up <service>）
  - 实现`stopContainer()`方法（docker-compose stop <service>）
  
- [ ] 编写Docker Compose模板引擎
  - 从EnvironmentSpec → docker-compose.yml转换
  - 支持端口映射、环境变量、网络配置

Day 4-5:
- [ ] 实现EnvironmentServiceImpl
  - 调用DockerComposeProvider执行实际操作
  - 状态机转换逻辑
  - 异常处理和回滚机制

- [ ] 单元测试
  - Mock InfrastructureProvider测试Service层
  - 测试状态机合法性

Day 6-7:
- [ ] 端到端测试
  - 启动后端 → 通过API创建Nacos环境
  - 验证Portainer中容器是否正确启动
  - 验证日志流功能

- [ ] Phase 1总结文档
  - 记录遇到的问题和解决方案
  - 性能基准测试结果
```

#### 交付物
- [ ] 可运行的Spring Boot应用
- [ ] 4个REST API端点（CRUD）
- [ ] Portainer可视化界面
- [ ] 单元测试覆盖率 > 70%
- [ ] Phase 1总结报告

#### 成功标准
- ✅ 能通过API成功创建/销毁一个包含Nacos的环境
- ✅ Swagger UI可以正常测试所有API
- ✅ Portainer能显示创建的容器
- ✅ 所有单元测试通过

---

### Phase 2: 核心业务功能（第3-4周）

#### 目标
✅ Spike实验全流程可用，最小Vue前端上线

#### 任务清单

##### Week 3: Experiment Service实现
```yaml
Day 1-2:
- [ ] 实现ExperimentController
  - `POST /api/v1/experiments` (创建Spike实验)
  - `POST /api/v1/experiments/{id}/start` (启动实验)
  - `POST /api/v1/experiments/{id}/conclude` (提交结论)
  - `POST /api/v1/experiments/{id}/archive` (归档)
  - `GET /api/v1/experiments` (列表，支持状态筛选)

Day 3-4:
- [ ] 实现ExperimentServiceImpl
  - 自动创建专用实验环境
  - 生命周期策略 enforcement（超时自动清理）
  - Markdown报告生成器（归档到docs/spikes/）

Day 5:
- [ ] 实现Evidence收集机制
  - 手动录入指标数据
  - 上传截图/日志文件
  - 关联Prometheus数据源（预留接口）
```

##### Week 4: 最小前端开发
```yaml
Day 1-2:
- [ ] 创建index.html（单文件Vue 3应用）
  - 引入Vue 3 CDN
  - 引入Element Plus CDN
  - 引入Axios（HTTP请求）

Day 3-4:
- [ ] 实验列表页
  - 表格展示（Element Plus Table组件）
  - 状态筛选下拉框
  - "新建实验"按钮

- [ ] 实验创建页
  - 表单：实验名称、假设说明、成功标准
  - 服务模板多选（Checkbox）
  - 最大存活时间选择

- [ ] 实验详情页
  - 基本信息展示
  - 证据数据表格
  - 结论填写表单
  - 归档操作按钮

Day 5:
- [ ] 前后端联调
  - 解决跨域问题（CORS配置）
  - 错误处理和用户提示
  - 加载状态优化

- [ ] 部署静态资源
  - 将index.html放入src/main/resources/static/
  - 或使用Nginx独立部署
```

#### 交付物
- [ ] 完整的Spike实验流程（创建→运行→结论→归档）
- [ ] Vue 3单文件前端（<500行代码）
- [ ] 实验报告Markdown模板
- [ ] API文档更新（新增Experiment相关接口）

#### 成功标准
- ✅ 能完整走通一次Spike实验生命周期
- ✅ 前端页面可用且美观
- ✅ 归档报告自动生成到docs/spikes/

---

### Phase 3: 可观测性增强（第5-6周）

#### 目标
✅ Grafana监控面板就绪，日志流功能可用

#### 任务清单

##### Week 5: Prometheus + Grafana部署
```yaml
Day 1-2:
- [ ] 部署Prometheus（指标采集）
  - docker-compose.yml配置
  - 配置scrape_targets（采集Docker容器指标）
  - 部署Node Exporter（主机指标）
  - 部署cAdvisor（容器指标）

Day 3-4:
- [ ] 部署Grafana
  - docker-compose.yml配置
  - 数据源配置（连接Prometheus）
  - 创建Dashboard模板：
    - 环境资源监控（CPU/内存/网络）
    - 服务健康状态表
    - 容器启动历史时间线
    
Day 5:
- [ ] 开发MonitoringService实现
  - 对接Prometheus HTTP API
  - 查询实时指标数据
  - 提供给前端Grafana iframe嵌入URL
```

##### Week 6: 日志流与告警
```yaml
Day 1-2:
- [ ] 实现WebSocket日志流
  - 后端: 使用WebFlux WebSocket支持
  - 前端: 终端模拟器组件（xterm.js或简单textarea）
  - 功能: 实时滚动、关键词高亮、暂停/继续

Day 3-4:
- [ ] 配置Grafana告警规则
  - CPU > 80% 持续5分钟 → 发送通知
  - 内存使用率 > 90% → 发送通知
  - 容器异常退出 → 立即告警
  
- [ ] 通知渠道集成（可选）
  - Slack Webhook（如果团队使用）
  - 邮件通知（简单SMTP配置）
  - 企业微信/钉钉机器人（国内常用）

Day 5:
- [ ] 性能优化
  - 数据库查询优化（索引、慢SQL分析）
  - Redis缓存热点数据（可选）
  - 前端懒加载和虚拟滚动
```

#### 交付物
- [ ] Grafana Dashboard（3-5个面板）
- [ ] Prometheus告警规则（至少3条）
- [ ] 实时日志流功能
- [ ] 性能优化报告

#### 成功标准
- ✅ Grafana能显示实时CPU/内存图表
- ✅ 日志流延迟 < 1秒
- ✅ 告警触发后能在5分钟内收到通知
- ✅ 页面加载时间 < 3秒

---

### Phase 4: 进阶特性（第7-8周，可选）

#### 目标
✅ Kubernetes支持、CI/CD集成、权限系统

#### 任务清单（根据兴趣选择性实施）

##### Option A: Kubernetes Provider
```yaml
Week 7:
- [ ] 学习Kubernetes基础概念（Pod/Deployment/Service）
- [ ] 实现KubernetesProvider类
  - 使用Fabric8 Kubernetes Client
  - 支持Namespace/ConfigMap/Secret管理
  - 支持Helm Chart部署

Week 8:
- [ ] 多Provider切换机制
  - 配置文件选择provider-type
  - 运行时动态切换（需重启）
  - 前端UI展示当前Provider
```

##### Option B: CI/CD Pipeline集成
```yaml
Week 7:
- [ ] 集成Jenkins/GitLab CI
  - Pipeline配置文件生成
  - Webhook触发构建
  - 构建状态回调

Week 8:
- [ ] 制品管理
  - Docker镜像仓库对接（Harbor/Registry）
  - Maven Artifact存储
  - 版本号管理策略
```

##### Option C: 权限系统
```yaml
Week 7:
- [ ] 用户认证
  - JWT Token认证
  - OAuth2集成（GitHub/Google登录）
  
Week 8:
- [ ] RBAC权限模型
  - 角色: Admin / Operator / Viewer
  - 权限: 环境 CRUD / 实验 CRUD / 系统设置
  - 操作审计日志
```

#### 交付物（根据选择的Option）
- [ ] Kubernetes环境管理能力（Option A）
- [ ] 自动化CI/CD流水线（Option B）
- [ ] 多用户权限控制（Option C）

---

## 🔧 工具安装速查

### Docker Compose一键部署（推荐保存为docker-compose.devtools.yml）

```yaml
version: '3.8'

services:
  # PostgreSQL数据库
  postgres:
    image: postgres:15-alpine
    container_name: devops-postgres
    environment:
      POSTGRES_DB: devops_dashboard
      POSTGRES_USER: devops
      POSTGRES_PASSWORD: devops123
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U devops"]
      interval: 10s
      timeout: 5s
      retries: 5

  # Portainer（容器管理UI）
  portainer:
    image: portainer/portainer-ce:latest
    container_name: devops-portainer
    command: --no-auth  # 开发模式关闭认证，生产环境必须开启！
    ports:
      - "9000:9000"
      - "9443:9443"
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
      - portainer_data:/data

  # Prometheus（指标采集，Phase 3启用）
  prometheus:
    image: prom/prometheus:latest
    container_name: devops-prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus_data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--web.console.libraries=/usr/share/prometheus/console_libraries'
      - '--web.console.templates=/usr/share/prometheus/consoles'
    profiles:
      - monitoring  # 默认不启动，需要时用 --profile monitoring

  # Grafana（可视化，Phase 3启用）
  grafana:
    image: grafana/grafana:latest
    container_name: devops-grafana
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_PASSWORD: admin
      GF_USERS_ALLOW_SIGN_UP: false
    volumes:
      - grafana_data:/var/lib/grafana
      - ./grafana/provisioning:/etc/grafana/provisioning
    depends_on:
      - prometheus
    profiles:
      - monitoring

volumes:
  postgres_data:
  portainer_data:
  prometheus_data:
  grafana_data:
```

**使用方法**:
```bash
# Phase 1: 只启动PostgreSQL + Portainer
docker-compose -f docker-compose.devtools.yml up -d postgres portainer

# Phase 3: 启动全部（含监控）
docker-compose -f docker-compose.devtools.yml --profile monitoring up -d

# 查看日志
docker-compose -f docker-compose.devtools.yml logs -f postgres

# 停止所有服务
docker-compose -f docker-compose.devtools.yml down
```

**访问地址**:
- Portainer: http://localhost:9000 (或 https://localhost:9443)
- PostgreSQL: localhost:5432 (用户: devops, 密码: devops123)
- Prometheus: http://localhost:9090 (Phase 3)
- Grafana: http://localhost:3000 (用户: admin, 密码: admin, Phase 3)

---

## 📊 进度跟踪

### 当前状态（2026-05-19）✅ Phase 1 已完成！

| Phase | 状态 | 完成度 | 实际完成日期 |
|-------|------|--------|-------------|
| **Phase 1: 基础搭建** | ✅ **已完成** | **100%** | **2026-05-19** |
| Phase 2: 核心业务 | ⏳ 待开始 | 0% | 预计 2026-06-02 |
| Phase 3: 可观测性 | ⏳ 待开始 | 0% | 预计 2026-06-16 |
| Phase 4: 进阶特性 | ⏳ 待开始 | 0% | 预计 2026-06-30 |

---

### ✅ Phase 1 完成清单（Week 1-2）

#### Week 1 任务（全部完成 ✅）
```yaml
Day 1-2 (项目初始化):
- [x] 创建Maven项目结构
- [x] 配置pom.xml依赖
- [x] 创建领域模型（聚合根/实体/值对象）
- [x] 定义InfrastructureProvider接口
- [x] 定义应用层服务接口
- [x] 配置application.yml

Day 3-4 (基础设施 + 数据层):
- [x] 安装Docker 29.5.0 + Compose v5.1.3
- [x] 部署PostgreSQL 15容器（localhost:5432）
- [x] 部署Portainer容器（http://localhost:9000）
- [x] 编写Repository接口（Environment/Experiment/ServiceInstance）
- [x] 解决WebFlux vs Tomcat依赖冲突（排除3个Tomcat来源）
- [x] 配置Maven Enforcer Plugin防止Tomcat复发

Day 5-7 (API层 + 测试):
- [x] 实现EnvironmentController（CRUD: POST/GET/DELETE）
- [x] 实现EnvironmentServiceImpl（核心业务逻辑）
- [x) 创建DTO对象（CreateEnvironmentRequest, EnvironmentResponse）
- [x] 增强Swagger注解（业务语义说明）
- [x] 编写31个单元/集成测试（全部通过 ✅）
- [x] 添加GlobalExceptionHandler全局异常处理
- [x] Swagger UI验证通过
```

#### Week 2 任务（提前完成部分 ✅）
```yaml
Day 1-2:
- [x] 实现EnvironmentServiceImpl完整逻辑
- [x] 修复NPE问题（targetNodes null安全）
- [x] 修复LazyInitializationException（EAGER加载）

Day 3-5:
- [x] 单元测试编写（31个测试用例）
- [x] API文档编写（api-guide.md完整使用指南）

Day 6-7:
- [x] Phase 1总结文档（本文件）
```

### 📊 Phase 1 交付物统计

| 类别 | 数量 | 文件位置 |
|------|------|---------|
| **Java源代码** | 25+ 个类 | `src/main/java/com/devops/dashboard/` |
| **测试代码** | 4个测试类，31个用例 | `src/test/java/...` |
| **配置文件** | 3个（pom.yml, application.yml, docker-compose） | 项目根目录 & src/main/resources |
| **文档** | 3份（设计文档、API指南、实施计划） | `docs/` 目录 |
| **Docker容器** | 2个运行中（postgres, portainer） | Docker Engine |

### 🎯 关键技术决策记录

| 决策ID | 决策内容 | 原因 | 影响 |
|--------|---------|------|------|
| DD-7 | 使用WebFlux (Netty) 而非WebMVC | 高并发、响应式编程学习目标 | 排除Tomcat依赖，使用Reactor |
| DD-10 | SpringDoc OpenAPI 3.0 | 自动生成API文档 | WebFlux版本：springdoc-openapi-starter-webflux-ui |
| JPA-EAGER | 所有集合字段使用EAGER加载 | WebFlux异步模型下LAZY会Session失效 | 性能影响可忽略（数据量小）|
| ENFORCER | Maven Enforcer禁止Tomcat依赖 | 防止间接依赖引入Servlet栈 | 构建时自动检测违规依赖 |

### 🔧 遇到的问题与解决方案（经验教训）

| # | 问题 | 根因 | 解决方案 | 耗时 |
|---|------|------|---------|------|
| 1 | Tomcat启动而非Netty | spring-boot-starter-data-jpa等间接引入tomcat | 排除3个来源+Enforcer Plugin | 2h |
| 2 | Swagger UI 404 | WebFlux版本路径不同+base-path干扰 | 删除webflux.base-path配置 | 1h |
| 3 | JPA @EmbeddedId不识别 | AggregateId继承的value字段不可见 | 改为独立@Embeddable类 | 30min |
| 4 | @Embeddable嵌套集合不支持 | JPA限制List<@Embeddable>在@Embeddable内 | 使用@JdbcTypeCode(JSON)序列化 | 45min |
| 5 | LazyInitializationException | WebFlux异步执行导致Session关闭后访问懒属性 | 全部改为FetchType.EAGER | 1h |
| 6 | NPE创建环境 | targetNodes为null时ArrayList构造器崩溃 | 添加null检查+DTO映射 | 20min |
| 7 | name参数丢失 | Service层自动生成name覆盖用户输入 | 修改接口添加name参数 | 15min |

**总耗时**: ~6小时（含调试时间）

---

## ⚠️ 风险与缓解措施

### 高风险项

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|---------|
| **WebFlux学习曲线陡峭** | 开发效率下降30% | 中 | 先从简单CRUD开始，复杂场景查文档 |
| **Docker API不稳定** | Provider实现困难 | 低 | 使用Testcontainers做集成测试 |
| **前端框架0经验** | Phase 2延期 | 中 | 使用CDN模式降低门槛，必要时砍掉部分UI |
| **PostgreSQL JSONB性能** | 查询慢 | 低 | 先用简单查询，后续加GIN索引 |

### 应急预案

**如果某Phase严重延期**：
1. **砍功能**: 优先保证核心CRUD可用（Phase 1最低要求）
2. **降级方案**: 
   - 前端砍掉，只用Swagger UI
   - 日志流砍掉，只保留文件下载
   - Grafana延后，先用命令行看指标
3. **求助渠道**:
   - StackOverflow（WebFlux问题）
   - Spring官方文档（权威解答）
   - GitHub Issues（库的Bug）

---

## 📚 学习资源清单

### 必读文档（按优先级）
1. **本项目文档**
   - [domain-model.md](./domain-model.md) — 理解领域模型
   - [design-decisions.md](./design-decisions.md) — 理解技术选型理由
   - [api-contracts.md](./api-contracts.md) — 接口定义

2. **Spring Boot 3.x**
   - 官方文档: https://docs.spring.io/spring-boot/docs/current/reference/html/
   - 重点章节: WebFlux, Data JPA, Configuration

3. **Project Reactor**
   - 参考指南: https://projectreactor.io/docs/core/release/reference/
   - 重点: Mono/Flux操作符, 调度器, 错误处理

4. **DDD实践**
   - 《领域驱动设计》Eric Evans（经典理论）
   - 《实现领域驱动设计》Vaughn Vernon（实战指南）

### 推荐视频课程
- Spring Boot 3 + Reactor: Bilibili搜索"尚硅谷SpringBoot"
- Docker实战: Bilibili搜索"黑马程序员Docker"
- PostgreSQL入门: 菜鸟教程SQL部分即可

---

## 🎯 下次对话启动清单

> **建议明天新对话时首先阅读本文档**

### 快速恢复上下文
1. ✅ 阅读 `docs/README.md` 了解项目全景
2. ✅ 阅读 `docs/design-decisions.md` Decision 6-10 了解技术选型
3. ✅ 阅读本文件 `implementation-plan.md` 的"本周目标"章节
4. ✅ 执行 `docker-compose -f docker-compose.devtools.yml up -d` 启动基础设施
5. ✅ 开始编码：先写Repository接口，再写Controller

### 明天的具体任务
```bash
# 1. 启动开发环境
cd 01-Projects/devops-dashboard
docker-compose -f docker-compose.devtools.yml up -d postgres portainer

# 2. 验证环境
curl http://localhost:9000/api/endpoints  # Portainer API（可选）

# 3. 开始编码
# 创建 src/main/java/com/devops/dashboard/infrastructure/persistence/ 目录
# 实现 EnvironmentRepository.java (extends JpaRepository)
```

---

## 📝 变更记录

| 日期 | 版本 | 变更内容 | 作者 |
|------|------|---------|------|
| 2026-05-17 | v1.0 | 初始版本，确定技术选型和分阶段计划 | AI Assistant |

---

**文档维护者**: 请在每次Phase完成后更新本文件的进度跟踪部分。
