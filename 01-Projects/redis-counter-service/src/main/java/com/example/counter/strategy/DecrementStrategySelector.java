package com.example.counter.strategy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 扣减策略选择器
 * 根据配置选择对应的策略实现
 */
@Component
public class DecrementStrategySelector {

    @Autowired
    private AtomicDecrementStrategy atomicStrategy;

    @Autowired
    private RawDecrementStrategy rawStrategy;

    @Value("${stock.decrement.strategy:atomic}")
    private String strategyName;

    public DecrementStrategy select() {
        return switch (strategyName) {
            case "raw" -> rawStrategy;
            default -> atomicStrategy;
        };
    }

    public String getStrategyName() {
        return strategyName;
    }
}
