package com.example.counter.config;

import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker.CircuitBreakerStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 限流 + 熔断配置
 * L1: HTTP 入口（spike）
 * L2: Redis 服务（redis_decrement）
 * L3: RabbitMQ 服务（mq_send_order）
 */
@Configuration
public class SentinelConfig {

    private static final Logger log = LoggerFactory.getLogger(SentinelConfig.class);

    // L1: HTTP 入口资源名
    public static final String SPIKE_RESOURCE = "spike";
    // L2: Redis 资源名
    public static final String REDIS_DECREMENT_RESOURCE = "redis_decrement";
    // L3: MQ 资源名
    public static final String MQ_SEND_ORDER_RESOURCE = "mq_send_order";

    // L1 限流配置
    @Value("${sentinel.spike.qps-threshold:10000}")
    private double qpsThreshold;

    // L1 熔断配置
    @Value("${sentinel.spike.degrade.slow-ratio-threshold:0.5}")
    private double spikeSlowRatioThreshold;

    @Value("${sentinel.spike.degrade.min-request-amount:5}")
    private int spikeMinRequestAmount;

    @Value("${sentinel.spike.degrade.time-window:30}")
    private int spikeTimeWindow;

    @Value("${sentinel.spike.degrade.max-processing-time:3000}")
    private long spikeMaxProcessingTime;

    // L2 熔断配置
    @Value("${sentinel.redis.degrade.exception-ratio-threshold:0.5}")
    private double redisExceptionRatioThreshold;

    @Value("${sentinel.redis.degrade.min-request-amount:10}")
    private int redisMinRequestAmount;

    @Value("${sentinel.redis.degrade.time-window:30}")
    private int redisTimeWindow;

    // L3 熔断配置
    @Value("${sentinel.mq.degrade.exception-ratio-threshold:0.4}")
    private double mqExceptionRatioThreshold;

    @Value("${sentinel.mq.degrade.min-request-amount:10}")
    private int mqMinRequestAmount;

    @Value("${sentinel.mq.degrade.time-window:60}")
    private int mqTimeWindow;

    @PostConstruct
    public void init() {
        initFlowRules();
        initDegradeRules();
        log.info("Sentinel 配置初始化完成");
    }

    /**
     * 限流规则（L1 HTTP 入口）
     */
    private void initFlowRules() {
        List<FlowRule> rules = new ArrayList<>();

        FlowRule userRule = new FlowRule();
        userRule.setResource(SPIKE_RESOURCE);
        userRule.setGrade(1);  // QPS
        userRule.setCount(qpsThreshold);
        userRule.setControlBehavior(0);  // 直接拒绝
        rules.add(userRule);

        FlowRuleManager.loadRules(rules);
        log.info("Sentinel 限流规则已加载，QPS阈值: {}", qpsThreshold);
    }

    /**
     * 熔断规则（L1/L2/L3 三级）
     */
    private void initDegradeRules() {
        List<DegradeRule> rules = new ArrayList<>();

        // L1 熔断规则：慢调用比例
        rules.add(createSpikeDegradeRule());

        // L2 熔断规则：异常比例
        rules.add(createRedisDegradeRule());

        // L3 熔断规则：异常比例
        rules.add(createMqDegradeRule());

        DegradeRuleManager.loadRules(rules);
        log.info("Sentinel 熔断规则已加载（L1/L2/L3 三级）");
    }

    /**
     * L1 入口熔断规则：慢调用比例
     */
    private DegradeRule createSpikeDegradeRule() {
        return new DegradeRule(SPIKE_RESOURCE)
                .setGrade(CircuitBreakerStrategy.SLOW_REQUEST_RATIO.getType())
                .setCount(spikeMaxProcessingTime)
                .setSlowRatioThreshold(spikeSlowRatioThreshold)
                .setMinRequestAmount(spikeMinRequestAmount)
                .setTimeWindow(spikeTimeWindow);
    }

    /**
     * L2 Redis 熔断规则：异常比例
     */
    private DegradeRule createRedisDegradeRule() {
        return new DegradeRule(REDIS_DECREMENT_RESOURCE)
                .setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType())
                .setCount(redisExceptionRatioThreshold)
                .setMinRequestAmount(redisMinRequestAmount)
                .setTimeWindow(redisTimeWindow);
    }

    /**
     * L3 MQ 熔断规则：异常比例
     */
    private DegradeRule createMqDegradeRule() {
        return new DegradeRule(MQ_SEND_ORDER_RESOURCE)
                .setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType())
                .setCount(mqExceptionRatioThreshold)
                .setMinRequestAmount(mqMinRequestAmount)
                .setTimeWindow(mqTimeWindow);
    }
}