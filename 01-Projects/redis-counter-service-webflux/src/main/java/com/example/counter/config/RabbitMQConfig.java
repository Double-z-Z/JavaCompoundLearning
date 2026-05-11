package com.example.counter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 削峰配置
 * 消息队列配置：预扣库存 → 写入MQ → 异步创建订单
 */
@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.spike.exchange:spike-exchange}")
    private String spikeExchange;

    @Value("${rabbitmq.spike.queue:spike-order-queue}")
    private String spikeQueue;

    @Value("${rabbitmq.spike.routing-key:spike.order.create}")
    private String spikeRoutingKey;

    @Value("${rabbitmq.spike.prefetch:100}")
    private int prefetch;

    @Value("${rabbitmq.spike.concurrent-consumers:5}")
    private int concurrentConsumers;

    // ===== Exchange =====
    @Bean
    public DirectExchange spikeExchange() {
        return new DirectExchange(spikeExchange, true, false);
    }

    // ===== Queue =====
    @Bean
    public Queue spikeOrderQueue() {
        return QueueBuilder.durable(spikeQueue)
                .withArgument("x-dead-letter-exchange", spikeExchange + ".dlx")
                .withArgument("x-dead-letter-routing-key", spikeRoutingKey + ".dlq")
                .build();
    }

    @Bean
    public Queue spikeOrderDLQ() {
        return QueueBuilder.durable(spikeQueue + ".dlq").build();
    }

    @Bean
    public DirectExchange spikeDLXExchange() {
        return new DirectExchange(spikeExchange + ".dlx", true, false);
    }

    @Bean
    public Binding spikeDLQBinding() {
        return BindingBuilder.bind(spikeOrderDLQ())
                .to(spikeDLXExchange())
                .with(spikeRoutingKey + ".dlq");
    }

    // ===== Binding =====
    @Bean
    public Binding spikeOrderBinding() {
        return BindingBuilder.bind(spikeOrderQueue())
                .to(spikeExchange())
                .with(spikeRoutingKey);
    }

    // ===== Message Converter =====
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // ===== RabbitTemplate =====
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        template.setExchange(spikeExchange);
        template.setRoutingKey(spikeRoutingKey);
        return template;
    }

    // ===== Listener Container Factory =====
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setConcurrentConsumers(concurrentConsumers);
        factory.setMaxConcurrentConsumers(concurrentConsumers * 2);
        factory.setPrefetchCount(prefetch);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        return factory;
    }
}