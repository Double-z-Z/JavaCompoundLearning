package com.devops.dashboard.mcp.handler;

import com.devops.dashboard.application.host.HostService;
import com.devops.dashboard.application.mcp.ServiceRegistry;
import com.devops.dashboard.application.service.EnvironmentService;
import com.devops.dashboard.infrastructure.host.HostHealthCache;
import java.util.List;
import com.devops.dashboard.application.service.ServiceManifest;
import com.devops.dashboard.domain.environment.*;
import com.devops.dashboard.domain.exception.mcp.ServiceNotRegisteredException;
import com.devops.dashboard.mcp.dto.request.EnvCreateRequest;
import com.devops.dashboard.mcp.dto.request.EnvDeployRequest;
import com.devops.dashboard.mcp.dto.response.EnvOperationResponse;
import com.devops.dashboard.mcp.error.McpExceptionTranslator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 环境管理 MCP Handler。
 *
 * <p>实现 Phase 2 的环境管理 Tool 集合，提供环境的创建、部署、查询、销毁等操作接口。
 * 继承 {@link McpHandler} 基类获得统一的异步处理模板和异常翻译能力。</p>
 *
 * <h3>Tool 清单</h3>
 * <table border="1">
 *   <tr><th>Tool 名称</th><th>方法</th><th>说明</th></tr>
 *   <tr><td>{@code env_create}</td><td>{@link #envCreate(EnvCreateRequest)}</td><td>创建新环境</td></tr>
 *   <tr><td>{@code env_deploy_service}</td><td>{@link #envDeployService(EnvDeployRequest)}</td><td>部署服务到环境</td></tr>
 *   <tr><td>{@code env_get_access}</td><td>{@link #envGetAccess(String)}</td><td>获取环境访问端点</td></tr>
 *   <tr><td>{@code env_destroy}</td><td>{@link #envDestroy(String)}</td><td>销毁环境</td></tr>
 *   <tr><td>{@code env_list}</td><td>{@link #envList()}</td><td>列出所有环境</td></tr>
 * </table>
 *
 * <h3>异常处理策略</h3>
 * <p>所有方法统一通过 {@link #handleAsync(reactor.core.publisher.Mono)} 处理异常，
 * 经 {@link McpExceptionTranslator} 翻译为标准 MCP Error 格式，确保：</p>
 * <ul>
 *   <li>HostNotFoundException → HOST_NOT_FOUND (404)</li>
 *   <li>InvalidHostRoleException → INVALID_HOST_ROLE (400)</li>
 *   <li>HostCapabilityMismatchException → HOST_CAPABILITY_MISMATCH (400)</li>
 *   <li>EnvironmentNotFoundException → ENVIRONMENT_NOT_FOUND (404)</li>
 *   <li>其他异常 → UNKNOWN_ERROR (500)</li>
 * </ul>
 *
 * @see EnvironmentService
 * @see McpHandler
 */
@Component
public class EnvironmentHandler extends McpHandler {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentHandler.class);

    private final EnvironmentService environmentService;
    private final ServiceRegistry serviceRegistry;
    private final HostService hostService;
    private final HostHealthCache hostHealthCache;

    public EnvironmentHandler(McpExceptionTranslator errorTranslator,
                              EnvironmentService environmentService,
                              ServiceRegistry serviceRegistry,
                              HostService hostService,
                              HostHealthCache hostHealthCache) {
        super(errorTranslator);
        this.environmentService = environmentService;
        this.serviceRegistry = serviceRegistry;
        this.hostService = hostService;
        this.hostHealthCache = hostHealthCache;
    }

    /**
     * 创建新环境（MCP Tool: {@code env_create}）。
     *
     * <p>通过 {@link EnvironmentService#createFromSpec} 创建环境，自动校验目标主机角色和能力。
     * 异常经 {@link McpExceptionTranslator} 翻译后返回标准化错误。</p>
     *
     * @param request 环境创建请求参数（name/hostId/environmentType/isolationType）
     * @return JSON 格式的操作响应或错误信息
     */
    public reactor.core.publisher.Mono<String> envCreate(EnvCreateRequest request) {
        log.info("MCP Tool [env_create]: name={}, hostId={}, environmentType={}, isolationType={}",
                request.getName(), request.getHostId(), request.getEnvironmentType(), request.getIsolationType());

        return handleAsync(
                environmentService.createFromSpec(request.getName(), buildSpec(request))
                        .map(env -> EnvOperationResponse.success("Environment created successfully")
                                .envId(env.getIdValue())
                                .envName(env.getName())
                                .status(env.getStatus().name())
                                .hostId(env.getHostId())
                                .isolationType(env.getIsolationType() != null ? env.getIsolationType().name() : null)
                                .build()
                        )
        );
    }

    /**
     * 向指定环境部署服务（MCP Tool: {@code env_deploy_service}）。
     *
     * <p>部署前校验：
     * <ul>
     *   <li>serviceName 必须在 {@link ServiceRegistry} 中注册</li>
     *   <li>环境必须存在且状态为 READY/RUNNING</li>
     * </ul>
     *
     * @param request 服务部署请求参数（envId/serviceName/image）
     * @return JSON 格式的操作响应或错误信息
     */
    public reactor.core.publisher.Mono<String> envDeployService(EnvDeployRequest request) {
        log.info("MCP Tool [env_deploy_service]: envId={}, serviceName={}",
                request.getEnvId(), request.getServiceName());

        // V3: 校验 serviceName 是否在白名单中
        if (!serviceRegistry.isRegistered(request.getServiceName())) {
            return handleAsync(reactor.core.publisher.Mono.error(
                    new ServiceNotRegisteredException(
                            request.getServiceName(),
                            serviceRegistry.getAvailableServices()
                    )
            ));
        }

        return handleAsync(
                environmentService.deployService(
                        EnvironmentId.of(request.getEnvId()),
                        ServiceManifest.builder()
                                .serviceName(request.getServiceName())
                                .image(request.getImage())
                                .build()
                ).map(instance -> EnvOperationResponse.success("Service deployed successfully")
                        .envId(request.getEnvId())
                        .services(java.util.List.of(EnvOperationResponse.ServiceSummary.builder()
                                .instanceId(instance.getInstanceId())
                                .serviceName(instance.getServiceTemplate())
                                .status(instance.getStatus().name())
                                .build()))
                        .build()
                )
        );
    }

    /**
     * 获取环境的访问端点（MCP Tool: {@code env_get_access}）。
     *
     * @param envId 环境 ID 字符串
     * @return JSON 格式的操作响应，包含访问端点映射
     */
    public reactor.core.publisher.Mono<String> envGetAccess(String envId) {
        log.info("MCP Tool [env_get_access]: envId={}", envId);

        return handleAsync(
                environmentService.getAccessEndpoints(EnvironmentId.of(envId))
                        .map(endpoints -> EnvOperationResponse.success("Access endpoints retrieved")
                                .envId(envId)
                                .accessEndpoints(endpoints)
                                .build()
                )
        );
    }

    /**
     * 销毁指定环境（MCP Tool: {@code env_destroy}）。
     *
     * <p>此操作不可逆，AI 应在执行前向用户确认。</p>
     *
     * @param envId 要销毁的环境 ID
     * @return JSON 格式的操作响应
     */
    public reactor.core.publisher.Mono<String> envDestroy(String envId) {
        log.warn("MCP Tool [env_destroy]: envId={} (destructive operation)", envId);

        return handleAsync(
                environmentService.destroy(EnvironmentId.of(envId))
                        .then(reactor.core.publisher.Mono.fromCallable(() ->
                                EnvOperationResponse.success("Environment destroyed: " + envId)
                                        .envId(envId)
                                        .status(EnvironmentStatus.DESTROYED.name())
                                        .build()
                        ))
        );
    }

    /**
     * 列出所有环境（MCP Tool: {@code env_list}）。
     *
     * @param statusFilter 可选的状态筛选，为空时默认排除 DESTROYED
     * @return JSON 格式的环境列表摘要响应
     */
    public reactor.core.publisher.Mono<String> envList(List<String> statusFilter) {
        log.debug("MCP Tool [env_list]: statusFilter={}", statusFilter);

        return handleAsync(
                (statusFilter != null && !statusFilter.isEmpty()
                        ? environmentService.findByStatusIn(
                                statusFilter.stream()
                                        .map(EnvironmentStatus::valueOf)
                                        .toList())
                        : environmentService.listAll())
                        .collectList()
                        .map(envs -> {
                            // 获取可用宿主机列表（roles 包含 TARGET 的节点）
                            var availableHosts = hostService.getTopology().getHosts().stream()
                                    .filter(host -> host.isTarget())
                                    .map(host -> {
                                        java.util.Map<String, Object> hostMap = new java.util.LinkedHashMap<>();
                                        hostMap.put("id", host.id());
                                        hostMap.put("label", host.label());
                                        hostMap.put("status", hostHealthCache.get(host.id()).name());
                                        hostMap.put("roles", host.roles());
                                        hostMap.put("capabilities", host.capabilities());
                                        return hostMap;
                                    })
                                    .toList();

                            return EnvOperationResponse.builder()
                                    .success(true)
                                    .message(envs.size() + " environment(s) found")
                                    .environments(envs.stream().map(env -> {
                                        java.util.Map<String, Object> envMap = new java.util.LinkedHashMap<>();
                                        envMap.put("id", env.getIdValue());  // V3 一致：id
                                        envMap.put("name", env.getName());
                                        envMap.put("status", env.getStatus().name());
                                        envMap.put("hostId", env.getHostId());
                                        envMap.put("environmentType", env.getEnvironmentType() != null ? env.getEnvironmentType().name() : null);
                                        envMap.put("isolationType", env.getIsolationType() != null ? env.getIsolationType().name() : null);
                                        envMap.put("services", env.getServices().stream()
                                                .map(s -> s.getServiceTemplate())
                                                .distinct()
                                                .toList());
                                        envMap.put("createdAt", env.getCreatedAt() != null ? env.getCreatedAt().toString() : null);
                                        return envMap;
                                    }).toList())
                                    .availableHosts(availableHosts)
                                    .timestamp(java.time.LocalDateTime.now())
                                    .build();
                        })
        );
    }

    private EnvironmentSpec buildSpec(EnvCreateRequest request) {
        return EnvironmentSpec.builder()
                .environmentType(parseEnvironmentType(request.getEnvironmentType()))
                .hostId(request.getHostId())
                .isolationType(parseIsolationType(request.getIsolationType()))
                .runtimeConstraint(request.getRuntimeConstraint())
                .build();
    }

    private EnvironmentType parseEnvironmentType(String typeStr) {
        if (typeStr == null || typeStr.isBlank()) {
            return EnvironmentType.EXPERIMENT;
        }
        try {
            return EnvironmentType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown environment type: {}, defaulting to EXPERIMENT", typeStr);
            return EnvironmentType.EXPERIMENT;
        }
    }

    private IsolationType parseIsolationType(String isolationStr) {
        if (isolationStr == null || isolationStr.isBlank()) {
            return IsolationType.DOCKER;
        }
        try {
            return IsolationType.valueOf(isolationStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown isolation type: {}, defaulting to DOCKER", isolationStr);
            return IsolationType.DOCKER;
        }
    }
}
