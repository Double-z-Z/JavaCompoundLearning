# 数据结构定义

本文档定义所有核心领域对象的完整数据结构（YAML Schema格式）。

## 1. Environment 完整结构

```yaml
environment:
  # === 身份与元数据 ===
  id: string                    # "env-20260517-nacos-dev"
  name: string                  # "Nacos开发环境"
  created_at: datetime
  
  status: enum                  # CREATING | RUNNING | STOPPED | DESTROYED | FAILED
  
  access_endpoints:             # 创建后系统填充
    console_url: url
    api_url: url
  
  # === 规格说明 (值对象) ===
  spec:
    type: enum                  # DEV | TEST | STAGING | PROD | EXPERIMENT
    
    target_nodes:[]             # 目标节点列表
      - node_id: string
        ip: string
        role: enum              # primary | secondary
    
    resource_quota:             # 资源配额
      cpu:
        request: string         # "500m"
        limit: string           # "2000m"
      memory:
        request: string         # "512Mi"
        limit: string           # "2Gi"
    
    lifecycle_policy:           # 生命周期策略
      auto_destroy: bool
      max_lifetime: duration    # "24h"
      idle_timeout: duration    # "2h"
  
  # === 网络配置 (值对象) ===
  network:
    mode: enum                  # bridge | host | overlay
    network_name: string
  
  # === 服务实例列表 (实体) ===
  services: []
    - instance_id: string
      service_template: string  # 引用模板名
      
      status: enum              # PENDING | DEPLOYING | RUNNING | STOPPED | FAILED
      
      config:
        image: string
        ports: []               # container_port, host_port, protocol
        environment_variables: {}  # KEY=VALUE
        volumes: []             # source, destination, mode
        depends_on: []          # 依赖的服务名
        
        health_check:           # 健康检查
          endpoint: string
          initial_delay_seconds: int
          period_seconds: int
          timeout_seconds: int
      
      runtime:                  # 运行时信息（系统填充）
        container_id: string
        started_at: datetime
        resource_usage:
          cpu_percent: float
          memory_mb: int
        last_health_check:
          timestamp: datetime
          status: enum          # healthy | unhealthy
          response_time_ms: int
```

## 2. Experiment 完整结构

```yaml
experiment:
  # === 身份与元数据 ===
  id: string                    # "exp-20260517-rabbitmq-perf"
  title: string                 # "RabbitMQ性能基准测试"
  created_by: string
  created_at: datetime
  
  status: enum                  # PLANNING | RUNNING | COMPLETED | ARCHIVED | CANCELLED
  
  # === 假设 (值对象) ===
  hypothesis:
    statement: string           # "RabbitMQ可支持5万QPS..."
    background: string          # 背景
    success_criteria: []        # 成功标准
      - metric: string
        operator: enum          # >= | <= | == | !=
        value: number
  
  # === 实验环境 ===
  environment:
    # 继承Environment结构，但type固定为EXPERIMENT
    # 自动设置auto_destroy=true, max_lifetime="2h"
    spec:
      type: EXPERIMENT
      target_nodes: [...]
      resource_quota: {...}
      lifecycle_policy:
        auto_destroy: true
        max_lifetime: "2h"
    
    services: [...]             # 同Environment.services
  
  # === 证据数据 (值对象) ===
  evidence:
    collected_at: datetime
    
    metrics: []                 # 测量指标
      - name: string            # throughput_qps
        value: number
        unit: string
        measurement_tool: string
    
    artifacts: []               # 产物文件
      - type: enum              # log | graph | raw_data
        path: string
  
  # === 结论 (值对象) ===
  conclusion:
    decision: enum              # ACCEPT | REJECT | NEED_MORE_DATA | INCONCLUSIVE
    summary: text               # 结论摘要
    lessons_learned: []         # 经验教训
    next_steps: []              # 后续行动
  
  # === 归档信息 ===
  archived_at: datetime | null
  archive_path: string          # "docs/spikes/xxx.md"
```

## 3. Pipeline 结构

```yaml
pipeline:
  id: string
  name: string                  # "Nacos标准部署流水线"
  
  trigger:
    type: enum                  # manual | webhook | scheduled
  
  stages: []                    # 阶段列表（有序）
    - stage_id: string
      name: string              # "前置校验"
      order: int                # 执行顺序
      gate_type: enum           # automatic | manual_approval
      
      approval_config:          # 仅manual_approval时有效
        required_approvers: int
        timeout_minutes: int
        auto_reject_on_timeout: bool
      
      steps: []                 # 步骤列表
        - step_id: string
          name: string          # "Maven单元测试"
          action: enum          # maven_test | docker_build | deploy | health_check | ...
          
          config: {}            # action-specific配置
          
          timeout_seconds: int
          
          success_criteria: {}  # 成功条件
            exit_code: int
            test_pass_rate: int # %
            
          on_failure: enum      # abort_pipeline | continue | retry
```

## 4. 通用枚举与常量

### 状态机约束

**Environment状态转移**:
```
CREATING ──→ RUNNING ──→ STOPPED ──→ DESTROYED
   │           │           │
   ↓           ↓           ↓
 FAILED      FAILED      FAILED
```

**Experiment状态转移**:
```
PLANNING ──→ RUNNING ──→ COMPLETED ──→ ARCHIVED
   │           │           │
   ↓           ↓           ↓
 CANCELLED  CANCELLED   CANCELLED
```

### 默认值参考

| 配置项 | 个人模式默认值 | 企业模式默认值 |
|--------|--------------|---------------|
| max_lifetime | 24h | 永久（手动销毁） |
| idle_timeout | 2h | 不启用 |
| auto_destroy | false | false |
| health_check.interval | 10s | 30s |
| gate_type | automatic | manual_approval |
