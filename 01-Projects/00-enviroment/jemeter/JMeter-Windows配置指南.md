# JMeter Windows 压测配置

## 1. 下载 JMeter

1. 访问 https://jmeter.apache.org/download_jmeter.cgi
2. 下载 `apache-jmeter-5.6.3.zip`（或最新版本）
3. 解压到 `C:\jmeter` 目录

## 2. 配置环境变量（可选）

```powershell
# 管理员 PowerShell
[Environment]::SetEnvironmentVariable("Path", $env:Path + ";C:\jmeter\bin", "User")
```

## 3. 运行测试

### 方式一：命令行运行（推荐）

```powershell
cd C:\jmeter\bin

# 运行测试
.\jmeter.bat -n -t C:\Users\zhuhengyi321\Documents\redis-test.jmx -l C:\Users\zhuhengyi321\Documents\results.jtl

# 查看报告
.\jmeter.bat -g C:\Users\zhuhengyi321\Documents\results.jtl -o C:\Users\zhuhengyi321\Documents\report
```

### 方式二：GUI 界面运行

```powershell
.\jmeter.bat
```

然后 File → Open 选择 `.jmx` 文件，点击绿色三角运行。

## 4. 测试计划说明

| 配置项 | 值 | 说明 |
|-------|-----|------|
| 线程数 | 100 | 并发用户数 |
| 加速时间 | 10秒 | 逐步增加到100线程 |
| 持续时间 | 30秒 | 压测时长 |
| 目标URL | http://10.0.0.142:8080 | 应用服务器 |
| 请求路径 | /stock/SKU001/decrement?quantity=1 | POST |

## 5. 先初始化库存

```bash
curl -X POST http://10.0.0.142:8080/stock/SKU001/init?quantity=1000000000
```

## 6. 验证服务可用

```bash
curl http://10.0.0.142:8080/stock/SKU001
```

## 7. 备选：简化测试（线程数调整）

如果 100 线程太高，可以先试 50：

修改 jmx 文件中的 `<intProp name="ThreadGroup.num_threads">100</intProp>`
改为 `50`。

---

## 常见问题

### 中文乱码
在 `bin/jmeter.properties` 中添加：
```properties
sampleresult.default.encoding=UTF-8
```

### 内存不足
编辑 `bin/jmeter.bat`，找到 `-Xms512m -Xmx512m` 改为：
```batch
set HEAP=-Xms1g -Xmx4g
```
