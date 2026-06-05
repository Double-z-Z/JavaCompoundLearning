package com.example.order.interfaces.rest;

import com.example.order.migration.MigrationService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v2/migration")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "migration.active", havingValue = "true")
public class MigrationController {

    private final MigrationService migrationService;

    public MigrationController(MigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @PostMapping("/start")
    public Map<String, Object> start() {
        return Map.of("result", migrationService.start());
    }

    @PostMapping("/double-write")
    public Map<String, Object> doubleWrite() {
        return Map.of("result", migrationService.startDoubleWrite());
    }

    @PostMapping("/advance")
    public Map<String, Object> advance(@RequestParam int percent) {
        return Map.of("result", migrationService.advance(percent));
    }

    @PostMapping("/pause")
    public Map<String, Object> pause() {
        return Map.of("result", migrationService.pause());
    }

    @PostMapping("/rollback")
    public Map<String, Object> rollback(@RequestParam int shard) {
        return Map.of("result", migrationService.rollback(shard));
    }

    @GetMapping("/status")
    public String status() {
        return migrationService.status();
    }

    @GetMapping("/read-from-new")
    public Map<String, Object> readFromNew(@RequestParam long userId) {
        return Map.of("userId", userId, "readFromNewShard", migrationService.readFromNewShard(userId));
    }
}
