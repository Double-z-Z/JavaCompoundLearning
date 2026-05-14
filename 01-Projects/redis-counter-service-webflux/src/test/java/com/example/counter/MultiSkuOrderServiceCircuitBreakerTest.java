package com.example.counter;

import com.example.counter.dto.MultiSkuOrderRequest;
import com.example.counter.dto.OrderItem;
import com.example.counter.dto.OrderResult;
import com.example.counter.service.MultiSkuOrderService;
import com.example.counter.service.MultiSkuOrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * L2 熔断测试（Redis 服务熔断）
 * 纯单元测试，不依赖 Spring Context
 */
public class MultiSkuOrderServiceCircuitBreakerTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private RedisScript<Long> decrementScript;

    private MultiSkuOrderService orderService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        orderService = new MultiSkuOrderServiceImpl(redisTemplate, decrementScript);
    }

    private MultiSkuOrderRequest createRequest(String sku, int qty) {
        MultiSkuOrderRequest request = new MultiSkuOrderRequest();
        OrderItem item = new OrderItem();
        item.setSku(sku);
        item.setQty(qty);
        request.setItems(List.of(item));
        return request;
    }

    @Test
    void testCircuitBreakerFallback_whenRedisUnavailable() {
        String sku = "CIRCUIT_TEST_" + System.currentTimeMillis();
        MultiSkuOrderRequest request = createRequest(sku, 1);

        // 模拟 Redis 执行抛出异常（模拟熔断场景）
        when(redisTemplate.execute(any(RedisScript.class), any(), any()))
                .thenReturn(Flux.error(new RuntimeException("Redis connection refused")));

        // 当 Redis 异常时，应该降级返回系统繁忙
        OrderResult result = orderService.placeOrder(request).block();

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("degraded");

        // 验证 Redis 被调用（触发熔断计数）
        verify(redisTemplate, atLeastOnce()).execute(any(RedisScript.class), any(), any());
    }

    @Test
    void testNormalFlow_whenRedisAvailable() {
        String sku = "CIRCUIT_NORMAL_" + System.currentTimeMillis();
        MultiSkuOrderRequest request = createRequest(sku, 1);

        // 模拟 Redis 返回成功（库存充足，返回剩余库存 99）
        when(redisTemplate.execute(any(RedisScript.class), any(), any()))
                .thenReturn(Flux.just(99L));

        // 正常下单应该成功
        OrderResult result = orderService.placeOrder(request).block();

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void testInsufficientStock_shouldReturnFailure() {
        String sku = "CIRCUIT_INSUFF_" + System.currentTimeMillis();
        MultiSkuOrderRequest request = createRequest(sku, 100);

        // 模拟 Redis 返回 -1（库存不足）
        when(redisTemplate.execute(any(RedisScript.class), any(), any()))
                .thenReturn(Flux.just(-1L));

        // 库存不足应该失败
        OrderResult result = orderService.placeOrder(request).block();

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getFailed()).containsKey(sku);
    }

    @Test
    void testMultipleSkus_allSuccess() {
        MultiSkuOrderRequest request = new MultiSkuOrderRequest();
        OrderItem item1 = new OrderItem();
        item1.setSku("SKU1");
        item1.setQty(1);
        OrderItem item2 = new OrderItem();
        item2.setSku("SKU2");
        item2.setQty(1);
        request.setItems(List.of(item1, item2));

        // 两个 SKU 都成功
        when(redisTemplate.execute(any(RedisScript.class), any(), any()))
                .thenReturn(Flux.just(99L));

        OrderResult result = orderService.placeOrder(request).block();

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
    }
}