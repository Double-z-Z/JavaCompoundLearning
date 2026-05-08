package com.example.counter;

import com.example.counter.dto.MultiSkuOrderRequest;
import com.example.counter.dto.OrderItem;
import com.example.counter.dto.OrderResult;
import com.example.counter.service.MultiSkuOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import reactor.test.StepVerifier;

import java.util.List;

/**
 * 多SKU下单 Saga 补偿测试
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.data.redis.cluster.nodes=10.0.0.102:6379,10.0.0.103:6379,10.0.0.104:6379,10.0.0.105:6379,10.0.0.106:6379,10.0.0.107:6379"
})
public class MultiSkuOrderServiceTest {

    @Autowired
    private MultiSkuOrderService orderService;

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    @Test
    void testAllSkuSuccess() {
        String prefix = "TESTSUCCESS_";
        List<String> skus = List.of(prefix + "A", prefix + "B", prefix + "C");

        // 清理
        skus.forEach(sku -> redisTemplate.delete("stock:" + sku).block());

        // 初始化库存
        skus.forEach(sku -> redisTemplate.opsForValue().set("stock:" + sku, "100").block());

        // 创建订单：每个SKU买10
        MultiSkuOrderRequest request = new MultiSkuOrderRequest();
        request.setItems(List.of(
            new OrderItem(prefix + "A", 10),
            new OrderItem(prefix + "B", 10),
            new OrderItem(prefix + "C", 10)
        ));

        orderService.placeOrder(request)
                .as(StepVerifier::create)
                .expectNextMatches(result -> {
                    if (!result.isSuccess()) {
                        System.out.println("Expected success but got: " + result.getMessage());
                        return false;
                    }
                    System.out.println("All success: " + result.getDecremented());
                    return true;
                })
                .verifyComplete();
    }

    @Test
    void testPartialFailureWithCompensation() {
        String prefix = "TESTCOMP_";
        String sku1 = prefix + "A";  // 库存充足
        String sku2 = prefix + "B";  // 库存不足

        // 清理
        redisTemplate.delete("stock:" + sku1).block();
        redisTemplate.delete("stock:" + sku2).block();

        // 初始化：sku1=100, sku2=5
        redisTemplate.opsForValue().set("stock:" + sku1, "100").block();
        redisTemplate.opsForValue().set("stock:" + sku2, "5").block();

        // 创建订单：sku1买10（成功），sku2买200（失败）
        MultiSkuOrderRequest request = new MultiSkuOrderRequest();
        request.setItems(List.of(
            new OrderItem(sku1, 10),
            new OrderItem(sku2, 200)
        ));

        orderService.placeOrder(request)
                .as(StepVerifier::create)
                .expectNextMatches(result -> {
                    System.out.println("Result: success=" + result.isSuccess() + ", msg=" + result.getMessage());
                    System.out.println("Decremented: " + result.getDecremented());
                    System.out.println("Failed: " + result.getFailed());

                    // 验证：sku1应该回滚到100，sku2保持5
                    String sku1Stock = redisTemplate.opsForValue().get("stock:" + sku1).block();
                    String sku2Stock = redisTemplate.opsForValue().get("stock:" + sku2).block();
                    System.out.println("Final stocks - " + sku1 + ": " + sku1Stock + ", " + sku2 + ": " + sku2Stock);

                    return "100".equals(sku1Stock) && "5".equals(sku2Stock);
                })
                .verifyComplete();
    }
}