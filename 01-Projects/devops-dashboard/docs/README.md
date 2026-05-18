# DevOps Dashboard - 领域设计文档

> 本目录包含项目的核心领域设计，每个文档聚焦单一职责

## 文档索引

| 文档 | 职责 | 内容概要 | 优先级 |
|------|------|---------|--------|
| [domain-model.md](./domain-model.md) | 领域模型总览 | 聚合根识别、实体/值对象定义、关系图 | ⭐⭐⭐ 必读 |
| [design-decisions.md](./design-decisions.md) | 设计决策记录 | 关键架构决策及理由（含技术选型） | ⭐⭐⭐ 必读 |
| [implementation-plan.md](./implementation-plan.md) | 实施计划 | 分阶段路线图、任务清单、工具安装 | ⭐⭐⭐ 明天必读 |
| [data-structures.md](./data-structures.md) | 数据结构定义 | Environment/Experiment/Pipeline的完整Schema | ⭐⭐ 参考即可 |
| [service-templates.md](./service-templates.md) | 服务模板库 | Nacos/RabbitMQ/MySQL等中间件模板 | ⭐⭐ Phase 2参考 |
| [pipeline-design.md](./pipeline-design.md) | 流水线编排 | CI/CD阶段定义、门禁机制、自动化策略 | ⭐ Phase 4参考 |
| [api-contracts.md](./api-contracts.md) | API接口契约 | 核心接口定义（Java） | ⭐⭐ 编码时参考 |

---

## 📖 新手阅读顺序（推荐）

### 第一次打开项目时：
1. **先读本文** → 了解项目全景和设计原则
2. **读 [domain-model.md](./domain-model.md)** → 理解核心业务概念
3. **读 [design-decisions.md](./design-decisions.md) Decision 1-5** → 理解架构决策

### 开始编码前：
4. **读 [design-decisions.md](./design-decisions.md) Decision 6-10** → 了解技术选型理由
5. **读 [implementation-plan.md](./implementation-plan.md)** → 查看当前Phase的任务清单
6. **按任务清单开始编码**

---

## 设计原则

1. **聚合根优先**: Environment和Experiment是核心业务实体
2. **值对象不可变**: Spec/Quota/Policy等使用值对象保证不变性
3. **插件化基础设施**: Docker Compose/K8s/Ansible可互换
4. **环境晋升**: local → integration → staging → prod
5. **实验生命周期**: 创建 → 运行 → 结论 → 归档 → 清理
6. **响应式优先**: WebFlux非阻塞模型，适合IO密集型场景
7. **工具复用**: Grafana/Portainer替代自研通用功能，聚焦核心业务

---

## 🎯 当前阶段：Phase 1 基础搭建

**时间**: 2026-05-17 ~ 2026-05-31 (2周)

**核心目标**:
- ✅ 项目可编译运行
- ✅ 基础CRUD API可用
- ✅ Portainer已部署用于容器管理

**立即行动**:
```bash
# 1. 启动基础设施（PostgreSQL + Portainer）
docker-compose -f docker-compose.devtools.yml up -d postgres portainer

# 2. 验证环境
浏览器访问: http://localhost:9000 (Portainer)

# 3. 开始编码
详见 implementation-plan.md 的"本周目标"章节
```

---

## 📊 项目进度总览

```
Phase 1: ████████░░░░░░░░ 30% (进行中)
Phase 2: ░░░░░░░░░░░░░░░ 0% (待开始)
Phase 3: ░░░░░░░░░░░░░░░ 0% (待开始)
Phase 4: ░░░░░░░░░░░░░░░ 0% (可选)
```

**预计完成时间**: 2026-07-12 (8周后)
