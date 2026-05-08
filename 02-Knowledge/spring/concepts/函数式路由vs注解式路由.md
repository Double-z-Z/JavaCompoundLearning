---
type: atomic-note
id: CONCEPT-functional-vs-annotation-routing
created: 2026-05-07
tags: [webflux, routing, functional-programming, annotation, router-function]
related_emrg: [EMRG-Spring性能优化]
related_goal: [GOAL-Java核心深化]
mastery: 45
---

# 函数式路由 vs 注解式路由

## 一句话定义

**注解式路由**：基于地址的代码分配，用 `@GetMapping` 等注解在 Controller 方法上声明路由（编译时确定）
**函数式路由**：基于代码的地址分配，用 `RouterFunction` 对象组合路由定义（运行时可动态操作）

## 核心理解

### 本质差异：不是位置问题，而是组合能力

```java
// 注解式（Controller）— "基于地址的代码分配"
@RestController
public class StockController {
    
    @GetMapping("/stock/{sku}")           // 地址在这里定义
    public Mono<Stock> getStock(@PathVariable String sku) { ... }
    
    @PostMapping("/stock/{sku}/decrement") // 地址在这里定义
    public Mono<Result> decrement(...) { ... }
}

// 函数式（RouterFunction）— "基于代码的地址分配"
@Bean
RouterFunction<ServerResponse> routes(StockHandler handler) {
    return route()
        .GET("/stock/{sku}", handler::getStock)      // 代码在这里引用
        .POST("/stock/{sku}/decrement", handler::decrement)
        .build();
}
```

**关键洞察**：
- 注解式：路由与代码**强耦合**，编译时固定，无法动态修改
- 函数式：路由是**一等公民**，可像数据一样操作（组合、条件、过滤）

### 编程范式差异

| 维度 | 注解式 | 函数式 |
|------|--------|--------|
| **思维模式** | 面向对象（类组织） | 函数式（管道组合） |
| **路由定义** | 声明式（元数据/注解） | 编程式（代码即配置） |
| **扩展方式** | 继承/注解/AOP | 组合/高阶函数 |
| **可测试性** | 需要 Mock 容器 | 纯函数，易测试 |
| **动态性** | 低（编译时固定） | 高（运行时可组合） |
| **认知负担** | 低（符合直觉） | 中等（需要理解组合） |

### 类比理解

```
注解式 = 菜单（预先写好，你只能点菜）
├─ 结构清晰，一目了然
├─ 但不能临时改菜单
└─ 适合固定场景

函数式 = 自助餐（自己搭配）
├─ 灵活组合
├─ 可以随时增减菜品
└─ 适合需要动态变化的场景
```

## 函数式的核心优势：组合能力

### 1. 路由组合

```java
// 组合多个模块的路由
RouterFunction<ServerResponse> combined = 
    stockRoutes(handler)          // 库存路由
    .and(orderRoutes(handler))     // 订单路由
    .and(userRoutes(handler));     // 用户路由
```

### 2. 条件启用

```java
// 根据环境或配置动态决定是否启用某组路由
RouterFunction<ServerResponse> apiRoutes =
    env.isProd() 
        ? stockRoutes(handler)     // 生产环境才启用
        : route();                  // 测试环境返回空路由
```

### 3. 插件化架构

```java
// 动态注册插件的路由（低代码平台、CMS）
List<Module> modules = moduleRegistry.getAll();
RouterFunction<ServerResponse> appRoutes = route();
for (Module m : modules) {
    appRoutes = appRoutes.and(m.routes());  // 插件自动注册路由
}
```

### 4. 横切关注点统一处理

```java
// 一行代码给所有路由添加认证和日志
RouterFunction<ServerResponse> securedRoutes =
    stockRoutes(handler)
        .filter(authFilter)         // 统一加认证
        .filter(loggingFilter);     // 统一加日志
```

## 实际场景对比

### 场景 1：API 版本管理

```java
// 注解式：需要多个 Controller 或条件判断
@RestController
@RequestMapping("/api/v1")
public class StockControllerV1 { ... }

@RestController  
@RequestMapping("/api/v2")
public class StockControllerV2 { ... }

// 函数式：优雅地组合版本路由
@Bean
RouterFunction<ServerResponse> apiRoutes(Handler handler) {
    return route()
        .nest(path("/api/v1"), () -> v1Routes(handler))
        .nest(path("/api/v2"), () -> v2Routes(handler))
        .build();
}
```

### 场景 2：权限控制

```java
// 注解式：分散在各处
@PreAuthorize("hasRole('ADMIN')")
@GetMapping("/admin/stock") 
public Mono<?> adminStock() { ... }

// 函数式：集中声明
RouterFunction<ServerResponse> adminRoutes = 
    route()
        .path("/admin")
        .filter(requireRole("ADMIN"))   // 一行搞定所有 admin 路由
        .GET("/stock", handler::adminStock)
        .POST("/stock/init", handler::initStock)
        .build();
```

### 场景 3：多租户 SaaS

```java
// 不同租户看到不同的 API
@Bean
RouterFunction<ServerResponse> tenantRoutes(TenantConfig config) {
    return route()
        .nest(path("/api/custom"), () => 
            config.hasFeature("custom_fields")
                ? customFieldRoutes()
                : route()  // 无此功能，返回 404
        )
        .build();
}
```

## 最佳实践：混合模式（推荐）

Spring 官方推荐：**核心业务用注解式 + 动态逻辑用函数式包装**

```java
@Configuration
class DynamicRouter {
    
    @Bean
    RouterFunction<ServerResponse> dynamicRoutes(
            StockController controller,  // 注入传统 Controller
            FeatureFlags flags) {
        
        return route()
            // 大部分请求走 Controller（固定路由，开发效率高）
            .nest(path("/api"), 
                addController(controller))  // Spring 提供的工具方法
            )
            // 特殊路径走函数式（动态路由，灵活）
            .path("/api/v2")
                .filter(req -> flags.isEnabled("v2_api"))
                .GET("/stock/{sku}", this::handleV2)
            .build();
    }
}
```

**好处**：
- ✅ 两全其美：清晰分离 + 灵活组合
- ✅ 团队协作友好：大部分开发者只需写注解式
- ✅ 架构师可以集中管理动态路由逻辑

## 关键关联

- [[WebFlux响应式编程]]: 函数式路由是 WebFlax 的两种风格之一
- [[Flux核心概念]]: 函数式路由通常返回 Mono/Flux 类型
- [[动态路由场景分类]]: 决策何时使用哪种路由风格的框架
- [[MVC架构模式]]: 注解式路由是 MVC 的传统实现方式

**为什么需要这些关联**：
- WebFlux：理解函数式路由在 WebFlux 中的位置
- Flux：函数式路由返回的数据类型基础
- 动态路由场景分类：实际决策框架
- MVC：理解注解式路由的历史背景

## 常见误区

| 误区 | 正确理解 |
|------|----------|
| "函数式路由一定更好" | 各有适用场景，需按路由变化频率选择 |
| "必须二选一" | 可以混合使用，Spring 官方推荐混合模式 |
| "函数式路由更难维护" | 对于动态场景反而更易维护（集中管理） |
| "注解式无法实现动态路由" | 可以通过 AOP/Filter 实现，但不如函数式直观 |

## 掌握度评估

- 当前等级：🌿 理解
- 已理解：
  - ✅ 两种路由风格的核心差异（组合能力 vs 开发效率）
  - ✅ 函数式路由的组合、条件、过滤等优势
  - ✅ 实际应用场景（API 版本、权限、多租户）
  - ✅ 混合模式的最佳实践
- 下一步：在项目中实践函数式路由，体验其灵活性
