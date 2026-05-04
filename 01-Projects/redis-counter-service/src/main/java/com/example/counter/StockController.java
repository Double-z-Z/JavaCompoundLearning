package com.example.counter;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 库存 REST API 控制器
 * POST /stock/{sku}/init?quantity=100
 * POST /stock/{sku}/decrement?quantity=1
 * GET  /stock/{sku}
 */
@RestController
@RequestMapping("/stock")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("pong");
    }

    @PostMapping("/{sku}/init")
    public ResponseEntity<Map<String, Object>> initStock(
            @PathVariable String sku,
            @RequestParam long quantity) {
        stockService.initStock(sku, quantity);
        return ResponseEntity.ok(createResponse(sku, quantity, "initialized"));
    }

    @PostMapping("/{sku}/decrement")
    public ResponseEntity<Map<String, Object>> decrement(
            @PathVariable String sku,
            @RequestParam long quantity) {
        Long result = stockService.decrementStock(sku, quantity);
        Map<String, Object> response = new HashMap<>();
        response.put("sku", sku);
        if (result == -1) {
            response.put("status", "insufficient_stock");
            response.put("remaining", -1);
        } else {
            response.put("status", "success");
            response.put("remaining", result);
        }
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