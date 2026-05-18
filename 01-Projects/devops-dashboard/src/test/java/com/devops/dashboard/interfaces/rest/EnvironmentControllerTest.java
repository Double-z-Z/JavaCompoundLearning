package com.devops.dashboard.interfaces.rest;

import com.devops.dashboard.application.service.EnvironmentService;
import com.devops.dashboard.domain.environment.*;
import com.devops.dashboard.domain.environment.valueobject.LifecyclePolicy;
import com.devops.dashboard.domain.environment.valueobject.ResourceQuota;
import com.devops.dashboard.domain.exception.EnvironmentNotFoundException;
import com.devops.dashboard.interfaces.dto.CreateEnvironmentRequest;
import com.devops.dashboard.interfaces.dto.EnvironmentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("EnvironmentController 集成测试")
@WebFluxTest(EnvironmentController.class)
class EnvironmentControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private EnvironmentService environmentService;

    private Environment testEnvironment;

    @BeforeEach
    void setUp() {
        testEnvironment = Environment.create("test-env", EnvironmentSpec.builder()
                .type(EnvironmentType.DEV)
                .resourceQuota(ResourceQuota.development())
                .lifecyclePolicy(LifecyclePolicy.defaultForDev())
                .build());
        testEnvironment.markAsRunning(Map.of("app", "http://localhost:8080"));
    }

    @Nested
    @DisplayName("POST /environments - 创建环境")
    class CreateEnvironment {

        @Test
        @DisplayName("应该成功创建环境并返回 201")
        void shouldCreateEnvironmentSuccessfully() {
            // Given
            CreateEnvironmentRequest request = CreateEnvironmentRequest.builder()
                    .name("dev-nacos")
                    .type(EnvironmentType.DEV)
                    .build();

            when(environmentService.createFromSpec(eq("dev-nacos"), any(EnvironmentSpec.class)))
                    .thenReturn(Mono.just(testEnvironment));

            // When & Then
            webTestClient.post()
                    .uri("/environments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody()
                    .jsonPath("$.name").isEqualTo("test-env")
                    .jsonPath("$.type").isEqualTo("DEV")
                    .jsonPath("$.status").isEqualTo("RUNNING");
        }

        @Test
        @DisplayName("缺少必填字段应该返回 400")
        void shouldReturn400WhenMissingRequiredFields() {
            // Given - name 为空
            CreateEnvironmentRequest request = CreateEnvironmentRequest.builder()
                    .name("")  // 空名称
                    .type(EnvironmentType.DEV)
                    .build();

            // When & Then
            webTestClient.post()
                    .uri("/environments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("传入完整配置应该正确映射到 EnvironmentSpec")
        void shouldMapFullConfiguration() {
            // Given
            CreateEnvironmentRequest request = CreateEnvironmentRequest.builder()
                    .name("dev-full")
                    .type(EnvironmentType.EXPERIMENT)
                    .resourceQuota(ResourceQuota.builder()
                            .cpuRequest("1000m")
                            .cpuLimit("4000m")
                            .memoryRequest("2Gi")
                            .memoryLimit("8Gi")
                            .build())
                    .lifecyclePolicy(LifecyclePolicy.builder()
                            .autoDestroy(true)
                            .maxLifetime("2h")
                            .idleTimeout("30m")
                            .destroyOnFailure(true)
                            .build())
                    .targetNodes(java.util.List.of(
                            CreateEnvironmentRequest.TargetNodeDTO.builder()
                                    .nodeId("node-1")
                                    .ip("192.168.1.100")
                                    .role("primary")
                                    .build()
                    ))
                    .build();

            when(environmentService.createFromSpec(eq("dev-full"), any(EnvironmentSpec.class)))
                    .thenReturn(Mono.just(testEnvironment));

            // When & Then
            webTestClient.post()
                    .uri("/environments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isCreated();

            // 验证 Service 被调用时包含正确的 targetNodes
            verify(environmentService).createFromSpec(eq("dev-full"), argThat(spec ->
                    spec.getTargetNodes() != null &&
                    spec.getTargetNodes().size() == 1 &&
                    spec.getTargetNodes().get(0).getNodeId().equals("node-1")
            ));
        }
    }

    @Nested
    @DisplayName("GET /environments - 查询环境列表")
    class ListEnvironments {

        @Test
        @DisplayName("应该返回所有环境列表")
        void shouldReturnAllEnvironments() {
            // Given
            when(environmentService.findByStatus(null))
                    .thenReturn(Flux.just(testEnvironment));

            // When & Then
            webTestClient.get()
                    .uri("/environments")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(EnvironmentResponse.class)
                    .hasSize(1)
                    .value(list -> assertThat(list.get(0).getName()).isEqualTo("test-env"));
        }

        @Test
        @DisplayName("按状态筛选应该只返回匹配的环境")
        void shouldFilterByStatus() {
            // Given
            when(environmentService.findByStatus(EnvironmentStatus.RUNNING))
                    .thenReturn(Flux.just(testEnvironment));

            // When & Then
            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/environments")
                            .queryParam("status", "RUNNING")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(EnvironmentResponse.class)
                    .hasSize(1);
        }

        @Test
        @DisplayName("空列表应该返回空数组")
        void shouldReturnEmptyArrayWhenNoEnvironments() {
            // Given
            when(environmentService.findByStatus(null))
                    .thenReturn(Flux.empty());

            // When & Then
            webTestClient.get()
                    .uri("/environments")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(EnvironmentResponse.class)
                    .hasSize(0);
        }
    }

    @Nested
    @DisplayName("GET /environments/{id} - 查询环境详情")
    class GetEnvironment {

        @Test
        @DisplayName("应该返回环境详情")
        void shouldReturnEnvironmentDetails() {
            // Given
            String envId = "env-test-123";
            when(environmentService.findById(EnvironmentId.of(envId)))
                    .thenReturn(Mono.just(testEnvironment));

            // When & Then
            webTestClient.get()
                    .uri("/environments/{id}", envId)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isNotEmpty()
                    .jsonPath("$.name").isEqualTo("test-env")
                    .jsonPath("$.serviceCount").isNumber();
        }

        @Test
        @DisplayName("查询不存在的环境应该返回 404")
        void shouldReturn404ForNonExistentEnvironment() {
            // Given
            String envId = "env-nonexistent";
            when(environmentService.findById(EnvironmentId.of(envId)))
                    .thenReturn(Mono.error(new EnvironmentNotFoundException(envId)));

            // When & Then
            webTestClient.get()
                    .uri("/environments/{id}", envId)
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }

    @Nested
    @DisplayName("DELETE /environments/{id} - 销毁环境")
    class DestroyEnvironment {

        @Test
        @DisplayName("应该成功销毁环境并返回 204")
        void shouldDestroyEnvironmentSuccessfully() {
            // Given
            String envId = "env-destroy-123";
            when(environmentService.destroy(EnvironmentId.of(envId)))
                    .thenReturn(Mono.empty());

            // When & Then
            webTestClient.delete()
                    .uri("/environments/{id}", envId)
                    .exchange()
                    .expectStatus().isNoContent();
        }

        @Test
        @DisplayName("销毁不存在的环境应该返回 404")
        void shouldReturn404WhenDestroyingNonExistent() {
            // Given
            String envId = "env-missing-456";
            when(environmentService.destroy(EnvironmentId.of(envId)))
                    .thenReturn(Mono.error(new EnvironmentNotFoundException(envId)));

            // When & Then
            webTestClient.delete()
                    .uri("/environments/{id}", envId)
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }
}
