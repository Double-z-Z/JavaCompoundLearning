package com.devops.dashboard.application.service;

import com.devops.dashboard.domain.environment.*;
import com.devops.dashboard.domain.exception.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 环境管理核心服务
 * 负责环境的创建、销毁、服务部署等操作
 */
public interface EnvironmentService {

    /**
     * 从规格说明创建环境
     *
     * @param spec 环境规格（包含服务列表、资源配置等）
     * @return 创建的环境实例（含访问地址等信息）
     * @throws EnvironmentCreationException 创建失败时抛出
     */
    Mono<Environment> createFromSpec(EnvironmentSpec spec);

    /**
     * 销毁指定环境
     * 会停止所有服务并释放资源
     *
     * @param envId 环境ID
     * @throws EnvironmentNotFoundException 环境不存在
     */
    Mono<Void> destroy(EnvironmentId envId);

    /**
     * 向已有环境部署新服务
     *
     * @param envId 目标环境ID
     * @param manifest 服务清单（引用模板+覆盖配置）
     * @return 部署的服务实例
     */
    Mono<ServiceInstance> deployService(EnvironmentId envId, ServiceManifest manifest);

    /**
     * 停止环境中的指定服务
     */
    Mono<Void> stopService(EnvironmentId envId, String instanceId);

    /**
     * 重启服务
     */
    Mono<ServiceInstance> restartService(EnvironmentId envId, String instanceId);

    /**
     * 查询环境状态
     */
    Mono<EnvironmentStatus> getStatus(EnvironmentId envId);

    /**
     * 列出环境中所有服务实例
     */
    Flux<ServiceInstance> listServices(EnvironmentId envId);

    /**
     * 根据ID查找环境
     */
    Mono<Environment> findById(EnvironmentId envId);

    /**
     * 根据状态查找环境列表
     */
    Flux<Environment> findByStatus(EnvironmentStatus status);
}
