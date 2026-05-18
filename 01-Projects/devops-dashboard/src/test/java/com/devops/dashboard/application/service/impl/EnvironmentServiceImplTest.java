package com.devops.dashboard.application.service.impl;

import com.devops.dashboard.application.service.EnvironmentService;
import com.devops.dashboard.domain.environment.*;
import com.devops.dashboard.domain.environment.valueobject.LifecyclePolicy;
import com.devops.dashboard.domain.environment.valueobject.ResourceQuota;
import com.devops.dashboard.domain.exception.EnvironmentNotFoundException;
import com.devops.dashboard.infrastructure.persistence.EnvironmentRepository;
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
import static org.mockito.Mockito.*;

@DisplayName("EnvironmentServiceImpl 单元测试")
@ExtendWith(MockitoExtension.class)
class EnvironmentServiceImplTest {

    @Mock
    private EnvironmentRepository environmentRepository;

    @InjectMocks
    private EnvironmentServiceImpl environmentService;

    @Nested
    @DisplayName("创建环境")
    class CreateEnvironment {

        @Test
        @DisplayName("应该使用用户指定的名称创建环境")
        void shouldCreateEnvironmentWithProvidedName() {
            // Given
            String name = "dev-myapp";
            EnvironmentSpec spec = createDefaultSpec();
            
            Environment savedEnv = createMockEnvironment(name);
            when(environmentRepository.save(any(Environment.class))).thenReturn(savedEnv);

            // When
            var result = environmentService.createFromSpec(name, spec).block();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo(name);
            verify(environmentRepository, times(1)).save(any(Environment.class));
        }

        @Test
        @DisplayName("当名称为空时应该自动生成名称")
        void shouldAutoGenerateNameWhenBlank() {
            // Given
            String blankName = "   ";
            EnvironmentSpec spec = createDefaultSpec();
            
            Environment savedEnv = createMockEnvironment("dev-1234567890");
            when(environmentRepository.save(any(Environment.class))).thenReturn(savedEnv);

            // When
            var result = environmentService.createFromSpec(blankName, spec).block();

            // Then
            assertThat(result).isNotNull();
            verify(environmentRepository).save(argThat(env -> 
                env.getName().startsWith("dev-") || env.getName().startsWith("test-")
            ));
        }

        @Test
        @DisplayName("应该保存环境到数据库")
        void shouldSaveToDatabase() {
            // Given
            EnvironmentSpec spec = createDefaultSpec();
            when(environmentRepository.save(any(Environment.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            environmentService.createFromSpec("test-save", spec).block();

            // Then
            verify(environmentRepository).save(argThat(env -> 
                env.getName().equals("test-save") &&
                env.getStatus() == EnvironmentStatus.CREATING &&
                env.getType() == EnvironmentType.DEV
            ));
        }
    }

    @Nested
    @DisplayName("销毁环境")
    class DestroyEnvironment {

        @Test
        @DisplayName("应该成功销毁存在的环境")
        void shouldDestroyExistingEnvironment() {
            // Given
            EnvironmentId envId = EnvironmentId.of("env-test-123");
            Environment existingEnv = createMockEnvironment("test-env");
            existingEnv.markAsRunning(Map.of());
            
            when(environmentRepository.findById(envId)).thenReturn(Optional.of(existingEnv));
            when(environmentRepository.save(any(Environment.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            environmentService.destroy(envId).block();

            // Then
            verify(environmentRepository).save(argThat(env -> 
                env.getStatus() == EnvironmentStatus.DESTROYED
            ));
        }

        @Test
        @DisplayName("销毁不存在的环境应该抛出异常")
        void shouldThrowExceptionWhenDestroyingNonExistent() {
            // Given
            EnvironmentId envId = EnvironmentId.of("env-nonexistent");
            when(environmentRepository.findById(envId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> environmentService.destroy(envId).block())
                    .isInstanceOf(EnvironmentNotFoundException.class)
                    .hasMessageContaining("env-nonexistent");
        }
    }

    @Nested
    @DisplayName("查询环境")
    class FindEnvironment {

        @Test
        @DisplayName("根据 ID 查询应该返回正确结果")
        void shouldFindById() {
            // Given
            EnvironmentId envId = EnvironmentId.of("env-find-123");
            Environment expectedEnv = createMockEnvironment("find-me");
            when(environmentRepository.findById(envId)).thenReturn(Optional.of(expectedEnv));

            // When
            var result = environmentService.findById(envId).block();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("find-me");
        }

        @Test
        @DisplayName("查询不存在的 ID 应该抛出异常")
        void shouldThrowExceptionForNonExistentId() {
            // Given
            EnvironmentId envId = EnvironmentId.of("env-missing");
            when(environmentRepository.findById(envId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> environmentService.findById(envId).block())
                    .isInstanceOf(EnvironmentNotFoundException.class);
        }

        @Test
        @DisplayName("按状态查询应该返回匹配的环境列表")
        void shouldFindByStatus() {
            // Given
            Environment runningEnv = createMockEnvironment("running-env");
            runningEnv.markAsRunning(Map.of());
            
            when(environmentRepository.findByStatus(EnvironmentStatus.RUNNING))
                    .thenReturn(java.util.List.of(runningEnv));

            // When
            var results = environmentService.findByStatus(EnvironmentStatus.RUNNING)
                    .collectList().block();

            // Then
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getStatus()).isEqualTo(EnvironmentStatus.RUNNING);
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

    private Environment createMockEnvironment(String name) {
        Environment env = Environment.create(name, createDefaultSpec());
        return env;
    }
}
