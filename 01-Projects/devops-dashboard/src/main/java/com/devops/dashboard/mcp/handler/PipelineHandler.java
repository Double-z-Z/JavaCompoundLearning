package com.devops.dashboard.mcp.handler;

import com.devops.dashboard.application.host.HostService;
import com.devops.dashboard.application.mcp.ServiceRegistry;
import com.devops.dashboard.application.service.ServiceTemplatesConfig;
import com.devops.dashboard.domain.environment.EnvironmentId;
import com.devops.dashboard.domain.exception.mcp.PreconditionFailedException;
import com.devops.dashboard.domain.exception.mcp.ServiceNotRegisteredException;
import com.devops.dashboard.domain.host.Capability;
import com.devops.dashboard.domain.host.HostAccess;
import com.devops.dashboard.domain.host.HostId;
import com.devops.dashboard.domain.mcp.*;
import com.devops.dashboard.infrastructure.host.HostHealthCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.devops.dashboard.mcp.dto.request.EnvCreateRequest;
import com.devops.dashboard.mcp.dto.request.EnvDeployRequest;
import com.devops.dashboard.mcp.dto.request.HealthCheckRequest;
import com.devops.dashboard.mcp.error.McpExceptionTranslator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 部署流水线 MCP Handler（V3 新增）。
 *
 * <p>实现 {@code deploy_pipeline} Tool，作为 MCP 部署领域的【首选入口】。
 * 内部封装完整部署流水线的原子化编排，采用 Saga 模式实现失败补偿。</p>
 *
 * <h3>执行流程</h3>
 * <pre>{@code
 * 1. env_create      → 创建环境
 * 2. env_deploy_service → 部署服务
 * 3. test_health_check → 健康检查
 * 4. analyze_network_path → 网络验证
 *
 * 任何阶段失败 → 触发补偿（env_destroy）→ COMPENSATED
 * }</pre>
 *
 * <h3>约束</h3>
 * <ul>
 *   <li>入口唯一性：此 Tool 是部署的【首选入口】，AI 不应手动分步调用子工具</li>
 *   <li>原子性：任何子步骤失败 → 整体失败 → 触发补偿清理</li>
 * </ul>
 */
@Component
public class PipelineHandler extends McpHandler {

    private static final Logger log = LoggerFactory.getLogger(PipelineHandler.class);

    private final EnvironmentHandler environmentHandler;
    private final TestingHandler testingHandler;
    private final DiagnosisHandler diagnosisHandler;
    private final ServiceRegistry serviceRegistry;
    private final HostService hostService;
    private final HostHealthCache hostHealthCache;
    private final ServiceTemplatesConfig serviceTemplates;

    public PipelineHandler(
            McpExceptionTranslator errorTranslator,
            EnvironmentHandler environmentHandler,
            TestingHandler testingHandler,
            DiagnosisHandler diagnosisHandler,
            ServiceRegistry serviceRegistry,
            HostService hostService,
            HostHealthCache hostHealthCache,
            ServiceTemplatesConfig serviceTemplates) {
        super(errorTranslator);
        this.environmentHandler = environmentHandler;
        this.testingHandler = testingHandler;
        this.diagnosisHandler = diagnosisHandler;
        this.serviceRegistry = serviceRegistry;
        this.hostService = hostService;
        this.hostHealthCache = hostHealthCache;
        this.serviceTemplates = serviceTemplates;
    }

    /**
     * 执行完整部署流水线（MCP Tool: {@code deploy_pipeline}）。
     *
     * @param serviceName     服务名
     * @param targetHostId    目标宿主机 ID
     * @param version         镜像标签或 Git tag
     * @param envType         运行时隔离类型：docker | native
     * @param verifyEndpoints 部署后验证的 HTTP 端点路径列表
     * @param runtimeConstraint 运行时版本约束
     * @param keepOnFailure   失败时是否保留环境用于排查
     * @return JSON 格式的流水线执行结果
     */
    public Mono<String> deployPipeline(
            String serviceName,
            String targetHostId,
            String version,
            String envType,
            List<String> verifyEndpoints,
            String runtimeConstraint,
            boolean keepOnFailure) {

        log.info("MCP Tool [deploy_pipeline]: service={}, host={}, version={}, envType={}",
                serviceName, targetHostId, version, envType);

        // V3: 校验 serviceName 是否在白名单中
        if (!serviceRegistry.isRegistered(serviceName)) {
            return handleAsync(Mono.error(
                    new ServiceNotRegisteredException(
                            serviceName,
                            serviceRegistry.getAvailableServices()
                    )
            ));
        }

        // V3: fail-fast 前置校验（host 可达性、能力、SSH 连通性）
        try {
            runPrechecks(targetHostId, envType);
        } catch (PreconditionFailedException e) {
            return handleAsync(Mono.error(e));
        }

        DeploySpec spec = new DeploySpec(
                serviceName,
                targetHostId,
                version,
                envType,
                verifyEndpoints,
                runtimeConstraint,
                keepOnFailure
        );

        DeployPipeline pipeline = new DeployPipeline(PipelineId.generate(), spec);

        DeployPipeline.PipelineExecutor executor = new PipelineExecutorImpl(
                environmentHandler,
                testingHandler,
                diagnosisHandler,
                hostService,
                serviceTemplates
        );

        return pipeline.execute(executor)
                .map(result -> {
                    var json = objectMapper.createObjectNode();
                    json.put("pipelineId", result.pipelineId());
                    json.put("status", result.status());
                    if (result.envId() != null) {
                        json.put("envId", result.envId());
                    }
                    json.set("stages", serializeStages(result.stages()));
                    if (result.createdAt() != null) {
                        json.put("createdAt", result.createdAt().toString());
                    }
                    if (result.completedAt() != null) {
                        json.put("completedAt", result.completedAt().toString());
                    }
                    if (result.error() != null) {
                        var errorNode = objectMapper.createObjectNode();
                        errorNode.put("message", result.error().message());
                        errorNode.put("forbidden", "禁止本地执行 docker/ssh/curl 替代");
                        var nextStepsArray = objectMapper.createArrayNode();
                        result.error().nextSteps().forEach(nextStepsArray::add);
                        errorNode.set("nextSteps", nextStepsArray);
                        json.set("error", errorNode);
                    }
                    try {
                        return objectMapper.writeValueAsString(json);
                    } catch (Exception e) {
                        return "{\"error\": \"Serialization failed\"}";
                    }
                });
    }

    private void runPrechecks(String targetHostId, String envType) {
        // 1. 主机是否存在且为 TARGET 角色
        try {
            hostService.validateRole(HostId.of(targetHostId), com.devops.dashboard.domain.host.HostRole.TARGET);
        } catch (RuntimeException e) {
            throw new PreconditionFailedException(
                    e.getMessage(),
                    java.util.List.of("env_list")
            );
        }

        // 2. 能力检查：按 envType 校验对应 capability
        Capability requiredCap = "native".equalsIgnoreCase(envType)
                ? Capability.NATIVE
                : Capability.DOCKER;
        try {
            hostService.validateCapability(HostId.of(targetHostId), requiredCap);
        } catch (RuntimeException e) {
            String hostLabel = hostService.getHostLabel(HostId.of(targetHostId));
            String capName = requiredCap == Capability.DOCKER ? "Docker" : "Native";
            var nextSteps = requiredCap == Capability.DOCKER
                    ? java.util.List.of("env_list", "host_install_docker")
                    : java.util.List.of("env_list");
            throw new PreconditionFailedException(
                    "主机 " + hostLabel + " 缺少 " + capName + " 运行时能力。"
                            + (requiredCap == Capability.DOCKER
                                ? "建议：① 使用 host_install_docker 安装 Docker；② 或使用已具备 docker 能力的主机"
                                : "建议：选择具备 native 能力的主机"),
                    nextSteps
            );
        }

        // 3. SSH 连通性快速探测（非阻塞，仅告警）
        var healthStatus = hostHealthCache.get(targetHostId);
        if (healthStatus == com.devops.dashboard.domain.host.HostHealthStatus.UNHEALTHY) {
            throw new PreconditionFailedException(
                    "主机 " + targetHostId + " SSH 不可达，请检查主机是否开机且 SSH 服务正常",
                    java.util.List.of("env_list", "env_get_logs")
            );
        }
    }

    private com.fasterxml.jackson.databind.node.ArrayNode serializeStages(List<PipelineStage> stages) {
        var array = objectMapper.createArrayNode();
        for (PipelineStage stage : stages) {
            var obj = objectMapper.createObjectNode();
            obj.put("name", stage.name());
            obj.put("status", stage.status().name());
            obj.put("output", stage.output() != null ? stage.output() : "");
            array.add(obj);
        }
        return array;
    }

    /**
     * Pipeline 执行器实现：调用底层 MCP Handlers。
     */
    private static class PipelineExecutorImpl implements DeployPipeline.PipelineExecutor {

        private static final ObjectMapper healthMapper = new ObjectMapper();

        private final EnvironmentHandler environmentHandler;
        private final TestingHandler testingHandler;
        private final DiagnosisHandler diagnosisHandler;
        private final HostService hostService;
        private final ServiceTemplatesConfig serviceTemplates;

        PipelineExecutorImpl(
                EnvironmentHandler environmentHandler,
                TestingHandler testingHandler,
                DiagnosisHandler diagnosisHandler,
                HostService hostService,
                ServiceTemplatesConfig serviceTemplates) {
            this.environmentHandler = environmentHandler;
            this.testingHandler = testingHandler;
            this.diagnosisHandler = diagnosisHandler;
            this.hostService = hostService;
            this.serviceTemplates = serviceTemplates;
        }

        @Override
        public Mono<EnvironmentId> createEnv(DeploySpec spec) {
            EnvCreateRequest request = EnvCreateRequest.builder()
                    .name("pipeline-" + System.currentTimeMillis())
                    .hostId(spec.targetHostId())
                    .environmentType("EXPERIMENT")               // 流水线环境统一为实验类型
                    .isolationType(spec.envType())               // envType = docker/native → 隔离类型
                    .runtimeConstraint(spec.runtimeConstraint()) // 运行时版本约束
                    .build();

            return environmentHandler.envCreate(request)
                    .map(json -> {
                        // 从 JSON 响应中提取 envId
                        // 格式: { "success": true, "envId": "env-xxx", ... }
                        String envId = extractEnvId(json);
                        return EnvironmentId.of(envId);
                    });
        }

        @Override
        public Mono<EnvironmentId> deployService(EnvironmentId envId, DeploySpec spec) {
            EnvDeployRequest request = EnvDeployRequest.builder()
                    .envId(envId.value())
                    .serviceName(spec.serviceName())
                    .image(spec.version())
                    .build();

            return environmentHandler.envDeployService(request)
                    .flatMap(json -> {
                        // 防御性检查：如果响应中 services[0].status 为 FAILED，则传播错误
                        try {
                            var root = healthMapper.readTree(json);
                            var services = root.path("services");
                            if (services.isArray() && services.size() > 0) {
                                String status = services.get(0).path("status").asText();
                                if ("FAILED".equals(status)) {
                                    return Mono.error(new RuntimeException("Service deployment failed: " + json));
                                }
                            }
                        } catch (Exception ignored) {
                            // 解析失败不影响主流程
                        }
                        return Mono.just(envId);
                    });
        }

        @Override
        public Mono<String> healthCheck(EnvironmentId envId, DeploySpec spec) {
            if (spec.verifyEndpoints() == null || spec.verifyEndpoints().isEmpty()) {
                return Mono.just("No verify endpoints configured, skipping health check");
            }

            String path = spec.verifyEndpoints().get(0);
            HostAccess access = hostService.getHostAccess(HostId.of(spec.targetHostId()));
            String targetIp = access.getSshHost();

            // 从服务模板查找健康检查端口，未配置则默认 8080
            int port = serviceTemplates.get(spec.serviceName())
                    .map(ServiceTemplatesConfig.TemplateEntry::port)
                    .orElse(8080);

            HealthCheckRequest request = HealthCheckRequest.builder()
                    .targetUrl("http://" + targetIp + ":" + port + path)
                    .timeoutSeconds(10)
                    .build();

            return testingHandler.testHealthCheck(request)
                    .flatMap(json -> {
                        try {
                            var root = healthMapper.readTree(json);
                            if (root.has("healthy") && root.get("healthy").asBoolean()) {
                                return Mono.just("Health check passed: " + json);
                            }
                            String errorMsg = root.has("errorMessage")
                                    ? root.get("errorMessage").asText()
                                    : "statusCode=" + root.path("statusCode").asInt();
                            return Mono.error(new RuntimeException("Health check failed: " + errorMsg));
                        } catch (Exception e) {
                            return Mono.error(new RuntimeException("Failed to parse health check result: " + e.getMessage(), e));
                        }
                    });
        }

        @Override
        public Mono<String> networkVerify(EnvironmentId envId, DeploySpec spec) {
            // 网络验证为可选步骤，失败不影响流水线
            return Mono.just("Network verification: skipped (non-critical)");
        }

        @Override
        public Mono<Void> destroyEnv(EnvironmentId envId) {
            return environmentHandler.envDestroy(envId.value())
                    .then();
        }

        private String extractEnvId(String json) {
            // 简单的 JSON 解析：提取 "envId" 字段值
            try {
                int idx = json.indexOf("\"envId\"");
                if (idx < 0) idx = json.indexOf("'envId'");
                if (idx >= 0) {
                    int start = json.indexOf("\"", idx + 7);
                    int end = json.indexOf("\"", start + 1);
                    return json.substring(start + 1, end);
                }
            } catch (Exception e) {
                log.warn("Failed to extract envId from JSON", e);
            }
            return "unknown";
        }
    }
}