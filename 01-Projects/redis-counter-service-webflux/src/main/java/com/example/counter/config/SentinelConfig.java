package com.example.counter.config;

import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 自适应限流配置
 */
@Configuration
public class SentinelConfig {

    private static final Logger log = LoggerFactory.getLogger(SentinelConfig.class);

    public static final String SPIKE_RESOURCE = "spike";

    @Value("${sentinel.spike.qps-threshold:10000}")
    private double qpsThreshold;

    @PostConstruct
    public void init() {
        List<FlowRule> rules = new ArrayList<>();

        FlowRule userRule = new FlowRule();
        userRule.setResource(SPIKE_RESOURCE);
        userRule.setGrade(1);  // QPS
        userRule.setCount(qpsThreshold);
        userRule.setControlBehavior(0);

        rules.add(userRule);
        FlowRuleManager.loadRules(rules);

        log.info("Sentinel 限流规则已加载，QPS阈值: {}", qpsThreshold);
    }
}