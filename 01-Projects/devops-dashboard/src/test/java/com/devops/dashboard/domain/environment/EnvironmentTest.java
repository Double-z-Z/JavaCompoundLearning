package com.devops.dashboard.domain.environment;

import com.devops.dashboard.domain.environment.valueobject.LifecyclePolicy;
import com.devops.dashboard.domain.environment.valueobject.ResourceQuota;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Environment 实体测试 (V3)")
class EnvironmentTest {

    @Nested
    @DisplayName("创建环境")
    class CreateEnvironment {

        @Test
        @DisplayName("应该成功创建环境并设置初始状态为 CREATING")
        void shouldCreateEnvironmentWithCorrectInitialState() {
            // Given
            String name = "dev-nacos";
            EnvironmentSpec spec = createDefaultSpec();

            // When
            Environment env = Environment.create(name, spec);

            // Then
            assertThat(env).isNotNull();
            assertThat(env.getName()).isEqualTo(name);
            assertThat(env.getType()).isEqualTo(EnvironmentType.DEV);
            assertThat(env.getStatus()).isEqualTo(EnvironmentStatus.CREATING);
            assertThat(env.getCreatedAt()).isBeforeOrEqualTo(LocalDateTime.now());
            assertThat(env.getId()).isNotNull();
            assertThat(env.getId().getValue()).startsWith("env-");
        }

        @Test
        @DisplayName("应该使用提供的资源配额")
        void shouldUseProvidedResourceQuota() {
            // Given
            ResourceQuota customQuota = ResourceQuota.builder()
                    .cpuRequest("1000m")
                    .cpuLimit("4000m")
                    .memoryRequest("2Gi")
                    .memoryLimit("8Gi")
                    .build();

            EnvironmentSpec spec = EnvironmentSpec.builder()
                    .type(EnvironmentType.TEST)
                    .resourceQuota(customQuota)
                    .lifecyclePolicy(LifecyclePolicy.defaultForDev())
                    .build();

            // When
            Environment env = Environment.create("test-custom", spec);

            // Then
            assertThat(env.getResourceQuota()).isEqualTo(customQuota);
            assertThat(env.getResourceQuota().getCpuRequest()).isEqualTo("1000m");
        }

        @Test
        @DisplayName("当资源配额为 null 时应使用默认值")
        void shouldUseDefaultResourceQuotaWhenNull() {
            // Given
            EnvironmentSpec spec = EnvironmentSpec.builder()
                    .type(EnvironmentType.DEV)
                    .resourceQuota(null)
                    .lifecyclePolicy(null)
                    .build();

            // When
            Environment env = Environment.create("dev-test", spec);

            // Then
            assertThat(env.getResourceQuota()).isNotNull();
            assertThat(env.getLifecyclePolicy()).isNotNull();
        }

        @Test
        @DisplayName("当 targetNodes 为 null 时不应抛异常")
        void shouldHandleNullTargetNodes() {
            // Given
            EnvironmentSpec spec = EnvironmentSpec.builder()
                    .type(EnvironmentType.DEV)
                    .targetNodes(null)  // 显式传入 null
                    .build();

            // When & Then
            assertThatCode(() -> Environment.create("dev-null-nodes", spec))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("状态转换 (V3)")
    class StateTransitions {

        @Test
        @DisplayName("CREATING -> READY 应该成功")
        void shouldTransitionFromCreatingToReady() {
            // Given
            Environment env = createEnvironmentInStatus(EnvironmentStatus.CREATING);

            // When
            env.markAsReady();

            // Then
            assertThat(env.getStatus()).isEqualTo(EnvironmentStatus.READY);
        }

        @Test
        @DisplayName("READY -> DEPLOYING 应该成功")
        void shouldTransitionFromReadyToDeploying() {
            // Given
            Environment env = createEnvironmentInStatus(EnvironmentStatus.READY);

            // When
            env.markAsDeploying();

            // Then
            assertThat(env.getStatus()).isEqualTo(EnvironmentStatus.DEPLOYING);
        }

        @Test
        @DisplayName("DEPLOYING -> RUNNING 应该成功")
        void shouldTransitionFromDeployingToRunning() {
            // Given
            Environment env = createEnvironmentInStatus(EnvironmentStatus.DEPLOYING);

            // When
            env.markAsRunning(Map.of("app", "http://localhost:8080"));

            // Then
            assertThat(env.getStatus()).isEqualTo(EnvironmentStatus.RUNNING);
        }

        @Test
        @DisplayName("CREATING -> ERROR 应该成功")
        void shouldTransitionFromCreatingToError() {
            // Given
            Environment env = createEnvironmentInStatus(EnvironmentStatus.CREATING);

            // When
            env.markAsError();

            // Then
            assertThat(env.getStatus()).isEqualTo(EnvironmentStatus.ERROR);
        }

        @Test
        @DisplayName("ERROR -> READY (修复后) 应该成功")
        void shouldTransitionFromErrorToReady() {
            // Given
            Environment env = createEnvironmentInStatus(EnvironmentStatus.ERROR);

            // When
            env.markAsReadyFromError();

            // Then
            assertThat(env.getStatus()).isEqualTo(EnvironmentStatus.READY);
        }

        @Test
        @DisplayName("RUNNING -> DESTROYED 应该成功")
        void shouldTransitionFromRunningToDestroyed() {
            // Given
            Environment env = createEnvironmentInStatus(EnvironmentStatus.RUNNING);

            // When
            env.markAsDestroyed();

            // Then
            assertThat(env.getStatus()).isEqualTo(EnvironmentStatus.DESTROYED);
        }

        @Test
        @DisplayName("非法状态转换应该抛出异常")
        void shouldThrowExceptionForInvalidTransition() {
            // Given
            Environment env = createEnvironmentInStatus(EnvironmentStatus.DESTROYED);

            // When & Then
            assertThatThrownBy(() -> env.markAsRunning(Map.of()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("当前状态为 DESTROYED，不允许转换为 RUNNING");
        }

        @Test
        @DisplayName("DESTROYED 是终态，不允许任何转换")
        void destroyedIsTerminalState() {
            // Given
            Environment env = createEnvironmentInStatus(EnvironmentStatus.DESTROYED);

            // Then - 所有操作都应该失败
            assertThatThrownBy(() -> env.markAsRunning(Map.of())).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> env.markAsReady()).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> env.markAsDeploying()).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> env.markAsDestroyed()).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("完整部署流程: CREATING -> READY -> DEPLOYING -> RUNNING")
        void shouldFollowFullDeploymentPath() {
            // Given
            Environment env = createEnvironmentInStatus(EnvironmentStatus.CREATING);

            // When & Then
            env.markAsReady();
            assertThat(env.getStatus()).isEqualTo(EnvironmentStatus.READY);

            env.markAsDeploying();
            assertThat(env.getStatus()).isEqualTo(EnvironmentStatus.DEPLOYING);

            env.markAsRunning(Map.of("app", "http://localhost:8080"));
            assertThat(env.getStatus()).isEqualTo(EnvironmentStatus.RUNNING);
        }
    }

    @Nested
    @DisplayName("服务实例管理")
    class ServiceManagement {

        @Test
        @DisplayName("添加服务实例应该增加计数")
        void shouldAddServiceInstance() {
            // Given
            Environment env = createEnvironmentInStatus(EnvironmentStatus.RUNNING);
            ServiceInstance service = ServiceInstance.create("nacos", "nacos/nacos:v2.3.0");

            // When
            env.addService(service);

            // Then
            assertThat(env.getServices()).hasSize(1);
            assertThat(env.getServices()).contains(service);
        }

        @Test
        @DisplayName("查找服务实例应该返回正确结果")
        void shouldFindServiceByInstanceId() {
            // Given
            Environment env = createEnvironmentInStatus(EnvironmentStatus.RUNNING);
            ServiceInstance service = ServiceInstance.create("mysql", "mysql:8.0");
            env.addService(service);

            // When
            var found = env.findServiceByInstanceId(service.getInstanceId());

            // Then
            assertThat(found).isPresent();
            assertThat(found.get()).isEqualTo(service);
        }

        @Test
        @DisplayName("查找不存在的服务应该返回空")
        void shouldReturnEmptyForNonExistentService() {
            // Given
            Environment env = createEnvironmentInStatus(EnvironmentStatus.RUNNING);

            // When
            var found = env.findServiceByInstanceId("non-existent");

            // Then
            assertThat(found).isEmpty();
        }
    }

    // ==================== 辅助方法 ====================

    private EnvironmentSpec createDefaultSpec() {
        return EnvironmentSpec.builder()
                .type(EnvironmentType.DEV)
                .resourceQuota(ResourceQuota.development())
                .lifecyclePolicy(LifecyclePolicy.defaultForDev())
                .build();
    }

    /**
     * V3 辅助方法：通过正确的状态转换路径创建指定状态的环境
     */
    private Environment createEnvironmentInStatus(EnvironmentStatus status) {
        Environment env = Environment.create("test-env", createDefaultSpec());

        switch (status) {
            case CREATING:
                break; // 默认就是 CREATING
            case READY:
                env.markAsReady();
                break;
            case DEPLOYING:
                env.markAsReady();
                env.markAsDeploying();
                break;
            case RUNNING:
                env.markAsReady();
                env.markAsDeploying();
                env.markAsRunning(Map.of("app", "http://localhost:8080"));
                break;
            case ERROR:
                env.markAsError();
                break;
            case DESTROYED:
                env.markAsReady();
                env.markAsDeploying();
                env.markAsRunning(Map.of());
                env.markAsDestroyed();
                break;
        }

        return env;
    }
}