package com.example.counter;

import com.example.counter.dto.MultiSkuOrderRequest;
import com.example.counter.dto.OrderItem;
import com.example.counter.dto.OrderResult;
import com.example.counter.service.MultiSkuOrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.test.context.TestPropertySource;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MultiSKU 订单服务 - 数据倾斜测试
 *
 * 测试场景：
 * - T-050: 单热点SKU高并发
 * - T-051: 多SKU部分失败（部分库存不足）
 * - T-052: 补偿链完整性测试
 * - T-053: 热点SKU + 普通SKU 混合场景
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.data.redis.cluster.nodes=10.0.0.102:6379,10.0.0.103:6379,10.0.0.104:6379,10.0.0.105:6379,10.0.0.106:6379,10.0.0.107:6379"
})
@DisplayName("MultiSKU Order Service - 数据倾斜测试")
public class MultiSkuOrderServiceDataSkewTest {

    private static final String PREFIX = "TEST_P2_";
    private static final int THREAD_COUNT = 10;

    @Autowired
    private MultiSkuOrderService orderService;

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    @AfterEach
    void cleanup() {
        redisTemplate.scan(ScanOptions.scanOptions().match(PREFIX + "*").build())
            .flatMap(key -> redisTemplate.unlink(key))
            .collectList()
            .block(Duration.ofSeconds(5));
    }

    // 辅助方法：安全获取failed map
    private Map<String, Integer> safeGetFailed(OrderResult result) {
        return result.getFailed() != null ? result.getFailed() : Collections.emptyMap();
    }

    // ========== T-050: 单热点SKU高并发 ==========

    @Test
    @DisplayName("T-050: 单热点SKU高并发")
    void testHotSkuHighConcurrency() throws InterruptedException {
        String sku = PREFIX + "HOT";
        int initialStock = 50;
        int requestCount = 20;
        int requestQty = 1;

        // 初始化库存
        redisTemplate.opsForValue().set("stock:" + sku, String.valueOf(initialStock)).block();

        // 并发请求
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(requestCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<OrderResult> results = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < requestCount; i++) {
            final int requestId = i;
            executor.submit(() -> {
                try {
                    MultiSkuOrderRequest request = new MultiSkuOrderRequest();
                    request.setItems(List.of(new OrderItem(sku, requestQty)));

                    OrderResult result = orderService.placeOrder(request).block(Duration.ofSeconds(5));
                    results.add(result);

                    if (result != null && result.isSuccess()) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.err.println("Request " + requestId + " failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // 验证结果
        System.out.println("T-050 Results: success=" + successCount.get() + ", fail=" + failCount.get());

        // 验证库存无超卖
        String finalStock = redisTemplate.opsForValue().get("stock:" + sku).block();
        System.out.println("Final stock: " + finalStock);

        assertTrue(Integer.parseInt(finalStock) >= 0, "Stock should not be negative");
        assertEquals(initialStock - successCount.get() * requestQty, Integer.parseInt(finalStock),
                "Stock should equal initial - successCount * qty");
    }

    // ========== T-051: 多SKU部分失败（部分库存不足） ==========

    @Test
    @DisplayName("T-051: 多SKU部分失败（部分库存不足）")
    void testPartialFailureWithCompensation() {
        String skuA = PREFIX + "T051_A";
        String skuB = PREFIX + "T051_B";

        // 初始化：skuA充足，skuB不足
        redisTemplate.opsForValue().set("stock:" + skuA, "100").block();
        redisTemplate.opsForValue().set("stock:" + skuB, "5").block();

        MultiSkuOrderRequest request = new MultiSkuOrderRequest();
        request.setItems(List.of(
            new OrderItem(skuA, 10),
            new OrderItem(skuB, 10)
        ));

        orderService.placeOrder(request)
                .as(StepVerifier::create)
                .expectNextMatches(result -> {
                    assertFalse(result.isSuccess());
                    assertTrue(result.getMessage().contains("Partial failure"));
                    return true;
                })
                .verifyComplete();

        // 验证补偿：skuA应该回滚到100
        assertEquals("100", redisTemplate.opsForValue().get("stock:" + skuA).block());
        // 验证skuB保持5
        assertEquals("5", redisTemplate.opsForValue().get("stock:" + skuB).block());
    }

    // ========== T-052: 补偿链完整性测试 ==========

    @Test
    @DisplayName("T-052: 补偿后再次下单成功")
    void testRetryAfterCompensation() {
        String skuA = PREFIX + "T052_A";
        String skuB = PREFIX + "T052_B";

        // 第一次：部分失败
        redisTemplate.opsForValue().set("stock:" + skuA, "100").block();
        redisTemplate.opsForValue().set("stock:" + skuB, "5").block();

        MultiSkuOrderRequest request1 = new MultiSkuOrderRequest();
        request1.setItems(List.of(
            new OrderItem(skuA, 10),
            new OrderItem(skuB, 10)
        ));

        orderService.placeOrder(request1)
                .as(StepVerifier::create)
                .expectNextMatches(result -> {
                    assertFalse(result.isSuccess());
                    return true;
                })
                .verifyComplete();

        // 验证第一次失败后，skuA已回滚
        assertEquals("100", redisTemplate.opsForValue().get("stock:" + skuA).block());

        // 第二次：用正确的数量下单
        MultiSkuOrderRequest request2 = new MultiSkuOrderRequest();
        request2.setItems(List.of(
            new OrderItem(skuA, 10),
            new OrderItem(skuB, 5)
        ));

        orderService.placeOrder(request2)
                .as(StepVerifier::create)
                .expectNextMatches(result -> {
                    assertTrue(result.isSuccess());
                    assertEquals(90L, result.getDecremented().get(skuA));
                    assertEquals(0L, result.getDecremented().get(skuB));
                    return true;
                })
                .verifyComplete();

        // 验证最终库存
        assertEquals("90", redisTemplate.opsForValue().get("stock:" + skuA).block());
        assertEquals("0", redisTemplate.opsForValue().get("stock:" + skuB).block());
    }

    // ========== T-053: 热点SKU + 普通SKU 混合场景 ==========

    @Test
    @DisplayName("T-053: 热点SKU + 普通SKU 混合场景")
    void testHotSkuWithNormalSku() {
        String hotSku = PREFIX + "HOT_NORMAL";
        String normalSku = PREFIX + "NORMAL";

        // 初始化：热点SKU库存少，普通SKU库存多
        redisTemplate.opsForValue().set("stock:" + hotSku, "3").block();
        redisTemplate.opsForValue().set("stock:" + normalSku, "100").block();

        MultiSkuOrderRequest request = new MultiSkuOrderRequest();
        request.setItems(List.of(
            new OrderItem(hotSku, 2),
            new OrderItem(normalSku, 10)
        ));

        orderService.placeOrder(request)
                .as(StepVerifier::create)
                .expectNextMatches(result -> {
                    assertTrue(result.isSuccess());
                    assertEquals(1L, result.getDecremented().get(hotSku));
                    assertEquals(90L, result.getDecremented().get(normalSku));
                    return true;
                })
                .verifyComplete();

        // 验证最终库存
        assertEquals("1", redisTemplate.opsForValue().get("stock:" + hotSku).block());
        assertEquals("90", redisTemplate.opsForValue().get("stock:" + normalSku).block());
    }

    // ========== T-054: 热点SKU库存耗尽 ==========

    @Test
    @DisplayName("T-054: 热点SKU库存刚好耗尽")
    void testHotSkuExactDepletion() {
        String sku = PREFIX + "T054";
        int initialStock = 5;

        redisTemplate.opsForValue().set("stock:" + sku, String.valueOf(initialStock)).block();

        // 5个请求，每个qty=1
        for (int i = 0; i < initialStock; i++) {
            MultiSkuOrderRequest request = new MultiSkuOrderRequest();
            request.setItems(List.of(new OrderItem(sku, 1)));

            orderService.placeOrder(request)
                    .as(StepVerifier::create)
                    .expectNextMatches(result -> {
                        assertTrue(result.isSuccess());
                        return true;
                    })
                    .verifyComplete();
        }

        // 第6个请求应该失败
        MultiSkuOrderRequest request6 = new MultiSkuOrderRequest();
        request6.setItems(List.of(new OrderItem(sku, 1)));

        orderService.placeOrder(request6)
                .as(StepVerifier::create)
                .expectNextMatches(result -> {
                    assertFalse(result.isSuccess());
                    assertTrue(safeGetFailed(result).containsKey(sku));
                    return true;
                })
                .verifyComplete();

        // 验证库存为0
        assertEquals("0", redisTemplate.opsForValue().get("stock:" + sku).block());
    }

    // ========== T-055: 多SKU全部失败 ==========

    @Test
    @DisplayName("T-055: 多SKU全部失败（无需补偿）")
    void testAllSkuFailure() {
        String skuA = PREFIX + "T055_A";
        String skuB = PREFIX + "T055_B";

        redisTemplate.opsForValue().set("stock:" + skuA, "5").block();
        redisTemplate.opsForValue().set("stock:" + skuB, "5").block();

        MultiSkuOrderRequest request = new MultiSkuOrderRequest();
        request.setItems(List.of(
            new OrderItem(skuA, 10),
            new OrderItem(skuB, 10)
        ));

        orderService.placeOrder(request)
                .as(StepVerifier::create)
                .expectNextMatches(result -> {
                    assertFalse(result.isSuccess());
                    assertTrue(result.getDecremented().isEmpty());
                    assertEquals(2, safeGetFailed(result).size());
                    return true;
                })
                .verifyComplete();

        // 验证库存均未变化
        assertEquals("5", redisTemplate.opsForValue().get("stock:" + skuA).block());
        assertEquals("5", redisTemplate.opsForValue().get("stock:" + skuB).block());
    }
}