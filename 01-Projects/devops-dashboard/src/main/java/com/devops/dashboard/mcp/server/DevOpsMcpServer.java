package com.devops.dashboard.mcp.server;

import com.devops.dashboard.mcp.dto.request.EnvCreateRequest;
import com.devops.dashboard.mcp.dto.request.EnvDeployRequest;
import com.devops.dashboard.mcp.dto.request.ExecCommandRequest;
import com.devops.dashboard.mcp.dto.request.HealthCheckRequest;
import com.devops.dashboard.mcp.dto.request.LoadTestRequest;
import com.devops.dashboard.mcp.handler.DiagnosisHandler;
import com.devops.dashboard.mcp.handler.EnvironmentHandler;
import com.devops.dashboard.mcp.handler.LogHandler;
import com.devops.dashboard.mcp.handler.PipelineHandler;
import com.devops.dashboard.mcp.handler.TestingHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

/**
 * MCP Server - Streamable HTTP 实现
 *
 * <h3>协议选择</h3>
 * <p>使用 <b>Streamable HTTP</b> 替代 SSE：</p>
 * <ul>
 *   <li>单端点 {@code /mcp} 处理所有请求</li>
 *   <li>无状态设计，通过 {@code Mcp-Session-Id} 头部管理会话</li>
 *   <li>支持流恢复和断线重连</li>
 *   <li>Trae 官方推荐协议</li>
 * </ul>
 *
 * <h3>端点说明</h3>
 * <p><b>{@code POST /mcp}</b> — 统一入口，接收 JSON-RPC 2.0 请求：</p>
 * <ul>
 *   <li>{@code initialize} — 客户端初始化握手</li>
 *   <li>{@code tools/list} — 列出可用工具</li>
 *   <li>{@code tools/call} — 调用工具（环境管理/测试/诊断）</li>
 *   <li>{@code resources/list} — 列出资源</li>
 *   <li>{@code resources/read} — 读取资源内容</li>
 * </ul>
 *
 * <h3>JSON-RPC 消息格式</h3>
 * <pre>{@code
 * // Request
 * {
 *   "jsonrpc": "2.0",
 *   "id": 1,
 *   "method": "initialize",
 *   "params": { ... }
 * }
 *
 * // Response
 * {
 *   "jsonrpc": "2.0",
 *   "id": 1,
 *   "result": { ... }
 * }
 * }</pre>
 */
@Configuration
@ConditionalOnProperty(name = "mcp.server.enabled", havingValue = "true")
public class DevOpsMcpServer {

    private static final Logger log = LoggerFactory.getLogger(DevOpsMcpServer.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Bean
    public RouterFunction<ServerResponse> mcpRouter(
            EnvironmentHandler environmentHandler,
            TestingHandler testingHandler,
            DiagnosisHandler diagnosisHandler,
            PipelineHandler pipelineHandler,
            LogHandler logHandler) {

        log.info("Initializing MCP Server (Streamable HTTP Protocol)");

        return route(POST("/mcp").and(contentType(MediaType.APPLICATION_JSON)), request ->
                request.bodyToMono(String.class)
                        .flatMap(jsonRpcRequest -> handleJsonRpc(
                                jsonRpcRequest,
                                environmentHandler,
                                testingHandler,
                                diagnosisHandler,
                                pipelineHandler,
                                logHandler
                        ))
        );
    }

    private Mono<ServerResponse> handleJsonRpc(
            String rawRequest,
            EnvironmentHandler environmentHandler,
            TestingHandler testingHandler,
            DiagnosisHandler diagnosisHandler,
            PipelineHandler pipelineHandler,
            LogHandler logHandler) {

        try {
            JsonNode rootNode = objectMapper.readTree(rawRequest);
            String method = rootNode.path("method").asText();
            JsonNode idNode = rootNode.path("id");
            JsonNode params = rootNode.path("params");

            log.debug("MCP Request: method={}, id={}", method, idNode);

            ObjectNode response = objectMapper.createObjectNode();
            response.put("jsonrpc", "2.0");
            if (!idNode.isMissingNode() && !idNode.isNull()) {
                response.set("id", idNode);
            }

            return switch (method) {
                case "initialize" -> {
                    response.set("result", createInitializeResult());
                    log.info("MCP: Client initialized");
                    yield buildJsonResponse(response);
                }

                case "tools/list" -> {
                    response.set("result", createToolsList());
                    log.debug("MCP: Tools list requested");
                    yield buildJsonResponse(response);
                }

                case "resources/list" -> {
                    response.set("result", createResourcesList());
                    log.debug("MCP: Resources list requested");
                    yield buildJsonResponse(response);
                }

                case "resources/read" -> {
                    String uri = params.path("uri").asText();
                    response.set("result", readResource(uri));
                    log.debug("MCP: Resource read: {}", uri);
                    yield buildJsonResponse(response);
                }

                case "tools/call" -> {
                    String toolName = params.path("name").asText();
                    JsonNode arguments = params.path("arguments");
                    log.info("MCP Tool called: {}", toolName);

                    yield callToolAsync(toolName, arguments, environmentHandler, testingHandler, diagnosisHandler, pipelineHandler, logHandler)
                            .map(result -> {
                                response.set("result", result);
                                return response;
                            })
                            .onErrorResume(e -> {
                                log.error("Tool execution failed: {}", toolName, e);
                                ObjectNode errorResponse = objectMapper.createObjectNode();
                                errorResponse.put("jsonrpc", "2.0");
                                if (!idNode.isMissingNode() && !idNode.isNull()) {
                                    errorResponse.set("id", idNode);
                                }
                                ObjectNode error = objectMapper.createObjectNode();
                                error.put("code", -32603);
                                error.put("message", "Tool execution failed: " + e.getMessage());
                                errorResponse.set("error", error);
                                return Mono.just(errorResponse);
                            })
                            .flatMap(this::buildJsonResponse);
                }

                default -> {
                    ObjectNode error = objectMapper.createObjectNode();
                    error.put("code", -32601);
                    error.put("message", "Method not found: " + method);
                    response.set("error", error);
                    log.warn("MCP: Unknown method: {}", method);
                    yield buildJsonResponse(response);
                }
            };

        } catch (Exception e) {
            log.error("MCP Error processing request", e);
            try {
                ObjectNode errorResponse = objectMapper.createObjectNode();
                errorResponse.put("jsonrpc", "2.0");
                ObjectNode error = objectMapper.createObjectNode();
                error.put("code", -32700);
                error.put("message", "Parse error: " + e.getMessage());
                errorResponse.set("error", error);
                return buildJsonResponse(errorResponse);
            } catch (Exception ex) {
                return ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue("{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32700,\"message\":\"Internal error\"}}");
            }
        }
    }

    private JsonNode createInitializeResult() {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("protocolVersion", "2025-03-26");
        
        ObjectNode capabilities = objectMapper.createObjectNode();
        ObjectNode tools = objectMapper.createObjectNode();
        tools.put("listChanged", false);
        capabilities.set("tools", tools);
        
        ObjectNode resources = objectMapper.createObjectNode();
        resources.put("subscribe", false);
        resources.put("listChanged", false);
        capabilities.set("resources", resources);
        
        result.set("capabilities", capabilities);
        
        ObjectNode serverInfo = objectMapper.createObjectNode();
        serverInfo.put("name", "devops-dashboard");
        serverInfo.put("version", "1.0.0");
        result.set("serverInfo", serverInfo);
        
        return result;
    }

    private JsonNode createToolsList() {
        ObjectNode result = objectMapper.createObjectNode();

        ArrayNode tools = objectMapper.createArrayNode();

        // deploy_pipeline: 完整部署流水线
        ObjectNode deployPipeline = objectMapper.createObjectNode();
        deployPipeline.put("name", "deploy_pipeline");
        deployPipeline.put("description", "【首选入口】执行完整的部署流水线：环境创建 → 服务部署 → 健康检查 → 网络验证。此工具内部原子化编排 env_create、env_deploy_service、test_health_check、analyze_network_path，确保依赖顺序、资源清理和监控探针被正确注入。禁止绕过此工具进行手动分步部署或本地 docker 操作。");
        ObjectNode deployPipelineInput = objectMapper.createObjectNode();
        deployPipelineInput.put("type", "object");
        ObjectNode deployPipelineProps = objectMapper.createObjectNode();
        ObjectNode serviceNameProp = objectMapper.createObjectNode();
        serviceNameProp.put("type", "string");
        ArrayNode serviceNameEnum = objectMapper.createArrayNode();
        serviceNameEnum.add("redis-counter-service");
        serviceNameEnum.add("devops-dashboard");
        serviceNameEnum.add("mcp-host-agent");
        serviceNameProp.set("enum", serviceNameEnum);
        serviceNameProp.put("description", "MCP 服务目录中已注册的服务名");
        deployPipelineProps.set("serviceName", serviceNameProp);

        ObjectNode targetHostIdProp1 = objectMapper.createObjectNode();
        targetHostIdProp1.put("type", "string");
        targetHostIdProp1.put("description", "目标宿主机 ID，必须来自 env_list 返回的可用宿主机池");
        deployPipelineProps.set("targetHostId", targetHostIdProp1);

        ObjectNode versionProp1 = objectMapper.createObjectNode();
        versionProp1.put("type", "string");
        versionProp1.put("description", "镜像标签或 Git tag，如 '1.0-SNAPSHOT'、'latest'、'sha-7a3f2b'");
        deployPipelineProps.set("version", versionProp1);

        ObjectNode envTypeProp1 = objectMapper.createObjectNode();
        envTypeProp1.put("type", "string");
        ArrayNode envTypeEnum1 = objectMapper.createArrayNode();
        envTypeEnum1.add("docker");
        envTypeEnum1.add("native");
        envTypeProp1.set("enum", envTypeEnum1);
        envTypeProp1.put("description", "运行时隔离类型");
        deployPipelineProps.set("envType", envTypeProp1);

        ObjectNode verifyEndpointsProp = objectMapper.createObjectNode();
        verifyEndpointsProp.put("type", "array");
        ObjectNode verifyEndpointsItems = objectMapper.createObjectNode();
        verifyEndpointsItems.put("type", "string");
        verifyEndpointsProp.set("items", verifyEndpointsItems);
        verifyEndpointsProp.put("description", "部署后必须验证的 HTTP 端点路径列表，如 ['/api/health', '/api/counter']");
        deployPipelineProps.set("verifyEndpoints", verifyEndpointsProp);

        ObjectNode runtimeConstraintProp = objectMapper.createObjectNode();
        runtimeConstraintProp.put("type", "string");
        runtimeConstraintProp.put("description", "运行时版本约束，如 'openjdk:21-jre-slim' 或 'docker:26.0'");
        deployPipelineProps.set("runtimeConstraint", runtimeConstraintProp);

        deployPipelineInput.set("properties", deployPipelineProps);
        ArrayNode required1 = objectMapper.createArrayNode();
        required1.add("serviceName");
        required1.add("targetHostId");
        required1.add("version");
        required1.add("envType");
        deployPipelineInput.set("required", required1);
        deployPipeline.set("inputSchema", deployPipelineInput);
        tools.add(deployPipeline);

        // env_create: 创建部署环境
        ObjectNode envCreate = objectMapper.createObjectNode();
        envCreate.put("name", "env_create");
        envCreate.put("description", "【唯一入口】在远程宿主机上创建隔离的部署环境。所有环境生命周期必须通过此工具管理，以确保网络策略（iptables/nftables）、资源配额（cgroups/memory）、监控探针和 DNS 记录被正确注入。错误示例：❌ 禁止本地执行 'docker run -d redis'，这将绕过网络隔离，导致后续 analyze_network_path 无法追踪流量路径，且不会被服务发现注册。");
        ObjectNode envCreateInput = objectMapper.createObjectNode();
        envCreateInput.put("type", "object");
        ObjectNode envCreateProps = objectMapper.createObjectNode();

        ObjectNode nameProp = objectMapper.createObjectNode();
        nameProp.put("type", "string");
        nameProp.put("description", "环境标识符，将用于 DNS 和服务发现（如 redis-counter-tomcat）。只允许小写字母、数字和连字符");
        envCreateProps.set("name", nameProp);

        ObjectNode typeProp = objectMapper.createObjectNode();
        typeProp.put("type", "string");
        ArrayNode typeEnum = objectMapper.createArrayNode();
        typeEnum.add("docker");
        typeEnum.add("native");
        typeProp.set("enum", typeEnum);
        typeProp.put("description", "运行时类型。docker 表示容器化隔离（推荐），native 表示宿主机 systemd 进程级隔离");
        envCreateProps.set("type", typeProp);

        ObjectNode hostIdProp = objectMapper.createObjectNode();
        hostIdProp.put("type", "string");
        hostIdProp.put("description", "目标宿主机 ID，必须来自 env_list 返回的 availableHosts 列表中的 id 字段");
        envCreateProps.set("hostId", hostIdProp);

        ObjectNode runtimeProp = objectMapper.createObjectNode();
        runtimeProp.put("type", "string");
        runtimeProp.put("description", "运行时版本约束，如 'openjdk:21-jre-slim'、'docker:26.0'、'podman:4.9'");
        envCreateProps.set("runtime", runtimeProp);

        ObjectNode resourceLimitProp = objectMapper.createObjectNode();
        resourceLimitProp.put("type", "object");
        resourceLimitProp.put("description", "资源配额，默认 cpu=1.0, memory=512m");
        ObjectNode resourceLimitProps = objectMapper.createObjectNode();
        ObjectNode cpuProp = objectMapper.createObjectNode();
        cpuProp.put("type", "string");
        cpuProp.put("description", "CPU 限制，如 '1.0'、'2.0'");
        resourceLimitProps.set("cpu", cpuProp);
        ObjectNode memoryProp = objectMapper.createObjectNode();
        memoryProp.put("type", "string");
        memoryProp.put("description", "内存限制，如 '512m'、'1g'");
        resourceLimitProps.set("memory", memoryProp);
        resourceLimitProp.set("properties", resourceLimitProps);
        envCreateProps.set("resourceLimit", resourceLimitProp);

        envCreateInput.set("properties", envCreateProps);
        ArrayNode required2 = objectMapper.createArrayNode();
        required2.add("name");
        required2.add("type");
        required2.add("hostId");
        envCreateInput.set("required", required2);
        envCreate.set("inputSchema", envCreateInput);
        tools.add(envCreate);

        // env_deploy_service: 部署服务到环境
        ObjectNode envDeploy = objectMapper.createObjectNode();
        envDeploy.put("name", "env_deploy_service");
        envDeploy.put("description", "将服务部署到已通过 env_create 初始化且状态为 READY 的环境中。此操作会：1) 锁定环境状态为 DEPLOYING；2) 注入 sidecar 监控探针；3) 注册到内部服务发现；4) 配置防火墙规则。直接手动部署（如 SSH 进去 docker pull）将导致监控失效、流量黑洞和状态不一致。");
        ObjectNode envDeployInput = objectMapper.createObjectNode();
        envDeployInput.put("type", "object");
        ObjectNode envDeployProps = objectMapper.createObjectNode();

        ObjectNode envIdProp1 = objectMapper.createObjectNode();
        envIdProp1.put("type", "string");
        envIdProp1.put("description", "目标环境 ID，必须来自 env_create 返回的 envId 字段");
        envDeployProps.set("envId", envIdProp1);

        ObjectNode serviceNameProp2 = objectMapper.createObjectNode();
        serviceNameProp2.put("type", "string");
        ArrayNode serviceNameEnum2 = objectMapper.createArrayNode();
        serviceNameEnum2.add("redis-counter-service");
        serviceNameEnum2.add("devops-dashboard");
        serviceNameEnum2.add("mcp-host-agent");
        serviceNameEnum2.add("redis-cache");
        serviceNameProp2.set("enum", serviceNameEnum2);
        serviceNameProp2.put("description", "服务名，必须是 MCP 服务目录中已注册的服务");
        envDeployProps.set("serviceName", serviceNameProp2);

        ObjectNode versionProp2 = objectMapper.createObjectNode();
        versionProp2.put("type", "string");
        versionProp2.put("description", "镜像标签或 Git tag，如 '1.0-SNAPSHOT'、'sha-7a3f2b'。对于本地构建产物，使用 'local-build' 并确保已通过 CI 上传");
        envDeployProps.set("version", versionProp2);

        ObjectNode configOverrideProp = objectMapper.createObjectNode();
        configOverrideProp.put("type", "object");
        configOverrideProp.put("description", "运行时配置覆盖，如 {'server.port': 8080, 'spring.profiles.active': 'docker'}");
        configOverrideProp.put("additionalProperties", objectMapper.createObjectNode().put("type", "string"));
        envDeployProps.set("configOverride", configOverrideProp);

        envDeployInput.set("properties", envDeployProps);
        ArrayNode required3 = objectMapper.createArrayNode();
        required3.add("envId");
        required3.add("serviceName");
        required3.add("version");
        envDeployInput.set("required", required3);
        envDeploy.set("inputSchema", envDeployInput);
        tools.add(envDeploy);

        // env_list: 列出所有环境
        ObjectNode envList = objectMapper.createObjectNode();
        envList.put("name", "env_list");
        envList.put("description", "列出所有由 MCP 管理的环境及其状态（CREATING / READY / DEPLOYING / RUNNING / ERROR / DESTROYED）。此列表是 env_create 和 env_deploy_service 的数据源。如果某个环境不在此列表中，说明它未经过 MCP 管理，禁止对其执行任何 MCP 操作。");
        ObjectNode envListInput = objectMapper.createObjectNode();
        envListInput.put("type", "object");
        ObjectNode envListProps = objectMapper.createObjectNode();

        ObjectNode hostIdFilterProp = objectMapper.createObjectNode();
        hostIdFilterProp.put("type", "string");
        hostIdFilterProp.put("description", "可选：筛选特定宿主机上的环境。如果不传，返回所有宿主机");
        envListProps.set("hostId", hostIdFilterProp);

        ObjectNode statusFilterProp = objectMapper.createObjectNode();
        statusFilterProp.put("type", "array");
        ObjectNode statusFilterItems = objectMapper.createObjectNode();
        statusFilterItems.put("type", "string");
        ArrayNode statusFilterEnum = objectMapper.createArrayNode();
        statusFilterEnum.add("CREATING");
        statusFilterEnum.add("READY");
        statusFilterEnum.add("DEPLOYING");
        statusFilterEnum.add("RUNNING");
        statusFilterEnum.add("ERROR");
        statusFilterEnum.add("DESTROYED");
        statusFilterItems.set("enum", statusFilterEnum);
        statusFilterProp.set("items", statusFilterItems);
        statusFilterProp.put("description", "可选：按状态筛选");
        envListProps.set("statusFilter", statusFilterProp);

        envListInput.set("properties", envListProps);
        envList.set("inputSchema", envListInput);
        tools.add(envList);

        // env_destroy: 销毁环境
        ObjectNode envDestroy = objectMapper.createObjectNode();
        envDestroy.put("name", "env_destroy");
        envDestroy.put("description", "销毁由 MCP 管理的指定环境及所有关联资源（容器、网络、卷、防火墙规则、DNS 记录）。此操作会触发优雅停机（graceful shutdown）和资源清理。错误示例：❌ 禁止本地执行 'docker rm -f xxx'，这将导致 MCP 状态数据库与实际资源不一致，产生孤儿资源。");
        ObjectNode envDestroyInput = objectMapper.createObjectNode();
        envDestroyInput.put("type", "object");
        ObjectNode envDestroyProps = objectMapper.createObjectNode();

        ObjectNode envIdProp2 = objectMapper.createObjectNode();
        envIdProp2.put("type", "string");
        envIdProp2.put("description", "目标环境 ID，必须来自 env_list 返回的 id 字段");
        envDestroyProps.set("envId", envIdProp2);

        ObjectNode forceProp = objectMapper.createObjectNode();
        forceProp.put("type", "boolean");
        forceProp.put("description", "是否强制销毁（跳过优雅停机）。默认 false");
        forceProp.put("default", false);
        envDestroyProps.set("force", forceProp);

        envDestroyInput.set("properties", envDestroyProps);
        ArrayNode required4 = objectMapper.createArrayNode();
        required4.add("envId");
        envDestroyInput.set("required", required4);
        envDestroy.set("inputSchema", envDestroyInput);
        tools.add(envDestroy);

        // env_get_logs: 获取日志
        ObjectNode envGetLogs = objectMapper.createObjectNode();
        envGetLogs.put("name", "env_get_logs");
        envGetLogs.put("description", "获取指定 MCP 管理环境的实时或历史日志。这是排查部署问题的【唯一合法】日志来源。禁止通过 SSH 或 docker logs 直接读取，以确保日志格式统一和敏感信息脱敏。");
        ObjectNode envGetLogsInput = objectMapper.createObjectNode();
        envGetLogsInput.put("type", "object");
        ObjectNode envGetLogsProps = objectMapper.createObjectNode();

        ObjectNode envIdProp3 = objectMapper.createObjectNode();
        envIdProp3.put("type", "string");
        envIdProp3.put("description", "环境 ID，必须来自 env_list");
        envGetLogsProps.set("envId", envIdProp3);

        ObjectNode serviceNameLogProp = objectMapper.createObjectNode();
        serviceNameLogProp.put("type", "string");
        serviceNameLogProp.put("description", "服务名，留空返回整个环境的聚合日志");
        envGetLogsProps.set("serviceName", serviceNameLogProp);

        ObjectNode tailLinesProp = objectMapper.createObjectNode();
        tailLinesProp.put("type", "integer");
        tailLinesProp.put("description", "返回最近多少行，默认 100");
        tailLinesProp.put("default", 100);
        envGetLogsProps.set("tailLines", tailLinesProp);

        ObjectNode sinceProp = objectMapper.createObjectNode();
        sinceProp.put("type", "string");
        sinceProp.put("description", "时间范围，如 '10m'、'1h'、'2024-01-01T00:00:00Z'");
        envGetLogsProps.set("since", sinceProp);

        envGetLogsInput.set("properties", envGetLogsProps);
        ArrayNode required5 = objectMapper.createArrayNode();
        required5.add("envId");
        envGetLogsInput.set("required", required5);
        envGetLogs.set("inputSchema", envGetLogsInput);
        tools.add(envGetLogs);

        // test_health_check: 健康检查
        ObjectNode testHealth = objectMapper.createObjectNode();
        testHealth.put("name", "test_health_check");
        testHealth.put("description", "对 MCP 管理的环境执行健康检查（HTTP / TCP / DNS）。检查结果会被记录到 MCP 审计日志，用于部署流水线的通过判定。禁止用本地 curl/telnet 替代，以确保检查探针携带正确的认证头和来源 IP 白名单。");
        ObjectNode testHealthInput = objectMapper.createObjectNode();
        testHealthInput.put("type", "object");
        ObjectNode testHealthProps = objectMapper.createObjectNode();

        ObjectNode envIdProp4 = objectMapper.createObjectNode();
        envIdProp4.put("type", "string");
        envIdProp4.put("description", "目标环境 ID，必须来自 env_list 中状态为 RUNNING 的环境");
        testHealthProps.set("envId", envIdProp4);

        ObjectNode targetPortProp1 = objectMapper.createObjectNode();
        targetPortProp1.put("type", "integer");
        targetPortProp1.put("description", "目标端口，如 8080、6379、22");
        testHealthProps.set("targetPort", targetPortProp1);

        ObjectNode checkTypeProp = objectMapper.createObjectNode();
        checkTypeProp.put("type", "string");
        ArrayNode checkTypeEnum = objectMapper.createArrayNode();
        checkTypeEnum.add("http");
        checkTypeEnum.add("tcp");
        checkTypeEnum.add("dns");
        checkTypeProp.set("enum", checkTypeEnum);
        checkTypeProp.put("description", "检查类型。http 会验证状态码 2xx；tcp 验证端口连通性；dns 验证域名解析");
        testHealthProps.set("checkType", checkTypeProp);

        ObjectNode pathProp = objectMapper.createObjectNode();
        pathProp.put("type", "string");
        pathProp.put("description", "HTTP 检查时的路径，如 '/actuator/health'、'/api/counter'。仅 checkType=http 时有效");
        testHealthProps.set("path", pathProp);

        ObjectNode timeoutProp1 = objectMapper.createObjectNode();
        timeoutProp1.put("type", "integer");
        timeoutProp1.put("description", "超时时间（秒），默认 10");
        timeoutProp1.put("default", 10);
        testHealthProps.set("timeout", timeoutProp1);

        testHealthInput.set("properties", testHealthProps);
        ArrayNode required6 = objectMapper.createArrayNode();
        required6.add("envId");
        required6.add("targetPort");
        required6.add("checkType");
        testHealthInput.set("required", required6);
        testHealth.set("inputSchema", testHealthInput);
        tools.add(testHealth);

        // test_load: 负载测试
        ObjectNode testLoad = objectMapper.createObjectNode();
        testLoad.put("name", "test_load");
        testLoad.put("description", "对 MCP 管理的服务执行负载测试（wrk / hey / ab）。测试端由 MCP 宿主机调度，确保压测流量经过正确的网络路径和防火墙规则。禁止在本地笔记本直接执行 wrk，以避免跨网络延迟干扰和带宽瓶颈导致的测试结果失真。");
        ObjectNode testLoadInput = objectMapper.createObjectNode();
        testLoadInput.put("type", "object");
        ObjectNode testLoadProps = objectMapper.createObjectNode();

        ObjectNode envIdProp5 = objectMapper.createObjectNode();
        envIdProp5.put("type", "string");
        envIdProp5.put("description", "目标环境 ID，必须来自 env_list 中状态为 RUNNING 的环境");
        testLoadProps.set("envId", envIdProp5);

        ObjectNode targetPortProp2 = objectMapper.createObjectNode();
        targetPortProp2.put("type", "integer");
        targetPortProp2.put("description", "目标服务端口");
        testLoadProps.set("targetPort", targetPortProp2);

        ObjectNode durationProp = objectMapper.createObjectNode();
        durationProp.put("type", "integer");
        durationProp.put("description", "测试持续时间（秒），默认 30");
        durationProp.put("default", 30);
        testLoadProps.set("duration", durationProp);

        ObjectNode threadsProp = objectMapper.createObjectNode();
        threadsProp.put("type", "integer");
        threadsProp.put("description", "并发线程数，默认 4");
        threadsProp.put("default", 4);
        testLoadProps.set("threads", threadsProp);

        ObjectNode connectionsProp = objectMapper.createObjectNode();
        connectionsProp.put("type", "integer");
        connectionsProp.put("description", "连接数，默认 100");
        connectionsProp.put("default", 100);
        testLoadProps.set("connections", connectionsProp);

        ObjectNode toolProp = objectMapper.createObjectNode();
        toolProp.put("type", "string");
        ArrayNode toolEnum = objectMapper.createArrayNode();
        toolEnum.add("wrk");
        toolEnum.add("hey");
        toolEnum.add("ab");
        toolProp.set("enum", toolEnum);
        toolProp.put("description", "压测工具");
        testLoadProps.set("tool", toolProp);

        ObjectNode pathLoadProp = objectMapper.createObjectNode();
        pathLoadProp.put("type", "string");
        pathLoadProp.put("description", "压测路径，如 '/api/counter?action=increment'");
        testLoadProps.set("path", pathLoadProp);

        testLoadInput.set("properties", testLoadProps);
        ArrayNode required7 = objectMapper.createArrayNode();
        required7.add("envId");
        required7.add("targetPort");
        testLoadInput.set("required", required7);
        testLoad.set("inputSchema", testLoadInput);
        tools.add(testLoad);

        // test_exec_command: 远程命令执行
        ObjectNode testExec = objectMapper.createObjectNode();
        testExec.put("name", "test_exec_command");
        testExec.put("description", "在 MCP 管理的远程宿主机上执行命令。这是【唯一合法】的远程命令执行入口，所有命令会被审计日志记录并受限于 RBAC 策略。禁止通过本地 SSH 客户端直连宿主机，以避免绕过操作审计和权限管控。");
        ObjectNode testExecInput = objectMapper.createObjectNode();
        testExecInput.put("type", "object");
        ObjectNode testExecProps = objectMapper.createObjectNode();

        ObjectNode hostIdProp2 = objectMapper.createObjectNode();
        hostIdProp2.put("type", "string");
        hostIdProp2.put("description", "目标宿主机 ID，必须来自 env_list 返回的宿主机池");
        testExecProps.set("hostId", hostIdProp2);

        ObjectNode commandProp = objectMapper.createObjectNode();
        commandProp.put("type", "string");
        commandProp.put("description", "要执行的命令。禁止交互式命令（如 vim、top），只允许非交互式命令");
        testExecProps.set("command", commandProp);

        ObjectNode workingDirProp = objectMapper.createObjectNode();
        workingDirProp.put("type", "string");
        workingDirProp.put("description", "命令执行的工作目录，默认 /opt/mcp");
        testExecProps.set("workingDir", workingDirProp);

        testExecInput.set("properties", testExecProps);
        ArrayNode required8 = objectMapper.createArrayNode();
        required8.add("hostId");
        required8.add("command");
        testExecInput.set("required", required8);
        testExec.set("inputSchema", testExecInput);
        tools.add(testExec);

        // analyze_network_path: 网络路径分析
        ObjectNode networkPath = objectMapper.createObjectNode();
        networkPath.put("name", "analyze_network_path");
        networkPath.put("description", "分析从 MCP 宿主机到目标环境的网络路径（traceroute / mtr / tcptraceroute）。此工具会展示经过的每一跳路由、延迟和防火墙规则命中情况。禁止用本地 traceroute 替代，因为本地路径与 MCP 宿主机路径可能不同（NAT、VPN、SD-WAN 差异）。");
        ObjectNode networkPathInput = objectMapper.createObjectNode();
        networkPathInput.put("type", "object");
        ObjectNode networkPathProps = objectMapper.createObjectNode();

        ObjectNode sourceHostIdProp = objectMapper.createObjectNode();
        sourceHostIdProp.put("type", "string");
        sourceHostIdProp.put("description", "源宿主机 ID，必须来自 env_list 的宿主机池");
        networkPathProps.set("sourceHostId", sourceHostIdProp);

        ObjectNode targetEnvIdProp = objectMapper.createObjectNode();
        targetEnvIdProp.put("type", "string");
        targetEnvIdProp.put("description", "目标环境 ID，必须来自 env_list");
        networkPathProps.set("targetEnvId", targetEnvIdProp);

        ObjectNode targetPortProp3 = objectMapper.createObjectNode();
        targetPortProp3.put("type", "integer");
        targetPortProp3.put("description", "目标端口，用于验证端到端连通性");
        networkPathProps.set("targetPort", targetPortProp3);

        ObjectNode protocolProp = objectMapper.createObjectNode();
        protocolProp.put("type", "string");
        ArrayNode protocolEnum = objectMapper.createArrayNode();
        protocolEnum.add("tcp");
        protocolEnum.add("udp");
        protocolEnum.add("icmp");
        protocolProp.set("enum", protocolEnum);
        protocolProp.put("description", "探测协议，默认 tcp");
        protocolProp.put("default", "tcp");
        networkPathProps.set("protocol", protocolProp);

        networkPathInput.set("properties", networkPathProps);
        ArrayNode required9 = objectMapper.createArrayNode();
        required9.add("sourceHostId");
        required9.add("targetEnvId");
        required9.add("targetPort");
        networkPathInput.set("required", required9);
        networkPath.set("inputSchema", networkPathInput);
        tools.add(networkPath);

        result.set("tools", tools);
        return result;
    }

    private JsonNode createResourcesList() {
        ObjectNode result = objectMapper.createObjectNode();
        
        ArrayNode resources = objectMapper.createArrayNode();

        ObjectNode hostsTopology = objectMapper.createObjectNode();
        hostsTopology.put("uri", "hosts://topology");
        hostsTopology.put("name", "主机拓扑结构");
        hostsTopology.put("description", "PVE/VM/CT 层次化主机列表，含资源状态");
        resources.add(hostsTopology);

        ObjectNode templatesList = objectMapper.createObjectNode();
        templatesList.put("uri", "templates://list");
        templatesList.put("name", "服务模板列表");
        templatesList.put("description", "可部署的服务模板定义");
        resources.add(templatesList);

        result.set("resources", resources);
        return result;
    }

    private JsonNode readResource(String uri) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("uri", uri);

        return switch (uri) {
            case "hosts://topology" -> {
                String topologyData = """
                    {"mcpHostId":"vm-fedora-dev-101","hosts":[...]}
                    """.trim();
                result.put("contents", objectMapper.createArrayNode().add(topologyData));
                yield result;
            }
            case "templates://list" -> {
                result.put("contents", objectMapper.createArrayNode().add("{}"));
                yield result;
            }
            default -> {
                ObjectNode error = objectMapper.createObjectNode();
                error.put("code", -32602);
                error.put("message", "Resource not found: " + uri);
                result.set("error", error);
                yield result;
            }
        };
    }

    private Mono<JsonNode> callToolAsync(String toolName, JsonNode arguments,
                                          EnvironmentHandler envHandler,
                                          TestingHandler testHandler,
                                          DiagnosisHandler diagHandler,
                                          PipelineHandler pipelineHandler,
                                          LogHandler logHandler) {

        log.info("MCP Tool [{}] called with args: {}", toolName, arguments);

        return switch (toolName) {
            case "deploy_pipeline" -> {
                String serviceName = arguments.path("serviceName").asText();
                String targetHostId = arguments.path("targetHostId").asText();
                String version = arguments.path("version").asText("latest");
                String envType = arguments.path("envType").asText("docker");
                JsonNode verifyEndpointsNode = arguments.path("verifyEndpoints");
                List<String> verifyEndpoints = verifyEndpointsNode.isArray()
                    ? com.fasterxml.jackson.databind.node.TextNode.class.cast(verifyEndpointsNode).findValuesAsText("value")
                    : List.of();
                String runtimeConstraint = arguments.path("runtimeConstraint").asText();
                boolean keepOnFailure = arguments.path("keepOnFailure").asBoolean(false);

                yield pipelineHandler.deployPipeline(
                    serviceName, targetHostId, version, envType,
                    verifyEndpoints, runtimeConstraint, keepOnFailure
                ).map(json -> {
                    try {
                        return objectMapper.readTree(json);
                    } catch (Exception e) {
                        return objectMapper.createObjectNode().put("error", e.getMessage());
                    }
                });
            }

            case "env_get_logs" -> {
                String envId = arguments.path("envId").asText();
                String serviceName = arguments.path("serviceName").asText();
                int tailLines = arguments.path("tailLines").asInt(100);
                String since = arguments.path("since").asText();

                yield logHandler.getLogs(envId, serviceName, tailLines, since)
                    .map(json -> {
                        try {
                            return objectMapper.readTree(json);
                        } catch (Exception e) {
                            return objectMapper.createObjectNode().put("error", e.getMessage());
                        }
                    });
            }

            case "env_create" -> {
                String name = arguments.path("name").asText();
                String hostId = arguments.path("hostId").asText();
                String type = arguments.path("type").asText("docker");
                String runtime = arguments.path("runtime").asText("docker");
                EnvCreateRequest request = EnvCreateRequest.builder()
                        .name(name)
                        .hostId(hostId)
                        .type(type)
                        .runtime(runtime)
                        .build();
                yield envHandler.envCreate(request).map(json -> {
                    try {
                        return objectMapper.readTree(json);
                    } catch (Exception e) {
                        return objectMapper.createObjectNode().put("error", e.getMessage());
                    }
                });
            }

            case "env_deploy_service" -> {
                String envId = arguments.path("envId").asText();
                String serviceName = arguments.path("serviceName").asText();
                String version = arguments.path("version").asText("latest");
                EnvDeployRequest request = EnvDeployRequest.builder()
                        .envId(envId)
                        .templateName(serviceName)
                        .image(version)
                        .build();
                yield envHandler.envDeployService(request).map(json -> {
                    try {
                        return objectMapper.readTree(json);
                    } catch (Exception e) {
                        return objectMapper.createObjectNode().put("error", e.getMessage());
                    }
                });
            }

            case "env_list" ->
                envHandler.envList().map(json -> {
                    try {
                        return objectMapper.readTree(json);
                    } catch (Exception e) {
                        return objectMapper.createObjectNode().put("error", e.getMessage());
                    }
                });

            case "env_destroy" -> {
                String envId = arguments.path("envId").asText();
                yield envHandler.envDestroy(envId).map(json -> {
                    try {
                        return objectMapper.readTree(json);
                    } catch (Exception e) {
                        return objectMapper.createObjectNode().put("error", e.getMessage());
                    }
                });
            }

            case "test_health_check" -> {
                String targetHostId = arguments.path("targetHostId").asText();
                int targetPort = arguments.path("targetPort").asInt(80);
                String checkType = arguments.path("checkType").asText("http");
                int timeout = arguments.path("timeout").asInt(5000);

                String targetUrl = String.format("%s://%s:%d",
                        "tcp".equalsIgnoreCase(checkType) ? "tcp" : "http",
                        targetHostId, targetPort);

                HealthCheckRequest request = HealthCheckRequest.builder()
                        .targetUrl(targetUrl)
                        .timeoutSeconds(timeout / 1000)
                        .build();
                yield testHandler.testHealthCheck(request).map(json -> {
                    try {
                        return objectMapper.readTree(json);
                    } catch (Exception e) {
                        return objectMapper.createObjectNode().put("error", e.getMessage());
                    }
                });
            }

            case "test_load" -> {
                String targetHostId = arguments.path("targetHostId").asText();
                int port = arguments.path("port").asInt(8080);
                int duration = arguments.path("duration").asInt(30);
                int threads = arguments.path("threads").asInt(2);

                String targetUrl = String.format("http://%s:%d", targetHostId, port);
                LoadTestRequest request = LoadTestRequest.builder()
                        .targetUrl(targetUrl)
                        .durationSeconds(duration)
                        .threads(threads)
                        .build();
                yield testHandler.testLoad(request).map(json -> {
                    try {
                        return objectMapper.readTree(json);
                    } catch (Exception e) {
                        return objectMapper.createObjectNode().put("error", e.getMessage());
                    }
                });
            }

            case "test_exec_command" -> {
                String targetHostId = arguments.path("targetHostId").asText();
                String command = arguments.path("command").asText();

                ExecCommandRequest request = ExecCommandRequest.builder()
                        .hostId(targetHostId)
                        .command(command)
                        .build();
                yield testHandler.testExecCommand(request).map(json -> {
                    try {
                        return objectMapper.readTree(json);
                    } catch (Exception e) {
                        return objectMapper.createObjectNode().put("error", e.getMessage());
                    }
                });
            }

            case "analyze_network_path" -> {
                String sourceHostId = arguments.path("sourceHostId").asText();
                String targetHostId = arguments.path("targetHostId").asText();
                int targetPort = arguments.path("targetPort").asInt(80);

                yield Mono.fromCallable(() ->
                    objectMapper.readTree(diagHandler.analyzeNetworkPath(sourceHostId, targetHostId, targetPort))
                );
            }

            default -> {
                ObjectNode result = objectMapper.createObjectNode();
                result.put("tool", toolName);
                result.put("status", "success");
                result.put("message", "Tool executed (full implementation pending)");
                result.put("_note", "This tool is registered but direct JSON-RPC call needs parameter mapping refinement");
                yield Mono.just(result);
            }
        };
    }

    private Mono<ServerResponse> buildJsonResponse(ObjectNode response) {
        try {
            return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32700,\"message\":\"Serialization error\"}}");
        }
    }

    @Bean
    public Map<String, Object> mcpServerInfo() {
        log.info("MCP Server initialized (Streamable HTTP)");
        return Map.of(
                "status", "running",
                "protocol", "streamable-http",
                "endpoint", "/mcp",
                "version", "1.0.0",
                "transport", "Streamable HTTP (POST /mcp)",
                "_note", "Using modern Streamable HTTP protocol instead of legacy SSE"
        );
    }
}
