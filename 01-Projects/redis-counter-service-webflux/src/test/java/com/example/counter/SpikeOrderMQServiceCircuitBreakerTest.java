package com.example.counter;

import com.example.counter.dto.PreDeductResult;
import com.example.counter.service.SpikeOrderMQService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import reactor.test.StepVerifier;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * L3 熔断测试（RabbitMQ 服务熔断）
 * 纯单元测试，不依赖 Spring Context
 */
public class SpikeOrderMQServiceCircuitBreakerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private SpikeOrderMQService mqService;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        mqService = new SpikeOrderMQService(rabbitTemplate);

        // 通过反射设置 @Value 字段（因为单元测试不解析注解）
        setField(mqService, "exchange", "spike-exchange");
        setField(mqService, "routingKey", "spike.order.create");
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void testCircuitBreakerFallback_whenMQUnavailable() {
        PreDeductResult deductResult = PreDeductResult.success("ORDER-123", null);

        // 模拟 MQ 发送失败
        doThrow(new AmqpException("Connection refused"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        // MQ 异常时，应该降级到本地队列，不抛异常
        mqService.sendOrderMessage(deductResult, "user123", "req123")
                .as(StepVerifier::create)
                .verifyComplete();

        // 验证消息被暂存到本地队列
        assertThat(mqService.getPendingMessageCount()).isEqualTo(1);
    }

    @Test
    void testResendPendingMessages_afterMQRecovered() {
        PreDeductResult deductResult = PreDeductResult.success("ORDER-RESEND-" + System.currentTimeMillis(), null);

        // 模拟 MQ 失败，触发降级暂存
        doThrow(new AmqpException("Connection refused"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        mqService.sendOrderMessage(deductResult, "user123", "req123").block();

        // 验证消息已暂存
        assertThat(mqService.getPendingMessageCount()).isEqualTo(1);

        // 模拟 MQ 恢复
        doNothing().when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        // 触发补偿重发
        Integer resendCount = mqService.resendPendingMessages().block();

        // 验证重发成功
        assertThat(resendCount).isEqualTo(1);
        assertThat(mqService.getPendingMessageCount()).isEqualTo(0);
    }

    @Test
    void testNoMessageSent_whenDeductResultNotSuccess() {
        PreDeductResult deductResult = PreDeductResult.insufficient("not enough stock", null, null);

        // 预扣失败时，不应该发送 MQ 消息
        mqService.sendOrderMessage(deductResult, "user123", "req123").block();

        // 验证没有消息暂存（因为根本没尝试发送）
        assertThat(mqService.getPendingMessageCount()).isEqualTo(0);
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void testMQSendSuccess_noFallback() {
        PreDeductResult deductResult = PreDeductResult.success("ORDER-SUCCESS-" + System.currentTimeMillis(), null);

        // 模拟 MQ 发送成功
        doNothing().when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        // 正常发送应该成功
        mqService.sendOrderMessage(deductResult, "user123", "req123")
                .as(StepVerifier::create)
                .verifyComplete();

        // 验证没有消息暂存（发送成功）
        assertThat(mqService.getPendingMessageCount()).isEqualTo(0);
        verify(rabbitTemplate, times(1)).convertAndSend(anyString(), anyString(), any(Object.class));
    }
}