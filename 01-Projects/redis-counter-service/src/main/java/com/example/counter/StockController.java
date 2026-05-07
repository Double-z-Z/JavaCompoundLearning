package com.example.counter;

import com.example.counter.dto.BatchDecrementRequest;
import com.example.counter.dto.BatchDecrementResponse;
import com.example.counter.dto.DecrementResult;
import com.example.counter.strategy.DecrementStrategySelector;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存 REST API 控制器
 * POST /stock/{sku}/init?quantity=100
 * POST /stock/{sku}/decrement?quantity=1
 * POST /stock/{sku}/batch-decrement
 * GET  /stock/{sku}
 * GET  /stock/strategy
 */
@RestController
@RequestMapping("/stock")
public class StockController {

    private final StockService stockService;
    private final DecrementStrategySelector strategySelector;

    public StockController(StockService stockService, DecrementStrategySelector strategySelector) {
        this.stockService = stockService;
        this.strategySelector = strategySelector;
    }

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("pong");
    }

    @GetMapping("/strategy")
    public ResponseEntity<Map<String, String>> getStrategy() {
        Map<String, String> response = new HashMap<>();
        response.put("strategy", strategySelector.getStrategyName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{sku}/init")
    public ResponseEntity<Map<String, Object>> initStock(
            @PathVariable String sku,
            @RequestParam long quantity) {
        stockService.initStock(sku, quantity);
        return ResponseEntity.ok(createResponse(sku, quantity, "initialized"));
    }

    @PostMapping("/{sku}/decrement")
    public ResponseEntity<String> decrement(
            @PathVariable String sku,
            @RequestParam long quantity) {
        Long result = stockService.decrementStock(sku, quantity);
        if (result == -1) {
            return ResponseEntity.ok("-1");
        } else {
            return ResponseEntity.ok(String.valueOf(result));
        }
    }

    @PostMapping("/{sku}/batch-decrement")
    public ResponseEntity<BatchDecrementResponse> batchDecrement(
            @PathVariable String sku,
            @RequestBody BatchDecrementRequest request) {

        List<Long> quantities = request.getRequests().stream()
                .map(BatchDecrementRequest.DecrementItem::getQuantity)
                .toList();

        List<DecrementResult> results = stockService.batchDecrementStock(sku, quantities);

        // Build summary
        BatchDecrementResponse.BatchSummary summary = new BatchDecrementResponse.BatchSummary();
        summary.setTotal(results.size());
        summary.setSuccess((int) results.stream().filter(r -> "success".equals(r.getStatus())).count());
        summary.setFailed((int) results.stream().filter(r -> "insufficient_stock".equals(r.getStatus())).count());
        summary.setFinalStock(stockService.getStock(sku));

        // Build response
        BatchDecrementResponse response = new BatchDecrementResponse();
        response.setSku(sku);
        response.setResults(results);
        response.setSummary(summary);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{sku}")
    public ResponseEntity<Map<String, Object>> getStock(@PathVariable String sku) {
        Long stock = stockService.getStock(sku);
        return ResponseEntity.ok(createResponse(sku, stock, "retrieved"));
    }

    private Map<String, Object> createResponse(String sku, Long value, String action) {
        Map<String, Object> response = new HashMap<>();
        response.put("sku", sku);
        response.put("stock", value);
        response.put("action", action);
        return response;
    }
}
