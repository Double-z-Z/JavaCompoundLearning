# E2E Test 模块架构设计文档

> 版本: v1.0
> 状态: Draft
> 作者: 架构组
> 更新日期: 2026-06-07

---

## 1. 背景与目标

### 1.1 背景
随着系统功能迭代加速，单一单元测试和接口级集成测试已无法满足以下诉求：
- 跨服务、跨模块的完整业务流程验证
- 用户视角下真实场景的回归保障
- 上线前最后一公里的"信任锚点"

### 1.2 目标
构建一个**独立、可扩展、可观测**的 E2E（End-to-End）测试模块，提供：
1. 端到端业务场景的自动化执行能力
2. 统一的测试用例管理与复用机制
3. 与 CI/CD 流水线无缝集成
4. 失败用例的快速定位与重试

### 1.3 非目标（Out of Scope）
- 不替代单元测试 / 集成测试
- 不参与生产环境流量回放
- 不承担性能压测职责（由独立压测模块承担）

---

## 2. 架构总览

### 2.1 分层视图

```
┌─────────────────────────────────────────────────────────────┐
│  CLI / CI Runner        Web Report         调度中心          │  ← 接入层
├─────────────────────────────────────────────────────────────┤
│  Test Orchestrator  (场景编排 / 用例调度 / 依赖管理)         │  ← 编排层
├─────────────────────────────────────────────────────────────┤
│  Test Case Library  (用例库 / Fixtures / Page Objects)      │  ← 用例层
├─────────────────────────────────────────────────────────────┤
│  Driver Adapter  (HTTP / gRPC / DB / Browser / MQ)          │  ← 驱动层
├─────────────────────────────────────────────────────────────┤
│  Test Runtime  (Env Manager / Data Prep / Hooks / Reports)  │  ← 运行时
└─────────────────────────────────────────────────────────────┘
```

### 2.2 关键技术选型

| 维度 | 选型 | 理由 |
|------|------|------|
| 用例语言 | TypeScript / Java | 与项目主语言保持一致 |
| 浏览器驱动 | Playwright | 跨浏览器、多语言、稳定性优于 Selenium |
| HTTP 客户端 | axios / OkHttp | 生态成熟、支持拦截器 |
| 断言库 | Jest Expect / AssertJ | 链式断言、失败信息可读 |
| 报告 | Allure + 自定义 | 趋势分析、附件丰富 |
| 调度 | 自研 Orchestrator + npx/junit5 | 轻依赖、易嵌入 |

---

## 3. 模块划分

### 3.1 模块清单

| 模块 | 路径 | 职责 | 依赖 |
|------|------|------|------|
| `e2e-core` | `e2e/core/` | 运行时核心：上下文、钩子、配置加载 | 无 |
| `e2e-driver` | `e2e/driver/` | 驱动适配：HTTP/gRPC/DB/Browser 封装 | e2e-core |
| `e2e-fixtures` | `e2e/fixtures/` | 测试数据准备与清理 | e2e-core, e2e-driver |
| `e2e-cases` | `e2e/cases/` | 业务用例集合（按域划分） | e2e-core, e2e-driver, e2e-fixtures |
| `e2e-orchestrator` | `e2e/orchestrator/` | 场景编排、依赖解析、并发控制 | e2e-core, e2e-cases |
| `e2e-report` | `e2e/report/` | 报告生成、趋势归档、通知 | e2e-core, e2e-orchestrator |
| `e2e-cli` | `e2e/cli.ts` | 入口：参数解析、env 注入 | all |

### 3.2 模块依赖图

```
e2e-cli ─┬─> e2e-orchestrator ─> e2e-cases
         │                  │
         │                  └─> e2e-fixtures ─> e2e-driver
         │
         ├─> e2e-report
         └─> e2e-core  (横向被所有模块依赖)
```

依赖单向，无环。`e2e-core` 是唯一的"叶子基础模块"。

---

## 4. 接口定义

### 4.1 核心接口（Type / Trait）

```ts
// ── 1. 用例契约 ─────────────────────────────────
interface TestCase {
  id: string;                    // 唯一标识: "order.create.happy"
  domain: string;                // 业务域: "order"
  priority: 'P0' | 'P1' | 'P2' | 'P3';
  tags: string[];                // ["smoke", "regression"]
  dependsOn?: string[];          // 依赖的 case id
  setup(ctx: TestContext): Promise<void>;
  execute(ctx: TestContext): Promise<void>;
  teardown(ctx: TestContext): Promise<void>;
}

// ── 2. 上下文 ────────────────────────────────────
interface TestContext {
  env: 'dev' | 'staging' | 'prod-mirror';
  traceId: string;
  vars: Map<string, unknown>;    // 用例间数据传递
  http: HttpClient;              // 预配置 HTTP 客户端
  db: DbClient;                  // 数据库直连
  browser?: BrowserContext;      // 可选浏览器
  log: Logger;
  attach(name: string, data: Buffer | string): Promise<void>;
}

// ── 3. 驱动适配器接口 ────────────────────────────
interface HttpClient {
  request<T>(req: HttpRequest): Promise<HttpResponse<T>>;
  intercept(fn: InterceptorFn): void;   // mock/record
}

interface DbClient {
  query<T>(sql: string, params?: any[]): Promise<T[]>;
  exec(sql: string, params?: any[]): Promise<void>;
  truncate(tables: string[]): Promise<void>;
}

// ── 4. 钩子（生命周期）───────────────────────────
type Hook =
  | { phase: 'before-all';     fn: () => Promise<void> }
  | { phase: 'before-each';    fn: (ctx: TestContext) => Promise<void> }
  | { phase: 'after-each';     fn: (ctx: TestContext) => Promise<void> }
  | { phase: 'after-all';      fn: (results: CaseResult[]) => Promise<void> };

// ── 5. 执行结果 ──────────────────────────────────
interface CaseResult {
  caseId: string;
  status: 'passed' | 'failed' | 'skipped' | 'flaky';
  durationMs: number;
  retryCount: number;
  error?: { type: string; message: string; stack?: string };
  attachments: { name: string; mime: string; path: string }[];
}
```

### 4.2 编排器对外 API

```ts
class E2EOrchestrator {
  // 加载用例
  loadCases(paths: string[]): Promise<TestCase[]>;

  // 解析依赖并生成执行计划
  buildPlan(filter: CaseFilter): Promise<ExecutionPlan>;

  // 执行
  run(plan: ExecutionPlan, opts: RunOptions): Promise<RunSummary>;

  // 报告
  report(summary: RunSummary, format: 'allure' | 'html' | 'junit'): Promise<string>;
}

interface RunOptions {
  concurrency: number;          // 默认 1；P0 用例建议 1
  retry: { max: number; onlyFailed: boolean };
  timeoutMs: number;
  failFast: boolean;
  dryRun: boolean;               // 仅打印计划
}
```

### 4.3 CLI 命令规范

```bash
e2e run  --tag smoke --env staging --concurrency 2
e2e run  --case order.create.happy --retry 3
e2e list --domain order
e2e plan --tag regression --dry-run
e2e report --input ./reports/latest --format allure
```

---

## 5. 数据流

### 5.1 单用例执行流

```
       ┌────────────┐
start→ │ setup()    │ ── 创建数据 / 登录态
       └─────┬──────┘
             ▼
       ┌────────────┐
       │ execute()  │ ── HTTP/DB/Browser 调用
       └─────┬──────┘
             ▼
       ┌────────────┐
       │ assert     │ ── 业务断言 + 副作用校验
       └─────┬──────┘
             ▼
       ┌────────────┐
       │ teardown() │ ── 数据清理 / 资源释放
       └─────┬──────┘
             ▼
       ┌────────────┐
       │ report     │ ── 收集日志/截图/响应包
       └────────────┘
```

### 5.2 编排层数据流

```
CLI 参数
   │
   ▼
Config Loader ──> env 注入、密钥解密
   │
   ▼
Case Loader ──> 扫描 cases/** 目录，校验契约
   │
   ▼
Dependency Resolver ──> 拓扑排序，检测环依赖
   │
   ▼
Plan Builder ──> 按 priority / tag 分组
   │
   ▼
Worker Pool ──> 并发执行（受限）
   │
   ▼
Result Aggregator ──> 重试、合并、归档
   │
   ▼
Reporter ──> Allure / HTML / JUnit
```

### 5.3 关键数据约定

| 数据 | 流向 | 生命周期 | 隔离要求 |
|------|------|----------|----------|
| `traceId` | 注入 → 透传 → 报告 | 单次 run | 全局唯一 |
| `vars` | 用例间共享 | 单场景 | 不跨场景 |
| `fixtures` | 准备 → 使用 → 清理 | 单用例 | 必须可重入 |
| `auth token` | 登录态 → 后续调用 | 1h 或刷新前 | 加密存储 |

---

## 6. 异常处理策略

### 6.1 异常分类

| 类别 | 示例 | 策略 |
|------|------|------|
| **环境异常** | 网络不通、DB 不可达 | 立即失败，不重试，报警 |
| **配置异常** | 缺少 env、密钥错误 | 启动前 fail-fast |
| **用例异常** | 断言失败、timeout | 按策略重试 N 次 |
| **间歇性异常** | 第三方 API 抖动 | 标记 `flaky` + 重试 |
| **数据异常** | 唯一键冲突、数据脏 | 触发数据清理后重试 |
| **未知异常** | 未捕获 throw | 标记 `failed`，附 stack |

### 6.2 重试机制

```
case 失败
   │
   ├─ 命中 retryable? (status ∈ {5xx, timeout, flaky})
   │      │
   │      ├─ yes → 等待 backoff(2^n) → 重试 (最多 N 次)
   │      │
   │      └─ no  → 标记 failed，结束
   │
   └─ 达到 max retry → 标记 failed / flaky
```

规则：
- **断言失败**默认**不重试**（避免假阳性掩盖真问题）
- **网络/HTTP 5xx** 最多重试 2 次
- 每次重试前必须重新执行 `setup`（避免脏状态）
- 重试成本计入 `durationMs`

### 6.3 超时与熔断

- 单用例 `timeoutMs` 默认 60s，可覆盖
- 编排器总时长上限 `runTimeoutMs` 默认 30min
- 连续失败比例 > 50% 触发**快速终止**（failFast）
- 浏览器驱动使用 `context.setDefaultTimeout`

### 6.4 失败隔离

- 每个用例独立 `traceId`，日志互不污染
- 浏览器用例使用**独立 context**，cookie/storage 不串扰
- DB 写入使用**唯一前缀**（如 `e2e_<runId>_*`），teardown 强清理
- 任何用例 panic 不影响其他用例与进程退出码

### 6.5 错误信息规范

失败信息必须包含：
```
[caseId] <业务一句话>
Status: <HTTP/code>
Expected: <期望值>
Actual:   <实际值>
Trace:    <traceId>
Attach:   <截图/响应包路径>
```

---

## 7. 配置与环境

### 7.1 配置文件

```
e2e.config.ts          # 入口配置（dev/staging/prod-mirror）
e2e.config.local.ts    # 个人本地覆盖（不入库）
```

### 7.2 环境变量

| 变量 | 必填 | 说明 |
|------|------|------|
| `E2E_ENV` | 是 | dev / staging / prod-mirror |
| `E2E_BASE_URL` | 是 | 被测系统入口 |
| `E2E_DB_URL` | 是 | 测试库直连 |
| `E2E_AUTH_TOKEN` | 否 | 预置 token |
| `E2E_HEADLESS` | 否 | 浏览器是否无头 |
| `E2E_PARALLEL` | 否 | 并发数 |

### 7.3 环境隔离

- **禁止**直连生产库（CI 强制校验 `E2E_ENV`）
- **禁止**使用生产密钥（CI 注入 staging 专属凭据）
- 测试账号需打标 `isE2E=true`，业务侧可识别并限流

---

## 8. 可观测性

### 8.1 日志
- 结构化 JSON 日志，关键字段：`traceId / caseId / phase / durationMs`
- 每个用例独立 `logs/<caseId>.log`
- 控制台实时输出，CI 阶段落盘归档

### 8.2 指标
- 用例总数 / 通过 / 失败 / 跳过 / flaky
- P50 / P95 / P99 duration
- 重试率（衡量系统稳定性）
- 环境异常次数（关注度 > 用例异常）

### 8.3 报告
- Allure：趋势、分类、附件
- HTML：单次详情页，含 trace、截图、网络包
- 失败用例自动 `@` 相关 owner

---

## 9. CI/CD 集成

```
PR 提交 ─> 冒烟 (smoke tag, 5min)
                │
                ▼
合并 main ─> 回归 (regression tag, 30min)
                │
                ▼
发布前 ─> 全量 (P0+P1, 1h)
                │
                ▼
定时 ─> 健康巡检 (每日 02:00, 关键路径)
```

CI 关键点：
- 失败阻断 PR 合并（P0/P1）
- 报告产物上传 artifact
- flaky 用例**不阻断**，转交人工 review

---

## 10. 安全与合规

- 测试数据**禁止**含真实 PII
- 涉及支付的用例使用沙箱网关
- 截图、响应包报告前**自动脱敏**（手机号、身份证、token）
- 凭据通过 CI Secret 注入，不入代码库

---

## 11. 演进路线

| 阶段 | 时间 | 目标 |
|------|------|------|
| v1.0 | M1 | 单服务核心路径用例 + 报告 |
| v1.5 | M2 | 跨服务编排、依赖解析 |
| v2.0 | M3 | 流量回放（生产脱敏流量） |
| v2.5 | M4 | AI 辅助用例生成与失败归因 |

---

## 12. 风险与对策

| 风险 | 影响 | 对策 |
|------|------|------|
| 用例不稳定（flaky） | 降低信任度 | 强制重试 + 标记 + 周清理 |
| 测试环境不可用 | 阻塞 CI | 环境健康检查 + 备用环境 |
| 用例膨胀执行慢 | CI 变慢 | 分级执行 + 用例归档机制 |
| 数据脏导致误判 | 假阳性/阴性 | 强 setup/teardown + 幂等性 |
| 与生产耦合 | 误操作 | 环境隔离 + 权限最小化 |

---

## 13. 附录

### 13.1 目录结构

```
e2e/
├── core/                    # 上下文、钩子、配置
├── driver/                  # HTTP/gRPC/DB/Browser
├── fixtures/                # 数据准备
├── cases/                   # 用例（按域划分）
│   ├── order/
│   ├── payment/
│   └── user/
├── orchestrator/            # 编排
├── report/                  # 报告
├── cli.ts                   # CLI 入口
├── e2e.config.ts
└── reports/                 # 输出（git ignore）
```

### 13.2 用例示例

```ts
// e2e/cases/order/create.happy.ts
export default {
  id: 'order.create.happy',
  domain: 'order',
  priority: 'P0',
  tags: ['smoke', 'regression'],
  dependsOn: ['user.login.successful'],
  async setup(ctx) {
    await ctx.db.exec('INSERT INTO cart ...', [fixture]);
  },
  async execute(ctx) {
    const res = await ctx.http.request({
      method: 'POST',
      url: '/api/orders',
      body: { skuId: 'X-1001', qty: 2 },
    });
    expect(res.status).toBe(201);
    expect(res.body.orderId).toMatch(/^ORD-/);
    ctx.vars.set('orderId', res.body.orderId);
  },
  async teardown(ctx) {
    await ctx.db.exec('DELETE FROM orders WHERE id = ?', [ctx.vars.get('orderId')]);
  },
};
```

### 13.3 评审清单（DoR / DoD）

**DoR（开工前）**
- [ ] 业务路径已画流程图
- [ ] 测试数据来源明确
- [ ] 环境依赖列表确认
- [ ] 失败兜底策略达成共识

**DoD（完成时）**
- [ ] CI 中跑通 ≥ 3 次连续绿
- [ ] flaky 率 < 5%
- [ ] 报告可读、附件可下载
- [ ] README 描述何时运行

---

> **签字栏**
>
> | 角色 | 姓名 | 日期 |
> |------|------|------|
> | 架构 |  |  |
> | 测试 Owner |  |  |
> | 业务 Owner |  |  |
