---
created: 2026-05-17
type: project
id: PROJECT-运维控制面板
aliases:
  - PROJECT-运维控制面板
tags: [devops, docker, ansible, springboot]
status: planning
mastery: 0
related_emrg: []
related_goal: [GOAL-容器编排, GOAL-SpringCloud微服务, GOAL-Linux系统管理]
---

# DevOps控制面板

> 项目目标：构建一个中央控制面板，实现一键部署、状态监控和服务生命周期管理
> 项目类型：综合应用型（同时推进容器编排、SpringCloud、Linux三个GOAL）


## 涉及知识点

| 知识点 | 在项目中的角色 | 相关练习 |
|-------|--------------|---------|
| [[Docker Compose]] | 服务编排核心 | [[练习记录-Phase1]] |
| [[Ansible]] | 批量部署工具 | [[练习记录-Phase2]] |
| [[Spring Boot]] | 后端API服务 | [[练习记录-Phase2]] |
| [[Shell脚本]] | 基础设施自动化 | [[练习记录-Phase1]] |
| [[Vue.js]] | 前端可视化 | [[练习记录-Phase3]] |


## 架构设计

### 项目结构
```
devops-dashboard/
├── backend/                    # Spring Boot后端
│   ├── src/main/java/com/example/dashboard/
│   ├── src/main/resources/application.yml
│   └── pom.xml
├── frontend/                   # Vue前端
│   ├── src/
│   └── package.json
├── ansible/                   # Ansible配置
│   ├── inventory.ini          # 目标节点清单
│   ├── deploy.yml             # 部署Playbook
│   └── templates/             # 配置模板
├── docker-compose/            # 服务编排模板
│   ├── nacos.yml
│   ├── rabbitmq.yml
│   └── common.yml
├── scripts/                   # Shell脚本
│   ├── deploy.sh              # 一键部署脚本
│   └── ssh-setup.sh           # SSH免密配置
└── README.md
```

### 关键设计决策
- **决策1**：采用Agent模式，开发机作为控制中心，各VM作为目标节点，便于灵活扩展
- **决策2**：先用Docker Compose实现服务编排，未来可无缝演进到K8s
- **决策3**：后端用Spring Boot，既能满足当前需求，又能作为SpringCloud学习的起点

### 组件交互流程
```
[前端页面] ↔ [Spring Boot API] ↔ [Ansible] ↔ [目标VM]
                                          ↓
                                   [Docker容器]
```


## 实现阶段

### Phase 1: 基础设施搭建（第1-2周）
**目标**: 实现SSH免密登录 + 一键部署单个应用 + Docker化中间件
**验证方式**: 
- `./scripts/deploy.sh rabbitmq` 一键启动RabbitMQ容器
- `./scripts/deploy.sh nacos` 一键启动Nacos容器
**关联练习**: [[练习记录-Phase1]]
**可能遇到的问题**: 
- ⚠️ SSH密钥权限问题（参考常见错误模式）
- ⚠️ Docker网络配置冲突

### Phase 2: 服务编排与API（第3-4周）
**目标**: Docker Compose编排 + Spring Boot API + 基础状态查询
**验证方式**: 
- 编写`docker-compose.yml`启动完整微服务栈
- API返回所有服务状态（running/stopped）
**关联练习**: [[练习记录-Phase2]]
**可能遇到的问题**: 
- ⚠️ Compose网络模式选择
- ⚠️ 容器间通信配置

### Phase 3: 可视化Dashboard（第5-6周）
**目标**: Web前端页面 + 日志查看 + 批量操作 + 配置模板化
**验证方式**: 
- 页面实时显示各服务状态（绿/红指示灯）
- 点击按钮启停服务
- 支持多环境配置（dev/test/prod）
**关联练习**: [[练习记录-Phase3]]
**可能遇到的问题**: 
- ⚠️ 跨域问题（CORS配置）
- ⚠️ 实时日志流的实现


## 性能测试与对比

### 测试环境
- CPU: PVE 8核 / 目标VM按需分配
- 内存: PVE 32GB / 目标VM按需分配
- JDK版本: 17

### 对比方案
| 方案 | 部署时间 | 资源占用 | 扩展性 | 适用场景 |
|-----|---------|---------|-------|---------|
| 纯Shell脚本 | <30s | 低 | 差 | 简单场景 |
| **本项目实现** | <60s | 中 | 好 | 中小团队 |
| K8s方案 | >120s | 高 | 优秀 | 大规模集群 |


## 项目特有的坑与解决方案

### 问题1: SSH权限问题
**现象**: 密钥配置后仍需输入密码
**根因**: ~/.ssh目录或密钥文件权限过于开放（SSH要求严格权限）
**解决**: `chmod 700 ~/.ssh && chmod 600 ~/.ssh/id_rsa`
**预防**: 在脚本中自动设置正确权限

### 问题2: 端口冲突
**现象**: 启动容器时报端口已被占用
**根因**: 多个服务共用同一端口或宿主机端口被占用
**解决**: 使用docker-compose的端口映射功能，动态分配端口
**预防**: 制定端口规划表，避免冲突

### 问题3: 容器网络隔离
**现象**: 容器间无法通信
**根因**: 默认bridge网络隔离
**解决**: 自定义网络或使用host网络模式
**预防**: 在Compose文件中明确指定网络配置


## 跨概念综合洞察

### 概念间的协同效应
- **[[Ansible]] + [[Docker]]**: Ansible负责批量部署，Docker负责应用隔离，两者结合实现"一次打包，处处运行"
- **[[Spring Boot]] + [[Vue]]**: 后端提供REST API，前端负责可视化，实现完整的人机交互闭环
- **[[Shell脚本]] + [[Ansible]]**: Shell处理本地任务，Ansible处理远程批量任务，分工明确

### 与单一概念理解的差异
- 在实际项目中，Docker不仅仅是运行容器，还需要考虑网络、存储、日志等多方面
- Spring Boot作为API网关，需要处理并发请求、认证授权、异常处理等生产级问题


## 复盘总结

### 架构层面的收获
1. 理解了DevOps工具链的完整链路（代码→构建→部署→监控）
2. 掌握了基础设施即代码的理念（用脚本/配置管理基础设施）
3. 学习了如何设计可演进的架构（从简单到复杂的平滑过渡）

### 待深入的方向
- [[Kubernetes]] 在生产环境的应用
- [[Istio]] 服务网格的集成
- [[Prometheus]] + [[Grafana]] 监控体系


## 相关链接
- 主题地图: [[EMRG-DevOps]]
- 项目拆解: [[LEARNING-ROADMAP-运维控制面板]]
- 错误记录: [[MISTAKE-SSH权限问题]]


---
📊 **项目完成度**: 0%
🎯 **核心收获**: 待完成
🔗 **关联练习数**: 0
📈 **涉及知识点掌握度提升**: 待统计