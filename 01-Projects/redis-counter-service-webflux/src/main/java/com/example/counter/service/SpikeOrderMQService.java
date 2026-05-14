package com.example.counter.service;

import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.adapter.reactor.SentinelReactorTransformer;
import com.example.counter.config.SentinelConfig;
import com.example.counter.dto.PreDeductResult;
import com.example.counter.dto.SpikeOrderMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 秒杀 MQ 生产者服务
 * 预扣库存成功后，写入 MQ 异步创建订单
 * L3 熔断保护：MQ 不可用时本地暂存，后续补偿重发
 */
@Service
public class SpikeOrderMQService {

    private static final Logger log = LoggerFactory.getLogger(SpikeOrderMQService.class);

    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.spike.exchange:spike-exchange}")
    private String exchange;

    @Value("${rabbitmq.spike.routing-key:spike.order.create}")
    private String routingKey;

    // 本地消息暂存队列（MQ 熔断时使用）
    private final Queue<SpikeOrderMessage> localMessageQueue = new ConcurrentLinkedQueue<>();

    public SpikeOrderMQService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 发送订单消息到 MQ
     * 使用 publishOn 避免阻塞 WebFlux 线程
     * L3 熔断保护：MQ 异常时本地暂存
     */
    public Mono<Void> sendOrderMessage(PreDeductResult deductResult, String userId, String requestId) {
        if (!deductResult.isSuccess()) {
            return Mono.empty();
        }

        SpikeOrderMessage message = new SpikeOrderMessage(
                deductResult.getOrderId(),
                userId,
                null,
                System.currentTimeMillis(),
                requestId
        );

        // L3 Sentinel 埋点
        return doSendMessage(message)
                .transform(new SentinelReactorTransformer<>(SentinelConfig.MQ_SEND_ORDER_RESOURCE))
                .onErrorResume(Exception.class, e -> {
                    Tracer.trace(e);
                    log.warn("MQ 熔断降级，消息暂存本地: orderId={}, error={}", message.getOrderId(), e.getMessage());
                    return fallbackToLocalQueue(message);
                });
    }

    /**
     * 实际发送 MQ 消息
     */
    private Mono<Void> doSendMessage(SpikeOrderMessage message) {
        return Mono.fromRunnable(() -> {
            try {
                rabbitTemplate.convertAndSend(exchange, routingKey, message);
                log.debug("订单消息已发送到MQ: orderId={}, exchange={}, routingKey={}",
                        message.getOrderId(), exchange, routingKey);
            } catch (Exception e) {
                log.error("发送订单消息到MQ失败: orderId={}", message.getOrderId(), e);
                throw e;
            }
        }).publishOn(Schedulers.boundedElastic()).then();
    }

    /**
     * L3 降级逻辑：本地暂存消息
     */
    private Mono<Void> fallbackToLocalQueue(SpikeOrderMessage message) {
        return Mono.fromRunnable(() -> {
            localMessageQueue.offer(message);
            log.warn("MQ降级：消息暂存本地队列，orderId={}, queueSize={}",
                    message.getOrderId(), localMessageQueue.size());
        }).then();
    }

    /**
     * 补偿重发Pending消息（定时调用或手动触发）
     */
    public Mono<Integer> resendPendingMessages() {
        return Mono.fromCallable(() -> {
            int count = 0;
            SpikeOrderMessage msg;
            while ((msg = localMessageQueue.poll()) != null) {
                try {
                    rabbitTemplate.convertAndSend(exchange, routingKey, msg);
                    count++;
                    log.info("补偿重发MQ消息成功: orderId={}", msg.getOrderId());
                } catch (Exception e) {
                    log.error("补偿重发MQ消息失败: orderId={}", msg.getOrderId(), e);
                    // 重新入队，等待下次重发
                    localMessageQueue.offer(msg);
                    break; // 防止死循环
                }
            }
            return count;
        }).publishOn(Schedulers.boundedElastic());
    }

    /**
     * 获取待重发消息数量（用于监控）
     */
    public int getPendingMessageCount() {
        return localMessageQueue.size();
    }

    /**
     * 生成订单号
     */
    public String generateOrderId() {
        return "ORD-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}