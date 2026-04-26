---
name: java-refactor
description: |
  Java 代码重构 Skill。分两条路径：
  1. 批量重构（升级/迁移/统一替换）→ OpenRewrite
  2. 局部重构（提取方法/重命名/消除重复）→ 直接编辑

  目标：快速执行、快速验证、最小上下文占用。
tools: [shell, Read, Edit, Glob]
---

# Java 重构

## 触发
用户提到：重构、升级、迁移、清理、优化结构、提取方法、重命名、替换 API。

## 0. 前置检查（10 秒内完成）
```bash
git status --short
mvn -v 2>/dev/null || gradle -v 2>/dev/null
```
- 工作区未提交变更 > 20 个文件：提醒用户先 commit，否则继续。
- 无构建工具：终止，告知不支持。

## 1. 路径判断
- 涉及多文件/跨模块/版本升级/框架迁移 → **路径 A：OpenRewrite**
- 单文件内结构优化（方法过长、命名不清、重复代码） → **路径 B：直接编辑**

---

## 路径 A：OpenRewrite（批量）

### 1.1 一键执行（含 dry-run）
```bash
# Maven — 先 dry-run 看变更范围，再执行
mvn -U org.openrewrite.maven:rewrite-maven-plugin:dryRun   -Drewrite.activeRecipes=<RECIPE>   -Drewrite.exportDatatables=true

# 若变更文件 > 0 且用户未喊停，直接执行
mvn -U org.openrewrite.maven:rewrite-maven-plugin:runNoFork   -Drewrite.activeRecipes=<RECIPE>
```

```bash
# Gradle
gradle rewriteDryRun && gradle rewriteRun
```

### 1.2 常用 Recipe
| 目标 | Recipe |
|---|---|
| Java 11→17 | `org.openrewrite.java.migrate.UpgradeToJava17` |
| Java 17→21 | `org.openrewrite.java.migrate.UpgradeToJava21` |
| Spring Boot 2→3 | `org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_2` |
| JUnit 4→5 | `org.openrewrite.java.testing.junit5.JUnit4to5Migration` |
| Jakarta EE | `org.openrewrite.java.migrate.jakarta.JakartaEE10` |
| 静态分析清理 | `org.openrewrite.staticanalysis.CommonStaticAnalysis` |

### 1.3 若未配置插件
直接告知用户粘贴以下到 pom.xml `<plugins>`，**不自动修改**：
```xml
<plugin>
  <groupId>org.openrewrite.maven</groupId>
  <artifactId>rewrite-maven-plugin</artifactId>
  <version>6.0.0</version>
</plugin>
```

---

## 路径 B：直接编辑（局部）

### 2.1 快速定位
```
Read 目标文件 → 识别坏味道（方法>50行/重复块/魔法数/命名不清）
```

### 2.2 执行
- 一次只做一个重构动作
- 用 Edit 精确修改，保持原有缩进/换行/注释
- 改完后立即进入验证

---

## 3. 验证（必须）

```bash
# Maven
mvn clean test -DfailIfNoTests=false

# Gradle
gradle clean test
```

- **通过**：报告变更文件数 + 关键修改点，结束。
- **失败**：
  1. 分析失败是否与重构相关
  2. 相关则尝试修复（最多 2 轮）
  3. 仍失败则回滚：`git checkout -- . && git clean -fd`
  4. 报告失败原因，终止

无测试的项目至少编译：
```bash
mvn clean compile || gradle clean compileJava
```

---

## 4. 红线
1. **禁止正则批量替换** Java 代码（用 OpenRewrite 或 AST 感知工具）。
2. **禁止在未验证前声称完成**。
3. **禁止一次混多个重构主题**（如升级 Spring Boot + 提取方法分两次做）。
4. **dry-run 变更文件为 0 时**：立即告知用户无匹配目标，终止，不浪费时间。
