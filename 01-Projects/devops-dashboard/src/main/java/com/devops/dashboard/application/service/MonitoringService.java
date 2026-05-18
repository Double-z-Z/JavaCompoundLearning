package com.devops.dashboard.application.service;

import com.devops.dashboard.infrastructure.provider.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 可观测性服务
 * 提供日志流、指标查询、健康检查等功能
 */
public interface MonitoringService {

    /**
     * 实时流式获取容器日志（WebSocket/Flux）
     *
     * @param containerId 容器ID
     * @param options 日志过滤选项（行数、时间范围、关键词）
     * @return 响应式日志流
     */
    Flux<String> streamLogs(ContainerId containerId, LogOptions options);

    /**
     * 批量查询服务健康状态
     *
     * @param envId 环境ID（可选，null则查全部）
     * @return 各服务的健康快照
     */
    Mono<Map<String, HealthSnapshot>> checkAllHealth(String envId);

    /**
     * 获取服务资源使用情况
     * CPU、内存、网络IO等
     */
    Mono<ResourceUsage> getResourceUsage(String serviceInstanceId);

    /**
     * 获取服务事件历史
     * 启动、停止、崩溃重启等
     */
    Flux<ServiceEvent> getEventHistory(String serviceInstanceId, Duration period);

    // ========== 值对象 ==========

    record HealthSnapshot(
        String instanceId,
        boolean isHealthy,
        String status,
        long responseTimeMs,
        LocalDateTime lastCheckTime
    ) {}

    record ResourceUsage(
        double cpuPercent,
        long memoryMb,
        long memoryLimitMb,
        double networkRxKbps,
        double networkTxKbps,
        LocalDateTime timestamp
    ) {}

    record ServiceEvent(
        String eventType,
        String message,
        LocalDateTime timestamp,
        Map<String, Object> metadata
    ) {}
}
