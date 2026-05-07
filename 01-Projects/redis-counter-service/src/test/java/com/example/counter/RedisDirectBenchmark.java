package com.example.counter;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis 直连压测 - 绕过 HTTP 层定位瓶颈
 * 对比：HTTP 层 (38.7K QPS) vs Redis 直连 (?)
 */
public class RedisDirectBenchmark {

    // Redis Cluster 节点（从你的 application.yml 获取）
    private static final String[] REDIS_NODES = {
        "10.0.0.102", "10.0.0.103", "10.0.0.104", 
        "10.0.0.105", "10.0.0.106", "10.0.0.107"
    };
    
    // 使用单个节点测试，避免 Cluster 路由开销
    private static final String TEST_NODE = "10.0.0.104";
    private static final int REDIS_PORT = 6379;
    
    private static final String SKU = "BENCH001";
    private static final String STOCK_KEY = "stock:" + SKU;
    
    // 压测参数
    private static final int WARMUP_REQUESTS = 5000;
    private static final int TOTAL_REQUESTS = 100000;
    private static final int CONCURRENT_THREADS = 100;

    // Lua 脚本（与 StockServiceImpl 完全一致）
    private static final String DECREMENT_SCRIPT = """
            local stock = tonumber(redis.call('GET', KEYS[1]) or 0)
            local quantity = tonumber(ARGV[1])
            if stock >= quantity then
                return redis.call('DECRBY', KEYS[1], quantity)
            else
                return -1
            end
            """;

    public static void main(String[] args) throws Exception {
        System.out.println("========== Redis 直连压测 ==========");
        System.out.println("目标节点: " + TEST_NODE + ":" + REDIS_PORT);
        System.out.println("并发线程: " + CONCURRENT_THREADS);
        System.out.println("总请求数: " + TOTAL_REQUESTS);
        System.out.println();

        // 1. 配置 Lettuce 客户端（与 Spring Boot 配置类似）
        ClientResources clientResources = DefaultClientResources.builder()
                .ioThreadPoolSize(4)
                .computationThreadPoolSize(4)
                .build();

        RedisURI redisURI = RedisURI.builder()
                .withHost(TEST_NODE)
                .withPort(REDIS_PORT)
                .withTimeout(Duration.ofSeconds(3))
                .build();

        RedisClient client = RedisClient.create(clientResources, redisURI);
        
        // 2. 建立连接
        StatefulRedisConnection<String, String> connection = client.connect();
        RedisCommands<String, String> sync = connection.sync();

        // 3. 初始化库存
        System.out.println("初始化库存...");
        sync.set(STOCK_KEY, "10000000");
        String initialStock = sync.get(STOCK_KEY);
        System.out.println("初始库存: " + initialStock);
        System.out.println();

        // 4. 预热 JVM + 连接池
        System.out.println("预热中 (" + WARMUP_REQUESTS + " 请求)...");
        for (int i = 0; i < WARMUP_REQUESTS; i++) {
            sync.eval(DECREMENT_SCRIPT, io.lettuce.core.ScriptOutputType.INTEGER,
                    new String[]{STOCK_KEY}, "1");
        }
        
        // 重置库存
        sync.set(STOCK_KEY, "10000000");
        System.out.println("预热完成，库存已重置");
        System.out.println();

        // 5. 正式压测
        System.out.println("开始压测...");
        long startTime = System.currentTimeMillis();

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch latch = new CountDownLatch(TOTAL_REQUESTS);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        AtomicLong totalLatency = new AtomicLong(0);

        for (int i = 0; i < TOTAL_REQUESTS; i++) {
            executor.submit(() -> {
                long reqStart = System.nanoTime();
                try {
                    Long result = sync.eval(DECREMENT_SCRIPT, 
                            io.lettuce.core.ScriptOutputType.INTEGER,
                            new String[]{STOCK_KEY}, "1");
                    
                    long latency = System.nanoTime() - reqStart;
                    totalLatency.addAndGet(latency);
                    
                    if (result != null && result >= 0) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                }
                latch.countDown();
            });
        }

        latch.await();
        long duration = System.currentTimeMillis() - startTime;
        executor.shutdown();

        // 6. 计算结果
        double qps = TOTAL_REQUESTS * 1000.0 / duration;
        double avgLatencyMs = (totalLatency.get() / TOTAL_REQUESTS) / 1_000_000.0;

        // 7. 输出报告
        System.out.println("\n========== 压测结果 ==========");
        System.out.printf("总请求数:    %,d%n", TOTAL_REQUESTS);
        System.out.printf("成功扣减:    %,d%n", successCount.get());
        System.out.printf("库存不足:    %,d%n", failCount.get());
        System.out.printf("总耗时:      %,d ms%n", duration);
        System.out.printf("QPS:         %,.2f%n", qps);
        System.out.printf("平均延迟:    %.3f ms%n", avgLatencyMs);

        // 8. 对比分析
        System.out.println("\n========== 瓶颈对比分析 ==========");
        System.out.printf("HTTP 完整链路:    ~38,700 QPS%n");
        System.out.printf("Redis 直连:       ~%,.0f QPS%n", qps);
        System.out.printf("差值 (HTTP开销):  ~%,.0f QPS%n", (qps - 38700));
        
        if (qps > 50000) {
            System.out.println("结论: 瓶颈在 Spring MVC / JSON 序列化层");
        } else if (qps > 40000) {
            System.out.println("结论: HTTP 层和 Redis 层都有优化空间");
        } else {
            System.out.println("结论: 瓶颈在 Redis 客户端 / 网络 / Lua 执行");
        }

        // 9. 清理
        connection.close();
        client.shutdown();
        clientResources.shutdown();
        
        System.out.println("\n压测完成！");
    }
}
