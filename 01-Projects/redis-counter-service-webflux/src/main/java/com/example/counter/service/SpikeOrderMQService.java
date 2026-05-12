package com.example.counter.service;

import com.example.counter.dto.PreDeductResult;
import com.example.counter.dto.SpikeOrderMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

/**
 * 秒杀 MQ 生产者服务
 * 预扣库存成功后，写入 MQ 异步创建订单
 */
@Service
public class SpikeOrderMQService {

    private static final Logger log = LoggerFactory.getLogger(SpikeOrderMQService.class);

    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.spike.exchange:spike-exchange}")
    private String exchange;

    @Value("${rabbitmq.spike.routing-key:spike.order.create}")
    private String routingKey;

    public SpikeOrderMQService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 发送订单消息到 MQ
     * 使用 publishOn 避免阻塞 WebFlux 线程
     */
    public Mono<Void> sendOrderMessage(PreDeductResult deductResult, String userId, String requestId) {
        if (!deductResult.isSuccess()) {
            return Mono.empty();
        }

        return Mono.fromRunnable(() -> {
            try {
                SpikeOrderMessage message = new SpikeOrderMessage(
                        deductResult.getOrderId(),
                        userId,
                        null,  // items will be populated from request context
                        System.currentTimeMillis(),
                        requestId
                );

                rabbitTemplate.convertAndSend(exchange, routingKey, message);
                log.debug("订单消息已发送到MQ: orderId={}, exchange={}, routingKey={}",
                        deductResult.getOrderId(), exchange, routingKey);
            } catch (Exception e) {
                log.error("发送订单消息到MQ失败: orderId={}", deductResult.getOrderId(), e);
                throw e;
            }
        }).publishOn(Schedulers.boundedElastic()).then();
    }

    /**
     * 生成订单号
     */
    public String generateOrderId() {
        return "ORD-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}