package com.devops.dashboard.mcp.handler;

import com.devops.dashboard.application.service.EnvironmentService;
import com.devops.dashboard.application.service.ServiceManifest;
import com.devops.dashboard.domain.environment.*;
import com.devops.dashboard.domain.exception.environment.EnvironmentNotFoundException;
import com.devops.dashboard.domain.exception.host.HostNotFoundException;
import com.devops.dashboard.mcp.dto.request.EnvCreateRequest;
import com.devops.dashboard.mcp.dto.request.EnvDeployRequest;
import com.devops.dashboard.mcp.dto.response.EnvOperationResponse;
import com.devops.dashboard.mcp.error.McpExceptionTranslator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;

@DisplayName("EnvironmentHandler 单元测试")
@ExtendWith(MockitoExtension.class)
class EnvironmentHandlerTest {

    @Mock
    private EnvironmentService environmentService;

    @Mock
    private McpExceptionTranslator errorTranslator;

    private EnvironmentHandler environmentHandler;

    @BeforeEach
    void setUp() {
        environmentHandler = new EnvironmentHandler(errorTranslator, environmentService);
    }

    @Nested
    @DisplayName("env_create Tool")
    class EnvCreate {

        @Test
        @DisplayName("成功创建环境应返回包含环境信息的成功响应")
        void shouldReturnSuccessOnValidCreate() {
            EnvCreateRequest request = EnvCreateRequest.builder()
                    .name("nacos-test")
                    .hostId("vm-ubuntu-test")
                    .type("EXPERIMENT")
                    .runtime("DOCKER")
                    .build();

            Environment env = createMockEnvironment("nacos-test", "vm-ubuntu-test");
            given(environmentService.createFromSpec(anyString(), any(EnvironmentSpec.class)))
                    .willReturn(reactor.core.publisher.Mono.just(env));

            String result = environmentHandler.envCreate(request).block();

            assertThat(result).contains("success");
            assertThat(result).contains("true");
            assertThat(result).contains("nacos-test");
            assertThat(result).contains("CREATING");
            assertThat(result).contains("vm-ubuntu-test");
        }

        @Test
        @DisplayName("主机不存在时应返回 HOST_NOT_FOUND 错误")
        void shouldReturnErrorWhenHostNotFound() {
            EnvCreateRequest request = EnvCreateRequest.builder()
                    .name("bad-host-test")
                    .hostId("nonexistent")
                    .type("EXPERIMENT")
                    .build();

            given(environmentService.createFromSpec(anyString(), any(EnvironmentSpec.class)))
                    .willReturn(reactor.core.publisher.Mono.error(new HostNotFoundException("nonexistent")));
            given(errorTranslator.translate(any(Exception.class)))
                    .willReturn(new com.devops.dashboard.mcp.error.McpError(
                            "HOST_NOT_FOUND", "Host not found: nonexistent", 404, "主机不存在"));

            String result = environmentHandler.envCreate(request).block();

            assertThat(result).contains("HOST_NOT_FOUND");
        }

        @Test
        @DisplayName("类型为空时应默认为 EXPERIMENT")
        void shouldDefaultToExperimentType() {
            EnvCreateRequest request = EnvCreateRequest.builder()
                    .name("no-type-test")
                    .build();

            given(environmentService.createFromSpec(eq("no-type-test"), any(EnvironmentSpec.class)))
                    .willReturn(reactor.core.publisher.Mono.just(createMockEnvironment("no-type-test", null)));

            environmentHandler.envCreate(request).block();

            then(environmentService).should().createFromSpec(eq("no-type-test"),
                    argThat(spec -> spec.getType() == EnvironmentType.EXPERIMENT));
        }
    }

    @Nested
    @DisplayName("env_deploy_service Tool")
    class EnvDeploy {

        @Test
        @DisplayName("成功部署服务应返回服务实例信息")
        void shouldReturnServiceInfoOnSuccess() {
            EnvDeployRequest request = EnvDeployRequest.builder()
                    .envId("env-deploy-001")
                    .templateName("nacos-server")
                    .image("nacos/nacos-server:v2.3.0")
                    .build();

            ServiceInstance instance = createMockInstance("svc-nacos-001", "nacos-server");
            given(environmentService.deployService(any(EnvironmentId.class), any(ServiceManifest.class)))
                    .willReturn(reactor.core.publisher.Mono.just(instance));

            String result = environmentHandler.envDeployService(request).block();

            assertThat(result).contains("success");
            assertThat(result).contains("env-deploy-001");
            assertThat(result).contains("nacos-server");
            assertThat(result).contains("RUNNING");
        }
    }

    @Nested
    @DisplayName("env_get_access Tool")
    class EnvGetAccess {

        @Test
        @DisplayName("应返回访问端点映射")
        void shouldReturnEndpoints() {
            given(environmentService.getAccessEndpoints(any(EnvironmentId.class)))
                    .willReturn(reactor.core.publisher.Mono.just(Map.of(
                            "console", "http://10.0.0.103:8848/nacos",
                            "api", "http://10.0.0.103:8848/nacos/v1")));

            String result = environmentHandler.envGetAccess("env-access-001").block();

            assertThat(result).contains("success");
            assertThat(result).contains("console");
            assertThat(result).contains("10.0.0.103:8848");
        }
    }

    @Nested
    @DisplayName("env_destroy Tool")
    class EnvDestroy {

        @Test
        @DisplayName("成功销毁应返回 DESTROYED 状态")
        void shouldReturnDestroyedStatus() {
            given(environmentService.destroy(any(EnvironmentId.class)))
                    .willReturn(reactor.core.publisher.Mono.empty());

            String result = environmentHandler.envDestroy("env-destroy-001").block();

            assertThat(result).contains("success");
            assertThat(result).contains("DESTROYED");
        }

        @Test
        @DisplayName("环境不存在时应返回 ENVIRONMENT_NOT_FOUND 错误")
        void shouldReturnErrorWhenEnvNotFound() {
            given(environmentService.destroy(any(EnvironmentId.class)))
                    .willReturn(reactor.core.publisher.Mono.error(new EnvironmentNotFoundException("env-missing")));

            given(errorTranslator.translate(any()))
                    .willReturn(new com.devops.dashboard.mcp.error.McpError(
                            "ENVIRONMENT_NOT_FOUND", "Not found", 404, "环境不存在"));

            String result = environmentHandler.envDestroy("env-missing").block();

            assertThat(result).contains("ENVIRONMENT_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("env_list Tool")
    class EnvList {

        @Test
        @DisplayName("应返回环境数量统计")
        void shouldReturnEnvCount() {
            given(environmentService.listAll())
                    .willReturn(reactor.core.publisher.Flux.fromIterable(java.util.List.of()));

            String result = environmentHandler.envList().block();

            assertThat(result).contains("success");
            assertThat(result).contains("0 environment(s)");
        }
    }

    private Environment createMockEnvironment(String name, String hostId) {
        return Environment.create(name, EnvironmentSpec.builder()
                .type(EnvironmentType.EXPERIMENT)
                .hostId(hostId)
                .runtime(RuntimeType.DOCKER)
                .build());
    }

    private ServiceInstance createMockInstance(String instanceId, String template) {
        ServiceInstance instance = ServiceInstance.create(template, template + ":latest");
        instance.markAsRunning("container-" + instanceId);
        return instance;
    }
}
