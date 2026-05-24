package com.devops.dashboard.mcp.dto.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("EnvOperationResponse DTO 单元测试")
class EnvOperationResponseTest {

    @Nested
    @DisplayName("Builder 模式")
    class BuilderPattern {

        @Test
        @DisplayName("应该正确构建完整响应")
        void shouldBuildFullResponse() {
            EnvOperationResponse response = EnvOperationResponse.builder()
                    .success(true)
                    .message("OK")
                    .envId("env-001")
                    .envName("test-env")
                    .status("RUNNING")
                    .hostId("vm-ubuntu-test")
                    .isolationType("DOCKER")
                    .accessEndpoints(Map.of("console", "http://localhost:8848"))
                    .timestamp(LocalDateTime.now())
                    .build();

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("OK");
            assertThat(response.getEnvId()).isEqualTo("env-001");
            assertThat(response.getEnvName()).isEqualTo("test-env");
            assertThat(response.getStatus()).isEqualTo("RUNNING");
            assertThat(response.getHostId()).isEqualTo("vm-ubuntu-test");
            assertThat(response.getIsolationType()).isEqualTo("DOCKER");
            assertThat(response.getAccessEndpoints()).hasSize(1);
            assertThat(response.getTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("应该支持构建最小化响应")
        void shouldBuildMinimalResponse() {
            EnvOperationResponse response = EnvOperationResponse.builder()
                    .success(false)
                    .message("Failed")
                    .build();

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getMessage()).isEqualTo("Failed");
            assertThat(response.getEnvId()).isNull();
            assertThat(response.getServices()).isNull();
        }
    }

    @Nested
    @DisplayName("工厂方法")
    class FactoryMethods {

        @Test
        @DisplayName("success() 应预填 success=true 和 timestamp")
        void successFactoryShouldPreFillFields() {
            EnvOperationResponse.EnvOperationResponseBuilder builder = EnvOperationResponse.success("done");
            EnvOperationResponse response = builder.build();

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("done");
            assertThat(response.getTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("failure() 应预填 success=false 和 timestamp")
        void failureFactoryShouldPreFillFields() {
            EnvOperationResponse.EnvOperationResponseBuilder builder = EnvOperationResponse.failure("error");
            EnvOperationResponse response = builder.build();

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getMessage()).isEqualTo("error");
            assertThat(response.getTimestamp()).isNotNull();
        }
    }

    @Nested
    @DisplayName("ServiceSummary 内部类")
    class ServiceSummaryClass {

        @Test
        @DisplayName("应该正确构建服务摘要")
        void shouldBuildSummary() {
            EnvOperationResponse.ServiceSummary summary = EnvOperationResponse.ServiceSummary.builder()
                    .instanceId("svc-abc123")
                    .serviceName("nacos-server")
                    .status("RUNNING")
                    .build();

            assertThat(summary.getInstanceId()).isEqualTo("svc-abc123");
            assertThat(summary.getServiceName()).isEqualTo("nacos-server");
            assertThat(summary.getStatus()).isEqualTo("RUNNING");
        }
    }
}
