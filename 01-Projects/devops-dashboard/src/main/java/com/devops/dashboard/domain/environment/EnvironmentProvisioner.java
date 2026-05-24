package com.devops.dashboard.domain.environment;

import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 环境基础设施提供者接口
 *
 * 环境与服务的完整生命周期接口：
 * - provision - 创建环境基础设施
 * - deployService - 在环境中部署服务
 * - stopService - 停止已部署的服务
 * - startService - 启动已停止的服务
 * - teardown - 销毁环境及其中所有资源
 * - checkStatus - 查询基础设施状态
 */
public interface EnvironmentProvisioner {

    Mono<Environment> provision(EnvironmentSpec spec);

    Mono<Map<String, String>> deployService(EnvironmentId envId, String serviceName, String image, String instanceId);

    Mono<Void> stopService(EnvironmentId envId, String instanceId);

    Mono<Void> startService(EnvironmentId envId, String instanceId, String image);

    Mono<Void> teardown(EnvironmentId id);

    Mono<EnvironmentStatus> checkStatus(EnvironmentId id);
}