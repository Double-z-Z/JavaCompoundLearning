package com.devops.dashboard.interfaces.rest;

import com.devops.dashboard.application.service.ExperimentService;
import com.devops.dashboard.application.service.impl.ExperimentServiceImpl;
import com.devops.dashboard.domain.experiment.*;
import com.devops.dashboard.domain.experiment.valueobject.Conclusion;
import com.devops.dashboard.domain.experiment.valueobject.Evidence;
import com.devops.dashboard.domain.experiment.valueobject.Hypothesis;
import com.devops.dashboard.interfaces.dto.ConclusionRequest;
import com.devops.dashboard.interfaces.dto.CreateExperimentRequest;
import com.devops.dashboard.interfaces.dto.ExperimentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("ExperimentController 集成测试")
@WebFluxTest(ExperimentController.class)
class ExperimentControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ExperimentService experimentService;

    private Experiment testExperiment;

    @BeforeEach
    void setUp() {
        testExperiment = Experiment.createSpike(SpikeRequest.builder()
                .title("Test Spike Experiment")
                .createdBy("tester")
                .hypothesis(Hypothesis.builder()
                        .statement("Test hypothesis statement")
                        .build())
                .build());

        // Disable archive report file I/O during tests
        if (experimentService instanceof ExperimentServiceImpl) {
            ((ExperimentServiceImpl) experimentService).setArchiveReportsEnabled(false);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/experiments - 创建实验")
    class CreateExperiment {

        @Test
        @DisplayName("应该成功创建实验并返回 201")
        void shouldCreateExperimentSuccessfully() {
            // Given
            CreateExperimentRequest request = new CreateExperimentRequest();
            request.setTitle("New Spike Experiment");
            request.setCreatedBy("developer");
            request.setHypothesisStatement("Nacos can handle 1000 TPS");
            request.setHypothesisBackground("Production needs high throughput");

            when(experimentService.createSpike(any(SpikeRequest.class)))
                    .thenReturn(Mono.just(testExperiment));

            // When & Then
            webTestClient.post()
                    .uri("/api/v1/experiments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody()
                    .jsonPath("$.id").isNotEmpty()
                    .jsonPath("$.title").isEqualTo("Test Spike Experiment");
        }

        @Test
        @DisplayName("缺少必填字段应该返回 400")
        void shouldReturn400WhenMissingRequiredFields() {
            // Given - title 为空
            CreateExperimentRequest request = new CreateExperimentRequest();
            request.setTitle("");
            request.setCreatedBy("developer");
            request.setHypothesisStatement("Some hypothesis");

            // When & Then
            webTestClient.post()
                    .uri("/api/v1/experiments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isBadRequest();
        }
    }

    @Nested
    @DisplayName("GET /api/v1/experiments - 查询实验列表")
    class ListExperiments {

        @Test
        @DisplayName("应该返回所有实验列表")
        void shouldReturnAllExperiments() {
            // Given
            when(experimentService.findByStatus(null))
                    .thenReturn(Flux.just(testExperiment));

            // When & Then
            webTestClient.get()
                    .uri("/api/v1/experiments")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(ExperimentResponse.class)
                    .hasSize(1)
                    .value(list -> assertThat(list.get(0).getTitle())
                            .isEqualTo("Test Spike Experiment"));
        }

        @Test
        @DisplayName("按状态筛选应该只返回匹配的实验")
        void shouldFilterByStatus() {
            // Given
            Experiment runningExp = Experiment.createSpike(SpikeRequest.builder()
                    .title("Running Experiment")
                    .createdBy("tester")
                    .hypothesis(Hypothesis.builder().statement("Test").build())
                    .build());
            runningExp.start("some-env-id");

            when(experimentService.findByStatus(ExperimentStatus.RUNNING))
                    .thenReturn(Flux.just(runningExp));

            // When & Then
            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/experiments")
                            .queryParam("status", "RUNNING")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(ExperimentResponse.class)
                    .hasSize(1);
        }

        @Test
        @DisplayName("空列表应该返回空数组")
        void shouldReturnEmptyArrayWhenNoExperiments() {
            // Given
            when(experimentService.findByStatus(null))
                    .thenReturn(Flux.empty());

            // When & Then
            webTestClient.get()
                    .uri("/api/v1/experiments")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBodyList(ExperimentResponse.class)
                    .hasSize(0);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/experiments/{id} - 查询实验详情")
    class GetExperiment {

        @Test
        @DisplayName("应该返回实验详情")
        void shouldReturnExperimentDetails() {
            // Given
            String expId = "exp-detail-123";
            when(experimentService.findById(ExperimentId.of(expId)))
                    .thenReturn(Mono.just(testExperiment));

            // When & Then
            webTestClient.get()
                    .uri("/api/v1/experiments/{id}", expId)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isNotEmpty()
                    .jsonPath("$.title").isEqualTo("Test Spike Experiment")
                    .jsonPath("$.status").isEqualTo("PLANNING");
        }

        @Test
        @DisplayName("查询不存在的实验应该返回错误")
        void shouldReturnErrorForNonExistentExperiment() {
            // Given - controller rethrows IllegalArgumentException which becomes 400
            String expId = "exp-nonexistent";
            when(experimentService.findById(ExperimentId.of(expId)))
                    .thenReturn(Mono.error(new IllegalArgumentException("Experiment not found: " + expId)));

            // When & Then
            webTestClient.get()
                    .uri("/api/v1/experiments/{id}", expId)
                    .exchange()
                    .expectStatus().isBadRequest(); // controller rethrows as 400
        }
    }

    @Nested
    @DisplayName("POST /api/v1/experiments/{id}/start - 启动实验")
    class StartExperiment {

        @Test
        @DisplayName("应该成功启动实验")
        void shouldStartExperimentSuccessfully() {
            // Given
            String expId = "exp-start-123";
            Experiment runningExp = Experiment.createSpike(SpikeRequest.builder()
                    .title("Running Experiment")
                    .createdBy("tester")
                    .hypothesis(Hypothesis.builder().statement("Test").build())
                    .build());
            runningExp.start("new-env-id");

            when(experimentService.start(ExperimentId.of(expId)))
                    .thenReturn(Mono.just(runningExp));

            // When & Then
            webTestClient.post()
                    .uri("/api/v1/experiments/{id}/start", expId)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isNotEmpty();
        }

        @Test
        @DisplayName("启动不存在的实验应该返回错误")
        void shouldReturnErrorWhenStartingNonExistent() {
            // Given
            String expId = "exp-missing-start";
            when(experimentService.start(ExperimentId.of(expId)))
                    .thenReturn(Mono.error(new IllegalArgumentException("Experiment not found")));

            // When & Then
            webTestClient.post()
                    .uri("/api/v1/experiments/{id}/start", expId)
                    .exchange()
                    .expectStatus().isBadRequest();
        }
    }

    @Nested
    @DisplayName("POST /api/v1/experiments/{id}/evidence - 记录证据")
    class RecordEvidence {

        @Test
        @DisplayName("应该成功记录证据")
        void shouldRecordEvidenceSuccessfully() {
            // Given
            String expId = "exp-evidence-123";
            Experiment runningExp = Experiment.createSpike(SpikeRequest.builder()
                    .title("Evidence Test")
                    .createdBy("tester")
                    .hypothesis(Hypothesis.builder().statement("Test").build())
                    .build());
            runningExp.start("some-env-id");

            when(experimentService.recordEvidence(eq(ExperimentId.of(expId)), any(Evidence.class)))
                    .thenReturn(Mono.just(runningExp));

            // When & Then
            webTestClient.post()
                    .uri("/api/v1/experiments/{id}/evidence", expId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(java.util.Map.of("metrics", "tps: 1200"))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isNotEmpty();
        }
    }

    @Nested
    @DisplayName("POST /api/v1/experiments/{id}/conclude - 提交结论")
    class ConcludeExperiment {

        @Test
        @DisplayName("应该成功提交结论")
        void shouldConcludeExperimentSuccessfully() {
            // Given
            String expId = "exp-conclude-123";
            ConclusionRequest request = new ConclusionRequest();
            request.setDecision("ACCEPT");
            request.setSummary("The hypothesis was supported");
            request.setLessonsLearned("Nacos handles high TPS well");
            request.setNextSteps("Proceed to production deployment");

            Experiment concludedExp = Experiment.createSpike(SpikeRequest.builder()
                    .title("Conclude Test")
                    .createdBy("tester")
                    .hypothesis(Hypothesis.builder().statement("Test").build())
                    .build());
            concludedExp.start("env-id");
            concludedExp.conclude(Conclusion.builder()
                    .decision(ExperimentDecision.ACCEPT)
                    .summary("Test")
                    .build());

            when(experimentService.conclude(eq(ExperimentId.of(expId)), any(Conclusion.class)))
                    .thenReturn(Mono.just(concludedExp));

            // When & Then
            webTestClient.post()
                    .uri("/api/v1/experiments/{id}/conclude", expId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isNotEmpty();
        }
    }

    @Nested
    @DisplayName("POST /api/v1/experiments/{id}/archive - 归档实验")
    class ArchiveExperiment {

        @Test
        @DisplayName("应该成功归档实验")
        void shouldArchiveExperimentSuccessfully() {
            // Given
            String expId = "exp-archive-123";
            Experiment archivedExp = Experiment.createSpike(SpikeRequest.builder()
                    .title("Archive Test")
                    .createdBy("tester")
                    .hypothesis(Hypothesis.builder().statement("Test").build())
                    .build());
            archivedExp.start("env-id");
            archivedExp.conclude(Conclusion.builder()
                    .decision(ExperimentDecision.ACCEPT)
                    .summary("Test")
                    .build());
            archivedExp.archive("docs/spikes/test.md");

            when(experimentService.archive(ExperimentId.of(expId)))
                    .thenReturn(Mono.just(archivedExp));

            // When & Then
            webTestClient.post()
                    .uri("/api/v1/experiments/{id}/archive", expId)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isNotEmpty();
        }
    }

    @Nested
    @DisplayName("POST /api/v1/experiments/{id}/cancel - 取消实验")
    class CancelExperiment {

        @Test
        @DisplayName("应该成功取消实验")
        void shouldCancelExperimentSuccessfully() {
            // Given
            String expId = "exp-cancel-123";
            Experiment cancelledExp = Experiment.createSpike(SpikeRequest.builder()
                    .title("Cancel Test")
                    .createdBy("tester")
                    .hypothesis(Hypothesis.builder().statement("Test").build())
                    .build());
            cancelledExp.start("env-id");
            cancelledExp.cancel();

            when(experimentService.cancel(ExperimentId.of(expId)))
                    .thenReturn(Mono.just(cancelledExp));

            // When & Then
            webTestClient.post()
                    .uri("/api/v1/experiments/{id}/cancel", expId)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isNotEmpty();
        }
    }
}