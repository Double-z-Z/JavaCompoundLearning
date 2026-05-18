# 流水线编排设计

本文档定义标准化部署流水线的结构和行为。

## 设计理念

**环境晋升模型 (Environment Promotion)**:
```
代码提交
    ↓
[Unit Test] ← 单元测试（业务逻辑，无外部依赖）
    ↓ 通过
[Contract Test] ← 契约测试（API接口兼容性）
    ↓ 通过
[Integration Test] ← 集成测试（组件间协作）
    ↓ 通过
[E2E Test] ← 端到端测试（完整流程验证）
    ↓ 通过
[Staging] ← 预发布环境（人工验证）
    ↓ 人工审批
[Production] ← 生产环境
```

---

## 核心概念

### Stage（阶段）
流水线的基本组成单元，按顺序执行。

**内置阶段**:
| Stage | 用途 | 门禁类型 | 超时 |
|-------|------|---------|------|
| validate | 前置校验 | automatic | 2min |
| build | 构建镜像 | automatic | 5min |
| deploy | 部署到目标 | manual_approval | 10min |
| verify | 部署后验证 | automatic | 5min |

### Step（步骤）
Stage内的原子操作。

**支持的操作类型 (Action)**:
```java
enum ActionType {
    MAVEN_TEST,              // 运行Maven单元测试
    DOCKER_LINT,             // Dockerfile语法检查
    DOCKER_BUILD,            // 构建Docker镜像
    DEPLOY_TO_ENVIRONMENT,   // 部署到目标环境
    WAIT_FOR_HEALTHY,        // 等待健康检查通过
    HTTP_REQUEST,            // 发送HTTP请求（冒烟测试）
    SEND_NOTIFICATION,       // 发送通知
    CUSTOM_SCRIPT           // 执行自定义脚本
}
```

### Gate（门禁）
Stage之间的关卡。

**类型**:
- **automatic**: 自动通过（前序Step全部成功）
- **manual_approval**: 需要人工审批

**审批配置**:
```yaml
approval_config:
  required_approvers: 1      # 需要几人审批
  timeout_minutes: 60        # 超时时间
  auto_reject_on_timeout: false  # 超时是否自动拒绝
```

---

## 标准流水线模板

### Template 1: 快速开发部署（个人模式）

适用: 本地开发，追求速度

```yaml
pipeline_id: quick-dev-deploy
name: "快速开发部署"
trigger: manual

stages:
  - stage: validate
    gate: automatic
    steps:
      - action: maven_test
        timeout: 120s
        on_failure: abort_pipeline
  
  - stage: deploy
    gate: automatic           # 无需审批
    steps:
      - action: docker_build
        timeout: 180s
      - action: deploy_to_environment
        timeout: 120s
      - action: wait_for_healthy
        timeout: 60s
  
  - stage: verify
    gate: automatic
    steps:
      - action: http_request
        config:
          url: "${SERVICE_URL}/health"
          expected_status: 200
        timeout: 30s
```

**特点**:
- ✅ 最快路径: validate → deploy → verify
- ✅ 无人工审批
- ⚠️ 无集成测试（适合开发阶段）

---

### Template 2: 标准发布流水线（企业模式）

适用: Staging/Prod环境，强调质量

```yaml
pipeline_id: standard-release
name: "标准发布流水线"
trigger: webhook  # Git push触发

stages:
  - stage: validate
    gate: automatic
    steps:
      - action: maven_test
        config:
          profile: test
          coverage_threshold: 80  # 代码覆盖率阈值
        timeout: 180s
        on_failure: abort_pipeline
      
      - action: docker_lint
        timeout: 30s
  
  - stage: build
    gate: automatic
    steps:
      - action: docker_build
        config:
          tag: "${IMAGE_NAME}:${GIT_COMMIT_SHORT}"
          push_to_registry: true
        timeout: 300s
  
  - stage: integration-test
    gate: automatic
    steps:
      - action: custom_script
        config:
          script: "./scripts/integration-test.sh"
          env:
            SERVICE_URL: "${STAGING_URL}"
        timeout: 600s
        on_failure: abort_pipeline
  
  - stage: deploy-to-staging
    gate: manual_approval
    approval:
      required_approvers: 1
      timeout: 120min
    steps:
      - action: deploy_to_environment
        config:
          environment: staging
        timeout: 180s
      - action: wait_for_healthy
        timeout: 120s
  
  - stage: e2e-verify
    gate: automatic
    steps:
      - action: http_request
        config:
          url: "${STAGING_URL}/api/health"
          expected_status: 200
        timeout: 30s
      - action: send_notification
        config:
          channels: [slack]
          message: "✅ 已部署到Staging: ${SERVICE_NAME} v${VERSION}"

staging_to_prod_promotion:
  trigger: manual  # 手动触发生产发布
  requires_staging_approval: true
  rollback_enabled: true
  rollback_strategy: previous_version
```

**特点**:
- ✅ 完整质量门禁
- ✅ 人工审批机制
- ✅ 支持回滚
- ✅ 通知集成

---

## 失败处理策略

| 场景 | 策略 | 行为 |
|------|------|------|
| Maven测试失败 | abort_pipeline | 终止整个流水线 |
| Docker构建失败 | abort_pipeline | 终止并清理镜像 |
| 健康检查超时 | retry(3次) | 重试后仍失败则终止 |
| 部署到Staging失败 | continue | 记录错误，继续后续步骤（可选） |
| E2E测试失败 | abort_pipeline | 终止，阻止进入Staging |

---

## 与Spike实验的关系

**Spike实验不走完整Pipeline**，使用简化版:

```yaml
pipeline_id: spike-deploy
name: "实验环境快速部署"

stages:
  - stage: quick-deploy
    gate: automatic
    steps:
      - action: docker_build
      - action: deploy_to_environment
      - action: wait_for_healthy
        timeout: 60s  # 更短的超时

# 实验完成后自动清理
post_actions:
  - action: collect_metrics
  - action: generate_report
  - action: teardown_environment
```

**理由**: Spike追求速度，质量保证由人工验证（查看报告）。
