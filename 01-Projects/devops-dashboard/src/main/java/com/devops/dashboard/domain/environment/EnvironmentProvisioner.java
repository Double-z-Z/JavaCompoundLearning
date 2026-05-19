package com.devops.dashboard.domain.environment;

import reactor.core.publisher.Mono;

/**
 * 环境基础设施提供者接口
 *
 * 环境聚合根的生命周期接口：
 * - provision - 创建并关联环境
 * - teardown - 销毁环境
 * - checkStatus - 查询基础设施状态
 */
public interface EnvironmentProvisioner {

    Mono<Environment> provision(EnvironmentSpec spec);

    Mono<Void> teardown(EnvironmentId id);

    Mono<EnvironmentStatus> checkStatus(EnvironmentId id);
}