package com.example.counter.controller;

import com.example.counter.service.HotStockCacheService;
import com.example.counter.service.ReactiveStockService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * 库存 REST API 控制器（响应式版本）
 * 使用 @RestController 注解，方法返回 Mono/Flux
 */
@RestController
@RequestMapping("/stock")
public class ReactiveStockController {

    private final ReactiveStockService stockService;
    private final HotStockCacheService hotStockCacheService;

    public ReactiveStockController(ReactiveStockService stockService,
                                   HotStockCacheService hotStockCacheService) {
        this.stockService = stockService;
        this.hotStockCacheService = hotStockCacheService;
    }

    @GetMapping("/ping")
    public Mono<ResponseEntity<String>> ping() {
        return Mono.just(ResponseEntity.ok("pong"));
    }

    @PostMapping("/{sku}/init")
    public Mono<ResponseEntity<String>> initStock(
            @PathVariable String sku,
            @RequestParam long quantity) {
        return stockService.initStock(sku, quantity)
                .then(Mono.just(ResponseEntity.ok("initialized")));
    }

    @PostMapping("/{sku}/decrement")
    public Mono<ResponseEntity<String>> decrement(
            @PathVariable String sku,
            @RequestParam(name = "quantity") long quantity) {
        return stockService.decrementStock(sku, quantity)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/{sku}")
    public Mono<ResponseEntity<String>> getStock(@PathVariable String sku) {
        return stockService.getStock(sku)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/{sku}/init-with-cache")
    public Mono<ResponseEntity<String>> initWithCache(
            @PathVariable String sku,
            @RequestParam long quantity) {
        return hotStockCacheService.initWithCache(sku, quantity)
                .then(Mono.just(ResponseEntity.ok("initialized with cache")));
    }

    @GetMapping("/{sku}/cached")
    public Mono<ResponseEntity<String>> getStockCached(@PathVariable String sku) {
        return hotStockCacheService.getStockWithCache(sku)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/{sku}/decrement-cached")
    public Mono<ResponseEntity<String>> decrementCached(
            @PathVariable String sku,
            @RequestParam(name = "quantity") long quantity) {
        return hotStockCacheService.decrementWithCache(sku, quantity)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{sku}/cache")
    public Mono<ResponseEntity<String>> invalidateCache(@PathVariable String sku) {
        hotStockCacheService.invalidate(sku);
        return Mono.just(ResponseEntity.ok("cache invalidated"));
    }
}