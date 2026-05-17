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
实现SSH免密登录 + 一键部署单个应用 + Docker化中间件

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

## 练习2：编写一键部署脚本

### 任务描述
编写`deploy.sh`脚本，支持一键部署Docker容器到指定节点。

### 脚本内容
```bash
#!/bin/bash
set -e

# 部署脚本 - 一键部署Docker服务

# 参数检查
if [ $# -lt 2 ]; then
    echo "Usage: $0 <service> <target-node>"
    echo "Example: $0 rabbitmq redis-1"
    exit 1
fi

SERVICE=$1
TARGET=$2

# 服务配置映射
case $SERVICE in
    rabbitmq)
        IMAGE="rabbitmq:3.12-management"
        PORT_MAP="-p 5672:5672 -p 15672:15672"
        ENV_VARS="-e RABBITMQ_DEFAULT_USER=admin -e RABBITMQ_DEFAULT_PASS=password"
        ;;
    nacos)
        IMAGE="nacos/nacos-server:v2.2.3"
        PORT_MAP="-p 8848:8848 -p 9848:9848 -p 9849:9849"
        ENV_VARS="-e MODE=standalone -e NACOS_AUTH_ENABLE=true"
        ;;
    *)
        echo "Unknown service: $SERVICE"
        exit 1
        ;;
esac

echo "=== Deploying $SERVICE to $TARGET ==="

# 停止旧容器
ssh $TARGET "docker stop $SERVICE 2>/dev/null || true"
ssh $TARGET "docker rm $SERVICE 2>/dev/null || true"

# 启动新容器
ssh $TARGET "docker run -d --name $SERVICE $PORT_MAP $ENV_VARS $IMAGE"

echo "=== Deployment completed ==="
echo "Checking status..."
ssh $TARGET "docker ps | grep $SERVICE"
```

### 脚本使用方式
```bash
# 给脚本添加执行权限
chmod +x deploy.sh

# 部署RabbitMQ到redis-1节点
./deploy.sh rabbitmq redis-1

# 部署Nacos到redis-2节点
./deploy.sh nacos redis-2
```

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
- [x] deploy.sh脚本编写完成
- [x] RabbitMQ Docker部署测试通过
- [x] Nacos Docker部署测试通过

### 掌握度提升
- Shell脚本: +10
- Docker基础: +10

### 待解决问题
- 需要添加更多服务支持（MySQL、Redis、Gateway等）
- 需要处理容器启动失败的重试机制

### 下一步计划
进入Phase 2，学习Docker Compose和Spring Boot API开发