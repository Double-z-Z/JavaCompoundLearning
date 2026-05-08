package com.example.counter;

import com.example.counter.service.ReactiveStockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WebFlux 版本单元测试
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.data.redis.cluster.nodes=10.0.0.102:6379,10.0.0.103:6379,10.0.0.104:6379,10.0.0.105:6379,10.0.0.106:6379,10.0.0.107:6379"
})
public class ReactiveStockServiceTest {

    @Autowired
    private ReactiveStockService stockService;

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    private static final String TEST_SKU = "TEST_SKU_WF_";

    @Test
    void testInitAndGetStock() {
        String sku = TEST_SKU + "init";

        // 清理
        redisTemplate.delete("stock:" + sku).block();

        // 初始化
        stockService.initStock(sku, 100).block();

        // 查询
        stockService.getStock(sku)
                .as(StepVerifier::create)
                .expectNext("100")
                .verifyComplete();
    }

    @Test
    void testDecrementStock() {
        String sku = TEST_SKU + "decr";

        // 清理
        redisTemplate.delete("stock:" + sku).block();

        // 初始化 100
        stockService.initStock(sku, 100).block();

        // 扣减 10
        stockService.decrementStock(sku, 10)
                .as(StepVerifier::create)
                .expectNext("90")
                .verifyComplete();
    }

    @Test
    void testDecrementInsufficientStock() {
        String sku = TEST_SKU + "insuff";

        // 清理
        redisTemplate.delete("stock:" + sku).block();

        // 初始化 5
        stockService.initStock(sku, 5).block();

        // 扣减 10（库存不足）
        stockService.decrementStock(sku, 10)
                .as(StepVerifier::create)
                .expectNext("-1")
                .verifyComplete();
    }

    @Test
    void testNoOversell() {
        String sku = TEST_SKU + "oversell";

        // 清理
        redisTemplate.delete("stock:" + sku).block();

        // 初始化 100
        stockService.initStock(sku, 100).block();

        // 扣减 200 次，每次 1
        for (int i = 0; i < 200; i++) {
            stockService.decrementStock(sku, 1).block();
        }

        // 最终库存应该是 0（100 次成功，100 次失败返回 -1）
        stockService.getStock(sku)
                .as(StepVerifier::create)
                .expectNext("0")
                .verifyComplete();
    }
}