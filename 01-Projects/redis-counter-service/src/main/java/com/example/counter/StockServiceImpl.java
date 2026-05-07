package com.example.counter;

import com.example.counter.dto.DecrementResult;
import com.example.counter.strategy.DecrementStrategy;
import com.example.counter.strategy.DecrementStrategySelector;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 库存服务实现
 * 支持策略模式切换扣减策略
 */
@Service
public class StockServiceImpl implements StockService {

    private final StringRedisTemplate redisTemplate;
    private final DecrementStrategySelector strategySelector;

    public StockServiceImpl(StringRedisTemplate redisTemplate,
                           DecrementStrategySelector strategySelector) {
        this.redisTemplate = redisTemplate;
        this.strategySelector = strategySelector;
    }

    @Override
    public void initStock(String sku, long quantity) {
        redisTemplate.opsForValue().set("stock:" + sku, String.valueOf(quantity));
    }

    @Override
    public Long decrementStock(String sku, long quantity) {
        DecrementStrategy strategy = strategySelector.select();
        return strategy.decrement(sku, quantity);
    }

    @Override
    public List<DecrementResult> batchDecrementStock(String sku, List<Long> quantities) {
        DecrementStrategy strategy = strategySelector.select();
        List<Long> results = strategy.batchDecrement(sku, quantities);

        List<DecrementResult> decrementResults = new ArrayList<>();
        for (int i = 0; i < quantities.size(); i++) {
            long remaining = results.get(i);
            DecrementResult result = new DecrementResult();
            result.setRequestId("req-" + i);
            if (remaining >= 0) {
                result.setStatus("success");
                result.setRemaining(remaining);
            } else {
                result.setStatus("insufficient_stock");
                result.setRemaining(-1);
            }
            decrementResults.add(result);
        }
        return decrementResults;
    }

    @Override
    public Long getStock(String sku) {
        String value = redisTemplate.opsForValue().get("stock:" + sku);
        return value != null ? Long.parseLong(value) : 0L;
    }
}
