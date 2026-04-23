---
created: 2026-04-22
tags: [nio, maven, testing, build-tools]
status: 🌿
mastery: 40
---

# Maven Surefire 插件

## 一句话定义
Maven Surefire 是负责执行单元测试的插件，通过 Fork 机制在独立 JVM 中运行测试，确保测试隔离和构建稳定性。

---

## 核心理解

### 1. 插件与生命周期绑定

```
Maven 生命周期
│
├─ validate
├─ compile
├─ test-compile
├─ test ←── 绑定 surefire:test
├─ package
└─ ...
```

**默认绑定**：Maven 的 super pom 中定义了 `test` 阶段绑定 `surefire:test`

### 2. Fork 机制（核心）

```
Maven 主进程（父 JVM）
│
├─ 解析 pom.xml
├─ 到达 test 阶段
├─ 调用 surefire:test
│
└─ Fork ──────────────────────────┐
    │                              │
    ▼                              │
子 JVM（Surefire Booter）          │
    │                              │
    ├─ 扫描测试类                  │
    ├─ 执行 @Before → @Test → @After
    ├─ 生成报告文件 ───────────────┤
    │   （target/surefire-reports/）
    └─ 返回退出码 ────────────────┘
        │
        ▼
    进程 1 读取返回码
```

**为什么 Fork？**
- **隔离性**：测试崩溃不影响 Maven 主进程
- **类路径控制**：独立的测试类路径
- **内存控制**：独立的堆内存设置

### 3. 关键配置参数

| 参数 | 默认值 | 作用 |
|------|--------|------|
| `forkCount` | 1 | 同时启动几个 JVM |
| `reuseForks` | true | 是否重用 JVM |
| `runOrder` | filesystem | 测试类执行顺序 |
| `parallel` | false | 是否并行执行测试方法 |

### 4. 测试隔离配置

**默认（共享 JVM）**：
```xml
<reuseForks>true</reuseForks>
<!-- 问题：静态变量在测试间共享 -->
```

**完全隔离（每个类独立 JVM）**：
```xml
<reuseForks>false</reuseForks>
<!-- 每个测试类都启动新 JVM -->
```

**顺序执行**：
```xml
<runOrder>alphabetical</runOrder>
<!-- 按字母顺序执行，避免时序依赖 -->
```

### 5. 性能测试分离模式

```xml
<!-- 默认排除性能测试 -->
<excludes>
    <exclude>**/performance/*Test.java</exclude>
</excludes>

<!-- Profile 单独运行 -->
<profile>
    <id>performance</id>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                    <includes>
                        <include>**/performance/*Test.java</include>
                    </includes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</profile>
```

使用：
```bash
mvn test              # 只运行功能测试
mvn test -Pperformance # 只运行性能测试
```

---

## 关键关联

### [[Java ProcessBuilder]]
- Surefire 使用 `ProcessBuilder` 创建子进程
- 通过命令行启动新 JVM 实例

### [[JVM 内存模型]]
- 每个 Fork 的 JVM 有独立的堆内存
- 静态变量在不同 JVM 间不共享

### [[Maven 生命周期]]
- `test` 阶段默认绑定 `surefire:test`
- 可以通过 `skipTests` 属性跳过

### [[测试设计]]
- 测试应该独立、可重复
- 避免测试间的状态依赖

---

## 我的误区与疑问

### ❌ 误区 1：以为 `mvn test` 在主 JVM 运行测试
实际上 Surefire 默认会 Fork 新 JVM，即使 `reuseForks=true` 也是在新 JVM 中运行。

### ❌ 误区 2：忽略静态变量的影响
```java
// 错误：静态变量在 reuseForks=true 时共享
private static int counter = 0;

// 正确：每个测试独立
private int counter = 0;
```

### ❓ 疑问：为什么有时需要 `reuseForks=false`？
当测试修改了：
- 系统属性
- 静态变量
- 线程池
- 文件句柄

时需要完全隔离。

---

## 代码与实践

### 查看实际执行的命令
```bash
mvn test -X  # 开启调试日志，可以看到 fork 的命令行
```

输出示例：
```
Forking command line: java -cp target/test-classes:target/classes:...
    org.apache.maven.surefire.booter.ForkedBooter
    com.example.Server.BroadcastTest
```

### 调试 Fork 的 JVM
```xml
<configuration>
    <forkMode>once</forkMode>
    <argLine>-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005</argLine>
</configuration>
```

---

## 深入思考

💡 **Fork 的代价**：
- 每个 JVM 启动需要 ~1-2 秒
- 内存占用增加
- 适合功能测试，不适合单元测试

💡 **平衡策略**：
- 单元测试：`reuseForks=true`（快速）
- 集成测试：`reuseForks=false`（隔离）

---

## 来源
- 项目：[[nio-chatroom]]
- 对话：[[2026-04-22-testing-best-practices-dialogue]]

---

## 🤖 AI评价

### 掌握度评估
- 当前等级：🌿理解
- 更新记录：
  - 2026-04-22: mastery=40 (理解 Fork 机制和配置参数)

### 建议下一步
1. 深入研究 ProcessBuilder API
2. 学习 Maven 插件开发

---

```dataview
TABLE status, mastery, length(file.inlinks) as "入链", length(file.outlinks) as "出链"
FROM #maven OR #testing
SORT mastery DESC
```
