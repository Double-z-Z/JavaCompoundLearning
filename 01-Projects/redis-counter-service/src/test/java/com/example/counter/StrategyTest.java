package com.example.counter;

import com.example.counter.dto.DecrementResult;
import com.example.counter.strategy.AtomicDecrementStrategy;
import com.example.counter.strategy.DecrementStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 扣减策略单元测试
 *
 * 注意：这些测试需要真实的 Redis 环境
 * 运行前确保 Redis 服务可用
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.data.redis.cluster.nodes=10.0.0.102:6379,10.0.0.103:6379,10.0.0.104:6379,10.0.0.105:6379,10.0.0.106:6379,10.0.0.107:6379",
    "stock.decrement.strategy=atomic"
})
public class StrategyTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private StockService stockService;

    private static final String TEST_SKU = "TEST_SKU_";

    @BeforeEach
    void setUp() {
        // 清理测试数据
        redisTemplate.delete("stock:" + TEST_SKU + "atomic");
        redisTemplate.delete("stock:" + TEST_SKU + "pipeline-atomic");
        redisTemplate.delete("stock:" + TEST_SKU + "pipeline-raw");
    }

    // ==================== AtomicStrategy 测试 ====================

    @Test
    void testAtomicStrategy_SingleDecrement() {
        AtomicDecrementStrategy strategy = new AtomicDecrementStrategy(redisTemplate);

        stockService.initStock(TEST_SKU + "atomic", 100);

        Long result = strategy.decrement(TEST_SKU + "atomic", 10);

        assertEquals(90, result);
        assertEquals(90, stockService.getStock(TEST_SKU + "atomic"));
    }

    @Test
    void testAtomicStrategy_InsufficientStock() {
        AtomicDecrementStrategy strategy = new AtomicDecrementStrategy(redisTemplate);

        stockService.initStock(TEST_SKU + "atomic", 5);

        Long result = strategy.decrement(TEST_SKU + "atomic", 10);

        assertEquals(-1, result);
        assertEquals(5, stockService.getStock(TEST_SKU + "atomic")); // 库存未变
    }

    @Test
    void testAtomicStrategy_ConcurrentDecrement_NoOversell() throws InterruptedException {
        AtomicDecrementStrategy strategy = new AtomicDecrementStrategy(redisTemplate);
        int initialStock = 100;
        int threadCount = 50;
        int decrementsPerThread = 5;

        stockService.initStock(TEST_SKU + "atomic", initialStock);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    Long result = strategy.decrement(TEST_SKU + "atomic", decrementsPerThread);
                    if (result != null && result >= 0) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // 验证：成功次数 * 每次扣减数 <= 初始库存
        assertTrue(successCount.get() * decrementsPerThread <= initialStock,
                "超卖发生！成功次数: " + successCount.get() + ", 初始库存: " + initialStock);

        // 验证：最终库存 = 初始库存 - 成功次数 * 每次扣减数
        long finalStock = stockService.getStock(TEST_SKU + "atomic");
        assertEquals(initialStock - successCount.get() * decrementsPerThread, finalStock);
    }

    // ==================== 集成测试 ====================

    @Test
    void testStockService_Decrement_UsesStrategy() {
        stockService.initStock(TEST_SKU + "strategy", 50);

        Long result = stockService.decrementStock(TEST_SKU + "strategy", 10);

        assertEquals(40, result);
    }

    @Test
    void testStockService_BatchDecrement_ReturnsCorrectResults() {
        stockService.initStock(TEST_SKU + "batch", 100);

        List<Long> quantities = List.of(10L, 20L, 30L, 40L);
        List<DecrementResult> results = stockService.batchDecrementStock(TEST_SKU + "batch", quantities);

        assertEquals(4, results.size());

        // 统计成功和失败
        long successCount = results.stream()
                .filter(r -> "success".equals(r.getStatus()))
                .count();
        long failCount = results.stream()
                .filter(r -> "insufficient_stock".equals(r.getStatus()))
                .count();

        System.out.println("成功: " + successCount + ", 失败: " + failCount);
        assertEquals(4, successCount + failCount);
    }

    @Test
    void testOversellProtection_WithAtomicStrategy() throws InterruptedException {
        // 初始库存 100，并发 200 个线程，每个扣减 1
        int initialStock = 100;
        int threadCount = 200;

        stockService.initStock(TEST_SKU + "oversell", initialStock);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    Long result = stockService.decrementStock(TEST_SKU + "oversell", 1);
                    if (result != null && result >= 0) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // 验证：成功次数 <= 初始库存（无超卖）
        System.out.println("初始库存: " + initialStock + ", 成功扣减: " + successCount.get());
        assertTrue(successCount.get() <= initialStock,
                "超卖发生！成功: " + successCount.get() + ", 初始: " + initialStock);
    }
}
