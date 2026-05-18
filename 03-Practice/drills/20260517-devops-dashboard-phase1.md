---
created: 2026-05-17
type: drill
related_project: [[PROJECT-运维控制面板]]
related_goal: [GOAL-Linux系统管理, GOAL-容器编排]
mastery_change: +10
tags: [shell, docker, ssh]
---

# DevOps控制面板 - Phase 1 练习记录

## 阶段目标
实现SSH免密登录 + **Makefile声明式任务编排** + Docker化中间件

### 生产级思维
- **标准化**: 用Makefile替代Shell脚本，声明式定义任务依赖
- **可复用**: 统一入口 `make deploy-xxx` / `make status` / `make logs`
- **可维护**: 任务依赖关系清晰，避免重复代码

---

## 练习1：SSH免密登录配置

### 任务描述
在开发机(10.0.0.142)上配置SSH免密登录到所有目标VM节点。

### 步骤记录

#### 1.1 生成SSH密钥对
```bash
# 在开发机上执行
ssh-keygen -t ed25519 -C "devops@dashboard" -f ~/.ssh/dashboard_key -N ""
```

#### 1.2 复制公钥到目标节点
```bash
# 手动方式（首次需要密码）
ssh-copy-id -i ~/.ssh/dashboard_key.pub root@10.0.0.102
ssh-copy-id -i ~/.ssh/dashboard_key.pub root@10.0.0.103
# ... 所有目标节点
```

#### 1.3 配置SSH config文件
```bash
cat > ~/.ssh/config << EOF
Host redis-1
  HostName 10.0.0.102
  User root
  IdentityFile ~/.ssh/dashboard_key
  StrictHostKeyChecking no

Host redis-2
  HostName 10.0.0.103
  User root
  IdentityFile ~/.ssh/dashboard_key
  StrictHostKeyChecking no
EOF
```

#### 1.4 验证免密登录
```bash
ssh redis-1 "echo 'Success!'"
```

### 遇到的问题
- **问题**: SSH仍然要求输入密码
- **原因**: 目标节点`~/.ssh/authorized_keys`权限不对
- **解决**: 
  ```bash
  ssh redis-1 "chmod 700 ~/.ssh && chmod 600 ~/.ssh/authorized_keys"
  ```

---

## 练习2：编写Makefile任务编排（生产级方案）

### 任务描述
用Makefile替代Shell脚本，实现声明式任务编排。

### Makefile内容
```makefile
# DevOps Dashboard - Makefile
# 声明式任务编排，替代Shell脚本

# 变量定义
TARGET ?= redis-1
SERVICE ?= rabbitmq

# 颜色输出
GREEN := \033[0;32m
YELLOW := \033[1;33m
NC := \033[0m

# 帮助信息
.PHONY: help
help: ## 显示帮助信息
	@echo "DevOps Dashboard - 任务列表:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  $(GREEN)%-20s$(NC) %s\n", $$1, $$2}'

# SSH连接测试
.PHONY: test-ssh
test-ssh: ## 测试SSH免密登录
	@echo "$(YELLOW)Testing SSH connection to $(TARGET)...$(NC)"
	ssh $(TARGET) "echo '✅ SSH OK'"

# 部署RabbitMQ
.PHONY: deploy-rabbitmq
deploy-rabbitmq: test-ssh ## 部署RabbitMQ到目标节点
	@echo "$(YELLOW)Deploying RabbitMQ to $(TARGET)...$(NC)"
	ssh $(TARGET) "docker stop rabbitmq 2>/dev/null || true"
	ssh $(TARGET) "docker rm rabbitmq 2>/dev/null || true"
	ssh $(TARGET) "docker run -d --name rabbitmq \
		-p 5672:5672 -p 15672:15672 \
		-e RABBITMQ_DEFAULT_USER=admin \
		-e RABBITMQ_DEFAULT_PASS=password \
		rabbitmq:3.12-management"
	@echo "$(GREEN)✅ RabbitMQ deployed!$(NC)"

# 部署Nacos
.PHONY: deploy-nacos
deploy-nacos: test-ssh ## 部署Nacos到目标节点
	@echo "$(YELLOW)Deploying Nacos to $(TARGET)...$(NC)"
	ssh $(TARGET) "docker stop nacos 2>/dev/null || true"
	ssh $(TARGET) "docker rm nacos 2>/dev/null || true"
	ssh $(TARGET) "docker run -d --name nacos \
		-p 8848:8848 -p 9848:9848 -p 9849:9849 \
		-e MODE=standalone \
		-e NACOS_AUTH_ENABLE=true \
		nacos/nacos-server:v2.2.3"
	@echo "$(GREEN)✅ Nacos deployed!$(NC)"

# 查看所有服务状态
.PHONY: status
status: ## 查看所有节点服务状态
	@echo "$(YELLOW)Service Status:$(NC)"
	@for node in redis-1 redis-2 redis-3; do \
		echo "--- $$node ---"; \
		ssh $$node "docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'" 2>/dev/null || echo "❌ Unreachable"; \
	done

# 查看日志
.PHONY: logs
logs: ## 查看指定服务日志 (make logs SERVICE=rabbitmq)
	@ssh $(TARGET) "docker logs -f --tail=100 $(SERVICE)"

# 停止服务
.PHONY: stop
stop: ## 停止指定服务 (make stop SERVICE=rabbitmq)
	@ssh $(TARGET) "docker stop $(SERVICE)"
	@echo "$(GREEN)✅ $(SERVICE) stopped$(NC)"

# 重启服务
.PHONY: restart
restart: stop deploy-$(SERVICE) ## 重启指定服务

# 清理所有容器
.PHONY: cleanup
cleanup: ## 停止并删除所有已部署的容器
	@for node in redis-1 redis-2 redis-3; do \
		echo "Cleaning $$node..."; \
		ssh $$node "docker stop $$(docker ps -q) 2>/dev/null || true"; \
		ssh $$node "docker rm $$(docker ps -aq) 2>/dev/null || true"; \
	done
```

### 使用方式
```bash
# 查看所有可用任务
make help

# 部署服务（默认redis-1）
make deploy-rabbitmq
make deploy-nacos TARGET=redis-2

# 查看状态
make status

# 查看日志
make logs SERVICE=rabbitmq TARGET=redis-1

# 停止/重启
make stop SERVICE=nacos TARGET=redis-2
make restart SERVICE=rabbitmq TARGET=redis-1
```

### Makefile vs Shell脚本对比
| 特性 | Shell脚本 | Makefile |
|------|----------|----------|
| **语法** | 过程式（命令序列） | 声明式（依赖关系） |
| **复用性** | 低（函数调用） | 高（目标依赖） |
| **可读性** | 中等 | 优秀（自动生成帮助） |
| **错误处理** | 手动set -e | 内置错误检测 |
| **并行执行** | 困难 | `make -j`原生支持 |

---

## 练习3：Docker化中间件测试

### 任务描述
测试部署的中间件服务是否正常运行。

### 验证步骤

#### 3.1 验证RabbitMQ
```bash
# 检查容器状态
ssh redis-1 "docker ps | grep rabbitmq"

# 测试Web管理界面
curl -s http://10.0.0.102:15672/api/healthchecks/node | jq .

# 使用Python测试连接
python3 -c "
import pika
credentials = pika.PlainCredentials('admin', 'password')
connection = pika.BlockingConnection(pika.ConnectionParameters('10.0.0.102', 5672, '/', credentials))
print('RabbitMQ connection successful!')
connection.close()
"
```

#### 3.2 验证Nacos
```bash
# 检查容器状态
ssh redis-2 "docker ps | grep nacos"

# 测试Nacos API
curl -s http://10.0.0.103:8848/nacos/v1/console/health/readiness | jq .

# 注册服务
curl -X POST 'http://10.0.0.103:8848/nacos/v1/ns/instance' \
  -d 'serviceName=test-service&ip=10.0.0.142&port=8080'
```

---

## 阶段总结

### 完成情况
- [x] SSH免密登录配置完成
- [x] **Makefile任务编排**编写完成（生产级方案）
- [x] RabbitMQ Docker部署测试通过
- [x] Nacos Docker部署测试通过

### 掌握度提升
- Shell脚本: +5（基础）
- **Makefile/声明式编排**: +15（核心）
- Docker基础: +10
- SSH配置: +5

### 关键收获（生产级思维）
1. **标准化**: Makefile的`.PHONY`和依赖关系让任务更规范
2. **可复用**: `make help`自动生成文档，降低使用门槛
3. **可维护**: 变量化配置（TARGET/SERVICE），避免硬编码
4. **错误处理**: 依赖关系确保前置条件（如test-ssh）

### 待解决问题
- 需要添加更多服务支持（MySQL、Redis、Gateway等）
- 需要处理容器启动失败的重试机制
- 考虑引入Justfile替代Makefile（更现代的语法）

### 下一步计划
进入Phase 2，学习：
- Docker Compose多服务编排
- Spring Boot API开发 + WebSocket实时日志
- 异步任务队列（@Async）