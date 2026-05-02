package com.example.counter;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 计数器 REST API 控制器
 * 提供 HTTP 接口操作计数器
 */
@RestController
@RequestMapping("/counter")
public class CounterController {
    
    private final CounterService counterService;
    
    public CounterController(CounterService counterService) {
        this.counterService = counterService;
    }
    
    /**
     * 计数器自增 1
     * POST /counter/{key}/incr
     */
    @PostMapping("/{key}/incr")
    public ResponseEntity<Map<String, Object>> incr(@PathVariable String key) {
        Long value = counterService.incr(key);
        return ResponseEntity.ok(createResponse(key, value, "incremented"));
    }
    
    /**
     * 计数器自增指定值
     * POST /counter/{key}/incr/{delta}
     */
    @PostMapping("/{key}/incr/{delta}")
    public ResponseEntity<Map<String, Object>> incrBy(
            @PathVariable String key,
            @PathVariable long delta) {
        Long value = counterService.incrBy(key, delta);
        return ResponseEntity.ok(createResponse(key, value, "incremented"));
    }
    
    /**
     * 计数器自减 1
     * POST /counter/{key}/decr
     */
    @PostMapping("/{key}/decr")
    public ResponseEntity<Map<String, Object>> decr(@PathVariable String key) {
        Long value = counterService.decr(key);
        return ResponseEntity.ok(createResponse(key, value, "decremented"));
    }
    
    /**
     * 获取计数器值
     * GET /counter/{key}
     */
    @GetMapping("/{key}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String key) {
        Long value = counterService.get(key);
        return ResponseEntity.ok(createResponse(key, value, "retrieved"));
    }
    
    /**
     * 设置计数器值
     * POST /counter/{key}/set/{value}
     */
    @PostMapping("/{key}/set/{value}")
    public ResponseEntity<Map<String, Object>> set(
            @PathVariable String key,
            @PathVariable long value) {
        counterService.set(key, value);
        return ResponseEntity.ok(createResponse(key, value, "set"));
    }
    
    /**
     * 设置计数器值并指定过期时间
     * POST /counter/{key}/set/{value}?expire=3600
     */
    @PostMapping("/{key}/set/{value}/expire")
    public ResponseEntity<Map<String, Object>> setWithExpire(
            @PathVariable String key,
            @PathVariable long value,
            @RequestParam long seconds) {
        counterService.set(key, value, seconds);
        return ResponseEntity.ok(createResponse(key, value, "set_with_expire"));
    }
    
    /**
     * 删除计数器
     * DELETE /counter/{key}
     */
    @DeleteMapping("/{key}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String key) {
        counterService.delete(key);
        Map<String, Object> response = new HashMap<>();
        response.put("key", key);
        response.put("status", "deleted");
        return ResponseEntity.ok(response);
    }
    
    private Map<String, Object> createResponse(String key, Long value, String action) {
        Map<String, Object> response = new HashMap<>();
        response.put("key", key);
        response.put("value", value);
        response.put("action", action);
        return response;
    }
}
