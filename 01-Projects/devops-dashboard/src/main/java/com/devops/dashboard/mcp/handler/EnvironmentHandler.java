package com.devops.dashboard.mcp.handler;

import com.devops.dashboard.application.service.EnvironmentService;
import com.devops.dashboard.application.service.ServiceManifest;
import com.devops.dashboard.domain.environment.*;
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

    public EnvironmentHandler(McpExceptionTranslator errorTranslator, EnvironmentService environmentService) {
        super(errorTranslator);
        this.environmentService = environmentService;
    }

    /**
     * 创建新环境（MCP Tool: {@code env_create}）。
     *
     * <p>通过 {@link EnvironmentService#createFromSpec} 创建环境，自动校验目标主机角色和能力。
     * 异常经 {@link McpExceptionTranslator} 翻译后返回标准化错误。</p>
     *
     * @param request 环境创建请求参数（name/hostId/type/runtime）
     * @return JSON 格式的操作响应或错误信息
     */
    public reactor.core.publisher.Mono<String> envCreate(EnvCreateRequest request) {
        log.info("MCP Tool [env_create]: name={}, hostId={}, type={}, runtime={}",
                request.getName(), request.getHostId(), request.getType(), request.getRuntime());

        return handleAsync(
                environmentService.createFromSpec(request.getName(), buildSpec(request))
                        .map(env -> EnvOperationResponse.success("Environment created successfully")
                                .envId(env.getIdValue())
                                .envName(env.getName())
                                .status(env.getStatus().name())
                                .hostId(env.getHostId())
                                .runtime(env.getRuntime() != null ? env.getRuntime().name() : null)
                                .build()
                        )
        );
    }

    /**
     * 向指定环境部署服务（MCP Tool: {@code env_deploy_service}）。
     *
     * @param request 服务部署请求参数（envId/templateName/image）
     * @return JSON 格式的操作响应或错误信息
     */
    public reactor.core.publisher.Mono<String> envDeployService(EnvDeployRequest request) {
        log.info("MCP Tool [env_deploy_service]: envId={}, templateName={}",
                request.getEnvId(), request.getTemplateName());

        return handleAsync(
                environmentService.deployService(
                        EnvironmentId.of(request.getEnvId()),
                        ServiceManifest.builder()
                                .templateName(request.getTemplateName())
                                .image(request.getImage())
                                .build()
                ).map(instance -> EnvOperationResponse.success("Service deployed successfully")
                        .envId(request.getEnvId())
                        .services(java.util.List.of(EnvOperationResponse.ServiceSummary.builder()
                                .instanceId(instance.getInstanceId())
                                .templateName(instance.getServiceTemplate())
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
     * @return JSON 格式的环境列表摘要响应
     */
    public reactor.core.publisher.Mono<String> envList() {
        log.debug("MCP Tool [env_list]: listing all environments");

        return handleAsync(
                environmentService.listAll()
                        .collectList()
                        .map(envs -> EnvOperationResponse.builder()
                                .success(true)
                                .message(envs.size() + " environment(s) found")
                                .environments(envs.stream().map(env -> {
                                    java.util.Map<String, Object> envMap = new java.util.LinkedHashMap<>();
                                    envMap.put("envId", env.getIdValue());
                                    envMap.put("name", env.getName());
                                    envMap.put("status", env.getStatus().name());
                                    envMap.put("hostId", env.getHostId());
                                    envMap.put("runtime", env.getRuntime() != null ? env.getRuntime().name() : null);
                                    return envMap;
                                }).toList())
                                .timestamp(java.time.LocalDateTime.now())
                                .build()
                        )
        );
    }

    private EnvironmentSpec buildSpec(EnvCreateRequest request) {
        return EnvironmentSpec.builder()
                .type(parseEnvironmentType(request.getType()))
                .hostId(request.getHostId())
                .runtime(parseRuntimeType(request.getRuntime()))
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

    private RuntimeType parseRuntimeType(String runtimeStr) {
        if (runtimeStr == null || runtimeStr.isBlank()) {
            return RuntimeType.DOCKER;
        }
        try {
            return RuntimeType.valueOf(runtimeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown runtime type: {}, defaulting to DOCKER", runtimeStr);
            return RuntimeType.DOCKER;
        }
    }
}
