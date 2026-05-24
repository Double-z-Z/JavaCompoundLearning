package com.devops.dashboard.application.service.impl;

import com.devops.dashboard.application.host.HostService;
import com.devops.dashboard.application.service.EnvironmentService;
import com.devops.dashboard.domain.environment.*;
import com.devops.dashboard.domain.environment.valueobject.LifecyclePolicy;
import com.devops.dashboard.domain.environment.valueobject.ResourceQuota;
import com.devops.dashboard.domain.exception.environment.EnvironmentCreationException;
import com.devops.dashboard.domain.exception.environment.EnvironmentNotFoundException;
import com.devops.dashboard.domain.exception.host.HostCapabilityMismatchException;
import com.devops.dashboard.domain.exception.host.HostNotFoundException;
import com.devops.dashboard.domain.exception.host.InvalidHostRoleException;
import com.devops.dashboard.infrastructure.environment.EnvironmentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@DisplayName("EnvironmentServiceImpl 单元测试（全响应式版本）")
@ExtendWith(MockitoExtension.class)
class EnvironmentServiceImplTest {

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private HostService hostService;

    @Mock
    private EnvironmentProvisioner provisioner;

    @InjectMocks
    private EnvironmentServiceImpl environmentService;

    @Nested
    @DisplayName("创建环境（响应式链路）")
    class CreateEnvironment {

        @Test
        @DisplayName("应该通过 Provisioner 创建并持久化环境")
        void shouldCreateViaProvisionerAndSave() {
            String name = "dev-myapp";
            EnvironmentSpec spec = createDefaultSpec();
            Environment provisionedEnv = createMockEnvironment(name);

            given(provisioner.provision(spec))
                    .willReturn(reactor.core.publisher.Mono.just(provisionedEnv));
            when(environmentRepository.save(any(Environment.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            var result = environmentService.createFromSpec(name, spec).block();

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo(name);
            assertThat(result.getStatus()).isEqualTo(EnvironmentStatus.READY);  // V3: markAsReadyFromExternal sets to READY
            verify(provisioner).provision(spec);
            verify(environmentRepository, atLeastOnce()).save(any(Environment.class));
        }

        @Test
        @DisplayName("当 Provisioner 返回空结果时应抛出异常")
        void shouldThrowWhenProvisionerReturnsEmpty() {
            EnvironmentSpec spec = createDefaultSpec();

            given(provisioner.provision(spec))
                    .willReturn(reactor.core.publisher.Mono.empty());

            assertThatThrownBy(() -> environmentService.createFromSpec("test-empty", spec).block())
                    .isInstanceOf(EnvironmentCreationException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("当 Provisioner 抛出异常时应包装为 EnvironmentCreationException")
        void shouldWrapProvisionerException() {
            EnvironmentSpec spec = createDefaultSpec();
            given(provisioner.provision(spec))
                    .willReturn(reactor.core.publisher.Mono.error(new RuntimeException("docker not found")));

            assertThatThrownBy(() -> environmentService.createFromSpec("test-err", spec).block())
                    .isInstanceOf(EnvironmentCreationException.class)
                    .hasMessageContaining("docker not found");
        }

        @Test
        @DisplayName("当名称为空时应该自动生成名称")
        void shouldAutoGenerateNameWhenBlank() {
            String blankName = "   ";
            EnvironmentSpec spec = createDefaultSpec();
            Environment provisionedEnv = createMockEnvironment(blankName);

            given(provisioner.provision(spec))
                    .willReturn(reactor.core.publisher.Mono.just(provisionedEnv));
            when(environmentRepository.save(any(Environment.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            var result = environmentService.createFromSpec(blankName, spec).block();

            assertThat(result).isNotNull();
            verify(provisioner).provision(argThat(s ->
                s.getEnvironmentType() == EnvironmentType.DEV
            ));
        }
    }

    @Nested
    @DisplayName("Host 校验")
    class HostValidation {

        @Test
        @DisplayName("指定有效 TARGET 主机 + DOCKER 运行时应通过校验并调用 provisioner")
        void shouldPassValidationAndCallProvisioner() {
            EnvironmentSpec spec = EnvironmentSpec.builder()
                    .environmentType(EnvironmentType.EXPERIMENT)
                    .hostId("vm-ubuntu-test")
                    .isolationType(IsolationType.DOCKER)
                    .build();
            Environment mockEnv = createMockEnvironment("test-host-valid");

            given(provisioner.provision(spec))
                    .willReturn(reactor.core.publisher.Mono.just(mockEnv));
            when(environmentRepository.save(any(Environment.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            environmentService.createFromSpec("test-host-valid", spec).block();

            verify(hostService).validateRole(com.devops.dashboard.domain.host.HostId.of("vm-ubuntu-test"),
                    com.devops.dashboard.domain.host.HostRole.TARGET);
            verify(hostService).validateCapability(com.devops.dashboard.domain.host.HostId.of("vm-ubuntu-test"),
                    com.devops.dashboard.domain.host.Capability.DOCKER);
        }

        @Test
        @DisplayName("主机角色不是 TARGET 时应抛出 InvalidHostRoleException")
        void shouldThrowOnInvalidRole() {
            EnvironmentSpec spec = EnvironmentSpec.builder()
                    .environmentType(EnvironmentType.EXPERIMENT)
                    .hostId("vm-loadgen-01")
                    .isolationType(IsolationType.NATIVE)
                    .build();
            doThrow(new InvalidHostRoleException("vm-loadgen-01",
                    com.devops.dashboard.domain.host.HostRole.TARGET, java.util.Set.of()))
                    .when(hostService).validateRole(any(), any());

            assertThatThrownBy(() -> environmentService.createFromSpec("test-bad-role", spec).block())
                    .isInstanceOf(InvalidHostRoleException.class);
        }

        @Test
        @DisplayName("主机不支持 DOCKER 能力时应抛出异常")
        void shouldThrowOnMissingCapability() {
            EnvironmentSpec spec = EnvironmentSpec.builder()
                    .environmentType(EnvironmentType.EXPERIMENT)
                    .hostId("vm-loadgen-01")
                    .isolationType(IsolationType.DOCKER)
                    .build();
            doThrow(new HostCapabilityMismatchException("vm-loadgen-01",
                    com.devops.dashboard.domain.host.Capability.DOCKER, java.util.Set.of()))
                    .when(hostService).validateCapability(any(), any());

            assertThatThrownBy(() -> environmentService.createFromSpec("test-no-docker", spec).block())
                    .isInstanceOf(HostCapabilityMismatchException.class);
        }

        @Test
        @DisplayName("主机不存在时应抛出 HostNotFoundException")
        void shouldThrowOnHostNotFound() {
            EnvironmentSpec spec = EnvironmentSpec.builder()
                    .environmentType(EnvironmentType.EXPERIMENT)
                    .hostId("nonexistent-host")
                    .isolationType(IsolationType.DOCKER)
                    .build();
            doThrow(new HostNotFoundException("nonexistent-host"))
                    .when(hostService).validateRole(any(), any());

            assertThatThrownBy(() -> environmentService.createFromSpec("test-not-found", spec).block())
                    .isInstanceOf(HostNotFoundException.class);
        }

        @Test
        @DisplayName("未指定 hostId 时应跳过主机校验")
        void shouldSkipValidationWhenNoHostId() {
            EnvironmentSpec spec = EnvironmentSpec.builder()
                    .environmentType(EnvironmentType.EXPERIMENT)
                    .build();
            Environment mockEnv = createMockEnvironment("test-no-host");

            given(provisioner.provision(spec))
                    .willReturn(reactor.core.publisher.Mono.just(mockEnv));
            when(environmentRepository.save(any(Environment.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            environmentService.createFromSpec("test-no-host", spec).block();

            verify(hostService, never()).validateRole(any(), any());
            verify(hostService, never()).validateCapability(any(), any());
        }

        @Test
        @DisplayName("NATIVE 运行时不应校验 DOCKER 能力")
        void shouldNotCheckDockerForNativeRuntime() {
            EnvironmentSpec spec = EnvironmentSpec.builder()
                    .environmentType(EnvironmentType.EXPERIMENT)
                    .hostId("vm-ubuntu-test")
                    .isolationType(IsolationType.NATIVE)
                    .build();
            Environment mockEnv = createMockEnvironment("test-native");

            given(provisioner.provision(spec))
                    .willReturn(reactor.core.publisher.Mono.just(mockEnv));
            when(environmentRepository.save(any(Environment.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            environmentService.createFromSpec("test-native", spec).block();

            verify(hostService).validateRole(any(), eq(com.devops.dashboard.domain.host.HostRole.TARGET));
            verify(hostService, never()).validateCapability(any(), any());
        }
    }

    @Nested
    @DisplayName("销毁环境")
    class DestroyEnvironment {

        @Test
        @DisplayName("应该先调用 Teardown 再标记销毁，即使 Teardown 失败也继续标记")
        void shouldTeardownThenMarkDestroyed() {
            EnvironmentId envId = EnvironmentId.of("env-test-123");
            Environment existingEnv = createMockEnvironmentRunning("test-env");

            when(environmentRepository.findById(envId)).thenReturn(Optional.of(existingEnv));
            when(environmentRepository.save(any(Environment.class))).thenAnswer(inv -> inv.getArgument(0));
            given(provisioner.teardown(envId))
                    .willReturn(reactor.core.publisher.Mono.empty());

            environmentService.destroy(envId).block();

            verify(provisioner).teardown(envId);
            verify(environmentRepository).save(argThat(env ->
                env.getStatus() == EnvironmentStatus.DESTROYED
            ));
        }

        @Test
        @DisplayName("销毁不存在的环境应该抛出异常")
        void shouldThrowExceptionWhenDestroyingNonExistent() {
            EnvironmentId envId = EnvironmentId.of("env-nonexistent");
            when(environmentRepository.findById(envId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> environmentService.destroy(envId).block())
                    .isInstanceOf(EnvironmentNotFoundException.class);
        }

        @Test
        @DisplayName("Teardown 失败不应阻止状态更新")
        void shouldContinueWithDbUpdateEvenIfTeardownFails() {
            EnvironmentId envId = EnvironmentId.of("env-teardown-fail");
            Environment existingEnv = createMockEnvironmentRunning("fail-env");

            when(environmentRepository.findById(envId)).thenReturn(Optional.of(existingEnv));
            when(environmentRepository.save(any(Environment.class))).thenAnswer(inv -> inv.getArgument(0));
            given(provisioner.teardown(envId))
                    .willReturn(reactor.core.publisher.Mono.error(new RuntimeException("compose down failed")));

            environmentService.destroy(envId).block();

            verify(environmentRepository).save(argThat(env ->
                env.getStatus() == EnvironmentStatus.DESTROYED
            ));
        }
    }

    @Nested
    @DisplayName("查询环境")
    class FindEnvironment {

        @Test
        @DisplayName("根据 ID 查询应返回正确结果")
        void shouldFindById() {
            EnvironmentId envId = EnvironmentId.of("env-find-123");
            Environment expectedEnv = createMockEnvironment("find-me");
            when(environmentRepository.findById(envId)).thenReturn(Optional.of(expectedEnv));

            var result = environmentService.findById(envId).block();

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("find-me");
        }

        @Test
        @DisplayName("查询不存在的 ID 应抛出异常")
        void shouldThrowExceptionForNonExistentId() {
            EnvironmentId envId = EnvironmentId.of("env-missing");
            when(environmentRepository.findById(envId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> environmentService.findById(envId).block())
                    .isInstanceOf(EnvironmentNotFoundException.class);
        }

        @Test
        @DisplayName("按状态查询应返回匹配的环境列表")
        void shouldFindByStatus() {
            Environment runningEnv = createMockEnvironmentRunning("running-env");

            when(environmentRepository.findByStatus(EnvironmentStatus.RUNNING))
                    .thenReturn(java.util.List.of(runningEnv));

            var results = environmentService.findByStatus(EnvironmentStatus.RUNNING)
                    .collectList().block();

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getStatus()).isEqualTo(EnvironmentStatus.RUNNING);
        }
    }

    @Nested
    @DisplayName("获取访问端点")
    class GetAccessEndpoints {

        @Test
        @DisplayName("应返回环境的访问端点映射")
        void shouldReturnAccessEndpoints() {
            EnvironmentId envId = EnvironmentId.of("env-access-123");
            Environment env = createMockEnvironment("access-env");
            env.markAsReadyFromExternal(Map.of("console", "http://10.0.0.103:8848/nacos"));

            when(environmentRepository.findById(envId)).thenReturn(Optional.of(env));

            var endpoints = environmentService.getAccessEndpoints(envId).block();

            assertThat(endpoints).isNotNull();
            assertThat(endpoints).containsEntry("console", "http://10.0.0.103:8848/nacos");
        }

        @Test
        @DisplayName("环境不存在时应抛出异常")
        void shouldThrowWhenEnvNotFound() {
            EnvironmentId envId = EnvironmentId.of("env-missing-access");
            when(environmentRepository.findById(envId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> environmentService.getAccessEndpoints(envId).block())
                    .isInstanceOf(EnvironmentNotFoundException.class);
        }
    }

    private EnvironmentSpec createDefaultSpec() {
        return EnvironmentSpec.builder()
                .environmentType(EnvironmentType.DEV)
                .resourceQuota(ResourceQuota.development())
                .lifecyclePolicy(LifecyclePolicy.defaultForDev())
                .build();
    }

    /**
     * V3 辅助方法：通过正确的状态转换路径创建指定状态的环境
     */
    private Environment createMockEnvironment(String name) {
        Environment env = Environment.create(name, createDefaultSpec());
        return env;
    }

    /**
     * V3 辅助方法：创建处于 RUNNING 状态的环境
     */
    private Environment createMockEnvironmentRunning(String name) {
        Environment env = Environment.create(name, createDefaultSpec());
        env.markAsReady();
        env.markAsDeploying();
        env.markAsRunning(Map.of("app", "http://localhost:8080"));
        return env;
    }
}
