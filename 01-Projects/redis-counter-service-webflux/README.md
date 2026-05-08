# Redis Counter Service WebFlux 版本

基于 Spring WebFlux 的响应式版本，与 Spring MVC 版本对比学习。

详细设计文档见 [docs/design.md](docs/design.md)

## 技术栈

- Spring Boot 3.2.0
- Spring WebFlux（响应式）
- Spring Data Redis Reactive
- Netty（嵌入式服务器，默认端口 8081）
- Lettuce 响应式客户端

## 运行

```bash
cd redis-counter-service-webflux
mvn package -DskipTests
java -jar target/redis-counter-service-webflux-1.0-SNAPSHOT.jar
```

## 对比测试

| 端口 | 版本 | 说明 |
|------|------|------|
| 8080 | WebFlux | 新版本（替代 MVC 版本） |

```bash
# 初始化库存
curl -X POST "http://localhost:8081/stock/TEST001/init?quantity=100"

# 扣减（返回纯文本）
curl -X POST "http://localhost:8081/stock/TEST001/decrement?quantity=1"
# 返回: 99 或 -1

# 查询
curl "http://localhost:8081/stock/TEST001"
```

## 压测对比

```powershell
# WebFlux 版本
.\bombardier.exe -c 200 -d 30s http://localhost:8081/stock/TEST001/decrement?quantity=1

# Spring MVC 版本对比
.\bombardier.exe -c 200 -d 30s http://localhost:8080/stock/TEST001/decrement?quantity=1
```

## 与 MVC 版本的核心区别

| 方面 | Spring MVC | Spring WebFlux |
|------|------------|----------------|
| 线程模型 | 同步阻塞 | 异步非阻塞 |
| 容器 | Tomcat | Netty |
| 并发处理 | 线程池 | Event Loop |
| 适用场景 | 低并发 | 高并发 |