package com.devops.dashboard.application.service.impl;

import com.devops.dashboard.application.service.EnvironmentService;
import com.devops.dashboard.domain.environment.Environment;
import com.devops.dashboard.domain.environment.EnvironmentId;
import com.devops.dashboard.domain.environment.EnvironmentSpec;
import com.devops.dashboard.domain.environment.EnvironmentType;
import com.devops.dashboard.domain.environment.valueobject.LifecyclePolicy;
import com.devops.dashboard.domain.environment.valueobject.ResourceQuota;
import com.devops.dashboard.domain.experiment.Experiment;
import com.devops.dashboard.domain.experiment.ExperimentDecision;
import com.devops.dashboard.domain.experiment.ExperimentId;
import com.devops.dashboard.domain.experiment.ExperimentStatus;
import com.devops.dashboard.domain.experiment.SpikeRequest;
import com.devops.dashboard.domain.experiment.valueobject.Conclusion;
import com.devops.dashboard.domain.experiment.valueobject.Evidence;
import com.devops.dashboard.domain.experiment.valueobject.Hypothesis;
import com.devops.dashboard.infrastructure.experiment.ExperimentRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("ExperimentServiceImpl 单元测试")
@ExtendWith(MockitoExtension.class)
class ExperimentServiceImplTest {

    @Mock
    private ExperimentRepository experimentRepository;

    @Mock
    private EnvironmentService environmentService;

    @InjectMocks
    private ExperimentServiceImpl experimentService;

    @BeforeEach
    void setUp() {
        experimentService.setArchiveReportsEnabled(false);
    }

    @Nested
    @DisplayName("创建实验 createSpike")
    class CreateSpike {

        @Test
        @DisplayName("应该成功创建实验并返回 PLANNING 状态")
        void shouldCreateExperimentWithPlanningStatus() {
            // Given
            SpikeRequest request = SpikeRequest.builder()
                    .title("Test Spike")
                    .createdBy("tester")
                    .hypothesis(Hypothesis.builder()
                            .statement("Test hypothesis")
                            .build())
                    .build();

            when(experimentRepository.save(any(Experiment.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            Experiment result = experimentService.createSpike(request).block();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(ExperimentStatus.PLANNING);
            assertThat(result.getTitle()).isEqualTo("Test Spike");
            verify(experimentRepository, times(1)).save(any(Experiment.class));
        }

        @Test
        @DisplayName("当 hypothesis 为空时 hypothesis 为 null")
        void shouldHandleNullHypothesis() {
            // Given
            SpikeRequest request = SpikeRequest.builder()
                    .title("Null Hypothesis Test")
                    .createdBy("tester")
                    .hypothesis(null)
                    .build();

            when(experimentRepository.save(any(Experiment.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            Experiment result = experimentService.createSpike(request).block();

            // Then
            assertThat(result).isNotNull();
            // hypothesis remains null when not provided
            assertThat(result.getHypothesis()).isNull();
        }
    }

    @Nested
    @DisplayName("启动实验 start")
    class StartExperiment {

        @Test
        @DisplayName("应该成功启动实验并创建环境")
        void shouldStartExperimentAndCreateEnvironment() {
            // Given
            ExperimentId expId = ExperimentId.of("exp-123");
            Experiment experiment = createMockExperiment("exp-123");
            // createSpike() already sets status to PLANNING

            Environment createdEnv = Environment.create("exp-env-exp-123",
                    EnvironmentSpec.builder()
                            .type(EnvironmentType.EXPERIMENT)
                            .resourceQuota(ResourceQuota.development())
                            .lifecyclePolicy(LifecyclePolicy.forExperiment())
                            .build());

            when(experimentRepository.findById(expId)).thenReturn(Optional.of(experiment));
            when(environmentService.createFromSpec(anyString(), any(EnvironmentSpec.class)))
                    .thenReturn(Mono.just(createdEnv));
            when(experimentRepository.save(any(Experiment.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            Experiment result = experimentService.start(expId).block();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(ExperimentStatus.RUNNING);
            assertThat(result.getEnvironmentId()).isNotNull();
            verify(environmentService).createFromSpec(anyString(), any(EnvironmentSpec.class));
        }

        @Test
        @DisplayName("找不到实验应该抛出异常")
        void shouldThrowExceptionWhenExperimentNotFound() {
            // Given
            ExperimentId expId = ExperimentId.of("exp-nonexistent");
            when(experimentRepository.findById(expId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> experimentService.start(expId).block())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Experiment not found");
        }
    }

    @Nested
    @DisplayName("提交结论 conclude")
    class ConcludeExperiment {

        @Test
        @DisplayName("应该成功提交结论")
        void shouldConcludeExperimentSuccessfully() {
            // Given
            ExperimentId expId = ExperimentId.of("exp-456");
            Experiment experiment = createMockExperiment("exp-456");
            experiment.start("fake-env-id"); // transition to RUNNING

            Conclusion conclusion = Conclusion.builder()
                    .decision(ExperimentDecision.ACCEPT)
                    .summary("Test summary")
                    .lessonsLearned(List.of("Lesson 1"))
                    .nextSteps(List.of("Next 1"))
                    .build();

            when(experimentRepository.findById(expId)).thenReturn(Optional.of(experiment));
            when(experimentRepository.save(any(Experiment.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            Experiment result = experimentService.conclude(expId, conclusion).block();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(ExperimentStatus.COMPLETED);
            assertThat(result.getConclusion()).isNotNull();
            assertThat(result.getConclusion().getDecision()).isEqualTo(ExperimentDecision.ACCEPT);
        }

        @Test
        @DisplayName("找不到实验应该抛出异常")
        void shouldThrowExceptionWhenExperimentNotFound() {
            // Given
            ExperimentId expId = ExperimentId.of("exp-nonexistent");
            Conclusion conclusion = Conclusion.builder()
                    .decision(ExperimentDecision.ACCEPT)
                    .summary("Test")
                    .build();

            when(experimentRepository.findById(expId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> experimentService.conclude(expId, conclusion).block())
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("记录证据 recordEvidence")
    class RecordEvidence {

        @Test
        @DisplayName("应该成功记录证据")
        void shouldRecordEvidenceSuccessfully() {
            // Given
            ExperimentId expId = ExperimentId.of("exp-evidence");
            Experiment experiment = createMockExperiment("exp-evidence");
            experiment.start("fake-env-id"); // transition to RUNNING

            Evidence evidence = Evidence.builder()
                    .collectedAt(LocalDateTime.now())
                    .metrics(List.of(
                            Evidence.Metric.builder().name("tps").value(1200).unit("req/s").build(),
                            Evidence.Metric.builder().name("latency").value(45).unit("ms").build()
                    ))
                    .artifacts(Collections.emptyList())
                    .build();

            when(experimentRepository.findById(expId)).thenReturn(Optional.of(experiment));
            when(experimentRepository.save(any(Experiment.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            Experiment result = experimentService.recordEvidence(expId, evidence).block();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getEvidence()).isNotNull();
            assertThat(result.getEvidence().getMetrics()).hasSize(2);
            assertThat(result.getEvidence().getMetrics().get(0).getName()).isEqualTo("tps");
        }
    }

    @Nested
    @DisplayName("归档实验 archive")
    class ArchiveExperiment {

        @Test
        @DisplayName("应该成功归档实验并清理环境")
        void shouldArchiveExperimentAndCleanupEnvironment() {
            // Given
            ExperimentId expId = ExperimentId.of("exp-archive");
            Experiment experiment = createMockExperiment("exp-archive");
            experiment.start("env-linked-123"); // to RUNNING
            experiment.conclude(Conclusion.builder()
                    .decision(ExperimentDecision.ACCEPT)
                    .summary("Test")
                    .build()); // to COMPLETED

            when(experimentRepository.findById(expId)).thenReturn(Optional.of(experiment));
            when(experimentRepository.save(any(Experiment.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(environmentService.destroy(any(EnvironmentId.class)))
                    .thenReturn(Mono.empty());

            // When
            Experiment result = experimentService.archive(expId).block();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(ExperimentStatus.ARCHIVED);
            verify(environmentService).destroy(any(EnvironmentId.class));
        }

        @Test
        @DisplayName("归档时没有关联环境应该正常处理")
        void shouldArchiveEvenWithoutLinkedEnvironment() {
            // Given
            ExperimentId expId = ExperimentId.of("exp-no-env");
            Experiment experiment = createMockExperiment("exp-no-env");
            experiment.start(null); // no environment
            experiment.conclude(Conclusion.builder()
                    .decision(ExperimentDecision.ACCEPT)
                    .summary("Test")
                    .build());

            when(experimentRepository.findById(expId)).thenReturn(Optional.of(experiment));
            when(experimentRepository.save(any(Experiment.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            Experiment result = experimentService.archive(expId).block();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(ExperimentStatus.ARCHIVED);
            verify(environmentService, never()).destroy(any());
        }
    }

    @Nested
    @DisplayName("取消实验 cancel")
    class CancelExperiment {

        @Test
        @DisplayName("应该成功取消实验")
        void shouldCancelExperimentSuccessfully() {
            // Given
            ExperimentId expId = ExperimentId.of("exp-cancel");
            Experiment experiment = createMockExperiment("exp-cancel");
            experiment.start("env-to-destroy"); // to RUNNING

            when(experimentRepository.findById(expId)).thenReturn(Optional.of(experiment));
            when(experimentRepository.save(any(Experiment.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(environmentService.destroy(any(EnvironmentId.class)))
                    .thenReturn(Mono.empty());

            // When
            Experiment result = experimentService.cancel(expId).block();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(ExperimentStatus.CANCELLED);
            verify(environmentService).destroy(any(EnvironmentId.class));
        }
    }

    @Nested
    @DisplayName("查询实验")
    class FindExperiments {

        @Test
        @DisplayName("findByStatus 返回 null 时应该返回全部实验")
        void shouldReturnAllExperimentsWhenStatusIsNull() {
            // Given
            Experiment exp1 = createMockExperiment("exp-1");
            Experiment exp2 = createMockExperiment("exp-2");
            when(experimentRepository.findAll()).thenReturn(List.of(exp1, exp2));

            // When
            List<Experiment> results = experimentService.findByStatus(null)
                    .collectList().block();

            // Then
            assertThat(results).hasSize(2);
            verify(experimentRepository).findAll();
        }

        @Test
        @DisplayName("findByStatus 按状态筛选应该正确过滤")
        void shouldFilterByStatus() {
            // Given
            Experiment runningExp = createMockExperiment("exp-running");
            runningExp.start("some-env"); // to RUNNING

            when(experimentRepository.findByStatus(ExperimentStatus.RUNNING))
                    .thenReturn(List.of(runningExp));

            // When
            List<Experiment> results = experimentService.findByStatus(ExperimentStatus.RUNNING)
                    .collectList().block();

            // Then
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getStatus()).isEqualTo(ExperimentStatus.RUNNING);
        }

        @Test
        @DisplayName("findById 找到实验应该返回正确结果")
        void shouldFindByIdSuccessfully() {
            // Given
            ExperimentId expId = ExperimentId.of("exp-find");
            Experiment expected = createMockExperiment("exp-find");
            when(experimentRepository.findById(expId)).thenReturn(Optional.of(expected));

            // When
            Experiment result = experimentService.findById(expId).block();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getTitle()).isEqualTo("exp-find");
        }

        @Test
        @DisplayName("findById 找不到应该抛出异常")
        void shouldThrowExceptionWhenNotFound() {
            // Given
            ExperimentId expId = ExperimentId.of("exp-missing");
            when(experimentRepository.findById(expId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> experimentService.findById(expId).block())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Experiment not found");
        }
    }

    // ==================== 辅助方法 ====================

    private Experiment createMockExperiment(String id) {
        SpikeRequest request = SpikeRequest.builder()
                .title(id)
                .createdBy("tester")
                .hypothesis(Hypothesis.builder()
                        .statement("Test hypothesis for " + id)
                        .build())
                .build();
        return Experiment.createSpike(request);
    }
}