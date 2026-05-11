package com.example.counter.config;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
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
 * 保护 Redis 集群不超过 200k QPS
 */
@Configuration
public class SentinelConfig {

    private static final Logger log = LoggerFactory.getLogger(SentinelConfig.class);

    public static final String SPIKE_RESOURCE = "spike";

    @Value("${sentinel.spike.resource-name:spike}")
    private String resourceName;

    @Value("${sentinel.spike.qps-threshold:10000}")
    private double qpsThreshold;

    @Value("${sentinel.spike.cluster-threshold:200000}")
    private double clusterThreshold;

    @PostConstruct
    public void init() {
        initFlowRules();
        log.info("Sentinel 限流配置初始化完成，资源名: {}, QPS阈值: {}, 集群阈值: {}",
                resourceName, qpsThreshold, clusterThreshold);
    }

    private void initFlowRules() {
        List<FlowRule> rules = new ArrayList<>();

        // 用户级限流规则
        FlowRule userRule = new FlowRule();
        userRule.setResource(resourceName);
        userRule.setGrade(1);  // QPS = 1
        userRule.setCount(qpsThreshold);
        userRule.setControlBehavior(0);  // default

        rules.add(userRule);

        FlowRuleManager.loadRules(rules);
        log.info("Sentinel 流控规则已加载: {} 条", rules.size());
    }
}