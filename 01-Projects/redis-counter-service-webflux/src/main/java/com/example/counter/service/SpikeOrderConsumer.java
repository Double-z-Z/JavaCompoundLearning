package com.example.counter.service;

import com.example.counter.dto.SpikeOrderMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

/**
 * 秒杀订单 MQ 消费者
 * 异步创建订单，处理预扣库存后的订单落地
 */
@Service
public class SpikeOrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(SpikeOrderConsumer.class);

    /**
     * 消费 MQ 消息，异步创建订单
     *
     * 处理流程：
     * 1. 接收预扣成功的订单消息
     * 2. 创建订单（入库）
     * 3. 更新订单状态
     * 4. （可选）发送通知
     *
     * 高并发下确保：
     * - 订单不丢失（MQ Ack 模式）
     * - 消费顺序（同一 SKU 订单顺序处理）
     */
    @RabbitListener(queues = "${rabbitmq.spike.queue:spike-order-queue}")
    public void consumeOrderMessage(SpikeOrderMessage message) {
        log.info("收到订单消息: orderId={}, userId={}, requestId={}",
                message.getOrderId(), message.getUserId(), message.getRequestId());

        try {
            // TODO: 实际的订单创建逻辑（调用订单服务/入库）
            createOrder(message);

            log.info("订单创建完成: orderId={}", message.getOrderId());
        } catch (Exception e) {
            log.error("订单创建失败: orderId={}", message.getOrderId(), e);
            // MQ 会自动重试，这里可以记录失败日志
            throw e;  // 让 MQ 重试
        }
    }

    /**
     * 创建订单
     * 这里只是示例，实际需要：
     * 1. 订单入库（MySQL/NoSQL）
     * 2. 发送订单完成通知（WebSocket/短信）
     * 3. 更新用户积分/优惠券等
     */
    private void createOrder(SpikeOrderMessage message) {
        // Simulate order creation
        log.info("[OrderService] 创建订单: orderId={}, userId={}, items={}, timestamp={}",
                message.getOrderId(),
                message.getUserId(),
                message.getItems(),
                message.getTimestamp());

        // 这里可以添加：
        // - 订单入库
        // - 发送通知
        // - 更新统计
    }
}